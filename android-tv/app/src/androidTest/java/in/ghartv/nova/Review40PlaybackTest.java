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

/** End-to-end local playback clock assertions, not a claim of external-source compatibility. */
@RunWith(AndroidJUnit4.class)
public class Review40PlaybackTest {
    Activity activity;WebView web;
    private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void plain(String html)throws Exception{
        Intent i=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=InstrumentationRegistry.getInstrumentation().startActivitySync(i);CountDownLatch ready=new CountDownLatch(1);
        ui(()->{web=new WebView(activity);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setBlockNetworkLoads(true);activity.setContentView(web);
            web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){ready.countDown();}});
            String u="https://flixmomo.app/movie/owned";web.loadDataWithBaseURL(u,"<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font:18px sans-serif}button,a{display:inline-block;padding:12px;margin:10px}</style>"+html,"text/html","UTF-8",u);});assertTrue(ready.await(6,TimeUnit.SECONDS));SystemClock.sleep(150);
    }
    private JSONObject eval(String js)throws Exception{String[] value={null};CountDownLatch l=new CountDownLatch(1);ui(()->web.evaluateJavascript(js,x->{value[0]=x;l.countDown();}));assertTrue(l.await(4,TimeUnit.SECONDS));return new JSONObject((String)new JSONTokener(value[0]).nextValue());}
    private Object field(Object o,String n){for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{java.lang.reflect.Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}catch(Exception e){throw new AssertionError(e);}throw new AssertionError(n);}
    private View text(View v,String label){if(v instanceof TextView&&label.equals(((TextView)v).getText().toString()))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View x=text(((ViewGroup)v).getChildAt(n),label);if(x!=null)return x;}return null;}
    private void press(int key){ui(()->{activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));});SystemClock.sleep(70);}
    private void nativeTitle(boolean anchor)throws Exception{
        Intent i=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review40HarnessActivity.class).putExtra("anchor",anchor).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=InstrumentationRegistry.getInstrumentation().startActivitySync(i);web=(WebView)field(activity,"browser");
        long end=SystemClock.elapsedRealtime()+6500;int[] count={0};while(SystemClock.elapsedRealtime()<end){ui(()->count[0]=((FilmHomeView)field(activity,"homePanel")).cardCount());if(count[0]>0)break;SystemClock.sleep(40);}assertTrue(count[0]>0);
        ui(()->((FilmHomeView)field(activity,"homePanel")).focusRefresh());press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
        boolean[] ready={false};end=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<end){ui(()->{View watch=text((View)field(activity,"detailPanel"),"Watch now");ready[0]=watch!=null&&watch.isEnabled();});if(ready[0])break;SystemClock.sleep(40);}assertTrue("Native title Watch action should be enabled from observed h2 metadata",ready[0]);
    }
    @After public void close(){if(activity!=null)ui(()->activity.finish());}
    @Test public void h2TitleAndMuiStyleFactsAreRead()throws Exception{
        plain("<nav><h1>FLIXMOMO</h1></nav><main><section><h2>Paper Lantern</h2><p>Movie 1 hr 41 min</p><button>WATCH NOW</button><p>A locally authored synopsis long enough to represent the overview on a real title page, without sample media.</p></section><aside><p><b>Genres:</b><span>Adventure, Drama</span></p><p><b>Language:</b><span>Punjabi</span></p></aside></main>");
        JSONObject d=eval(FilmPageSnapshot.read()).getJSONObject("detail");assertEquals("Paper Lantern",d.getString("title"));assertTrue(d.getString("synopsis").contains("locally authored"));assertEquals("Punjabi",d.getJSONObject("facts").getString("Language"));assertEquals("1 hr 41 min",d.getJSONObject("facts").getString("Duration"));
    }
    @Test public void h3TitleWithoutMainDoesNotUseRelatedHeading()throws Exception{
        plain("<section><h3>Paper Lantern</h3><button>WATCH NOW</button><p>An original, clearly separated overview belonging to the current title, not to another recommendation.</p></section><footer><h1>FlixMomo</h1></footer><h2>Related movies</h2>");assertEquals("Paper Lantern",eval(FilmPageSnapshot.read()).getJSONObject("detail").getString("title"));
    }
    @Test public void unrelatedStructuredRecommendationIsNotCopied()throws Exception{
        plain("<h2>Paper Lantern</h2><button>Watch now</button><script type='application/ld+json'>{\"@type\":\"Movie\",\"name\":\"Another Title\",\"description\":\"Wrong synopsis\",\"contentUrl\":\"https://private.invalid/source\"}</script>");String value=eval(FilmPageSnapshot.read()).toString();assertFalse(value.contains("Wrong synopsis"));assertFalse(value.contains("private.invalid"));
    }
    @Test public void visibleWatchLabelWinsOverUnrelatedTitleTooltip()throws Exception{
        plain("<h2>Paper Lantern</h2><button title='Start Paper Lantern'>WATCH NOW</button>");assertTrue(eval(FilmPageSnapshot.read()).getJSONObject("detail").getBoolean("watch"));assertEquals("TAP",eval(FilmPageFocus.script("watch")).getString("state"));
    }
    @Test public void watchLinkUsesExactApprovedRouteWithoutPixelGuess()throws Exception{
        plain("<a href='/watch/owned'>WATCH NOW</a>");JSONObject action=eval(FilmPageFocus.script("watch"));assertEquals("NAVIGATE",action.getString("state"));assertEquals("https://flixmomo.app/watch/owned",action.getString("target"));
    }
    @Test public void externalWatchLinkIsNotNavigated()throws Exception{
        plain("<a href='https://flixmomo2.app/watch/owned'>WATCH NOW</a>");assertEquals("NOT_FOUND",eval(FilmPageFocus.script("watch")).getString("state"));
    }
    @Test public void tapCoordinatesUseTheVisualViewport()throws Exception{
        plain("<button style='position:absolute;left:100px;top:100px'>Watch now</button>");eval(FilmPageFocus.script("watch"));
        eval("(()=>{Object.defineProperty(window,'visualViewport',{configurable:true,value:{offsetLeft:20,offsetTop:30,width:500,height:300}});return JSON.stringify({ok:true})})()");
        JSONObject t=eval(FilmPageFocus.script("commit"));assertEquals("TAP",t.getString("state"));assertEquals(500,t.getInt("width"));assertEquals(300,t.getInt("height"));assertTrue(t.getDouble("x")>0);assertTrue(t.getDouble("y")>0);
    }
    @Test public void ambiguousMediaDoesNotGuessAnIframe()throws Exception{
        plain("<iframe style='width:300px;height:180px' srcdoc=''></iframe><iframe style='width:300px;height:180px' srcdoc=''></iframe>");assertEquals("AMBIGUOUS_MEDIA",eval(FilmPageFocus.script("media")).getString("state"));
    }
    @Test public void snapshotDoesNotExposeStreamUrlsOrCallAnIframePlaying()throws Exception{
        plain("<iframe style='width:300px;height:180px' srcdoc=''></iframe><video style='display:none' src='https://private.invalid/movie.mp4'></video>");JSONObject s=eval(FilmPageSnapshot.read());assertEquals(1,s.getInt("frameCount"));assertFalse(s.getBoolean("mediaPlaying"));assertFalse(s.toString().contains("private.invalid"));
    }
    @Test public void actualNativeTitleContainsObservedDescriptionAndLanguage()throws Exception{
        nativeTitle(false);ui(()->{FilmDetailView d=(FilmDetailView)field(activity,"detailPanel");assertEquals(View.VISIBLE,d.getVisibility());assertEquals("Paper Lantern",d.currentTitle());assertTrue(((TextView)field(d,"synopsis")).getText().toString().contains("original test story"));assertTrue(((TextView)field(d,"facts")).getText().toString().contains("Punjabi"));assertFalse(web.isFocusable());});
    }
    private JSONObject transitionDiagnostic()throws Exception{
        // Test-only introspection of this owned fixture; no device-wide logs,
        // credentials, external account or real provider sessions are collected.
        JSONObject result=new JSONObject();
        ui(()->{try{
            result.put("elapsed",SystemClock.elapsedRealtime());
            for(String name:new String[]{"homeRequested","detailRequested","loading","pageReady","mainFrameError","lastRequested","playUntil","playAfter","mediaAttempted","lastMediaTime"})result.put(name,field(activity,name));
            Object controls=field(activity,"nativeControls");JSONObject c=new JSONObject();
            for(String name:new String[]{"active","blocked","reading","pageBusy","postersReady","probeCount","generation","snapshotUrl","pending","detailFocusApplied"})c.put(name,field(controls,name));
            result.put("controls",c);result.put("webUrl",web.getUrl());result.put("webFocus",web.hasFocus());
            result.put("webWidth",web.getWidth());result.put("webHeight",web.getHeight());
            result.put("notice",((TextView)field(activity,"playNotice")).getText().toString());
            result.put("hint",((TextView)field(activity,"help")).getText().toString());
        }catch(Exception|AssertionError e){try{result.put("diagnosticError",e.getClass().getSimpleName());}catch(JSONException ignored){}}});
        result.put("snapshot",eval(FilmPageSnapshot.read()));
        result.put("dom",eval("JSON.stringify({active:document.activeElement?.tagName,visible:document.visibilityState,pendingTag:window.__ghartvTap39?.element?.tagName,pendingUrl:window.__ghartvTap39?.url,videoReady:document.querySelector('video')?.readyState,videoError:document.querySelector('video')?.error?.code})"));
        return result;
    }
    private void requirePlaybackClock()throws Exception{
        ui(()->{View watch=text((View)field(activity,"detailPanel"),"Watch now");watch.requestFocus();});press(KeyEvent.KEYCODE_DPAD_CENTER);
        long end=SystemClock.elapsedRealtime()+12000;JSONObject state=new JSONObject();
        while(SystemClock.elapsedRealtime()<end){state=eval("JSON.stringify({path:location.pathname,time:document.querySelector('video')?.currentTime||0,paused:document.querySelector('video')?.paused??true,trusted:!!window.trustedMedia,watchClicks:window.watchClicks||0})");if(state.optDouble("time")>.35&&!state.optBoolean("paused"))break;SystemClock.sleep(70);}
        if(state.optDouble("time")<=.35||state.optBoolean("paused")||!state.optBoolean("trusted"))state.put("transitionDiagnostic",transitionDiagnostic());
        assertTrue("A highlight/click is insufficient: owned video must actually advance. "+state,state.optDouble("time")>.35);assertFalse(state.optBoolean("paused"));assertTrue(state.optBoolean("trusted"));assertEquals("/watch/owned",state.getString("path"));
        android.graphics.Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(shot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"review40-owned-playback-fixture.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    }
    @Test public void oneNativeWatchPressNavigatesSpaAndAdvancesOwnedVideo()throws Exception{nativeTitle(false);requirePlaybackClock();assertEquals(1,eval("JSON.stringify({count:window.watchClicks})").getInt("count"));}
    @Test public void oneNativeWatchPressFollowsApprovedLinkAndAdvancesOwnedVideo()throws Exception{nativeTitle(true);requirePlaybackClock();}
}
