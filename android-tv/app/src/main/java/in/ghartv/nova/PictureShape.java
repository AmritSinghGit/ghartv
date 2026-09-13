package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/** Per-channel display preference. This changes framing, not source resolution. */
@UnstableApi
public final class PictureShape {
    private static final int[] MODES = {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
    };

    private PictureShape() {}

    private static SharedPreferences prefs(Activity activity) {
        return activity.getSharedPreferences(AppConfig.PREFS, Activity.MODE_PRIVATE);
    }

    private static String key(String channel) { return "picture_shape_channel_" + channel; }

    private static int selected(Activity activity, String channel) {
        if (channel == null || channel.isEmpty()) return 0;
        return Math.max(0, Math.min(2, prefs(activity).getInt(key(channel), 0)));
    }

    public static void apply(Activity activity, PlayerView view, String channel) {
        if (view == null) return;
        int resize = MODES[selected(activity, channel)];
        if (view.getResizeMode() != resize) view.setResizeMode(resize);
    }

    public static void show(Activity activity, PlayerView view, String channel) {
        if (channel == null || channel.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Choose a channel first")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Picture shape · saved for this channel")
                .setSingleChoiceItems(new String[]{
                        "Original / Fit — whole picture, correct proportions",
                        "Zoom / Crop — fills screen; may cut logos or captions",
                        "Stretch to screen — fills 16:9; objects become wider"
                }, selected(activity, channel), (dialog, which) -> {
                    prefs(activity).edit().putInt(key(channel), which).apply();
                    apply(activity, view, channel);
                    Telemetry.event(activity, "picture_shape_changed", Telemetry.data(
                            "mode", which == 0 ? "fit" : which == 1 ? "zoom" : "stretch"));
                })
                .setPositiveButton("Done", (dialog, which) -> TvUi.immersive(activity))
                .setNeutralButton("Reset to Original", (dialog, which) -> {
                    prefs(activity).edit().remove(key(channel)).apply();
                    apply(activity, view, channel);
                    TvUi.immersive(activity);
                })
                .setNegativeButton("What changes?", (dialog, which) -> new AlertDialog.Builder(activity)
                        .setTitle("Shape is not source quality")
                        .setMessage("Fit preserves a 4:3 picture with side bars. Zoom fills 16:9 by cropping. Stretch fills 16:9 by widening the picture. None of these adds detail that the provider's source does not contain; GharTV does not claim AI upscaling where it is not actually running.")
                        .setPositiveButton("Back", (innerDialog, innerWhich) -> show(activity, view, channel))
                        .show())
                .show();
    }
}
