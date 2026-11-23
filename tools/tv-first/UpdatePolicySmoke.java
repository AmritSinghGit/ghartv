import in.ghartv.nova.UpdatePolicy;
public final class UpdatePolicySmoke {
    private static int checks;
    private static void ok(boolean value){checks++;if(!value)throw new AssertionError("UpdatePolicy check "+checks);}
    public static void main(String[] args){
        String pub="https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.1/GharTV.apk", base="http://10.0.2.2:8099";
        ok(UpdatePolicy.allowedApk(pub,""));                       // release build: public address allowed
        ok(UpdatePolicy.allowedApk(pub,null));
        ok(!UpdatePolicy.allowedApk(base+"/b.apk",""));             // release build: test address refused
        ok(UpdatePolicy.allowedApk(base+"/b-67.apk",base));         // test build: apk under the test base allowed
        ok(!UpdatePolicy.allowedApk(pub,base));                     // test build: public address not used
        ok(!UpdatePolicy.allowedApk("http://10.0.2.2:8099/x/../b.apk",base));
        ok(!UpdatePolicy.allowedApk("http://10.0.2.2:8099/dir/b.apk",base));
        ok(!UpdatePolicy.allowedApk("http://10.0.2.2:8099.evil/b.apk",base));
        ok(!UpdatePolicy.allowedApk("http://10.0.2.2:8099/b.apk?x=1",base));
        ok(!UpdatePolicy.allowedApk("http://10.0.2.3:8099/b.apk",base));
        ok(UpdatePolicy.compare(66,67)==UpdatePolicy.State.AVAILABLE);
        ok(UpdatePolicy.compare(67,67)==UpdatePolicy.State.CURRENT_PUBLIC);
        ok(UpdatePolicy.compare(68,67)==UpdatePolicy.State.AHEAD_OF_PUBLIC);
        System.out.println("UpdatePolicySmoke ok "+checks);
    }
}
