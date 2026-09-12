package in.ghartv.nova;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

/** Starts only our own activity. Never opens the TV-wide Assistant/search UI. */
public final class VoiceSearchController {
    public static final int REQUEST_CODE = 5403;
    private VoiceSearchController() {}
    public static boolean launch(Activity activity) {
        try {
            activity.startActivityForResult(new Intent(activity, VoiceSearchActivity.class), REQUEST_CODE);
            Telemetry.event(activity, "voice_search_opened", Telemetry.data("scope", "ghartv_only"));
            return true;
        } catch (RuntimeException error) {
            Toast.makeText(activity, "Use GharTV text search on this TV.", Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
