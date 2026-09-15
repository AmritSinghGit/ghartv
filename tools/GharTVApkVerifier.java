import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/** Cryptographic verifier via the installed Android library, not a CLI text regex. */
class GharTVApkVerifier {
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();
        for(byte b:bytes)out.append(String.format("%02x",b&255));
        return out.toString();
    }
    public static void main(String[] args) {
        if(args.length!=1){System.out.println("{\"ok\":false,\"error\":\"ARGUMENT_REQUIRED\"}");System.exit(2);}
        try {
            ApkVerifier.Result result=new ApkVerifier.Builder(new File(args[0])).build().verify();
            if(!result.isVerified()){
                System.out.println("{\"ok\":false,\"error\":\"APK_SIGNATURE_INVALID\",\"error_count\":"+result.getErrors().size()+"}");
                System.exit(3);return;
            }
            List<String> hashes=new ArrayList<>();
            for(X509Certificate cert:result.getSignerCertificates())
                hashes.add("\""+hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))+"\"");
            if(hashes.isEmpty()){
                System.out.println("{\"ok\":false,\"error\":\"VERIFIED_WITHOUT_SIGNER_CERTIFICATE\"}");System.exit(4);return;
            }
            System.out.println("{\"ok\":true,\"engine\":\"android-apksig-library\",\"certificate_sha256\":["+String.join(",",hashes)+"],\"v1\":"+result.isVerifiedUsingV1Scheme()+",\"v2\":"+result.isVerifiedUsingV2Scheme()+",\"v3\":"+result.isVerifiedUsingV3Scheme()+"}");
        } catch(Exception | LinkageError error) {
            // No DN, stream/account data or raw exception messages enter the handoff.
            System.out.println("{\"ok\":false,\"error\":\"APK_VERIFIER_EXCEPTION\",\"class\":\""+error.getClass().getSimpleName()+"\"}");
            System.exit(5);
        }
    }
}
