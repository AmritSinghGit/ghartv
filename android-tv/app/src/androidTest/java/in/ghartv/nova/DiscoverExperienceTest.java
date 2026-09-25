package in.ghartv.nova;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Production Discover view and page coordinator, with local owned fixture pixels.
 * No provider sign-in, video extraction, real movie or speech-service claim. */
@RunWith(AndroidJUnit4.class)
public class DiscoverExperienceTest {
    private PreviewHarnessActivity activity;
    private WebView web;
    private FilmNativeControls controls;
    private FrameLayout frame;
    private FilmHomeView home;
    private volatile String opened="";
    private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    @Before public void setup(){
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        ui(()->{web=new WebView(activity);web.getSettings().setJavaScriptEnabled(true);web.setFocusableInTouchMode(true);frame=new FrameLayout(activity);frame.addView(web,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(frame);});
    }
    @After public void teardown(){ui(()->{if(controls!=null)controls.destroy();web.destroy();activity.finish();});}
    private void page(String path,String body) throws Exception {
        String url="https://flixmomo.app"+path;CountDownLatch ready=new CountDownLatch(1);
        ui(()->{web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){ready.countDown();}@Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){return new android.webkit.WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}});
            web.loadDataWithBaseURL(url,"<!doctype html><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{margin:30px;background:#0b1b29;color:white;font:18px sans-serif}button,a,select,input{display:inline-block;padding:15px;margin:12px;font:18px sans-serif}h1{font:28px sans-serif}</style><title>GharTV owned fixture</title>"+body,"text/html","UTF-8",url);});
        assertTrue(ready.await(6,TimeUnit.SECONDS));SystemClock.sleep(180);
    }
    private JSONObject eval(String js) throws Exception {String[] answer={null};CountDownLatch done=new CountDownLatch(1);ui(()->web.evaluateJavascript(js,v->{answer[0]=v;done.countDown();}));assertTrue(done.await(4,TimeUnit.SECONDS));return new JSONObject((String)new JSONTokener(answer[0]).nextValue());}
    private void attach(){ui(()->{
        TextView chrome=new TextView(activity);chrome.setVisibility(View.GONE);frame.addView(chrome);
        controls=new FilmNativeControls(activity,frame,chrome,new FilmNativeControls.Host(){
            public void navigate(String url){opened=url;} public void usePage(){web.requestFocus();} public void searchToolbar(){chrome.setVisibility(View.VISIBLE);} public void nativeMode(){} public void navigationHint(String value){}
        });controls.attach(web);web.requestFocus();
    });}
    private void awaitTitle(String expected) throws Exception {long deadline=SystemClock.elapsedRealtime()+3500;String title="";while(SystemClock.elapsedRealtime()<deadline){title=eval("JSON.stringify({title:document.title})").getString("title");if(expected.equals(title))return;SystemClock.sleep(40);}assertEquals(expected,title);}
    private String details(){return "<nav><a href='/'>Home</a><a href='/watchlist'>Watchlist</a></nav><h1>A film title</h1><button id='watch' onclick=\"document.title=event.isTrusted?'WATCH_TRUSTED':'WATCH_UNTRUSTED'\">WATCH NOW</button><button id='save' onclick=\"document.title=event.isTrusted?'SAVE_TRUSTED':'SAVE_UNTRUSTED'\">ADD TO WATCHLIST</button>";}
    @Test public void detailFocusFindsWatchRatherThanUnrelatedHeader() throws Exception {
        page("/movie/one",details());JSONObject result=eval(FilmPageFocus.script("focus"));assertEquals("FOCUSED",result.getString("state"));assertEquals("WATCH NOW",result.getString("label"));assertEquals("watch",eval("JSON.stringify({id:document.activeElement.id})").getString("id"));
    }
    @Test public void actualRemoteOkClicksWatchWithNativeUserGesture() throws Exception {
        page("/movie/one",details());attach();eval(FilmPageFocus.script("focus"));ui(()->assertTrue(controls.pageKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER))));awaitTitle("WATCH_TRUSTED");
    }
    @Test public void nativeWatchlistActionClicksCurrentProviderControl() throws Exception {
        page("/movie/one",details());attach();ui(()->controls.pageAction("watchlist"));awaitTitle("SAVE_TRUSTED");
    }
    @Test public void arrowMovesFromWatchToWatchlistAndOkActivatesIt() throws Exception {
        page("/movie/one",details());attach();eval(FilmPageFocus.script("focus"));ui(()->assertTrue(controls.pageKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))));
        long end=SystemClock.elapsedRealtime()+3000;while(!"save".equals(eval("JSON.stringify({id:document.activeElement.id})").optString("id"))&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);
        assertEquals("save",eval("JSON.stringify({id:document.activeElement.id})").getString("id"));ui(()->controls.pageKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)));awaitTitle("SAVE_TRUSTED");
    }
    @Test public void inputEditingAndModalFocusAreRespected() throws Exception {
        page("/login","<button>Watch now</button><div role='dialog' aria-modal='true'><input id='email' placeholder='Email'><button id='login'>Sign in</button></div>");
        assertFalse(eval(FilmPageFocus.script("scan")).getBoolean("watch"));eval("(()=>{document.querySelector('#email').focus();return JSON.stringify({ok:true})})()");assertEquals("EDITING",eval(FilmPageFocus.script("left")).getString("state"));
    }
    @Test public void pageFocusRejectsExternalWatchLinksAndVerificationPages() throws Exception {
        page("/movie/one","<a href='https://unrelated.invalid/video'>Watch now</a><button disabled>Player 6</button>");assertFalse(eval(FilmPageFocus.script("scan")).getBoolean("watch"));assertEquals("NOT_FOUND",eval(FilmPageFocus.script("watch")).getString("state"));
        page("/dummy","<h1>Verify you are human</h1><button>Watch now</button>");assertEquals("VERIFICATION_REQUIRED",eval(FilmPageFocus.script("watch")).getString("state"));
    }
    @Test public void homepageSnapshotContainsRealImageAndMetadataButNoInventedImage() throws Exception {
        page("/","<a href='/movie/one'><img src='https://image.tmdb.org/t/p/w300/one.jpg' alt='Image Title'><h3>Display Title</h3><span>2025 · Adventure</span></a><a href='/movie/two'>No artwork here</a>");
        JSONObject snapshot=eval(FilmPageSnapshot.read());JSONArray result=snapshot.getJSONArray("results");assertEquals(2,result.length());assertEquals("Display Title",result.getJSONObject(0).getString("title"));assertTrue(result.getJSONObject(0).getString("metadata").contains("2025"));assertTrue(result.getJSONObject(0).getString("image").endsWith("one.jpg"));assertEquals("",result.getJSONObject(1).getString("image"));
    }
    private JSONObject suggestions() throws Exception {
        return new JSONObject("{\"results\":[{\"title\":\"The First Journey\",\"url\":\"https://flixmomo.app/movie/one\",\"image\":\"https://image.tmdb.org/t/p/w300/one.jpg\",\"metadata\":\"2024 · Adventure\"},{\"title\":\"The Second Journey\",\"url\":\"https://flixmomo.app/movie/two\",\"image\":\"https://image.tmdb.org/t/p/w300/two.jpg\",\"metadata\":\"2025 · Mystery\"},{\"title\":\"Unsafe artwork\",\"url\":\"https://flixmomo.app/movie/three\",\"image\":\"http://127.0.0.1/private\"}]}");
    }
    private void home(){ui(()->{
        home=new FilmHomeView(activity,new FilmHomeView.Host(){public void open(String url){opened=url;}public void refresh(){}public void provider(){}public void privacy(){}},(target,url)->{
            Bitmap image=Bitmap.createBitmap(120,180,Bitmap.Config.ARGB_8888);image.eraseColor(url.contains("one")?0xff24677a:0xff785273);target.setImageBitmap(image);
        });frame.addView(home,new FrameLayout.LayoutParams(-1,-1));
    });}
    private List<ImageView> images(View view){List<ImageView> result=new ArrayList<>();if(view instanceof ImageView)result.add((ImageView)view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)result.addAll(images(((ViewGroup)view).getChildAt(i)));return result;}
    @Test public void nativeHomeCoversProviderAndShowsActualPosterCards() throws Exception {
        page("/","<h1>Provider homepage underneath</h1>");home();JSONObject data=suggestions();ui(()->{home.render(data);assertEquals(2,home.cardCount());assertSame(home,frame.getChildAt(frame.getChildCount()-1));assertEquals(2,images(home).size());assertNotNull(images(home).get(0).getDrawable());});
        Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(shot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"discover-home-fixture.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    }
    @Test public void homeRefreshRetainsSelectedPosterAndActivatesCorrectTitle() throws Exception {
        home();JSONObject data=suggestions();ui(()->{home.render(data);View card=(View)images(home).get(1).getParent();card.requestFocus();home.render(data);assertSame(card,home.findFocus());card.performClick();});assertEquals("https://flixmomo.app/movie/two",opened);
    }
    @Test public void nativeHomeShowsNoMadeUpCardsWhenProviderUnavailableAndCanClear() throws Exception {
        home();ui(()->{home.unavailable("Provider verification required.");assertEquals(0,home.cardCount());});JSONObject data=suggestions();ui(()->{home.render(data);assertEquals(2,home.cardCount());home.discardSuggestions();assertEquals(0,home.cardCount());assertTrue(images(home).isEmpty());});
    }
    @Test public void sharedVoiceResultHasSameQueryBoundaryAndCancellation() {
        Intent speech=new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList("  Harry Potter  ")));
        assertEquals("Harry Potter",UnifiedSearch.voiceText(UnifiedSearch.VOICE,Activity.RESULT_OK,speech));assertEquals("",UnifiedSearch.voiceText(UnifiedSearch.VOICE,Activity.RESULT_CANCELED,speech));assertEquals("",UnifiedSearch.voiceText(1,Activity.RESULT_OK,speech));assertFalse(UnifiedSearch.valid("x"));assertFalse(UnifiedSearch.valid("x".repeat(121)));assertEquals("A B",UnifiedSearch.clean("A\nB"));
    }
    @Test public void retiredFeatureClassIsNotPresentAndNoticeDoesNotClaimImmunity() {
        try{Class.forName("in.ghartv.nova.MovieHubActivity");fail("Retired feature still compiled");}catch(ClassNotFoundException expected){}
        assertTrue(ReviewNotice.TEXT.contains("does not replace permission"));assertTrue(ReviewNotice.TEXT.contains("Tor is not enabled"));assertTrue(ReviewNotice.TEXT.contains("does not record/store the audio"));
    }
    @Test public void watchStepAndPlayerControlsWorkInOnePageFlowWithoutExtractingMedia() throws Exception {
        String html="<button id='watch' onclick=\"document.getElementById('options').style.display='block';this.style.display='none';document.title='WATCH_OPEN'\">Watch now</button><section id='options' style='display:none'><button onclick=\"document.title='PLAYER1'\">Player 1</button><button onclick=\"document.title='PLAYER2'\">Player 2</button></section>";
        page("/movie/one",html);attach();ui(()->controls.pageAction("watch"));awaitTitle("WATCH_OPEN");JSONObject snapshot=eval(FilmPageSnapshot.read());assertEquals(2,snapshot.getJSONArray("players").length());assertEquals("SELECTION_REQUESTED",eval(FilmPageSnapshot.select("https://flixmomo.app/movie/one",1,"Player 2")).getString("state"));awaitTitle("PLAYER2");assertFalse(snapshot.getBoolean("mediaObservable"));
    }
}
