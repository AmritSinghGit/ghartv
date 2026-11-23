package in.ghartv.nova;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.DisplayMetrics;
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

    /** Owner 18 Nov 15:44: the proportions of the bigger picture (960 dp wide: big title, big pad keys, air) at every TV size. A 1080p TV usually reports a small density (1440 dp wide), which made everything look small; this screen is laid out as if the TV were always 960 dp wide. */
    @Override protected void attachBaseContext(Context base) {
        DisplayMetrics metrics = base.getResources().getDisplayMetrics();
        int longSide = Math.max(metrics.widthPixels, metrics.heightPixels);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.densityDpi = Math.max(160, Math.min(640, Math.round(longSide / 6f)));
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TvUi.immersive(this);
        api = new JioApiClient(this);
        setContentView(buildUi());
        Telemetry.screen(this, "login");
        // The diagnostics question is asked after login (MainActivity), not over this screen (owner/CTO 18 Nov).

        JioSession existing = JioSession.load(this);
        if (!existing.mobile.isEmpty()) mobile.setText(existing.mobile);
        padStart.requestFocus();
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

    private Button padStart;
    private TextView heading;

    /** One screen (owner 18 Nov 14:52: keep the info on the left, direct login, fewest screens and presses). Left: brand, title, a short line, a small channel card. Right: the number or OTP, one action and the app's own pad. Nothing has a fixed height that can push content off the screen: the left column wraps its content, the pad takes the remaining height by weight. */
    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(TvUi.BG);
        root.addView(new AuroraBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setPadding(TvUi.dp(this, 44), TvUi.dp(this, 30), TvUi.dp(this, 44), TvUi.dp(this, 30));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        shell.addView(buildStory(), new LinearLayout.LayoutParams(0, -1, .46f));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(0, -1, .54f);
        panelParams.leftMargin = TvUi.dp(this, 30);
        shell.addView(buildEntry(), panelParams);
        return root;
    }

    private View buildStory() {
        LinearLayout story = new LinearLayout(this);
        story.setOrientation(LinearLayout.VERTICAL);
        story.setGravity(Gravity.CENTER_VERTICAL);
        story.setPadding(TvUi.dp(this, 4), 0, TvUi.dp(this, 10), 0);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = TvUi.label(this, "GHAR TV", 26, TvUi.TEXT, true);
        brand.setLetterSpacing(.14f);
        brandRow.addView(brand, new LinearLayout.LayoutParams(-2, -2));
        TextView jio = TvUi.badge(this, "JIO LIVE", TvUi.MINT);
        LinearLayout.LayoutParams jioParams = new LinearLayout.LayoutParams(-2, TvUi.dp(this, 28));
        jioParams.leftMargin = TvUi.dp(this, 16);
        brandRow.addView(jio, jioParams);
        story.addView(brandRow, new LinearLayout.LayoutParams(-1, -2));

        TextView title = TvUi.label(this, "Live television, built for your remote.", 34, TvUi.TEXT, true);
        title.setMaxLines(3);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = TvUi.dp(this, 16);
        story.addView(title, titleParams);

        TextView body = TvUi.label(this,
                "Connect the Jio number linked to your account. Your channel guide and player open on one screen, controlled by one remote.",
                15, TvUi.MUTED, false);
        body.setMaxLines(4);
        body.setLineSpacing(TvUi.dp(this, 3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = TvUi.dp(this, 14);
        story.addView(body, bodyParams);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(TvUi.dp(this, 18), TvUi.dp(this, 14), TvUi.dp(this, 18), TvUi.dp(this, 14));
        preview.setBackground(TvUi.gradient(Color.argb(222, 6, 23, 35), Color.argb(224, 8, 47, 58),
                26, Color.argb(100, 115, 245, 194), 1, this));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.topMargin = TvUi.dp(this, 20);
        story.addView(preview, previewParams);
        LinearLayout liveRow = new LinearLayout(this);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        liveRow.addView(TvUi.badge(this, "\u25cf LIVE", TvUi.MINT), new LinearLayout.LayoutParams(-2, TvUi.dp(this, 26)));
        liveRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        liveRow.addView(TvUi.label(this, "CH 101", 15, TvUi.CYAN, true), new LinearLayout.LayoutParams(-2, -2));
        preview.addView(liveRow, new LinearLayout.LayoutParams(-1, -2));
        TextView now = TvUi.label(this, "Your family\u2019s live channels", 22, TvUi.TEXT, true);
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(-1, -2);
        nowParams.topMargin = TvUi.dp(this, 8);
        preview.addView(now, nowParams);
        preview.addView(TvUi.label(this, "Channel numbers  \u2022  CH \u00b1  \u2022  GUIDE  \u2022  Favourites", 13, TvUi.MUTED, false), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        // The build name is not shown here (it wrapped under the buttons at 4K): it is in the update check result and in Diagnostics.
        footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        Button films = TvUi.button(this, "Search without Jio login", false);
        films.setOnClickListener(view -> startActivity(new Intent(this, FlixMomoActivity.class)));
        footer.addView(films, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 36)));
        Button updates = TvUi.button(this, "Check update", false);
        updates.setOnClickListener(view -> UpdateManager.check(this, true));
        LinearLayout.LayoutParams updatesParams = new LinearLayout.LayoutParams(-2, TvUi.dp(this, 36));
        updatesParams.leftMargin = TvUi.dp(this, 10);
        footer.addView(updates, updatesParams);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = TvUi.dp(this, 18);
        story.addView(footer, footerParams);
        return story;
    }

    /** Right column: what is being asked, the one action, and the app's own pad below it. The pad takes the remaining height by weight, so it shrinks instead of covering anything. */
    private View buildEntry() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(TvUi.dp(this, 22), TvUi.dp(this, 16), TvUi.dp(this, 22), TvUi.dp(this, 14));
        card.setBackground(TvUi.gradient(Color.argb(244, 6, 18, 29), Color.argb(242, 8, 31, 43),
                30, Color.argb(110, 83, 228, 255), 1.3f, this));

        step = TvUi.badge(this, "1  CONNECT", TvUi.MINT);
        card.addView(step, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 30)));

        heading = TvUi.label(this, "Your Jio number", 26, TvUi.TEXT, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = TvUi.dp(this, 8);
        card.addView(heading, headingParams);

        mobile = field("10-digit Jio mobile number", InputType.TYPE_CLASS_PHONE);
        mobile.setId(View.generateViewId());
        LinearLayout.LayoutParams mobileParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 54));
        mobileParams.topMargin = TvUi.dp(this, 10);
        card.addView(mobile, mobileParams);

        otpDestination = TvUi.label(this, "", 15, TvUi.MINT, true);
        otpDestination.setVisibility(View.GONE);
        LinearLayout.LayoutParams destinationParams = new LinearLayout.LayoutParams(-1, -2);
        destinationParams.topMargin = TvUi.dp(this, 8);
        card.addView(otpDestination, destinationParams);

        otp = field("OTP from Jio", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        otp.setId(View.generateViewId());
        otp.setVisibility(View.GONE);
        LinearLayout.LayoutParams otpParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 54));
        otpParams.topMargin = TvUi.dp(this, 8);
        card.addView(otp, otpParams);

        send = TvUi.button(this, "Send OTP", true);
        send.setId(View.generateViewId());
        send.setTextSize(19);
        send.setOnClickListener(view -> sendOtp());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 52));
        sendParams.topMargin = TvUi.dp(this, 10);
        card.addView(send, sendParams);

        verify = TvUi.button(this, "Verify and open live TV", true);
        verify.setId(View.generateViewId());
        verify.setTextSize(19);
        verify.setVisibility(View.GONE);
        verify.setOnClickListener(view -> verifyOtp());
        LinearLayout.LayoutParams verifyParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 52));
        verifyParams.topMargin = TvUi.dp(this, 10);
        card.addView(verify, verifyParams);

        changeNumber = TvUi.button(this, "Change number", false);
        changeNumber.setId(View.generateViewId());
        changeNumber.setVisibility(View.GONE);
        changeNumber.setOnClickListener(view -> resetToMobile());
        LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 40));
        changeParams.topMargin = TvUi.dp(this, 6);
        card.addView(changeNumber, changeParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        progress.setIndeterminateTintList(TvUi.tint(TvUi.MINT));
        statusRow.addView(progress, new LinearLayout.LayoutParams(TvUi.dp(this, 26), TvUi.dp(this, 26)));
        status = TvUi.label(this, "The OTP is checked by Jio and never stored by GharTV.", 13, TvUi.MUTED, false);
        status.setMaxLines(2);
        LinearLayout.LayoutParams statusText = new LinearLayout.LayoutParams(0, -2, 1f);
        statusText.leftMargin = TvUi.dp(this, 10);
        statusRow.addView(status, statusText);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = TvUi.dp(this, 8);
        card.addView(statusRow, statusParams);

        LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        padParams.topMargin = TvUi.dp(this, 10);
        card.addView(buildPad(), padParams);

        send.setNextFocusDownId(padStart.getId());
        verify.setNextFocusDownId(changeNumber.getId());
        changeNumber.setNextFocusDownId(padStart.getId());
        return card;
    }

    /** 3 x 4 pad drawn by the app (the system pad is never shown): 1-9, delete, 0, go. Rows and keys share the height by weight. */
    private View buildPad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setBackground(TvUi.gradient(Color.argb(244, 6, 18, 29), Color.argb(242, 8, 31, 43), 26, Color.argb(110, 83, 228, 255), 1.3f, this));
        pad.setPadding(TvUi.dp(this, 12), TvUi.dp(this, 12), TvUi.dp(this, 12), TvUi.dp(this, 12));
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"\u232b", "0", "\u2192"}};
        Button[][] grid=new Button[4][3];
        pad.setClipChildren(false);pad.setClipToPadding(false);
        pad.setMinimumHeight(TvUi.dp(this,236));
        for(int row=0;row<4;row++){
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setWeightSum(3f);line.setClipChildren(false);line.setClipToPadding(false);
            for(int col=0;col<3;col++){
                final String label=keys[row][col];
                Button key=TvUi.button(this,label,label.equals("\u2192"));
                key.setTextSize(25);key.setGravity(Gravity.CENTER);
                key.setPadding(0,0,0,0);key.setMinWidth(0);key.setMinHeight(0);
                key.setId(View.generateViewId());
                key.setOnClickListener(v->padKey(label));
                LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,-1,1f);
                kp.setMargins(TvUi.dp(this,4),TvUi.dp(this,4),TvUi.dp(this,4),TvUi.dp(this,4));
                line.addView(key,kp);grid[row][col]=key;
                if(label.equals("5"))padStart=key;
            }
            pad.addView(line,new LinearLayout.LayoutParams(-1,0,1f));
        }
        for(int row=0;row<4;row++)for(int col=0;col<3;col++){
            Button k=grid[row][col];
            k.setNextFocusLeftId(grid[row][Math.max(0,col-1)].getId());
            k.setNextFocusRightId(grid[row][Math.min(2,col+1)].getId());
            k.setNextFocusUpId(grid[Math.max(0,row-1)][col].getId());
            k.setNextFocusDownId(grid[Math.min(3,row+1)][col].getId());
        }
        return pad;
    }

    private EditText activeField() { return otpSent ? otp : mobile; }

    private void padKey(String label) {
        EditText field = activeField();
        String text = field.getText().toString();
        if (label.equals("\u232b")) {
            if (!text.isEmpty()) field.setText(text.substring(0, text.length() - 1));
        } else if (label.equals("\u2192")) {
            if (otpSent) verifyOtp(); else sendOtp();
        } else if (text.length() < (otpSent ? 6 : 10)) {
            field.setText(text + label);
            // Jio's code has 6 digits: the 6th one verifies by itself (one press fewer). A wrong code just fails and can be retyped.
            if (otpSent && text.length() + 1 == 6) verifyOtp();
        }
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
        edit.setShowSoftInputOnFocus(false);
        edit.setFocusable(false);
        edit.setFocusableInTouchMode(false);
        edit.setCursorVisible(false);
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
        heading.setText("Enter the OTP");
        send.setText("Resend OTP");
        mobile.setVisibility(View.GONE);
        send.setVisibility(View.GONE);
        otpDestination.setText("OTP sent to " + maskedMobile(normalizedMobile()));
        otpDestination.setVisibility(View.VISIBLE);
        otp.setVisibility(View.VISIBLE);
        verify.setVisibility(View.VISIBLE);
        changeNumber.setVisibility(View.VISIBLE);
        padStart.requestFocus();
    }

    private void resetToMobile() {
        otpSent = false;
        step.setText("1  CONNECT");
        heading.setText("Your Jio number");
        mobile.setVisibility(View.VISIBLE);
        send.setVisibility(View.VISIBLE);
        send.setText("Send OTP");
        otpDestination.setVisibility(View.GONE);
        otp.setText("");
        otp.setVisibility(View.GONE);
        verify.setVisibility(View.GONE);
        changeNumber.setVisibility(View.GONE);
        status.setText("The OTP is checked by Jio and never stored by GharTV.");
        status.setTextColor(TvUi.MUTED);
        padStart.requestFocus();
    }

    private void sendOtp() {
        if (!send.isEnabled()) return;   // a request is already running
        String number = normalizedMobile();
        if (number.length() != 10 || number.charAt(0) < '6') {
            status.setText("Enter a valid 10-digit Indian mobile number.");
            status.setTextColor(TvUi.ERROR);
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
                    padStart.requestFocus();
                });
            }
        });
    }

    private void verifyOtp() {
        if (!verify.isEnabled()) return;   // the 6th digit already started the check; the go key or OK must not start a second one
        String number = normalizedMobile();
        String code = otp.getText().toString().replaceAll("[^0-9]", "");
        if (!otpSent || code.length() < 4) {
            Toast.makeText(this, "Enter the OTP received from Jio", Toast.LENGTH_SHORT).show();
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
                    padStart.requestFocus();
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
            // The TV remote's own number keys type into the field; delete removes the last digit.
            if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) { padKey(String.valueOf(key - KeyEvent.KEYCODE_0)); return true; }
            if (key >= KeyEvent.KEYCODE_NUMPAD_0 && key <= KeyEvent.KEYCODE_NUMPAD_9) { padKey(String.valueOf(key - KeyEvent.KEYCODE_NUMPAD_0)); return true; }
            if (key == KeyEvent.KEYCODE_DEL) { padKey("\u232b"); return true; }
        }
        return super.dispatchKeyEvent(event);
    }
}
