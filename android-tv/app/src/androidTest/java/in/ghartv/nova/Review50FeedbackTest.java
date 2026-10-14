package in.ghartv.nova;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebViewClient;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Later owner feedback. No provider streams or account state used. */
@RunWith(AndroidJUnit4.class)
public class Review50FeedbackTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    final AtomicInteger exits=new AtomicInteger();
    @After public void close(){h.close();}
    Object f(String name){return h.field(h.activity,name);}
    FilmPlaybackMonitor monitor(){return (FilmPlaybackMonitor)f("startMonitor");}
    FilmNativeControls controls(){return (FilmNativeControls)f("nativeControls");}
    void full()throws Exception{h.player();h.ui(()->((FlixMomoActivity)h.activity).enterFullScreen(new FrameLayout(h.activity),exits::incrementAndGet));SystemClock.sleep(100);}
    void invoke(String name){try{java.lang.reflect.Method m=FlixMomoActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(h.activity);}catch(Exception e){throw new AssertionError(e);}}
    String label(){return ((TextView)h.field(monitor(),"label")).getText().toString();}
    void raw(int action,int key,int repeat,int flags){long t=SystemClock.uptimeMillis();h.ui(()->h.activity.dispatchKeyEvent(new KeyEvent(t,t,action,key,repeat,0,-1,0,flags)));}

    @Test public void supportingFailureIsRecordedButNeverPaintedAsVideoFailure()throws Exception{
        h.player();h.ui(()->{monitor().leave();monitor().begin();monitor().requestFailure("Embedded request error -1");assertFalse(label().contains("Embedded request"));assertTrue(monitor().running());JSONObject e=monitor().evidence();assertEquals("Embedded request error -1",e.optString("resource_error"));assertEquals(1,e.optInt("supporting_resource_errors"));assertFalse(e.optBoolean("supporting_error_is_playback_failure"));});
    }
    @Test public void aManualTimeoutCanBeDismissedWithoutClaimingPlaybackSuccess()throws Exception{
        h.player();h.ui(()->{monitor().leave();monitor().begin();monitor().budget.unavailable("Playback status unavailable");monitor().controlsOpen(false);assertTrue(monitor().panel.isShown());monitor().pause();assertEquals(View.GONE,monitor().panel.getVisibility());assertFalse(monitor().healthy());assertEquals(FilmStartBudget.State.MANUAL,monitor().budget.state);});
    }
    @Test public void furtherSupportingErrorsCannotReopenDismissedPanel()throws Exception{
        h.player();h.ui(()->{monitor().leave();monitor().begin();monitor().pause();monitor().requestFailure("Embedded HTTP 403");monitor().controlsOpen(false);assertEquals(View.GONE,monitor().panel.getVisibility());assertFalse(monitor().running());});
    }
    @Test public void supportingErrorCannotChangeAnObservedPlayingState()throws Exception{
        h.player();h.ui(()->{monitor().leave();monitor().budget.state=FilmStartBudget.State.PLAYING;monitor().requestFailure("Embedded HTTP 404");assertTrue(monitor().healthy());assertEquals(View.GONE,monitor().panel.getVisibility());});
    }
    @Test public void supportingDiagnosticsAreBounded()throws Exception{
        h.player();h.ui(()->{monitor().leave();monitor().begin();for(int i=0;i<100;i++)monitor().requestFailure("Embedded request error -1");assertEquals(64,monitor().evidence().optInt("supporting_resource_errors"));assertFalse(label().contains("error -1"));});
    }
    @Test public void explicitNewAttemptRestoresOnlyFreshProgress()throws Exception{
        h.player();h.ui(()->{monitor().leave();monitor().begin();monitor().requestFailure("Old request");monitor().pause();monitor().begin();assertTrue(monitor().panel.isShown());assertFalse(monitor().evidence().optBoolean("manual_controls_requested"));assertEquals("",monitor().evidence().optString("resource_error"));assertTrue(label().startsWith(ReviewDisplay.label()));});
    }
    @Test public void hiddenParentCannotOwnMonitorFocus()throws Exception{
        h.player();h.ui(()->{monitor().leave();monitor().begin();h.text(monitor().panel,"Cancel").requestFocus();assertTrue(monitor().ownsFocus());((View)f("browseRoot")).setVisibility(View.GONE);assertFalse(monitor().ownsFocus());});
    }
    @Test public void fullscreenStopsObservationWithoutPausingTheMovie()throws Exception{
        full();h.ui(()->{assertFalse(monitor().running());assertFalse(monitor().panel.isShown());assertFalse(monitor().ownsFocus());assertNotNull(f("custom"));});
    }
    @Test public void fullscreenFExitsOnceAndReturnsOptionsFocus()throws Exception{
        full();h.press(KeyEvent.KEYCODE_F);SystemClock.sleep(100);h.ui(()->{assertNull(f("custom"));assertTrue(((View)f("pageButton")).hasFocus());assertFalse((Boolean)f("mouseActive"));assertEquals(View.GONE,monitor().panel.getVisibility());});assertEquals(1,exits.get());
    }
    @Test public void cancelledFullscreenFDoesNotExit()throws Exception{
        full();raw(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_F,0,0);raw(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_F,0,KeyEvent.FLAG_CANCELED);h.ui(()->assertNotNull(f("custom")));assertEquals(0,exits.get());
    }
    @Test public void repeatedFKeyReleaseCannotExitTwice()throws Exception{
        full();raw(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_F,0,0);raw(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_F,3,0);raw(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_F,0,0);raw(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_F,0,0);assertEquals(1,exits.get());
    }
    @Test public void fullscreenEscapeRestoresReachableToolbar()throws Exception{
        full();h.press(KeyEvent.KEYCODE_ESCAPE);h.ui(()->{assertNull(f("custom"));assertTrue(((View)f("pageButton")).hasFocus());});assertEquals(1,exits.get());
    }
    @Test public void escapeFromMouseRevealsControlsWithoutHistoryNavigation()throws Exception{
        h.player();String url=h.eval("JSON.stringify({url:location.href})").getString("url");h.mouse();h.press(KeyEvent.KEYCODE_ESCAPE);h.ui(()->{assertFalse((Boolean)f("mouseActive"));assertTrue(controls().visible());assertTrue(controls().ownsFocus());assertFalse(monitor().panel.isShown());});assertEquals(url,h.eval("JSON.stringify({url:location.href})").getString("url"));
    }
    @Test public void delayedWebFocusAfterFullscreenExitIsCorrected()throws Exception{
        full();h.ui(()->{h.web.getWebChromeClient().onHideCustomView();h.web.requestFocus();});SystemClock.sleep(150);h.ui(()->assertTrue(((View)f("pageButton")).hasFocus()));
    }
    @Test public void oneViewerVersionIdentityAndTechnicalVersionOnlyInDetails()throws Exception{
        assertEquals("Review "+BuildConfig.VERSION_CODE,ReviewDisplay.label());assertEquals("GharTV Films · Review "+BuildConfig.VERSION_CODE,ReviewDisplay.films());assertTrue(ReviewDisplay.technical().contains(BuildConfig.VERSION_NAME));assertFalse(ReviewDisplay.title().contains("rc14"));
        h.player();h.ui(()->{invoke("toolbar");assertNotNull(h.text((View)f("chrome"),ReviewDisplay.films()));invoke("showOptions");assertTrue(((android.app.AlertDialog)f("optionsDialog")).isShowing());});
    }
    @Test public void realProviderFullscreenCallbackThenFDoesNotReloadOwnedVideo()throws Exception{
        h.player();h.eval("JSON.stringify((()=>{const v=document.querySelector('video');const b=document.createElement('button');b.textContent='Fullscreen';b.onclick=()=>v.requestFullscreen();v.before(b);b.scrollIntoView({block:'center'});window.loadCount=0;v.addEventListener('loadstart',()=>window.loadCount++);return {ok:true}})())");
        h.ui(()->{controls().menu();h.text((View)h.field(controls(),"tray"),"Fullscreen").requestFocus();});h.press(KeyEvent.KEYCODE_DPAD_CENTER);
        boolean[] yes={false};long until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until){h.ui(()->yes[0]=f("custom")!=null);if(yes[0])break;SystemClock.sleep(30);}assertTrue("Real WebView fullscreen",yes[0]);
        h.press(KeyEvent.KEYCODE_F);SystemClock.sleep(100);h.ui(()->{assertNull(f("custom"));assertTrue(((View)f("pageButton")).hasFocus());});assertEquals(0,h.eval("JSON.stringify({loads:window.loadCount})").getInt("loads"));
    }
}
