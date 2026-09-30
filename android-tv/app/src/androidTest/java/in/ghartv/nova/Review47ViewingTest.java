package in.ghartv.nova;
import android.app.AlertDialog;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.widget.*;
import org.json.JSONObject;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review47ViewingTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    final AtomicInteger exits=new AtomicInteger();
    @After public void close(){h.close();}
    Object f(String n){return h.field(h.activity,n);}
    void invoke(String name){try{java.lang.reflect.Method m=FlixMomoActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(h.activity);}catch(Exception e){throw new AssertionError(e);}}
    void fullscreen()throws Exception{
        h.player();h.ui(()->{FrameLayout surface=new FrameLayout(h.activity);surface.setBackgroundColor(0xff123456);((FlixMomoActivity)h.activity).enterFullScreen(surface,exits::incrementAndGet);});SystemClock.sleep(180);
    }
    View button(String name){return h.text(h.activity.findViewById(android.R.id.content),name);}
    void proof(String name)throws Exception{
        android.graphics.Bitmap shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if(shot==null)throw new AssertionError("No test screenshot");
        java.io.File target=new java.io.File(h.activity.getExternalFilesDir(null),name);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(target)){if(!shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new AssertionError("Screenshot failed");}finally{shot.recycle();}
    }
    @Test public void trueFullscreenHidesAllBrowseCreditAndNotices()throws Exception{
        fullscreen();h.ui(()->{assertEquals(View.GONE,((View)f("browseRoot")).getVisibility());assertFalse(((View)f("credit")).isShown());assertFalse(((View)f("playNotice")).isShown());View host=(View)f("fullHost"),screen=(View)f("screen");assertEquals(screen.getWidth(),host.getWidth());assertEquals(screen.getHeight(),host.getHeight());assertFalse(((FilmNativeControls)f("nativeControls")).visible());});
    }
    @Test public void exitFullscreenReleasesPointerAndFocusesReachableOptions()throws Exception{
        fullscreen();h.press(KeyEvent.KEYCODE_BACK);h.ui(()->{assertNull(f("custom"));assertFalse((Boolean)f("mouseActive"));assertFalse(((RemoteWebCursor)f("cursor")).enabled());assertTrue(((View)f("pageButton")).hasFocus());assertEquals("Options",((TextView)h.activity.getCurrentFocus()).getText().toString());assertTrue(h.activity.getCurrentFocus().isShown());});assertEquals(1,exits.get());
        h.press(KeyEvent.KEYCODE_DPAD_CENTER);h.ui(()->assertTrue(((AlertDialog)f("optionsDialog")).isShowing()));
    }
    @Test public void pageRequestedFullscreenExitAlsoRestoresToolbar()throws Exception{
        fullscreen();h.ui(()->h.web.getWebChromeClient().onHideCustomView());h.ui(()->{assertNull(f("custom"));assertTrue(((View)f("pageButton")).hasFocus());});assertEquals(1,exits.get());
    }
    @Test public void fullscreenMenuIsTemporaryAndResumeRetainsSamePlayer()throws Exception{
        fullscreen();Object before=f("custom");h.press(KeyEvent.KEYCODE_MENU);h.ui(()->{assertTrue(((View)f("fullMenu")).isShown());assertFalse((Boolean)f("mouseActive"));assertEquals("Resume",((TextView)h.activity.getCurrentFocus()).getText().toString());});h.press(KeyEvent.KEYCODE_DPAD_CENTER);h.ui(()->{assertSame(before,f("custom"));assertFalse(((View)f("fullMenu")).isShown());assertTrue((Boolean)f("mouseActive"));});assertEquals(0,exits.get());
    }
    @Test public void fullscreenSourcesReturnToActualDynamicChoices()throws Exception{
        fullscreen();h.press(KeyEvent.KEYCODE_MENU);h.press(KeyEvent.KEYCODE_DPAD_RIGHT);h.press(KeyEvent.KEYCODE_DPAD_CENTER);h.ui(()->{assertNull(f("custom"));FilmNativeControls controls=(FilmNativeControls)f("nativeControls");assertTrue(controls.visible());View choices=(View)h.field(controls,"choices");assertNotNull(h.text(choices,"Player 12 · BEST"));});
    }
    @Test public void repeatedExitInvokesProviderCallbackOnce()throws Exception{
        fullscreen();h.ui(()->{h.web.getWebChromeClient().onHideCustomView();h.web.getWebChromeClient().onHideCustomView();});assertEquals(1,exits.get());
    }
    @Test public void fullscreenPointerStillReceivesActualOkClick()throws Exception{
        fullscreen();AtomicInteger clicks=new AtomicInteger();h.ui(()->{View v=(View)f("custom");v.setOnClickListener(w->clicks.incrementAndGet());});h.press(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(1,clicks.get());
    }
    @Test public void nativeExperimentAndDuplicateTogglesNotInNormalHeader()throws Exception{
        h.player();h.ui(()->invoke("toolbar"));h.ui(()->{for(String name:new String[]{"Use page","Cursor: on","Cursor: off","Scroll: off","Native player","Connection","Diagnostics"})assertNull(name,button(name));assertNotNull(button("Options"));assertNotNull(button("Search"));assertFalse((Boolean)f("NATIVE_PLAYER_EXPERIMENT"));});
    }
    @Test public void toolbarArrowsNotSwallowedAfterPointerExit()throws Exception{
        h.player();h.mouse();h.ui(()->invoke("toolbar"));h.press(KeyEvent.KEYCODE_DPAD_LEFT);h.ui(()->{assertTrue(((View)f("chrome")).hasFocus());assertFalse((Boolean)f("mouseActive"));assertFalse(h.web.hasFocus());});
    }
    @Test public void playerTrayContainsOnlyDistinctViewingActions()throws Exception{
        h.player();h.ui(()->{FilmNativeControls c=(FilmNativeControls)f("nativeControls");c.menu();View t=(View)h.field(c,"tray");for(String s:new String[]{"Play","Sources","Fullscreen","Mouse","More","Hide"})assertNotNull(s,h.text(t,s));for(String s:new String[]{"Watchlist","Not playing","Native player"})assertNull(s,h.text(t,s));});
    }
    @Test public void idleMouseFadesWithoutDisablingRemoteClick()throws Exception{
        h.player();h.mouse();h.ui(()->{RemoteWebCursor c=(RemoteWebCursor)f("cursor");((Runnable)h.field(c,"idleHide")).run();assertEquals(View.INVISIBLE,c.getVisibility());assertTrue(c.enabled());});h.press(KeyEvent.KEYCODE_DPAD_RIGHT);h.ui(()->assertEquals(View.VISIBLE,((View)f("cursor")).getVisibility()));
    }
    @Test public void nearbyArtworkPreloadsTwoRowsWithoutScroll()throws Exception{
        h.start();AtomicInteger loads=new AtomicInteger();JSONObject cards=h.cards(60);h.ui(()->{FilmHomeView home=h.home(loads);h.activity.setContentView(home);home.render(cards);});h.waitLoads(loads,8);assertTrue(loads.get()<30);
    }
    JSONObject detail(String slug)throws Exception{return new JSONObject().put("url","https://flixmomo.app/movie/"+slug).put("title","Owned film "+slug).put("synopsis","An owned story created for this controlled test, not a provider movie.").put("watch",true).put("facts",new JSONObject());}
    @Test public void visitCacheIsBoundedAndExpiresWithoutNetwork()throws Exception{
        long[] time={10};FilmVisitCache c=new FilmVisitCache(()->time[0]);for(int i=0;i<20;i++)c.put(detail("x"+i));assertEquals(12,c.size());assertNull(c.get("https://flixmomo.app/movie/x0"));assertNotNull(c.get("https://flixmomo.app/movie/x19"));time[0]+=600001;assertNull(c.get("https://flixmomo.app/movie/x19"));
    }
    @Test public void visitCacheDoesNotShareMutableProviderData()throws Exception{
        FilmVisitCache c=new FilmVisitCache();JSONObject original=detail("one");c.put(original);original.put("title","MUTATED");JSONObject read=c.get("https://flixmomo.app/movie/one");assertEquals("Owned film one",read.getString("title"));read.put("title","MUTATED AGAIN");assertEquals("Owned film one",c.get("https://flixmomo.app/movie/one").getString("title"));c.clear();assertEquals(0,c.size());
    }
    @Test public void visitCacheRejectsOtherOriginsAndUrlSecrets()throws Exception{
        FilmVisitCache c=new FilmVisitCache();for(String u:new String[]{"https://unknown.example/movie/one","https://flixmomo.app/movie/one?token=private","https://flixmomo.app/movie/one#private","http://flixmomo.app/movie/one"})c.put(detail("one").put("url",u));assertEquals(0,c.size());
    }
    @Test public void cachedDetailsNeverAuthorizeWatchBeforeCurrentPageLoads()throws Exception{
        h.start();JSONObject d=detail("one");h.ui(()->{FilmDetailView v=new FilmDetailView(h.activity,new FilmDetailView.Host(){public void watch(){}public void watchlist(){}public void original(){}public void back(){}});h.activity.setContentView(v);v.begin(d.optString("url"),null);v.preview(d);assertEquals("Owned film one",v.currentTitle());assertFalse(h.text(v,"Watch now").isEnabled());assertTrue(((ProgressBar)h.field(v,"progress")).isShown());});
    }
    @Test public void returningToCardsDoesNotReloadOrLoseProviderPage()throws Exception{
        h.player();String before=h.eval("JSON.stringify({url:location.href})").getString("url");h.ui(()->invoke("returnToCards"));assertEquals(before,h.eval("JSON.stringify({url:location.href})").getString("url"));
    }
    @Test public void explicitFullscreenActionEntersActualWebViewCustomMode()throws Exception{
        h.player();h.eval("JSON.stringify((()=>{const v=document.querySelector('video');const b=document.createElement('button');b.textContent='Fullscreen';b.onclick=()=>v.requestFullscreen();v.before(b);b.scrollIntoView({block:'center'});return {ok:true}})())");
        h.ui(()->{FilmNativeControls c=(FilmNativeControls)f("nativeControls");c.menu();h.text((View)h.field(c,"tray"),"Fullscreen").requestFocus();});h.press(KeyEvent.KEYCODE_DPAD_CENTER);
        long end=SystemClock.elapsedRealtime()+5000;boolean[] yes={false};while(SystemClock.elapsedRealtime()<end){h.ui(()->yes[0]=f("custom")!=null);if(yes[0])break;SystemClock.sleep(50);}assertTrue("A trusted click must enter actual provider fullscreen",yes[0]);SystemClock.sleep(300);proof("review47-owned-fullscreen.png");h.press(KeyEvent.KEYCODE_MENU);proof("review47-fullscreen-menu.png");h.press(KeyEvent.KEYCODE_BACK);proof("review47-toolbar-return.png");
    }
}
