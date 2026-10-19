package in.ghartv.nova;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

/** System DNS first; bounded TLS-verified DoH fallback only for Jio-owned hostnames. */
final class JioSecureDns implements Dns {
    private final Dns system;
    private final Dns fallback;
    private final AtomicLong fallbackCount=new AtomicLong();
    private volatile long lastFallbackAt;

    JioSecureDns(){this(Dns.SYSTEM,createFallback());}
    JioSecureDns(Dns primary,Dns secondary){system=primary;fallback=secondary;}

    @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        UnknownHostException directFailure=null;
        try {
            List<InetAddress> direct=system.lookup(hostname);
            if(direct!=null&&!direct.isEmpty())return direct;
            directFailure=new UnknownHostException(hostname+" returned no system DNS addresses");
        } catch(UnknownHostException failure) {directFailure=failure;}
        if(!isJioHost(hostname)||fallback==null)throw directFailure;
        try {
            List<InetAddress> resolved=fallback.lookup(hostname);
            if(resolved==null||resolved.isEmpty())throw new UnknownHostException(hostname+" returned no secure DNS addresses");
            fallbackCount.incrementAndGet();lastFallbackAt=android.os.SystemClock.elapsedRealtime();return resolved;
        } catch(UnknownHostException fallbackFailure) {
            fallbackFailure.addSuppressed(directFailure);throw fallbackFailure;
        }
    }

    static boolean isJioHost(String hostname){
        if(hostname==null)return false;String host=hostname.trim().toLowerCase(Locale.ROOT);
        return host.equals("jio.com")||host.endsWith(".jio.com");
    }
    long fallbackCount(){return fallbackCount.get();}
    boolean usedRecently(){long at=lastFallbackAt;return at>0&&android.os.SystemClock.elapsedRealtime()-at<10L*60L*1000L;}

    private static Dns createFallback(){
        try {
            OkHttpClient bootstrap=new OkHttpClient.Builder().dns(Dns.SYSTEM).build();
            return new DnsOverHttps.Builder().client(bootstrap)
                    .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                    .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"),InetAddress.getByName("1.0.0.1"))
                    .includeIPv6(false).post(true).build();
        } catch(Exception ignored) {return null;}
    }
}
