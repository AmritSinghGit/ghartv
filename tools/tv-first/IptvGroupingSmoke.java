import in.ghartv.nova.IptvChannel;
import in.ghartv.nova.IptvGrouping;
import in.ghartv.nova.IptvPlaylistParser;
import java.util.*;
public final class IptvGroupingSmoke {
    private static int checks;
    private static void ok(boolean v){checks++;if(!v)throw new AssertionError("IptvGrouping check "+checks);}
    public static void main(String[] a){
        ok("News".equals(IptvGrouping.typeOf("News;General")));
        ok("Sport".equals(IptvGrouping.typeOf("Sports")));
        ok("Children".equals(IptvGrouping.typeOf("Kids")));
        ok("Other".equals(IptvGrouping.typeOf("Weather")));
        ok(IptvGrouping.NOT_SET.equals(IptvGrouping.typeOf("")));
        ok(IptvGrouping.NOT_SET.equals(IptvGrouping.typeOf("Other")));
        String m3u="#EXTM3U\n"
          +"#EXTINF:-1 tvg-id=\"a\" tvg-country=\"IN\" tvg-language=\"Hindi;English\" group-title=\"News\",A\nhttps://example.com/a.m3u8\n"
          +"#EXTINF:-1 tvg-id=\"b\" group-title=\"Music\",B\nhttps://example.com/b.m3u8\n"
          +"#EXTINF:-1 tvg-id=\"c\",C\nhttps://example.com/c.m3u8\n";
        List<IptvChannel> ch=IptvPlaylistParser.parse(m3u).channels;
        ok(ch.size()==3);
        ok("IN".equals(ch.get(0).country)&&"Hindi;English".equals(ch.get(0).language));
        ok(ch.get(1).country.isEmpty()&&ch.get(1).language.isEmpty());   // not invented
        Map<String,Integer> lang=IptvGrouping.counts(ch,"Language");
        ok(lang.get("Hindi")==1&&lang.get("English")==1&&lang.get("not set")==2&&lang.get("All")==3);
        Map<String,Integer> country=IptvGrouping.counts(ch,"Country");
        ok(country.get("IN")==1&&country.get("not set")==2);
        Map<String,Integer> type=IptvGrouping.counts(ch,"Type");
        ok(type.get("News")==1&&type.get("Music")==1&&type.get("not set")==1);
        ok(IptvGrouping.filter(ch,"Language","English","").size()==1);
        ok(IptvGrouping.filter(ch,"Country","not set","").size()==2);
        ok(IptvGrouping.groups(ch,"Type").get(0).equals("All"));
        ok("Type".equals(IptvGrouping.next("Group"))&&"Group".equals(IptvGrouping.next("Language")));
        System.out.println("IPTV_GROUPING="+checks+"_PASS");
    }
}
