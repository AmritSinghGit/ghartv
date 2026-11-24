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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Owned HTTPS WebView fixture: document-start injection and phone media
 * commands across a real cross-origin iframe, without external network,
 * third-party material or the owner's installed GharTV data.
 */
@RunWith(AndroidJUnit4.class)
public class FilmPhoneFrameBridgeTest {
    private PreviewHarnessActivity activity;
    private WebView page;
    private final String parent="https://ghartv-review-fixture.invalid/home";
    private final String child="https://ghartv-review-player.invalid/video";
    private final CountDownLatch loaded=new CountDownLatch(1);
    private void ui(Runnable r){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }
    @Before public void open(){
        assumeTrue("System WebView must support document-start scripts",
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT));
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
            PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation()
            .startActivitySync(intent);
        ui(()->{
            page=new WebView(activity);
            page.getSettings().setJavaScriptEnabled(true);
            page.getSettings().setDomStorageEnabled(true);
            page.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            activity.setContentView(page);
            WebViewCompat.addDocumentStartJavaScript(page,
                FilmPhoneFrameBridge.documentStart(),Collections.singleton("*"));
            page.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,
                    WebResourceRequest request){
                    String url=request.getUrl().toString();
                    String html;
                    if(parent.equals(url))
                        html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                            +"<body><iframe id='player' width='810' height='480' src='"+child
                            +"' style='display:block;border:0'></iframe></body>";
                    else if(child.equals(url))
                        html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                            +"<body><video id='video' controls width='800' height='465' "
                            +"style='display:block;background:black'></video></body>";
                    else return null;
                    return new WebResourceResponse("text/html","UTF-8",
                        new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageFinished(WebView view,String url){
                    if(parent.equals(url))loaded.countDown();
                }
            });
            page.loadUrl(parent);
        });
    }
    @After public void close(){
        if(activity!=null)ui(()->{
            if(page!=null){page.stopLoading();page.destroy();page=null;}
            activity.finish();
        });
    }
    private String evaluate(String script)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<String> raw=new AtomicReference<>();
        ui(()->page.evaluateJavascript(script,value->{raw.set(value);ready.countDown();}));
        assertTrue("WebView evaluateJavascript timed out",ready.await(4,TimeUnit.SECONDS));
        Object decoded=new JSONTokener(raw.get()).nextValue();
        assertTrue("Expected JSON-string WebView response",decoded instanceof String);
        return (String)decoded;
    }
    @Test public void iframePlaybackStateAndFullscreenAreReachableWithoutReadingFrameDom()
        throws Exception{
        assertTrue("Parent HTTPS fixture loaded",loaded.await(8,TimeUnit.SECONDS));
        JSONObject state=null;
        long end=android.os.SystemClock.elapsedRealtime()+9000;
        while(android.os.SystemClock.elapsedRealtime()<end){
            state=new JSONObject(evaluate(FilmPhonePlayback.sample()));
            if(state.optBoolean("observable"))break;
            android.os.SystemClock.sleep(300);
        }
        assertNotNull(state);
        assertTrue("Frame bridge did not report the one owned video: "+state,
            state.getBoolean("observable"));
        assertEquals("paused",state.getString("state"));
        assertFalse("Empty media cannot pretend to be seekable",state.getBoolean("seekable"));
        JSONObject command=new JSONObject(evaluate(FilmPhonePlayback.command("pause",0)));
        assertEquals("REQUESTED",command.getString("result"));
        assertEquals("IMMERSIVE_READY",
            evaluate(FilmPlaybackProbe.fullscreenRequested()));
        assertEquals("RESTORED",evaluate(FilmPlaybackProbe.restoreFullscreen()));
    }
}
