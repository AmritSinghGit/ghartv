package in.ghartv.nova;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hardware classes, not identifiers. Extra reporting requires separate device-local consent. */
public final class HardwareDiagnostics {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("ghartv_hardware_details",Context.MODE_PRIVATE);}
    private HardwareDiagnostics(){}
    public static boolean enabled(Context c){return prefs(c).getBoolean("share",false)&&Telemetry.isEnabled(c);}
    public static void maybeReport(Context context){
        Context c=context.getApplicationContext();
        if(!enabled(c))return;
        IO.execute(()->{
            long now=System.currentTimeMillis(),last=prefs(c).getLong("last_report",0);
            if(now>=last&&now-last<24L*60*60*1000)return;
            JSONObject sample=snapshot(c);
            if(enabled(c)){Telemetry.event(c,"hardware_snapshot",sample);prefs(c).edit().putLong("last_report",now).apply();Telemetry.enqueueUpload(c,false);}
        });
    }
    public static void show(Activity a){
        TextView view=TvUi.label(a,"Reading this TV’s capabilities…",15,TvUi.TEXT,false);
        int pad=TvUi.dp(a,24);view.setPadding(pad,pad,pad,pad);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Hardware & picture · local inspection")
            .setView(view).setPositiveButton("Close",null)
            .setNeutralButton(enabled(a)?"Stop hardware sharing":"Share hardware diagnostics",(d,w)->{
                if(enabled(a)){prefs(a).edit().putBoolean("share",false).apply();return;}
                new AlertDialog.Builder(a).setTitle("Share technical hardware details?")
                    .setMessage("Optional: CPU architecture/core count, chipset model, approximate RAM, low-memory class, reported display mode and available decoder counts. No serial, Android ID, IP, precise location or provider account. Sent at most once a day when ordinary diagnostics are enabled. You can stop here at any time; server retention follows diagnostic policy.")
                    .setPositiveButton("Enable",(x,y)->{
                        if(!Telemetry.isEnabled(a)){new AlertDialog.Builder(a).setMessage("First enable ordinary diagnostics in Diagnostics & privacy. Hardware sharing has not been enabled.").setPositiveButton("OK",null).show();return;}
                        prefs(a).edit().putBoolean("share",true).putLong("last_report",0).apply();maybeReport(a);
                    }).setNegativeButton("Not now",null).show();
            }).create();
        dialog.show();
        IO.execute(()->{
            JSONObject sample=snapshot(a.getApplicationContext());StringBuilder text=new StringBuilder();
            Iterator<String> keys=sample.keys();while(keys.hasNext()){String key=keys.next();text.append(key.replace('_',' ')).append(": ").append(sample.opt(key)).append("\n");}
            text.append("\nExtra hardware sharing: ").append(enabled(a)?"on":"off")
                .append("\n\nSource resolution and TV output are different. Decoder availability is not proof a stream is native HD/4K. AI super-resolution is not running in this release. Prefer an authorised HD source; TV-side enhancement is device-specific.");
            MAIN.post(()->{if(!a.isFinishing()&&!a.isDestroyed()&&dialog.isShowing())view.setText(text.toString());});
        });
    }
    private static JSONObject snapshot(Context c){
        JSONObject o=new JSONObject();
        try {
            ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();if(am!=null)am.getMemoryInfo(m);
            o.put("android_api",Build.VERSION.SDK_INT);o.put("cpu_abi",Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:"unknown");
            o.put("cpu_cores",Math.min(64,Runtime.getRuntime().availableProcessors()));
            o.put("ram_mb_bucket",Math.round(m.totalMem/(256.0*1024*1024))*256);
            o.put("available_ram_mb_bucket",Math.round(m.availMem/(128.0*1024*1024))*128);
            o.put("low_ram_class",am!=null&&am.isLowRamDevice());o.put("memory_pressure",m.lowMemory);
            if(Build.VERSION.SDK_INT>=31){o.put("soc_model",Build.SOC_MODEL);o.put("soc_manufacturer",Build.SOC_MANUFACTURER);}
            WindowManager wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
            if(wm!=null){Display display=wm.getDefaultDisplay();if(display!=null){Display.Mode mode=display.getMode();o.put("display_width",mode.getPhysicalWidth());o.put("display_height",mode.getPhysicalHeight());o.put("display_hz",Math.round(mode.getRefreshRate()));}}
            int avc=0,hevc=0,av1=0,hardware=0;
            for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
                if(codec.isEncoder())continue;
                boolean isVideo=false;for(String type:codec.getSupportedTypes()){
                    if("video/avc".equals(type))avc++;if("video/hevc".equals(type))hevc++;if("video/av01".equals(type))av1++;
                    if(type.startsWith("video/"))isVideo=true;
                }
                if(isVideo&&Build.VERSION.SDK_INT>=29&&codec.isHardwareAccelerated())hardware++;
            }
            o.put("avc_decoders",avc);o.put("hevc_decoders",hevc);o.put("av1_decoders",av1);o.put("hardware_video_decoders",hardware);
        }catch(Exception ignored){try{o.put("capability_scan_partial",true);}catch(Exception ignoredAgain){}}
        return o;
    }
}
