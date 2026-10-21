package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.MediaDrm;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import androidx.media3.common.C;
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
        {"jio_media_cdn", "jiotv.catchup.cdn.jio.com"},
        {"updates", "api.github.com"}, {"collector", "ghartv-telemetry.ghartv-47d9a0.workers.dev"},
        {"flixmomo_app", "flixmomo.app"}, {"flixmomo_st", "flixmomo.st"}, {"flixmomo_bet", "flixmomo.bet"},
        {"film_catalogue", "api.themoviedb.org"}, {"iptv_org", "iptv-org.github.io"}
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
            .setMessage("Checking system DNS, recovered HTTPS and Widevine separately for Jio, plus IPTV-org, registered FlixMomo addresses, updates and diagnostics. Jio stays direct and never uses Tor; secure DNS is tried only after Android DNS fails. No login, stream or private data is sent. This can take up to 15 seconds.")
            .setNegativeButton("Close", null).show();
        check(activity.getApplicationContext(), "manual_" + SystemClock.elapsedRealtime(), result -> {
            if(!wait.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            wait.dismiss();
            StringBuilder text = new StringBuilder("This TV: " + (result.optBoolean("validated") ? "Android reports internet access" : "Internet access not validated") + "\n\n");
            JSONArray checks = result.optJSONArray("checks");
            if(checks != null) for(int i=0;i<checks.length();i++) {
                JSONObject item=checks.optJSONObject(i); if(item==null) continue;
                text.append(item.optString("service")).append(": ").append(item.optString("dns")).append(" / ").append(item.optString("https")).append(item.has("tls_reason")?" · "+item.optString("tls_reason"):"").append('\n');
                if(item.has("clock_offset_seconds"))text.append("  TV minus verified HTTPS time: ").append(item.optLong("clock_offset_seconds")).append(" seconds").append(Math.abs(item.optLong("clock_offset_seconds"))>120?" — check TV date/time":"").append('\n');
            }
            JSONObject drm=result.optJSONObject("widevine");
            if(drm!=null)text.append("\nWidevine: ")
                .append(drm.optBoolean("supported")?"SUPPORTED":"NOT SUPPORTED")
                .append(" · security level ").append(drm.optString("security_level","UNKNOWN"))
                .append("\n");
            text.append("\nTV clock: ").append(new java.util.Date()).append("\nA date/certificate failure can be caused by the TV clock or the server certificate. Do not disable verification.\n");
            text.append("\nHTTPS reachable does not prove account access or playback.\n\nDiagnostics: ")
                .append(Telemetry.isEnabled(activity)?"enabled":"off; error references are not uploaded")
                .append("\nWaiting on this TV: ").append(Telemetry.queuedCount(activity))
                .append("\n").append(Telemetry.lastStatus(activity));
            new AlertDialog.Builder(activity).setTitle("Connection check")
                .setMessage(text).setPositiveButton("Network settings", (d,w)-> {
                    try {activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}
                    catch(Exception e){activity.startActivity(new Intent(Settings.ACTION_SETTINGS));}
                }).setNeutralButton("TV date & time", (d,w)->{try{activity.startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));}catch(Exception ignored){}})
                .setNegativeButton("Close",null).show();
        });
    }
    private static JSONObject probe(String[] host) {
        long start=SystemClock.elapsedRealtime();
        JSONObject r=Telemetry.data("service",host[0],"dns","NOT_CHECKED","https","NOT_CHECKED");
        try {
            try {
                InetAddress.getAllByName(host[1]);
                r.put("dns","RESOLVED");
            } catch(java.net.UnknownHostException direct) {
                r.put("dns","DNS_UNAVAILABLE");
                r.put("dns_cause",NetworkFailure.rootType(direct));
            }
            OkHttpClient client;
            if(host[0].equals("jio_media_cdn"))client=JioNetwork.mediaClient();
            else if(host[0].startsWith("jio_"))client=JioNetwork.directApi();
            else client=CLIENT;
            try(Response response=client.newCall(new Request.Builder()
                    .url("https://"+host[1]+"/").head().build()).execute()) {
                r.put("https","REACHABLE");
                r.put("http",response.code());
                if(host[0].equals("jio_media_cdn"))
                    r.put("route",JioNetwork.mediaRecoveryLabel());
                else if(host[0].startsWith("jio_"))
                    r.put("route",JioNetwork.recoveryLabel());
                java.util.Date serverDate=response.headers().getDate("Date");
                if(serverDate!=null)r.put("clock_offset_seconds",
                        (System.currentTimeMillis()-serverDate.getTime())/1000);
            }
        } catch(Exception e) {
            String code=NetworkFailure.classify(e);
            try {
                r.put("https",code);
                r.put("cause",NetworkFailure.rootType(e));
                if(code.equals("TLS_FAILED"))r.put("tls_reason",NetworkFailure.tlsReason(e));
            }catch(Exception ignored){}
        }
        try{r.put("elapsed_ms",SystemClock.elapsedRealtime()-start);}catch(Exception ignored){}
        return r;
    }

    private static JSONObject widevine() {
        JSONObject result=Telemetry.data("supported",false,"security_level","UNKNOWN");
        MediaDrm drm=null;
        try {
            boolean supported=MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID);
            result.put("supported",supported);
            if(!supported)return result;
            drm=new MediaDrm(C.WIDEVINE_UUID);
            String level=drm.getPropertyString("securityLevel");
            result.put("security_level",level==null||level.trim().isEmpty()?"UNKNOWN":level);
        } catch(Exception error) {
            try{result.put("error",NetworkFailure.rootType(error));}catch(Exception ignored){}
        } finally {if(drm!=null)try{drm.release();}catch(Exception ignored){}}
        return result;
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
                result.put("checks",checks);
                result.put("widevine",widevine());
                result.put("status","COMPLETE");
                if(Telemetry.isEnabled(context)){
                    JSONObject drm=result.optJSONObject("widevine");
                    Telemetry.event(context,"network_diagnostic_completed",Telemetry.data(
                        "jio_api_route",JioNetwork.recoveryLabel(),
                        "jio_media_route",JioNetwork.mediaRecoveryLabel(),
                        "widevine_supported",drm!=null&&drm.optBoolean("supported"),
                        "widevine_level",drm==null?"UNKNOWN":drm.optString("security_level","UNKNOWN")));
                    Telemetry.enqueueUpload(context,false);
                }
                // This tag contains a fixed, non-secret technical schema for the owner-run probe only.
                Log.i("GharTVNetwork", "GHNET_V1 "+result.toString());
            } catch(Exception ignored) {try{result.put("status","CHECK_FAILED");}catch(Exception e){} }
            finally{RUNNING.set(false);if(done!=null)MAIN.post(()->done.result(result));}
        });
    }
}
