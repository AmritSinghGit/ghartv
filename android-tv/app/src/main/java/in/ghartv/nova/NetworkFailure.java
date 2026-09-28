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
    public static String tlsReason(Throwable error) {
        boolean tls=false,chain=false;
        for(int i=0;error!=null&&i<12;i++,error=error.getCause()) {
            if(error instanceof java.security.cert.CertificateExpiredException)return "CERTIFICATE_EXPIRED";
            if(error instanceof java.security.cert.CertificateNotYetValidException)return "CERTIFICATE_NOT_YET_VALID";
            if(error instanceof java.security.cert.CertPathValidatorException)chain=true;
            if(error instanceof javax.net.ssl.SSLException)tls=true;
        }
        return chain?"CERTIFICATE_CHAIN_REJECTED":tls?"TLS_VALIDATION_FAILED":"NO_CERTIFICATE_CAUSE_IDENTIFIED";
    }
    public static String rootType(Throwable error) {
        if(error==null)return "Unknown"; int i=0;
        while(error.getCause()!=null&&error.getCause()!=error&&i++<12)error=error.getCause();
        return error.getClass().getSimpleName();
    }
}
