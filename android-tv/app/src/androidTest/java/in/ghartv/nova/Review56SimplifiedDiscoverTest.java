package in.ghartv.nova;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review56SimplifiedDiscoverTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();}
    Object f(String name){return h.field(h.activity,name);}
    boolean flag(String name){return (Boolean)f(name);}
    void invoke(String name){try{java.lang.reflect.Method m=FlixMomoActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(h.activity);}catch(Exception e){throw new AssertionError(e);}}
    void start()throws Exception{h.activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review56SimpleHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));h.web=(WebView)f("browser");long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){if(((Review56SimpleHarnessActivity)h.activity).requested.length()>0)break;SystemClock.sleep(30);}assertFalse(((Review56SimpleHarnessActivity)h.activity).requested.isEmpty());}
    void search(String value)throws Exception{start();h.ui(()->((EditText)f("query")).setText(value));h.ui(()->invoke("search"));long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){if(((Review56SimpleHarnessActivity)h.activity).requested.contains("/search?q="))break;SystemClock.sleep(30);}assertTrue(((Review56SimpleHarnessActivity)h.activity).requested,((Review56SimpleHarnessActivity)h.activity).requested.contains("/search?q="));}
    @Test public void discoverStartsAsOneSearchToolbarOverTheProviderBrowser()throws Exception{start();h.ui(()->{assertEquals(View.VISIBLE,((View)f("chrome")).getVisibility());assertEquals(View.VISIBLE,h.web.getVisibility());assertEquals(View.GONE,((View)f("homePanel")).getVisibility());assertEquals(View.GONE,((View)f("detailPanel")).getVisibility());assertTrue(flag("pointerPage"));assertTrue(((EditText)f("query")).hasFocus());});}
    @Test public void nativeSuggestionAndProviderButtonsAreAbsentFromTheVisibleFlow()throws Exception{start();h.ui(()->{View root=(View)f("screen"),refresh=h.text(root,"Refresh suggestions"),provider=h.text(root,"Open provider page");assertNotNull(refresh);assertNotNull(provider);assertFalse(refresh.isShown());assertFalse(provider.isShown());assertNull(h.text(root,"Discover"));assertNull(h.text(root,"Options"));assertNotNull(h.text(root,"Search"));assertNotNull(h.text(root,"Voice"));assertNotNull(h.text(root,"Back to TV guide"));});}
    @Test public void submittedSearchHidesToolbarAndOpensOriginalResultsWithPointer()throws Exception{search("Paper Lantern");SystemClock.sleep(120);h.ui(()->{assertEquals(View.GONE,((View)f("chrome")).getVisibility());assertEquals(View.GONE,((View)f("homePanel")).getVisibility());assertTrue(flag("pointerPage"));assertTrue(flag("mouseActive"));});}
    @Test public void hardwareSearchRestoresTheToolbar()throws Exception{search("Paper Lantern");h.press(KeyEvent.KEYCODE_SEARCH);h.ui(()->{assertEquals(View.VISIBLE,((View)f("chrome")).getVisibility());assertTrue(((EditText)f("query")).hasFocus());});}
    @Test public void menuOffersSearchAndBackToGuideInsteadOfProviderButtons()throws Exception{search("Paper Lantern");h.press(KeyEvent.KEYCODE_MENU);h.ui(()->{AlertDialog d=(AlertDialog)f("optionsDialog");assertTrue(d.isShowing());View root=d.getWindow().getDecorView();assertNotNull(h.text(root,"Search films or series"));assertNotNull(h.text(root,"Back to TV guide"));assertNull(h.text(root,"Open provider page"));});}
    @Test public void backAtSearchResultsReturnsToTheSearchToolbarBeforeLeaving()throws Exception{search("Paper Lantern");h.press(KeyEvent.KEYCODE_BACK);h.ui(()->{assertFalse(h.activity.isFinishing());assertEquals(View.VISIBLE,((View)f("chrome")).getVisibility());assertTrue(((EditText)f("query")).hasFocus());});}
    @Test public void searchDoesNotRenderNativeCardsOrCachedLiveMatches()throws Exception{search("Paper Lantern");h.ui(()->{assertEquals(0,((FilmHomeView)f("homePanel")).cardCount());assertEquals(View.GONE,((View)f("homePanel")).getVisibility());assertEquals(View.GONE,((View)f("liveResults")).getVisibility());});}
}
