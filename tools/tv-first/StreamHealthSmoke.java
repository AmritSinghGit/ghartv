import in.ghartv.nova.IptvChannel;
import in.ghartv.nova.StreamFallback;
import in.ghartv.nova.StreamHealth;
import java.util.*;
public final class StreamHealthSmoke {
    private static int checks;
    private static void ok(boolean v){checks++;if(!v)throw new AssertionError("StreamHealth check "+checks);}
    private static IptvChannel ch(String id,String url){IptvChannel c=new IptvChannel();c.id=id;c.name="X";c.streamUrl=url;return c;}
    public static void main(String[] a){
        StreamHealth h=new StreamHealth("");
        ok(h.score("u")==50&&!h.isDead("u"));
        h.recordFailure("u",1);h.recordFailure("u",2);ok(!h.isDead("u")&&h.score("u")==10);
        h.recordFailure("u",3);ok(h.isDead("u")&&h.score("u")==0);
        h.recordPicture("u",1500,4);ok(!h.isDead("u")&&h.failsInARow("u")==0&&h.score("u")==82);
        StreamHealth r=new StreamHealth(h.toJson());ok(r.score("u")==82);
        ok(new StreamHealth("{bad").score("z")==50);
        h.recordFailure("d",1);h.recordFailure("d",1);h.recordFailure("d",1);
        ok(h.order(Arrays.asList("d","n","u")).equals(Arrays.asList("u","n","d")));
        List<IptvChannel> all=Arrays.asList(ch("a","https://h/1"),ch("a","https://h/2"),ch("a","https://h/3"),ch("a","https://h/2"),ch("b","https://h/9"));
        StreamHealth g=new StreamHealth("");g.recordPicture("https://h/3",900,1);
        List<IptvChannel> alt=StreamFallback.alternates(all.get(0),all,g);
        ok(alt.size()==2&&alt.get(0).streamUrl.equals("https://h/3")&&alt.get(1).streamUrl.equals("https://h/2"));
        for(int i=0;i<3;i++)g.recordFailure("https://h/2",i);
        ok(StreamFallback.alternates(all.get(0),all,g).size()==1);
        ok(StreamFallback.alternates(all.get(4),all,g).isEmpty());
        for(int i=0;i<StreamHealth.KEEP+20;i++)g.recordFailure("k"+i,i);
        ok(new StreamHealth(g.toJson()).score("k0")==50&&g.failsInARow("k"+(StreamHealth.KEEP+19))==1);
        System.out.println("StreamHealthSmoke "+checks+" checks ok");
    }
}
