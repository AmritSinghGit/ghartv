package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local-only: store player preference ONLY after observed first frame + time progression. */
final class FilmVerifiedSourceHistory {
    private static final String FILE="ghartv.verified-player-history";
    private static final Pattern TITLE=Pattern.compile("^/(movie|tv)/(\\d{1,10})(?:/|$)",Pattern.CASE_INSENSITIVE);
    static String keyFor(String url){
        try{
            URI uri=new URI(url);
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||host==null)return null;
            String h=host.toLowerCase(Locale.ROOT);
            if(!(h.equals("flixmomo.bet")||h.equals("www.flixmomo.bet")||
                 h.equals("flixmomo.app")||h.equals("flixmomo.st")||
                 h.equals("www.flixmomo.st")))return null;
            Matcher m=TITLE.matcher(uri.getPath());
            if(!m.find())return null;
            byte[] d=MessageDigest.getInstance("SHA-256")
                .digest((m.group(1).toLowerCase(Locale.ROOT)+":"+m.group(2)).getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder("id_");
            for(int i=0;i<12;i++)b.append(String.format(Locale.ROOT,"%02x",d[i]&255));
            return b.toString();
        }catch(Exception e){return null;}
    }
    static String get(Context c,String titleUrl){
        String key=keyFor(titleUrl);return key==null?"":c.getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(key,"");
    }
    static boolean remember(Context c,String titleUrl,String label){
        String key=keyFor(titleUrl);
        if(key==null||label==null||!label.matches("(?i)^(player|server|source)\\s*#?\\s*\\d{1,3}$"))return false;
        c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putString(key,label).apply();
        return true;
    }
}
