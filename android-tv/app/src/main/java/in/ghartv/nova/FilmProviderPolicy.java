package in.ghartv.nova;

import android.net.Uri;
import org.json.JSONArray;

/** Reviewed origin registry. New/lookalike domains are never self-authorising.
 * Remote configuration changes need a separately verified app/config update. */
final class FilmProviderPolicy {
    static final String VERSION="flixmomo-2026-10-14-r52";
    static final String HOME="https://flixmomo.bet/";
    static final String CREDIT="FlixMomo suggestions & player · independent GharTV interface";
    private static final String[] HOSTS={"flixmomo.app","flixmomo.st","www.flixmomo.st","flixmomo.bet","www.flixmomo.bet"};
    static boolean host(String host){for(String item:HOSTS)if(item.equals(host))return true;return false;}
    static boolean allowed(Uri uri){return uri!=null&&"https".equals(uri.getScheme())&&host(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1;}
    static String hostsJavascript(){JSONArray a=new JSONArray();for(String h:HOSTS)a.put(h);return a.toString();}
    static boolean detail(Uri uri){String p=uri==null?null:uri.getPath();return allowed(uri)&&p!=null&&p.matches("(?i)^/(movie|tv|show|title)/[a-z0-9][^?#]*")&&!p.toLowerCase(java.util.Locale.ROOT).contains("/watch");}
    // Persist only an already-reviewed HTTPS root, never a query or credentials.
    static String registeredHome(String raw){
        if(raw==null)return null;
        try{Uri u=Uri.parse(raw);String path=u.getPath();
            if(!allowed(u)||u.getQuery()!=null||u.getFragment()!=null||!(path==null||path.isEmpty()||"/".equals(path)))return null;
            return "https://"+u.getHost()+"/";
        }catch(RuntimeException ignored){return null;}
    }
    static String pageHome(String raw){
        if(raw==null)return null;
        try{Uri u=Uri.parse(raw);return allowed(u)?"https://"+u.getHost()+"/":null;}
        catch(RuntimeException ignored){return null;}
    }
    private FilmProviderPolicy(){}
}
