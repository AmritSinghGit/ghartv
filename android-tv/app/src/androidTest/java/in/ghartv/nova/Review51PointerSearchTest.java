package in.ghartv.nova;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review51PointerSearchTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();}
    void invoke(String name,Class<?>[] types,Object... values){try{java.lang.reflect.Method m=FlixMomoActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(h.activity,values);}catch(Exception e){throw new AssertionError(e);}}
    Object f(String n){return h.field(h.activity,n);}
    boolean flag(String n){return (Boolean)f(n);}
    void start()throws Exception{
        h.activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review51PointerHarness.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));h.web=(WebView)f("browser");
        long until=SystemClock.elapsedRealtime()+6000;boolean[] ready={false};while(SystemClock.elapsedRealtime()<until){h.ui(()->ready[0]=((FilmHomeView)f("homePanel")).cardCount()>0);if(ready[0])break;SystemClock.sleep(30);}assertTrue(ready[0]);
    }
    void title()throws Exception{start();h.ui(()->((FilmHomeView)f("homePanel")).focusRefresh());h.press(KeyEvent.KEYCODE_DPAD_DOWN);h.press(KeyEvent.KEYCODE_DPAD_CENTER);waitPath("/movie/lantern");}
    void waitPath(String path)throws Exception{long until=SystemClock.elapsedRealtime()+6000;JSONObject s=new JSONObject();while(SystemClock.elapsedRealtime()<until){s=h.eval("JSON.stringify({p:location.pathname,ready:document.readyState})");if(path.equals(s.optString("p"))&&"complete".equals(s.optString("ready")))break;SystemClock.sleep(50);}assertEquals(path,s.optString("p"));SystemClock.sleep(100);}
    JSONObject point(String selector)throws Exception{return h.eval("JSON.stringify((()=>{const e=document.querySelector("+JSONObject.quote(selector)+");e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,w:innerWidth,h:innerHeight}})())");}
    void click(String selector)throws Exception{JSONObject p=point(selector);SystemClock.sleep(100);h.ui(()->{RemoteCursorState s=(RemoteCursorState)h.field(f("cursor"),"state");s.position((float)(p.optDouble("x")*h.web.getWidth()/p.optDouble("w")),(float)(p.optDouble("y")*h.web.getHeight()/p.optDouble("h")));});h.press(KeyEvent.KEYCODE_DPAD_CENTER);}
    Channel channel(String name,String lang,String now,int number){Channel c=new Channel();c.name=name;c.language=lang;c.nowTitle=now;c.number=number;return c;}
    @Test public void releaseDefaultIsPointerFirst(){ h.ui(()->assertTrue(new FlixMomoActivity().pointerFirstBrowsing())); }
    @Test public void selectingCardOpensOriginalDocumentWithoutParsedDetailsGate()throws Exception{title();h.ui(()->{assertTrue(flag("pointerPage"));assertTrue(flag("mouseActive"));assertFalse(flag("detailRequested"));assertEquals(View.GONE,((View)f("detailPanel")).getVisibility());assertFalse((Boolean)h.field(f("nativeControls"),"active"));});}
    @Test public void pageWithDuplicateWatchLabelsRemainsManuallyUsable()throws Exception{title();h.eval("JSON.stringify((()=>{const a=document.querySelector('a');a.after(a.cloneNode(true));return {ok:true}})())");click("a");waitPath("/watch/lantern");assertTrue(flag("pointerPage"));}
    @Test public void arrowMovesPointerInsteadOfSelectingProviderLink()throws Exception{title();RemoteCursorState s=(RemoteCursorState)h.field(f("cursor"),"state");float x=s.x();h.press(KeyEvent.KEYCODE_DPAD_RIGHT);assertTrue(s.x()>x);assertFalse(h.eval("JSON.stringify({link:document.activeElement.tagName==='A'})").getBoolean("link"));}
    @Test public void pointerClickNavigatesObservedWatchLinkOnce()throws Exception{title();click("a");waitPath("/watch/lantern");assertTrue(flag("mouseActive"));}
    @Test public void noDelayedMonitorOrBlueTrayAfterPageNavigation()throws Exception{title();click("a");waitPath("/watch/lantern");SystemClock.sleep(2000);h.ui(()->{assertFalse(((FilmNativeControls)f("nativeControls")).visible());assertEquals(View.GONE,((FilmPlaybackMonitor)f("startMonitor")).panel.getVisibility());});}
    @Test public void pointerStartsOwnedVideoWithGenuineUserClick()throws Exception{title();click("a");waitPath("/watch/lantern");click("video");JSONObject out=new JSONObject();long until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until){out=h.eval("JSON.stringify({t:document.querySelector('video').currentTime,paused:document.querySelector('video').paused,trusted:window.trustedMedia})");if(out.optDouble("t")>.4&&!out.optBoolean("paused"))break;SystemClock.sleep(60);}assertTrue(out.toString(),out.optDouble("t")>.4);assertTrue(out.optBoolean("trusted"));}
    @Test public void menuOpensExplicitNavigationAndCloseRestoresPointer()throws Exception{
        title();h.press(KeyEvent.KEYCODE_MENU);
        h.ui(()->{AlertDialog d=(AlertDialog)f("optionsDialog");assertTrue("Menu must show navigation",d.isShowing());d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
        // AlertDialog posts its button callback and dismissal to the UI queue.
        // Wait for those real callbacks; do not call resumePagePointer or dismiss directly.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        h.ui(()->{assertFalse("Close must dismiss navigation",((AlertDialog)f("optionsDialog")).isShowing());assertTrue("Close must restore the pointer",flag("mouseActive"));});
    }
    @Test public void backFromTitleReturnsToRetainedResultsOnce()throws Exception{title();h.press(KeyEvent.KEYCODE_BACK);assertTrue(flag("homeRequested"));assertFalse(flag("pointerPage"));assertFalse(flag("mouseActive"));}
    @Test public void backFromWatchNavigatesPageHistoryWithoutOpeningTray()throws Exception{title();click("a");waitPath("/watch/lantern");h.press(KeyEvent.KEYCODE_BACK);waitPath("/movie/lantern");assertTrue(flag("mouseActive"));assertFalse(((FilmNativeControls)f("nativeControls")).visible());}
    @Test public void invalidOriginDoesNotNavigateOrExitCurrentPage()throws Exception{title();h.ui(()->invoke("openProviderTitle",new Class[]{String.class},"https://evil.invalid/movie/x"));assertEquals("/movie/lantern",h.eval("JSON.stringify({p:location.pathname})").getString("p"));}
    @Test public void providerSearchInputYieldsToKeyboardUntilBack()throws Exception{
        title();h.eval("JSON.stringify((()=>{document.body.innerHTML='<input id=q style=width:450px;height:80px><div>Owned search</div>';return {ok:true}})())");click("#q");
        // The real pointer click and WebView focus observation are asynchronous.
        // Observe them on the UI thread; never inject editor state or retry the click.
        long until=SystemClock.elapsedRealtime()+5000;boolean[] editing={false};
        while(SystemClock.elapsedRealtime()<until){h.ui(()->editing[0]=flag("pageEditor"));if(editing[0])break;SystemClock.sleep(30);}
        String[] diagnostic={""};if(!editing[0]){
            h.ui(()->diagnostic[0]="mouse="+flag("mouseActive")+" windowFocus="+h.activity.hasWindowFocus()+" journal="+((FilmReviewJournal)f("reviewJournal")).snapshot());
            try{diagnostic[0]+=" DOM="+h.eval("JSON.stringify({active:document.activeElement?.tagName,ready:document.readyState,inputPresent:!!document.querySelector('#q')})");}catch(Exception error){diagnostic[0]+=" DOM unavailable: "+error.getClass().getSimpleName();}
        }
        assertTrue("Provider input must enter keyboard editing after the real click. "+diagnostic[0],editing[0]);
        h.press(KeyEvent.KEYCODE_BACK);
        h.ui(()->{assertFalse("Back must leave keyboard editing",flag("pageEditor"));assertTrue("Back must restore the pointer",flag("mouseActive"));});
    }
    @Test public void plainTextSearchHasOneCombinedQuery()throws Exception{start();h.ui(()->{((EditText)f("query")).setText("  Paper Lantern  ");invoke("search",new Class[]{});});String url=((Review51PointerHarness)h.activity).requested;assertEquals("https://flixmomo.app/search?q=Paper%20Lantern",url);assertTrue(flag("homeRequested"));}
    @Test public void injectedSpeechResultUsesSameSearchPath()throws Exception{start();Intent data=new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList("Paper Lantern")));h.ui(()->invoke("onActivityResult",new Class[]{int.class,int.class,Intent.class},UnifiedSearch.VOICE,Activity.RESULT_OK,data));assertEquals("https://flixmomo.app/search?q=Paper%20Lantern",((Review51PointerHarness)h.activity).requested);}
    @Test public void repeatedSearchIntentUpdatesExistingFilmActivity()throws Exception{title();Intent in=new Intent().putExtra(UnifiedSearch.QUERY,"Paper Lantern");h.ui(()->invoke("onNewIntent",new Class[]{Intent.class},in));assertEquals("Paper Lantern",((EditText)f("query")).getText().toString());assertFalse(flag("pointerPage"));}
    @Test public void cancelledSpeechResultDoesNotNavigate()throws Exception{start();String previous=((Review51PointerHarness)h.activity).requested;h.ui(()->invoke("onActivityResult",new Class[]{int.class,int.class,Intent.class},UnifiedSearch.VOICE,Activity.RESULT_CANCELED,null));assertEquals(previous,((Review51PointerHarness)h.activity).requested);}
    @Test public void unrelatedMovieTextDoesNotReturnGenericLiveChannels(){assertTrue(UnifiedSearch.channels(Arrays.asList(channel("PTC News","Punjabi","Live news",1)),"stranger things").isEmpty());}
    @Test public void liveMatchRequiresAllQueryWords(){Channel a=channel("PTC News","Punjabi","Live news",1),b=channel("PTC Music","Punjabi","Music",2);assertEquals(Arrays.asList(a),UnifiedSearch.channels(Arrays.asList(a,b),"ptc news"));}
    @Test public void matchingPreservesPunjabiAndHindiScripts(){Channel a=channel("ਪੰਜਾਬੀ ਖ਼ਬਰਾਂ","Punjabi","ਹੁਣ",1),b=channel("हिंदी समाचार","Hindi","आज",2);assertEquals(Arrays.asList(a),UnifiedSearch.channels(Arrays.asList(a,b),"ਪੰਜਾਬੀ"));assertEquals(Arrays.asList(b),UnifiedSearch.channels(Arrays.asList(a,b),"हिंदी"));}
    @Test public void channelNumberAndProgrammeTitleRemainSearchable(){Channel a=channel("Owned","English","Paper Lantern",7);assertEquals(1,UnifiedSearch.channels(Arrays.asList(a),"007").size());assertEquals(1,UnifiedSearch.channels(Arrays.asList(a),"paper lantern").size());}
    @Test public void speechIntentIncludesExplicitLanguageAndResultContract(){Intent i=UnifiedSearch.voiceIntent("pa-IN");assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,i.getAction());assertEquals("pa-IN",i.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));assertEquals(5,i.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS,0));}
    @Test public void speechErrorsHaveActionableFallbackWithoutClaimingSuccess(){assertTrue(InAppVoiceSearch.errorMessage(SpeechRecognizer.ERROR_NO_MATCH).contains("Type")||InAppVoiceSearch.errorMessage(SpeechRecognizer.ERROR_NO_MATCH).contains("type"));assertTrue(InAppVoiceSearch.errorMessage(SpeechRecognizer.ERROR_NETWORK).contains("network"));assertTrue(InAppVoiceSearch.errorMessage(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE).contains("language"));}
    @Test public void invalidOrEmptyTranscriptsNeverOpenSearch(){assertEquals("",UnifiedSearch.firstResult(Arrays.asList("","a",new String(new char[121]).replace('\0','x'))));assertEquals("Paper Lantern",UnifiedSearch.firstResult(Arrays.asList("","Paper Lantern")));}
    @Test public void unmatchedPermissionCallbackDoesNothing(){ h.ui(()->{ assertFalse(InAppVoiceSearch.permission(new Activity(),777,new int[]{})); assertTrue(InAppVoiceSearch.permission(new Activity(),InAppVoiceSearch.PERMISSION,new int[]{})); }); }
}
