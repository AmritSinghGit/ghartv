package in.ghartv.nova;

import android.app.AlertDialog;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review48InputStabilityTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();}
    Object f(String n){return h.field(h.activity,n);}
    void invoke(String n){try{java.lang.reflect.Method m=FlixMomoActivity.class.getDeclaredMethod(n);m.setAccessible(true);m.invoke(h.activity);}catch(Exception e){throw new AssertionError(e);}}
    RemoteWebCursor cursor(){return (RemoteWebCursor)f("cursor");}
    void full(AtomicInteger exits)throws Exception{h.player();h.ui(()->((FlixMomoActivity)h.activity).enterFullScreen(new FrameLayout(h.activity),exits::incrementAndGet));SystemClock.sleep(150);}
    void press(int key){h.press(key);}
    void raw(int action,int key){h.ui(()->h.activity.dispatchKeyEvent(new KeyEvent(action,key)));}
    @Test public void pointerStaysVisibleFourSecondsThenFadesAfterFive()throws Exception{
        h.player();h.mouse();h.ui(()->cursor().enter());SystemClock.sleep(4100);h.ui(()->assertEquals(View.VISIBLE,cursor().getVisibility()));
        SystemClock.sleep(1200);h.ui(()->{assertEquals(View.INVISIBLE,cursor().getVisibility());assertTrue(cursor().enabled());});
        press(KeyEvent.KEYCODE_DPAD_RIGHT);h.ui(()->assertEquals(View.VISIBLE,cursor().getVisibility()));
    }
    @Test public void shortArrowTapMovesWithoutWaitingForAnimationFrame()throws Exception{
        h.player();h.mouse();h.ui(()->{RemoteCursorState s=(RemoteCursorState)h.field(cursor(),"state");float x=s.x();cursor().handle(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));cursor().handle(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));assertTrue(s.x()>x);});
    }
    @Test public void heldArrowDoesNotFadeAndReleaseRestartsIdleTime()throws Exception{
        h.player();h.mouse();raw(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(5100);h.ui(()->assertEquals(View.VISIBLE,cursor().getVisibility()));raw(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(200);h.ui(()->assertEquals(View.VISIBLE,cursor().getVisibility()));
    }
    @Test public void physicalHoverWakesPointerWithoutClicking()throws Exception{
        AtomicInteger clicks=new AtomicInteger();full(new AtomicInteger());h.ui(()->{
            ((View)f("custom")).setOnClickListener(v->clicks.incrementAndGet());((Runnable)h.field(cursor(),"idleHide")).run();
            long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_HOVER_MOVE,80,90,0);e.setSource(InputDevice.SOURCE_MOUSE);
            try{h.activity.dispatchGenericMotionEvent(e);}finally{e.recycle();}
            assertEquals(View.VISIBLE,cursor().getVisibility());assertEquals(0,clicks.get());
        });
    }
    @Test public void fullscreenMovementAndClickSurviveIdleFade()throws Exception{
        AtomicInteger clicks=new AtomicInteger();full(new AtomicInteger());h.ui(()->{((View)f("custom")).setOnClickListener(v->clicks.incrementAndGet());((Runnable)h.field(cursor(),"idleHide")).run();});
        press(KeyEvent.KEYCODE_DPAD_LEFT);press(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(1,clicks.get());h.ui(()->assertEquals(View.VISIBLE,cursor().getVisibility()));
    }
    @Test public void menuToggleResumesSameFullscreenWithoutNavigation()throws Exception{
        AtomicInteger exits=new AtomicInteger();full(exits);Object view=f("custom");press(KeyEvent.KEYCODE_MENU);press(KeyEvent.KEYCODE_MENU);h.ui(()->{assertSame(view,f("custom"));assertFalse(((View)f("fullMenu")).isShown());assertTrue(cursor().enabled());});assertEquals(0,exits.get());
    }
    @Test public void cancelledBackDoesNotCloseFullscreen()throws Exception{
        AtomicInteger exits=new AtomicInteger();full(exits);raw(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK);h.ui(()->h.activity.dispatchKeyEvent(KeyEvent.changeFlags(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK),KeyEvent.FLAG_CANCELED)));assertEquals(0,exits.get());assertNotNull(f("custom"));
    }
    @Test public void repeatedBackUpCannotFallThroughIntoRestoredToolbar()throws Exception{
        AtomicInteger exits=new AtomicInteger();full(exits);press(KeyEvent.KEYCODE_BACK);raw(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK);assertEquals(1,exits.get());h.ui(()->{assertTrue(((View)f("pageButton")).hasFocus());assertFalse((Boolean)f("homeRequested"));});
    }
    @Test public void backFromRestoredToolbarGoesToResultsNotPageToggle()throws Exception{
        full(new AtomicInteger());press(KeyEvent.KEYCODE_BACK);press(KeyEvent.KEYCODE_BACK);h.ui(()->{assertTrue((Boolean)f("homeRequested"));assertTrue(((View)f("homePanel")).isShown());});
    }
    @Test public void cancelledMenuDoesNotOpenAnotherInputLayer()throws Exception{
        full(new AtomicInteger());raw(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU);h.ui(()->h.activity.dispatchKeyEvent(KeyEvent.changeFlags(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MENU),KeyEvent.FLAG_CANCELED)));h.ui(()->assertFalse(((View)f("fullMenu")).isShown()));
    }
    @Test public void lateFallbackCannotTakePointerFromNativeToolbar()throws Exception{
        h.player();h.ui(()->{FilmNativeControls.Host host=(FilmNativeControls.Host)h.field(f("nativeControls"),"host");host.actionOutcome("media","TARGET_OBSCURED");invoke("toolbar");});SystemClock.sleep(200);h.ui(()->{assertFalse((Boolean)f("mouseActive"));assertFalse(cursor().enabled());assertTrue(((View)f("pageButton")).hasFocus());});
    }
    @Test public void delayedPointerFallbackCannotLeaveFullscreen()throws Exception{
        h.player();h.ui(()->{((FilmNativeControls.Host)h.field(f("nativeControls"),"host")).actionOutcome("media","TARGET_CHANGED");((FlixMomoActivity)h.activity).enterFullScreen(new FrameLayout(h.activity),()->{});});SystemClock.sleep(200);h.ui(()->{assertNotNull(f("custom"));assertEquals(View.GONE,((View)f("browseRoot")).getVisibility());assertTrue(cursor().enabled());});
    }
    @Test public void pageFocusLossDoesNotHidePointerMode()throws Exception{
        h.player();h.mouse();h.ui(()->{h.web.getOnFocusChangeListener().onFocusChange(h.web,false);assertEquals(View.VISIBLE,cursor().getVisibility());assertTrue(cursor().enabled());});
    }
    @Test public void repeatedPendingPlayCannotQueueAnotherVideoToggle()throws Exception{
        h.player();h.ui(()->{try{Object c=f("nativeControls");java.lang.reflect.Field busy=c.getClass().getDeclaredField("pageBusy");busy.setAccessible(true);busy.set(c,true);FilmNativeControls controls=(FilmNativeControls)c;controls.pageAction("media");controls.pageAction("media");controls.pageAction("fullscreen");assertTrue(((java.util.ArrayDeque<?>)h.field(c,"pageActions")).isEmpty());busy.set(c,false);}catch(Exception e){throw new AssertionError(e);}});
    }
    @Test public void automaticTrayCannotReopenDuringUserPlay()throws Exception{
        h.player();h.ui(()->{FilmNativeControls c=(FilmNativeControls)f("nativeControls");c.suppressAutomaticTray();c.hideAll();c.refreshState();});SystemClock.sleep(400);h.ui(()->assertFalse(((FilmNativeControls)f("nativeControls")).visible()));
    }
    @Test public void localJournalRejectsArbitraryPrivateStringsAndIsBounded()throws Exception{
        FilmReviewJournal j=new FilmReviewJournal();j.record("https://private.example/title","PAGE",true,true);j.record("PLAY_REQUEST","movie-name",true,true);assertEquals(0,j.snapshot().getJSONArray("events").length());
        for(int n=0;n<180;n++)j.record("BACK","POINTER",true,false);JSONObject s=j.snapshot();assertEquals(128,s.getJSONArray("events").length());assertTrue(s.getBoolean("truncated"));assertFalse(s.getBoolean("network_upload"));assertFalse(s.toString().contains("private.example"));
        s.getJSONArray("events").getJSONObject(0).put("event","MUTATED");assertFalse(j.snapshot().toString().contains("MUTATED"));
    }
    @Test public void journalContainsActualMenuBackLayersNotMovieNames()throws Exception{
        full(new AtomicInteger());press(KeyEvent.KEYCODE_MENU);press(KeyEvent.KEYCODE_BACK);JSONObject s=((FilmReviewJournal)f("reviewJournal")).snapshot();String text=s.toString();assertTrue(text.contains("FULLSCREEN_MENU"));assertTrue(text.contains("FULLSCREEN_EXIT"));assertFalse(text.contains("flixmomo.app"));assertFalse(text.contains("lantern"));
    }
    @Test public void interactionLogAvailableFromOptions()throws Exception{
        h.player();h.ui(()->invoke("toolbar"));press(KeyEvent.KEYCODE_DPAD_CENTER);h.ui(()->{AlertDialog d=(AlertDialog)f("optionsDialog");assertTrue(d.isShowing());boolean found=false;for(int n=0;n<d.getListView().getAdapter().getCount();n++)if("Recent interaction log".equals(d.getListView().getAdapter().getItem(n)))found=true;assertTrue(found);});
    }
    @Test public void heldBackExitIsSingleAction()throws Exception{
        AtomicInteger exits=new AtomicInteger();full(exits);h.ui(()->{long t=SystemClock.uptimeMillis();h.activity.dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0));for(int n=1;n<4;n++)h.activity.dispatchKeyEvent(new KeyEvent(t,t+n,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,n));h.activity.dispatchKeyEvent(new KeyEvent(t,t+5,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0));});assertEquals(1,exits.get());h.ui(()->assertTrue(((View)f("pageButton")).hasFocus()));
    }
    @Test public void actualVideoPlayFullscreenMenuResumeAndExitKeepsPlayback()throws Exception{
        h.player();h.eval("JSON.stringify((()=>{const v=document.querySelector('video');v.loop=true;v.pause();v.currentTime=0;const b=document.createElement('button');b.textContent='Fullscreen';b.onclick=()=>v.requestFullscreen();v.before(b);return {ok:true}})())");
        h.ui(()->{FilmNativeControls c=(FilmNativeControls)f("nativeControls");c.menu();h.text((View)h.field(c,"tray"),"Play").requestFocus();});press(KeyEvent.KEYCODE_DPAD_CENTER);
        long until=SystemClock.elapsedRealtime()+6000;JSONObject v=new JSONObject();while(SystemClock.elapsedRealtime()<until){v=h.eval("JSON.stringify({t:document.querySelector('video').currentTime,p:document.querySelector('video').paused})");if(v.optDouble("t")>.35&&!v.optBoolean("p"))break;SystemClock.sleep(80);}assertTrue(v.toString(),v.optDouble("t")>.35);assertFalse(v.getBoolean("p"));
        h.ui(()->{FilmNativeControls c=(FilmNativeControls)f("nativeControls");c.menu();h.text((View)h.field(c,"tray"),"Fullscreen").requestFocus();});press(KeyEvent.KEYCODE_DPAD_CENTER);
        until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until&&f("custom")==null)SystemClock.sleep(60);assertNotNull(f("custom"));
        press(KeyEvent.KEYCODE_MENU);press(KeyEvent.KEYCODE_DPAD_CENTER);assertNotNull(f("custom"));press(KeyEvent.KEYCODE_BACK);h.ui(()->assertTrue(((View)f("pageButton")).hasFocus()));
        v=h.eval("JSON.stringify({t:document.querySelector('video').currentTime,p:document.querySelector('video').paused})");assertFalse("No repeated Play toggle or restart during menu/fullscreen transitions: "+v,v.getBoolean("p"));
    }
}
