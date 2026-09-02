package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UpdateManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private UpdateManager() {}

    public static void check(Activity activity, boolean ownerInitiated) {
        long now = System.currentTimeMillis();
        long last = activity.getSharedPreferences(AppConfig.PREFS, Activity.MODE_PRIVATE)
                .getLong(AppConfig.KEY_LAST_UPDATE_CHECK, 0L);
        if (!ownerInitiated && now - last < AppConfig.UPDATE_CHECK_INTERVAL_MS) return;
        activity.getSharedPreferences(AppConfig.PREFS, Activity.MODE_PRIVATE)
                .edit().putLong(AppConfig.KEY_LAST_UPDATE_CHECK, now).apply();
        Telemetry.event(activity, "update_check", Telemetry.data("manual", ownerInitiated, "result", "started"));

        if (ownerInitiated) Toast.makeText(activity, "Checking for GharTV updates…", Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            try {
                UpdateInfo update = fetchManifest();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    boolean available = update.versionCode > BuildConfig.VERSION_CODE;
                    Telemetry.event(activity, "update_check", Telemetry.data(
                            "manual", ownerInitiated,
                            "result", "success",
                            "update_available", available,
                            "offered_version_code", update.versionCode));
                    if (available) showUpdate(activity, update);
                    else if (ownerInitiated) Toast.makeText(activity, "GharTV is up to date", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                Telemetry.error(activity, "update_check", error, Telemetry.data("manual", ownerInitiated));
                activity.runOnUiThread(() -> {
                    if (ownerInitiated && !activity.isFinishing()) {
                        Toast.makeText(activity, "Update check failed: " + readable(error), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private static UpdateInfo fetchManifest() throws Exception {
        Request request = new Request.Builder()
                .url(AppConfig.UPDATE_MANIFEST)
                .header("User-Agent", "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("GitHub returned HTTP " + response.code());
            String body = response.body() == null ? "{}" : response.body().string();
            JSONObject json = new JSONObject(body);
            UpdateInfo info = new UpdateInfo();
            info.versionCode = json.optInt("versionCode", 0);
            info.versionName = json.optString("versionName", "new version");
            info.apkUrl = json.optString("apkUrl", "");
            info.sha256 = json.optString("sha256", "").toLowerCase(Locale.ROOT);
            info.notes = json.optString("notes", "A newer GharTV build is available.");
            if (info.versionCode <= 0 || info.apkUrl.isEmpty() || info.sha256.length() != 64) {
                throw new IllegalStateException("The update manifest is incomplete");
            }
            return info;
        }
    }

    private static void showUpdate(Activity activity, UpdateInfo update) {
        Telemetry.event(activity, "update_available", Telemetry.data("offered_version_code", update.versionCode));
        new AlertDialog.Builder(activity)
                .setTitle("GharTV update available")
                .setMessage(update.versionName + "\n\n" + update.notes +
                        "\n\nThe APK is verified by SHA-256 before Android opens the installer.")
                .setPositiveButton("Download update", (dialog, which) -> download(activity, update))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void download(Activity activity, UpdateInfo update) {
        Telemetry.event(activity, "update_download", Telemetry.data("result", "started", "version_code", update.versionCode));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "Allow GharTV to install updates, then choose Check for updates again.", Toast.LENGTH_LONG).show();
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            return;
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(TvUi.dp(activity, 22), TvUi.dp(activity, 18), TvUi.dp(activity, 22), TvUi.dp(activity, 18));
        ProgressBar progress = new ProgressBar(activity);
        content.addView(progress, new LinearLayout.LayoutParams(TvUi.dp(activity, 42), TvUi.dp(activity, 42)));
        TextView label = TvUi.label(activity, "Downloading and verifying " + update.versionName + "…", 16, TvUi.TEXT, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, TvUi.dp(activity, 58), 1f);
        labelParams.leftMargin = TvUi.dp(activity, 18);
        content.addView(label, labelParams);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Updating GharTV")
                .setView(content)
                .setCancelable(false)
                .create();
        dialog.show();

        EXECUTOR.execute(() -> {
            try {
                File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (directory == null) directory = activity.getFilesDir();
                if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create the update folder");
                File target = new File(directory, "GharTV-Jio-Live-" + safe(update.versionName) + ".apk");
                Request request = new Request.Builder().url(update.apkUrl)
                        .header("User-Agent", "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME).build();
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IllegalStateException("APK download returned HTTP " + response.code());
                    }
                    try (InputStream input = response.body().byteStream(); FileOutputStream output = new FileOutputStream(target)) {
                        byte[] buffer = new byte[64 * 1024];
                        int count;
                        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                        output.getFD().sync();
                    }
                }
                String actual = sha256(target);
                if (!actual.equalsIgnoreCase(update.sha256)) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    throw new SecurityException("Downloaded APK checksum did not match the signed release manifest");
                }
                File verified = target;
                Telemetry.event(activity, "update_download", Telemetry.data(
                        "result", "success",
                        "version_code", update.versionCode));
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    promptInstall(activity, verified);
                });
            } catch (Exception error) {
                Telemetry.error(activity, "update_download", error, Telemetry.data("version_code", update.versionCode));
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(activity, "Update failed: " + readable(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void promptInstall(Activity activity, File apk) {
        Telemetry.event(activity, "update_install_prompt", Telemetry.data("result", "opened"));
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".files", apk);
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(install);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        StringBuilder out = new StringBuilder();
        for (byte value : digest.digest()) out.append(String.format(Locale.US, "%02x", value));
        return out.toString();
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static String readable(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static final class UpdateInfo {
        int versionCode;
        String versionName = "";
        String apkUrl = "";
        String sha256 = "";
        String notes = "";
    }
}
