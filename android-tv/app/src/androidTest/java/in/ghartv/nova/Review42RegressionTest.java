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
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review42RegressionTest {
    Activity activity;WebView web;
    void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    Object field(Object o,String n){for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{java.lang.reflect.Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}catch(Exception e){throw new AssertionError(e);}throw new AssertionError(n);}
    View text(View v,String label){if(v instanceof TextView&&label.equals(((TextView)v).getText().toString()))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View x=text(((ViewGroup)v).getChildAt(n),label);if(x!=null)return x;}return null;}
    void press(int key){ui(()->{activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));});SystemClock.sleep(90);}
    JSONObject eval(String js)throws Exception{String[] raw={null};CountDownLatch l=new CountDownLatch(1);ui(()->web.evaluateJavascript(js,s->{raw[0]=s;l.countDown();}));assertTrue(l.await(4,TimeUnit.SECONDS));return new JSONObject((String)new JSONTokener(raw[0]).nextValue());}
    void plain(String html)throws Exception{
        activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));CountDownLatch l=new CountDownLatch(1);
        ui(()->{web=new WebView(activity);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setBlockNetworkLoads(true);activity.setContentView(web);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){l.countDown();}});String u="https://flixmomo.app/movie/lantern";web.loadDataWithBaseURL(u,"<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:20px;font:18px sans-serif}button{padding:12px}h2{font-size:26px}</style>"+html,"text/html","UTF-8",u);});assertTrue(l.await(6,TimeUnit.SECONDS));SystemClock.sleep(100);
    }
    void nativeWatch(boolean noMedia)throws Exception{
        activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review42HarnessActivity.class).putExtra("no_media",noMedia).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));web=(WebView)field(activity,"browser");
        long end=SystemClock.elapsedRealtime()+6000;int[] count={0};while(SystemClock.elapsedRealtime()<end){ui(()->count[0]=((FilmHomeView)field(activity,"homePanel")).cardCount());if(count[0]>0)break;SystemClock.sleep(30);}assertTrue(count[0]>0);
        ui(()->((FilmHomeView)field(activity,"homePanel")).focusRefresh());press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
        boolean[] ready={false};end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){ui(()->{View v=text((View)field(activity,"detailPanel"),"Watch now");ready[0]=v!=null&&v.isEnabled();});if(ready[0])break;SystemClock.sleep(30);}assertTrue(ready[0]);
        ui(()->text((View)field(activity,"detailPanel"),"Watch now").requestFocus());press(KeyEvent.KEYCODE_DPAD_CENTER);
        end=SystemClock.elapsedRealtime()+6000;while(SystemClock.elapsedRealtime()<end){if(eval("JSON.stringify({path:location.pathname})").optString("path").equals("/watch/lantern"))break;SystemClock.sleep(30);}assertEquals("/watch/lantern",eval("JSON.stringify({path:location.pathname})").getString("path"));
        end=SystemClock.elapsedRealtime()+6000;while(SystemClock.elapsedRealtime()<end){ui(()->ready[0]=((JSONArray)field(field(activity,"nativeControls"),"players")).length()==12);if(ready[0])break;SystemClock.sleep(30);}assertTrue("All decorated source controls should be detected",ready[0]);
    }
    @After public void close(){if(activity!=null)ui(()->activity.finish());}
    @Test public void visibleDivSynopsisBeatsGenericJsonLd()throws Exception{
        plain("<h2>Paper Lantern</h2><div>Movie 1 hr 41 min</div><button>WATCH NOW</button><div>A locally authored synopsis about a library and its volunteers rebuilding together after a storm.</div><script type='application/ld+json'>{\"@type\":\"Movie\",\"name\":\"Paper Lantern\",\"description\":\"Watch full movies in HD for free on Flixmomo. No registration required.\"}</script>");JSONObject d=eval(FilmPageSnapshot.read()).getJSONObject("detail");assertTrue(d.getString("synopsis").contains("locally authored synopsis"));assertFalse(d.getString("synopsis").contains("No registration"));assertEquals("1 hr 41 min",d.getJSONObject("facts").getString("Duration"));
    }
    @Test public void genericOnlyDescriptionIsNotPresentedAsSynopsis()throws Exception{
        plain("<h2>Paper Lantern</h2><button>WATCH NOW</button><p>Watch full movies in HD for free on Flixmomo. No registration required.</p><script type='application/ld+json'>{\"@type\":\"Movie\",\"name\":\"Paper Lantern\",\"description\":\"Watch full movies in HD for free on Flixmomo. No registration required.\"}</script>");assertEquals("",eval(FilmPageSnapshot.read()).getJSONObject("detail").getString("synopsis"));
    }
    @Test public void descriptionWithSpanFormattingIsNotLost()throws Exception{plain("<h2>Paper Lantern</h2><button>Watch now</button><div><span>A locally authored long description using a styled span rather than a paragraph, as many modern pages do.</span></div>");assertTrue(eval(FilmPageSnapshot.read()).getJSONObject("detail").getString("synopsis").contains("styled span"));}
    @Test public void divFactsAndLazyArtworkAreObserved()throws Exception{plain("<img style='float:left;width:156px;height:234px' data-src='https://image.tmdb.org/paper.png' alt='Paper Lantern'><h2>Paper Lantern</h2><button>Watch now</button><aside><div><div>Language:</div><div>Punjabi</div></div><div><div>Duration:</div><div>1 hr 41 min</div></div></aside>");JSONObject d=eval(FilmPageSnapshot.read()).getJSONObject("detail");assertEquals("Punjabi",d.getJSONObject("facts").getString("Language"));assertEquals("https://image.tmdb.org/paper.png",d.getString("image"));}
    @Test public void relatedSynopsisCannotFillMissingCurrentSynopsis()throws Exception{plain("<h2>Paper Lantern</h2><button>Watch now</button><h2>Related movies</h2><div>A very long unrelated recommendation description must not appear on this title, even though it exists in the page.</div>");assertEquals("",eval(FilmPageSnapshot.read()).getJSONObject("detail").getString("synopsis"));}
    @Test public void playStartsLoadedVideoWithoutReselectingSource()throws Exception{
        nativeWatch(false);SystemClock.sleep(2600);eval("JSON.stringify((()=>{const v=document.querySelector('video');v.pause();v.currentTime=0;window.trustedMedia=false;return {ok:true}})())");int before=eval("JSON.stringify({count:window.reselections})").getInt("count");
        ui(()->((FilmNativeControls)field(activity,"nativeControls")).menu());press(KeyEvent.KEYCODE_DPAD_CENTER);
        JSONObject s=new JSONObject();long end=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<end){s=eval("JSON.stringify({time:document.querySelector('video').currentTime,paused:document.querySelector('video').paused,trusted:!!window.trustedMedia,reselections:window.reselections})");if(s.optDouble("time")>.35&&!s.optBoolean("paused"))break;SystemClock.sleep(60);}
        assertTrue("Play must advance video, not recreate its source: "+s,s.optDouble("time")>.35);assertFalse(s.getBoolean("paused"));assertTrue(s.getBoolean("trusted"));assertEquals(before,s.getInt("reselections"));
    }
    @Test public void shownTrayOwnsFocusAndAllArrowsStayInControls()throws Exception{nativeWatch(true);ui(()->((FilmNativeControls)field(activity,"nativeControls")).menu());for(int k:new int[]{KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_LEFT}){press(k);ui(()->assertTrue(((LinearLayout)field(field(activity,"nativeControls"),"tray")).hasFocus()));}}
    @Test public void focusedControlsDoNotDisappearOnAutoHideTimeout()throws Exception{nativeWatch(true);ui(()->{FilmNativeControls c=(FilmNativeControls)field(activity,"nativeControls");c.menu();((Runnable)field(c,"hide")).run();assertTrue(c.visible());assertTrue(c.ownsFocus());});}
    @Test public void firstPausedPlayerTrayReceivesVisibleFocus()throws Exception{nativeWatch(true);ui(()->{FilmNativeControls c=(FilmNativeControls)field(activity,"nativeControls");assertTrue(c.visible());assertTrue(c.ownsFocus());assertEquals("Play",((TextView)activity.getCurrentFocus()).getText().toString());});}
    @Test public void enterOnFocusedVideoIsNotClassifiedAsEditing()throws Exception{plain("<video tabindex='0' style='width:400px;height:220px'></video><script>document.querySelector('video').focus()</script>");assertEquals("TAP",eval(FilmPageFocus.script("activate")).getString("state"));}
    @Test public void expiredCertificateCauseIsNotHiddenByHandshakeWrapper(){javax.net.ssl.SSLHandshakeException e=new javax.net.ssl.SSLHandshakeException("Chain validation failed");e.initCause(new java.security.cert.CertificateExpiredException());assertEquals("CERTIFICATE_EXPIRED",NetworkFailure.tlsReason(e));}
    @Test public void futureCertificateCauseIsNotHiddenByHandshakeWrapper(){javax.net.ssl.SSLHandshakeException e=new javax.net.ssl.SSLHandshakeException("Chain validation failed");e.initCause(new java.security.cert.CertificateNotYetValidException());assertEquals("CERTIFICATE_NOT_YET_VALID",NetworkFailure.tlsReason(e));}
}
