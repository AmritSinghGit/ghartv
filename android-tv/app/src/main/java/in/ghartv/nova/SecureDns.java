package in.ghartv.nova;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * System DNS first, followed by independently bootstrapped TLS-verified DNS providers.
 * A secure-only instance is used after a connection failure so a stale system answer is
 * not retried. Private, local and single-label names are never sent to a public resolver.
 */
final class SecureDns implements Dns {
    interface HostScope { boolean allows(String host); }

    private static final long CACHE_MS = 10L * 60L * 1000L;
    /** A hung emulator/router resolver must not consume the whole request budget. */
    static final long SYSTEM_DNS_BUDGET_MS = 3000L;
    private static final ExecutorService SYSTEM_LOOKUPS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "ghartv-system-dns");
        thread.setDaemon(true);
        return thread;
    });
    private static final class Cached {
        final long until;
        final List<InetAddress> addresses;
        Cached(List<InetAddress> values) {
            until = android.os.SystemClock.elapsedRealtime() + CACHE_MS;
            addresses = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    private final Dns primary;
    private final List<Dns> secure;
    private final HostScope scope;
    private final boolean usePrimary;
    private final Map<String,Cached> cache = new ConcurrentHashMap<>();
    private final AtomicLong fallbackCount = new AtomicLong();
    private volatile long lastFallbackAt;

    SecureDns(Dns first, List<Dns> providers, HostScope allowed, boolean primaryEnabled) {
        primary = first == null ? Dns.SYSTEM : first;
        secure = providers == null ? Collections.emptyList() : new ArrayList<>(providers);
        scope = allowed == null ? SecureDns::publicHostname : allowed;
        usePrimary = primaryEnabled;
    }

    static SecureDns systemFirst(HostScope scope) {
        return new SecureDns(Dns.SYSTEM, defaultProviders(), scope, true);
    }

    static SecureDns secureOnly(HostScope scope) {
        return new SecureDns(Dns.SYSTEM, defaultProviders(), scope, false);
    }

    @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        String host = normalize(hostname);
        if (host.isEmpty()) throw new UnknownHostException("empty hostname");
        UnknownHostException directFailure = null;
        boolean recoverable = scope.allows(host);
        if (recoverable) {
            // A name that needed recovery minutes ago reuses that answer instead of
            // waiting on the same failing system resolver for every HLS/DASH connection.
            Cached recent = cache.get(host);
            if (recent != null && recent.until > android.os.SystemClock.elapsedRealtime()
                    && !recent.addresses.isEmpty()) {
                noteFallback();
                return recent.addresses;
            }
        }
        if (usePrimary) {
            try {
                List<InetAddress> direct = recoverable ? boundedPrimary(host) : primary.lookup(host);
                if (direct != null && !direct.isEmpty()) return direct;
                directFailure = new UnknownHostException(host + " returned no system DNS addresses");
            } catch (UnknownHostException failure) {
                directFailure = failure;
            }
        }
        if (!recoverable) {
            if (directFailure != null) throw directFailure;
            throw new UnknownHostException(host + " is outside the secure DNS recovery scope");
        }

        long now = android.os.SystemClock.elapsedRealtime();
        Cached saved = cache.get(host);
        if (saved != null && saved.until > now && !saved.addresses.isEmpty()) {
            noteFallback();
            return saved.addresses;
        }
        if (saved != null) cache.remove(host, saved);

        UnknownHostException aggregate = directFailure;
        for (Dns provider : secure) {
            if (provider == null) continue;
            try {
                List<InetAddress> values = provider.lookup(host);
                if (values == null || values.isEmpty()) {
                    throw new UnknownHostException(host + " returned no secure DNS addresses");
                }
                cache.put(host, new Cached(values));
                noteFallback();
                return values;
            } catch (UnknownHostException failure) {
                if (aggregate == null) aggregate = failure;
                else if (aggregate != failure) aggregate.addSuppressed(failure);
            }
        }
        if (aggregate != null) throw aggregate;
        throw new UnknownHostException(host + " has no available DNS route");
    }

    private List<InetAddress> boundedPrimary(String host) throws UnknownHostException {
        if (primary != Dns.SYSTEM) return primary.lookup(host);
        Future<List<InetAddress>> pending = SYSTEM_LOOKUPS.submit(() -> primary.lookup(host));
        try {
            return pending.get(SYSTEM_DNS_BUDGET_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException slow) {
            pending.cancel(true);
            throw new UnknownHostException(host + " system DNS gave no answer within "
                    + SYSTEM_DNS_BUDGET_MS + " ms");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof UnknownHostException) throw (UnknownHostException) cause;
            UnknownHostException wrapped = new UnknownHostException(host + " system DNS failed");
            wrapped.initCause(cause);
            throw wrapped;
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new UnknownHostException(host + " DNS lookup interrupted");
        }
    }

    long fallbackCount() { return fallbackCount.get(); }
    boolean usedRecently() {
        long at = lastFallbackAt;
        return at > 0L && android.os.SystemClock.elapsedRealtime() - at < CACHE_MS;
    }

    private void noteFallback() {
        fallbackCount.incrementAndGet();
        lastFallbackAt = android.os.SystemClock.elapsedRealtime();
    }

    static boolean publicHostname(String hostname) {
        String host = normalize(hostname);
        if (host.isEmpty() || host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".internal")
                || host.indexOf('.') < 1 || host.contains(":")) return false;
        if (host.matches("[0-9.]+")) return false;
        return host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?");
    }

    private static String normalize(String hostname) {
        return hostname == null ? "" : hostname.trim().toLowerCase(Locale.ROOT);
    }

    private static List<Dns> defaultProviders() {
        List<Dns> providers = new ArrayList<>();
        add(providers, "https://cloudflare-dns.com/dns-query", new byte[][]{
                {(byte)1,(byte)1,(byte)1,(byte)1}, {(byte)1,(byte)0,(byte)0,(byte)1}});
        add(providers, "https://dns.google/dns-query", new byte[][]{
                {(byte)8,(byte)8,(byte)8,(byte)8}, {(byte)8,(byte)8,(byte)4,(byte)4}});
        add(providers, "https://dns.quad9.net/dns-query", new byte[][]{
                {(byte)9,(byte)9,(byte)9,(byte)9}, {(byte)149,(byte)112,(byte)112,(byte)112}});
        return Collections.unmodifiableList(providers);
    }

    private static void add(List<Dns> providers, String url, byte[][] addresses) {
        try {
            List<InetAddress> bootstrap = new ArrayList<>();
            for (byte[] value : addresses) bootstrap.add(InetAddress.getByAddress(value));
            OkHttpClient client = new OkHttpClient.Builder()
                    .dns(Dns.SYSTEM)
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .callTimeout(4, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
            providers.add(new DnsOverHttps.Builder()
                    .client(client)
                    .url(HttpUrl.get(url))
                    .bootstrapDnsHosts(bootstrap.toArray(new InetAddress[0]))
                    .includeIPv6(false)
                    .post(true)
                    .build());
        } catch (Exception ignored) {
            // Keep the remaining independently bootstrapped providers.
        }
    }
}
