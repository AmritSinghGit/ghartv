package in.ghartv.nova;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

public final class TvUi {
    public static final int BG = Color.rgb(2, 7, 13);
    public static final int SURFACE = Color.rgb(8, 22, 34);
    public static final int SURFACE_2 = Color.rgb(12, 36, 51);
    public static final int SURFACE_3 = Color.rgb(17, 57, 73);
    public static final int TEXT = Color.rgb(247, 252, 255);
    public static final int MUTED = Color.rgb(151, 180, 197);
    public static final int CYAN = Color.rgb(83, 228, 255);
    public static final int MINT = Color.rgb(115, 245, 194);
    public static final int PINK = Color.rgb(255, 94, 181);
    public static final int AMBER = Color.rgb(255, 200, 87);
    public static final int ERROR = Color.rgb(255, 132, 145);

    private TvUi() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int fill, float radiusDp, int stroke, float strokeDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), stroke);
        return drawable;
    }

    public static GradientDrawable gradient(int start, int end, float radiusDp, int stroke, float strokeDp, Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), stroke);
        return drawable;
    }

    public static TextView label(Context context, String text, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    public static TextView badge(Context context, String text, int color) {
        TextView badge = label(context, text, 11, color, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        badge.setLetterSpacing(.08f);
        badge.setBackground(rounded(Color.argb(58, Color.red(color), Color.green(color), Color.blue(color)),
                18, Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)), 1, context));
        return badge;
    }

    public static Button button(Context context, String text, boolean primary) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setTextSize(primary ? 15 : 13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        if (primary) {
            focusCard(button, Color.rgb(15, 87, 93), Color.rgb(24, 144, 139), 20);
        } else {
            focusCard(button, Color.rgb(10, 30, 44), Color.rgb(18, 72, 88), 18);
        }
        return button;
    }

    public static void focusCard(View view, int normalColor, int focusColor, float radiusDp) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClipToOutline(false);
        view.setBackground(rounded(normalColor, radiusDp, Color.argb(42, 255, 255, 255), 1, view.getContext()));
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(rounded(
                    focused ? focusColor : normalColor,
                    radiusDp,
                    focused ? Color.rgb(225, 255, 249) : Color.argb(42, 255, 255, 255),
                    focused ? 2.3f : 1f,
                    v.getContext()));
            v.animate()
                    .scaleX(focused ? 1.045f : 1f)
                    .scaleY(focused ? 1.045f : 1f)
                    .translationZ(focused ? dp(v.getContext(), 11) : 0)
                    .alpha(focused ? 1f : .96f)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(120)
                    .start();
        });
    }

    public static ColorStateList tint(int color) {
        return ColorStateList.valueOf(color);
    }

    public static void immersive(Activity activity) {
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
