import in.ghartv.nova.Channel;
import in.ghartv.nova.IptvChannel;
import in.ghartv.nova.LiveSearch;
import java.util.*;
public final class LiveSearchSmoke {
    private static int checks;
    private static void ok(boolean v){checks++;if(!v)throw new AssertionError("LiveSearch check "+checks);}
    static Channel j(int n,String name,String state){Channel c=new Channel();c.number=n;c.name=name;c.accessState=state;return c;}
    static IptvChannel i(String name){IptvChannel c=new IptvChannel();c.id=name;c.name=name;c.streamUrl="https://x/"+name;return c;}
    public static void main(String[] a){
        List<Channel> jio=Arrays.asList(j(101,"Star Sports 1","available"),j(102,"Star Sports 2","unavailable"),j(205,"Aaj Tak","available"),j(300,"Sports Zone Premium","subscription"));
        List<IptvChannel> ip=Arrays.asList(i("Aaj Tak"),i("Sports 18"),i("BBC News"),i("Star Sports 1"));
        List<LiveSearch.Hit> r=LiveSearch.search(jio,ip,"star sports");
        ok(r.size()==2&&r.get(0).jio.number==101);                       // working first, dead one last
        ok(r.get(1).jio.number==102);
        ok(r.get(0).alternates==1);                                      // IPTV twin folded in, not a second row
        r=LiveSearch.search(jio,ip,"aaj tak");
        ok(r.size()==1&&r.get(0).jio!=null&&r.get(0).alternates==1);
        r=LiveSearch.search(jio,ip,"bbc");
        ok(r.size()==1&&r.get(0).iptv!=null&&"Public IPTV".equals(r.get(0).source()));
        r=LiveSearch.search(jio,ip,"205");
        ok(r.size()>=1&&r.get(0).jio.number==205);                       // channel number
        r=LiveSearch.search(jio,ip,"sports");
        ok(r.get(0).score>=r.get(r.size()-1).score&&r.size()==4);
        ok(LiveSearch.search(jio,ip,"").isEmpty()&&LiveSearch.search(null,null,"x").isEmpty());
        ok(LiveSearch.search(jio,ip,"zzzz").isEmpty());
        List<IptvChannel> many=new ArrayList<>();for(int k=0;k<100;k++)many.add(i("Movie "+k));
        ok(LiveSearch.search(null,many,"movie").size()==LiveSearch.MAX_HITS);
        System.out.println("LIVE_SEARCH="+checks+"_PASS");
    }
}
