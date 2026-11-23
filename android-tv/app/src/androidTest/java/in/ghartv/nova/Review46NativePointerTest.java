package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Media3 playback of an owned local clip, never a third-party movie. */
@RunWith(AndroidJUnit4.class)
public class Review46NativePointerTest {
    Activity activity;
    final Review44LoadingMouseTest webCase=new Review44LoadingMouseTest();
    void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    Object field(Object obj,String name){return webCase.field(obj,name);}
    void press(int k){ui(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,k)));SystemClock.sleep(60);ui(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,k)));SystemClock.sleep(80);}
    ExoPlayer player(){return (ExoPlayer)field(activity,"player");}
    void startNative(){
        activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),NativeFilmPlayerActivity.class).putExtra(NativeFilmPlayerActivity.EXTRA_MEDIA,NativeFilmPlayerActivity.CHECK_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean[] ready={false};long until=SystemClock.elapsedRealtime()+8000;
        while(SystemClock.elapsedRealtime()<until){ui(()->ready[0]=player()!=null&&player().getPlaybackState()==Player.STATE_READY&&(Boolean)field(activity,"firstFrame")&&player().getCurrentPosition()>350);if(ready[0])break;SystemClock.sleep(50);}assertTrue("Native decoder must render a first frame and advance time",ready[0]);
    }
    boolean hasWebView(View v){if(v instanceof WebView)return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasWebView(((ViewGroup)v).getChildAt(i)))return true;return false;}
    @After public void close(){if(activity!=null)ui(()->activity.finish());webCase.close();}
    @Test public void nativeDecoderRendersAndAdvancesWithoutAWebView(){startNative();ui(()->{assertTrue(player().isPlaying());assertFalse(hasWebView(activity.findViewById(android.R.id.content)));});}
    @Test public void nativeRemotePauseAndPlay(){startNative();press(KeyEvent.KEYCODE_MEDIA_PAUSE);ui(()->assertFalse(player().getPlayWhenReady()));press(KeyEvent.KEYCODE_MEDIA_PLAY);ui(()->assertTrue(player().getPlayWhenReady()));}
    @Test public void nativeRemoteSeeksBothDirections(){startNative();press(KeyEvent.KEYCODE_MEDIA_PAUSE);long[] before={0};ui(()->before[0]=player().getCurrentPosition());press(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);ui(()->assertTrue(player().getCurrentPosition()>before[0]+9000));press(KeyEvent.KEYCODE_MEDIA_REWIND);ui(()->assertTrue(Math.abs(player().getCurrentPosition()-before[0])<1200));}
    @Test public void nativeSpeedControlChangesEngineRate(){startNative();ui(()->{View b=webCase.text(activity.findViewById(android.R.id.content),"Speed");assertNotNull(b);b.performClick();AlertDialog d=(AlertDialog)field(activity,"speedDialog");assertNotNull(d);d.getListView().performItemClick(d.getListView().getChildAt(4),4,4);assertEquals(1.5f,player().getPlaybackParameters().speed,.001f);d.dismiss();});}
    @Test public void nativeLifecycleReleasesDecoderAndRestoresPausedPosition(){startNative();press(KeyEvent.KEYCODE_MEDIA_PAUSE);ui(()->player().seekTo(5000));SystemClock.sleep(100);ui(()->{InstrumentationRegistry.getInstrumentation().callActivityOnPause(activity);assertNull(player());InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);});boolean[] ready={false};long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){ui(()->ready[0]=player()!=null&&player().getPlaybackState()==Player.STATE_READY);if(ready[0])break;SystemClock.sleep(40);}assertTrue(ready[0]);ui(()->{assertFalse(player().getPlayWhenReady());assertTrue(player().getCurrentPosition()>=4900);});}
    @Test public void nativeControlsCanBeHiddenAndReopened(){startNative();ui(()->webCase.text(activity.findViewById(android.R.id.content),"Hide").performClick());ui(()->assertEquals(View.GONE,((View)field(activity,"controls")).getVisibility()));press(KeyEvent.KEYCODE_MENU);ui(()->{assertEquals(View.VISIBLE,((View)field(activity,"controls")).getVisibility());assertTrue(((View)field(activity,"pause")).hasFocus());});}
    @Test public void explicitRegisteredDirectMediaTypesAreRecognized(){for(String suffix:new String[]{"mp4","webm","m3u8","mpd"})assertTrue(NativeFilmPlayerActivity.supported(Uri.parse("https://flixmomo.app/offered/file."+suffix)));}
    @Test public void pageAddressesAndUntrustedSourcesAreNotNativeMedia(){for(String url:new String[]{"https://flixmomo.app/movie/title","http://flixmomo.app/file.mp4","https://unregistered.example/file.mp4","https://flixmomo.app.attacker.example/file.mp4","https://user:pass@flixmomo.app/file.mp4","blob:https://flixmomo.app/id","file:///private/file.mp4","asset:///something-else.mp4","https://flixmomo.app/file.mp4#fragment"})assertFalse(url,NativeFilmPlayerActivity.supported(Uri.parse(url)));assertFalse(NativeFilmPlayerActivity.supported(null));}
    @Test public void pointerGeometryOnlyReturnsPositionNotMediaSource()throws Exception{webCase.player();JSONObject r=webCase.eval(FilmPointerPosition.script());assertEquals("POSITION_ONLY",r.getString("state"));assertTrue(r.getDouble("x")>0&&r.getDouble("x")<1);assertTrue(r.getDouble("y")>0&&r.getDouble("y")<1);assertEquals(3,r.length());}
    @Test public void pointerPlacementRejectsStaleUserInput()throws Exception{webCase.player();webCase.mouse();ui(()->{RemoteWebCursor c=(RemoteWebCursor)field(webCase.activity,"cursor");long token=c.placementToken();c.handle(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));c.handle(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));RemoteCursorState s=(RemoteCursorState)field(c,"state");float x=s.x();assertFalse(c.aimFraction(.05f,.05f,token));assertEquals(x,s.x(),.001f);});}
    @Test public void positioningNeverSendsAnAutomaticClick()throws Exception{webCase.player();webCase.eval("JSON.stringify((()=>{window.pointerClicks=0;document.addEventListener('click',()=>window.pointerClicks++);return {ok:true}})())");webCase.mouse();SystemClock.sleep(500);assertEquals(0,webCase.eval("JSON.stringify({clicks:window.pointerClicks})").getInt("clicks"));}
    @Test public void ambiguousMediaRegionsKeepManualPointer()throws Exception{webCase.player();webCase.eval("JSON.stringify((()=>{const v=document.querySelector('video');v.style.width='240px';v.style.height='130px';const clone=v.cloneNode(false);clone.removeAttribute('src');v.after(clone);return {ok:true}})())");JSONObject r=webCase.eval(FilmPointerPosition.script());assertEquals("MANUAL",r.getString("state"));}
}
