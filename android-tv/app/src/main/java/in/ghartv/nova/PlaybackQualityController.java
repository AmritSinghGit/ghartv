package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Extends the existing player's panel; no second player, stream proxy or decoder. */
@UnstableApi
public final class PlaybackQualityController {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private PlayerView view;
    private ExoPlayer observed;
    private TextView details;
    private boolean active;
    private boolean buttonAdded;
    private long sampledAt;
    private long dropped;
    private long bandwidth = -1;
    private long attachedAt;
    private long firstFrameMs = -1;
    private int mode;
    private final AnalyticsListener listener = new AnalyticsListener() {
        @Override public void onDroppedVideoFrames(EventTime time, int count, long elapsedMs) { dropped += Math.max(0, count); }
        @Override public void onBandwidthEstimate(EventTime time, int duration, long bytes, long estimate) { if (estimate > 0) bandwidth = estimate; }
        @Override public void onRenderedFirstFrame(EventTime time, Object output, long renderTimeMs) {
            if (firstFrameMs < 0) firstFrameMs = Math.max(0, renderTimeMs - attachedAt);
        }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active || activity.isFinishing()) return;
            try {
                if (view == null) view = findPlayer(activity.findViewById(android.R.id.content));
                if (!buttonAdded) addPanelButton();
                Player current = view == null ? null : view.getPlayer();
                if (current != observed) {
                    disconnect();
                    if (current instanceof ExoPlayer) {
                        observed = (ExoPlayer) current; dropped = 0; bandwidth = -1; firstFrameMs = -1;
                        attachedAt = SystemClock.elapsedRealtime(); sampledAt = attachedAt;
                        observed.addAnalyticsListener(listener); apply();
                    }
                }
                PictureShape.apply(activity, view, ((PlayerActivity) activity).pictureChannelId());
                if (details != null) details.setText(description());
                long now = SystemClock.elapsedRealtime();
                if (observed != null && now - sampledAt >= 60000) {
                    // Opt-in diagnostics only; no channel/programme identity or stream URL.
                    JSONObject sample = Telemetry.data("quality_mode", mode, "dropped_frames", dropped,
                        "observation_ms", now - sampledAt, "is_playing", observed.isPlaying(),
                        "buffer_ahead_ms", observed.getTotalBufferedDuration());
                    Format f = observed.getVideoFormat();
                    if (f != null) {
                        if (f.width > 0) sample.put("source_width", f.width);
                        if (f.height > 0) sample.put("source_height", f.height);
                        if (f.averageBitrate > 0) sample.put("declared_video_bps", f.averageBitrate);
                    }
                    if (bandwidth > 0) sample.put("bandwidth_estimate_bps", bandwidth);
                    Telemetry.event(activity, "playback_quality_sample", sample);
                    Telemetry.enqueueUpload(activity, false);
                    dropped = 0; sampledAt = now;
                }
            } catch (Exception ignored) { // A channel switch may release the previous player during inspection.
                if (details != null) details.setText("Player changed. Waiting for the next sample…");
            }
            handler.postDelayed(this, 1000);
        }
    };
    private PlaybackQualityController(Activity activity) {
        this.activity = activity; prefs = activity.getSharedPreferences(AppConfig.PREFS, Activity.MODE_PRIVATE);
        mode = Math.max(0, Math.min(2, prefs.getInt("picture_quality_mode", 0)));
    }
    public static void register(Application application) {
        Map<Activity, PlaybackQualityController> controllers = new IdentityHashMap<>();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) {
                if (!(a instanceof PlayerActivity)) return;
                PlaybackQualityController c = controllers.get(a);
                if (c == null) { c = new PlaybackQualityController(a); controllers.put(a, c); }
                c.active = true; c.handler.removeCallbacks(c.tick); c.handler.post(c.tick);
            }
            @Override public void onActivityPaused(Activity a) {
                PlaybackQualityController c = controllers.get(a);
                if (c != null) { c.active = false; c.handler.removeCallbacks(c.tick); c.disconnect(); }
            }
            @Override public void onActivityDestroyed(Activity a) {
                PlaybackQualityController c = controllers.remove(a);
                if (c != null) { c.active = false; c.handler.removeCallbacksAndMessages(null); c.disconnect(); c.details = null; }
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        });
    }
    private void disconnect() {
        if (observed != null) { try { observed.removeAnalyticsListener(listener); } catch (RuntimeException ignored) {} }
        observed = null;
    }
    private static PlayerView findPlayer(View root) {
        if (root instanceof PlayerView) return (PlayerView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                PlayerView found = findPlayer(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
    private static Button findGuide(View root) {
        if (root instanceof Button && "Guide".contentEquals(((Button) root).getText())) return (Button) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findGuide(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
    private void addPanelButton() {
        // The canonical PlayerActivity places its Guide action in this row.
        // Fail closed on another layout rather than overlaying permanent controls.
        Button guide = findGuide(activity.findViewById(android.R.id.content));
        if (guide == null || !(guide.getParent() instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) guide.getParent();
        if (row.getOrientation() != LinearLayout.HORIZONTAL) return;
        Button button = TvUi.button(activity, "Picture", false);
        button.setOnClickListener(v -> showModes());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(TvUi.dp(activity, 94), TvUi.dp(activity, 32));
        params.rightMargin = TvUi.dp(activity, 6);
        row.addView(button, Math.max(0, row.indexOfChild(guide) - 1), params); buttonAdded = true;
    }
    private void apply() {
        PictureShape.apply(activity, view, ((PlayerActivity) activity).pictureChannelId());
        if (observed == null || !observed.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return;
        TrackSelectionParameters.Builder b = observed.getTrackSelectionParameters().buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).setForceLowestBitrate(false)
            .setForceHighestSupportedBitrate(mode == 1)
            .setMaxVideoSize(mode == 2 ? 854 : Integer.MAX_VALUE, mode == 2 ? 480 : Integer.MAX_VALUE)
            .setMaxVideoBitrate(mode == 2 ? 1000000 : Integer.MAX_VALUE);
        observed.setTrackSelectionParameters(b.build());
    }
    private void showModes() {
        new AlertDialog.Builder(activity).setTitle("Picture quality · GharTV")
            .setSingleChoiceItems(new String[]{"Auto — adaptive; recommended", "Highest supported source — may buffer", "Data saver — prefer 480p / 1 Mbps"}, mode,
                (d, which) -> { mode = which; prefs.edit().putInt("picture_quality_mode", mode).apply(); apply(); })
            .setPositiveButton("Done", (d,w) -> TvUi.immersive(activity))
            .setNeutralButton("Live picture stats", (d,w) -> showStats())
            .setNegativeButton("Picture shape", (d,w) -> showScaling()).show();
    }
    private void showScaling() {
        PictureShape.show(activity, view, ((PlayerActivity) activity).pictureChannelId());
    }
    private void showStats() {
        details = TvUi.label(activity, description(), 16, TvUi.TEXT, false);
        details.setPadding(TvUi.dp(activity, 24), TvUi.dp(activity, 18), TvUi.dp(activity, 24), TvUi.dp(activity, 18));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Live picture diagnostics").setView(details)
            .setPositiveButton("Close", (d,w) -> {}).create();
        dialog.setOnDismissListener(d -> { details = null; TvUi.immersive(activity); }); dialog.show();
    }
    private String description() {
        if (observed == null) return "Waiting for this channel's player…";
        Format f = observed.getVideoFormat();
        String source = f != null && f.width > 0 && f.height > 0 ? f.width + " × " + f.height : "Not reported";
        String declared = f != null && f.averageBitrate > 0 ? mbps(f.averageBitrate) : "Not reported";
        String display = "Not reported";
        if (view != null && view.getDisplay() != null) {
            android.view.Display.Mode m = view.getDisplay().getMode(); display = m.getPhysicalWidth() + " × " + m.getPhysicalHeight();
        }
        return "Source video: " + source + "\nTV-reported display mode: " + display
            + "\nDeclared source bitrate: " + declared + "\nEstimated media bandwidth: " + (bandwidth > 0 ? mbps(bandwidth) : "Not sampled yet")
            + "\nBuffer ahead: " + observed.getTotalBufferedDuration() + " ms"
            + "\nDropped frames in current observation window: " + dropped
            + "\nObserver attachment → first frame: " + (firstFrameMs >= 0 ? firstFrameMs + " ms" : "Not observed")
            + "\n\nStats refresh each second. These are technical samples, not a speed test or full tune-time measurement."
            + "\nFit/crop/stretch uses the existing TV rendering path. AI super-resolution is NOT implemented. Higher output resolution does not turn an SD source into native HD/4K."
            + "\nQuality preferences cannot exceed the provider's authorised source renditions or decoder capabilities.";
    }
    private static String mbps(long value) { return String.format(Locale.US, "%.2f Mbps", value / 1000000.0); }
}
