package in.ghartv.nova;

import java.net.URI;
import java.util.Locale;

/** In-app Tor browser only: public HTTPS address validation, no DNS or network calls. */
final class TorWebUrlPolicy {
    private TorWebUrlPolicy(){}
    static String normalize(String text){
        if(text==null)return null;
        String s=text.trim();
        if(s.length()<4||s.length()>2048||s.matches("(?s).*\\s+.*"))return null;
        if(!s.contains("://"))s="https://"+s;
        return allowed(s)?s:null;
    }
    static String addressOrSearch(String input){
        if(input==null)return null;
        String s=input.trim();
        if(s.isEmpty()||s.length()>400)return null;
        if(s.contains(" ")||(!s.contains(".")&&!s.contains("://"))){
            try{return "https://search.brave.com/search?q="+
                java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8.name());}
            catch(Exception e){return null;}
        }
        return normalize(s);
    }
    static boolean allowed(String text){
        if(text==null||text.length()>2048)return false;
        try{
            URI uri=new URI(text);
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||
               uri.getPort()!=-1||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||
               uri.getRawAuthority()==null||uri.isOpaque())return false;
            String h=host.toLowerCase(Locale.ROOT);
            if(h.length()>253||!h.contains(".")||h.endsWith(".")||
               h.endsWith(".local")||h.endsWith(".localhost")||h.endsWith(".internal")||
               h.endsWith(".test")||h.endsWith(".invalid")||h.equals("localhost")||
               h.startsWith("127.")||h.startsWith("10.")||h.startsWith("192.168.")||
               h.startsWith("169.254.")||h.startsWith("0.")||h.startsWith("172.")||
               h.startsWith("100.64.")||h.startsWith("100.65.")||
               h.startsWith("100.66.")||h.startsWith("100.67.")||
               h.matches("(?:\\d+\\.){3}\\d+")||
               h.indexOf(':')>=0||h.indexOf('[')>=0||
               h.equals("jiotvapi.media.jio.com")||h.equals("jiotvapi.cdn.jio.com")||
               h.equals("api.github.com")||
               h.equals("ghartv-telemetry.ghartv-47d9a0.workers.dev"))
                return false;
            // Never allow malformed or non-ASCII lookalike labels in this v1 route.
            if(!h.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+"))return false;
            return true;
        }catch(Exception bad){return false;}
    }
}
