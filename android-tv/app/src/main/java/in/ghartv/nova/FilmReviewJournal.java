package in.ghartv.nova;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Bounded local technical UI trace. Never a URL/title/key recorder or network sender. */
final class FilmReviewJournal {
    static final int CAPACITY=128;
    private static final Set<String> EVENTS=new HashSet<>(Arrays.asList(
        "PLAY_REQUEST","FULLSCREEN_REQUEST","FULLSCREEN_ENTER","FULLSCREEN_EXIT","FULLSCREEN_MENU","FULLSCREEN_RESUME",
        "POINTER_ENTER","POINTER_EXIT","POINTER_WAKE","POINTER_FADE","POINTER_CLICK","USE_PAGE","TOOLBAR_OPEN","BACK","MENU",
        "ACTION_PENDING","NAVIGATING","TARGET_CONTROL","TARGET_VIDEO","TARGET_FRAME","TARGET_OBSCURED","TARGET_CHANGED",
        "VISUAL_TIMEOUT","NOT_FOUND","MEDIA_NOT_READY","AMBIGUOUS_MEDIA","CLICK_SENT_NOT_PLAYBACK_PROOF","ALREADY_PLAYING"));
    private static final Set<String> LAYERS=new HashSet<>(Arrays.asList("FULLSCREEN_MENU","FULLSCREEN","POINTER","DETAIL","DISCOVER","TOOLBAR","CONTROLS","PAGE"));
    private final ArrayDeque<JSONObject> rows=new ArrayDeque<>();
    private final long began=SystemClock.elapsedRealtime();
    private long sequence;
    synchronized void record(String event,String layer,boolean enabled,boolean visible){
        if(!EVENTS.contains(event)||!LAYERS.contains(layer))return;
        try{
            JSONObject row=new JSONObject().put("schema","ghartv.ui-transition.v1").put("version_code",BuildConfig.VERSION_CODE)
                .put("seq",++sequence).put("elapsed_ms",Math.max(0,SystemClock.elapsedRealtime()-began))
                .put("event",event).put("layer",layer).put("pointer_enabled",enabled).put("pointer_visible",visible);
            if(rows.size()>=CAPACITY)rows.removeFirst();rows.addLast(row);
            // Android's local ring only. Bounded logging, with no collector upload.
            if(sequence<=512)android.util.Log.i("GharTVReview","GHUX_V1 "+row);
        }catch(Exception ignored){}
    }
    synchronized JSONObject snapshot(){
        try{
            JSONArray copy=new JSONArray();for(JSONObject row:rows)copy.put(new JSONObject(row.toString()));
            return new JSONObject().put("schema","ghartv.local-interactions.v1").put("version_code",BuildConfig.VERSION_CODE)
                .put("events",copy).put("truncated",sequence>CAPACITY).put("network_upload",false);
        }catch(Exception ignored){return new JSONObject();}
    }
    synchronized String summary(){
        StringBuilder out=new StringBuilder("Last "+rows.size()+" technical actions in this Films session. No movie names, search text, URLs, pointer coordinates or raw keystrokes. This is not an automatic upload.\n\n");
        int skip=Math.max(0,rows.size()-18),i=0;for(JSONObject row:rows){if(i++<skip)continue;
            out.append(row.optLong("elapsed_ms")/1000).append("s · ").append(row.optString("event")).append(" · ").append(row.optString("layer")).append('\n');}
        return out.toString();
    }
    boolean save(Context context){
        java.io.File dir=context.getExternalFilesDir(null);if(dir==null)return false;
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(dir,"ghartv-review-interactions.json"))){
            stream.write(snapshot().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));return true;
        }catch(Exception ignored){return false;}
    }
}
