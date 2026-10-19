package in.ghartv.nova;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLHandshakeException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review59NetworkRecoveryTest {
    private static InetAddress address() {
        try { return InetAddress.getByAddress(new byte[]{1, 1, 1, 1}); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }
    private static String family(String name) { return "review59_" + name + "_" + System.nanoTime(); }
    private static Request get() { return new Request.Builder().url("https://public.example/test").build(); }
    private static Request post() {
        return new Request.Builder().url("https://public.example/test")
                .post(new FormBody.Builder().add("fixture", "owned").build()).build();
    }

    @Test public void secureDnsUsesSystemBeforeAnyPublicResolver() throws Exception {
        AtomicInteger primary = new AtomicInteger(), secure = new AtomicInteger();
        Dns first = host -> { primary.incrementAndGet(); return Collections.singletonList(address()); };
        Dns fallback = host -> { secure.incrementAndGet(); return Collections.singletonList(address()); };
        SecureDns dns = new SecureDns(first, Collections.singletonList(fallback), SecureDns::publicHostname, true);
        assertEquals(1, dns.lookup("public.example").size());
        assertEquals(1, primary.get());
        assertEquals(0, secure.get());
        assertEquals(0, dns.fallbackCount());
    }

    @Test public void secureDnsTriesIndependentProvidersAndCachesTheAnswer() throws Exception {
        AtomicInteger firstSecure = new AtomicInteger(), secondSecure = new AtomicInteger();
        Dns system = host -> { throw new UnknownHostException(host); };
        Dns failed = host -> { firstSecure.incrementAndGet(); throw new UnknownHostException("one"); };
        Dns recovered = host -> { secondSecure.incrementAndGet(); return Collections.singletonList(address()); };
        SecureDns dns = new SecureDns(system, Arrays.asList(failed, recovered), SecureDns::publicHostname, true);
        assertEquals(1, dns.lookup("public.example").size());
        assertEquals(1, dns.lookup("public.example").size());
        assertEquals(1, firstSecure.get());
        assertEquals(1, secondSecure.get());
        assertEquals(2, dns.fallbackCount());
    }

    @Test public void secureOnlySkipsSystemAndNeverLeaksLocalNames() throws Exception {
        AtomicInteger primary = new AtomicInteger(), secure = new AtomicInteger();
        Dns system = host -> { primary.incrementAndGet(); return Collections.singletonList(address()); };
        Dns provider = host -> { secure.incrementAndGet(); return Collections.singletonList(address()); };
        SecureDns dns = new SecureDns(system, Collections.singletonList(provider), SecureDns::publicHostname, false);
        assertEquals(1, dns.lookup("public.example").size());
        assertEquals(0, primary.get());
        assertEquals(1, secure.get());
        try { dns.lookup("localhost"); fail(); }
        catch (UnknownHostException expected) { assertTrue(expected.getMessage().contains("outside")); }
        assertEquals(1, secure.get());
    }

    @Test public void jioScopeIncludesJioCinemaButRejectsLookalikes() {
        assertTrue(JioSecureDns.isJioHost("jiotvapi.cdn.jio.com"));
        assertTrue(JioSecureDns.isJioHost("media.jiocinema.com"));
        assertFalse(JioSecureDns.isJioHost("jio.com.evil.invalid"));
        assertFalse(JioSecureDns.isJioHost("notjio.com"));
    }

    @Test public void directSuccessDoesNotOpenSecureOrTor() throws Exception {
        FakeFactory direct = FakeFactory.success(204), secure = FakeFactory.failure(new UnknownHostException("unused"));
        AtomicInteger torOpens = new AtomicInteger();
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure,
                () -> { torOpens.incrementAndGet(); return FakeFactory.success(204); }, family("direct"));
        try (Response response = routes.newCall(get()).execute()) { assertEquals(204, response.code()); }
        assertEquals(1, direct.calls.get());
        assertEquals(0, secure.calls.get());
        assertEquals(0, torOpens.get());
    }

    @Test public void dnsFailureUsesSecureRouteBeforeTor() throws Exception {
        FakeFactory direct = FakeFactory.failure(new UnknownHostException("dns"));
        FakeFactory secure = FakeFactory.success(204);
        AtomicInteger torOpens = new AtomicInteger();
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure,
                () -> { torOpens.incrementAndGet(); return FakeFactory.success(204); }, family("secure"));
        try (Response response = routes.newCall(get()).execute()) { assertEquals(204, response.code()); }
        assertEquals(1, direct.calls.get());
        assertEquals(1, secure.calls.get());
        assertEquals(0, torOpens.get());
    }

    @Test public void connectionFailuresReachTorAsTheThirdRoute() throws Exception {
        FakeFactory direct = FakeFactory.failure(new ConnectException("direct"));
        FakeFactory secure = FakeFactory.failure(new ConnectException("secure"));
        FakeFactory tor = FakeFactory.success(204);
        AtomicInteger torOpens = new AtomicInteger();
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure,
                () -> { torOpens.incrementAndGet(); return tor; }, family("tor"));
        try (Response response = routes.newCall(get()).execute()) { assertEquals(204, response.code()); }
        assertEquals(1, direct.calls.get());
        assertEquals(1, secure.calls.get());
        assertEquals(1, tor.calls.get());
        assertEquals(1, torOpens.get());
    }

    @Test public void tlsFailureIsReturnedWithoutSecureOrTorRetry() {
        FakeFactory direct = FakeFactory.failure(new SSLHandshakeException("fixture certificate failure"));
        FakeFactory secure = FakeFactory.success(204), tor = FakeFactory.success(204);
        AtomicInteger torOpens = new AtomicInteger();
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure,
                () -> { torOpens.incrementAndGet(); return tor; }, family("tls"));
        try { routes.newCall(get()).execute(); fail(); }
        catch (SSLHandshakeException expected) { assertEquals("fixture certificate failure", expected.getMessage()); }
        catch (IOException wrong) { fail(wrong.toString()); }
        assertEquals(0, secure.calls.get());
        assertEquals(0, torOpens.get());
    }

    @Test public void providerHttpDenialIsReturnedWithoutRouteShopping() throws Exception {
        FakeFactory direct = FakeFactory.success(403), secure = FakeFactory.success(204), tor = FakeFactory.success(204);
        AtomicInteger torOpens = new AtomicInteger();
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure,
                () -> { torOpens.incrementAndGet(); return tor; }, family("http"));
        try (Response response = routes.newCall(get()).execute()) { assertEquals(403, response.code()); }
        assertEquals(0, secure.calls.get());
        assertEquals(0, torOpens.get());
    }

    @Test public void postTimeoutIsNotReplayedAcrossRoutes() {
        FakeFactory direct = FakeFactory.failure(new SocketTimeoutException("fixture timeout"));
        FakeFactory secure = FakeFactory.success(204), tor = FakeFactory.success(204);
        AtomicInteger torOpens = new AtomicInteger();
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure,
                () -> { torOpens.incrementAndGet(); return tor; }, family("post"));
        try { routes.newCall(post()).execute(); fail(); }
        catch (SocketTimeoutException expected) { assertEquals("fixture timeout", expected.getMessage()); }
        catch (IOException wrong) { fail(wrong.toString()); }
        assertEquals(0, secure.calls.get());
        assertEquals(0, torOpens.get());
    }

    @Test public void getTimeoutCanRecoverThroughSecureDnsRoute() throws Exception {
        FakeFactory direct = FakeFactory.failure(new SocketTimeoutException("fixture timeout"));
        FakeFactory secure = FakeFactory.success(204);
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure, null, family("get"));
        try (Response response = routes.newCall(get()).execute()) { assertEquals(204, response.code()); }
        assertEquals(1, direct.calls.get());
        assertEquals(1, secure.calls.get());
    }

    @Test public void successfulTorRouteIsReusedFirstForFollowUpSegments() throws Exception {
        FakeFactory direct = FakeFactory.failure(new ConnectException("direct"));
        FakeFactory secure = FakeFactory.failure(new ConnectException("secure"));
        FakeFactory tor = FakeFactory.success(204, 204);
        AtomicInteger torOpens = new AtomicInteger();
        String family = family("sticky_tor");
        RouteFallbackCallFactory routes = new RouteFallbackCallFactory(direct, secure,
                () -> { torOpens.incrementAndGet(); return tor; }, family);
        try (Response first = routes.newCall(get()).execute()) { assertEquals(204, first.code()); }
        try (Response second = routes.newCall(get()).execute()) { assertEquals(204, second.code()); }
        assertEquals(1, direct.calls.get());
        assertEquals(1, secure.calls.get());
        assertEquals(2, tor.calls.get());
        assertEquals(2, torOpens.get());
        assertEquals(NetworkRouteState.TOR, NetworkRouteState.recent(family));
    }

    private static final class FakeFactory implements Call.Factory {
        final AtomicInteger calls = new AtomicInteger();
        final Queue<Object> outcomes = new ArrayDeque<>();

        static FakeFactory success(int... codes) {
            FakeFactory value = new FakeFactory();
            for (int code : codes) value.outcomes.add(Integer.valueOf(code));
            return value;
        }
        static FakeFactory failure(IOException... failures) {
            FakeFactory value = new FakeFactory();
            value.outcomes.addAll(Arrays.asList(failures));
            return value;
        }
        @Override public Call newCall(Request request) {
            calls.incrementAndGet();
            Object outcome = outcomes.poll();
            if (outcome == null) outcome = Integer.valueOf(204);
            return new FakeCall(request, outcome);
        }
    }

    private static final class FakeCall implements Call {
        final Request request;
        final Object outcome;
        boolean executed;
        boolean canceled;
        FakeCall(Request value, Object result) { request = value; outcome = result; }
        @Override public Request request() { return request; }
        @Override public Response execute() throws IOException {
            if (executed) throw new IllegalStateException("Already Executed");
            executed = true;
            if (canceled) throw new IOException("Canceled");
            if (outcome instanceof IOException) throw (IOException) outcome;
            return response(request, ((Integer) outcome).intValue());
        }
        @Override public void enqueue(Callback callback) {
            if (executed) throw new IllegalStateException("Already Executed");
            executed = true;
            if (canceled) { callback.onFailure(this, new IOException("Canceled")); return; }
            if (outcome instanceof IOException) callback.onFailure(this, (IOException) outcome);
            else {
                try { callback.onResponse(this, response(request, ((Integer) outcome).intValue())); }
                catch (IOException ignored) {}
            }
        }
        @Override public void cancel() { canceled = true; }
        @Override public boolean isExecuted() { return executed; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call clone() { return new FakeCall(request, outcome); }
        @Override public Timeout timeout() { return new Timeout(); }
    }

    private static Response response(Request request, int code) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message(code == 204 ? "No Content" : "Fixture").build();
    }
}
