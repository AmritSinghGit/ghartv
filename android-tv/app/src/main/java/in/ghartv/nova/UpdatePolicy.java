package in.ghartv.nova;

import java.net.URI;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;

/** UI state and trust rules independent of transport; a failed request is never 'up to date'. */
public final class UpdatePolicy {
    private UpdatePolicy() {}
    public enum State { AVAILABLE, CURRENT_PUBLIC, AHEAD_OF_PUBLIC }
    public static State compare(int installed, int offered) {
        if (installed <= 0 || offered <= 0) throw new IllegalArgumentException("Invalid version code");
        return offered > installed ? State.AVAILABLE : offered == installed ? State.CURRENT_PUBLIC : State.AHEAD_OF_PUBLIC;
    }
    public static boolean allowedApk(String value) {
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && uri.getRawPath().matches("/AmritSinghGit/ghartv/releases/download/v[0-9A-Za-z._-]+/[0-9A-Za-z._-]+\\.apk");
        } catch (Exception e) { return false; }
    }
    public static String failureCode(Throwable error) {
        for (int i=0; error!=null && i<8; i++,error=error.getCause()) {
            if (error instanceof UnknownHostException) return "DNS_UNAVAILABLE";
            if (error instanceof SocketTimeoutException || error instanceof java.io.InterruptedIOException) return "REQUEST_TIMED_OUT";
            if (error instanceof SecurityException) return "MANIFEST_REJECTED";
        }
        return "UPDATE_SERVICE_UNAVAILABLE";
    }
}
