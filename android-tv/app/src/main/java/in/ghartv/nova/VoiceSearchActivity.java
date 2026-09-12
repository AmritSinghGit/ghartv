package in.ghartv.nova;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/** One explicit, bounded microphone interaction; text/audio never enters telemetry. */
public final class VoiceSearchActivity extends Activity implements RecognitionListener {
    private static final int AUDIO_PERMISSION = 551;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextView status;
    private EditText input;
    private Button listen;
    private boolean listening;
    private final Runnable timeout = () -> fail("Listening timed out. Try again or type below.");

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        TvUi.immersive(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(TvUi.dp(this, 72), TvUi.dp(this, 30), TvUi.dp(this, 72), TvUi.dp(this, 30));
        root.setBackgroundColor(TvUi.BG);
        root.addView(TvUi.label(this, "Search GharTV only", 30, TvUi.TEXT, true));
        root.addView(TvUi.label(this, "Channels and programmes inside this app — not the whole television.", 16, TvUi.MUTED, false));
        status = TvUi.label(this, "Microphone starts only when you select Start listening.", 17, TvUi.CYAN, false);
        root.addView(status, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 65)));
        input = new EditText(this);
        input.setSingleLine(true); input.setHint("Type a channel, number or programme");
        input.setTextColor(TvUi.TEXT); input.setHintTextColor(TvUi.MUTED);
        root.addView(input, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 54)));
        listen = TvUi.button(this, "Start listening", true);
        listen.setOnClickListener(v -> requestListening()); root.addView(listen);
        Button search = TvUi.button(this, "Search typed text", false);
        search.setOnClickListener(v -> complete(input.getText().toString())); root.addView(search);
        Button cancel = TvUi.button(this, "Back to GharTV", false);
        cancel.setOnClickListener(v -> finish()); root.addView(cancel);
        root.addView(TvUi.label(this, "On-device recognition is preferred when available. Otherwise your configured speech provider may process audio remotely. GharTV does not save the recording or upload your words to diagnostics.", 13, TvUi.MUTED, false));
        setContentView(root); listen.requestFocus();
    }

    private void requestListening() {
        if (listening) { stop(); status.setText("Stopped. Select Start listening to try again."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION); return;
        }
        startListening();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != AUDIO_PERMISSION || isFinishing()) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
        else fail("Microphone permission was not granted. Text search still works.");
    }
    private void startListening() {
        stop();
        try {
            boolean local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
            if (!local && !SpeechRecognizer.isRecognitionAvailable(this)) {
                fail("This TV has no speech recognition service. Use text search."); return;
            }
            recognizer = local ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this) : SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            request.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            listening = true; listen.setText("Stop listening");
            status.setText(local ? "Listening on this device…" : "Listening with the configured speech provider…");
            handler.postDelayed(timeout, 15000); recognizer.startListening(request);
        } catch (RuntimeException e) { fail("Speech recognition could not start. Use text search or retry."); }
    }
    private void stop() {
        handler.removeCallbacks(timeout); listening = false;
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (RuntimeException ignored) {}
            recognizer = null;
        }
        if (listen != null) listen.setText("Start listening");
    }
    private void fail(String message) { stop(); if (status != null) status.setText(message); }
    private void complete(String text) {
        String query = text == null ? "" : text.trim();
        if (query.isEmpty()) { fail("No words received. Try again or type your search."); return; }
        stop(); ArrayList<String> values = new ArrayList<>(); values.add(query);
        setResult(RESULT_OK, new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, values));
        finish();
    }
    @Override protected void onPause() { stop(); super.onPause(); }
    @Override protected void onDestroy() { stop(); super.onDestroy(); }
    @Override public void onResults(Bundle results) {
        if (!listening || isFinishing()) return;
        ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        complete(values == null || values.isEmpty() ? "" : values.get(0));
    }
    @Override public void onError(int error) {
        if (listening) fail("Recognition failed (" + error + "). Retry or use text search.");
    }
    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {} // Deliberately not retained.
    @Override public void onEndOfSpeech() { if (listening) status.setText("Recognising your search…"); }
    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}
}
