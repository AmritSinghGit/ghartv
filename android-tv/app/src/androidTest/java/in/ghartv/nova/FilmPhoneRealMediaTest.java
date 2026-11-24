package in.ghartv.nova;

import android.content.Intent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** A real 30-second locally bundled test video inside a cross-origin iframe.
 * No network bypass, third-party video, login data or original TV app touched.
 */
@RunWith(AndroidJUnit4.class)
public class FilmPhoneRealMediaTest {
    private static final String PARENT="https://ghartv-test-owner.invalid/parent";
    private static final String CHILD="https://ghartv-test-player.invalid/child";
    private static final String MEDIA="https://ghartv-test-player.invalid/clip.mp4";
    private PreviewHarnessActivity activity;
    private WebView browser;
    private final CountDownLatch loaded=new CountDownLatch(1);
    private void ui(Runnable task){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }
    private static WebResourceResponse page(String html){
        return new WebResourceResponse("text/html","UTF-8",
            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
    }
    private WebResourceResponse movie(WebResourceRequest request){
        try{
            byte[] data;
            try(InputStream file=activity.getAssets().open("ghartv-native-check.mp4")){
                data=file.readAllBytes();
            }
            int start=0,end=data.length-1;
            String range=request.getRequestHeaders().get("Range");
            boolean partial=range!=null&&range.startsWith("bytes=");
            if(partial){
                String[] parts=range.substring(6).split("-",2);
                start=Integer.parseInt(parts[0]);
                if(parts.length>1&&!parts[1].isEmpty())end=Integer.parseInt(parts[1]);
                start=Math.max(0,Math.min(start,data.length-1));
                end=Math.max(start,Math.min(end,data.length-1));
            }
            HashMap<String,String> headers=new HashMap<>();
            headers.put("Accept-Ranges","bytes");
            headers.put("Content-Length",String.valueOf(end-start+1));
            headers.put("Access-Control-Allow-Origin","*");
            headers.put("Cache-Control","no-store");
            if(partial)headers.put("Content-Range","bytes "+start+"-"+end+"/"+data.length);
            return new WebResourceResponse("video/mp4",null,
                partial?206:200,partial?"Partial Content":"OK",headers,
                new ByteArrayInputStream(data,start,end-start+1));
        }catch(Exception failure){
            return new WebResourceResponse("text/plain","UTF-8",
                new ByteArrayInputStream(new byte[0]));
        }
    }
    @Before public void open()throws Exception{
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT));
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
            PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation()
            .startActivitySync(intent);
        ui(()->{
            browser=new WebView(activity);
            browser.getSettings().setJavaScriptEnabled(true);
            browser.getSettings().setMediaPlaybackRequiresUserGesture(false);
            browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            WebViewCompat.addDocumentStartJavaScript(browser,
                FilmPhoneFrameBridge.documentStart(),Collections.singleton("*"));
            activity.setContentView(browser);
            browser.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                    String u=request.getUrl().toString();
                    if(PARENT.equals(u))return page("<!doctype html><meta name='viewport' "
                        +"content='width=device-width,initial-scale=1'><iframe width='850' "
                        +"height='510' style='display:block' src='"+CHILD+"'></iframe>");
                    if(CHILD.equals(u))return page("<!doctype html><meta name='viewport' "
                        +"content='width=device-width,initial-scale=1'><video controls muted "
                        +"playsinline width='820' height='480' style='display:block' src='"
                        +MEDIA+"'></video>");
                    if(MEDIA.equals(u))return movie(request);
                    return null;
                }
                @Override public void onPageFinished(WebView v,String u){
                    if(PARENT.equals(u))loaded.countDown();
                }
            });
            browser.loadUrl(PARENT);
        });
        assertTrue("Local parent fixture loaded",loaded.await(8,TimeUnit.SECONDS));
    }
    @After public void close(){
        if(activity!=null)ui(()->{
            if(browser!=null){browser.stopLoading();browser.destroy();}
            activity.finish();
        });
    }
    private String eval(String script)throws Exception{
        AtomicReference<String> raw=new AtomicReference<>();
        CountDownLatch result=new CountDownLatch(1);
        ui(()->browser.evaluateJavascript(script,s->{raw.set(s);result.countDown();}));
        assertTrue("JS callback not returned",result.await(4,TimeUnit.SECONDS));
        Object decoded=new JSONTokener(raw.get()).nextValue();
        assertTrue(decoded instanceof String);
        return (String)decoded;
    }
    private JSONObject sample()throws Exception{
        return new JSONObject(eval(FilmPhonePlayback.sample()));
    }
    @Test public void actualMovieClockPlayPauseSeekAndPhoneFullscreen()throws Exception{
        JSONObject state=null;
        long deadline=android.os.SystemClock.elapsedRealtime()+13000;
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            state=sample();
            if(state.optBoolean("observable")&&state.optBoolean("seekable")&&
                state.optLong("durationMs")>=25000)break;
            android.os.SystemClock.sleep(300);
        }
        assertNotNull(state);
        assertTrue("Real fixture video must provide a timeline: "+state,
            state.getBoolean("seekable"));
        assertEquals("REQUESTED",new JSONObject(eval(
            FilmPhonePlayback.command("play",0))).getString("result"));
        deadline=android.os.SystemClock.elapsedRealtime()+8000;
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            state=sample();
            if("playing".equals(state.optString("state"))&&
                state.optLong("positionMs")>350)break;
            android.os.SystemClock.sleep(300);
        }
        assertEquals("Playback must be observed, not assumed: "+state,
            "playing",state.getString("state"));
        assertTrue(state.getLong("positionMs")>350);
        assertEquals("IMMERSIVE_READY",eval(FilmPlaybackProbe.fullscreenRequested()));
        assertEquals("REQUESTED",new JSONObject(eval(
            FilmPhonePlayback.command("pause",0))).getString("result"));
        deadline=android.os.SystemClock.elapsedRealtime()+6000;
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            state=sample();
            if("paused".equals(state.optString("state")))break;
            android.os.SystemClock.sleep(270);
        }
        assertEquals("Paused media is confirmed", "paused",state.getString("state"));
        assertEquals("REQUESTED",new JSONObject(eval(
            FilmPhonePlayback.command("seek",15000))).getString("result"));
        deadline=android.os.SystemClock.elapsedRealtime()+6000;
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            state=sample();
            if(state.optLong("positionMs")>=14000)break;
            android.os.SystemClock.sleep(270);
        }
        assertTrue("Seek must change actual position: "+state,
            state.getLong("positionMs")>=14000);
        assertEquals("RESTORED",eval(FilmPlaybackProbe.restoreFullscreen()));
    }
}
