package in.ghartv.nova;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Consent-gated foreground time measurement. Reports rounded active seconds only;
 * no channel/program identity is attached.
 */
public final class EngagementTracker {
    private static final long HEARTBEAT_MS = 5L * 60L * 1000L;
    private static final Map<Activity, Session> SESSIONS = new WeakHashMap<>();

    private EngagementTracker() {}

    public static synchronized void start(Activity activity, String screen) {
        if (activity == null || !Telemetry.isEnabled(activity)) return;
        if (SESSIONS.containsKey(activity)) return;
        Session session = new Session(activity, screen == null ? "unknown" : screen);
        SESSIONS.put(activity, session);
        session.start();
    }

    public static synchronized void stop(Activity activity) {
        Session session = SESSIONS.remove(activity);
        if (session != null) session.stop();
    }

    private static final class Session {
        final Activity activity;
        final String screen;
        final Handler handler = new Handler(Looper.getMainLooper());
        long lastMark = SystemClock.elapsedRealtime();
        boolean running = true;

        final Runnable heartbeat = new Runnable() {
            @Override public void run() {
                if (!running) return;
                emit(false);
                handler.postDelayed(this, HEARTBEAT_MS);
            }
        };

        Session(Activity activity, String screen) {
            this.activity = activity;
            this.screen = screen;
        }

        void start() {
            Telemetry.event(activity, "session_start", Telemetry.data("screen", screen));
            handler.postDelayed(heartbeat, HEARTBEAT_MS);
        }

        void stop() {
            if (!running) return;
            running = false;
            handler.removeCallbacks(heartbeat);
            emit(true);
            Telemetry.event(activity, "session_end", Telemetry.data("screen", screen));
        }

        void emit(boolean finalSlice) {
            long now = SystemClock.elapsedRealtime();
            long elapsed = Math.max(0L, now - lastMark);
            lastMark = now;
            long seconds = Math.max(0L, Math.min(300L, Math.round(elapsed / 30_000d) * 30L));
            if (seconds < 5L && finalSlice) return;
            Telemetry.event(activity, "active_time", Telemetry.data(
                    "screen", screen,
                    "active_seconds", seconds,
                    "final_slice", finalSlice
            ));
        }
    }
}
