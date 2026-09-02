package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Privacy-first, opt-in technical diagnostics for GharTV.
 *
 * The collector deliberately avoids Jio mobile numbers, OTPs, credentials,
 * cookies, stream/license URLs, hardware identifiers and successful channel
 * viewing history. A failed Jio channel ID may be included so a failure can be
 * reproduced. All attributes are scrubbed again before upload, and the server
 * performs a second independent scrub.
 */
public final class Telemetry {
    public enum UploadOutcome { SUCCESS, EMPTY, DISABLED, RETRY, PERMANENT_FAILURE }

    private static final String PREFS = "ghartv_telemetry";
    private static final String KEY_DECIDED = "consent_decided";
    private static final String KEY_ENABLED = "diagnostics_enabled";
    private static final String KEY_INSTALL_ID = "install_id";
    private static final String KEY_LAST_SCREEN = "last_screen";
    private static final String KEY_LAST_UPLOAD = "last_upload";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final String KEY_CONFIG_ENDPOINT = "config_endpoint";
    private static final String KEY_CONFIG_KEY = "config_ingest_key";
    private static final String KEY_CONFIG_ENABLED = "config_enabled";
    private static final String KEY_CONFIG_FETCHED_AT = "config_fetched_at";

    private static final String QUEUE_DIR = "telemetry";
    private static final String QUEUE_FILE = "events.jsonl";
    private static final String UNIQUE_PERIODIC = "ghartv-telemetry-periodic";
    private static final String UNIQUE_IMMEDIATE = "ghartv-telemetry-immediate";

    private static final int MAX_QUEUE_EVENTS = 500;
    private static final int MAX_QUEUE_BYTES = 512 * 1024;
    private static final int MAX_BATCH_EVENTS = 40;
    private static final long CONFIG_TTL_MS = 6L * 60L * 60L * 1000L;

