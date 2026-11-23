package in.ghartv.nova;

import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Controlled resolver failures through the unchanged production preview controller. */
@RunWith(AndroidJUnit4.class)
public class Review53PreviewFailureTest {
    PreviewHarnessActivity activity;
    void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    @Before public void launch(){activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @After public void close(){if(activity!=null)ui(()->activity.finish());InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    void failure(String id,String expected)throws Exception{
        ui(()->activity.focus(id));boolean[] failed={false};long end=SystemClock.elapsedRealtime()+2500;
        while(SystemClock.elapsedRealtime()<end){ui(()->failed[0]=activity.controller.phase()==PreviewGate.Phase.FAILED);if(failed[0])break;SystemClock.sleep(30);}
        assertTrue("Actual controller must finish the failed request",failed[0]);
        ui(()->{String text=activity.status.getText().toString();assertTrue(text,text.contains(expected));assertFalse(text.contains("private fixture"));assertTrue(activity.controller.muted());});
        SystemClock.sleep(400);assertEquals("No automatic retry after failure",1,activity.calls.get());
    }
    @Test public void reusedGuideIntentPreservesInvalidProbeWithoutRunningIt(){
        ui(()->{MainActivity guide=new MainActivity();Intent invalid=new Intent().putExtra("ghartv_network_probe","bad");guide.onNewIntent(invalid);assertSame(invalid,guide.getIntent());assertEquals("bad",guide.getIntent().getStringExtra("ghartv_network_probe"));});
    }
    @Test public void dnsFailureIsDistinguishedWithoutPrivateMessage()throws Exception{failure("dns","DNS failed after recovery");}
    @Test public void tlsFailureDoesNotSuggestDisablingSecurity()throws Exception{failure("tls","Secure");}
    @Test public void timeoutIsNotMisreportedAsDns()throws Exception{failure("timeout","timed out");}
    @Test public void connectionFailureDoesNotClaimPlayback()throws Exception{failure("connection","could not be reached");}
}
