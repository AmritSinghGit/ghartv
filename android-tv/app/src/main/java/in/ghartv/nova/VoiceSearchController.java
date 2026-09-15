package in.ghartv.nova;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import java.util.Locale;

public final class VoiceSearchController {
    public static final int REQUEST_CODE = 5403;

    private VoiceSearchController() {}

    public static boolean launch(Activity activity) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a channel, number or programme");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            activity.startActivityForResult(intent, REQUEST_CODE);
            Telemetry.event(activity, "voice_search_opened", Telemetry.data("available", true));
            return true;
        } catch (ActivityNotFoundException error) {
            Telemetry.error(activity, "voice_search_unavailable", error, Telemetry.data("available", false));
            Toast.makeText(activity, "Voice search is not available on this TV. Opening text search.", Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
