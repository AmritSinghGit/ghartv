import in.ghartv.nova.UpdatePolicy;
public class UpdatePolicySmoke {
 public static void main(String[] args) throws Exception {
  if(UpdatePolicy.compare(22,17)!=UpdatePolicy.State.AHEAD_OF_PUBLIC)throw new AssertionError();
  if(UpdatePolicy.compare(17,17)!=UpdatePolicy.State.CURRENT_PUBLIC)throw new AssertionError();
  if(UpdatePolicy.compare(17,23)!=UpdatePolicy.State.AVAILABLE)throw new AssertionError();
  String good="https://github.com/AmritSinghGit/ghartv/releases/download/v0.5.4-rc8/GharTV.apk";
  if(!UpdatePolicy.allowedApk(good))throw new AssertionError();
  for(String v:new String[]{good+"?token=x",good.replace("github.com","github.com.evil.invalid"),good.replace("https:","http:"),good.replace("/ghartv/","/other/"),good.replace("GharTV.apk","../GharTV.apk"),good+"#x"})if(UpdatePolicy.allowedApk(v))throw new AssertionError(v);
  if(!UpdatePolicy.failureCode(new java.net.UnknownHostException()).equals("DNS_UNAVAILABLE"))throw new AssertionError();
  if(!UpdatePolicy.failureCode(new java.net.SocketTimeoutException()).equals("REQUEST_TIMED_OUT"))throw new AssertionError();
  System.out.println("UPDATE_POLICY_JVM_STATES_URL_BOUNDARIES_AND_FAILURE_CLASSIFICATION=PASS");
 }
}
