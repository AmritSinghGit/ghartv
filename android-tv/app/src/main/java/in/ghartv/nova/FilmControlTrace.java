package in.ghartv.nova;
import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Consent-gated, bounded action outcomes; no title, URL, input text or stable identifier. */
final class FilmControlTrace {
    private static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("watch","media","request","mouse","source"));
    private static final Set<String> OUTCOMES=new HashSet<>(Arrays.asList("BEGIN","NAVIGATING","TARGET_CONTROL","TARGET_VIDEO","TARGET_FRAME","TARGET_OBSCURED","TARGET_CHANGED","VISUAL_TIMEOUT","NOT_FOUND","MEDIA_NOT_READY","AMBIGUOUS_MEDIA","CLICK_SENT_NOT_PLAYBACK_PROOF","ALREADY_PLAYING","CLOCK_ADVANCED","UNCONFIRMED","MOUSE_CLICK_SENT","IFRAME_UNOBSERVABLE"));
    private String attempt="";private long began;private int count;
    private final Set<String> seen=new HashSet<>();
    void reset(){attempt=UUID.randomUUID().toString().replace("-", "").substring(0,16);began=SystemClock.elapsedRealtime();count=0;seen.clear();}
    void begin(Context context){
        if(context==null||!Telemetry.isEnabled(context)){attempt="";return;}
        reset();emit(context,"request","BEGIN");
    }
    JSONObject record(String action,String outcome){
        if(attempt.isEmpty()||count>=16||!ACTIONS.contains(action)||!OUTCOMES.contains(outcome)||!seen.add(action+":"+outcome))return null;
        count++;
        return Telemetry.data("attempt_ref",attempt,"action",action,"outcome",outcome,"engine","embedded_webview","elapsed_ms",Math.min(120000,Math.max(0,SystemClock.elapsedRealtime()-began)));
    }
    void emit(Context context,String action,String outcome){
        if(context==null||!Telemetry.isEnabled(context))return;
        JSONObject data=record(action,outcome);if(data!=null)Telemetry.event(context,"film_control_result",data);
    }
}
