package in.ghartv.nova;

import android.content.Context;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * Jio never uses Tor. API and public media names use Android system DNS first,
 * then independently bootstrapped TLS-verified DNS only when system resolution
 * fails. Media bytes, DRM, authentication, entitlement and TLS remain direct.
 */
final class JioNetwork {
    private static final JioSecureDns API_DNS = new JioSecureDns();
    private static final SecureDns MEDIA_DNS = SecureDns.systemFirst(SecureDns::publicHostname);
    private static final AtomicLong API_REPORTED = new AtomicLong();
    private static final AtomicLong MEDIA_REPORTED = new AtomicLong();

    private static final OkHttpClient API = new OkHttpClient.Builder()
            .dns(API_DNS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(16, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(new ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build();

    private static final OkHttpClient MEDIA = new OkHttpClient.Builder()
            .dns(MEDIA_DNS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(false)
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .build();

    private static final OkHttpClient PREVIEW = new OkHttpClient.Builder()
            .dns(MEDIA_DNS)
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
    static Call.Factory media(Context context) { return MEDIA; }
    static Call.Factory preview(Context context) { return PREVIEW; }
    static OkHttpClient directApi() { return API; }
    static OkHttpClient mediaClient() { return MEDIA; }
    static OkHttpClient previewClient() { return PREVIEW; }
    static Dns dns() { return API_DNS; }
    static Dns mediaDns() { return MEDIA_DNS; }

    static boolean apiFallbackUsedRecently() { return API_DNS.usedRecently(); }
    static boolean mediaFallbackUsedRecently() { return MEDIA_DNS.usedRecently(); }
    static boolean fallbackUsedRecently() {
        return apiFallbackUsedRecently() || mediaFallbackUsedRecently();
    }
    static boolean torUsedRecently() { return false; }
    static String recoveryLabel() {
        return apiFallbackUsedRecently() ? "secure DNS recovery" : "direct";
    }
    static String mediaRecoveryLabel() {
        return mediaFallbackUsedRecently() ? "secure DNS recovery" : "direct";
    }
    static void reportFallbackIfNew(Context context) {
        long count = API_DNS.fallbackCount();
        long previous = API_REPORTED.get();
        if (count > previous && API_REPORTED.compareAndSet(previous, count)) {
            Telemetry.event(context, "jio_dns_fallback", Telemetry.data(
                    "plane", "api",
                    "route", "secure_dns_after_system_failure",
                    "event_version_code", BuildConfig.VERSION_CODE));
        }
    }
    static void reportMediaFallbackIfNew(Context context) {
        long count = MEDIA_DNS.fallbackCount();
        long previous = MEDIA_REPORTED.get();
        if (count > previous && MEDIA_REPORTED.compareAndSet(previous, count)) {
            Telemetry.event(context, "jio_dns_fallback", Telemetry.data(
                    "plane", "media",
                    "route", "secure_dns_after_system_failure",
                    "event_version_code", BuildConfig.VERSION_CODE));
        }
    }
}
