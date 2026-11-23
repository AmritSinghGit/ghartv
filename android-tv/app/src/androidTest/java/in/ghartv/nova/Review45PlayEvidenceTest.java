package in.ghartv.nova;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review45PlayEvidenceTest {
    final Review44LoadingMouseTest h=new Review44LoadingMouseTest();
    @After public void close(){h.close();}
    void cover(String extra)throws Exception {
        h.player();
        h.eval("JSON.stringify((()=>{const v=document.querySelector('video');v.pause();v.currentTime=0;window.coverClicks=0;window.badClicks=0;window.trustedCover=false;v.onclick=null;const box=document.createElement('div');box.style.cssText='position:relative;width:620px;height:350px';v.before(box);box.append(v);const b=document.createElement('button');b.id='play-cover';b.textContent='Play video';b.style.cssText='position:absolute;left:260px;top:140px;width:100px;height:70px;margin:0;padding:0;z-index:3';b.onclick=e=>{window.coverClicks++;window.trustedCover=e.isTrusted;b.remove();v.play()};box.append(b);window.testBox=box;{"+extra+"};return {ok:true}})())");
        SystemClock.sleep(100);
    }
    void play(){h.ui(()->{FilmNativeControls c=(FilmNativeControls)h.field(h.activity,"nativeControls");c.menu();View v=h.text((View)h.field(c,"tray"),"Play");assertNotNull(v);v.requestFocus();});h.press(KeyEvent.KEYCODE_DPAD_CENTER);}
    JSONObject state()throws Exception{return h.eval("JSON.stringify({time:document.querySelector('video').currentTime,paused:document.querySelector('video').paused,clicks:window.coverClicks,bad:window.badClicks,trusted:window.trustedCover,reselections:window.reselections})");}
    JSONObject waitPlaying()throws Exception{JSONObject s=new JSONObject();long until=SystemClock.elapsedRealtime()+6000;while(SystemClock.elapsedRealtime()<until){s=state();if(s.optDouble("time")>.35&&!s.optBoolean("paused"))break;SystemClock.sleep(60);}return s;}
    @Test public void nativePlayClicksSemanticOverlayOnceAndStartsVideo()throws Exception{
        cover("");int before=state().getInt("reselections");play();JSONObject s=waitPlaying();assertTrue(s.toString(),s.getDouble("time")>.35);assertFalse(s.getBoolean("paused"));assertTrue(s.getBoolean("trusted"));assertEquals(1,s.getInt("clicks"));assertEquals(before,s.getInt("reselections"));
    }
    @Test public void offsetPlayOverlayUsesItsActualLocation()throws Exception{
        cover("document.getElementById('play-cover').style.left='440px';document.getElementById('play-cover').style.top='65px'");play();JSONObject s=waitPlaying();assertTrue(s.toString(),s.getDouble("time")>.35);assertEquals(1,s.getInt("clicks"));
    }
    @Test public void unrelatedOverlayIsNotClicked()throws Exception{
        cover("const b=document.getElementById('play-cover');b.textContent='Advertisement';b.style.cssText='position:absolute;inset:0;width:620px;height:350px;margin:0;z-index:3';b.onclick=()=>window.badClicks++");play();SystemClock.sleep(1200);JSONObject s=state();assertEquals(0,s.getInt("bad"));assertEquals(0,s.getInt("clicks"));assertTrue(s.getBoolean("paused"));
    }
    @Test public void twoPlayTargetsAreAmbiguousNotRandomlyClicked()throws Exception{
        cover("const c=document.getElementById('play-cover').cloneNode(true);c.id='second';c.style.left='80px';c.onclick=()=>window.badClicks++;testBox.append(c)");JSONObject out=h.eval(FilmPageFocus.script("media"));assertEquals("AMBIGUOUS_MEDIA",out.getString("state"));assertEquals(0,state().getInt("bad"));
    }
    @Test public void changedPendingTargetRemainsRejected()throws Exception{
        cover("");assertEquals("TAP",h.eval(FilmPageFocus.script("media")).getString("state"));h.eval("JSON.stringify((()=>{document.getElementById('play-cover').remove();return {ok:true}})())");assertEquals("STALE",h.eval(FilmPageFocus.script("commit")).getString("state"));
    }
    @Test public void traceUsesEnumsAndNeverCopiesUnknownInput()throws Exception{
        FilmControlTrace t=new FilmControlTrace();t.reset();assertNull(t.record("movie-secret-name","BEGIN"));assertNull(t.record("media","https://private.example/path"));JSONObject row=t.record("media","TARGET_CONTROL");assertNotNull(row);assertEquals("embedded_webview",row.getString("engine"));assertTrue(row.getString("attempt_ref").matches("[a-f0-9]{16}"));assertEquals(5,row.length());assertNull(t.record("media","TARGET_CONTROL"));
    }
    @Test public void traceHasBoundedDistinctOutcomesAndNewAttemptReference()throws Exception{
        FilmControlTrace t=new FilmControlTrace();t.reset();String first=t.record("request","BEGIN").getString("attempt_ref");String[] outcomes={"TARGET_CONTROL","TARGET_VIDEO","TARGET_FRAME","TARGET_OBSCURED","TARGET_CHANGED","VISUAL_TIMEOUT","NOT_FOUND","MEDIA_NOT_READY","AMBIGUOUS_MEDIA","CLICK_SENT_NOT_PLAYBACK_PROOF","ALREADY_PLAYING","CLOCK_ADVANCED","UNCONFIRMED","MOUSE_CLICK_SENT","IFRAME_UNOBSERVABLE"};for(String o:outcomes)assertNotNull(t.record("media",o));assertNull(t.record("watch","NAVIGATING"));t.reset();assertNotEquals(first,t.record("request","BEGIN").getString("attempt_ref"));
    }
    @Test public void deadlineReportsUnobservableInsteadOfClaimingFailure()throws Exception{
        h.player();h.ui(()->{try{java.lang.reflect.Field f=FlixMomoActivity.class.getDeclaredField("attemptObservable");f.setAccessible(true);f.set(h.activity,false);f=FlixMomoActivity.class.getDeclaredField("playUntil");f.setAccessible(true);f.setLong(h.activity,1);((Runnable)h.field(h.activity,"playDeadline")).run();assertTrue(((TextView)h.field(h.activity,"playNotice")).getText().toString().contains("does not expose playback status"));}catch(Exception e){throw new AssertionError(e);}});
    }
}
