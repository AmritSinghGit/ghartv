package in.ghartv.nova;

import android.net.Uri;
import android.net.http.SslError;
import java.util.LinkedHashMap;

/** Per-navigation request evidence. Full request URLs are memory-only, never diagnostics. */
final class FilmConnectionState {
    enum Scope { MAIN_DOCUMENT, SUBRESOURCE, UNKNOWN }
    private final LinkedHashMap<String,Boolean> requests=new LinkedHashMap<>();
    private String intended="", started="";
    synchronized void begin(String url){requests.clear();intended=key(url);started="";requests.put(intended,true);}
    synchronized void started(String url){started=key(url);remember(started,true);}
    synchronized void observe(String url,boolean main){remember(key(url),main);}
    private void remember(String key,boolean main){
        if(key.isEmpty())return;
        if(Boolean.TRUE.equals(requests.get(key)))return; // Never demote a main request.
        if(requests.size()>=128){String oldest=requests.keySet().iterator().next();requests.remove(oldest);}
        requests.put(key,main);
    }
    synchronized Scope scope(String url){
        String k=key(url);if(k.isEmpty())return Scope.UNKNOWN;
        if(k.equals(intended)||k.equals(started)||Boolean.TRUE.equals(requests.get(k)))return Scope.MAIN_DOCUMENT;
        return Boolean.FALSE.equals(requests.get(k))?Scope.SUBRESOURCE:Scope.UNKNOWN;
    }
    static String key(String value){
        try{Uri u=Uri.parse(value==null?"":value);if(!"https".equals(u.getScheme())||u.getHost()==null)return "";return u.buildUpon().fragment(null).build().toString();}
        catch(Exception e){return "";}
    }
    static String origin(String url){
        try{Uri u=Uri.parse(url==null?"":url);String h=u.getHost();return h!=null&&h.matches("[a-zA-Z0-9.-]{1,253}")?"https://"+h:"unidentified host";}
        catch(Exception e){return "unidentified host";}
    }
    static String reason(int error){switch(error){
        case SslError.SSL_NOTYETVALID:return "CERTIFICATE_NOT_YET_VALID";
        case SslError.SSL_EXPIRED:return "CERTIFICATE_EXPIRED";
        case SslError.SSL_IDMISMATCH:return "CERTIFICATE_NAME_MISMATCH";
        case SslError.SSL_UNTRUSTED:return "CERTIFICATE_NOT_TRUSTED";
        case SslError.SSL_DATE_INVALID:return "CERTIFICATE_DATE_INVALID";
        default:return "CERTIFICATE_INVALID";
    }}
    static String help(int error){switch(error){
        case SslError.SSL_NOTYETVALID:case SslError.SSL_EXPIRED:case SslError.SSL_DATE_INVALID:
            return "Check the TV date/time. The provider may also need to renew its certificate.";
        case SslError.SSL_IDMISMATCH:return "The certificate does not match this address. Do not enter account details.";
        default:return "Check Android/System WebView updates and the network. The provider certificate may require repair.";
    }}
}
