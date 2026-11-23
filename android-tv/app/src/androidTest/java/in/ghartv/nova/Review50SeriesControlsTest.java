package in.ghartv.nova;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Owned pages/media, real Activity dispatch; not a live-provider episode. */
@RunWith(AndroidJUnit4.class)
public class Review50SeriesControlsTest {
    final Review42RegressionTest p=new Review42RegressionTest();
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();p.close();}
    String link(String label){return "<h2>Paper Lantern</h2><a href='/watch/lantern?season=1&amp;episode=1'>"+label+"</a>";}
    JSONObject snapshot(String html)throws Exception{p.plain(html);return p.eval(FilmPageSnapshot.read()).getJSONObject("detail");}
    FilmNativeControls controls(){return (FilmNativeControls)h.field(h.activity,"nativeControls");}
    void invoke(String name,Class<?>[] types,Object...args){h.ui(()->{try{java.lang.reflect.Method m=FlixMomoActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(h.activity,args);}catch(Exception e){throw new AssertionError(e);}});}
    void awaitReady()throws Exception{boolean[] ready={false};long end=SystemClock.elapsedRealtime()+6000;while(SystemClock.elapsedRealtime()<end){h.ui(()->ready[0]=((Button)h.field(h.field(h.activity,"detailPanel"),"watch")).isEnabled());if(ready[0])return;SystemClock.sleep(30);}assertTrue("Current episode action should become ready",ready[0]);}
    void series()throws Exception{
        h.player();
        String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:24px;font:18px sans-serif}a,button{display:inline-block;padding:15px}</style><h2>Paper Lantern</h2><p>A locally authored series description about a family library, used only for this test.</p><a href='/watch/lantern'>WATCH EPISODE 1</a>";
        h.ui(()->{((Map<String,byte[]>)h.field(h.activity,"pages")).put("https://flixmomo.app/tv/lantern",html.getBytes(StandardCharsets.UTF_8));});
        invoke("openDetail",new Class[]{String.class},"https://flixmomo.app/tv/lantern");awaitReady();
    }
    @Test public void episodeOneIsTheSameActionInSnapshotAndNavigation()throws Exception{JSONObject d=snapshot(link("WATCH EPISODE 1"));assertTrue(d.getBoolean("watch"));assertEquals("WATCH EPISODE 1",d.getString("watch_label"));JSONObject action=p.eval(FilmPageFocus.script("watch"));assertEquals("NAVIGATE",action.getString("state"));assertEquals("https://flixmomo.app/watch/lantern?season=1&episode=1",action.getString("target"));}
    @Test public void seasonEpisodeLabelIsRecognized()throws Exception{assertTrue(snapshot(link("Play Season 2 Episode 3")).getBoolean("watch"));}
    @Test public void compactResumeLabelIsRecognized()throws Exception{assertTrue(snapshot(link("Resume S02E04")).getBoolean("watch"));}
    @Test public void nextEpisodeLabelIsRecognizedWithoutGuessingUrl()throws Exception{assertTrue(snapshot(link("Watch next episode")).getBoolean("watch"));assertEquals("NAVIGATE",p.eval(FilmPageFocus.script("watch")).getString("state"));}
    @Test public void movieWatchNowStillNavigates()throws Exception{assertTrue(snapshot(link("WATCH NOW")).getBoolean("watch"));assertEquals("NAVIGATE",p.eval(FilmPageFocus.script("watch")).getString("state"));}
    @Test public void trailerAndWatchlistDoNotBecomeEpisodeActions()throws Exception{JSONObject d=snapshot(link("WATCH TRAILER")+"<button>Add to watchlist</button>");assertFalse(d.getBoolean("watch"));assertEquals("NOT_FOUND",p.eval(FilmPageFocus.script("watch")).getString("state"));}
    @Test public void disabledAndHiddenEpisodeButtonsRemainDisabled()throws Exception{JSONObject d=snapshot("<h2>Paper Lantern</h2><button disabled>Watch episode 1</button><div style='display:none'><button>Watch episode 2</button></div>");assertFalse(d.getBoolean("watch"));}
    @Test public void externalEpisodeLinkDoesNotEnableNativeWatch()throws Exception{JSONObject d=snapshot("<h2>Paper Lantern</h2><a href='https://unregistered.example/watch/episode-1'>Watch episode 1</a>");assertFalse(d.getBoolean("watch"));}
    @Test public void multipleEpisodeActionsRequireViewerSelection()throws Exception{JSONObject d=snapshot(link("Watch episode 1")+"<a href='/watch/lantern?episode=2'>Watch episode 2</a>");assertFalse(d.getBoolean("watch"));assertTrue(d.getBoolean("watch_ambiguous"));assertEquals("AMBIGUOUS_WATCH",p.eval(FilmPageFocus.script("watch")).getString("state"));}
    @Test public void changedEpisodeLabelCannotActivateAnOldButton()throws Exception{snapshot(link("Watch episode 2"));assertEquals("WATCH_ACTION_CHANGED",p.eval(FilmPageFocus.script("watch","Watch episode 1")).getString("state"));}
    @Test public void nativeSeriesDetailsShowEpisodeAndOnePressReachesPlayer()throws Exception{
        series();long started=SystemClock.elapsedRealtime();h.ui(()->{Button w=(Button)h.field(h.field(h.activity,"detailPanel"),"watch");assertEquals("Watch episode 1",w.getText().toString());w.requestFocus();});h.press(KeyEvent.KEYCODE_DPAD_CENTER);
        long end=SystemClock.elapsedRealtime()+6000;JSONObject d=new JSONObject();while(SystemClock.elapsedRealtime()<end){d=h.eval("JSON.stringify({path:location.pathname,video:!!document.querySelector('video')})");if("/watch/lantern".equals(d.optString("path"))&&d.optBoolean("video"))break;SystemClock.sleep(30);}assertEquals("/watch/lantern",d.getString("path"));assertTrue(d.getBoolean("video"));
        JSONObject timing=new JSONObject().put("scope","OWNED_SERIES_CLOUD_EMULATOR").put("watch_episode_to_player_document_ms",SystemClock.elapsedRealtime()-started).put("not_first_frame_or_provider_latency",true);
        h.ui(()->{try{java.io.File f=new java.io.File(h.activity.getExternalFilesDir(null),"review50-series-timing.json");try(java.io.FileWriter w=new java.io.FileWriter(f)){w.write(timing.toString());}}catch(Exception e){throw new AssertionError(e);}});
    }
    @Test public void episodeButtonUsesTrustedGestureOnce()throws Exception{
        h.player();h.eval("JSON.stringify((()=>{document.body.innerHTML='<h2>Paper Lantern</h2><button style=\"padding:30px\">Watch episode 1</button>';window.hits=0;window.trusted=false;document.querySelector('button').onclick=e=>{window.hits++;window.trusted=e.isTrusted};return {ok:true}})())");
        h.ui(()->controls().pageWatch("Watch episode 1"));JSONObject d=new JSONObject();long end=SystemClock.elapsedRealtime()+4000;while(SystemClock.elapsedRealtime()<end){d=h.eval("JSON.stringify({hits:window.hits,trusted:window.trusted})");if(d.optInt("hits")>0)break;SystemClock.sleep(40);}assertEquals(1,d.getInt("hits"));assertTrue(d.getBoolean("trusted"));
    }
    @Test public void menuTogglesVisibleControlsWithoutLeavingVideo()throws Exception{h.player();h.ui(()->{controls().hideAll();h.web.requestFocus();});h.press(KeyEvent.KEYCODE_MENU);h.ui(()->assertTrue(controls().ownsFocus()));h.press(KeyEvent.KEYCODE_MENU);h.ui(()->assertFalse(controls().visible()));assertEquals("/watch/lantern",h.eval("JSON.stringify({p:location.pathname})").getString("p"));}
    @Test public void hiddenTrayDoesNotReturnWhenSourceSnapshotRefreshes()throws Exception{h.player();h.ui(()->{controls().hideAll();controls().refreshState();});SystemClock.sleep(900);h.ui(()->assertFalse(controls().visible()));}
    @Test public void oldDelayedPromptCannotReopenHiddenTray()throws Exception{h.player();h.ui(()->{try{java.lang.reflect.Field f=FilmNativeControls.class.getDeclaredField("selectedByViewer");f.setAccessible(true);f.setBoolean(controls(),true);}catch(Exception e){throw new AssertionError(e);}controls().hideAll();((Runnable)h.field(controls(),"slow")).run();assertFalse(controls().visible());});}
    @Test public void manualControlsAndStartupStripDoNotCompeteForFocus()throws Exception{h.player();h.ui(()->{FilmPlaybackMonitor monitor=(FilmPlaybackMonitor)h.field(h.activity,"startMonitor");monitor.begin();controls().menu();assertTrue(controls().ownsFocus());assertEquals(View.GONE,monitor.panel.getVisibility());controls().hideAll();assertFalse(controls().ownsFocus());});}
    @Test public void menuDoesNotPauseHealthyOwnedPlayback()throws Exception{h.player();h.eval("JSON.stringify((()=>{const v=document.querySelector('video');v.play();return {ok:true}})())");h.ui(()->{controls().hideAll();h.web.requestFocus();});h.press(KeyEvent.KEYCODE_MENU);h.press(KeyEvent.KEYCODE_MENU);JSONObject d=h.eval("JSON.stringify({paused:document.querySelector('video').paused})");assertFalse(d.getBoolean("paused"));}
    @Test public void sourcesRetainDynamicCountAndReturnByBack()throws Exception{h.player();h.ui(()->{controls().menu();controls().openSources();assertEquals(12,((org.json.JSONArray)h.field(controls(),"players")).length());assertTrue(controls().ownsFocus());});h.press(KeyEvent.KEYCODE_BACK);h.ui(()->{assertTrue(controls().ownsFocus());assertEquals(View.GONE,((View)h.field(controls(),"choiceView")).getVisibility());});}
    @Test public void sourceSelectionDoesNotLeaveOldTrayOnTop()throws Exception{h.player();h.ui(()->{controls().openSources();LinearLayout choices=(LinearLayout)h.field(controls(),"choices");for(int i=0;i<choices.getChildCount();i++)if(choices.getChildAt(i) instanceof Button){((Button)choices.getChildAt(i)).performClick();break;}});SystemClock.sleep(900);h.ui(()->assertFalse(controls().visible()));}
    @Test public void cachedEpisodeMetadataCannotEnableWatchBeforeRefresh()throws Exception{p.plain(link("Watch episode 1"));JSONObject d=p.eval(FilmPageSnapshot.read()).getJSONObject("detail");p.ui(()->{FilmDetailView v=new FilmDetailView(p.activity,new FilmDetailView.Host(){public void watch(){}public void watchlist(){}public void original(){}public void back(){}});p.activity.setContentView(v);v.begin("https://flixmomo.app/movie/lantern",null);v.preview(d);assertFalse(((Button)p.field(v,"watch")).isEnabled());});}
}
