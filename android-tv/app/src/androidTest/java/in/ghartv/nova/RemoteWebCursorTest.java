package in.ghartv.nova;

import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual production cursor against local test HTML; no provider, account or movie. */
@RunWith(AndroidJUnit4.class)
public class RemoteWebCursorTest {
    private PreviewHarnessActivity activity;
    private WebView browser;
    private RemoteWebCursor cursor;
    private final AtomicInteger touches=new AtomicInteger();
    private final AtomicInteger lastTouch=new AtomicInteger(-1);
    private void ui(Runnable task){InstrumentationRegistry.getInstrumentation().runOnMainSync(task);}
    private void key(int action,int code){ui(()->cursor.handle(new KeyEvent(action,code)));}
    private String title(){final String[] out={null};ui(()->out[0]=browser.getTitle());return out[0];}
    private int scroll(){final int[] out={0};ui(()->out[0]=browser.getScrollY());return out[0];}
    private void waitTitle(String expected){long end=SystemClock.elapsedRealtime()+4000;while(SystemClock.elapsedRealtime()<end){if(expected.equals(title()))return;SystemClock.sleep(50);}assertEquals(expected,title());}
    @Before public void open() throws Exception {
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        CountDownLatch loaded=new CountDownLatch(1);
        ui(()->{
            FrameLayout frame=new FrameLayout(activity);
            browser=new WebView(activity){@Override public boolean onTouchEvent(MotionEvent event){touches.incrementAndGet();lastTouch.set(event.getActionMasked());return super.onTouchEvent(event);}};
            browser.getSettings().setJavaScriptEnabled(true);
            browser.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){loaded.countDown();}});
            frame.addView(browser,new FrameLayout.LayoutParams(-1,-1));
            cursor=new RemoteWebCursor(activity);frame.addView(cursor,new FrameLayout.LayoutParams(-1,-1));cursor.target(browser);
            activity.setContentView(frame);browser.requestFocus();
            String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>ready</title><style>body{margin:0;height:400vh;background:#132634}button{position:absolute;left:35vw;top:35vh;width:30vw;height:30vh;font:24px sans-serif}</style><button onclick=\"document.title='clicked'\">Local click target</button><p style='padding-top:110vh'>Local scrolling target</p>";
            browser.loadDataWithBaseURL("https://example.invalid/",html,"text/html","UTF-8",null);
        });
        assertTrue("local test document loaded",loaded.await(8,TimeUnit.SECONDS));SystemClock.sleep(300);ui(()->cursor.enter());
    }
    @After public void close(){ui(()->{cursor.leave();browser.destroy();activity.finish();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    @Test public void centerPressClicksActualLocalWebButton(){
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(70);key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER);
        waitTitle("clicked");assertTrue(touches.get()>=2);assertEquals(MotionEvent.ACTION_UP,lastTouch.get());
    }
    @Test public void pointerMovementMovesClickAwayFromCenter(){
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(800);key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT);
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(200);
        assertEquals("ready",title());
    }
    @Test public void pageDownScrollsActualWebView(){
        int before=scroll();key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_PAGE_DOWN);key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_PAGE_DOWN);
        long end=SystemClock.elapsedRealtime()+2000;while(scroll()<=before&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);
        assertTrue("real document moved down",scroll()>before);
    }
    @Test public void cancelReleasesAnUnfinishedClick(){
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER);ui(()->cursor.cancel());
        assertEquals(MotionEvent.ACTION_CANCEL,lastTouch.get());key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(MotionEvent.ACTION_CANCEL,lastTouch.get());
    }
    @Test public void disabledCursorLeavesRemoteKeysForNativeWebFocus(){
        ui(()->{cursor.enable(false);assertFalse(cursor.handle(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));assertEquals(View.INVISIBLE,cursor.getVisibility());});
    }
    @Test public void leavingStopsCursorAndDoesNotConsumeToolbarTyping(){
        ui(()->{cursor.enter();assertFalse(cursor.handle(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A)));cursor.leave();assertEquals(View.INVISIBLE,cursor.getVisibility());});
    }
}
