package in.ghartv.nova;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

/** Release classes running in the real Activity. Debug subclass substitutes only
 * network loading/artwork with owned fixtures. No device/account/provider data. */
@RunWith(AndroidJUnit4.class)
public class Review38RegressionTest {
    private DiscoverFocusHarnessActivity activity;
    private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private Object field(Object object,String name){
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException e){}catch(Exception e){throw new AssertionError(e);}
        throw new AssertionError(name);
    }
    private FilmHomeView home(){return (FilmHomeView)field(activity,"homePanel");}
    private WebView web(){return (WebView)field(activity,"browser");}
    private void open() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent=new Intent(context,DiscoverFocusHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(DiscoverFocusHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        long end=SystemClock.elapsedRealtime()+6500;int[] count={0};
        while(SystemClock.elapsedRealtime()<end){ui(()->count[0]=home().cardCount());if(count[0]==30)break;SystemClock.sleep(40);}
        assertEquals("Actual Activity populated thirty fixture posters",30,count[0]);SystemClock.sleep(120);
    }
    @After public void close(){if(activity!=null){ui(()->activity.finish());InstrumentationRegistry.getInstrumentation().waitForIdleSync();}}
    private boolean within(View item,View root){for(View v=item;v!=null;){if(v==root)return true;android.view.ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}return false;}
    private void press(int key){ui(()->{activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));});SystemClock.sleep(45);}
    private void visibleFocus(){ui(()->{View focus=activity.getCurrentFocus();assertNotNull("Focus must not disappear",focus);assertFalse("Focus cannot enter the covered WebView",within(focus,web()));Rect r=new Rect();assertTrue("Focused target must be visible on screen: "+focus,focus.getGlobalVisibleRect(r)&&r.width()>0&&r.height()>0);});}
    private View text(View view,String wanted){if(view instanceof TextView&&wanted.equals(((TextView)view).getText().toString()))return view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=text(((ViewGroup)view).getChildAt(n),wanted);if(found!=null)return found;}return null;}
    @Test public void actualActivityRepeatedDownRightLeftUpNeverLosesVisibleFocus() throws Exception {
        open();ui(()->((View)field(activity,"pageButton")).requestFocus());
        press(KeyEvent.KEYCODE_DPAD_DOWN);ui(()->assertEquals("Refresh suggestions",((TextView)activity.getCurrentFocus()).getText().toString()));visibleFocus();
        for(int i=0;i<6;i++){press(KeyEvent.KEYCODE_DPAD_DOWN);visibleFocus();}
        press(KeyEvent.KEYCODE_DPAD_RIGHT);visibleFocus();press(KeyEvent.KEYCODE_DPAD_LEFT);visibleFocus();
        for(int i=0;i<6;i++){press(KeyEvent.KEYCODE_DPAD_UP);visibleFocus();}
    }
    @Test public void coveredProviderCannotStealFocusEvenWhenItRequestsIt() throws Exception {
        open();ui(()->{assertFalse(web().isFocusable());assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,web().getDescendantFocusability());assertFalse("Browser requestFocus refused under Home",web().requestFocus());home().focusRefresh();});visibleFocus();
        ui(()->((FilmNativeControls)field(activity,"nativeControls")).menu());
        ui(()->assertFalse("Background player tray cannot rise over Home",((FilmNativeControls)field(activity,"nativeControls")).visible()));visibleFocus();
    }
    @Test public void refreshAndReturnDiscoverKeepKeyboardRouteToCards() throws Exception {
        open();ui(()->home().focusRefresh());press(KeyEvent.KEYCODE_DPAD_CENTER);
        SystemClock.sleep(500);visibleFocus();press(KeyEvent.KEYCODE_DPAD_DOWN);visibleFocus();
        ui(()->{View button=text(activity.getWindow().getDecorView(),"Discover");assertNotNull(button);button.performClick();});
        visibleFocus();ui(()->assertEquals("Refresh suggestions",((TextView)activity.getCurrentFocus()).getText().toString()));
        press(KeyEvent.KEYCODE_DPAD_DOWN);visibleFocus();
    }
    @Test public void emptySuggestionsStillExposeRefreshAndProviderActions() throws Exception {
        open();ui(()->{((FilmNativeControls)field(activity,"nativeControls")).pause();home().discardSuggestions();home().focusRefresh();});
        for(int i=0;i<6;i++){press(KeyEvent.KEYCODE_DPAD_DOWN);visibleFocus();}
        press(KeyEvent.KEYCODE_DPAD_RIGHT);ui(()->assertEquals("Open provider page",((TextView)activity.getCurrentFocus()).getText().toString()));
        press(KeyEvent.KEYCODE_DPAD_LEFT);visibleFocus();
    }
    @Test public void okOnVisiblePosterOpensThatPageAndRestoresWebFocus() throws Exception {
        open();ui(()->home().focusRefresh());press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_RIGHT);
        ui(()->assertTrue(activity.getCurrentFocus().getContentDescription().toString().startsWith("Fixture 1.")));
        press(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(200);
        ui(()->{assertEquals(View.GONE,home().getVisibility());assertTrue(web().isFocusable());assertTrue(web().getUrl().contains("/movie/fixture-1"));});
    }
    @Test public void loadedSuggestionRefreshDoesNotRebuildFocusedCard() throws Exception {
        open();ui(()->home().focusRefresh());press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_RIGHT);
        View[] focus={null};ui(()->focus[0]=activity.getCurrentFocus());SystemClock.sleep(2100);
        ui(()->assertSame("Readback must preserve selected card",focus[0],activity.getCurrentFocus()));visibleFocus();
        android.graphics.Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if(shot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),"review38-focus-fixture.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    }
    private Channel channel(int number,String language,String name){Channel c=new Channel();c.number=number;c.id="fixture-"+number;c.language=language;c.category="News";c.name=name;return c;}
    private List<Channel> catalogue(){return Arrays.asList(channel(8,"Punjabi","PTC News"),channel(2,"Hindi","Hindi News"),channel(1,"English","World News"),channel(5,"Hindi","Hindi Business"));}
    private List<Integer> numbers(List<Channel> channels){List<Integer> result=new ArrayList<>();for(Channel c:channels)result.add(c.number);return result;}
    private WatchHistoryStore history(){return new WatchHistoryStore(InstrumentationRegistry.getInstrumentation().getTargetContext());}
    @Test public void allAndLegacyHomeShowNumberOrderNotViewingRank(){WatchHistoryStore h=history();h.clear();h.recordReady(catalogue().get(0));ChannelIndex index=new ChannelIndex(catalogue(),new HashSet<>(Arrays.asList(8)),h);assertEquals(Arrays.asList(1,2,5,8),numbers(index.filter("All channels","")));assertEquals(numbers(index.filter("Home","")),numbers(index.filter("All channels","")));assertFalse(index.categories().contains("Home"));h.clear();}
    @Test public void recentIsNewestSuccessfulViewingOnly(){WatchHistoryStore h=history();h.clear();List<Channel> all=catalogue();h.recordReady(all.get(0));SystemClock.sleep(5);h.recordReady(all.get(1));h.recordTune(all.get(2));ChannelIndex index=new ChannelIndex(all,Collections.emptySet(),h);assertEquals(Arrays.asList(2,8),numbers(index.filter("Recent","")));h.clear();}
    @Test public void languageFiltersAndScopedSearchCannotReturnOtherLanguages(){WatchHistoryStore h=history();h.clear();ChannelIndex index=new ChannelIndex(catalogue(),Collections.emptySet(),h);assertEquals(Arrays.asList(2,5),numbers(index.filter("Hindi","")));assertEquals(Arrays.asList(8),numbers(index.filter("Punjabi","")));assertTrue(index.filter("Hindi","PTC").isEmpty());assertEquals(1,index.search("PTC").size());assertEquals(Arrays.asList(2,5),numbers(index.filter("Hindi","")));}
    @Test public void emptyRecentStaysAnEmptyViewNotFallbackHome(){WatchHistoryStore h=history();h.clear();ChannelIndex index=new ChannelIndex(catalogue(),Collections.emptySet(),h);assertTrue(index.categories().contains("Recent"));assertTrue(index.filter("Recent","").isEmpty());assertEquals(4,index.filter("All channels","").size());}
    @Test public void priorHomePreferenceMigratesAndLanguageNamesRemainSearchable(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.getSharedPreferences(AppConfig.PREFS,Context.MODE_PRIVATE).edit().putString(AppConfig.KEY_LAST_CATEGORY,"Home").commit();assertEquals(ChannelIndex.VIEW_ALL,new ChannelRepository(c).lastCategory());ChannelIndex index=new ChannelIndex(catalogue(),Collections.emptySet(),history());assertEquals(2,index.search("हिंदी").size());assertEquals(1,index.search("ਪੰਜਾਬੀ").size());}
    @Test public void microphoneLanguageModesAreExplicitAndAlternativesValidated(){
        for(String language:new String[]{"hi-IN","pa-IN","en-IN"}){Intent i=UnifiedSearch.voiceIntent(language);assertEquals(language,i.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));assertEquals(5,i.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS,0));}
        Intent speech=new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList("x","  PTC News  ")));assertEquals("PTC News",UnifiedSearch.voiceText(UnifiedSearch.VOICE,Activity.RESULT_OK,speech));assertEquals("",UnifiedSearch.voiceText(UnifiedSearch.VOICE,Activity.RESULT_CANCELED,speech));
    }
}
