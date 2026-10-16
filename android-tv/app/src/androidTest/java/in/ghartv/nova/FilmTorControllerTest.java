package in.ghartv.nova;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FilmTorControllerTest {
    private Context context;private FakeListener listener;private FakeTransport transport;private FakeProxy proxy;private FilmTorController controller;
    static final String SITE="https://flixmomo.bet/",CATALOGUE="https://api.themoviedb.org/3/discover/movie";
    static final class FakeTransport implements FilmTorController.Transport{
        FilmTorController.TransportResult callback;int starts,cancels,closes,port=8118;
        public void start(FilmTorController.TransportResult value){starts++;callback=value;}public int currentHttpPort(){return port;}public void cancel(){cancels++;}public void close(){closes++;}
        void ready(boolean ok){FilmTorController.TransportResult c=callback;callback=null;c.ready(ok,ok?port:0);}
    }
    static final class FakeProxy implements FilmTorController.ProxyAdapter{
        FilmTorController.Result apply,clear;boolean supported=true,applied;int port;
        public boolean supported(){return supported;}public boolean applied(){return applied;}
        public void apply(int p,FilmTorController.Result c){port=p;apply=c;}public void clear(FilmTorController.Result c){clear=c;}
        void applied(boolean ok){if(ok)applied=true;FilmTorController.Result c=apply;apply=null;c.done(ok);}void cleared(boolean ok){if(ok)applied=false;FilmTorController.Result c=clear;clear=null;c.done(ok);}
    }
    static final class FakeListener implements FilmTorController.Listener{
        boolean foreground=true,playing;FilmTorController.ConsentResult consent;String status,failure,reload;boolean tor;int consentRequests;
        public boolean foreground(){return foreground;}public boolean mediaPlaying(){return playing;}
        public void requestConsent(long ticket,FilmTorController.ConsentResult c){consentRequests++;consent=c;}
        public void status(String value){status=value;}public void reload(String value){reload=value;}public void routeChanged(boolean value){tor=value;}public void failure(String value){failure=value;}
        void answer(boolean value){FilmTorController.ConsentResult c=consent;consent=null;c.answer(value);}
    }
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.getSharedPreferences("ghartv.film-tor-fallback",Context.MODE_PRIVATE).edit().clear().commit();listener=new FakeListener();transport=new FakeTransport();proxy=new FakeProxy();controller=new FilmTorController(context,listener,transport,proxy);controller.beginNavigation(SITE);}
    @After public void close(){controller.destroy();}
    private void enable(){controller.setEnabled(true);}
    private void fail(){assertTrue(controller.failure(CATALOGUE,FilmTorFallback.Failure.CONNECT));}
    private void activate(){enable();fail();assertEquals(1,transport.starts);transport.ready(true);assertEquals(8118,proxy.port);proxy.applied(true);assertTrue(controller.active());}

    @Test public void directFailureAsksBeforeStartingTor(){fail();assertEquals(1,listener.consentRequests);assertEquals(0,transport.starts);}
    @Test public void consentStartsCircuitThenProxyThenReload(){fail();listener.answer(true);assertEquals(1,transport.starts);transport.ready(true);assertNotNull(proxy.apply);proxy.applied(true);assertTrue(listener.tor);assertEquals(SITE,listener.reload);}
    @Test public void refusalPreservesDirectAndDoesNotStart(){fail();listener.answer(false);assertEquals(0,transport.starts);assertFalse(controller.active());assertNotNull(listener.failure);}
    @Test public void enabledFallbackStillTriesDirectFirst(){enable();assertEquals(0,transport.starts);fail();assertEquals(1,transport.starts);}
    @Test public void jioAndImageFailuresNeverStartTor(){enable();assertFalse(controller.failure("https://jiotvapi.cdn.jio.com/",FilmTorFallback.Failure.CONNECT));assertFalse(controller.failure("https://image.tmdb.org/a.jpg",FilmTorFallback.Failure.CONNECT));assertEquals(0,transport.starts);}
    @Test public void tlsAuthAndRateFailuresNeverStartTor(){enable();for(FilmTorFallback.Failure f:new FilmTorFallback.Failure[]{FilmTorFallback.Failure.TLS,FilmTorFallback.Failure.HTTP_AUTH,FilmTorFallback.Failure.HTTP_DENIED,FilmTorFallback.Failure.HTTP_RATE_LIMIT,FilmTorFallback.Failure.EMPTY_RESULTS})assertFalse(controller.failure(CATALOGUE,f));assertEquals(0,transport.starts);}
    @Test public void activePlaybackAndBackgroundNeverSwitch(){enable();listener.playing=true;assertFalse(controller.failure(CATALOGUE,FilmTorFallback.Failure.TIMEOUT));listener.playing=false;listener.foreground=false;assertFalse(controller.failure(CATALOGUE,FilmTorFallback.Failure.TIMEOUT));assertEquals(0,transport.starts);}
    @Test public void staleCircuitCallbackCannotReload(){enable();fail();controller.beginNavigation(SITE+"search?q=next");transport.ready(true);assertNull(proxy.apply);assertNull(listener.reload);}
    @Test public void proxyFailureNeverReloads(){enable();fail();transport.ready(true);proxy.applied(false);assertNull(listener.reload);assertNotNull(listener.failure);}
    @Test public void explicitDirectWaitsForClearAcknowledgement(){activate();assertEquals(SITE,listener.reload);listener.reload=null;controller.requestDirect();assertTrue(proxy.applied);assertNull(listener.reload);assertTrue(listener.tor);proxy.cleared(true);assertFalse(controller.active());assertFalse(listener.tor);assertEquals(SITE,listener.reload);}
    @Test public void disablingActiveFallbackClearsIt(){activate();controller.setEnabled(false);assertNotNull(proxy.clear);proxy.cleared(true);assertFalse(controller.enabled());assertFalse(controller.active());}
    @Test public void catalogueSuccessPreventsFallback(){enable();controller.catalogueObserved(4);assertFalse(controller.failure(CATALOGUE,FilmTorFallback.Failure.NO_CATALOGUE_RESPONSE));assertEquals(0,transport.starts);}
}
