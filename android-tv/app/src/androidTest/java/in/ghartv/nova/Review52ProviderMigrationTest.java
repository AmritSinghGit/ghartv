package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.*;

/** Real Activity/callback paths with locally owned response bytes; no provider access. */
@RunWith(AndroidJUnit4.class)
public class Review52ProviderMigrationTest {
    final Review51PointerSearchTest scene=new Review51PointerSearchTest();
    final Review44LoadingMouseTest h=scene.h;
    SharedPreferences preferences;boolean hadHome;String previousHome;
    @Before public void preservePreference(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences=context.getSharedPreferences(FlixMomoActivity.PROVIDER_PREFERENCES,Context.MODE_PRIVATE);
        hadHome=preferences.contains("home");previousHome=preferences.getString("home",null);
        assertTrue(preferences.edit().remove("home").commit());
    }
    @After public void close(){
        h.close();InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        if(preferences!=null){SharedPreferences.Editor edit=preferences.edit();if(hadHome)edit.putString("home",previousHome);else edit.remove("home");assertTrue(edit.commit());}
    }
    Object field(String name){return h.field(h.activity,name);}
    FilmHomeView home(){return (FilmHomeView)field("homePanel");}
    FlixMomoActivity activity(){return (FlixMomoActivity)h.activity;}
    String status(){return ((TextView)h.field(home(),"status")).getText().toString();}
    void invoke(String name,Class<?>[] types,Object... args){scene.invoke(name,types,args);}
    void awaitCards(String root)throws Exception{
        long end=SystemClock.elapsedRealtime()+6000;boolean[] ready={false};
        while(SystemClock.elapsedRealtime()<end){h.ui(()->ready[0]=home().cardCount()>0&&h.web.getUrl()!=null&&h.web.getUrl().startsWith(root));if(ready[0])break;SystemClock.sleep(40);}
        assertTrue("Fresh owned cards must arrive from the selected origin",ready[0]);
    }
    WebResourceRequest request(String url,boolean main){
        return new WebResourceRequest(){
            public Uri getUrl(){return Uri.parse(url);}public boolean isForMainFrame(){return main;}
            public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}
            public String getMethod(){return "GET";}public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
        };
    }
    WebResourceResponse unavailable(){return new WebResourceResponse("text/plain","UTF-8",503,"Service Unavailable",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    @Test public void defaultIsTheReviewedBetRoot(){
        assertEquals("https://flixmomo.bet/",FilmProviderPolicy.HOME);
        assertEquals(FilmProviderPolicy.HOME,FlixMomoActivity.preferredProviderHome(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        assertTrue(FlixMomoActivity.allowedTop(Uri.parse(FilmProviderPolicy.HOME)));
    }
    @Test public void onlyRegisteredHttpsRootsCanBeSaved(){
        assertEquals("https://flixmomo.bet/",FilmProviderPolicy.registeredHome("https://flixmomo.bet"));
        for(String raw:new String[]{"http://flixmomo.bet/","https://flixmomo.bet.evil.example/","https://user@flixmomo.bet/","https://flixmomo.bet:8443/","https://flixmomo.bet/search?q=private","https://flixmomo.bet/#private","https://127.0.0.1/"})assertNull(raw,FilmProviderPolicy.registeredHome(raw));
    }
    @Test public void productionPreferenceReaderKeepsKnownAndRejectsUnknownOrigins(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(preferences.edit().putString("home","https://flixmomo.st/").commit());
        assertEquals("https://flixmomo.st/",FlixMomoActivity.preferredProviderHome(c));
        assertTrue(preferences.edit().putString("home","https://unregistered.example/").commit());
        assertEquals(FilmProviderPolicy.HOME,FlixMomoActivity.preferredProviderHome(c));
    }
    @Test public void switchingFromPointerModeRestartsTheRealCatalogueReader()throws Exception{
        scene.title();h.ui(()->{assertTrue((Boolean)field("pointerPage"));activity().selectProviderOrigin("https://flixmomo.bet/");});awaitCards("https://flixmomo.bet/");
        h.ui(()->{assertFalse((Boolean)field("pointerPage"));assertFalse((Boolean)field("mouseActive"));assertFalse((Boolean)field("pageEditor"));assertTrue((Boolean)h.field(field("nativeControls"),"active"));assertTrue(home().isShown());assertFalse(home().isLoadingData());assertEquals(FilmProviderPolicy.CREDIT,((TextView)field("credit")).getText().toString());});
    }
    @Test public void selectedOriginRetainsTheTypedQuery()throws Exception{
        scene.title();h.ui(()->{((EditText)field("query")).setText("Paper Lantern");activity().selectProviderOrigin("https://flixmomo.bet/");});awaitCards("https://flixmomo.bet/");
        assertEquals("https://flixmomo.bet/search?q=Paper%20Lantern",((Review51PointerHarness)h.activity).requested);
        h.ui(()->assertEquals("Paper Lantern",((EditText)field("query")).getText().toString()));
    }
    @Test public void searchAfterPointerToolbarResumesTheReader()throws Exception{
        scene.title();h.ui(()->{invoke("toolbar",new Class[]{});assertFalse((Boolean)h.field(field("nativeControls"),"active"));((EditText)field("query")).setText("Paper Lantern");invoke("search",new Class[]{});});awaitCards("https://flixmomo.app/");
        h.ui(()->{assertFalse((Boolean)field("pointerPage"));assertTrue((Boolean)h.field(field("nativeControls"),"active"));assertTrue(home().isShown());});
    }
    @Test public void discoverAfterPointerToolbarResumesTheReader()throws Exception{
        scene.title();h.ui(()->{invoke("toolbar",new Class[]{});invoke("showHome",new Class[]{boolean.class},true);});awaitCards("https://flixmomo.app/");
        h.ui(()->{assertFalse((Boolean)field("pointerPage"));assertTrue((Boolean)h.field(field("nativeControls"),"active"));assertFalse(home().isLoadingData());});
    }
    @Test public void invalidSelectionPreservesPageModeAndSavedChoice()throws Exception{
        scene.title();String before=((Review51PointerHarness)h.activity).requested;String saved=preferences.getString("home",null);
        h.ui(()->{activity().selectProviderOrigin("https://flixmomo.bet.evil.example/");assertTrue((Boolean)field("pointerPage"));assertTrue((Boolean)field("mouseActive"));});
        assertEquals(before,((Review51PointerHarness)h.activity).requested);assertEquals(saved,preferences.getString("home",null));
    }
    @Test public void mainHttpFailureIsVisibleAndSurvivesEmptySnapshots()throws Exception{
        scene.start();h.ui(()->{home().loading();h.web.getWebViewClient().onReceivedHttpError(h.web,request("https://flixmomo.app/",true),unavailable());assertTrue(status(),status().contains("HTTP 503"));assertFalse(home().isLoadingData());JSONObject empty=new JSONObject();try{empty.put("results",new JSONArray());}catch(Exception e){throw new AssertionError(e);}home().render(empty);assertTrue(status(),status().contains("HTTP 503"));assertEquals(View.GONE,((View)h.field(home(),"progress")).getVisibility());});
    }
    @Test public void catalogueDependencyFailureIsNotReportedAsMoviePlaybackFailure()throws Exception{
        scene.start();h.ui(()->{home().loading();h.web.getWebViewClient().onReceivedHttpError(h.web,request("https://api.themoviedb.org/3/trending/movie/day",false),unavailable());assertTrue(status(),status().contains("catalogue service"));assertFalse(home().isLoadingData());assertFalse(((FilmPlaybackMonitor)field("startMonitor")).healthy());assertFalse(((FilmPlaybackMonitor)field("startMonitor")).panel.isShown());});
    }
    @Test public void unrelatedImageFailureDoesNotDiscardReadableCards()throws Exception{
        scene.start();h.ui(()->{int count=home().cardCount();String before=status();h.web.getWebViewClient().onReceivedHttpError(h.web,request("https://image.tmdb.org/t/p/w300/owned.png",false),unavailable());assertEquals(count,home().cardCount());assertEquals(before,status());});
    }
    @Test public void rememberedOriginDoesNotStoreSearchOrTitlePaths()throws Exception{
        scene.start();h.ui(()->{((EditText)field("query")).setText("Paper Lantern");activity().selectProviderOrigin("https://flixmomo.bet/");});awaitCards("https://flixmomo.bet/");
        assertEquals("https://flixmomo.bet/",preferences.getString("home",null));
        assertEquals("https://flixmomo.bet/",FlixMomoActivity.preferredProviderHome(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        assertEquals("https://flixmomo.bet/",FilmProviderPolicy.pageHome("https://flixmomo.bet/movie/owned?q=private#fragment"));
    }
}
