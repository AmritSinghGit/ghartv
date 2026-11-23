package in.ghartv.nova;

import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Dns;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review58IptvAndDnsTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();}
    private String playlist(String lines){return "#EXTM3U\n"+lines;}

    @Test public void parserAcceptsSecureMetadataAndKeepsSourcesSeparate(){
        IptvPlaylistParser.Result result=IptvPlaylistParser.parse(playlist("#EXTINF:-1 tvg-id=\"owned.id\" tvg-logo=\"https://images.example/logo.png\" group-title=\"News\" http-user-agent=\"Owned UA\",Owned News\nhttps://media.example/live.m3u8\n"));
        assertEquals(1,result.channels.size());IptvChannel c=result.channels.get(0);assertEquals("owned.id",c.id);assertEquals("Owned News",c.name);assertEquals("News",c.group);assertEquals("Owned UA",c.userAgent);assertEquals("https://media.example/live.m3u8",c.streamUrl);
    }
    @Test public void parserRejectsCleartextPrivateAndUnsupportedStreams(){
        String raw=playlist("#EXTINF:-1,Clear\nhttp://public.example/live.m3u8\n#EXTINF:-1,Private\nhttps://127.0.0.1/live.m3u8\n#EXTINF:-1,File\nfile:///tmp/live.m3u8\n#EXTINF:-1,Secure\nhttps://public.example/live.m3u8\n");
        IptvPlaylistParser.Result result=IptvPlaylistParser.parse(raw);assertEquals(1,result.channels.size());assertEquals("Secure",result.channels.get(0).name);assertEquals(3,result.insecureOrPrivateSkipped);
    }
    @Test public void parserDeduplicatesAndNumbersSecureChannels(){
        String raw=playlist("#EXTINF:-1,First\nhttps://public.example/live.m3u8\n#EXTINF:-1,Duplicate\nhttps://public.example/live.m3u8\n#EXTINF:-1,Second\nhttps://public.example/second.m3u8\n");
        IptvPlaylistParser.Result result=IptvPlaylistParser.parse(raw);assertEquals(2,result.channels.size());assertEquals(1,result.duplicateSkipped);assertEquals(1,result.channels.get(0).number);assertEquals(2,result.channels.get(1).number);
    }
    @Test public void parserHonoursExtVlcUserAgentAndSecureReferrer(){
        String raw=playlist("#EXTINF:-1 group-title=\"Movies\",Owned Films\n#EXTVLCOPT:http-user-agent=Fixture Agent\n#EXTVLCOPT:http-referrer=https://ref.example/page\nhttps://public.example/film.m3u8\n");
        IptvChannel c=IptvPlaylistParser.parse(raw).channels.get(0);assertEquals("Fixture Agent",c.userAgent);assertEquals("https://ref.example/page",c.referrer);
    }
    @Test public void iptvSearchRequiresAllWordsAndRespectsGroup(){
        IptvChannel a=new IptvChannel();a.name="Punjab Public News";a.group="News";a.id="a";a.streamUrl="https://public.example/a.m3u8";
        IptvChannel b=new IptvChannel();b.name="Punjab Music";b.group="Music";b.id="b";b.streamUrl="https://public.example/b.m3u8";
        assertEquals(Arrays.asList(a),IptvRepository.filter(Arrays.asList(a,b),"All","public punjab"));assertEquals(Arrays.asList(b),IptvRepository.filter(Arrays.asList(a,b),"Music","punjab"));
        List<String> groups=IptvRepository.groups(Arrays.asList(a,b));assertEquals("All",groups.get(0));assertEquals(1,java.util.Collections.frequency(groups,"All"));
    }

    private static InetAddress address(){return InetAddress.getLoopbackAddress();}
    @Test public void jioDnsUsesSystemBeforeFallback()throws Exception{
        AtomicInteger fallback=new AtomicInteger();Dns system=host->Arrays.asList(address()),secondary=host->{fallback.incrementAndGet();return Arrays.asList(address());};JioSecureDns dns=new JioSecureDns(system,secondary);assertEquals(1,dns.lookup("jiotvapi.cdn.jio.com").size());assertEquals(0,fallback.get());
    }
    @Test public void jioDnsFallsBackOnlyAfterSystemFailureForJio()throws Exception{
        AtomicInteger fallback=new AtomicInteger();Dns system=host->{throw new UnknownHostException(host);},secondary=host->{fallback.incrementAndGet();return Arrays.asList(address());};JioSecureDns dns=new JioSecureDns(system,secondary);assertEquals(1,dns.lookup("jiotvapi.cdn.jio.com").size());assertEquals(1,fallback.get());assertEquals(1,dns.fallbackCount());
    }
    @Test public void jioDnsDoesNotFallbackForLookalikeOrUnrelatedHost()throws Exception{
        AtomicInteger fallback=new AtomicInteger();Dns system=host->{throw new UnknownHostException(host);},secondary=host->{fallback.incrementAndGet();return Arrays.asList(address());};JioSecureDns dns=new JioSecureDns(system,secondary);
        for(String host:new String[]{"jiotvapi.cdn.jio.com.evil.invalid","notjio.com","iptv-org.github.io"})try{dns.lookup(host);fail(host);}catch(UnknownHostException expected){}assertEquals(0,fallback.get());
    }
    @Test public void jioDnsPreservesFailureWhenSecureFallbackAlsoFails(){
        Dns fail=host->{throw new UnknownHostException(host);};JioSecureDns dns=new JioSecureDns(fail,fail);try{dns.lookup("jiotvapi.media.jio.com");fail();}catch(UnknownHostException expected){assertTrue(expected.getSuppressed().length>=1);}
    }

    private void startIptv()throws Exception{
        h.activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review58IptvHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long end=SystemClock.elapsedRealtime()+4000;while(SystemClock.elapsedRealtime()<end){IptvChannelAdapter adapter=(IptvChannelAdapter)h.field(h.activity,"adapter");if(adapter!=null&&adapter.getItemCount()==3)return;SystemClock.sleep(30);}fail("IPTV fixture guide did not load");
    }
    @Test public void separateIptvGuideLoadsWithoutJioSessionAndShowsThreeSources()throws Exception{
        startIptv();h.ui(()->{View root=h.activity.getWindow().getDecorView();assertNotNull(h.text(root,"Jio TV"));assertNotNull(h.text(root,"FlixMomo"));assertNotNull(h.text(root,"PUBLIC IPTV"));assertEquals(3,((IptvChannelAdapter)h.field(h.activity,"adapter")).getItemCount());});
    }
    @Test public void iptvSearchFiltersAndOpensOnlyTheSelectedPublicChannel()throws Exception{
        startIptv();h.ui(()->{((EditText)h.field(h.activity,"query")).setText("owned films");try{java.lang.reflect.Method m=IptvActivity.class.getDeclaredMethod("applyFilter",boolean.class);m.setAccessible(true);m.invoke(h.activity,true);}catch(Exception e){throw new AssertionError(e);}});SystemClock.sleep(120);
        h.ui(()->{IptvChannelAdapter adapter=(IptvChannelAdapter)h.field(h.activity,"adapter");assertEquals(1,adapter.getItemCount());IptvChannel selected=adapter.itemAt(0);assertNotNull(selected);((Review58IptvHarnessActivity)h.activity).openChannel(selected);assertEquals("Owned Films",((Review58IptvHarnessActivity)h.activity).opened.name);});
    }
    @Test public void productionIptvPlayerRendersOwnedFirstFrame()throws Exception{
        IptvChannel channel=new IptvChannel();channel.number=1;channel.id="owned";channel.name="Owned IPTV fixture";channel.group="Test";channel.streamUrl="https://public.example/owned.m3u8";
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review58IptvPlayerHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(IptvPlayerActivity.EXTRA_CHANNEL_JSON,channel.toJson().toString());h.activity=InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        long end=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<end){if((Boolean)h.field(h.activity,"firstFrameObserved"))return;SystemClock.sleep(40);}fail("Owned IPTV player fixture produced no first frame");
    }
}
