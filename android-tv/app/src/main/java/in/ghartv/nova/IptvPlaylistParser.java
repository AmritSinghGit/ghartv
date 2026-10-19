package in.ghartv.nova;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded M3U parser for the fixed IPTV-org source. Only public HTTPS streams are admitted. */
public final class IptvPlaylistParser {
    private static final Pattern ATTRIBUTE = Pattern.compile("([A-Za-z0-9_-]+)=\\\"([^\\\"]*)\\\"");
    static final int MAX_CHANNELS = 12_000;
    private IptvPlaylistParser() {}

    public static final class Result {
        public final List<IptvChannel> channels;
        public final int describedEntries;
        public final int insecureOrPrivateSkipped;
        public final int duplicateSkipped;
        Result(List<IptvChannel> values, int described, int unsafe, int duplicates) {
            channels = values; describedEntries = described;
            insecureOrPrivateSkipped = unsafe; duplicateSkipped = duplicates;
        }
    }

    private static final class Pending {
        String id="",name="",group="Other",logo="",userAgent="",referrer="";
    }

    public static Result parse(String raw) {
        List<IptvChannel> channels = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int described=0, unsafe=0, duplicates=0;
        Pending pending=null;
        if(raw==null)raw="";
        String[] lines=raw.split("\\r?\\n",-1);
        for(String source:lines) {
            String line=source==null?"":source.trim();
            if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF:")) {
                pending=parseInfo(line);described++;continue;
            }
            if(pending==null)continue;
            if(line.startsWith("#EXTVLCOPT:http-user-agent=")) {pending.userAgent=clean(line.substring(line.indexOf('=')+1),300);continue;}
            if(line.startsWith("#EXTVLCOPT:http-referrer=")||line.startsWith("#EXTVLCOPT:http-referer=")) {pending.referrer=safeOptionalUrl(line.substring(line.indexOf('=')+1));continue;}
            if(line.startsWith("#EXTVLCOPT:http-origin=")) {continue;}
            if(line.startsWith("#"))continue;
            String stream=line;
            if(!isPublicHttps(stream)) {unsafe++;pending=null;continue;}
            if(!seen.add(stream)) {duplicates++;pending=null;continue;}
            IptvChannel channel=new IptvChannel();
            channel.number=channels.size()+1;
            channel.id=pending.id.isEmpty()?"iptv-"+Integer.toHexString(stream.hashCode()):pending.id;
            channel.name=pending.name.isEmpty()?"Unnamed public channel":pending.name;
            channel.group=pending.group.isEmpty()?"Other":pending.group;
            channel.logoUrl=safeOptionalUrl(pending.logo);
            channel.streamUrl=stream;
            channel.userAgent=clean(pending.userAgent,300);
            channel.referrer=safeOptionalUrl(pending.referrer);
            channels.add(channel);pending=null;
            if(channels.size()>=MAX_CHANNELS)break;
        }
        return new Result(channels,described,unsafe,duplicates);
    }

    private static Pending parseInfo(String line) {
        Pending pending=new Pending();
        Matcher matcher=ATTRIBUTE.matcher(line);
        while(matcher.find()) {
            String key=matcher.group(1).toLowerCase(Locale.ROOT),value=clean(matcher.group(2),2048);
            if("tvg-id".equals(key))pending.id=clean(value,180);
            else if("tvg-logo".equals(key))pending.logo=value;
            else if("group-title".equals(key))pending.group=clean(value,80);
            else if("http-user-agent".equals(key))pending.userAgent=clean(value,300);
            else if("http-referrer".equals(key)||"http-referer".equals(key))pending.referrer=value;
        }
        int comma=commaOutsideQuotes(line);
        if(comma>=0&&comma+1<line.length())pending.name=clean(line.substring(comma+1),120);
        return pending;
    }

    private static int commaOutsideQuotes(String value) {
        boolean quoted=false;
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\"')quoted=!quoted;
            else if(c==','&&!quoted)return i;
        }
        return -1;
    }

    static boolean isPublicHttps(String value) {
        if(value==null||value.length()>2048)return false;
        try {
            URI uri=URI.create(value.trim());
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||uri.getFragment()!=null)return false;
            String host=uri.getHost();if(host==null||host.isEmpty())return false;
            host=host.toLowerCase(Locale.ROOT);
            if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.endsWith(".internal")||host.endsWith(".invalid")||host.contains(":"))return false;
            if(host.matches("(?:0|10|127)\\..*")||host.matches("169\\.254\\..*")||host.matches("192\\.168\\..*")||host.matches("172\\.(?:1[6-9]|2[0-9]|3[01])\\..*"))return false;
            return true;
        } catch(Exception ignored) {return false;}
    }

    private static String safeOptionalUrl(String value) {return isPublicHttps(value)?value.trim():"";}
    private static String clean(String value,int max) {
        if(value==null)return "";
        String text=value.replaceAll("[\\p{Cntrl}&&[^\\t]]"," ").replaceAll("\\s+"," ").trim();
        return text.length()>max?text.substring(0,max):text;
    }
}