    private static final Object FILE_LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean CONSENT_DIALOG_OPEN = new AtomicBoolean(false);
    private static final String SESSION_ID = shortHash(UUID.randomUUID().toString());
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private static final Pattern EVENT_NAME = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern BANNED_KEY = Pattern.compile(
            "(?i).*(mobile|phone|otp|password|passcode|secret|token|cookie|authorization|"
                    + "stream[_-]?url|license[_-]?url|headers?|device[_-]?id|android[_-]?id|"
                    + "subscriber|crmid|email|ssid|bssid|mac|serial|ip[_-]?address).*"
    );
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?91[- ]?)?[6-9]\\d{9}(?!\\d)");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(otp|password|passcode|token|cookie|authorization|secret)\\s*[:=]\\s*[^\\s,;]+"
    );
    private static final Pattern JWT_OR_LONG_SECRET = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{36,}(?:\\.[A-Za-z0-9_-]{12,}){0,2}(?![A-Za-z0-9_-])"
    );

    private static volatile Context appContext;
    private static volatile Thread.UncaughtExceptionHandler previousCrashHandler;

    private Telemetry() {}

    public static void initialize(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        if (!INITIALIZED.compareAndSet(false, true)) return;
        installCrashHandler();
        schedulePeriodicUpload(appContext);
        if (isEnabled(appContext)) {
            event(appContext, "app_open", data(
                    "launch", "application",
                    "queue_depth", queuedCount(appContext)
            ));
            enqueueUpload(appContext, false);
        }
    }

    public static boolean hasDecision(Context context) {
        return prefs(context).getBoolean(KEY_DECIDED, false);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences preferences = prefs(context);
        preferences.edit()
                .putBoolean(KEY_DECIDED, true)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
        if (enabled) {
            event(context, "consent_changed", data("enabled", true, "source", "tv_settings"));
            enqueueUpload(context, true);
        } else {
            clearQueuedEvents(context, false);
        }
    }

    public static void maybeRequestConsent(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (hasDecision(activity) || !CONSENT_DIALOG_OPEN.compareAndSet(false, true)) return;
        activity.runOnUiThread(() -> showConsentDialog(activity));
    }

    private static void showConsentDialog(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            CONSENT_DIALOG_OPEN.set(false);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Help improve GharTV?")
                .setMessage(
                        "Share privacy-filtered technical diagnostics so playback and TV-remote problems can be fixed in future releases.\n\n"
                                + "Included: app version, TV model and Android version, feature-use counts, startup/buffering timing, error type and HTTP status. When a channel fails, its Jio channel ID may be included so the failure can be reproduced.\n\n"
                                + "Never included: your Jio mobile number, OTP, passwords, tokens, cookies, stream or licence URLs, successful channel viewing history, IP address, Wi-Fi name, serial number or Android ID.\n\n"
                                + "You can preview, send or delete queued diagnostics at any time from Jio account → Diagnostics & privacy."
                )
                .setPositiveButton("Share diagnostics", (which, button) -> {
                    CONSENT_DIALOG_OPEN.set(false);
                    setEnabled(activity, true);
                })
                .setNegativeButton("Not now", (which, button) -> {
                    CONSENT_DIALOG_OPEN.set(false);
                    setEnabled(activity, false);
                })
                .setNeutralButton("Privacy details", null)
                .setOnCancelListener(dialogInterface -> CONSENT_DIALOG_OPEN.set(false))
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view -> DiagnosticsDialog.showPrivacyDetails(activity)));
        dialog.show();
    }

    public static void screen(Context context, String screen) {
        String safe = safeToken(screen, 40, "unknown");
        prefs(context).edit().putString(KEY_LAST_SCREEN, safe).apply();
        event(context, "screen_view", data("screen", safe));
    }

    public static void event(Context context, String name, JSONObject attributes) {
        if (context == null || !isEnabled(context)) return;
        String normalized = safeToken(name, 64, "event");
        if (!EVENT_NAME.matcher(normalized).matches()) normalized = "invalid_event_name";
        final String eventName = normalized;
        final JSONObject attrs = attributes == null ? new JSONObject() : attributes;
        final Context application = context.getApplicationContext();
        IO.execute(() -> appendEvent(application, buildEvent(application, null, eventName, attrs)));
    }

    public static String error(Context context, String stage, Throwable error, JSONObject attributes) {
        String eventId = UUID.randomUUID().toString();
        if (context == null || !isEnabled(context)) return reference(eventId);
        JSONObject attrs = attributes == null ? new JSONObject() : attributes;
        try {
            attrs.put("stage", safeToken(stage, 48, "unknown"));
            attrs.put("error_type", error == null ? "Unknown" : error.getClass().getSimpleName());
            attrs.put("message", scrubString(error == null ? "Unknown error" : readable(error)));
            attrs.put("fingerprint", errorFingerprint(error));
            attrs.put("stack_frames", stackFrames(error));
            attrs.put("network", networkType(context));
        } catch (JSONException ignored) {}
        Context application = context.getApplicationContext();
        IO.execute(() -> {
            appendEvent(application, buildEvent(application, eventId, "app_error", attrs));
            enqueueUpload(application, true);
        });
        return reference(eventId);
    }

    public static String playbackFailure(Context context, Channel channel, String stage,
                                         String message, int httpStatus, Throwable error) {
        JSONObject attrs = data(
                "stage", safeToken(stage, 48, "playback"),
                "http_status", httpStatus,
                "category", channel == null ? "unknown" : channel.category,
                "language", channel == null ? "unknown" : channel.language,
                "access_state", channel == null ? "unknown" : channel.accessState,
                "failed_channel_id", channel == null ? "" : safeToken(channel.id, 64, "")
        );
        if (message != null && !message.trim().isEmpty()) {
            try { attrs.put("provider_message", scrubString(message)); }
            catch (JSONException ignored) {}
        }
        return error(context, stage, error == null ? new IllegalStateException(message) : error, attrs);
    }

    public static JSONObject data(Object... values) {
        JSONObject object = new JSONObject();
        if (values == null) return object;
        for (int index = 0; index + 1 < values.length; index += 2) {
            Object key = values[index];
            if (key == null) continue;
            try { object.put(String.valueOf(key), values[index + 1]); }
            catch (JSONException ignored) {}
        }
        return object;
    }

    public static void enqueueUpload(Context context, boolean replaceExisting) {
        if (context == null || !isEnabled(context)) return;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TelemetryUploadWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                replaceExisting ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP,
                request
        );
    }

    private static void schedulePeriodicUpload(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                TelemetryUploadWorker.class, 12, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    static UploadOutcome upload(Context context) {
        if (context == null || !isEnabled(context)) return UploadOutcome.DISABLED;
        List<String> lines = readBatch(context, MAX_BATCH_EVENTS);
        if (lines.isEmpty()) return UploadOutcome.EMPTY;

        try {
            CollectorConfig config = resolveConfig(context);
            if (!config.enabled) {
                saveStatus(context, "Collector is temporarily disabled", false);
                return UploadOutcome.PERMANENT_FAILURE;
            }
            if (config.endpoint.isEmpty() || config.ingestKey.isEmpty()) {
                saveStatus(context, "Collector is not configured", false);
                return UploadOutcome.PERMANENT_FAILURE;
            }

            JSONArray events = new JSONArray();
            for (String line : lines) events.put(new JSONObject(line));
            JSONObject batch = new JSONObject();
            batch.put("schema", "ghartv.telemetry.batch.v1");
            batch.put("sent_at", System.currentTimeMillis());
            batch.put("install_hash", installHash(context));
            batch.put("session_id", SESSION_ID);
            batch.put("app_version", BuildConfig.VERSION_NAME);
            batch.put("version_code", BuildConfig.VERSION_CODE);
            batch.put("device", deviceEnvelope(context));
            batch.put("events", events);

            String endpoint = config.endpoint.endsWith("/v1/events")
                    ? config.endpoint
                    : stripTrailingSlash(config.endpoint) + "/v1/events";
            Request request = new Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME)
                    .header("X-GharTV-Ingest-Key", config.ingestKey)
                    .post(RequestBody.create(batch.toString(), JSON))
                    .build();
            try (Response response = CLIENT.newCall(request).execute()) {
                int code = response.code();
                if (response.isSuccessful()) {
                    removeFirst(context, lines.size());
                    saveStatus(context, "Sent " + lines.size() + " diagnostics", true);
                    return UploadOutcome.SUCCESS;
                }
                String status = "Collector returned HTTP " + code;
                saveStatus(context, status, false);
                return code == 408 || code == 425 || code == 429 || code >= 500
                        ? UploadOutcome.RETRY : UploadOutcome.PERMANENT_FAILURE;
            }
        } catch (Exception error) {
            saveStatus(context, "Upload waiting: " + scrubString(readable(error)), false);
            return UploadOutcome.RETRY;
        }
    }

    public static int queuedCount(Context context) {
        synchronized (FILE_LOCK) { return readAllLocked(context).size(); }
    }

    public static String lastStatus(Context context) {
        return prefs(context).getString(KEY_LAST_STATUS, "Nothing has been sent yet.");
    }

    public static long lastUploadAt(Context context) {
        return prefs(context).getLong(KEY_LAST_UPLOAD, 0L);
    }

    public static String preview(Context context) {
        List<String> lines;
        synchronized (FILE_LOCK) { lines = readAllLocked(context); }
        if (lines.isEmpty()) return "No technical diagnostics are waiting to be sent.";
        StringBuilder output = new StringBuilder();
        output.append("Queued reports: ").append(lines.size()).append("\n\n");
        int start = Math.max(0, lines.size() - 12);
        for (int index = start; index < lines.size(); index++) {
            try {
                JSONObject event = new JSONObject(lines.get(index));
                output.append("• ")
                        .append(event.optString("name", "event"))
                        .append("  —  ")
                        .append(event.optString("reference", ""))
                        .append('\n');
                JSONObject attrs = event.optJSONObject("attributes");
                if (attrs != null && attrs.length() > 0) {
                    output.append("  ").append(attrs.toString()).append('\n');
                }
            } catch (Exception ignored) {
                output.append("• unreadable local report\n");
            }
        }
        output.append("\nEach upload also adds the app version, TV manufacturer/model, Android version, locale, broad connection type and a random pseudonymous installation hash. Mobile numbers, OTPs, credentials, URLs and unique hardware IDs are removed before reports enter this queue.");
        return output.toString();
    }

    public static void clearQueuedEvents(Context context, boolean resetIdentity) {
        synchronized (FILE_LOCK) {
            File file = queueFile(context);
            if (file.exists()) file.delete();
        }
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove(KEY_LAST_UPLOAD)
                .putString(KEY_LAST_STATUS, "Local diagnostics were deleted.");
        if (resetIdentity) editor.remove(KEY_INSTALL_ID);
        editor.apply();
    }

    private static JSONObject buildEvent(Context context, String suppliedId, String name, JSONObject attrs) {
        String id = suppliedId == null ? UUID.randomUUID().toString() : suppliedId;
        JSONObject event = new JSONObject();
        try {
            event.put("id", id);
            event.put("reference", reference(id));
            event.put("timestamp", System.currentTimeMillis());
            event.put("name", name);
            event.put("screen", prefs(context).getString(KEY_LAST_SCREEN, "unknown"));
            event.put("attributes", sanitizeObject(attrs));
        } catch (JSONException ignored) {}
        return event;
    }

    private static JSONObject deviceEnvelope(Context context) {
        JSONObject device = new JSONObject();
        try {
            device.put("manufacturer", safeToken(Build.MANUFACTURER, 40, "unknown"));
            device.put("model", safeToken(Build.MODEL, 80, "unknown"));
            device.put("android_release", safeToken(Build.VERSION.RELEASE, 24, "unknown"));
            device.put("android_api", Build.VERSION.SDK_INT);
            device.put("locale", Locale.getDefault().toLanguageTag());
            device.put("network", networkType(context));
        } catch (JSONException ignored) {}
        return device;
    }

    private static CollectorConfig resolveConfig(Context context) throws Exception {
        SharedPreferences preferences = prefs(context);
        long now = System.currentTimeMillis();
        long fetched = preferences.getLong(KEY_CONFIG_FETCHED_AT, 0L);
        String endpoint = preferences.getString(KEY_CONFIG_ENDPOINT, "");
        String key = preferences.getString(KEY_CONFIG_KEY, "");
        boolean enabled = preferences.getBoolean(KEY_CONFIG_ENABLED, true);

        if (now - fetched > CONFIG_TTL_MS || endpoint.isEmpty() || key.isEmpty()) {
            Request request = new Request.Builder()
                    .url(AppConfig.TELEMETRY_CONFIG)
                    .header("User-Agent", "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME)
                    .build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = new JSONObject(response.body().string());
                    endpoint = json.optString("endpoint", endpoint);
                    key = json.optString("ingestKey", key);
                    enabled = json.optBoolean("enabled", true);
                    preferences.edit()
                            .putString(KEY_CONFIG_ENDPOINT, endpoint)
                            .putString(KEY_CONFIG_KEY, key)
                            .putBoolean(KEY_CONFIG_ENABLED, enabled)
                            .putLong(KEY_CONFIG_FETCHED_AT, now)
                            .apply();
                }
            } catch (Exception ignored) {
                // A build-time fallback keeps diagnostics available if GitHub config is briefly unreachable.
            }
        }
        if (endpoint == null || endpoint.trim().isEmpty()) endpoint = AppConfig.TELEMETRY_FALLBACK_ENDPOINT;
        if (key == null || key.trim().isEmpty()) key = AppConfig.TELEMETRY_FALLBACK_INGEST_KEY;
        return new CollectorConfig(endpoint == null ? "" : endpoint.trim(),
                key == null ? "" : key.trim(), enabled);
    }

    private static void appendEvent(Context context, JSONObject event) {
        synchronized (FILE_LOCK) {
            try {
                File file = queueFile(context);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileOutputStream output = new FileOutputStream(file, true)) {
                    output.write(event.toString().getBytes(StandardCharsets.UTF_8));
                    output.write('\n');
                    output.getFD().sync();
                }
                trimLocked(context);
            } catch (Exception ignored) {
                // Diagnostics must never interrupt television playback.
            }
        }
    }

    private static List<String> readBatch(Context context, int max) {
        synchronized (FILE_LOCK) {
            List<String> all = readAllLocked(context);
            if (all.isEmpty()) return Collections.emptyList();
            return new ArrayList<>(all.subList(0, Math.min(max, all.size())));
        }
    }

    private static List<String> readAllLocked(Context context) {
        File file = queueFile(context);
        if (!file.exists()) return new ArrayList<>();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        } catch (Exception ignored) {}
        return lines;
    }

    private static void removeFirst(Context context, int count) {
        synchronized (FILE_LOCK) {
            List<String> all = readAllLocked(context);
            if (all.isEmpty()) return;
            int from = Math.min(count, all.size());
            rewriteLocked(context, new ArrayList<>(all.subList(from, all.size())));
        }
    }

    private static void trimLocked(Context context) {
        List<String> all = readAllLocked(context);
        boolean tooMany = all.size() > MAX_QUEUE_EVENTS;
        File file = queueFile(context);
        boolean tooLarge = file.length() > MAX_QUEUE_BYTES;
        if (!tooMany && !tooLarge) return;
        int start = Math.max(0, all.size() - MAX_QUEUE_EVENTS);
        List<String> kept = new ArrayList<>(all.subList(start, all.size()));
        while (encodedSize(kept) > MAX_QUEUE_BYTES && kept.size() > 1) kept.remove(0);
        rewriteLocked(context, kept);
    }

    private static int encodedSize(List<String> lines) {
        int size = 0;
        for (String line : lines) size += line.getBytes(StandardCharsets.UTF_8).length + 1;
        return size;
    }

    private static void rewriteLocked(Context context, List<String> lines) {
        File target = queueFile(context);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File temporary = new File(parent, QUEUE_FILE + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            for (String line : lines) {
                output.write(line.getBytes(StandardCharsets.UTF_8));
                output.write('\n');
            }
            output.getFD().sync();
            if (target.exists() && !target.delete()) return;
            if (!temporary.renameTo(target)) {
                try (FileOutputStream fallback = new FileOutputStream(target, false)) {
                    for (String line : lines) {
                        fallback.write(line.getBytes(StandardCharsets.UTF_8));
                        fallback.write('\n');
                    }
                    fallback.getFD().sync();
                }
                temporary.delete();
            }
        } catch (Exception ignored) {}
    }

    private static File queueFile(Context context) {
        return new File(new File(context.getFilesDir(), QUEUE_DIR), QUEUE_FILE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void saveStatus(Context context, String status, boolean success) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_LAST_STATUS, scrubString(status));
        if (success) editor.putLong(KEY_LAST_UPLOAD, System.currentTimeMillis());
        editor.apply();
    }

    private static JSONObject sanitizeObject(JSONObject source) {
        JSONObject clean = new JSONObject();
        if (source == null) return clean;
        Iterator<String> keys = source.keys();
        int accepted = 0;
        while (keys.hasNext() && accepted < 24) {
            String key = keys.next();
            if (key == null || BANNED_KEY.matcher(key).matches()) continue;
            String safeKey = safeToken(key, 48, "field");
            Object value = source.opt(key);
            try {
                if (value instanceof JSONObject) clean.put(safeKey, sanitizeObject((JSONObject) value));
                else if (value instanceof JSONArray) clean.put(safeKey, sanitizeArray((JSONArray) value));
                else if (value instanceof Number || value instanceof Boolean || value == JSONObject.NULL) clean.put(safeKey, value);
                else clean.put(safeKey, scrubString(String.valueOf(value)));
                accepted++;
            } catch (JSONException ignored) {}
        }
        return clean;
    }

    private static JSONArray sanitizeArray(JSONArray source) {
        JSONArray clean = new JSONArray();
        int count = Math.min(source.length(), 24);
        for (int index = 0; index < count; index++) {
            Object value = source.opt(index);
            if (value instanceof JSONObject) clean.put(sanitizeObject((JSONObject) value));
            else if (value instanceof JSONArray) clean.put(sanitizeArray((JSONArray) value));
            else if (value instanceof Number || value instanceof Boolean || value == JSONObject.NULL) clean.put(value);
            else clean.put(scrubString(String.valueOf(value)));
        }
        return clean;
    }

    public static String scrubString(String input) {
        if (input == null) return "";
        String clean = input.replace('\n', ' ').replace('\r', ' ').trim();
        clean = SECRET_ASSIGNMENT.matcher(clean).replaceAll("$1=[REDACTED]");
        clean = PHONE.matcher(clean).replaceAll("[PHONE_REMOVED]");
        clean = URL.matcher(clean).replaceAll("[URL_REMOVED]");
        clean = JWT_OR_LONG_SECRET.matcher(clean).replaceAll("[SECRET_REMOVED]");
        clean = clean.replaceAll("\\s{2,}", " ");
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }

    private static String installHash(Context context) {
        SharedPreferences preferences = prefs(context);
        String id = preferences.getString(KEY_INSTALL_ID, "");
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            preferences.edit().putString(KEY_INSTALL_ID, id).apply();
        }
        return shortHash("ghartv-install:" + id);
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (int index = 0; index < 16; index++) output.append(String.format(Locale.US, "%02x", bytes[index]));
            return output.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static JSONArray stackFrames(Throwable error) {
        JSONArray frames = new JSONArray();
        if (error == null) return frames;
        StackTraceElement[] trace = error.getStackTrace();
        for (int index = 0; index < Math.min(8, trace.length); index++) {
            StackTraceElement frame = trace[index];
            String value = frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            frames.put(scrubString(value));
        }
        return frames;
    }

    private static String errorFingerprint(Throwable error) {
        if (error == null) return shortHash("unknown");
        StringBuilder source = new StringBuilder(error.getClass().getName())
                .append(':').append(scrubString(readable(error)));
        StackTraceElement[] trace = error.getStackTrace();
        for (int index = 0; index < Math.min(6, trace.length); index++) {
            StackTraceElement frame = trace[index];
            source.append('|').append(frame.getClassName())
                    .append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
        }
        return shortHash(source.toString());
    }

    private static String reference(String id) {
        String compact = id == null ? UUID.randomUUID().toString() : id.replace("-", "");
        return "GH-" + compact.substring(0, Math.min(8, compact.length())).toUpperCase(Locale.US);
    }

    private static String safeToken(String value, int max, String fallback) {
        if (value == null) return fallback;
        String clean = value.trim().replaceAll("[^A-Za-z0-9 ._+/-]", "_");
        if (clean.isEmpty()) return fallback;
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    private static String networkType(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "unknown";
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null) return "offline";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "vpn";
            return "other";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String readable(Throwable error) {
        if (error == null) return "Unknown error";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static String stripTrailingSlash(String value) {
        String clean = value == null ? "" : value.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }

    private static void installCrashHandler() {
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            Context context = appContext;
            if (context != null && isEnabled(context)) {
                JSONObject attrs = data(
                        "thread", safeToken(thread == null ? "unknown" : thread.getName(), 48, "unknown"),
                        "error_type", error == null ? "Unknown" : error.getClass().getSimpleName(),
                        "message", scrubString(readable(error)),
                        "fingerprint", errorFingerprint(error),
                        "stack_frames", stackFrames(error)
                );
                appendEvent(context, buildEvent(context, null, "app_crash", attrs));
            }
            if (previousCrashHandler != null) previousCrashHandler.uncaughtException(thread, error);
        });
    }

    private static final class CollectorConfig {
        final String endpoint;
        final String ingestKey;
        final boolean enabled;

        CollectorConfig(String endpoint, String ingestKey, boolean enabled) {
            this.endpoint = endpoint;
            this.ingestKey = ingestKey;
            this.enabled = enabled;
        }
    }
}
