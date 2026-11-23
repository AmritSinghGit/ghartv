package in.ghartv.nova;
import android.content.Context;
import android.util.Log;
import org.torproject.jni.TorService;
import net.freehaven.tor.control.TorControlConnection;
import java.util.Collections;

/** Tor country preference: applied to future exit circuits, never a location guarantee. */
final class TorCountryControl {
    private static TorService lastService;
    private static String lastCountry="";
    static synchronized boolean apply(Context app,TorService service){
        if(service==null)return false;
        String code=TorNetworkSettings.country(app);
        if(!TorNetworkSettings.valid(code))code="auto";
        if(service==lastService&&code.equals(lastCountry))return true;
        try{
            TorControlConnection control=service.getTorControlConnection();
            if(control==null)return false;
            if("auto".equals(code))control.resetConf(Collections.singleton("ExitNodes"));
            else control.setConf("ExitNodes","{"+code+"}");
            control.setConf("StrictNodes","0");
            // A currently playing stream is not interrupted here; changed
            // exit preferences take effect on future circuits.
            lastService=service;lastCountry=code;
            return true;
        }catch(java.io.IOException|RuntimeException error){
            Log.w("GharTVTor","Tor exit preference not applied; automatic routing retained");
            return false;
        }
    }
}
