package in.ghartv.nova;

import android.content.Intent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Regressions for phone Sources and Next while the real TV native menu is
 * paused in pointer mode. All provider controls are locally owned fixtures.
 */
@RunWith(AndroidJUnit4.class)
public final class FilmPhoneSourcesTest {
    private static final String PAGE="https://flixmomo.app/watch/phone-source-fixture";
    private PreviewHarnessActivity activity;
    private WebView page;
    private FilmNativeControls controls;
    private final AtomicReference<JSONObject> phone=new AtomicReference<>();
    private final AtomicInteger selected=new AtomicInteger(),failures=new AtomicInteger();
    private final CountDownLatch loaded=new CountDownLatch(1);
    private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    @Before public void open()throws Exception{
        Intent i=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
            PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(i);
        ui(()->{
            FrameLayout stage=new FrameLayout(activity);
            page=new WebView(activity);
            page.getSettings().setJavaScriptEnabled(true);
            stage.addView(page,new FrameLayout.LayoutParams(-1,-1));
            TextView toolbar=new TextView(activity);toolbar.setVisibility(View.GONE);stage.addView(toolbar);
            activity.setContentView(stage);
            controls=new FilmNativeControls(activity,stage,toolbar,new FilmNativeControls.Host(){
                public void navigate(String url){fail("Unexpected navigation");}
                public void usePage(){}
                public void searchToolbar(){}
                public void nativeMode(){}
                public void navigationHint(String hint){}
                public boolean pageIsVisible(){return true;}
                public void playerSelected(){selected.incrementAndGet();}
                public void sourcesChanged(String json){
                    try{phone.set(new JSONObject(json));}
                    catch(Exception e){throw new AssertionError(e);}
                }
                public void phoneSourceOutcome(String code){failures.incrementAndGet();}
            });
            controls.attach(page);
            page.setWebViewClient(new WebViewClient(){
                @Override public void onPageFinished(WebView w,String u){
                    if(PAGE.equals(u))loaded.countDown();
                }
            });
            StringBuilder html=new StringBuilder(
                "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                +"<style>button{display:block;width:220px;height:58px;margin:4px}</style>"
                +"<script>window.chosen=0;window.count=0;</script>");
            for(int n=1;n<=12;n++)html.append("<button onclick='chosen=")
                .append(n).append(";count++'>Player ").append(n).append("</button>");
            page.loadDataWithBaseURL(PAGE,html.toString(),"text/html","UTF-8",PAGE);
        });
        assertTrue("Provider fixture loaded",loaded.await(7,TimeUnit.SECONDS));
        ui(()->controls.pause());
    }
    @After public void close(){
        if(activity!=null)ui(()->{
            if(controls!=null)controls.destroy();
            if(page!=null)page.destroy();
            activity.finish();
        });
    }
    private JSONObject snapshot()throws Exception{
        AtomicReference<String> result=new AtomicReference<>();
        CountDownLatch callback=new CountDownLatch(1);
        ui(()->page.evaluateJavascript(
            "JSON.stringify({chosen:window.chosen,count:window.count})",
            raw->{result.set(raw);callback.countDown();}));
        assertTrue("Page response missing",callback.await(3,TimeUnit.SECONDS));
        return new JSONObject((String)new JSONTokener(result.get()).nextValue());
    }
    private JSONObject awaitSources()throws Exception{
        long deadline=android.os.SystemClock.elapsedRealtime()+4500;
        JSONObject value;
        do {
            value=phone.get();
            if(value!=null&&value.getJSONArray("choices").length()==12)return value;
            android.os.SystemClock.sleep(90);
        }while(android.os.SystemClock.elapsedRealtime()<deadline);
        fail("Phone did not receive 12 readable choices: "+value);
        return null;
    }
    private void awaitSelected(int number)throws Exception{
        long deadline=android.os.SystemClock.elapsedRealtime()+4500;
        JSONObject state=null;
        do {
            state=snapshot();
            if(state.getInt("chosen")==number)return;
            android.os.SystemClock.sleep(100);
        }while(android.os.SystemClock.elapsedRealtime()<deadline);
        fail("Chosen player was not activated: expected "+number+" got "+state);
    }
    @Test public void phoneShowsAllTwelveWithoutOpeningTheTvTray()throws Exception{
        ui(()->controls.requestPhoneSources());
        JSONObject result=awaitSources();
        assertEquals(12,result.getInt("observed"));
        assertEquals("Player 12",result.getJSONArray("choices")
            .getJSONObject(11).getString("label"));
        View choice=(View)field(controls,"choiceView");
        assertEquals("Phone Sources must not open a TV popup",View.GONE,
            choice.getVisibility());
        assertEquals(0,selected.get());
        assertEquals(0,failures.get());
    }
    @Test public void phoneCanSelectWhileTvMenuPausedThenNextMovesToNextNumber()
        throws Exception{
        ui(()->controls.requestPhoneSources());
        awaitSources();
        ui(()->controls.choosePhoneSource(1));
        awaitSelected(1);
        ui(()->controls.nextPhoneSource());
        awaitSelected(2);
        assertEquals(2,selected.get());
        assertEquals(0,failures.get());
    }
    @Test public void manualPlayerFourSelectsFiveAndPhoneSeesBatchTwo()
        throws Exception{
        ui(()->controls.requestPhoneSources());
        awaitSources();
        ui(()->controls.choosePhoneSource(4));
        awaitSelected(4);
        JSONObject result=phone.get();
        assertEquals(2,result.getInt("batch"));
        assertEquals(4,result.getInt("batchStart"));
        assertEquals(6,result.getInt("batchEnd"));
        assertEquals("checking",result.getString("phase"));
        ui(()->controls.nextPhoneSource());
        awaitSelected(5);
        assertEquals(0,failures.get());
    }
    private static Object field(Object object,String name){
        try{
            Field field=object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        }catch(Exception e){throw new AssertionError(e);}
    }
}
