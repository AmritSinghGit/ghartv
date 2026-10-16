package in.ghartv.nova;

import android.content.*;
import android.os.*;
import android.webkit.*;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.torproject.jni.TorService;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real native Tor + scoped WebView proxy smoke. The exit IP is never retained. */
@RunWith(AndroidJUnit4.class)
public class FilmTorNativeSmokeTest {
    private PreviewHarnessActivity activity;private TorService service;private boolean bound;private WebView web;private FilmTorProxy proxy;
    private final CountDownLatch connected=new CountDownLatch(1);
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,android.os.IBinder binder){if(binder instanceof TorService.LocalBinder)service=((TorService.LocalBinder)binder).getService();connected.countDown();}
        public void onServiceDisconnected(ComponentName name){service=null;}
    };
    @Before public void launch(){activity=(PreviewHarnessActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),PreviewHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @After public void close()throws Exception{
        if(proxy!=null){CountDownLatch clear=new CountDownLatch(1);proxy.clear(ok->clear.countDown());clear.await(10,TimeUnit.SECONDS);}
        if(web!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{activity.setContentView(new android.widget.FrameLayout(activity));web.destroy();});
        if(bound)try{activity.unbindService(connection);}catch(Exception ignored){}
        if(activity!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
    }
    @Test public void appLocalTorAndScopedWebViewProxyReachTorCheckWithoutRetainingExitIp()throws Exception{
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->bound=activity.bindService(new Intent(activity,TorService.class).setAction(TorService.ACTION_START),connection,Context.BIND_AUTO_CREATE));
        assertTrue("Tor service binding",bound);assertTrue("Tor service connected",connected.await(20,TimeUnit.SECONDS));assertNotNull(service);
        long until=SystemClock.elapsedRealtime()+100_000L;int port=0;
        while(SystemClock.elapsedRealtime()<until){try{port=service.getHttpTunnelPort();if(port>0&&"1".equals(service.getInfo("status/circuit-established")))break;}catch(Exception ignored){}SystemClock.sleep(500);}
        assertTrue("Tor circuit and HTTP tunnel",port>0);
        proxy=new FilmTorProxy(activity);assertTrue("production proxy supported",proxy.supported());
        CountDownLatch applied=new CountDownLatch(1);boolean[] accepted={false};proxy.apply(port,ok->{accepted[0]=ok;applied.countDown();});assertTrue("proxy acknowledgement",applied.await(10,TimeUnit.SECONDS));assertTrue("production proxy applied",accepted[0]);
        CountDownLatch page=new CountDownLatch(1),body=new CountDownLatch(1);String[] raw={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{web=new WebView(activity);web.getSettings().setJavaScriptEnabled(true);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){page.countDown();v.evaluateJavascript("document.body.innerText",value->{raw[0]=value;body.countDown();});}});activity.setContentView(web);web.loadUrl("https://check.torproject.org/api/ip");});
        assertTrue("Tor check page",page.await(45,TimeUnit.SECONDS));assertTrue("Tor check body",body.await(10,TimeUnit.SECONDS));
        Object decoded=new JSONTokener(raw[0]).nextValue();assertTrue(decoded instanceof String);JSONObject result=new JSONObject((String)decoded);assertTrue("Tor route attested",result.optBoolean("IsTor"));
    }
}
