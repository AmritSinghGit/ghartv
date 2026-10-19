package in.ghartv.nova;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

/** Public IPTV transport with secure DNS and bounded app-local Tor recovery. */
final class IptvNetwork {
    private static final SecureDns DIRECT_DNS = SecureDns.systemFirst(SecureDns::publicHostname);
    private static final SecureDns SECURE_DNS = SecureDns.secureOnly(SecureDns::publicHostname);
    private static final OkHttpClient CATALOGUE_DIRECT = catalogueClient(DIRECT_DNS);
    private static final OkHttpClient CATALOGUE_SECURE = catalogueClient(SECURE_DNS);
    private static final OkHttpClient MEDIA_DIRECT = mediaClient(DIRECT_DNS);
    private static final OkHttpClient MEDIA_SECURE = mediaClient(SECURE_DNS);
    private IptvNetwork() {}

    private static OkHttpClient catalogueClient(Dns dns) {
        return new OkHttpClient.Builder().dns(dns)
                .connectTimeout(7, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(28, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(false)
                .connectionPool(new ConnectionPool(4, 5, TimeUnit.MINUTES))
                .build();
    }

    private static OkHttpClient mediaClient(Dns dns) {
        return new OkHttpClient.Builder().dns(dns)
                .connectTimeout(9, TimeUnit.SECONDS)
                .readTimeout(24, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(false)
                .connectionPool(new ConnectionPool(8, 5, TimeUnit.MINUTES))
                .build();
    }

    static Call.Factory catalogue(Context context) {
        return new RouteFallbackCallFactory(context, CATALOGUE_DIRECT, CATALOGUE_SECURE,
                true, "iptv_catalogue", 70_000L);
    }
    static Call.Factory media(Context context) {
        return new RouteFallbackCallFactory(context, MEDIA_DIRECT, MEDIA_SECURE,
                true, "iptv_media", 70_000L);
    }
    static Call.Factory calls(Context context) { return catalogue(context); }

    static boolean recoveredRecently() {
        return DIRECT_DNS.usedRecently() || SECURE_DNS.usedRecently()
                || NetworkRouteState.usedRecently("iptv", NetworkRouteState.SECURE_DNS)
                || NetworkRouteState.usedRecently("iptv", NetworkRouteState.TOR);
    }
    static String routeLabel() {
        return NetworkRouteState.usedRecently("iptv", NetworkRouteState.TOR) ? "Tor recovery"
                : recoveredRecently() ? "secure DNS recovery" : "direct";
    }
}
