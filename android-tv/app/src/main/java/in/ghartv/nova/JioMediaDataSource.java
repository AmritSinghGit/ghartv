package in.ghartv.nova;

import android.content.Context;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.Collections;
import java.util.Map;

import okhttp3.Call;

/** Direct Jio media/DRM transport with bounded, DNS-only recovery; never Tor. */
@UnstableApi
final class JioMediaDataSource {
    private JioMediaDataSource() {}

    static OkHttpDataSource.Factory liveHttp(Context context, Map<String,String> headers) {
        return http(JioNetwork.media(context), headers);
    }

    static OkHttpDataSource.Factory previewHttp(Context context, Map<String,String> headers) {
        return http(JioNetwork.preview(context), headers);
    }

    static DefaultMediaSourceFactory liveSourceFactory(Context context, Map<String,String> headers) {
        return sourceFactory(liveHttp(context, headers));
    }

    static DefaultMediaSourceFactory previewSourceFactory(Context context, Map<String,String> headers) {
        return sourceFactory(previewHttp(context, headers));
    }

    private static OkHttpDataSource.Factory http(Call.Factory calls, Map<String,String> values) {
        Map<String,String> headers = values == null ? Collections.emptyMap() : values;
        String userAgent = headers.getOrDefault("User-Agent",
                "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME);
        return new OkHttpDataSource.Factory(calls)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(headers);
    }

    private static DefaultMediaSourceFactory sourceFactory(OkHttpDataSource.Factory http) {
        DefaultDrmSessionManagerProvider drm = new DefaultDrmSessionManagerProvider();
        // DRM provisioning/licence requests use the same direct client and DNS recovery.
        drm.setDrmHttpDataSourceFactory(http);
        return new DefaultMediaSourceFactory(http).setDrmSessionManagerProvider(drm);
    }
}
