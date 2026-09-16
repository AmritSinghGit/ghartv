import in.ghartv.nova.NetworkFailure;
public class NetworkRulesSmoke {public static void main(String[] args){
 if(!NetworkFailure.classify(new RuntimeException(new java.net.UnknownHostException())).equals("DNS_UNAVAILABLE"))throw new AssertionError();
 if(!NetworkFailure.classify(new javax.net.ssl.SSLHandshakeException("clock")).equals("TLS_FAILED"))throw new AssertionError();
 if(!NetworkFailure.classify(new RuntimeException(new java.net.SocketTimeoutException())).equals("TIMED_OUT"))throw new AssertionError();
 if(!NetworkFailure.rootType(new RuntimeException(new java.net.UnknownHostException())).equals("UnknownHostException"))throw new AssertionError();
 System.out.println("NETWORK_ROOT_CLASSIFICATION=PASS");}}
