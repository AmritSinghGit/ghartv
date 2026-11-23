package in.ghartv.nova;

import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Proposed instrumentation. Real controller and WebView, owned local HTML only.
 * Run on the existing authorised disposable CI test device, not the owner's app.
 * Navigation age is injected into the real controller; network/HTTP are not used.
 */
@RunWith(AndroidJUnit4.class)
public class FilmCatalogueObservationTest {
    private PreviewHarnessActivity activity;
    private WebView browser;
    private FilmNativeControls controls;
    private final AtomicInteger snapshots=new AtomicInteger();
    private final AtomicReference<JSONObject> lastSnapshot=new AtomicReference<>();
    private final CountDownLatch observed=new CountDownLatch(1);

    private void ui(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private void set(String name,Object value){
        try{Field f=FilmNativeControls.class.getDeclaredField(name);f.setAccessible(true);f.set(controls,value);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private Object get(String name){
        try{Field f=FilmNativeControls.class.getDeclaredField(name);f.setAccessible(true);return f.get(controls);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private long expireWindow(){long past=SystemClock.elapsedRealtime()-30000L;set("probeBegan",past);return past;}

    @Before public void open()throws Exception{
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        CountDownLatch loaded=new CountDownLatch(1);
        ui(()->{
            FrameLayout stage=new FrameLayout(activity);
            browser=new WebView(activity);browser.getSettings().setJavaScriptEnabled(true);
            stage.addView(browser,new FrameLayout.LayoutParams(-1,-1));
            TextView chrome=new TextView(activity);chrome.setVisibility(View.GONE);stage.addView(chrome);
            activity.setContentView(stage);
            browser.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
                @Override public void onPageFinished(WebView view,String url){loaded.countDown();}
            });
            String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    +"<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\">"
                    +"<title>Owned catalogue fixture</title><style>a{display:block;width:180px;height:160px}</style>"
                    +"<a href='/movie/owned'><h3>Owned catalogue item</h3></a>";
            browser.loadDataWithBaseURL(FilmProviderPolicy.HOME,html,"text/html","UTF-8",FilmProviderPolicy.HOME);
            controls=new FilmNativeControls(activity,stage,chrome,new FilmNativeControls.Host(){
                public void navigate(String url){throw new AssertionError("Unexpected navigation");}
                public void usePage(){}
                public void searchToolbar(){}
                public void nativeMode(){}
                public void navigationHint(String text){}
                public boolean pageIsVisible(){return false;}
                public void pageObserved(JSONObject data){lastSnapshot.set(data);snapshots.incrementAndGet();observed.countDown();}
            });
            controls.attach(browser);controls.started();
        });
        assertTrue("Owned WebView document must load",loaded.await(6,TimeUnit.SECONDS));
        ui(()->assertEquals("Fixture URL must match document location",FilmProviderPolicy.HOME,browser.getUrl()));
    }
    @After public void close(){
        ui(()->{
            if(controls!=null)controls.destroy();
            if(browser!=null)browser.destroy();
            if(activity!=null)activity.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void awaitSnapshot()throws Exception{
        assertTrue("The real controller must read the committed document",observed.await(4,TimeUnit.SECONDS));
        assertEquals("SNAPSHOT",lastSnapshot.get().getString("state"));
        assertEquals("Owned catalogue item",lastSnapshot.get().getJSONArray("results").getJSONObject(0).getString("title"));
    }
    @Test public void slowDocumentGetsAReadAfterItsFirstReadyNotification()throws Exception{
        ui(()->{expireWindow();controls.documentReady();assertTrue((Boolean)get("documentReadySeen"));});
        awaitSnapshot();
    }
    @Test public void repeatedReadyCallbacksDoNotRenewAnExpiredOrExhaustedWindow(){
        ui(()->{
            controls.documentReady();long past=expireWindow();set("probeCount",48);
            controls.documentReady();controls.documentReady();
            assertEquals(past,((Long)get("probeBegan")).longValue());assertEquals(48,get("probeCount"));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(0,snapshots.get());
    }
    @Test public void ordinaryFinishedNotificationsCannotRenewTheBudget(){
        ui(()->{
            controls.documentReady();long past=expireWindow();set("probeCount",48);controls.finished();
            assertEquals(past,((Long)get("probeBegan")).longValue());assertEquals(48,get("probeCount"));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(0,snapshots.get());
    }
    @Test public void aNewNavigationGetsItsOwnSingleObservationWindow()throws Exception{
        ui(()->{
            controls.documentReady();expireWindow();set("probeCount",48);
            controls.started();assertFalse((Boolean)get("documentReadySeen"));expireWindow();controls.documentReady();
        });
        awaitSnapshot();
    }
    @Test public void pausedPointerModeIsNotResumedByDocumentReadiness(){
        ui(()->{
            controls.pause();long past=expireWindow();controls.documentReady();
            assertFalse((Boolean)get("active"));assertFalse((Boolean)get("documentReadySeen"));
            assertEquals(past,((Long)get("probeBegan")).longValue());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(0,snapshots.get());
    }
    @Test public void failedPageCannotBeUnblockedByLateReadiness(){
        ui(()->{
            controls.failure();long past=expireWindow();controls.documentReady();
            assertTrue((Boolean)get("blocked"));assertFalse((Boolean)get("documentReadySeen"));
            assertEquals(past,((Long)get("probeBegan")).longValue());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(0,snapshots.get());
    }
    @Test public void connectionRegistryIncludesCatalogueWithoutReplacingExistingServices(){
        ui(()->{
            try{
                Field f=NetworkDiagnostics.class.getDeclaredField("HOSTS");f.setAccessible(true);
                String[][] rows=(String[][])f.get(null);Map<String,String> hosts=new HashMap<>();
                for(String[] row:rows)assertNull("Service identifiers must be unique",hosts.put(row[0],row[1]));
                assertEquals(9,hosts.size());
                assertEquals("jiotvapi.media.jio.com",hosts.get("jio_playback"));
                assertEquals("jiotvapi.cdn.jio.com",hosts.get("jio_guide"));
                assertEquals("api.github.com",hosts.get("updates"));
                assertEquals("ghartv-telemetry.ghartv-47d9a0.workers.dev",hosts.get("collector"));
                assertEquals("flixmomo.app",hosts.get("flixmomo_app"));
                assertEquals("flixmomo.st",hosts.get("flixmomo_st"));
                assertEquals("flixmomo.bet",hosts.get("flixmomo_bet"));
                assertEquals("api.themoviedb.org",hosts.get("film_catalogue"));
                assertEquals("iptv-org.github.io",hosts.get("iptv_org"));
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
    }
}
