package in.ghartv.nova;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual WebView/native view performance regressions; no external network or media. */
@RunWith(AndroidJUnit4.class)
public class Review43PerformanceTest {
    Activity activity;WebView web;
    void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    Object field(Object o,String n)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    void start(){activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    JSONObject eval(String js)throws Exception{String[] raw={null};CountDownLatch l=new CountDownLatch(1);ui(()->web.evaluateJavascript(js,s->{raw[0]=s;l.countDown();}));assertTrue(l.await(5,TimeUnit.SECONDS));return new JSONObject((String)new JSONTokener(raw[0]).nextValue());}
    void plain(String body)throws Exception{
        start();CountDownLatch loaded=new CountDownLatch(1);
        ui(()->{web=new WebView(activity);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setBlockNetworkLoads(true);activity.setContentView(web);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){loaded.countDown();}});String url="https://flixmomo.app/movie/paper";web.loadDataWithBaseURL(url,"<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Owned performance test</title><style>body{font:18px sans-serif}button{padding:12px}video{width:400px;height:230px}img{width:120px;height:180px}</style>"+body,"text/html","UTF-8",url);});
        assertTrue(loaded.await(6,TimeUnit.SECONDS));SystemClock.sleep(120);
    }
    @After public void close(){if(activity!=null)ui(()->{if(web!=null){web.stopLoading();web.destroy();}activity.finish();});}
    String title(){return "<h2>Paper Lantern</h2><button>WATCH NOW</button><div id='overview'>An owned synopsis about a library reopening with the help of its local neighbourhood volunteers.</div>";}
    @Test public void tenStableReadsPerformOneHeavyScan()throws Exception{
        plain(title());JSONObject out=null;double first=0,last=0;
        for(int i=0;i<10;i++){out=eval(FilmPageSnapshot.read());if(i==0)first=out.getDouble("snapshotReadMs");last=out.getDouble("snapshotReadMs");}
        assertEquals(1,out.getInt("descriptiveFullScans"));assertEquals(9,out.getInt("descriptiveCacheHits"));
        JSONObject result=new JSONObject().put("scope","OWNED_STATIC_DOCUMENT_NOT_OWNER_DEVICE_BENCHMARK").put("reads",10).put("full_scans",1).put("cache_hits",9).put("first_read_ms",first).put("last_cached_read_ms",last);
        java.nio.file.Files.write(new java.io.File(activity.getExternalFilesDir(null),"review43-performance.json").toPath(),result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void changedSynopsisInvalidatesCache()throws Exception{
        plain(title());eval(FilmPageSnapshot.read());eval("JSON.stringify((()=>{document.querySelector('#overview').textContent='A different locally authored description must replace the old cached description after an actual DOM mutation.';return {ok:true}})())");
        JSONObject out=eval(FilmPageSnapshot.read());assertEquals(2,out.getInt("descriptiveFullScans"));assertTrue(out.getJSONObject("detail").getString("synopsis").contains("different locally authored"));
    }
    @Test public void playerChoicesInvalidateWhenAdded()throws Exception{
        plain("<h2>Owned player</h2><button>PLAYER #1</button>");assertEquals(1,eval(FilmPageSnapshot.read()).getJSONArray("players").length());
        eval("JSON.stringify((()=>{const b=document.createElement('button');b.textContent='PLAYER #2';document.body.appendChild(b);return {ok:true}})())");assertEquals(2,eval(FilmPageSnapshot.read()).getJSONArray("players").length());
    }
    @Test public void selectedPlayerAttributeInvalidatesCache()throws Exception{
        plain("<h2>Owned player</h2><button id='p' aria-selected='false'>PLAYER #1</button>");assertFalse(eval(FilmPageSnapshot.read()).getJSONArray("players").getJSONObject(0).getBoolean("selected"));
        eval("JSON.stringify((()=>{document.querySelector('#p').setAttribute('aria-selected','true');return {ok:true}})())");assertTrue(eval(FilmPageSnapshot.read()).getJSONArray("players").getJSONObject(0).getBoolean("selected"));
    }
    @Test public void navigationInvalidatesWithoutDocumentReplacement()throws Exception{
        plain(title());eval(FilmPageSnapshot.read());eval("JSON.stringify((()=>{history.pushState({},'', '/search?q=paper');return {ok:true}})())");JSONObject out=eval(FilmPageSnapshot.read());assertTrue(out.getBoolean("search"));assertEquals(2,out.getInt("descriptiveFullScans"));
    }
    @Test public void cacheDoesNotHoldPlaybackState()throws Exception{
        plain("<video></video><script>window.clock=1;window.paused=true;const v=document.querySelector('video');Object.defineProperty(v,'currentTime',{get:()=>window.clock});Object.defineProperty(v,'paused',{get:()=>window.paused});Object.defineProperty(v,'readyState',{get:()=>4});</script>");
        assertFalse(eval(FilmPageSnapshot.read()).getBoolean("mediaPlaying"));eval("JSON.stringify((()=>{window.clock=4;window.paused=false;return {ok:true}})())");JSONObject out=eval(FilmPageSnapshot.read());assertTrue(out.getBoolean("descriptiveCacheHit"));assertTrue(out.getBoolean("mediaPlaying"));assertEquals(4,out.getDouble("mediaTime"),.01);
    }
    @Test public void verificationPageCannotReturnCachedCatalogue()throws Exception{
        plain(title());eval(FilmPageSnapshot.read());eval("JSON.stringify((()=>{document.querySelector('h2').textContent='Verify you are human';return {ok:true}})())");assertEquals("PROVIDER_VERIFICATION_REQUIRED",eval(FilmPageSnapshot.read()).getString("state"));
    }
    @Test public void formChangeInvalidatesPropertyOnlySelection()throws Exception{
        plain("<h2>Owned source choices</h2><select><option>Player #1</option><option>Player #2</option></select>");eval(FilmPageSnapshot.read());
        eval("JSON.stringify((()=>{const s=document.querySelector('select');s.selectedIndex=1;s.dispatchEvent(new Event('change',{bubbles:true}));return {ok:true}})())");JSONObject out=eval(FilmPageSnapshot.read());assertEquals(2,out.getInt("descriptiveFullScans"));assertTrue(out.getJSONArray("players").getJSONObject(1).getBoolean("selected"));
    }
    JSONObject cards(int n,String tag)throws Exception{JSONArray rows=new JSONArray();for(int i=0;i<n;i++)rows.put(new JSONObject().put("url","https://flixmomo.app/movie/paper-"+i).put("title","Paper "+i).put("metadata",tag).put("image","https://image.tmdb.org/paper-"+i+".png"));return new JSONObject().put("results",rows);}
    FilmHomeView home(AtomicInteger loads){FilmHomeView view=new FilmHomeView(activity,new FilmHomeView.Host(){public void open(String u){}public void refresh(){}public void provider(){}public void privacy(){}},(image,url)->{loads.incrementAndGet();image.setImageDrawable(new android.graphics.drawable.ColorDrawable(0xff123456));});return view;}
    @Test public void artworkLoadsNearViewportAndOnScrollNotAllAtOnce()throws Exception{
        start();AtomicInteger loads=new AtomicInteger();FilmHomeView[] h={null};JSONObject rows=cards(60,"Owned artwork");ui(()->{h[0]=home(loads);activity.setContentView(h[0]);h[0].render(rows);});
        long end=SystemClock.elapsedRealtime()+4000;while(loads.get()==0&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(25);int first=loads.get();assertTrue(first>0);assertTrue("All sixty requests must not start together: "+first,first<30);
        ScrollView scroll=(ScrollView)field(h[0],"scroll");ui(()->scroll.fullScroll(View.FOCUS_DOWN));end=SystemClock.elapsedRealtime()+4000;while(loads.get()<=first&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(25);assertTrue(loads.get()>first);assertEquals(60,h[0].cardCount());
    }
    @Test public void focusedCardsRetainMatchingMetadataDuringBackgroundRead()throws Exception{
        start();AtomicInteger loads=new AtomicInteger();FilmHomeView[] h={null};JSONObject before=cards(2,"before"),after=cards(2,"after");ui(()->{h[0]=home(loads);activity.setContentView(h[0]);h[0].render(before);});SystemClock.sleep(200);
        ui(()->{h[0].focusRefresh();h[0].handleRemote(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN),activity.getCurrentFocus());h[0].render(after);assertEquals("before",h[0].card("https://flixmomo.app/movie/paper-0").optString("metadata"));});
    }
}
