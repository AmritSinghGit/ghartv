package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetAddress;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Explicit, bounded connectivity checks. Never logs addresses, credentials or stream URLs. */
public final class NetworkDiagnostics {
    private NetworkDiagnostics() {}
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ExecutorService PROBES = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    private static final String[][] HOSTS = {
        {"jio_playback", "jiotvapi.media.jio.com"}, {"jio_guide", "jiotvapi.cdn.jio.com"},
        {"updates", "api.github.com"}, {"collector", "ghartv-telemetry.ghartv-47d9a0.workers.dev"}
    };
    public static boolean reviewProbe(Activity activity) {
        String id = activity.getIntent().getStringExtra("ghartv_network_probe");
        if(id == null || !id.matches("[A-Za-z0-9_-]{8,70}")) return false;
        activity.getIntent().removeExtra("ghartv_network_probe");
        check(activity.getApplicationContext(), id, null); return true;
    }
    public static void show(Activity activity) {
        if(activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog wait = new AlertDialog.Builder(activity).setTitle("Checking this TV's connection")
            .setMessage("Checking DNS and HTTPS separately for Jio, updates and diagnostics. No login, stream or private data is sent. This can take up to 15 seconds.")
            .setNegativeButton("Close", null).show();
        check(activity.getApplicationContext(), "manual_" + SystemClock.elapsedRealtime(), result -> {
            if(!wait.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            wait.dismiss();
            StringBuilder text = new StringBuilder("This TV: " + (result.optBoolean("validated") ? "Android reports internet access" : "Internet access not validated") + "\n\n");
            JSONArray checks = result.optJSONArray("checks");
            if(checks != null) for(int i=0;i<checks.length();i++) {
                JSONObject item=checks.optJSONObject(i); if(item==null) continue;
                text.append(item.optString("service")).append(": ").append(item.optString("dns")).append(" / ").append(item.optString("https")).append('\n');
            }
            text.append("\nHTTPS reachable does not prove account access or playback.\n\nDiagnostics: ")
                .append(Telemetry.isEnabled(activity)?"enabled":"off; error references are not uploaded")
                .append("\nWaiting on this TV: ").append(Telemetry.queuedCount(activity))
                .append("\n").append(Telemetry.lastStatus(activity));
            new AlertDialog.Builder(activity).setTitle("Connection check")
                .setMessage(text).setPositiveButton("Network settings", (d,w)-> {
                    try {activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}
                    catch(Exception e){activity.startActivity(new Intent(Settings.ACTION_SETTINGS));}
                }).setNeutralButton("Diagnostics & privacy", (d,w)->DiagnosticsDialog.show(activity))
                .setNegativeButton("Close",null).show();
        });
    }
    private static JSONObject probe(String[] host) {
        long start=SystemClock.elapsedRealtime(); JSONObject r=Telemetry.data("service",host[0],"dns","NOT_CHECKED","https","NOT_CHECKED");
        try {
            InetAddress.getAllByName(host[1]); r.put("dns","RESOLVED");
            try(Response response=CLIENT.newCall(new Request.Builder().url("https://"+host[1]+"/").head().build()).execute()) {
                r.put("https","REACHABLE"); r.put("http",response.code());
                java.util.Date serverDate=response.headers().getDate("Date");
                if(serverDate!=null)r.put("clock_offset_seconds",(System.currentTimeMillis()-serverDate.getTime())/1000);
            }
        } catch(Exception e) {
            String code=NetworkFailure.classify(e);
            try {if(code.equals("DNS_UNAVAILABLE"))r.put("dns",code);else r.put("https",code);}catch(Exception ignored){}
        }
        try{r.put("elapsed_ms",SystemClock.elapsedRealtime()-start);}catch(Exception ignored){} return r;
    }
    interface Done { void result(JSONObject r); }
    private static void check(Context context, String id, Done done) {
        if(!RUNNING.compareAndSet(false,true)) { if(done!=null)MAIN.post(()->done.result(Telemetry.data("status","CHECK_ALREADY_RUNNING"))); return; }
        IO.execute(()-> {
            JSONObject result=Telemetry.data("schema","ghartv.network-probe.v1","probe_id",id,"version_code",BuildConfig.VERSION_CODE,
                "observed_epoch_ms",System.currentTimeMillis(),"diagnostics_enabled",Telemetry.isEnabled(context),"queue_depth",Telemetry.queuedCount(context));
            try {
                ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkCapabilities cap=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
                result.put("validated",cap!=null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                java.util.List<Future<JSONObject>> jobs=new java.util.ArrayList<>();
                for(String[] host:HOSTS)jobs.add(PROBES.submit(()->probe(host)));
                JSONArray checks=new JSONArray();long deadline=SystemClock.elapsedRealtime()+12000;
                for(int i=0;i<jobs.size();i++)try{checks.put(jobs.get(i).get(Math.max(1,deadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS));}
                catch(Exception e){jobs.get(i).cancel(true);checks.put(Telemetry.data("service",HOSTS[i][0],"dns","CHECK_TIMED_OUT","https","NOT_CHECKED"));}
                result.put("checks",checks); result.put("status","COMPLETE");
                // This tag contains a fixed, non-secret technical schema for the owner-run probe only.
                Log.i("GharTVNetwork", "GHNET_V1 "+result.toString());
            } catch(Exception ignored) {try{result.put("status","CHECK_FAILED");}catch(Exception e){} }
            finally{RUNNING.set(false);if(done!=null)MAIN.post(()->done.result(result));}
        });
    }
}
