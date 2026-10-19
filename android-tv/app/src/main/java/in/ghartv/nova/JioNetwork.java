package in.ghartv.nova;

import android.content.Context;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * Shared Jio transport. It preserves provider authentication, TLS, DRM and entitlement
 * responses while recovering route failures through secure DNS and then app-local Tor.
 */
final class JioNetwork {
    private static final JioSecureDns API_DNS = new JioSecureDns();
    private static final JioSecureDns API_SECURE_DNS = JioSecureDns.secureOnly();
    private static final SecureDns MEDIA_DNS = SecureDns.systemFirst(SecureDns::publicHostname);
    private static final SecureDns MEDIA_SECURE_DNS = SecureDns.secureOnly(SecureDns::publicHostname);

    private static final OkHttpClient API_DIRECT = apiClient(API_DNS);
    private static final OkHttpClient API_SECURE = apiClient(API_SECURE_DNS);
    private static final OkHttpClient MEDIA_DIRECT = mediaClient(MEDIA_DNS, false);
    private static final OkHttpClient MEDIA_SECURE = mediaClient(MEDIA_SECURE_DNS, false);
    private static final OkHttpClient PREVIEW_DIRECT = mediaClient(MEDIA_DNS, true);
    private static final OkHttpClient PREVIEW_SECURE = mediaClient(MEDIA_SECURE_DNS, true);

    private static final AtomicLong REPORTED = new AtomicLong();
    private JioNetwork() {}

    private static OkHttpClient apiClient(Dns dns) {
        return new OkHttpClient.Builder().dns(dns)
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .callTimeout(16, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(8, 5, TimeUnit.MINUTES))
                .build();
    }

    private static OkHttpClient mediaClient(Dns dns, boolean preview) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().dns(dns)
                .connectTimeout(preview ? 4 : 10, TimeUnit.SECONDS)
                .readTimeout(preview ? 6 : 22, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(false)
                .connectionPool(new ConnectionPool(preview ? 4 : 10, 5, TimeUnit.MINUTES));
        if (preview) builder.callTimeout(7, TimeUnit.SECONDS);
        return builder.build();
    }

    static Call.Factory api(Context context) {
        return new RouteFallbackCallFactory(context, API_DIRECT, API_SECURE, true, "jio_api", 70_000L);
    }
    static Call.Factory media(Context context) {
        return new RouteFallbackCallFactory(context, MEDIA_DIRECT, MEDIA_SECURE, true, "jio_media", 70_000L);
    }
    static Call.Factory preview(Context context) {
        // Automatic previews stay bounded. They may reuse an already-ready Tor route, while
        // a full Watch Live request is allowed to wait for a cold Tor circuit.
        return new RouteFallbackCallFactory(context, PREVIEW_DIRECT, PREVIEW_SECURE, true, "jio_preview", 2_500L);
    }
    static OkHttpClient directApi() { return API_DIRECT; }
    static Dns dns() { return API_DNS; }

    static boolean fallbackUsedRecently() {
        return API_DNS.usedRecently() || API_SECURE_DNS.usedRecently()
                || MEDIA_DNS.usedRecently() || MEDIA_SECURE_DNS.usedRecently()
                || NetworkRouteState.usedRecently("jio", NetworkRouteState.SECURE_DNS)
                || NetworkRouteState.usedRecently("jio", NetworkRouteState.TOR);
    }
    static boolean torUsedRecently() {
        return NetworkRouteState.usedRecently("jio", NetworkRouteState.TOR);
    }
    static String recoveryLabel() {
        return torUsedRecently() ? "Tor recovery"
                : fallbackUsedRecently() ? "secure DNS recovery" : "direct";
    }
    static void reportFallbackIfNew(Context context) {
        long count = API_DNS.fallbackCount() + API_SECURE_DNS.fallbackCount()
                + MEDIA_DNS.fallbackCount() + MEDIA_SECURE_DNS.fallbackCount()
                + NetworkRouteState.generation();
        long previous = REPORTED.get();
        if (count > previous && REPORTED.compareAndSet(previous, count)) {
            Telemetry.event(context, "jio_network_recovery", Telemetry.data(
                    "route", torUsedRecently() ? "tor_after_direct_and_secure_dns" : "secure_dns_after_direct_failure",
                    "event_version_code", BuildConfig.VERSION_CODE));
        }
    }
}
