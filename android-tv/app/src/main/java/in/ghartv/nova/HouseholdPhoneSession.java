package in.ghartv.nova;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.function.Consumer;

/**
 * One process-local, home-network-only phone remote shared across GharTV screens.
 * No cloud, no global input injection, no credentials or URLs in status messages.
 * When the app process dies or its 10-minute idle window closes, it disappears.
 */
final class HouseholdPhoneSession {
    static final class Link {
        final String url;
        final boolean emulator;
        Link(String u,boolean e){url=u;emulator=e;}
    }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile PhoneLinkServer server;
    private static volatile PhoneLinkServer.Sink foreground;
    private static volatile String link="";
    private static volatile boolean emulator;
    private static volatile boolean waiting;
    private static volatile boolean connected;
    private static volatile boolean offeredThisProcess;
    /** Explicit, volatile per-pairing consent; app-private summary lasts <=7 days. */
    private static volatile GharTvUsageDiagnostics usage;
    static boolean offeredThisProcess(){return offeredThisProcess;}
    static void markOffered(){offeredThisProcess=true;}
    /** Only for an OS interruption before any scan/skip, not a viewer dismissal. */
    static void interruptedOffer(){if(!connected())offeredThisProcess=false;}
    private static volatile Consumer<Link> pending;

    private static void record(String event) {
        GharTvUsageDiagnostics d=usage;
        if(d==null)return;
        // A disconnected phone must not leave passive TV-use recording on.
        // Capture the disconnect itself, but suppress all subsequent TV
        // actions until this SAME opted-in socket reconnects.
        if(!connected()&&!"PHONE_DISCONNECTED".equals(event))return;
        d.record(event);
    }
    static String usageSummary(Activity owner) {
        return GharTvUsageDiagnostics.get(owner).summary();
    }
    static void clearUsage(Activity owner) {
        GharTvUsageDiagnostics.get(owner).clear();
        sendUsage();
    }
    private static void sendUsage() {
        PhoneLinkServer s=server;
        GharTvUsageDiagnostics d=usage;
        if(s!=null&&d!=null&&s.isRunning()){
            // New pairing does not gain access to older occupants' reports.
            // Only a phone that explicitly opted in THIS pairing can view counts.
            s.usageReport(d.sessionAuthorized()?d.report().toString():
                "{\"schema\":\"ghartv.usage-summary.v1\",\"enabled\":false,\"retention_days\":7,\"uploaded\":false,\"total\":0,\"counts\":{},\"steps\":[]}");
        }
    }
    private static PhoneLinkServer.Sink route(){
        return new PhoneLinkServer.Sink(){
            @Override public void move(int x,int y){PhoneLinkServer.Sink s=foreground;if(s!=null)s.move(x,y);}
            @Override public void click(){PhoneLinkServer.Sink s=foreground;if(s!=null)s.click();}
            @Override public void down(){PhoneLinkServer.Sink s=foreground;if(s!=null)s.down();}
            @Override public void up(){PhoneLinkServer.Sink s=foreground;if(s!=null)s.up();}
            @Override public void key(int code){PhoneLinkServer.Sink s=foreground;if(s!=null)s.key(code);}
            @Override public void text(String value){record("WEB_TEXT_SUBMIT");PhoneLinkServer.Sink s=foreground;if(s!=null)s.text(value);}
            @Override public void scroll(int x,int y){PhoneLinkServer.Sink s=foreground;if(s!=null)s.scroll(x,y);}
            @Override public void action(String action){
                GharTvUsageDiagnostics d=usage;
                if("USAGE_ON".equals(action)){
                    if(d!=null){d.begin();if(HouseholdPhoneSession.connected())d.record("PHONE_CONNECTED");}
                    sendUsage();return;
                }
                if("USAGE_OFF".equals(action)){
                    if(d!=null)d.stop();
                    sendUsage();return;
                }
                if("USAGE_VIEW".equals(action)){sendUsage();return;}
                if("USAGE_CLEAR".equals(action)){
                    if(d!=null)d.clear();
                    sendUsage();return;
                }
                switch(action){
                    case "PLAY":record("PLAY_REQUEST");break;
                    case "PAUSE":record("PAUSE_REQUEST");break;
                    case "FULLSCREEN":record("FULLSCREEN_REQUEST");break;
                    case "SOURCES":record("SOURCE_LIST");break;
                    case "NEXT":record("TRY_NEXT");break;
                    case "SEARCH":record("MODE_SEARCH");break;
                    default:break;
                }
                PhoneLinkServer.Sink s=foreground;if(s!=null)s.action(action);
            }
            @Override public void search(String term){record("SEARCH_SUBMIT");PhoneLinkServer.Sink s=foreground;if(s!=null)s.search(term);}
            @Override public void submitPageText(String term){record("WEB_TEXT_SUBMIT");PhoneLinkServer.Sink s=foreground;if(s!=null)s.submitPageText(term);}
            @Override public void edit(String command){
                if("BACKSPACE".equals(command))record("TEXT_BACKSPACE");
                else if("CLEAR".equals(command))record("TEXT_CLEAR");
                PhoneLinkServer.Sink s=foreground;if(s!=null)s.edit(command);
            }
            @Override public void liveText(String value){record("LIVE_TYPING");PhoneLinkServer.Sink s=foreground;if(s!=null)s.liveText(value);}
            @Override public void chooseSource(int number){record("SOURCE_SELECT");PhoneLinkServer.Sink s=foreground;if(s!=null)s.chooseSource(number);}
            @Override public void seek(long ms){record("SEEK_REQUEST");PhoneLinkServer.Sink s=foreground;if(s!=null)s.seek(ms);}
            @Override public void metric(String event){record(event);}
            @Override public void connected(boolean yes){
                connected=yes;
                record(yes?"PHONE_CONNECTED":"PHONE_DISCONNECTED");
                PhoneLinkServer.Sink s=foreground;if(s!=null)s.connected(yes);
                if(yes)sendUsage();
            }
        };
    }
    static boolean connected(){return server!=null&&server.isRunning()&&server.isConnected();}
    static void mode(String mode){
        switch(mode){
            case "HOME":record("MODE_HOME");break;
            case "SEARCH":record("MODE_SEARCH");break;
            case "EDITOR":record("MODE_EDITOR");break;
            case "VIDEO":record("MODE_VIDEO");break;
            case "FULLSCREEN":record("MODE_FULL");break;
            default:break;
        }
        PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.mode(mode);
    }
    static void feedback(String code){
        switch(code){
            case "SEARCH_SENT":record("SEARCH_SENT");break;
            case "INVALID_QUERY":record("INVALID_QUERY");break;
            case "FIELD_BLOCKED":record("FIELD_BLOCKED");break;
            case "FIELD_REQUIRED":record("FIELD_REQUIRED");break;
            case "EDIT_APPLIED":record("EDIT_APPLIED");break;
            case "EDIT_FIELD_REQUIRED":record("EDIT_FIELD_REQUIRED");break;
            case "SEARCH_UNAVAILABLE":record("SEARCH_UNAVAILABLE");break;
            case "SOURCE_UNAVAILABLE":record("SOURCE_UNAVAILABLE");break;
            case "SOURCE_EXHAUSTED":record("SOURCE_EXHAUSTED");break;
            case "CONTROL_UNAVAILABLE":record("PLAYBACK_UNKNOWN");break;
            case "FULLSCREEN_UNAVAILABLE":record("FULLSCREEN_UNAVAILABLE");break;
            case "SEEK_UNAVAILABLE":record("SEEK_UNAVAILABLE");break;
            default:break;
        }
        PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.feedback(code);
    }
    static void sources(String json){
        if(json!=null&&json.startsWith("{\"choices\":"))record("SOURCE_LIST");
        PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.sources(json);
    }
    static void refreshSources(){PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.refreshSources();}
    static void playback(String json){
        if(json!=null&&json.length()<=250){
            try{
                String state=new org.json.JSONObject(json).optString("state");
                if("playing".equals(state))record("PLAYBACK_PLAYING");
                else if("paused".equals(state))record("PLAYBACK_PAUSED");
                else if("unknown".equals(state))record("PLAYBACK_UNKNOWN");
            }catch(org.json.JSONException ignored){}
        }
        PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.playback(json);
    }
    static void attach(PhoneLinkServer.Sink sink){
        foreground=sink;
        if(sink!=null)sink.connected(connected());
    }
    static void detach(PhoneLinkServer.Sink sink){if(foreground==sink)foreground=null;}
    static void disconnect(){
        GharTvUsageDiagnostics d=usage;if(d!=null){d.stop();d.endSession();}
        PhoneLinkServer existing=server;server=null;link="";waiting=false;
        if(existing!=null)existing.stop();
        connected=false;
    }
    static void open(Activity a,PhoneLinkServer.Sink sink,Consumer<Link> callback){
        usage=GharTvUsageDiagnostics.get(a);
        attach(sink);
        PhoneLinkServer old=server;
        if(old!=null&&old.isRunning()&&(old.isConnected()||old.canStillPair())&&!link.isEmpty()){
            callback.accept(new Link(link,emulator));return;
        }
        if(waiting){pending=callback;return;}
        if(old!=null){disconnect();attach(sink);}
        waiting=true;pending=callback;
        try {
            emulator=android.os.Build.FINGERPRINT.contains("generic")||
                android.os.Build.PRODUCT.contains("sdk")||
                android.os.Build.MODEL.contains("Emulator");
            InetAddress address=emulator?InetAddress.getByName("127.0.0.1"):homeWifi();
            if(address==null)throw new java.io.IOException("No private home network");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=a.getAssets().open("phone/index.html")){
                byte[] b=new byte[4096];int n;
                while((n=in.read(b))!=-1)bytes.write(b,0,n);
            }
            PhoneLinkServer fresh=new PhoneLinkServer(route(),bytes.toByteArray());
            int port=fresh.start(address,0);
            server=fresh;
            link="http://"+address.getHostAddress()+":"+port+"/?k="+fresh.pairingKey();
            if(!emulator){finish(new Link(link,false));return;}
            final String key=fresh.pairingKey();
            new Thread(()->{
                String resolved=null;
                try(Socket sock=new Socket()){
                    sock.connect(new InetSocketAddress("10.0.2.2",18672),2500);
                    sock.setSoTimeout(3000);
                    sock.getOutputStream().write(("PAIR "+port+"\n").getBytes(StandardCharsets.UTF_8));
                    BufferedReader in=new BufferedReader(new InputStreamReader(sock.getInputStream(),StandardCharsets.UTF_8));
                    String response=in.readLine();
                    if(response!=null&&response.startsWith("OK http://")){
                        String base=response.substring(3).trim();
                        if(base.matches("http://(?:10|172|192)\\.[0-9.]+:18673"))
                            resolved=base+"/?k="+key;
                    }
                }catch(Exception ignored){}
                final String value=resolved;
                MAIN.post(()->{
                    if(server!=fresh)return;
                    if(value!=null)link=value;
                    finish(value==null?null:new Link(value,true));
                });
            },"ghartv-phone-lan-emulator-bridge").start();
        }catch(Exception ignored){disconnect();finish(null);}
    }
    private static void finish(Link ready){
        waiting=false;Consumer<Link> cb=pending;pending=null;
        if(cb!=null)cb.accept(ready);
    }
    private static InetAddress homeWifi(){
        try{
            Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
            InetAddress fallback=null;
            while(all!=null&&all.hasMoreElements()){
                NetworkInterface net=all.nextElement();
                if(!net.isUp()||net.isLoopback())continue;
                Enumeration<InetAddress> addresses=net.getInetAddresses();
                while(addresses.hasMoreElements()){
                    InetAddress a=addresses.nextElement();
                    if(!(a instanceof Inet4Address)||a.isLoopbackAddress()||!a.isSiteLocalAddress())continue;
                    if(net.getName().startsWith("wlan")||net.getName().startsWith("eth"))return a;
                    if(fallback==null)fallback=a;
                }
            }
            return fallback;
        }catch(Exception ignored){return null;}
    }
    private HouseholdPhoneSession(){}
}
