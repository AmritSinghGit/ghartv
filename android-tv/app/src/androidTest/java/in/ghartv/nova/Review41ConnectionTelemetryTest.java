package in.ghartv.nova;

import android.app.Activity;
import android.content.*;
import android.net.http.*;
import android.os.SystemClock;
import android.webkit.WebView;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import okio.Buffer;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Activity state and real queue/serialization with local intercepted responses only. */
@RunWith(AndroidJUnit4.class)
public class Review41ConnectionTelemetryTest {
    private Activity activity;
    private Context ctx;
    private final AtomicInteger calls=new AtomicInteger();
    private String payload="";
    private void ui(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private Object field(Object o,String n){for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{java.lang.reflect.Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}catch(Exception e){throw new AssertionError(e);}throw new AssertionError(n);}
    @Before public void isolate(){Context original=InstrumentationRegistry.getInstrumentation().getTargetContext();String suffix="-owned-test-"+UUID.randomUUID();File dir=new File(original.getCacheDir(),suffix);assertTrue(dir.mkdir());
        ctx=new ContextWrapper(original){@Override public Context getApplicationContext(){return this;}@Override public File getFilesDir(){return dir;}@Override public SharedPreferences getSharedPreferences(String name,int mode){return original.getSharedPreferences(name+suffix,mode);}};
    }
    @After public void close(){if(activity!=null)ui(()->activity.finish());}
    private void enabled(boolean yes){ctx.getSharedPreferences("ghartv_telemetry",0).edit().putBoolean("diagnostics_enabled",yes).putBoolean("consent_decided",true)
        .putBoolean("config_enabled",true).putString("config_endpoint","https://ghartv-telemetry.ghartv-47d9a0.workers.dev")
        .putString("config_ingest_key","owned-test-key").putLong("config_fetched_at",System.currentTimeMillis()).commit();}
    private void queue()throws Exception {
        java.lang.reflect.Method build=Telemetry.class.getDeclaredMethod("buildEvent",Context.class,String.class,String.class,JSONObject.class);build.setAccessible(true);
        JSONObject data=(JSONObject)build.invoke(null,ctx,null,"film_search",Telemetry.data("surface","discover","password","NEVER_UPLOAD","url","https://private.invalid/?token=secret"));
        java.lang.reflect.Method append=Telemetry.class.getDeclaredMethod("appendEvent",Context.class,JSONObject.class);append.setAccessible(true);append.invoke(null,ctx,data);
    }
    private OkHttpClient client(int status,String body){return new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor(chain->{
        calls.incrementAndGet();Request req=chain.request();assertEquals("ghartv-telemetry.ghartv-47d9a0.workers.dev",req.url().host());assertEquals("/v1/events",req.url().encodedPath());
        Buffer buffer=new Buffer();req.body().writeTo(buffer);payload=buffer.readUtf8();
        return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(status).message("Owned fixture").header("Location","https://foreign.invalid/collect").body(ResponseBody.create(body,MediaType.get("application/json"))).build();}).build();}
    private void start()throws Exception {
        Intent i=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),Review40HarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=InstrumentationRegistry.getInstrumentation().startActivitySync(i);long end=SystemClock.elapsedRealtime()+7000;boolean[] ready={false};
        while(SystemClock.elapsedRealtime()<end){ui(()->ready[0]=((FilmHomeView)field(activity,"homePanel")).cardCount()>0);if(ready[0])return;SystemClock.sleep(50);}fail("Owned home should render first");
    }
    @Test public void recordedSubresourceFailureKeepsNativePostersAndAlwaysCancels()throws Exception {
        start();AtomicInteger cancelled=new AtomicInteger();ui(()->{
            FilmConnectionState c=(FilmConnectionState)field(activity,"connection");c.observe("https://blocked.invalid/image.png?private=1",false);
            ((FlixMomoActivity)activity).certificateFailure(cancelled::incrementAndGet,new SslError(SslError.SSL_EXPIRED,new SslCertificate("owned","owned",new Date(),new Date()),"https://blocked.invalid/image.png?private=1"));
            assertEquals(1,cancelled.get());assertFalse((Boolean)field(activity,"mainFrameError"));assertTrue(((FilmHomeView)field(activity,"homePanel")).cardCount()>0);
            assertFalse(field(activity,"connectionDetail").toString().contains("private=1"));assertTrue(field(activity,"connectionDetail").toString().contains("CERTIFICATE_EXPIRED"));
        });
    }
    @Test public void mainCertificateFailureRemainsBlockedAfterPageFinished()throws Exception {
        start();AtomicInteger cancelled=new AtomicInteger();ui(()->{WebView w=(WebView)field(activity,"browser");String url=w.getUrl();
            ((FlixMomoActivity)activity).certificateFailure(cancelled::incrementAndGet,new SslError(SslError.SSL_IDMISMATCH,new SslCertificate("owned","owned",new Date(),new Date()),url));
            w.getWebViewClient().onPageFinished(w,url);assertEquals(1,cancelled.get());assertTrue((Boolean)field(activity,"mainFrameError"));assertFalse((Boolean)field(activity,"pageReady"));
        });
    }
    @Test public void unknownCertificateFailureDoesNotGetIgnored()throws Exception {
        start();AtomicInteger cancelled=new AtomicInteger();ui(()->{
            ((FlixMomoActivity)activity).certificateFailure(cancelled::incrementAndGet,new SslError(SslError.SSL_UNTRUSTED,new SslCertificate("owned","owned",new Date(),new Date()),"https://unknown.invalid/top"));
            assertEquals(1,cancelled.get());assertTrue((Boolean)field(activity,"mainFrameError"));
        });
    }
    @Test public void requestScopeIsResetBetweenNavigations(){FilmConnectionState c=new FilmConnectionState();c.begin("https://flixmomo.app/search?q=one");c.observe("https://resource.invalid/x",false);assertEquals(FilmConnectionState.Scope.SUBRESOURCE,c.scope("https://resource.invalid/x"));c.begin("https://flixmomo.app/search?q=two");assertEquals(FilmConnectionState.Scope.UNKNOWN,c.scope("https://resource.invalid/x"));}
    @Test public void redirectedMainIsNeverDemotedToResource(){FilmConnectionState c=new FilmConnectionState();c.begin("https://flixmomo.app/");c.observe("https://flixmomo.st/",true);c.observe("https://flixmomo.st/",false);assertEquals(FilmConnectionState.Scope.MAIN_DOCUMENT,c.scope("https://flixmomo.st/"));}
    @Test public void failureDetailNeverContainsPathCredentialsOrQuery(){assertEquals("https://flixmomo.app",FilmConnectionState.origin("https://name:secret@flixmomo.app/search?q=secret#private"));}
    @Test public void dateAndNameFailureAdviceIsSpecific(){assertTrue(FilmConnectionState.help(SslError.SSL_EXPIRED).contains("date/time"));assertEquals("CERTIFICATE_NAME_MISMATCH",FilmConnectionState.reason(SslError.SSL_IDMISMATCH));}
    @Test public void exactCollectorAddressOnly(){assertTrue(TelemetryDelivery.endpoint("https://ghartv-telemetry.ghartv-47d9a0.workers.dev/").endsWith("/v1/events"));for(String s:new String[]{"http://ghartv-telemetry.ghartv-47d9a0.workers.dev/","https://foreign.invalid/","https://user:pass@ghartv-telemetry.ghartv-47d9a0.workers.dev/","https://ghartv-telemetry.ghartv-47d9a0.workers.dev/?token=x","https://ghartv-telemetry.ghartv-47d9a0.workers.dev/admin"})assertEquals("",TelemetryDelivery.endpoint(s));}
    @Test public void ackRequiresCompleteCountAndRealBoolean()throws Exception {assertTrue(TelemetryDelivery.acknowledged(new JSONObject("{ok:true,accepted:1,rejected:0}"),1));for(String s:new String[]{"{ok:true,accepted:0,rejected:0}","{ok:true,accepted:1,rejected:1}","{ok:'true',accepted:1,rejected:0}","{ok:true,accepted:'1',rejected:0}","{ok:true,accepted:1}"})assertFalse(TelemetryDelivery.acknowledged(new JSONObject(s),1));}
    @Test public void disabledDiagnosticsMakeNoRequestOrEvent()throws Exception {enabled(false);queue();assertEquals(0,Telemetry.queuedCount(ctx));assertEquals(Telemetry.UploadOutcome.DISABLED,Telemetry.upload(ctx,client(200,"{}")));assertEquals(0,calls.get());}
    @Test public void completeAckRemovesRealQueueAndRecordsSuccess()throws Exception {enabled(true);queue();assertEquals(1,Telemetry.queuedCount(ctx));assertEquals(Telemetry.UploadOutcome.SUCCESS,Telemetry.upload(ctx,client(200,"{\"ok\":true,\"accepted\":1,\"rejected\":0}")));assertEquals(0,Telemetry.queuedCount(ctx));assertTrue(Telemetry.lastUploadAt(ctx)>0);assertEquals(1,new JSONObject(payload).getJSONArray("events").length());}
    @Test public void partialAckRetainsQueue()throws Exception {enabled(true);queue();assertEquals(Telemetry.UploadOutcome.RETRY,Telemetry.upload(ctx,client(200,"{\"ok\":true,\"accepted\":0,\"rejected\":1}")));assertEquals(1,Telemetry.queuedCount(ctx));assertEquals(0,Telemetry.lastUploadAt(ctx));}
    @Test public void authFailureRetainsQueueAndIsVisible()throws Exception {enabled(true);queue();assertEquals(Telemetry.UploadOutcome.PERMANENT_FAILURE,Telemetry.upload(ctx,client(401,"{}")));assertEquals(1,Telemetry.queuedCount(ctx));assertTrue(Telemetry.lastStatus(ctx).contains("401"));}
    @Test public void redirectDoesNotForwardIngestCredential()throws Exception {enabled(true);queue();assertEquals(Telemetry.UploadOutcome.PERMANENT_FAILURE,Telemetry.upload(ctx,client(302,"{}")));assertEquals(1,calls.get());assertEquals(1,Telemetry.queuedCount(ctx));assertTrue(Telemetry.lastStatus(ctx).contains("redirect not followed"));}
    @Test public void malformedAckRetainsQueue()throws Exception {enabled(true);queue();assertEquals(Telemetry.UploadOutcome.RETRY,Telemetry.upload(ctx,client(200,"not json")));assertEquals(1,Telemetry.queuedCount(ctx));}
    @Test public void privateFieldsAreNotInSerializedFilmEvent()throws Exception {enabled(true);queue();Telemetry.upload(ctx,client(200,"{\"ok\":true,\"accepted\":1,\"rejected\":0}"));assertTrue(payload.contains("film_search"));assertFalse(payload.contains("NEVER_UPLOAD"));assertFalse(payload.contains("private.invalid"));assertFalse(payload.contains("token=secret"));}
    @Test public void wrongConfiguredEndpointNeverReceivesBatch()throws Exception {enabled(true);queue();ctx.getSharedPreferences("ghartv_telemetry",0).edit().putString("config_endpoint","https://other.invalid").commit();assertEquals(Telemetry.UploadOutcome.PERMANENT_FAILURE,Telemetry.upload(ctx,client(200,"{}")));assertEquals(0,calls.get());assertEquals(1,Telemetry.queuedCount(ctx));}
    @Test public void deliveryCheckDoesNotOverrideConsent()throws Exception {enabled(false);CountDownLatch done=new CountDownLatch(1);Telemetry.sendNow(ctx,true,(outcome,status,queued)->{assertEquals(Telemetry.UploadOutcome.DISABLED,outcome);assertEquals(0,queued);done.countDown();});assertTrue(done.await(3,TimeUnit.SECONDS));assertFalse(Telemetry.isEnabled(ctx));}
}
