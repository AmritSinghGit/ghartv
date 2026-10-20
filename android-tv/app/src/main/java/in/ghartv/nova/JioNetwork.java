package in.ghartv.nova;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * Review38-compatible Jio transport. Jio API and media remain on Android's ordinary
 * direct TLS path; film Tor and public IPTV recovery stay separate.
 */
final class JioNetwork {
    private static final OkHttpClient API_DIRECT = new OkHttpClient.Builder()
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

    static Call.Factory api(Context context) { return API_DIRECT; }
    static Call.Factory media(Context context) { return MEDIA_DIRECT; }
    static Call.Factory preview(Context context) { return PREVIEW_DIRECT; }
    static OkHttpClient directApi() { return API_DIRECT; }
    static Dns dns() { return Dns.SYSTEM; }

    static boolean fallbackUsedRecently() { return false; }
    static boolean torUsedRecently() { return false; }
    static String recoveryLabel() { return "direct"; }
    static void reportFallbackIfNew(Context context) {
        // Direct Review38-compatible Jio transport has no alternate route to report.
    }
}
