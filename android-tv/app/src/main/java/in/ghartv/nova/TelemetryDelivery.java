package in.ghartv.nova;
import okhttp3.HttpUrl;
import org.json.JSONObject;

/** Current collector contract: no arbitrary destination, redirect or incomplete receipt. */
final class TelemetryDelivery {
    private static final String HOST="ghartv-telemetry.ghartv-47d9a0.workers.dev";
    static String endpoint(String value){
        try{HttpUrl u=HttpUrl.get(value);String p=u.encodedPath();
            if(!u.isHttps()||!HOST.equals(u.host())||u.port()!=443||!u.username().isEmpty()||!u.password().isEmpty()||u.query()!=null||u.fragment()!=null)return "";
            if(!p.equals("/")&&!p.equals("/v1/events"))return "";
            return u.newBuilder().encodedPath("/v1/events").build().toString();
        }catch(Exception e){return "";}
    }
    static boolean acknowledged(JSONObject ack,int count){
        Object accepted=ack.opt("accepted"),rejected=ack.opt("rejected");
        return Boolean.TRUE.equals(ack.opt("ok"))&&accepted instanceof Number&&rejected instanceof Number
            &&((Number)accepted).doubleValue()==count&&((Number)rejected).doubleValue()==0;
    }
    private TelemetryDelivery(){}
}
