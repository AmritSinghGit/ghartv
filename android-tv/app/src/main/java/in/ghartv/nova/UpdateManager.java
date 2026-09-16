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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Base64;
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

    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final String VERIFIED_SUCCESS = "ghartv_update_last_verified_success_v2";
    private static final String LAST_FAILURE = "ghartv_last_failed_update_check";
    private static final OkHttpClient MANIFEST_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build();
    private static final String API_MIRROR = "https://api.github.com/repos/AmritSinghGit/ghartv/contents/update/latest.json?ref=main";

    public static void check(Activity activity, boolean ownerInitiated) {
        long now = System.currentTimeMillis();
        android.content.SharedPreferences prefs = activity.getSharedPreferences(AppConfig.PREFS, Activity.MODE_PRIVATE);
        long success = prefs.getLong(VERIFIED_SUCCESS, 0L);
        long failed = prefs.getLong(LAST_FAILURE, 0L);
        if (!ownerInitiated && ((now >= success && now-success < AppConfig.UPDATE_CHECK_INTERVAL_MS)
                || (now >= failed && now-failed < 5*60_000L))) return;
        if (!CHECKING.compareAndSet(false,true)) {
            if(ownerInitiated) Toast.makeText(activity,"An update check is already running…",Toast.LENGTH_SHORT).show();
            return;
        }
        if(ownerInitiated) Toast.makeText(activity,"Checking the public GharTV update channel…",Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            try {
                UpdateInfo update = fetchManifest();
                prefs.edit().putLong(VERIFIED_SUCCESS,System.currentTimeMillis()).putLong(AppConfig.KEY_LAST_UPDATE_CHECK,System.currentTimeMillis()).remove(LAST_FAILURE).apply();
                activity.runOnUiThread(() -> {
                    if(activity.isFinishing() || activity.isDestroyed()) return;
                    UpdatePolicy.State state = UpdatePolicy.compare(BuildConfig.VERSION_CODE,update.versionCode);
                    Telemetry.event(activity,"update_check",Telemetry.data("manual",ownerInitiated,"result",state.name(),"offered_version_code",update.versionCode));
                    if(state == UpdatePolicy.State.AVAILABLE) showUpdate(activity,update);
                    else if(ownerInitiated) new AlertDialog.Builder(activity)
                        .setTitle(state == UpdatePolicy.State.AHEAD_OF_PUBLIC ? "You are testing a newer review" : "No newer public update")
                        .setMessage("Installed: "+BuildConfig.VERSION_NAME+" (code "+BuildConfig.VERSION_CODE+")\nPublic channel: "+update.versionName+" (code "+update.versionCode+")\n\n"+
                            (state == UpdatePolicy.State.AHEAD_OF_PUBLIC ? "The public channel has not been promoted to this review yet. Your review will not be downgraded." : "The public update check succeeded. This is the current public version."))
                        .setPositiveButton("Continue watching",null).show();
                });
            } catch (Exception error) {
                prefs.edit().putLong(LAST_FAILURE,System.currentTimeMillis()).apply();
                String code=UpdatePolicy.failureCode(error);
                Telemetry.event(activity,"update_check",Telemetry.data("manual",ownerInitiated,"result","NOT_CHECKED","reason",code));
                activity.runOnUiThread(() -> {
                    if(!ownerInitiated || activity.isFinishing() || activity.isDestroyed()) return;
                    String detail="DNS_UNAVAILABLE".equals(code) ? "This device could not resolve the update service address." :
                        "REQUEST_TIMED_OUT".equals(code) ? "The update service took too long to respond." :
                        "MANIFEST_REJECTED".equals(code) ? "The server returned update information that could not be verified." : "The update service is temporarily unavailable.";
                    new AlertDialog.Builder(activity).setTitle("Update check unavailable")
                        .setMessage(detail+"\n\nNo update result could be confirmed. Your installed app and saved settings have not changed. Check this TV’s connection and try again; playback is not blocked.")
                        .setPositiveButton("Retry check",(d,w)->check(activity,true))
                        .setNeutralButton("Connection check",(d,w)->NetworkDiagnostics.show(activity))
                        .setNegativeButton("Continue watching",null).show();
                });
            } finally { CHECKING.set(false); }
        });
    }

    private static UpdateInfo fetchManifest() throws Exception {
        Exception last = null;
        // Same repository file over two official HTTPS hosts. No alternate DNS, TLS bypass or unofficial APK source.
        for (String endpoint : new String[]{AppConfig.UPDATE_MANIFEST,API_MIRROR}) {
            try {
                Request request = new Request.Builder().url(endpoint)
                    .header("User-Agent","GharTV-Jio-Live/"+BuildConfig.VERSION_NAME)
                    .header("Accept","application/vnd.github+json").build();
                try (Response response = MANIFEST_CLIENT.newCall(request).execute()) {
                    if(!response.isSuccessful() || response.body()==null) throw new java.io.IOException("Update service unavailable");
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    try(InputStream input=response.body().byteStream()) {
                        byte[] bytes=new byte[4096];int count;
                        while((count=input.read(bytes))!=-1){if(buffer.size()+count>65536)throw new SecurityException("Manifest too large");buffer.write(bytes,0,count);}
                    }
                    JSONObject json = new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
                    if(endpoint.equals(API_MIRROR)) {
                        if(!"base64".equals(json.optString("encoding")) || !"update/latest.json".equals(json.optString("path")))
                            throw new SecurityException("Unexpected repository file");
                        byte[] content=Base64.decode(json.getString("content"),Base64.DEFAULT);
                        if(content.length>32768)throw new SecurityException("Manifest too large");
                        json=new JSONObject(new String(content,StandardCharsets.UTF_8));
                    }
                    UpdateInfo info = new UpdateInfo();
                    info.versionCode=json.optInt("versionCode",0);
                    info.versionName=json.optString("versionName","");
                    info.apkUrl=json.optString("apkUrl","");
                    info.sha256=json.optString("sha256","").toLowerCase(Locale.ROOT);
                    info.notes=json.optString("notes","A newer public GharTV build is available.");
                    if(info.notes.length()>1800)info.notes=info.notes.substring(0,1800);
                    if(info.versionCode<=0 || info.versionName.isEmpty() || info.versionName.length()>100 || !UpdatePolicy.allowedApk(info.apkUrl)
                        || !info.sha256.matches("[a-f0-9]{64}") || !json.optString("sourceCommit").matches("[a-f0-9]{40}")
                        || !"production".equals(json.optString("channel"))) throw new SecurityException("Update identity rejected");
                    return info;
                }
            } catch(Exception error) { last=error; }
        }
        throw last == null ? new java.io.IOException("Update service unavailable") : last;
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
                        long total=0;
                        while ((count = input.read(buffer)) >= 0) {
                            total+=count; if(total>64L*1024*1024) throw new SecurityException("APK exceeds allowed size");
                            output.write(buffer,0,count);
                        }
                        output.getFD().sync();
                    }
                }
                String actual = sha256(target);
                if (!actual.equalsIgnoreCase(update.sha256)) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    throw new SecurityException("Downloaded APK checksum did not match the public update manifest");
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
