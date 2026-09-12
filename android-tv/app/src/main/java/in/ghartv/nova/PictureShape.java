package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/** Per-channel rendering preference. Neither a source upscale nor DRM processing. */
@UnstableApi
public final class PictureShape {
    private PictureShape() {}
    private static final int[] MODES={AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,AspectRatioFrameLayout.RESIZE_MODE_FILL};
    private static SharedPreferences prefs(Activity a) { return a.getSharedPreferences(AppConfig.PREFS,Activity.MODE_PRIVATE); }
    private static String key(String channel) { return "picture_shape_channel_"+channel; }
    private static int selected(Activity a,String channel) {
        if(channel==null||channel.isEmpty())return 0;
        return Math.max(0,Math.min(2,prefs(a).getInt(key(channel),0)));
    }
    public static void apply(Activity a,PlayerView view,String channel) {
        if(view==null)return;
        int resize=MODES[selected(a,channel)];
        if(view.getResizeMode()!=resize)view.setResizeMode(resize);
    }
    public static void show(Activity a,PlayerView view,String channel) {
        if(channel==null||channel.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("Choose a channel first").setPositiveButton("Close",null).show();return;
        }
        new AlertDialog.Builder(a).setTitle("Picture shape · saved for this channel")
            .setSingleChoiceItems(new String[]{"Original / Fit — whole picture, correct proportions", "Zoom / Crop — fills screen; may cut logos or captions", "Stretch to screen — fills 16:9; objects become wider"},selected(a,channel),
                (dialog,which)->{prefs(a).edit().putInt(key(channel),which).apply();apply(a,view,channel);})
            .setPositiveButton("Done",(d,w)->TvUi.immersive(a))
            .setNeutralButton("Reset to Original",(d,w)->{prefs(a).edit().remove(key(channel)).apply();apply(a,view,channel);TvUi.immersive(a);})
            .setNegativeButton("What changes?",(d,w)->new AlertDialog.Builder(a).setTitle("Shape is not source quality")
                .setMessage("A 4:3 picture can fill a 16:9 screen by cropping about 25% of its original height, or by becoming about 33% wider. Neither adds HD/4K detail. Bars encoded inside the source video are not automatically detected or removed. The preference stays on this TV and is not sent to diagnostics.")
                .setPositiveButton("Back",(x,y)->show(a,view,channel)).show()).show();
    }
}
