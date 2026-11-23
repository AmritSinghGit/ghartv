package in.ghartv.nova;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Production Discover flow (provider pages under the pointer): ONE pointer click on a poster reaches
 * a playing video. Pages with several Watch actions, Back, and titles not opened by a click stay manual.
 */
@RunWith(AndroidJUnit4.class)
public class Review64OneClickPlayTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();}
    Object f(String n){return h.field(h.activity,n);}
    boolean flag(String n){return (Boolean)f(n);}
    void invoke(String name,Class<?>[] types,Object... values){try{java.lang.reflect.Method m=FlixMomoActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(h.activity,values);}catch(Exception e){throw new AssertionError(e);}}
    JSONObject page()throws Exception{
        return h.eval("JSON.stringify((()=>{const v=document.querySelector('video');return {p:location.pathname,ready:document.readyState,t:v?v.currentTime:0,paused:v?v.paused:true,clicks:window.mediaClicks||0,trusted:!!window.trustedMedia}})())");
    }
    void waitPath(String path)throws Exception{
        long until=SystemClock.elapsedRealtime()+10000;JSONObject s=new JSONObject();
        while(SystemClock.elapsedRealtime()<until){s=page();if(path.equals(s.optString("p"))&&"complete".equals(s.optString("ready"))){SystemClock.sleep(150);return;}SystemClock.sleep(50);}
        fail("Expected "+path+" but page is "+s);
    }
    JSONObject point(String selector)throws Exception{return h.eval("JSON.stringify((()=>{const e=document.querySelector("+JSONObject.quote(selector)+");e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,w:innerWidth,h:innerHeight}})())");}
    /** The viewer's click: move the pointer onto the element and press OK. */
    void click(String selector)throws Exception{
        JSONObject p=point(selector);SystemClock.sleep(100);
        h.ui(()->{RemoteCursorState s=(RemoteCursorState)h.field(f("cursor"),"state");s.position((float)(p.optDouble("x")*h.web.getWidth()/p.optDouble("w")),(float)(p.optDouble("y")*h.web.getHeight()/p.optDouble("h")));});
        h.press(KeyEvent.KEYCODE_DPAD_CENTER);
    }
    /** Clicks the poster; like a viewer, presses OK again if the pointer had faded and the page did not move. */
    void clickPoster()throws Exception{
        for(int attempt=0;attempt<3;attempt++){
            click("a");
            long until=SystemClock.elapsedRealtime()+4000;
            while(SystemClock.elapsedRealtime()<until){if(!"/search".equals(page().optString("p")))return;SystemClock.sleep(100);}
        }
    }
    /** Search results page (the fixture serves its poster page for any search) with the pointer on. */
    void results(boolean duplicateWatch)throws Exception{
        h.activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review64OneClickHarness.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("duplicate_watch",duplicateWatch));
        h.web=(WebView)f("browser");
        long until=SystemClock.elapsedRealtime()+7000;while(SystemClock.elapsedRealtime()<until&&((Review64OneClickHarness)h.activity).requested.isEmpty())SystemClock.sleep(30);
        h.ui(()->((EditText)f("query")).setText("Paper Lantern"));h.ui(()->invoke("search",new Class[]{}));
        waitPath("/search");
        h.ui(()->assertTrue("pointer is on the results page",flag("mouseActive")));
    }
    JSONObject waitPlaying()throws Exception{
        long until=SystemClock.elapsedRealtime()+25000;JSONObject s=new JSONObject();
        while(SystemClock.elapsedRealtime()<until){s=page();if("/watch/lantern".equals(s.optString("p"))&&s.optDouble("t")>.35&&!s.optBoolean("paused"))return s;SystemClock.sleep(100);}
        fail("Video did not start after one poster click: "+s);return s;
    }
    @Test public void onePosterClickPlaysTheVideoWithoutFurtherInput()throws Exception{
        results(false);clickPoster();
        JSONObject s=waitPlaying();
        assertEquals("exactly one provider-player click",1,s.optInt("clicks"));
        assertTrue("a genuine touch, not a script call",s.optBoolean("trusted"));
        h.ui(()->{assertFalse("no Sources tray over the video",((FilmNativeControls)f("nativeControls")).visible());assertTrue(flag("pointerPage"));});
    }
    @Test public void chainClearsOnceTheVideoIsPlaying()throws Exception{
        results(false);clickPoster();waitPlaying();
        long until=SystemClock.elapsedRealtime()+10000;boolean[] armed={true};
        while(SystemClock.elapsedRealtime()<until){h.ui(()->armed[0]=flag("oneClickPlay"));if(!armed[0])break;SystemClock.sleep(100);}
        assertFalse("one-click state must clear once playback is observed or the window lapses",armed[0]);
    }
    @Test public void backFromWatchPageDoesNotReplay()throws Exception{
        results(false);clickPoster();waitPlaying();
        h.press(KeyEvent.KEYCODE_BACK);waitPath("/movie/lantern");SystemClock.sleep(3500);
        assertEquals("Back stays on the title page","/movie/lantern",page().optString("p"));
        h.ui(()->{assertFalse(flag("oneClickPlay"));assertTrue("pointer stays available",flag("mouseActive"));});
    }
    @Test public void pageWithSeveralWatchActionsStaysManual()throws Exception{
        results(true);clickPoster();waitPath("/movie/lantern");SystemClock.sleep(3500);
        assertEquals("no guessed Watch action","/movie/lantern",page().optString("p"));
        h.ui(()->((Runnable)f("oneClickExpire")).run());
        h.ui(()->{assertFalse(flag("oneClickPlay"));assertTrue(flag("mouseActive"));assertTrue(((TextView)f("playNotice")).getText().toString().contains("Choose"));});
    }
    @Test public void viewerWatchClickOnAmbiguousPageStillStartsThePlayer()throws Exception{
        results(true);clickPoster();waitPath("/movie/lantern");SystemClock.sleep(1500);
        click("a");   // the viewer picks the first Watch action themselves
        waitPlaying();
    }
    @Test public void titleOpenedWithoutAPointerClickIsNotAutoPlayed()throws Exception{
        results(false);
        h.ui(()->invoke("openProviderTitle",new Class[]{String.class},"https://flixmomo.app/movie/lantern"));
        waitPath("/movie/lantern");SystemClock.sleep(3500);
        assertEquals("/movie/lantern",page().optString("p"));
        h.ui(()->assertFalse(flag("oneClickPlay")));
    }
}
