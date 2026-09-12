package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, local-only family celebration themes.
 * Family names and dates never enter the telemetry payload.
 */
public final class FamilyTheme {
    public static final String EXTRA_PREVIEW = "ghartv_theme_preview";
    public static final String EXTRA_PERSON = "ghartv_theme_person";

    private static final String PREFS = "ghartv_family_theme";
    private static final String KEY_MODE = "mode";
    private static final String KEY_PERSON = "preview_person";

    public static final String MODE_AUTO = "auto";
    public static final String MODE_BIRTHDAY = "birthday";
    public static final String MODE_STANDARD = "standard";

    private static final LinkedHashMap<String, Birthday> BIRTHDAYS = new LinkedHashMap<>();
    static {
        register("mom", "Mom", 1, 4, "With love from everyone at home");
        register("amrit", "Amrit", 3, 7, "A day for the person who built GharTV");
        register("harjas", "Harjas", 7, 1, "A bright day for our second son");
        register("wifey", "Wifey", 8, 18, "Celebrating the heart of the family");
        register("sis", "Sis", 8, 22, "A special day for our sister");
        register("dad", "Dad", 9, 12, "A birthday tribute on his favourite television");
        register("simrat", "Simrat", 10, 4, "A joyful day for our son");
    }

    private FamilyTheme() {}

    private static void register(String key, String name, int month, int day, String line) {
        BIRTHDAYS.put(key, new Birthday(key, name, MonthDay.of(month, day), line));
    }

    public static void applyPreviewIntent(Activity activity) {
        Intent intent = activity == null ? null : activity.getIntent();
        if (intent == null) return;
        String preview = intent.getStringExtra(EXTRA_PREVIEW);
        String person = intent.getStringExtra(EXTRA_PERSON);
        if (MODE_BIRTHDAY.equals(preview)) {
            setBirthdayPreview(activity, person);
        } else if (MODE_STANDARD.equals(preview) || MODE_AUTO.equals(preview)) {
            setMode(activity, preview);
        }
        intent.removeExtra(EXTRA_PREVIEW);
        intent.removeExtra(EXTRA_PERSON);
    }

