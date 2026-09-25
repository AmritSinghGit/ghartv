package in.ghartv.nova;

import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Production reader and UI-action script against local synthetic provider markup.
 * No provider/API/CDN/media requests. No claims of live provider layout compatibility. */
@RunWith(AndroidJUnit4.class)
public class FilmNativeControlsTest {
    private PreviewHarnessActivity activity;
    private WebView browser;
    private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    @Before public void open(){
        Intent i=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(i);
        ui(()->{browser=new WebView(activity);browser.getSettings().setJavaScriptEnabled(true);activity.setContentView(browser);});
    }
    @After public void close(){ui(()->{browser.destroy();activity.finish();});}
    private void document(String url,String html) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        ui(()->{browser.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView w,String u){latch.countDown();}});browser.loadDataWithBaseURL(url,"<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Local provider layout fixture</title>"+html,"text/html","UTF-8",null);});
        assertTrue(latch.await(6,TimeUnit.SECONDS));SystemClock.sleep(150);
    }
    private JSONObject evaluate(String js) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] value={null};
        ui(()->browser.evaluateJavascript(js,v->{value[0]=v;done.countDown();}));assertTrue(done.await(3,TimeUnit.SECONDS));
        Object unwrapped=new JSONTokener(value[0]).nextValue();return new JSONObject((String)unwrapped);
    }
    @Test public void returnsOnlyRealVisibleProviderTitleLinks() throws Exception {
        document("https://flixmomo.app/search?q=test","<a href='/movie/1'>Actual local title</a><a href='/movie/1'>Duplicate</a><a href='https://other.invalid/movie/1'>External</a><a style='display:none' href='/tv/2'>Hidden</a><a href='/login'>Login</a>");
        JSONObject r=evaluate(FilmPageSnapshot.read());assertEquals("SNAPSHOT",r.getString("state"));assertEquals(1,r.getJSONArray("results").length());assertEquals("Actual local title",r.getJSONArray("results").getJSONObject(0).getString("title"));assertEquals(0,r.getJSONArray("players").length());
    }
    @Test public void reportsOnlyActuallyOfferedPlayersNotFixedSix() throws Exception {
        document("https://flixmomo.st/movie/1","<button>Player 1</button><button>Player 2</button><button disabled>Player 3</button><button>Donate</button><button style='display:none'>Server 4</button>");
        JSONObject r=evaluate(FilmPageSnapshot.read());assertEquals(2,r.getJSONArray("players").length());assertEquals("Player 1",r.getJSONArray("players").getJSONObject(0).getString("label"));assertFalse(r.getBoolean("mediaObservable"));assertEquals(0,r.getInt("mediaError"));
    }
    @Test public void clickSelectsExistingProviderControlNotVideoResource() throws Exception {
        String url="https://flixmomo.st/movie/1";
        document(url,"<button onclick=\"document.title='selected-two'\">Player 2</button>");
        assertEquals("SELECTION_REQUESTED",evaluate(FilmPageSnapshot.select(url,0,"Player 2")).getString("state"));
        String[] title={null};ui(()->title[0]=browser.getTitle());assertEquals("selected-two",title[0]);
    }
    @Test public void wrongPageOrChangedPlayerCannotClick() throws Exception {
        String url="https://flixmomo.app/movie/1";document(url,"<button onclick=\"document.title='wrong'\">Player 2</button>");
        assertEquals("STALE_PAGE",evaluate(FilmPageSnapshot.select("https://flixmomo.app/movie/2",0,"Player 2")).getString("state"));
        assertEquals("PLAYER_CHANGED",evaluate(FilmPageSnapshot.select(url,0,"Player 1")).getString("state"));
    }
    @Test public void providerChallengeStopsExtractionRatherThanEvading() throws Exception {
        document("https://flixmomo.app/dummy","<h1>Verify you are human</h1><a href='/movie/1'>Must not extract</a>");
        assertEquals("PROVIDER_VERIFICATION_REQUIRED",evaluate(FilmPageSnapshot.read()).getString("state"));
    }
    @Test public void foreignOriginCannotUseTheReaderOrSelector() throws Exception {
        document("https://example.invalid/movie/1","<button>Player 1</button>");assertEquals("ORIGIN_REJECTED",evaluate(FilmPageSnapshot.read()).getString("state"));
        assertEquals("ORIGIN_REJECTED",evaluate(FilmPageSnapshot.select("https://example.invalid/movie/1",0,"Player 1")).getString("state"));
    }
}
