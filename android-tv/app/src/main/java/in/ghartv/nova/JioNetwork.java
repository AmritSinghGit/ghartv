package in.ghartv.nova;

import android.content.Context;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

/** Shared Jio transport. Credentials remain in request headers; only DNS names use the fallback. */
final class JioNetwork {
    private static final JioSecureDns DNS=new JioSecureDns();
    private static final OkHttpClient API=new OkHttpClient.Builder().dns(DNS)
            .connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS)
            .writeTimeout(12,TimeUnit.SECONDS).callTimeout(16,TimeUnit.SECONDS)
            .retryOnConnectionFailure(true).connectionPool(new ConnectionPool(8,5,TimeUnit.MINUTES)).build();
    private static final OkHttpClient MEDIA=new OkHttpClient.Builder().dns(DNS)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS)
            .callTimeout(40,TimeUnit.SECONDS).retryOnConnectionFailure(true)
            .followRedirects(true).followSslRedirects(false).build();
    private static final OkHttpClient PREVIEW=new OkHttpClient.Builder().dns(DNS)
            .connectTimeout(5,TimeUnit.SECONDS).readTimeout(6,TimeUnit.SECONDS)
            .callTimeout(12,TimeUnit.SECONDS).retryOnConnectionFailure(true)
            .followRedirects(true).followSslRedirects(false).build();
    private static final AtomicLong REPORTED=new AtomicLong();
    private JioNetwork(){}
    static OkHttpClient api(){return API;}
    static OkHttpClient media(){return MEDIA;}
    static OkHttpClient preview(){return PREVIEW;}
    static Dns dns(){return DNS;}
    static boolean fallbackUsedRecently(){return DNS.usedRecently();}
    static void reportFallbackIfNew(Context context){
        long count=DNS.fallbackCount(),previous=REPORTED.get();
        if(count>previous&&REPORTED.compareAndSet(previous,count))Telemetry.event(context,"jio_dns_fallback",Telemetry.data("route","secure_dns_after_system_failure","event_version_code",BuildConfig.VERSION_CODE));
    }
}
