package in.ghartv.nova;
/** Root-cause classification independent of Media3's wrapper class and message text. */
public final class NetworkFailure {
    private NetworkFailure() {}
    public static String classify(Throwable error) {
        for(int i=0; error!=null && i<12; i++,error=error.getCause()) {
            if(error instanceof java.net.UnknownHostException) return "DNS_UNAVAILABLE";
            if(error instanceof javax.net.ssl.SSLException) return "TLS_FAILED";
            if(error instanceof java.net.SocketTimeoutException || error instanceof java.io.InterruptedIOException) return "TIMED_OUT";
            if(error instanceof java.net.ConnectException || error instanceof java.net.NoRouteToHostException) return "CONNECTION_FAILED";
        }
        return "UNCLASSIFIED";
    }
    public static String rootType(Throwable error) {
        if(error==null)return "Unknown"; int i=0;
        while(error.getCause()!=null&&error.getCause()!=error&&i++<12)error=error.getCause();
        return error.getClass().getSimpleName();
    }
}
