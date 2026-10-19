package in.ghartv.nova;

import android.content.Context;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

/**
 * Per-request recovery chain: direct -> secure-DNS-only -> app-local Tor.
 * A route that succeeded recently is reused first for follow-up playlist/segment calls.
 * Tor is attempted only after a route-level IOException; HTTP, TLS, auth, DRM and
 * provider-denial responses are returned unchanged.
 */
final class RouteFallbackCallFactory implements Call.Factory {
    interface TorFactory { Call.Factory open() throws IOException; }
    private static final ExecutorService RECOVERY = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "ghartv-route-recovery");
        thread.setDaemon(true);
        return thread;
    });

    private final Call.Factory direct;
    private final Call.Factory secure;
    private final TorFactory tor;
    private final String family;

    RouteFallbackCallFactory(Context context, OkHttpClient directClient, OkHttpClient secureClient,
            boolean allowTor, String routeFamily) {
        this(context, directClient, secureClient, allowTor, routeFamily, 70_000L);
    }

    RouteFallbackCallFactory(Context context, OkHttpClient directClient, OkHttpClient secureClient,
            boolean allowTor, String routeFamily, long torWaitMs) {
        this(directClient, secureClient,
                allowTor ? new ProductionTorFactory(context, directClient, torWaitMs) : null,
                routeFamily);
    }

    RouteFallbackCallFactory(Call.Factory directFactory, Call.Factory secureFactory,
            TorFactory torFactory, String routeFamily) {
        direct = directFactory;
        secure = secureFactory;
        tor = torFactory;
        family = routeFamily == null ? "network" : routeFamily.trim().toLowerCase(Locale.ROOT);
    }

    @Override public Call newCall(Request request) { return new FallbackCall(request); }

    private String[] order() {
        String recent = NetworkRouteState.recent(family);
        if (NetworkRouteState.TOR.equals(recent) && tor != null) {
            return new String[]{NetworkRouteState.TOR, NetworkRouteState.DIRECT, NetworkRouteState.SECURE_DNS};
        }
        if (NetworkRouteState.SECURE_DNS.equals(recent)) {
            return tor == null
                    ? new String[]{NetworkRouteState.SECURE_DNS, NetworkRouteState.DIRECT}
                    : new String[]{NetworkRouteState.SECURE_DNS, NetworkRouteState.DIRECT, NetworkRouteState.TOR};
        }
        return tor == null
                ? new String[]{NetworkRouteState.DIRECT, NetworkRouteState.SECURE_DNS}
                : new String[]{NetworkRouteState.DIRECT, NetworkRouteState.SECURE_DNS, NetworkRouteState.TOR};
    }

    private Call.Factory factory(String route) throws IOException {
        if (NetworkRouteState.DIRECT.equals(route)) return direct;
        if (NetworkRouteState.SECURE_DNS.equals(route)) return secure;
        if (NetworkRouteState.TOR.equals(route) && tor != null) return tor.open();
        throw new IOException("Network route is unavailable");
    }

    private final class FallbackCall implements Call {
        private final Request request;
        private final AtomicBoolean executed = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile boolean canceled;
        private volatile Call current;
        private final Timeout timeout = new Timeout();

        FallbackCall(Request value) { request = value; }
        @Override public Request request() { return request; }

        @Override public Response execute() throws IOException {
            if (!executed.compareAndSet(false, true)) throw new IllegalStateException("Already Executed");
            String[] routes = order();
            IOException previous = null;
            for (int index = 0; index < routes.length; index++) {
                String route = routes[index];
                Call.Factory selected;
                try {
                    selected = factory(route);
                } catch (IOException unavailable) {
                    chain(unavailable, previous);
                    previous = unavailable;
                    if (index == routes.length - 1) throw unavailable;
                    continue;
                }
                try {
                    return run(selected, route);
                } catch (IOException failure) {
                    chain(failure, previous);
                    if (!retryable(request, failure) || index == routes.length - 1) throw failure;
                    previous = failure;
                }
            }
            throw previous == null ? new IOException("No network route was available") : previous;
        }

        private Response run(Call.Factory selected, String route) throws IOException {
            if (canceled) throw new IOException("Canceled");
            current = selected.newCall(request);
            if (canceled) current.cancel();
            Response response = current.execute();
            NetworkRouteState.mark(family, route);
            completed.set(true);
            return response;
        }

        @Override public void enqueue(Callback callback) {
            if (!executed.compareAndSet(false, true)) throw new IllegalStateException("Already Executed");
            String[] routes = order();
            enqueueStage(routes, 0, null, callback);
        }

        private void enqueueStage(String[] routes, int index, IOException previous, Callback callback) {
            if (canceled) { finishFailure(callback, canceled(previous)); return; }
            if (index >= routes.length) {
                finishFailure(callback, previous == null ? new IOException("No network route was available") : previous);
                return;
            }
            String route = routes[index];
            if (NetworkRouteState.TOR.equals(route)) {
                RECOVERY.execute(() -> openAndEnqueue(routes, index, previous, callback));
            } else {
                openAndEnqueue(routes, index, previous, callback);
            }
        }

        private void openAndEnqueue(String[] routes, int index, IOException previous, Callback callback) {
            String route = routes[index];
            Call.Factory selected;
            try {
                selected = factory(route);
            } catch (IOException unavailable) {
                chain(unavailable, previous);
                enqueueStage(routes, index + 1, unavailable, callback);
                return;
            } catch (RuntimeException failure) {
                enqueueStage(routes, index + 1, asIo(failure, previous), callback);
                return;
            }
            if (canceled) { finishFailure(callback, canceled(previous)); return; }
            Call call;
            try { call = selected.newCall(request); }
            catch (RuntimeException failure) {
                finishFailure(callback, asIo(failure, previous));
                return;
            }
            current = call;
            if (canceled) call.cancel();
            call.enqueue(new Callback() {
                @Override public void onFailure(Call ignored, IOException failure) {
                    chain(failure, previous);
                    if (canceled || !retryable(request, failure) || index == routes.length - 1) {
                        finishFailure(callback, failure);
                    } else {
                        enqueueStage(routes, index + 1, failure, callback);
                    }
                }
                @Override public void onResponse(Call ignored, Response response) {
                    if (!completed.compareAndSet(false, true)) { response.close(); return; }
                    NetworkRouteState.mark(family, route);
                    try { callback.onResponse(FallbackCall.this, response); }
                    catch (IOException callbackFailure) { response.close(); }
                }
            });
        }

        private void finishFailure(Callback callback, IOException failure) {
            if (!completed.compareAndSet(false, true)) return;
            callback.onFailure(this, failure);
        }

        @Override public void cancel() {
            canceled = true;
            Call value = current;
            if (value != null) value.cancel();
        }
        @Override public boolean isExecuted() { return executed.get(); }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call clone() { return new FallbackCall(request); }
        @Override public Timeout timeout() { Call value=current; return value==null?timeout:value.timeout(); }
    }

    private static boolean retryable(Request request, IOException failure) {
        if (!NetworkFailure.routeFailure(failure)) return false;
        String method = request.method();
        if ("GET".equals(method) || "HEAD".equals(method)) return true;
        String classification = NetworkFailure.classify(failure);
        return "DNS_UNAVAILABLE".equals(classification) || NetworkFailure.beforeRequestConnection(failure);
    }

    private static void chain(IOException value, IOException previous) {
        if (previous != null && previous != value) value.addSuppressed(previous);
    }
    private static IOException canceled(IOException previous) {
        IOException value = new IOException("Canceled");
        chain(value, previous);
        return value;
    }
    private static IOException asIo(Throwable failure, IOException previous) {
        IOException value = failure instanceof IOException ? (IOException) failure : new IOException(failure);
        chain(value, previous);
        return value;
    }

    private static final class ProductionTorFactory implements TorFactory {
        private final Context app;
        private final OkHttpClient template;
        private final long waitMs;
        ProductionTorFactory(Context context, OkHttpClient client, long timeoutMs) {
            app = context.getApplicationContext();
            template = client;
            waitMs = Math.max(500L, timeoutMs);
        }
        @Override public Call.Factory open() throws IOException {
            int port = AppTorTunnel.get(app).awaitHttpPort(waitMs);
            if (port < 1 || port > 65535) throw new IOException("App-local Tor route was not ready");
            return template.newBuilder()
                    .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", port)))
                    .dns(Dns.SYSTEM)
                    .build();
        }
    }
}
