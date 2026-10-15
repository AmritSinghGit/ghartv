package in.ghartv.nova;

import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.*;

/** Existing pointer Activity and owned documents; no live provider or network changes. */
@RunWith(AndroidJUnit4.class)
public class Review53ProviderRecoveryTest {
    final Review51PointerSearchTest scene=new Review51PointerSearchTest();
    final Review44LoadingMouseTest h=scene.h;
    @After public void close(){h.close();InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    Object field(String name){return h.field(h.activity,name);}
    View panel(){return (View)field("providerStatusPanel");}
    String message(){return ((TextView)field("status")).getText().toString();}
    WebResourceRequest request(boolean main){return new WebResourceRequest(){
        public Uri getUrl(){return Uri.parse(main?"https://flixmomo.app/movie/lantern":"https://image.tmdb.org/owned.png");}
        public boolean isForMainFrame(){return main;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}
        public String getMethod(){return "GET";}public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
    };}
    WebResourceError dns(){return new WebResourceError(){public int getErrorCode(){return WebViewClient.ERROR_HOST_LOOKUP;}public CharSequence getDescription(){return "private fixture details";}};}
    WebResourceResponse unavailable(){return new WebResourceResponse("text/plain","UTF-8",503,"Service Unavailable",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    void failDns(){h.web.getWebViewClient().onReceivedError(h.web,request(true),dns());}
    @Test public void mainDnsErrorHasVisibleRecoveryOutsideCatalogue()throws Exception{
        scene.title();h.ui(()->{failDns();assertTrue(panel().isShown());assertTrue(message(),message().contains("DNS"));assertFalse(message().contains("private fixture"));assertTrue(((View)field("retryButton")).hasFocus());assertFalse((Boolean)field("mouseActive"));assertTrue((Boolean)field("pointerPage"));});
    }
    @Test public void pendingPointerLoadHasVisibleStatusInsteadOfBlankScreen()throws Exception{
        scene.title();h.ui(()->{h.web.getWebViewClient().onPageStarted(h.web,"https://flixmomo.app/movie/lantern",null);assertTrue(panel().isShown());assertTrue(message().contains("Opening"));assertEquals(View.GONE,((View)field("providerRecoveryActions")).getVisibility());});
    }
    @Test public void boundedSlowLoadOffersRecoveryWithoutClaimingPlaybackFailure()throws Exception{
        scene.title();h.ui(()->{h.web.getWebViewClient().onPageStarted(h.web,"https://flixmomo.app/movie/lantern",null);((Runnable)field("slowLoad")).run();assertTrue(panel().isShown());assertTrue(((View)field("retryButton")).hasFocus());assertTrue(message().contains("not finished loading"));assertFalse(((FilmPlaybackMonitor)field("startMonitor")).healthy());});
    }
    @Test public void lateFinishedCallbackDoesNotEraseMainError()throws Exception{
        scene.title();h.ui(()->{h.web.getWebViewClient().onReceivedHttpError(h.web,request(true),unavailable());h.web.getWebViewClient().onPageFinished(h.web,"https://flixmomo.app/movie/lantern");assertTrue(panel().isShown());assertTrue(message(),message().contains("HTTP 503"));});
    }
    @Test public void explicitRetryUsesOwnedPageAndRestoresPointer()throws Exception{
        scene.title();h.ui(()->{failDns();assertTrue(((View)field("retryButton")).performClick());});
        long end=SystemClock.elapsedRealtime()+6000;boolean[] ready={false};
        while(SystemClock.elapsedRealtime()<end){h.ui(()->ready[0]=!panel().isShown()&&(Boolean)field("mouseActive")&&!(Boolean)field("mainFrameError"));if(ready[0])break;SystemClock.sleep(30);}
        assertTrue("Successful explicit retry restores pointer without a stale error",ready[0]);scene.waitPath("/movie/lantern");
    }
    @Test public void successfulPageKeepsRecoveryOffTheContent()throws Exception{
        scene.title();h.ui(()->{assertFalse(panel().isShown());assertTrue((Boolean)field("mouseActive"));});
    }
    @Test public void supportingImageFailureDoesNotReplaceProviderPage()throws Exception{
        scene.title();h.ui(()->{h.web.getWebViewClient().onReceivedHttpError(h.web,request(false),unavailable());assertFalse(panel().isShown());assertTrue((Boolean)field("mouseActive"));assertFalse((Boolean)field("mainFrameError"));});
    }
    @Test public void fullscreenNeverShowsProviderRecoveryOverVideo()throws Exception{
        scene.title();h.ui(()->{failDns();((FlixMomoActivity)h.activity).enterFullScreen(new FrameLayout(h.activity),()->{});assertFalse(panel().isShown());assertNotNull(field("custom"));});
    }
    @Test public void backFromErrorRetainsExistingResults()throws Exception{
        scene.title();h.ui(this::failDns);h.press(KeyEvent.KEYCODE_BACK);h.ui(()->{assertTrue((Boolean)field("homeRequested"));assertFalse(panel().isShown());assertTrue(((FilmHomeView)field("homePanel")).cardCount()>0);});
    }
}
