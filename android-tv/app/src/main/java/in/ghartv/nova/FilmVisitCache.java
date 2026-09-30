package in.ghartv.nova;
import android.net.Uri;
import android.os.SystemClock;
import java.util.LinkedHashMap;
import java.util.Iterator;
import org.json.JSONObject;

/** Small activity-local display cache, not a source of permission to play.
 * No requests, files, video preloading, cookies, telemetry or cross-visit history.
 */
final class FilmVisitCache {
    interface Clock {long now();}
    private final Clock clock;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private static final long MAX_AGE=10*60*1000;
    private static final class Entry {final long at;final String data;Entry(long t,String s){at=t;data=s;}}
    FilmVisitCache(){this(SystemClock::elapsedRealtime);}
    FilmVisitCache(Clock c){clock=c;}
    void put(JSONObject d){
        if(d==null)return;String url=d.optString("url");
        if(!FilmProviderPolicy.detail(Uri.parse(url))||url.length()>2048||Uri.parse(url).getQuery()!=null||Uri.parse(url).getFragment()!=null)return;
        String text=d.toString();if(text.length()>10000)return;
        entries.put(url,new Entry(clock.now(),text));
        while(entries.size()>12){Iterator<String> i=entries.keySet().iterator();i.next();i.remove();}
    }
    JSONObject get(String url){Entry e=entries.get(url);if(e==null)return null;if(clock.now()<e.at||clock.now()-e.at>=MAX_AGE){entries.remove(url);return null;}try{return new JSONObject(e.data);}catch(Exception ignored){return null;}}
    int size(){return entries.size();}
    void clear(){entries.clear();}
}
