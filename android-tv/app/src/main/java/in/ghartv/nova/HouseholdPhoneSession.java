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
    static boolean offeredThisProcess(){return offeredThisProcess;}
    static void markOffered(){offeredThisProcess=true;}
    /** Only for an OS interruption before any scan/skip, not a viewer dismissal. */
    static void interruptedOffer(){if(!connected())offeredThisProcess=false;}
    private static volatile Consumer<Link> pending;

    private static PhoneLinkServer.Sink route(){
        return new PhoneLinkServer.Sink(){
            @Override public void move(int x,int y){PhoneLinkServer.Sink s=foreground;if(s!=null)s.move(x,y);}
            @Override public void click(){PhoneLinkServer.Sink s=foreground;if(s!=null)s.click();}
            @Override public void down(){PhoneLinkServer.Sink s=foreground;if(s!=null)s.down();}
            @Override public void up(){PhoneLinkServer.Sink s=foreground;if(s!=null)s.up();}
            @Override public void key(int code){PhoneLinkServer.Sink s=foreground;if(s!=null)s.key(code);}
            @Override public void text(String value){PhoneLinkServer.Sink s=foreground;if(s!=null)s.text(value);}
            @Override public void scroll(int x,int y){PhoneLinkServer.Sink s=foreground;if(s!=null)s.scroll(x,y);}
            @Override public void action(String action){PhoneLinkServer.Sink s=foreground;if(s!=null)s.action(action);}
            @Override public void search(String term){PhoneLinkServer.Sink s=foreground;if(s!=null)s.search(term);}
            @Override public void submitPageText(String term){PhoneLinkServer.Sink s=foreground;if(s!=null)s.submitPageText(term);}
            @Override public void edit(String command){PhoneLinkServer.Sink s=foreground;if(s!=null)s.edit(command);}
            @Override public void liveText(String value){PhoneLinkServer.Sink s=foreground;if(s!=null)s.liveText(value);}
            @Override public void chooseSource(int number){PhoneLinkServer.Sink s=foreground;if(s!=null)s.chooseSource(number);}
            @Override public void connected(boolean yes){
                connected=yes;
                PhoneLinkServer.Sink s=foreground;if(s!=null)s.connected(yes);
            }
        };
    }
    static boolean connected(){return server!=null&&server.isRunning()&&server.isConnected();}
    static void mode(String mode){PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.mode(mode);}
    static void feedback(String code){PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.feedback(code);}
    static void sources(String json){PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.sources(json);}
    static void playback(String json){PhoneLinkServer s=server;if(s!=null&&s.isRunning())s.playback(json);}
    static void attach(PhoneLinkServer.Sink sink){
        foreground=sink;
        if(sink!=null)sink.connected(connected());
    }
    static void detach(PhoneLinkServer.Sink sink){if(foreground==sink)foreground=null;}
    static void disconnect(){
        PhoneLinkServer existing=server;server=null;link="";waiting=false;
        if(existing!=null)existing.stop();
        connected=false;
    }
    static void open(Activity a,PhoneLinkServer.Sink sink,Consumer<Link> callback){
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
