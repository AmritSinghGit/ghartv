package in.ghartv.nova;

import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Production page/controller in Android WebView. Local PNG pixels, no provider requests. */
@RunWith(AndroidJUnit4.class)
public class FilmNativeControlsTest {
    private PreviewHarnessActivity activity;
    private WebView browser;
    private FilmNativeControls controls;
    private volatile String navigated="";
    private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    @Before public void open(){Intent i=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(i);ui(()->{browser=new WebView(activity);browser.getSettings().setJavaScriptEnabled(true);activity.setContentView(browser);});}
    @After public void close(){ui(()->{if(controls!=null)controls.destroy();browser.destroy();activity.finish();});}
    private void document(String url,String html) throws Exception {
        // A missing src is a broken-image icon, not a loaded poster: Chromium can
        // ignore HTML width/height for that icon. Use actual local image bytes.
        String pixels="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGOQy8z+DwADjQHyoDTvrgAAAABJRU5ErkJggg==";
        final String fixture=html.replace("<img ","<img src='"+pixels+"' ");
        CountDownLatch latch=new CountDownLatch(1);
        ui(()->{browser.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView w,String u){latch.countDown();}});browser.loadDataWithBaseURL(url,"<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Local provider layout fixture</title>"+fixture,"text/html","UTF-8",null);});
        assertTrue(latch.await(6,TimeUnit.SECONDS));SystemClock.sleep(150);
    }
    private JSONObject evaluate(String js) throws Exception {CountDownLatch done=new CountDownLatch(1);String[] value={null};ui(()->browser.evaluateJavascript(js,v->{value[0]=v;done.countDown();}));assertTrue(done.await(3,TimeUnit.SECONDS));Object unwrapped=new JSONTokener(value[0]).nextValue();return new JSONObject((String)unwrapped);}
    @Test public void returnsOnlyRealVisibleProviderTitleLinks() throws Exception {document("https://flixmomo.app/search?q=test","<a href='/movie/1'>Actual local title</a><a href='/movie/1'>Duplicate</a><a href='https://other.invalid/movie/1'>External</a><a style='display:none' href='/tv/2'>Hidden</a><a href='/login'>Login</a>");JSONObject r=evaluate(FilmPageSnapshot.read());assertEquals("SNAPSHOT",r.getString("state"));assertEquals(1,r.getJSONArray("results").length());assertEquals("Actual local title",r.getJSONArray("results").getJSONObject(0).getString("title"));assertEquals(0,r.getJSONArray("players").length());}
    @Test public void reportsOnlyActuallyOfferedPlayersNotFixedSix() throws Exception {document("https://flixmomo.st/movie/1","<button>Player 1</button><button>Player 2</button><button disabled>Player 3</button><button>Donate</button><button style='display:none'>Server 4</button>");JSONObject r=evaluate(FilmPageSnapshot.read());assertEquals(2,r.getJSONArray("players").length());assertEquals("Player 1",r.getJSONArray("players").getJSONObject(0).getString("label"));assertFalse(r.getBoolean("mediaObservable"));assertEquals(0,r.getInt("mediaError"));}
    @Test public void clickSelectsExistingProviderControlNotVideoResource() throws Exception {String url="https://flixmomo.st/movie/1";document(url,"<button onclick=\"document.title='selected-two'\">Player 2</button>");assertEquals("SELECTION_REQUESTED",evaluate(FilmPageSnapshot.select(url,0,"Player 2")).getString("state"));String[] title={null};ui(()->title[0]=browser.getTitle());assertEquals("selected-two",title[0]);}
    @Test public void wrongPageOrChangedPlayerCannotClick() throws Exception {String url="https://flixmomo.app/movie/1";document(url,"<button onclick=\"document.title='wrong'\">Player 2</button>");assertEquals("STALE_PAGE",evaluate(FilmPageSnapshot.select("https://flixmomo.app/movie/2",0,"Player 2")).getString("state"));assertEquals("PLAYER_CHANGED",evaluate(FilmPageSnapshot.select(url,0,"Player 1")).getString("state"));}
    @Test public void providerChallengeStopsExtractionRatherThanEvading() throws Exception {document("https://flixmomo.app/dummy","<h1>Verify you are human</h1><a href='/movie/1'>Must not extract</a>");assertEquals("PROVIDER_VERIFICATION_REQUIRED",evaluate(FilmPageSnapshot.read()).getString("state"));}
    @Test public void foreignOriginCannotUseTheReaderOrSelector() throws Exception {document("https://example.invalid/movie/1","<button>Player 1</button>");assertEquals("ORIGIN_REJECTED",evaluate(FilmPageSnapshot.read()).getString("state"));assertEquals("ORIGIN_REJECTED",evaluate(FilmPageSnapshot.select("https://example.invalid/movie/1",0,"Player 1")).getString("state"));}
    private String posters(){return "<style>body{background:#111b26;color:white;font:16px sans-serif;padding:16px}section{display:flex;gap:24px}a{display:block;width:120px;color:white;text-decoration:none}img{width:120px;height:175px;background:linear-gradient(#286d6a,#102735)}h3{font-size:15px}small{color:#b1c5d4}</style><h1>LOCAL TEST FIXTURE · Review 36</h1><p>Original poster elements stay on the same page</p><section><a id='one' href='/movie/one'><img width='120' height='175' alt='First Title'><h3>First Title</h3><small>2024 · Adventure</small></a><a id='two' href='/movie/two'><img width='120' height='175' alt='Second Title'><h3>Second Title</h3><small>2025 · Mystery</small></a></section>";}
    @Test public void posterArtworkRemainsAndScanDoesNotResetFocus() throws Exception {document("https://flixmomo.app/search?q=test",posters());evaluate("(()=>{window.savedImage=document.images[0];return JSON.stringify({ok:true})})()");assertEquals("READY",evaluate(FilmPosterNavigation.script("scan")).getString("state"));assertEquals(0,evaluate(FilmPosterNavigation.script("focus")).getInt("index"));assertEquals(1,evaluate(FilmPosterNavigation.script("right")).getInt("index"));evaluate(FilmPosterNavigation.script("scan"));evaluate(FilmPosterNavigation.script("scan"));JSONObject s=evaluate("JSON.stringify({same:savedImage===document.images[0],focus:document.activeElement.id,images:document.images.length,styles:document.querySelectorAll('#ghartv-poster-focus-v1').length})");assertTrue(s.getBoolean("same"));assertEquals("two",s.getString("focus"));assertEquals(2,s.getInt("images"));assertEquals(1,s.getInt("styles"));}
    @Test public void slugLabelIsReadableWithoutReplacingPoster() throws Exception {document("https://flixmomo.app/search?q=test","<a id='one' href='/movie/title'><img width='100' height='160' alt='harry-potter-and-the-goblet-of-fire'></a>");JSONObject scan=evaluate(FilmPosterNavigation.script("scan"));assertEquals(scan.toString(),"READY",scan.getString("state"));assertEquals("Harry Potter And The Goblet Of Fire",evaluate(FilmPosterNavigation.script("focus")).getString("label"));JSONObject s=evaluate("JSON.stringify({images:document.images.length,buttons:document.querySelectorAll('button').length,alt:document.images[0].alt})");assertEquals(1,s.getInt("images"));assertEquals(0,s.getInt("buttons"));assertEquals("harry-potter-and-the-goblet-of-fire",s.getString("alt"));}
    @Test public void firstRowUpReturnsToNativeSearch() throws Exception {document("https://flixmomo.app/search?q=test","<a href='/movie/title'><img width='100' height='160' alt='Display Title'></a>");assertEquals("READY",evaluate(FilmPosterNavigation.script("scan")).getString("state"));evaluate(FilmPosterNavigation.script("focus"));assertEquals("TOOLBAR",evaluate(FilmPosterNavigation.script("up")).getString("state"));}
    @Test public void providerInputKeepsNormalCursorKeys() throws Exception {document("https://flixmomo.app/search?q=test","<input id='query' value='test'><a href='/movie/title'><img width='100' height='160' alt='Display Title'></a>");evaluate(FilmPosterNavigation.script("scan"));evaluate("(()=>{document.querySelector('#query').focus();return JSON.stringify({ok:true})})()");assertEquals("EDITING",evaluate(FilmPosterNavigation.script("left")).getString("state"));}
    private boolean coordinatorFlag(String name){try{java.lang.reflect.Field f=FilmNativeControls.class.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(controls);}catch(Exception e){throw new AssertionError(e);}}
    private void attachControls() throws Exception {
        ui(()->{
            ((ViewGroup)browser.getParent()).removeView(browser);FrameLayout frame=new FrameLayout(activity);frame.addView(browser,new FrameLayout.LayoutParams(-1,-1));TextView chrome=new TextView(activity);chrome.setVisibility(View.GONE);frame.addView(chrome);activity.setContentView(frame);
            controls=new FilmNativeControls(activity,frame,chrome,new FilmNativeControls.Host(){public void navigate(String u){navigated=u;}public void usePage(){browser.requestFocus();}public void searchToolbar(){chrome.setVisibility(View.VISIBLE);}public void nativeMode(){}public void navigationHint(String t){}});
            controls.attach(browser);browser.setFocusableInTouchMode(true);browser.requestFocus();controls.finished();
        });
        // Readiness crosses a renderer process. Await the actual observed result;
        // no forcing private fields or fixed sleep in place of readiness.
        boolean[] ready={false};long end=SystemClock.elapsedRealtime()+6000;
        while(!ready[0]&&SystemClock.elapsedRealtime()<end){ui(()->ready[0]=coordinatorFlag("postersReady"));if(!ready[0])SystemClock.sleep(50);}
        String[] context={""};ui(()->context[0]="ready="+coordinatorFlag("postersReady")+",reading="+coordinatorFlag("reading")+",blocked="+coordinatorFlag("blocked")+",focus="+browser.hasFocus()+",url="+browser.getUrl());
        assertTrue("Coordinator did not observe poster layout: "+context[0],ready[0]);ui(()->{browser.requestFocus();assertTrue("Native page focus must be active",browser.hasFocus());});
    }
    @Test public void fullCoordinatorNeverReplacesPostersWithNativeTiles() throws Exception {document("https://flixmomo.app/search?q=test",posters());evaluate("(()=>{window.savedImages=Array.from(document.images);return JSON.stringify({ok:true})})()");attachControls();ui(()->controls.finished());SystemClock.sleep(2200);ui(()->controls.finished());SystemClock.sleep(250);JSONObject result=evaluate("JSON.stringify({same:savedImages.every((x,i)=>x===document.images[i]),metadata:document.querySelectorAll('small').length,buttons:document.querySelectorAll('button').length})");assertTrue(result.getBoolean("same"));assertEquals(2,result.getInt("metadata"));assertEquals(0,result.getInt("buttons"));ui(()->assertFalse(controls.visible()));android.graphics.Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(shot!=null){try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"poster-review-fixture.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}shot.recycle();}}
    @Test public void actualCoordinatorRemoteActivatesSelectedOriginalPoster() throws Exception {document("https://flixmomo.app/search?q=test",posters());attachControls();assertEquals(0,evaluate(FilmPosterNavigation.script("focus")).getInt("index"));ui(()->assertTrue("Right key handled by poster coordinator",controls.posterKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))));long end=SystemClock.elapsedRealtime()+3000;while(!"two".equals(evaluate("JSON.stringify({id:document.activeElement.id})").optString("id"))&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);assertEquals("two",evaluate("JSON.stringify({id:document.activeElement.id})").getString("id"));ui(()->assertTrue("OK key handled by poster coordinator",controls.posterKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER))));end=SystemClock.elapsedRealtime()+3000;while(navigated.isEmpty()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);assertEquals("https://flixmomo.app/movie/two",navigated);}
    @Test public void advertisedProviderOriginAndTabControlsAreRecognized() throws Exception {String url="https://flixmomo.bet/movie/one";document(url,"<a href='#one' onclick=\"document.title='player1'\">Player 1</a><div role='tab' tabindex='0'>Player 2</div><a href='https://unrelated.invalid/'>Player 6</a>");assertTrue(FlixMomoActivity.allowedTop(android.net.Uri.parse(url)));JSONObject p=evaluate(FilmPageSnapshot.read());assertEquals(2,p.getJSONArray("players").length());assertEquals("SELECTION_REQUESTED",evaluate(FilmPageSnapshot.select(url,0,"Player 1")).getString("state"));}
}
