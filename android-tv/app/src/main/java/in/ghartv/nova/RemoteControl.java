package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Owner-message pairing and polling. Device credentials are random, encrypted by
 * Android Keystore and separate from Jio credentials. Only short text messages are
 * accepted; no remote playback, purchase, sign-out or provider action exists here.
 */
public final class RemoteControl {
    private static final String PREFS = "ghartv_remote_control";
    private static final String KEY_REGISTERED = "registered";
    private static final String KEY_PAIRED = "paired";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String SECURE_DEVICE_ID = "owner_control_device_id";
    private static final String SECURE_DEVICE_SECRET = "owner_control_device_secret";
    private static final long POLL_MS = 20_000L;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private static volatile Activity activeActivity;
    private static volatile String visibleCommand="";
    private static final Runnable POLL = new Runnable() {
        @Override public void run() {
            Activity activity = activeActivity;
            if (activity != null && !activity.isFinishing()) poll(activity);
            MAIN.postDelayed(this, POLL_MS);
        }
    };

    private RemoteControl() {}

    public static void register(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                activeActivity = activity;
                MAIN.removeCallbacks(POLL);
                MAIN.post(POLL);
            }

            @Override public void onActivityPaused(Activity activity) {
                if (activeActivity == activity) activeActivity = null;
                MAIN.removeCallbacks(POLL);
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    public static String status(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getBoolean(KEY_PAIRED, false)) {
            return "paired · " + preferences.getString(KEY_DEVICE_NAME, "GharTV");
        }
        return preferences.getBoolean(KEY_REGISTERED, false) ? "waiting for owner" : "not paired";
    }

    public static void showPairing(Activity activity) {
        final String code = String.format(Locale.US, "%06d", 100000 + RANDOM.nextInt(900000));
        TextView message = TvUi.label(activity,
                "Pairing code\n\n" + code + "\n\nCreating a private device connection…",
                22, TvUi.TEXT, true);
        message.setPadding(TvUi.dp(activity, 28), TvUi.dp(activity, 18),
                TvUi.dp(activity, 28), TvUi.dp(activity, 18));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Pair this television with the owner page")
                .setView(message)
                .setMessage("Enter this six-digit code on the private GharTV owner page. It expires after 10 minutes. This does not share the Jio phone number, OTP or session.")
                .setNegativeButton("Close", null)
                .create();
        dialog.show();

        IO.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", deviceId(activity));
                body.put("device_secret", deviceSecret(activity));
                body.put("pairing_code", code);
                body.put("app_version", BuildConfig.VERSION_NAME);
                body.put("version_code", BuildConfig.VERSION_CODE);
                Request request = new Request.Builder()
                        .url(endpoint("/v1/device/register"))
                        .header("X-GharTV-Ingest-Key", AppConfig.TELEMETRY_FALLBACK_INGEST_KEY)
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code());
                    JSONObject result = new JSONObject(response.body() == null ? "{}" : response.body().string());
                    prefs(activity).edit()
                            .putBoolean(KEY_REGISTERED, true)
                            .putBoolean(KEY_PAIRED, result.optBoolean("paired", false))
                            .apply();
                    MAIN.post(() -> {
                        if (!dialog.isShowing()) return;
                        message.setText("Pairing code\n\n" + code
                                + "\n\nReady. Enter this code on the private owner page.");
                    });
                }
            } catch (Exception error) {
                Telemetry.error(activity, "owner_pairing_register", error, Telemetry.data());
                MAIN.post(() -> {
                    if (!dialog.isShowing()) return;
                    message.setText("Pairing is not available yet.\n\nCheck the connection and try again.");
                });
            }
        });
    }

    private static void poll(Activity activity) {
        if (!prefs(activity).getBoolean(KEY_REGISTERED, false) || !IN_FLIGHT.compareAndSet(false, true)) return;
        IO.execute(() -> {
            try {
                String id = deviceId(activity);
                Request request = new Request.Builder()
                        .url(endpoint("/v1/device/commands?device_id=" + id))
                        .header("Authorization", "Device " + deviceSecret(activity))
                        .get()
                        .build();
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful()) return;
                    JSONObject body = new JSONObject(response.body() == null ? "{}" : response.body().string());
                    boolean paired = body.optBoolean("paired", false);
                    prefs(activity).edit().putBoolean(KEY_PAIRED, paired).apply();
                    JSONArray commands = body.optJSONArray("commands");
                    if (!paired || commands == null || commands.length() == 0) return;
                    JSONObject command = commands.optJSONObject(0);
                    if (command == null || !"message".equals(command.optString("type"))) return;
                    JSONObject payload = command.optJSONObject("payload");
                    String text = payload == null ? "" : Telemetry.scrubString(payload.optString("message", ""));
                    if (text.isEmpty()) return;
                    MAIN.post(() -> showMessage(activity, command.optString("id", ""), text));
                }
            } catch (Exception error) {
                Telemetry.error(activity, "owner_command_poll", error, Telemetry.data());
            } finally {
                IN_FLIGHT.set(false);
            }
        });
    }

    private static void showMessage(Activity activity, String commandId, String text) {
        if (activity.isFinishing() || activity.isDestroyed() || activeActivity!=activity || commandId.isEmpty()) return;
        if(commandId.equals(visibleCommand)||prefs(activity).getString("last_shown", "").equals(commandId)) {acknowledge(activity,commandId);return;}
        if(!visibleCommand.isEmpty())return;
        visibleCommand=commandId;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Message from Amrit")
                .setMessage(text)
                .setPositiveButton("OK", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        dialog.setOnDismissListener(d->visibleCommand="");
        dialog.show();
        prefs(activity).edit().putString("last_shown",commandId).apply();
        acknowledge(activity, commandId);
        Telemetry.event(activity, "owner_message_shown", Telemetry.data("message_length", text.length()));
    }

    private static void acknowledge(Context context, String commandId) {
        IO.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", deviceId(context));
                body.put("command_id", commandId);
                Request request = new Request.Builder()
                        .url(endpoint("/v1/device/commands/ack"))
                        .header("Authorization", "Device " + deviceSecret(context))
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response ignored = CLIENT.newCall(request).execute()) {}
            } catch (Exception ignored) {}
        });
    }

    private static String deviceId(Context context) throws Exception {
        String value = SecureStore.get(context, SECURE_DEVICE_ID);
        if (!value.isEmpty()) return value;
        value = randomHex(16);
        SecureStore.put(context, SECURE_DEVICE_ID, value);
        return value;
    }

    private static String deviceSecret(Context context) throws Exception {
        String value = SecureStore.get(context, SECURE_DEVICE_SECRET);
        if (!value.isEmpty()) return value;
        value = randomHex(32);
        SecureStore.put(context, SECURE_DEVICE_SECRET, value);
        return value;
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder output = new StringBuilder(bytes * 2);
        for (byte item : value) output.append(String.format(Locale.US, "%02x", item & 0xff));
        return output.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String endpoint(String path) {
        String base = AppConfig.TELEMETRY_FALLBACK_ENDPOINT;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }
}