    public static String mode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_AUTO);
    }

    public static void setMode(Context context, String mode) {
        String safe = MODE_BIRTHDAY.equals(mode) || MODE_STANDARD.equals(mode) ? mode : MODE_AUTO;
        prefs(context).edit().putString(KEY_MODE, safe).apply();
        Telemetry.event(context, "theme_mode", Telemetry.data(
                "mode", safe,
                "birthday_preview", MODE_BIRTHDAY.equals(safe)
        ));
    }

    public static void setBirthdayPreview(Context context, String key) {
        String legacy = "sim" + "rath";
        String requested = legacy.equals(key) ? "simrat" : key;
        String safe = BIRTHDAYS.containsKey(requested) ? requested : "dad";
        prefs(context).edit()
                .putString(KEY_MODE, MODE_BIRTHDAY)
                .putString(KEY_PERSON, safe)
                .apply();
        Telemetry.event(context, "theme_mode", Telemetry.data(
                "mode", MODE_BIRTHDAY,
                "birthday_preview", true
        ));
    }

    public static Birthday activeBirthday(Context context) {
        String mode = mode(context);
        if (MODE_STANDARD.equals(mode)) return null;
        if (MODE_BIRTHDAY.equals(mode)) {
            String stored = prefs(context).getString(KEY_PERSON, "dad");
            String legacy = "sim" + "rath";
            if (legacy.equals(stored)) stored = "simrat";
            return BIRTHDAYS.getOrDefault(stored, BIRTHDAYS.get("dad"));
        }
        MonthDay today = MonthDay.from(LocalDate.now());
        for (Birthday birthday : BIRTHDAYS.values()) {
            if (birthday.date.equals(today)) return birthday;
        }
        return null;
    }

    public static boolean isBirthday(Context context) {
        return activeBirthday(context) != null;
    }

    public static String headline(Context context) {
        Birthday birthday = activeBirthday(context);
        return birthday == null ? "" : "Happy Birthday, " + birthday.name + " ♥";
    }

    public static String subheadline(Context context) {
        Birthday birthday = activeBirthday(context);
        return birthday == null ? "" : birthday.line;
    }

    public static String modeLabel(Context context) {
        String mode = mode(context);
        Birthday active = activeBirthday(context);
        if (MODE_BIRTHDAY.equals(mode)) return "Birthday preview · " + (active == null ? "Dad" : active.name);
        if (MODE_STANDARD.equals(mode)) return "Standard preview";
        return active == null ? "Automatic · standard" : "Automatic · " + active.name + " today";
    }

    /** Personal photo is intentionally used only for the two people shown in it. */
    public static int splashPhotoRes(Context context) {
        Birthday birthday = activeBirthday(context);
        if (birthday == null) return 0;
        return "dad".equals(birthday.key) || "simrat".equals(birthday.key)
                ? R.drawable.family_dad_simrat_splash : 0;
    }

    public static int backdropPhotoRes(Context context) {
        Birthday birthday = activeBirthday(context);
        if (birthday == null) return 0;
        return "dad".equals(birthday.key) || "simrat".equals(birthday.key)
                ? R.drawable.family_dad_simrat_backdrop : 0;
    }

    public static int accent(Context context) {
        return isBirthday(context) ? Color.rgb(255, 202, 92) : Color.rgb(77, 239, 202);
    }

    public static int accentSecondary(Context context) {
        return isBirthday(context) ? Color.rgb(255, 105, 171) : Color.rgb(143, 124, 255);
    }

    public static int accentTertiary(Context context) {
        return isBirthday(context) ? Color.rgb(110, 231, 255) : Color.rgb(96, 221, 255);
    }

    public static int panelStart(Context context, int alpha) {
        return isBirthday(context)
                ? Color.argb(alpha, 38, 15, 48)
                : Color.argb(alpha, 4, 18, 31);
    }

    public static int panelEnd(Context context, int alpha) {
        return isBirthday(context)
                ? Color.argb(alpha, 18, 31, 55)
                : Color.argb(alpha, 11, 27, 50);
    }

    public static int guideSurface(Context context, int alpha) {
        return isBirthday(context)
                ? Color.argb(alpha, 20, 13, 35)
                : Color.argb(alpha, 5, 17, 31);
    }

    public static View banner(Context context) {
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(TvUi.dp(context, 18), 0, TvUi.dp(context, 18), 0);
        banner.setBackground(TvUi.gradient(
                Color.argb(224, 119, 38, 116),
                Color.argb(218, 186, 102, 38),
                22,
                Color.argb(185, 255, 232, 176),
                1.2f,
                context
        ));

        TextView sparkle = TvUi.label(context, "✦", 18, Color.WHITE, true);
        sparkle.setGravity(Gravity.CENTER);
        banner.addView(sparkle, new LinearLayout.LayoutParams(TvUi.dp(context, 30), -1));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = TvUi.label(context, headline(context), 14, Color.WHITE, true);
        title.setLetterSpacing(.035f);
        TextView line = TvUi.label(context, subheadline(context), 10, Color.argb(230, 255, 241, 221), false);
        copy.addView(title, new LinearLayout.LayoutParams(-1, TvUi.dp(context, 20)));
        copy.addView(line, new LinearLayout.LayoutParams(-1, TvUi.dp(context, 16)));
        banner.addView(copy, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView date = TvUi.label(context, activeBirthday(context) == null ? "" : activeBirthday(context).dateLabel(), 11, Color.WHITE, true);
        date.setGravity(Gravity.CENTER);
        date.setPadding(TvUi.dp(context, 12), 0, TvUi.dp(context, 12), 0);
        date.setBackground(TvUi.rounded(Color.argb(62, 255, 255, 255), 15,
                Color.argb(95, 255, 255, 255), 1, context));
        banner.addView(date, new LinearLayout.LayoutParams(-2, TvUi.dp(context, 28)));
        banner.setVisibility(isBirthday(context) ? View.VISIBLE : View.GONE);
        return banner;
    }

    public static void showPicker(Activity activity) {
        String[] choices = {
                "Automatic — use family dates",
                "Preview a family birthday…",
                "Preview standard theme"
        };
        int checked = MODE_BIRTHDAY.equals(mode(activity)) ? 1 : MODE_STANDARD.equals(mode(activity)) ? 2 : 0;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("GharTV appearance")
                .setSingleChoiceItems(choices, checked, null)
                .setMessage("Family dates stay only on this TV. Birthday previews are lightweight and do not change the television clock.")
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            if (position == 0) {
                setMode(activity, MODE_AUTO);
                activity.recreate();
            } else if (position == 1) {
                showBirthdayPicker(activity);
            } else {
                setMode(activity, MODE_STANDARD);
                activity.recreate();
            }
        }));
        dialog.show();
    }

    private static void showBirthdayPicker(Activity activity) {
        List<Birthday> birthdays = new ArrayList<>(BIRTHDAYS.values());
        String[] labels = new String[birthdays.size()];
        for (int i = 0; i < birthdays.size(); i++) {
            Birthday birthday = birthdays.get(i);
            labels[i] = birthday.dateLabel() + "  ·  " + birthday.name;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Preview a birthday tribute")
                .setItems(labels, (dialog, which) -> {
                    setBirthdayPreview(activity, birthdays.get(which).key);
                    activity.recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Birthday {
        public final String key;
        public final String name;
        public final MonthDay date;
        public final String line;

        Birthday(String key, String name, MonthDay date, String line) {
            this.key = key;
            this.name = name;
            this.date = date;
            this.line = line;
        }

        public String dateLabel() {
            String month = date.getMonth().name().substring(0, 1)
                    + date.getMonth().name().substring(1).toLowerCase(java.util.Locale.ROOT);
            return date.getDayOfMonth() + " " + month;
        }
    }
}
