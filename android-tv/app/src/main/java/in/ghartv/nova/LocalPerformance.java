package in.ghartv.nova;
import android.content.Context;
import android.os.SystemClock;
import android.view.Choreographer;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
/** Bounded, on-device technical measurements only: no titles, account data or URLs. */
public final class LocalPerformance {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final JSONArray samples=new JSONArray();
    public static void record(Context c,String key,long value){
        Context app=c.getApplicationContext();IO.execute(()->{try{
            samples.put(new JSONObject().put("metric",key).put("value",value).put("at_ms",System.currentTimeMillis()));
            while(samples.length()>40)samples.remove(0);
            android.app.ActivityManager am=(android.app.ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
            JSONObject root=new JSONObject().put("schema","ghartv.local-performance.v1").put("version_code",BuildConfig.VERSION_CODE).put("samples",samples)
                .put("heap_class_mb",am==null?0:am.getMemoryClass()).put("low_ram",am!=null&&am.isLowRamDevice());
            File dir=app.getExternalFilesDir(null);if(dir==null)return;
            File tmp=new File(dir,"performance.tmp"),out=new File(dir,"performance.json");
            Files.write(tmp.toPath(),root.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(),out.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception ignored){}});
    }
    public static void sampleStartupFrames(Context c){
        Context app=c.getApplicationContext();long began=SystemClock.elapsedRealtime();
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){long previous;int n,slow;
            @Override public void doFrame(long t){if(previous!=0){n++;if(t-previous>32000000L)slow++;}previous=t;
                if(n<120&&SystemClock.elapsedRealtime()-began<8000)Choreographer.getInstance().postFrameCallback(this);
                else{record(app,"startup_frame_intervals",n);record(app,"startup_intervals_over_32ms",slow);}
            }});
    }
}
