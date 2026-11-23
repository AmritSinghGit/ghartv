import in.ghartv.nova.PhoneLinkServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public final class PhoneLinkSmoke {
    private static int checks;
    private static void ok(boolean v){checks++;if(!v)throw new AssertionError("PhoneLink check "+checks);}
    static final List<String> got=Collections.synchronizedList(new ArrayList<>());
    static final CountDownLatch moved=new CountDownLatch(1);
    static PhoneLinkServer.Sink sink=new PhoneLinkServer.Sink(){
        public void move(int dx,int dy){got.add("M "+dx+" "+dy);}
        public void click(){got.add("C");} public void down(){got.add("D");} public void up(){got.add("U");}
        public void key(int k){got.add("K "+k);} public void text(String t){got.add("T "+t);}
        public void scroll(int x,int y){got.add("S "+x+" "+y);}
        public void action(String a){got.add("A "+a);}
        public void search(String term){got.add("Q "+term);}
        public void submitPageText(String term){got.add("E "+term);}
        public void connected(boolean c){got.add(c?"on":"off");}};
    static String hp,sess;
    static String http(int port,String target,String extra) throws Exception{
        try(Socket s=new Socket("127.0.0.1",port)){
            s.getOutputStream().write(("GET "+target+" HTTP/1.1\r\nHost: "+hp+"\r\n"+extra+"\r\n").getBytes());
            BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l).append('\n');return b.toString();}}
    static void frame(OutputStream o,String text)throws IOException{
        byte[] d=text.getBytes(StandardCharsets.UTF_8);byte[] m=new byte[4];new SecureRandom().nextBytes(m);
        ByteArrayOutputStream f=new ByteArrayOutputStream();f.write(0x81);
        if(d.length<126)f.write(0x80|d.length);else{f.write(0x80|126);f.write(d.length>>8);f.write(d.length&255);}
        f.write(m);for(int i=0;i<d.length;i++)f.write(d[i]^m[i&3]);o.write(f.toByteArray());o.flush();}
    static String readFrame(InputStream in)throws IOException{int b0=in.read(),b1=in.read();int n=b1&127;byte[] d=new byte[n];int r=0;while(r<n)r+=in.read(d,r,n-r);return new String(d,StandardCharsets.UTF_8);}
    static String http2(int port,String target,String headers) throws Exception{
        try(Socket s=new Socket("127.0.0.1",port)){
            s.getOutputStream().write(("GET "+target+" HTTP/1.1\r\n"+headers+"\r\n").getBytes());
            BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l).append('\n');return b.toString();}}
    public static void main(String[] a)throws Exception{
        byte[] page="<html>page</html>".getBytes();
        PhoneLinkServer s=new PhoneLinkServer(sink,page);
        int port=s.start(InetAddress.getByName("127.0.0.1"),0);String k=s.pairingKey();hp="127.0.0.1:"+port;
        ok(k.length()==32);
        ok(http(port,"/","").startsWith("HTTP/1.1 403"));                       // no key
        ok(http(port,"/?k=wrong","").startsWith("HTTP/1.1 403"));               // wrong key
        ok(http(port,"/?k="+k,"").contains("<html>page</html>"));               // page with key
        ok(http(port,"/ws?k="+k,"").startsWith("HTTP/1.1 403"));               // ws path without upgrade: same answer as everything else
        ok(PhoneLinkServer.homeAddress(InetAddress.getByName("192.168.1.20"))&&PhoneLinkServer.homeAddress(InetAddress.getByName("10.0.0.5"))&&PhoneLinkServer.homeAddress(InetAddress.getByName("172.20.1.1")));
        ok(!PhoneLinkServer.homeAddress(InetAddress.getByName("8.8.8.8"))&&!PhoneLinkServer.homeAddress(InetAddress.getByName("172.32.0.1")));
        try(Socket c=new Socket("127.0.0.1",port)){
            OutputStream o=c.getOutputStream();InputStream in=c.getInputStream();
            o.write(("GET /ws?k="+k+" HTTP/1.1\r\nHost: "+hp+"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").getBytes());o.flush();
            StringBuilder h=new StringBuilder();int last=0;int ch;while((ch=in.read())>=0){h.append((char)ch);last=(last<<8)|ch;if(last==0x0d0a0d0a)break;}
            ok(h.toString().startsWith("HTTP/1.1 101")&&h.toString().contains("s3pPLMBiTxaQ9kYGzzhZRbK+xOo="));   // RFC 6455 sample accept value
            String hello=readFrame(in);ok(hello.startsWith("S ")&&hello.length()==34);sess=hello.substring(2);
            ok(readFrame(in).equals("V BROWSE"));
            s.mode("FULLSCREEN");ok(readFrame(in).equals("V FULLSCREEN"));
            s.mode("SEARCH");ok(readFrame(in).equals("V SEARCH"));
            s.feedback("SEARCH_SENT");ok(readFrame(in).equals("R SEARCH_SENT"));
            s.feedback("FIELD_REQUIRED");ok(readFrame(in).equals("R FIELD_REQUIRED"));
            s.feedback("PASSWORD"); // unsupported feedback never sent
            s.mode("PASSWORD");  // unsupported mode ignored, no frame
            frame(o,"S 0 350");frame(o,"S 9000 -9000");frame(o,"A PLAY");
            frame(o,"A NEXT");frame(o,"A FULLSCREEN");frame(o,"A SEARCH");
            frame(o,"A GO");frame(o,"A MALWARE");frame(o,"A OPEN_URL");
            frame(o,"Q Planet Earth");
            frame(o,"E my documentary");
            frame(o,"Q "); // empty request is ignored by app validation
            frame(o,"M 5 -7");frame(o,"C");frame(o,"K 23");frame(o,"K 26");frame(o,"K 224");frame(o,"K 4");frame(o,"T hello हि");frame(o,"M 9999 -9999");frame(o,"K junk");
            long t0=System.nanoTime();frame(o,"P");ok(readFrame(in).equals("P"));long ping=(System.nanoTime()-t0)/1000;
            // 60 Hz pointer stream for 2 s then a ping
            for(int i=0;i<120;i++){frame(o,"M 1 1");Thread.sleep(16);}
            long t1=System.nanoTime();frame(o,"P");ok(readFrame(in).equals("P"));long lag=(System.nanoTime()-t1)/1000;
            ok(got.contains("M 5 -7")&&got.contains("C")&&got.contains("K 23")&&got.contains("T hello हि"));
            ok(got.contains("Q Planet Earth")&&got.contains("E my documentary"));
            ok(got.contains("M 400 -400"));                                          // clamped
            ok(got.contains("S 0 350")&&got.contains("S 400 -400"));
            ok(got.contains("A PLAY")&&got.contains("A NEXT")&&got.contains("A FULLSCREEN")&&
               got.contains("A SEARCH")&&got.contains("A GO"));
            ok(!got.contains("A MALWARE")&&!got.contains("A OPEN_URL"));
            ok(!got.contains("K junk")&&!got.contains("K 26")&&!got.contains("K 224")&&got.contains("K 4"));   // power/wake refused, back allowed                                             // bad number ignored
            long moves=got.stream().filter(x->x.equals("M 1 1")).count();ok(moves==120);
            ok(lag<20000);                                                           // ping after a stream under 20 ms
            System.out.println("ping_us="+ping+" ping_after_stream_us="+lag);
            // a second connection from the same address may claim; wrong key never
            ok(http(port,"/ws?k=bad","Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: abc\r\n").startsWith("HTTP/1.1 403"));
            // the key is dead after the first upgrade, even while the first phone is connected: page and a second socket are refused
            ok(http(port,"/?k="+k,"").startsWith("HTTP/1.1 403"));
            ok(http(port,"/ws?k="+k,"Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n").startsWith("HTTP/1.1 403"));
            ok(http(port,"/ws?s="+sess,"Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n").startsWith("HTTP/1.1 403"));   // still connected: no second socket
        }
        Thread.sleep(200);
        // reconnect inside 60 s from the same address with the session value works; with a wrong value, not
        try(Socket c=new Socket("127.0.0.1",port)){
            c.getOutputStream().write(("GET /ws?s=nope HTTP/1.1\r\nHost: "+hp+"\r\nUpgrade: websocket\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n").getBytes());
            ok(new String(c.getInputStream().readNBytes(12)).startsWith("HTTP/1.1 403"));}
        try(Socket c=new Socket("127.0.0.1",port)){
            c.getOutputStream().write(("GET /ws?s="+sess+" HTTP/1.1\r\nHost: "+hp+"\r\nUpgrade: websocket\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n").getBytes());
            ok(new String(c.getInputStream().readNBytes(12)).startsWith("HTTP/1.1 101"));}
        Thread.sleep(200);
        // other site as Origin, or another Host, is refused
        PhoneLinkServer s2=new PhoneLinkServer(sink,page);int p2=s2.start(InetAddress.getByName("127.0.0.1"),0);String k2=s2.pairingKey();String hp2="127.0.0.1:"+p2;
        ok(http2(p2,"/?k="+k2,"Host: "+hp2+"\r\nOrigin: http://evil.example\r\n").startsWith("HTTP/1.1 403"));
        ok(http2(p2,"/?k="+k2,"Host: evil.example\r\n").startsWith("HTTP/1.1 403"));
        ok(http2(p2,"/?k="+k2,"Host: "+hp2+"\r\nOrigin: http://"+hp2+"\r\n").startsWith("HTTP/1.1 200"));
        s2.stop();
        boolean refused=false;try{new PhoneLinkServer(sink,page).start(InetAddress.getByName("0.0.0.0"),0);}catch(IOException e){refused=true;}ok(refused);   // never all interfaces
        refused=false;try{new PhoneLinkServer(sink,page).start(InetAddress.getByName("100.100.1.1"),0);}catch(IOException e){refused=true;}ok(refused);   // not a tailnet address
        s.stop();
        ok(got.contains("on"));
        System.out.println("PHONE_LINK="+checks+"_PASS");
    }
}
