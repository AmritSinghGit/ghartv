package in.ghartv.nova;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Lightweight television launch screen. Birthday mode uses the family photo
 * only for Dad and Simrat; other birthdays retain the same celebration system
 * without inventing a personal photograph.
 */
public final class SplashActivity extends Activity {
    private static final long STANDARD_DURATION_MS = 650L;
    private static final long BIRTHDAY_DURATION_MS = 4_800L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean continued;
    private boolean holdForReview;
    private final Runnable continueRunnable = this::continueToApp;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        TvUi.immersive(this);
        holdForReview = getIntent() != null && getIntent().getBooleanExtra("ghartv_splash_hold", false);
        FamilyTheme.applyPreviewIntent(this);
        setContentView(buildUi());
        if (!holdForReview) {
            main.postDelayed(continueRunnable,
                    FamilyTheme.isBirthday(this) ? BIRTHDAY_DURATION_MS : STANDARD_DURATION_MS);
        }
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(continueRunnable);
        super.onDestroy();
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(TvUi.BG);

        if (FamilyTheme.isBirthday(this)) {
            int photo = FamilyTheme.splashPhotoRes(this);
            if (photo != 0) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageResource(photo);
                root.addView(image, new FrameLayout.LayoutParams(-1, -1));
            } else {
                root.addView(new AuroraBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));
            }

            View veil = new View(this);
            veil.setBackground(TvUi.gradient(
                    Color.argb(photo == 0 ? 132 : 70, 39, 9, 53),
                    Color.argb(235, 4, 12, 26),
                    0, Color.TRANSPARENT, 0, this));
            root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
            root.addView(new CelebrationView(this), new FrameLayout.LayoutParams(-1, -1));
            root.addView(buildBirthdayCopy(), birthdayCopyParams());
        } else {
            root.addView(new AuroraBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));
            root.addView(buildStandardCopy(), new FrameLayout.LayoutParams(-1, -1));
        }
        return root;
    }

    private View buildBirthdayCopy() {
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(TvUi.dp(this, 40), TvUi.dp(this, 36), TvUi.dp(this, 56), TvUi.dp(this, 36));

        TextView brand = TvUi.label(this, "GHAR TV  •  FAMILY CELEBRATION", 15,
                Color.argb(235, 255, 237, 203), true);
        brand.setLetterSpacing(.11f);
        copy.addView(brand, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 32)));

        TextView title = TvUi.label(this, FamilyTheme.headline(this), 43, Color.WHITE, true);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = TvUi.dp(this, 14);
        copy.addView(title, titleParams);

        TextView line = TvUi.label(this, FamilyTheme.subheadline(this), 19,
                Color.argb(238, 255, 239, 220), false);
        line.setMaxLines(3);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2);
        lineParams.topMargin = TvUi.dp(this, 14);
        copy.addView(line, lineParams);

        FamilyTheme.Birthday birthday = FamilyTheme.activeBirthday(this);
        TextView date = TvUi.label(this, birthday == null ? "" : birthday.dateLabel(), 15, Color.WHITE, true);
        date.setGravity(Gravity.CENTER);
        date.setPadding(TvUi.dp(this, 18), 0, TvUi.dp(this, 18), 0);
        date.setBackground(TvUi.rounded(Color.argb(76, 255, 255, 255), 18,
                Color.argb(130, 255, 230, 172), 1, this));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, TvUi.dp(this, 38));
        dateParams.topMargin = TvUi.dp(this, 24);
        copy.addView(date, dateParams);

        copy.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView hint = TvUi.label(this, "Press OK to enter live television", 13,
                Color.argb(205, 235, 245, 255), false);
        copy.addView(hint, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 28)));
        return copy;
    }

    private FrameLayout.LayoutParams birthdayCopyParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * .50f),
                -1,
                Gravity.END);
        return params;
    }

    private View buildStandardCopy() {
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER);
        TextView brand = TvUi.label(this, "GHAR TV", 48, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(.15f);
        copy.addView(brand, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 72)));
        TextView line = TvUi.label(this, "Live television, made for your family", 18,
                TvUi.MUTED, false);
        line.setGravity(Gravity.CENTER);
        copy.addView(line, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 36)));
        return copy;
    }

    private void continueToApp() {
        if (continued || isFinishing()) return;
        continued = true;
        main.removeCallbacks(continueRunnable);
        Intent next = new Intent(this, MainActivity.class);
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    continueToApp();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
