package in.ghartv.nova;

import android.os.SystemClock;
import android.view.View;
import android.view.KeyEvent;
import android.webkit.WebView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;

/** Existing real Activity with owned documents/video. No provider accounts or external movies. */
@RunWith(AndroidJUnit4.class)
public class Review49PlaybackBudgetTest {
    private final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();}
    private FilmStartBudget fresh(){FilmStartBudget b=new FilmStartBudget();b.begin(100);return b;}
    @Test public void pageAndPosterAreNotSuccess(){FilmStartBudget b=fresh();assertEquals(FilmStartBudget.State.STARTING,b.sample(1100,false,true,false,false,false,0,0,false));}
    @Test public void firstFrameNeedsOneSecondOfPositionProgress(){FilmStartBudget b=fresh();b.sample(500,true,false,true,true,false,1,0,false);assertEquals(FilmStartBudget.State.STARTING,b.sample(1400,true,false,true,true,false,2,0,false));assertEquals(FilmStartBudget.State.PLAYING,b.sample(1500,true,false,true,true,false,2.1,0,false));}
    @Test public void stationaryPosterClockIsNotPlayback(){FilmStartBudget b=fresh();b.sample(500,true,false,true,true,false,1,0,false);assertEquals(FilmStartBudget.State.STARTING,b.sample(1600,true,false,true,true,false,1,0,false));}
    @Test public void brokenObservableSourceRequestsNext(){FilmStartBudget b=fresh();assertEquals(FilmStartBudget.State.NEXT,b.sample(800,true,false,false,false,true,0,4,false));assertEquals(2,b.attempt);}
    @Test public void stalledObservableSourceHasBoundedTimeout(){FilmStartBudget b=fresh();assertEquals(FilmStartBudget.State.NEXT,b.sample(12101,true,false,false,false,true,0,0,false));}
    @Test public void unobservableIframeNeverAutoSwitches(){FilmStartBudget b=fresh();assertEquals(FilmStartBudget.State.MANUAL,b.sample(12101,false,true,false,false,false,0,0,false));}
    @Test public void activeVideoWithoutFrameApiIsLeftUndisturbed(){FilmStartBudget b=fresh();b.sample(1000,true,false,false,true,false,2,0,false);assertEquals(FilmStartBudget.State.MANUAL,b.sample(2200,true,false,false,true,false,3.2,0,false));assertEquals(-1,b.proofMs);}
    @Test public void pauseNeverStartsNextSource(){FilmStartBudget b=fresh();assertEquals(FilmStartBudget.State.PAUSED,b.sample(900,true,false,true,false,true,1,0,false));assertEquals(FilmStartBudget.State.PAUSED,b.sample(45000,true,false,true,false,true,1,4,false));}
    @Test public void cancellationIsTerminal(){FilmStartBudget b=fresh();b.cancel();assertEquals(FilmStartBudget.State.CANCELLED,b.sample(50000,true,false,false,false,true,0,4,false));}
    @Test public void attemptLimitIsThree(){FilmStartBudget b=fresh();b.sample(1000,true,false,false,false,true,0,4,false);b.selected(1001);b.sample(2000,true,false,false,false,true,0,4,false);b.selected(2001);assertEquals(FilmStartBudget.State.EXHAUSTED,b.sample(3000,true,false,false,false,true,0,4,false));assertEquals(3,b.attempt);}
    @Test public void totalBudgetIncludesSourceTransitions(){FilmStartBudget b=fresh();b.selected(44000);assertEquals(FilmStartBudget.State.EXHAUSTED,b.sample(45100,true,false,false,false,true,0,0,false));}
    @Test public void protectedPlayerRequiresManualControls(){FilmStartBudget b=fresh();assertEquals(FilmStartBudget.State.MANUAL,b.sample(12200,true,false,false,false,true,0,4,true));}
    @Test public void healthyPlaybackIgnoresLateErrors(){FilmStartBudget b=fresh();b.sample(500,true,false,true,true,false,1,0,false);b.sample(1700,true,false,true,true,false,2,0,false);assertEquals(FilmStartBudget.State.PLAYING,b.sample(100000,true,false,false,false,true,2,4,false));}
    @Test public void sourceTransitionResetsFrameProof(){FilmStartBudget b=fresh();b.sample(400,true,false,true,true,false,1,0,false);b.selected(1000);assertEquals(-1,b.firstFrameMs);assertEquals(FilmStartBudget.State.STARTING,b.sample(2500,true,false,false,false,true,0,0,false));}
    @Test public void unavailableStopsAutomation(){FilmStartBudget b=fresh();b.unavailable("Title unavailable");assertEquals(FilmStartBudget.State.MANUAL,b.sample(20000,true,false,false,false,true,0,4,false));}
    private FilmPlaybackMonitor monitor(){return (FilmPlaybackMonitor)h.field(h.activity,"startMonitor");}
    private String pageUrl(){String[] value={null};h.ui(()->value[0]=h.web.getUrl());return value[0];}
    private void mediaReady()throws Exception{h.player();h.ui(()->monitor().leave());}
    private void playOnce(){h.ui(()->{FilmNativeControls c=(FilmNativeControls)h.field(h.activity,"nativeControls");c.menu();h.text((View)h.field(c,"tray"),"Play").requestFocus();});h.press(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void record(String key,long value)throws Exception{
        java.io.File file=new java.io.File(h.activity.getExternalFilesDir(null),"review49-timing.json");
        JSONObject d=file.isFile()?new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8)):new JSONObject();
        d.put(key,value);d.put("scope","controlled_owned_video_on_cloud_emulator");
        java.nio.file.Files.write(file.toPath(),d.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void realVideoFirstFrameAndProgressAreMeasured()throws Exception{
        mediaReady();h.eval("JSON.stringify((()=>{const v=document.querySelector('video');v.pause();v.currentTime=0;return {ok:true}})())");
        playOnce();long until=SystemClock.elapsedRealtime()+7000;
        while(SystemClock.elapsedRealtime()<until&&monitor().budget.state!=FilmStartBudget.State.PLAYING)SystemClock.sleep(60);
        assertEquals(monitor().evidence().toString(),FilmStartBudget.State.PLAYING,monitor().budget.state);
        assertTrue(monitor().budget.firstFrameMs>=0);assertTrue(monitor().budget.proofMs>=FilmStartBudget.PROGRESS_INTERVAL_MS);
        JSONObject actual=h.eval("JSON.stringify({t:document.querySelector('video').currentTime,p:document.querySelector('video').paused})");assertTrue(actual.getDouble("t")>.25);assertFalse(actual.getBoolean("p"));
        record("play_to_first_frame_observed_ms",monitor().budget.firstFrameMs);record("play_to_verified_advancing_ms",monitor().budget.proofMs);
    }
    @Test public void explicitReleaseStopsVideoBeforeNext()throws Exception{
        mediaReady();JSONObject out=h.eval(FilmPlaybackProbe.release(pageUrl(),false));assertEquals("RELEASED",out.getString("state"));assertTrue(out.getBoolean("paused"));
        JSONObject v=h.eval("JSON.stringify({paused:document.querySelector('video').paused,source:document.querySelector('video').getAttribute('src')})");assertTrue(v.getBoolean("paused"));assertTrue(v.isNull("source"));
    }
    @Test public void automaticReleaseRefusesForeignFrame()throws Exception{
        mediaReady();h.eval("JSON.stringify((()=>{const f=document.createElement('iframe');f.src='about:blank';f.style.cssText='width:400px;height:250px';document.body.append(f);return {ok:true}})())");assertEquals("MANUAL_REQUIRED",h.eval(FilmPlaybackProbe.release(pageUrl(),true)).getString("state"));
    }
    @Test public void cancellationUnloadsProviderAndKeepsCards()throws Exception{
        mediaReady();h.ui(()->{monitor().begin();monitor().cancel();});SystemClock.sleep(200);
        assertEquals("about:blank",pageUrl());assertEquals(FilmStartBudget.State.CANCELLED,monitor().budget.state);
        h.ui(()->assertEquals(View.VISIBLE,((View)h.field(h.activity,"homePanel")).getVisibility()));
    }
    @Test public void progressAndManualControlsRemainAvailable()throws Exception{
        mediaReady();h.ui(()->{monitor().begin();assertEquals(View.VISIBLE,monitor().panel.getVisibility());assertNotNull(h.text(monitor().panel,"Cancel"));assertNotNull(h.text(monitor().panel,"Try next"));assertNotNull(h.text(monitor().panel,"Sources"));monitor().requestFailure("Embedded HTTP 403");assertNotNull(h.text(monitor().panel,"Cancel"));});
    }
    @Test public void playbackObservationReturnsNoMediaAddress()throws Exception{
        mediaReady();String out=h.eval(FilmPlaybackProbe.sample(91)).toString();
        assertFalse(out.contains("base64"));assertFalse(out.contains("cookies"));assertFalse(out.contains("data:video"));
    }
    @Test public void usableDetailsLatencyIsRecorded()throws Exception{
        mediaReady();long value=(long)h.field(h.activity,"detailUsableMs");assertTrue("Details must have usable timing",value>=0);record("details_usable_controlled_ms",value);
    }
    @Test public void cachedPreviewNeverEnablesStaleWatch()throws Exception{
        h.start();h.ui(()->{FilmDetailView d=new FilmDetailView(h.activity,new FilmDetailView.Host(){public void watch(){}public void watchlist(){}public void original(){}public void back(){}});
            h.activity.setContentView(d);JSONObject item=new JSONObject();try{item.put("url","https://flixmomo.app/movie/a").put("title","Owned A").put("watch",true);}catch(Exception e){throw new AssertionError(e);}
            long t=SystemClock.elapsedRealtime();d.begin("https://flixmomo.app/movie/a",item);d.preview(item);
            assertFalse(h.text(d,"Watch now").isEnabled());try{record("cached_preview_render_ms",SystemClock.elapsedRealtime()-t);}catch(Exception e){throw new AssertionError(e);}
        });
    }
    @Test public void leavingScreenDoesNotReschedulePendingPlay()throws Exception{
        mediaReady();h.ui(()->{monitor().begin();monitor().leave();});SystemClock.sleep(600);assertFalse(monitor().running());assertEquals(View.GONE,monitor().panel.getVisibility());
    }

    @Test public void realBrokenSourceReleasesBeforeSecondSourcePlays()throws Exception{
        mediaReady();
        h.eval("JSON.stringify((()=>{const v=document.querySelector('video');window.goodMedia=v.src;window.nextCount=0;window.pausedBeforeSelect=false;const buttons=Array.from(document.querySelectorAll('button')).filter(b=>b.textContent.startsWith('PLAYER'));buttons.forEach((b,i)=>{if(i>1)b.remove();else b.onclick=()=>{window.nextCount++;window.pausedBeforeSelect=v.paused;v.src=i===0?'data:video/mp4;base64,AAAA':window.goodMedia;v.load()}});v.pause();v.src='data:video/mp4;base64,AAAA';v.load();return {ok:true}})())");
        h.ui(()->((FilmNativeControls)h.field(h.activity,"nativeControls")).refreshState());SystemClock.sleep(250);
        h.ui(()->monitor().begin());long until=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<until&&monitor().budget.state!=FilmStartBudget.State.PLAYING)SystemClock.sleep(60);
        assertEquals(monitor().evidence().toString(),FilmStartBudget.State.PLAYING,monitor().budget.state);
        JSONObject d=h.eval("JSON.stringify({next:window.nextCount,pausedBefore:window.pausedBeforeSelect,nowPlaying:!document.querySelector('video').paused})");
        assertEquals(1,d.getInt("next"));assertTrue(d.getBoolean("pausedBefore"));assertTrue(d.getBoolean("nowPlaying"));assertEquals(2,monitor().budget.attempt);
    }
    @Test public void observableTrackAndSeekSurviveHealthyObservation()throws Exception{
        mediaReady();
        h.eval("JSON.stringify((()=>{const v=document.querySelector('video');const track=v.addTextTrack('subtitles','Owned captions','en');track.mode='showing';v.currentTime=.4;return {ok:true}})())");
        playOnce();SystemClock.sleep(1600);
        JSONObject v=h.eval("JSON.stringify({mode:document.querySelector('video').textTracks[0].mode,time:document.querySelector('video').currentTime})");
        assertEquals("showing",v.getString("mode"));assertTrue(v.getDouble("time")>.4);
    }
}
