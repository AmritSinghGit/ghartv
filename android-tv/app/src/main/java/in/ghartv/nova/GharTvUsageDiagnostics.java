package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Explicit per-session opt-in, TV-local troubleshooting summaries.
 *
 * Only allowlisted event NAMES and coarse relative seconds survive. No search
 * text, keystrokes, device/network identifiers, titles, URLs, IPs, tokens,
 * credentials, source labels, coordinates or request payloads are accepted.
 * Nothing is uploaded; all data stays in app-private preferences for <=7 days.
 * Opt-in never persists across process restart or TV/phone pairing reset.
 */
final class GharTvUsageDiagnostics {
    private static final String PREFS = "ghartv-owner-usage-v1";
    private static final String KEY = "technical_events";
    static final int RETENTION_DAYS = 7;
    private static final int MAX_TRACE = 36;
    private static final int MAX_DAYS = 7;
    private static final long DAY_MS = 86_400_000L;
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
        "CONSENT_ENABLED", "CONSENT_DISABLED",
        "PHONE_CONNECTED", "PHONE_DISCONNECTED", "PHONE_RECONNECTED",
        "PHONE_FOCUS_OK", "PHONE_FOCUS_BLOCKED", "PHONE_VOICE_FALLBACK",
        "LIVE_TYPING", "TEXT_BACKSPACE", "TEXT_CLEAR", "SEARCH_SUBMIT",
        "WEB_TEXT_SUBMIT", "SEARCH_SENT", "SEARCH_UNAVAILABLE", "FIELD_BLOCKED",
        "FIELD_REQUIRED", "EDIT_APPLIED", "EDIT_FIELD_REQUIRED", "INVALID_QUERY",
        "SOURCE_LIST", "SOURCE_SELECT", "SOURCE_UNAVAILABLE", "SOURCE_EXHAUSTED",
        "TRY_NEXT", "PLAY_REQUEST", "PAUSE_REQUEST", "FULLSCREEN_REQUEST",
        "FULLSCREEN_UNAVAILABLE", "SEEK_REQUEST", "SEEK_UNAVAILABLE",
        "PLAYBACK_PLAYING", "PLAYBACK_PAUSED", "PLAYBACK_UNKNOWN",
        "MODE_HOME", "MODE_SEARCH", "MODE_EDITOR", "MODE_VIDEO", "MODE_FULL"
    ));

    private static volatile GharTvUsageDiagnostics singleton;
    static GharTvUsageDiagnostics get(Context context) {
        if (singleton == null) synchronized (GharTvUsageDiagnostics.class) {
            if (singleton == null) singleton = new GharTvUsageDiagnostics(context.getApplicationContext());
        }
        return singleton;
    }
    private final SharedPreferences prefs;
    private JSONObject data;
    private boolean enabled;
    /** Report visibility is also limited to the same explicitly opted-in phone session. */
    private boolean sessionAuthorized;
    private long began = SystemClock.elapsedRealtime();
    private String lastEvent = "";
    private long lastAt = 0L;

    private GharTvUsageDiagnostics(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            data = new JSONObject(prefs.getString(KEY, ""));
        } catch (Exception ignored) {
            data = empty();
        }
        prune();
    }

    private static JSONObject set(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (org.json.JSONException ignored) {}
        return object;
    }
    private static JSONObject empty() {
        return set(set(set(new JSONObject(), "days", new JSONArray()),
            "trace", new JSONArray()), "trace_day", day());
    }
    private static long day() {
        return Math.max(0L, System.currentTimeMillis() / DAY_MS);
    }
    private void persist() {
        prefs.edit().putString(KEY, data.toString()).apply();
    }
    private void prune() {
        JSONArray old = data.optJSONArray("days");
        if (old == null) old = new JSONArray();
        JSONArray kept = new JSONArray();
        long today = day();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null || item.optJSONObject("counts") == null) continue;
            long d = item.optLong("day", -1);
            if (d >= today - RETENTION_DAYS + 1 && d <= today && kept.length() < MAX_DAYS)
                kept.put(item);
        }
        set(data, "days", kept);
        if (data.optLong("trace_day", -1) < today - RETENTION_DAYS + 1
            || data.optLong("trace_day", -1) > today
            || data.optJSONArray("trace") == null) {
            set(set(data, "trace", new JSONArray()), "trace_day", today);
        }
        persist();
    }
    synchronized boolean enabled() { return enabled; }
    synchronized boolean sessionAuthorized() { return sessionAuthorized; }
    synchronized void endSession() { enabled = false;sessionAuthorized = false; }
    synchronized void begin() {
        if (enabled) return;
        prune();
        enabled = true;
        sessionAuthorized = true;
        began = SystemClock.elapsedRealtime();
        lastAt = 0;
        lastEvent = "";
        set(set(data, "trace", new JSONArray()), "trace_day", day());
        record("CONSENT_ENABLED");
    }
    synchronized void stop() {
        if (!enabled) return;
        record("CONSENT_DISABLED");
        enabled = false;
    }
    synchronized void clear() {
        enabled = false;
        sessionAuthorized = false;
        data = empty();
        lastEvent = ""; lastAt = 0;
        prefs.edit().remove(KEY).apply();
    }
    synchronized void record(String event) {
        if (!enabled || event == null || !ALLOWED.contains(event)) return;
        long now = SystemClock.elapsedRealtime();
        // Do not collect every typed character, playback heartbeat or UI frame.
        long throttle = "LIVE_TYPING".equals(event) ? 6000 : 1800;
        if (event.equals(lastEvent) && now - lastAt < throttle) return;
        lastAt = now;lastEvent = event;
        prune();
        JSONArray days = data.optJSONArray("days");
        long today = day();
        JSONObject row = null;
        for (int i = 0; i < days.length(); i++) {
            JSONObject item = days.optJSONObject(i);
            if (item != null && item.optLong("day") == today) {row = item;break;}
        }
        if (row == null) {
            row = set(set(new JSONObject(), "day", today), "counts", new JSONObject());
            days.put(row);
        }
        JSONObject counts = row.optJSONObject("counts");
        set(counts, event, Math.min(10000, counts.optInt(event) + 1));
        JSONArray old = data.optJSONArray("trace");
        JSONArray trace = new JSONArray();
        for (int i = Math.max(0, old.length() - MAX_TRACE + 1); i < old.length(); i++)
            trace.put(old.optJSONObject(i));
        trace.put(set(set(new JSONObject(), "at_s",
            Math.max(0, Math.min(36000, (now - began) / 1000L))), "event", event));
        set(set(data, "trace", trace), "trace_day", today);
        persist();
    }
    synchronized JSONObject report() {
        prune();
        JSONObject totals = new JSONObject();
        JSONArray days = data.optJSONArray("days");
        int events = 0;
        for (int i = 0; i < days.length(); i++) {
            JSONObject counts = days.optJSONObject(i).optJSONObject("counts");
            java.util.Iterator<String> keys = counts.keys();
            while (keys.hasNext()) {
                String event = keys.next();
                // Validate any stored content again before displaying it.
                if (!ALLOWED.contains(event)) continue;
                int n = Math.max(0, Math.min(10000, counts.optInt(event)));
                set(totals, event, totals.optInt(event) + n);
                events += n;
            }
        }
        JSONObject result = set(set(set(set(set(set(new JSONObject(),
            "schema", "ghartv.usage-summary.v1"), "enabled", enabled),
            "retention_days", RETENTION_DAYS), "uploaded", false),
            "total", events), "counts", totals);
        set(result, "steps", new JSONArray());
        JSONArray source = data.optJSONArray("trace");
        JSONArray steps = result.optJSONArray("steps");
        for (int i = 0; i < source.length(); i++) {
            JSONObject row = source.optJSONObject(i);
            if (row != null && ALLOWED.contains(row.optString("event"))) {
                steps.put(set(set(new JSONObject(), "at_s",
                    Math.max(0, Math.min(36000, row.optLong("at_s")))),
                    "event", row.optString("event")));
            }
        }
        return result;
    }
    synchronized String summary() {
        JSONObject report = report();
        StringBuilder out = new StringBuilder("Technical diagnostics: ")
            .append(enabled ? "ON for this phone session" : "OFF")
            .append("\nStored only on this TV for up to 7 days · No automatic upload.")
            .append("\nNo search text, credentials, titles, URLs, or phone IDs recorded.")
            .append("\n\nRecorded technical actions: ").append(report.optInt("total"));
        JSONObject counts = report.optJSONObject("counts");
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        java.util.Iterator<String> keys = counts.keys();
        while (keys.hasNext()) names.add(keys.next());
        java.util.Collections.sort(names);
        for (String name : names) out.append("\n").append(name)
            .append(": ").append(counts.optInt(name));
        JSONArray steps = report.optJSONArray("steps");
        if (steps.length() > 0) {
            out.append("\n\nMost recent opted-in session (relative time):");
            for (int i = Math.max(0, steps.length() - 10); i < steps.length(); i++) {
                JSONObject row = steps.optJSONObject(i);
                if (row != null) out.append("\n+").append(row.optLong("at_s"))
                    .append("s  ").append(row.optString("event"));
            }
        }
        return out.toString();
    }
}
