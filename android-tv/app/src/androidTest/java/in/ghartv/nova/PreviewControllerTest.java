package in.ghartv.nova;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class PreviewControllerTest {
 private PreviewHarnessActivity a;
 private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
 private PreviewGate.Phase phase(){final PreviewGate.Phase[] p={null};ui(()->p[0]=a.controller.phase());return p[0];}
 private void waitFor(PreviewGate.Phase phase,long bound){long stop=SystemClock.elapsedRealtime()+bound;
  while(SystemClock.elapsedRealtime()<stop){if(phase()==phase)return;SystemClock.sleep(40);}assertEquals(phase,phase());}
 @Before public void launch(){
  Intent i=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  a=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(i);
 }
 @After public void close(){ui(()->a.finish());InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
 @Test public void automaticFirstFrameIsMutedAndBounded(){
  ui(()->a.focus("one"));waitFor(PreviewGate.Phase.PLAYING,6000);
  ui(()->{assertEquals(View.GONE,a.poster.getVisibility());assertTrue(a.controller.muted());assertEquals(View.VISIBLE,a.video.getVisibility());});
  waitFor(PreviewGate.Phase.FINISHED,13500);int count=a.calls.get();SystemClock.sleep(800);assertEquals(count,a.calls.get());
  ui(()->{assertEquals(View.VISIBLE,a.poster.getVisibility());assertEquals(View.GONE,a.video.getVisibility());});
 }
 @Test public void rapidMovementRequestsOnlyTheLastCard(){
  ui(()->{a.focus("old");a.focus("new");});waitFor(PreviewGate.Phase.PLAYING,6000);assertEquals("new",a.lastId);assertEquals(1,a.calls.get());
  ui(()->a.controller.focused(false));assertEquals(PreviewGate.Phase.IDLE,phase());SystemClock.sleep(800);assertEquals(1,a.calls.get());
 }
 @Test public void leavingForFindBeforeDwellNeverFetches(){ui(()->{a.focus("one");a.controller.focused(false);});SystemClock.sleep(950);assertEquals(0,a.calls.get());}
 @Test public void slowPreviewTimesOutWithoutLatePicture(){
  ui(()->a.focus("slow"));waitFor(PreviewGate.Phase.FAILED,6500);SystemClock.sleep(900);
  ui(()->{assertEquals(View.VISIBLE,a.poster.getVisibility());assertEquals(View.GONE,a.video.getVisibility());});assertEquals(1,a.calls.get());
 }
 @Test public void savedOffIsHonoured(){ui(()->{a.getSharedPreferences("ghartv_comfort_v1",0).edit().putBoolean("auto_preview",false).commit();a.focus("one");});SystemClock.sleep(950);assertEquals(0,a.calls.get());}
 @Test public void pauseReturnToSameCardRearmsOnce(){ui(()->a.focus("one"));waitFor(PreviewGate.Phase.PLAYING,6000);
  ui(()->{a.controller.stop(false);a.focus("one");});waitFor(PreviewGate.Phase.PLAYING,6000);assertEquals(2,a.calls.get());}
 @Test public void catalogueRenumberDoesNotRestartSameChannel(){
  ui(()->a.focus("stable-id"));waitFor(PreviewGate.Phase.PLAYING,6000);
  ui(()->{Channel c=new Channel();c.id="stable-id";c.number=999;c.name="Fixture";a.controller.select(c);});
  SystemClock.sleep(800);assertEquals(1,a.calls.get());assertEquals(PreviewGate.Phase.PLAYING,phase());
 }

}
