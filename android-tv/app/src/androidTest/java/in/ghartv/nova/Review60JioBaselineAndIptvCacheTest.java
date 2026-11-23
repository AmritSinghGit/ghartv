package in.ghartv.nova;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review60JioBaselineAndIptvCacheTest {
    private Context context;
    private File cache;
    private byte[] previous;
    private long previousModified;

    @Before public void before() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        cache = new File(context.getFilesDir(), "iptv-org-index.m3u");
        if (cache.exists()) {
            previous = java.nio.file.Files.readAllBytes(cache.toPath());
            previousModified = cache.lastModified();
        }
        IptvRepository.clearParsedCacheForTests();
    }

    @After public void after() throws Exception {
        IptvRepository.clearParsedCacheForTests();
        if (previous == null) {
            if (cache.exists()) assertTrue(cache.delete());
        } else {
            try (FileOutputStream out = new FileOutputStream(cache)) { out.write(previous); }
            assertTrue(cache.setLastModified(previousModified));
        }
    }

    @Test public void jioApiRetainsOkHttpWithoutTorRouteFactory() throws Exception {
        JioApiClient api = new JioApiClient(context);
        java.lang.reflect.Field field = JioApiClient.class.getDeclaredField("client");
        field.setAccessible(true);
        Object client = field.get(api);
        assertTrue(client instanceof OkHttpClient);
        assertFalse(client instanceof RouteFallbackCallFactory);
        assertFalse(JioNetwork.torUsedRecently());
    }

    @Test public void jioApiAndMediaUseDnsOnlyRecoveryAndNeverTor() {
        assertTrue(JioNetwork.api(context) instanceof OkHttpClient);
        assertTrue(((OkHttpClient) JioNetwork.api(context)).dns() instanceof JioSecureDns);
        assertTrue(JioNetwork.dns() instanceof JioSecureDns);
        assertTrue(JioNetwork.media(context) instanceof OkHttpClient);
        assertTrue(JioNetwork.preview(context) instanceof OkHttpClient);
        assertTrue(((OkHttpClient) JioNetwork.media(context)).dns() instanceof SecureDns);
        assertTrue(((OkHttpClient) JioNetwork.preview(context)).dns() instanceof SecureDns);
        assertSame(JioNetwork.mediaDns(),((OkHttpClient) JioNetwork.media(context)).dns());
        assertFalse(JioNetwork.torUsedRecently());
    }

    @Test public void staleVerifiedIptvCacheOpensWithoutWaitingForNetwork() throws Exception {
        writePlaylist();
        assertTrue(cache.setLastModified(System.currentTimeMillis() - 8L * 60L * 60L * 1000L));
        AtomicInteger calls = new AtomicInteger();
        IptvRepository repository = new IptvRepository(context, new FailingFactory(calls));
        IptvRepository.Result result = repository.load(false);
        assertTrue(result.fromCache);
        assertEquals(2, result.channels.size());
        assertEquals(0, calls.get());
        assertTrue(result.warning.contains("Refresh"));
    }

    @Test public void explicitIptvRefreshFallsBackToTheVerifiedCacheOnce() throws Exception {
        writePlaylist();
        AtomicInteger calls = new AtomicInteger();
        IptvRepository repository = new IptvRepository(context, new FailingFactory(calls));
        IptvRepository.Result result = repository.load(true);
        assertTrue(result.fromCache);
        assertEquals(2, result.channels.size());
        assertEquals(1, calls.get());
        assertTrue(result.warning.contains("could not refresh"));
    }

    private void writePlaylist() throws Exception {
        String raw = "#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"News\",Owned News\nhttps://public.example/news.m3u8\n"
                + "#EXTINF:-1 group-title=\"Movies\",Owned Movies\nhttps://public.example/movies.m3u8\n";
        try (FileOutputStream out = new FileOutputStream(cache)) {
            out.write(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class FailingFactory implements Call.Factory {
        private final AtomicInteger calls;
        FailingFactory(AtomicInteger value) { calls = value; }
        @Override public Call newCall(Request request) {
            calls.incrementAndGet();
            return new FailingCall(request);
        }
    }

    private static final class FailingCall implements Call {
        private final Request request;
        private boolean executed;
        private boolean canceled;
        FailingCall(Request value) { request = value; }
        @Override public Request request() { return request; }
        @Override public Response execute() throws IOException {
            if (executed) throw new IllegalStateException("Already Executed");
            executed = true;
            throw new UnknownHostException("owned fixture");
        }
        @Override public void enqueue(Callback callback) {
            if (executed) throw new IllegalStateException("Already Executed");
            executed = true;
            callback.onFailure(this, new UnknownHostException("owned fixture"));
        }
        @Override public void cancel() { canceled = true; }
        @Override public boolean isExecuted() { return executed; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call clone() { return new FailingCall(request); }
        @Override public Timeout timeout() { return new Timeout(); }
    }
}
