package in.ghartv.nova;

import android.content.Context;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * Jio stays off Tor. API/catalogue names use Android system DNS first and a
 * TLS-verified, Jio-scoped secure-DNS fallback only after resolution fails.
 * Media, preview, DRM and entitlement traffic retain the Review38-compatible
 * direct transport and all provider/TLS restrictions.
 */
final class JioNetwork {
    private static final JioSecureDns API_DNS = new JioSecureDns();
    private static final AtomicLong REPORTED = new AtomicLong();

    private static final OkHttpClient API = new OkHttpClient.Builder()
            .dns(API_DNS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(16, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(new ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build();

    private static final OkHttpClient MEDIA_DIRECT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(false)
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .build();

    private static final OkHttpClient PREVIEW_DIRECT = new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(false)
            .connectionPool(new ConnectionPool(4, 5, TimeUnit.MINUTES))
            .build();

    private JioNetwork() {}

    static Call.Factory api(Context context) { return API; }
    static Call.Factory media(Context context) { return MEDIA_DIRECT; }
    static Call.Factory preview(Context context) { return PREVIEW_DIRECT; }
    static OkHttpClient directApi() { return API; }
    static Dns dns() { return API_DNS; }

    static boolean fallbackUsedRecently() { return API_DNS.usedRecently(); }
    static boolean torUsedRecently() { return false; }
    static String recoveryLabel() {
        return fallbackUsedRecently() ? "secure DNS recovery" : "direct";
    }
    static void reportFallbackIfNew(Context context) {
        long count = API_DNS.fallbackCount();
        long previous = REPORTED.get();
        if (count > previous && REPORTED.compareAndSet(previous, count)) {
            Telemetry.event(context, "jio_dns_fallback", Telemetry.data(
                    "route", "secure_dns_after_system_failure",
                    "event_version_code", BuildConfig.VERSION_CODE));
        }
    }
}
