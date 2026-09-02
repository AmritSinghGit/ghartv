package in.ghartv.nova;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LoginActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private JioApiClient api;
    private EditText mobile;
    private EditText otp;
    private Button send;
    private Button verify;
    private Button changeNumber;
    private TextView step;
    private TextView status;
    private TextView otpDestination;
    private ProgressBar progress;
    private boolean otpSent;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TvUi.immersive(this);
        api = new JioApiClient(this);
        setContentView(buildUi());
        Telemetry.screen(this, "login");
        mainHandler.postDelayed(() -> Telemetry.maybeRequestConsent(this), 900L);

        JioSession existing = JioSession.load(this);
        if (!existing.mobile.isEmpty()) mobile.setText(existing.mobile);
        mobile.requestFocus();
        executor.execute(api::prewarm);
    }

    @Override protected void onResume() {
        super.onResume();
        TvUi.immersive(this);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(TvUi.BG);
        root.addView(new AuroraBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setPadding(TvUi.dp(this, 44), TvUi.dp(this, 34), TvUi.dp(this, 44), TvUi.dp(this, 34));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        shell.addView(buildStory(), new LinearLayout.LayoutParams(0, -1, .52f));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(0, -1, .48f);
        panelParams.leftMargin = TvUi.dp(this, 34);
        shell.addView(buildLoginPanel(), panelParams);
        return root;
    }

    private View buildStory() {
        LinearLayout story = new LinearLayout(this);
        story.setOrientation(LinearLayout.VERTICAL);
        story.setGravity(Gravity.CENTER_VERTICAL);
        story.setPadding(TvUi.dp(this, 4), 0, TvUi.dp(this, 14), 0);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = TvUi.label(this, "GHAR TV", 23, TvUi.TEXT, true);
        brand.setLetterSpacing(.14f);
        brandRow.addView(brand, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 40)));
        TextView jio = TvUi.badge(this, "JIO LIVE", TvUi.MINT);
        LinearLayout.LayoutParams jioParams = new LinearLayout.LayoutParams(-2, TvUi.dp(this, 28));
        jioParams.leftMargin = TvUi.dp(this, 16);
        brandRow.addView(jio, jioParams);
        story.addView(brandRow, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 44)));

        TextView title = TvUi.label(this, "Live television, built for your remote.", 38, TvUi.TEXT, true);
        title.setMaxLines(3);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = TvUi.dp(this, 18);
        story.addView(title, titleParams);

        TextView body = TvUi.label(this,
                "Connect the Jio mobile number linked to your account. GharTV then becomes your live channel guide and player — one screen, one remote, no YouTube shortcuts.",
                15, TvUi.MUTED, false);
        body.setMaxLines(5);
        body.setLineSpacing(TvUi.dp(this, 3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = TvUi.dp(this, 16);
        story.addView(body, bodyParams);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(TvUi.dp(this, 20), TvUi.dp(this, 16), TvUi.dp(this, 20), TvUi.dp(this, 16));
        preview.setBackground(TvUi.gradient(Color.argb(222, 6, 23, 35), Color.argb(224, 8, 47, 58),
                26, Color.argb(100, 115, 245, 194), 1, this));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 188));
        previewParams.topMargin = TvUi.dp(this, 24);
        story.addView(preview, previewParams);

        LinearLayout liveRow = new LinearLayout(this);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        liveRow.addView(TvUi.badge(this, "● LIVE", TvUi.MINT), new LinearLayout.LayoutParams(-2, TvUi.dp(this, 28)));
        liveRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView channelNumber = TvUi.label(this, "CH 101", 16, TvUi.CYAN, true);
        liveRow.addView(channelNumber, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 28)));
        preview.addView(liveRow, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 30)));

        TextView now = TvUi.label(this, "Your family’s live channels", 24, TvUi.TEXT, true);
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 42));
        nowParams.topMargin = TvUi.dp(this, 12);
        preview.addView(now, nowParams);
        TextView next = TvUi.label(this, "Channel numbers  •  CH ±  •  GUIDE  •  Favourites", 13, TvUi.MUTED, false);
        preview.addView(next, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 28)));

        LinearLayout rail = new LinearLayout(this);
        rail.setGravity(Gravity.CENTER_VERTICAL);
        String[] items = {"NEWS", "HINDI", "PUNJABI", "MOVIES"};
        for (String item : items) {
            TextView tile = TvUi.label(this, item, 11, TvUi.TEXT, true);
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(TvUi.rounded(Color.argb(105, 255, 255, 255), 14, Color.argb(45, 255, 255, 255), 1, this));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, TvUi.dp(this, 42), 1f);
            tileParams.rightMargin = TvUi.dp(this, 8);
            rail.addView(tile, tileParams);
        }
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 44));
        railParams.topMargin = TvUi.dp(this, 10);
        preview.addView(rail, railParams);

        TextView hints = TvUi.label(this,
                "Use the TV remote for daily viewing. The Google TV phone remote makes the one-time number and OTP entry easier.",
                12, Color.rgb(132, 161, 179), false);
        hints.setMaxLines(2);
        LinearLayout.LayoutParams hintsParams = new LinearLayout.LayoutParams(-1, -2);
        hintsParams.topMargin = TvUi.dp(this, 18);
        story.addView(hints, hintsParams);
        return story;
    }

    private View buildLoginPanel() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(TvUi.dp(this, 30), TvUi.dp(this, 24), TvUi.dp(this, 30), TvUi.dp(this, 22));
        card.setBackground(TvUi.gradient(Color.argb(244, 6, 18, 29), Color.argb(242, 8, 31, 43),
                30, Color.argb(110, 83, 228, 255), 1.3f, this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        step = TvUi.badge(this, "1  CONNECT", TvUi.MINT);
        top.addView(step, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 30)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView secure = TvUi.label(this, "PRIVATE ON THIS TV", 11, TvUi.CYAN, true);
        secure.setLetterSpacing(.07f);
        top.addView(secure, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 30)));
        card.addView(top, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 32)));

        TextView heading = TvUi.label(this, "Connect JioTV", 30, TvUi.TEXT, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 48));
        headingParams.topMargin = TvUi.dp(this, 12);
        card.addView(heading, headingParams);

        TextView explainer = TvUi.label(this,
                "Enter the 10-digit mobile number that receives your Jio OTP.",
                15, TvUi.MUTED, false);
        card.addView(explainer, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 34)));

        mobile = field("10-digit Jio mobile number", InputType.TYPE_CLASS_PHONE);
        mobile.setId(View.generateViewId());
        mobile.setImeOptions(EditorInfo.IME_ACTION_GO);
        mobile.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                sendOtp();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams mobileParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 60));
        mobileParams.topMargin = TvUi.dp(this, 12);
        card.addView(mobile, mobileParams);

        send = TvUi.button(this, "Send OTP", true);
        send.setId(View.generateViewId());
        send.setOnClickListener(view -> sendOtp());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 56));
        sendParams.topMargin = TvUi.dp(this, 12);
        card.addView(send, sendParams);

        otpDestination = TvUi.label(this, "", 13, TvUi.MINT, true);
        otpDestination.setVisibility(View.GONE);
        LinearLayout.LayoutParams destinationParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 26));
        destinationParams.topMargin = TvUi.dp(this, 10);
        card.addView(otpDestination, destinationParams);

        otp = field("Enter OTP", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        otp.setId(View.generateViewId());
        otp.setImeOptions(EditorInfo.IME_ACTION_DONE);
        otp.setVisibility(View.GONE);
        otp.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                verifyOtp();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams otpParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 60));
        otpParams.topMargin = TvUi.dp(this, 8);
        card.addView(otp, otpParams);

        verify = TvUi.button(this, "Verify and open live TV", true);
        verify.setId(View.generateViewId());
        verify.setVisibility(View.GONE);
        verify.setOnClickListener(view -> verifyOtp());
        LinearLayout.LayoutParams verifyParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 58));
        verifyParams.topMargin = TvUi.dp(this, 12);
        card.addView(verify, verifyParams);

        changeNumber = TvUi.button(this, "Change number", false);
        changeNumber.setId(View.generateViewId());
        changeNumber.setVisibility(View.GONE);
        changeNumber.setOnClickListener(view -> resetToMobile());
        LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 44));
        changeParams.topMargin = TvUi.dp(this, 8);
        card.addView(changeNumber, changeParams);

        mobile.setNextFocusDownId(send.getId());
        send.setNextFocusUpId(mobile.getId());
        send.setNextFocusDownId(otp.getId());
        otp.setNextFocusUpId(send.getId());
        otp.setNextFocusDownId(verify.getId());
        verify.setNextFocusUpId(otp.getId());
        verify.setNextFocusDownId(changeNumber.getId());
        changeNumber.setNextFocusUpId(verify.getId());

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        progress.setIndeterminateTintList(TvUi.tint(TvUi.MINT));
        statusRow.addView(progress, new LinearLayout.LayoutParams(TvUi.dp(this, 34), TvUi.dp(this, 34)));
        status = TvUi.label(this, "OTP is verified by Jio and is never stored by GharTV.", 13, TvUi.MUTED, false);
        status.setMaxLines(3);
        LinearLayout.LayoutParams statusText = new LinearLayout.LayoutParams(0, -1, 1f);
        statusText.leftMargin = TvUi.dp(this, 12);
        statusRow.addView(status, statusText);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        statusParams.topMargin = TvUi.dp(this, 14);
        card.addView(statusRow, statusParams);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView version = TvUi.label(this, "TV TEST CANDIDATE  •  " + BuildConfig.VERSION_NAME, 11, Color.rgb(117, 149, 167), true);
        footer.addView(version, new LinearLayout.LayoutParams(0, TvUi.dp(this, 36), 1f));
        Button updates = TvUi.button(this, "Check update", false);
        updates.setOnClickListener(view -> UpdateManager.check(this, true));
        footer.addView(updates, new LinearLayout.LayoutParams(TvUi.dp(this, 128), TvUi.dp(this, 38)));
        card.addView(footer, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 40)));
        return card;
    }

    private EditText field(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setHintTextColor(Color.rgb(111, 145, 166));
        edit.setTextColor(TvUi.TEXT);
        edit.setTextSize(19);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setSelectAllOnFocus(false);
        edit.setPadding(TvUi.dp(this, 20), 0, TvUi.dp(this, 20), 0);
        TvUi.focusCard(edit, Color.rgb(8, 29, 43), Color.rgb(14, 69, 82), 18);
        return edit;
    }

    private String normalizedMobile() {
        String digits = mobile.getText().toString().replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) digits = digits.substring(2);
        return digits;
    }

    private String maskedMobile(String number) {
        if (number.length() != 10) return number;
        return "+91 •••••• " + number.substring(6);
    }

    private void busy(boolean value, String message) {
        send.setEnabled(!value);
        verify.setEnabled(!value);
        changeNumber.setEnabled(!value);
        mobile.setEnabled(!value);
        otp.setEnabled(!value);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        status.setText(message);
        status.setTextColor(value ? TvUi.CYAN : TvUi.MUTED);
    }

    private void revealOtp() {
        otpSent = true;
        step.setText("2  VERIFY");
        send.setText("Resend OTP");
        mobile.setVisibility(View.GONE);
        send.setVisibility(View.GONE);
        otpDestination.setText("OTP sent to " + maskedMobile(normalizedMobile()));
        otpDestination.setVisibility(View.VISIBLE);
        otp.setVisibility(View.VISIBLE);
        verify.setVisibility(View.VISIBLE);
        changeNumber.setVisibility(View.VISIBLE);
        otp.requestFocus();
    }

    private void resetToMobile() {
        otpSent = false;
        step.setText("1  CONNECT");
        mobile.setVisibility(View.VISIBLE);
        send.setVisibility(View.VISIBLE);
        send.setText("Send OTP");
        otpDestination.setVisibility(View.GONE);
        otp.setText("");
        otp.setVisibility(View.GONE);
        verify.setVisibility(View.GONE);
        changeNumber.setVisibility(View.GONE);
        status.setText("OTP is verified by Jio and is never stored by GharTV.");
        status.setTextColor(TvUi.MUTED);
        mobile.requestFocus();
    }

    private void sendOtp() {
        String number = normalizedMobile();
        if (number.length() != 10 || number.charAt(0) < '6') {
            status.setText("Enter a valid 10-digit Indian mobile number.");
            status.setTextColor(TvUi.ERROR);
            mobile.requestFocus();
            return;
        }
        busy(true, "Requesting OTP from Jio…");
        long startedAt = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                api.sendOtp(number);
                Telemetry.event(this, "otp_send", Telemetry.data(
                        "result", "success",
                        "duration_ms", System.currentTimeMillis() - startedAt));
                mainHandler.post(() -> {
                    busy(false, "OTP sent. Enter the code received from Jio.");
                    status.setTextColor(TvUi.MINT);
                    revealOtp();
                });
            } catch (Exception error) {
                String reference = Telemetry.error(this, "otp_send", error, Telemetry.data(
                        "result", "failure",
                        "duration_ms", System.currentTimeMillis() - startedAt));
                mainHandler.post(() -> {
                    busy(false, readable(error) + (Telemetry.isEnabled(this) ? "  •  " + reference : ""));
                    status.setTextColor(TvUi.ERROR);
                    send.requestFocus();
                });
            }
        });
    }

    private void verifyOtp() {
        String number = normalizedMobile();
        String code = otp.getText().toString().replaceAll("[^0-9]", "");
        if (!otpSent || code.length() < 4) {
            Toast.makeText(this, "Enter the OTP received from Jio", Toast.LENGTH_SHORT).show();
            otp.requestFocus();
            return;
        }
        busy(true, "Verifying JioTV…");
        long startedAt = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                api.verifyOtp(number, code);
                Telemetry.event(this, "otp_verify", Telemetry.data(
                        "result", "success",
                        "duration_ms", System.currentTimeMillis() - startedAt));
                mainHandler.post(() -> {
                    status.setText("Connected. Opening live television…");
                    status.setTextColor(TvUi.MINT);
                    Intent main = new Intent(this, MainActivity.class);
                    main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(main);
                    finish();
                });
            } catch (Exception error) {
                String reference = Telemetry.error(this, "otp_verify", error, Telemetry.data(
                        "result", "failure",
                        "duration_ms", System.currentTimeMillis() - startedAt));
                mainHandler.post(() -> {
                    busy(false, readable(error) + (Telemetry.isEnabled(this) ? "  •  " + reference : ""));
                    status.setTextColor(TvUi.ERROR);
                    otp.requestFocus();
                });
            }
        });
    }

    private String readable(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    @Override public void onBackPressed() {
        if (otpSent) resetToMobile();
        else super.onBackPressed();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if ((key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER) && otp.hasFocus() && otpSent) {
                verifyOtp();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
