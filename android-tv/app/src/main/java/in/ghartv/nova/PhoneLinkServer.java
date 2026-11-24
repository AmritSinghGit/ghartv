package in.ghartv.nova;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phone control server (owner 18 Nov 13:50): the TV serves one small page and one WebSocket to a phone browser on the
 * home network. The page URL carries a one-time pairing key shown as a QR code. Plain Java sockets, no library.
 * Messages (text frames, first character is the operation): M dx dy | C | D | U | K keycode | T text | P (ping, echoed).
 * Typed text and key codes are never logged here.
 */
public final class PhoneLinkServer {
    public interface Sink {
        void move(int dx, int dy);
        void click();
        void down();
        void up();
        void key(int keyCode);
        void text(String text);
        void connected(boolean connected);
        default void scroll(int dx,int dy) {}
        default void action(String action) {}
        default void search(String term) {}
        default void submitPageText(String term) {}
        /** Explicit editing of the currently selected safe TV field. */
        default void edit(String command) {}
        /** Bounded, complete current phone draft; empty string clears field. */
        default void liveText(String value) {}
        /** Source number from the actually observed provider controls. */
        default void chooseSource(int number) {}
        /** Absolute seek position in milliseconds, validated against observed media. */
        default void seek(long positionMs) {}
    }

    static final int MAX_FRAME = 4096;
    static final long KEY_LIFE_MS = 10 * 60_000L;
    static final long IDLE_MS = 10 * 60_000L;
    private static final int MAX_CONNECTIONS = 4;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Sink sink;
    private final byte[] page;
    private final String key;
    private final long createdAt;
    private final AtomicInteger open = new AtomicInteger();
    private volatile ServerSocket server;
    /** CSO R2007 MUST 1: only these key codes can be sent from a phone (d-pad, enter, back, home, delete, media, volume, channel). */
    static final Set<Integer> ALLOWED_KEYS = new HashSet<>(Arrays.asList(19, 20, 21, 22, 23, 66, 4, 3, 67, 112, 85, 126, 127, 90, 89, 87, 88, 24, 25, 164, 166, 167));
    static final long RECONNECT_MS = 60_000L;
    private volatile String claimedBy;
    private volatile boolean keyUsed;
    private volatile long dropped;
    private volatile String hostPort = "";
    private volatile boolean stopped;
    private volatile OutputStream activeOutput;
    // Android StrictMode forbids socket writes on the main thread. All outbound
    // screen state uses this bounded single socket writer, not the UI looper.
    private final java.util.concurrent.ExecutorService outbound=
        java.util.concurrent.Executors.newSingleThreadExecutor(r->{
            Thread t=new Thread(r,"ghartv-phone-network-writer");t.setDaemon(true);return t;
        });
    private volatile String lastMode="BROWSE";
    private volatile String lastSources="{\"choices\":[]}";
    private volatile String lastPlayback="{\"state\":\"unknown\",\"fullscreen\":false}";
    public void playback(String json){
        if(json==null||json.length()>250||!json.startsWith("{\"state\":"))return;
        if(json.equals(lastPlayback))return;
        lastPlayback=json;
        sendAsync("H "+json);
    }
    /** Explicit phone Refresh must receive the current list even if unchanged. */
    public void refreshSources(){sendAsync("O "+lastSources);}
    /** Only source labels/numbers/badges, no URLs, iframe state or credentials. */
    public void sources(String json){
        if(json==null||json.length()>3500||!json.startsWith("{\"choices\":"))return;
        if(json.equals(lastSources))return;
        lastSources=json;
        sendAsync("O "+json);
    }
    private void sendAsync(String msg){
        final OutputStream out=activeOutput;
        if(out==null||outbound.isShutdown())return;
        try{outbound.execute(()->{
            if(activeOutput!=out||stopped)return;
            try{writeFrame(out,1,msg.getBytes(StandardCharsets.UTF_8));}
            catch(IOException ignored){}
        });}catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    /** No text/URL/secrets in protocol feedback; codes are strictly bounded. */
    public void feedback(String code){
        if(!("SEARCH_SENT".equals(code)||"INVALID_QUERY".equals(code)||
             "WEB_TEXT_SENT".equals(code)||"FIELD_REQUIRED".equals(code)||
             "FIELD_BLOCKED".equals(code)||"SEARCH_UNAVAILABLE".equals(code)||
             "EDIT_APPLIED".equals(code)||"EDIT_FIELD_REQUIRED".equals(code)||
             "CONTROL_UNAVAILABLE".equals(code)||"SEEK_UNAVAILABLE".equals(code)||
             "SEEK_REQUESTED".equals(code)||"PLAY_REQUESTED".equals(code)||
             "PAUSE_REQUESTED".equals(code)||
             "FULLSCREEN_UNAVAILABLE".equals(code)||
             "FULLSCREEN_REQUESTED".equals(code)||
             "SOURCE_UNAVAILABLE".equals(code)||
             "SOURCE_EXHAUSTED".equals(code)))return;
        final OutputStream out=activeOutput;
        if(out!=null&&!outbound.isShutdown()){
            try{outbound.execute(()->{
                if(activeOutput!=out||stopped)return;
                try{writeFrame(out,1,("R "+code).getBytes(StandardCharsets.UTF_8));}
                catch(IOException ignored){}
            });}catch(java.util.concurrent.RejectedExecutionException ignored){}
        }
    }
    public boolean isRunning(){return !stopped&&server!=null&&!server.isClosed();}
    /** Coarse screen state only; never URLs, text or playback identifiers. */
    public void mode(String mode){
        if(!("HOME".equals(mode)||"BROWSE".equals(mode)||"SEARCH".equals(mode)||"EDITOR".equals(mode)||
             "VIDEO".equals(mode)||"STARTING".equals(mode)||"FULLSCREEN".equals(mode)))return;
        if(mode.equals(lastMode))return;
        lastMode=mode;
        final OutputStream out=activeOutput;
        if(out!=null&&!outbound.isShutdown()){
            try{outbound.execute(()->{
                if(activeOutput!=out||stopped)return;
                try{writeFrame(out,1,("V "+mode).getBytes(StandardCharsets.UTF_8));}
                catch(IOException ignored){}
            });}catch(java.util.concurrent.RejectedExecutionException ignored){}
        }
    }
    private volatile long lastActivity;

    public PhoneLinkServer(Sink sink, byte[] page) {
        this.sink = sink;
        this.page = page;
        this.key = newKey();
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = createdAt;
    }

    public String pairingKey() { return key; }
    /** Harmless state booleans only; never export session IDs or pairing secrets. */
    public boolean isConnected() { return connected && !stopped; }
    public boolean canStillPair() {
        return !stopped && !keyUsed && System.currentTimeMillis()-createdAt < KEY_LIFE_MS;
    }

    public static String newKey() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }

    /** Starts listening on the given local address (the TV's Wi-Fi address); port 0 picks a free one. Returns the port. */
    public int start(InetAddress bind, int port) throws IOException {
        if (!homeAddress(bind)) throw new IOException("not a home address");
        server = new ServerSocket(port, 8, bind);
        hostPort = bind.getHostAddress() + ":" + server.getLocalPort();
        Thread accept = new Thread(this::acceptLoop, "phone-link-accept");
        accept.setDaemon(true);
        accept.start();
        Thread idle = new Thread(this::idleLoop, "phone-link-idle");
        idle.setDaemon(true);
        idle.start();
        return server.getLocalPort();
    }

    public void stop() {
        stopped = true;activeOutput=null;outbound.shutdownNow();
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        sink.connected(false);
    }

    /** Only the home network: loopback, private (10/8, 172.16/12, 192.168/16) and link-local addresses. */
    public static boolean homeAddress(InetAddress a) {
        return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress();
    }

    private void idleLoop() {
        while (!stopped) {
            try { Thread.sleep(5_000); } catch (InterruptedException e) { return; }
            if (System.currentTimeMillis() - lastActivity > IDLE_MS) { stop(); return; }
            synchronized (this) { if (keyUsed && !connected && dropped > 0 && System.currentTimeMillis() - dropped > RECONNECT_MS) { claimedBy = null; sess = ""; } }
        }
    }

    private void acceptLoop() {
        while (!stopped) {
            try {
                Socket s = server.accept();
                if (!homeAddress(s.getInetAddress()) || open.get() >= MAX_CONNECTIONS) { s.close(); continue; }
                open.incrementAndGet();
                Thread t = new Thread(() -> { try { handle(s); } finally { open.decrementAndGet(); try { s.close(); } catch (IOException ignored) {} } }, "phone-link-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (stopped) return;
            }
        }
    }

    private boolean keyOk(String given) {
        if (given == null || System.currentTimeMillis() - createdAt > KEY_LIFE_MS) return false;
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }

    private static String query(String target, String name) {
        int q = target.indexOf('?');
        if (q < 0) return null;
        for (String part : target.substring(q + 1).split("&")) {
            int e = part.indexOf('=');
            if (e > 0 && part.substring(0, e).equals(name)) return part.substring(e + 1);
        }
        return null;
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(15_000);
            s.setTcpNoDelay(true);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 2 || !req[0].equals("GET")) { refuse(out); return; }
            String target = req[1];
            String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
            String who = s.getInetAddress().getHostAddress();
            String wsKey = null, host = null, origin = null;
            for (String l : lines) {
                String low = l.toLowerCase(Locale.ROOT);
                if (low.startsWith("sec-websocket-key:")) wsKey = l.substring(18).trim();
                else if (low.startsWith("host:")) host = l.substring(5).trim();
                else if (low.startsWith("origin:")) origin = l.substring(7).trim();
            }
            // MUST 5: the Host must be this TV's own address and port; an Origin, if sent, must be the same page.
            if (host == null || !host.equals(hostPort) || (origin != null && !origin.equals("http://" + hostPort))) { refuse(out); return; }
            if (!sessionOrKey(query(target, "k"), who, path)) { refuse(out); return; }
            lastActivity = System.currentTimeMillis();
            if (path.equals("/") && wsKey == null) { reply(out, 200, "text/html; charset=utf-8", page); return; }
            if (path.equals("/ws") && wsKey != null) {
                if (wsKey.length() != 24) { refuse(out); return; }
                synchronized (this) {
                    boolean fresh = !keyUsed && keyOk(query(target, "k"));
                    boolean again = keyUsed && who.equals(claimedBy) && !connected && System.currentTimeMillis() - dropped <= RECONNECT_MS && sess.equals(query(target, "s"));
                    if (!fresh && !again) { refuse(out); return; }
                    if (fresh) { keyUsed = true; claimedBy = who; sess = newKey(); }
                    connected = true;
                }
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((wsKey + GUID).getBytes(StandardCharsets.UTF_8)));
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                s.setSoTimeout(0);
                activeOutput=out;
                // Always send the reconnect identity before notifying the
                // foreground Activity. That callback can immediately queue a
                // new mode; otherwise V could race ahead of S on first pair.
                writeFrame(out, 1, ("S " + sess).getBytes(StandardCharsets.UTF_8));
                sink.connected(true);
                // The newly paired/reconnected socket MUST receive a full
                // mode snapshot. mode(lastMode) previously deduplicated itself
                // and sent nothing, leaving the phone in HOME/BROWSE while
                // the TV had already focused Search or a player.
                sendAsync("V "+lastMode);
                sendAsync("O "+lastSources);
                sendAsync("H "+lastPlayback);
                try { frames(in, out); } finally {
                    activeOutput=null;
                    synchronized (this) { connected = false; dropped = System.currentTimeMillis(); }
                    sink.connected(false);
                }
                return;
            }
            refuse(out);
        } catch (Exception ignored) {
        }
    }

    private volatile boolean connected;
    private volatile String sess = "";

    /** The page needs the key until the first socket upgrade, then it is dead; the socket may come back with the session value for 60 s from the same address. */
    private boolean sessionOrKey(String k, String who, String path) {
        if (path.equals("/ws") && keyUsed) return who.equals(claimedBy) && !sess.isEmpty();
        return !keyUsed && keyOk(k);
    }

    private static void refuse(OutputStream out) throws IOException {
        reply(out, 403, "text/plain", "closed".getBytes(StandardCharsets.UTF_8));
    }

    private void frames(InputStream in, OutputStream out) throws IOException {
        while (!stopped) {
            int b0 = in.read();
            if (b0 < 0) return;
            int b1 = in.read();
            if (b1 < 0) return;
            int op = b0 & 0x0f;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7f;
            if (len == 126) len = (in.read() << 8) | in.read();
            else if (len == 127) return;
            if (!masked || len > MAX_FRAME) return;
            byte[] mask = new byte[4];
            readFully(in, mask);
            byte[] data = new byte[(int) len];
            readFully(in, data);
            for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
            if (stopped) return;
            lastActivity = System.currentTimeMillis();
            if (op == 8) return;
            if (op == 9) { writeFrame(out, 10, data); continue; }
            if (op != 1) continue;
            String m = new String(data, StandardCharsets.UTF_8);
            if (!dispatch(m)) { writeFrame(out, 1, "P".getBytes(StandardCharsets.UTF_8)); }
        }
    }

    /** Returns true if handled as a control message; false for a ping that must be echoed. */
    boolean dispatch(String m) {
        if (m.isEmpty()) return true;
        try {
            switch (m.charAt(0)) {
                case 'M': { String[] p = m.substring(2).split(" "); sink.move(clamp(Integer.parseInt(p[0])), clamp(Integer.parseInt(p[1]))); return true; }
                case 'C': sink.click(); return true;
                case 'D': sink.down(); return true;
                case 'U': sink.up(); return true;
                case 'K': { int code = Integer.parseInt(m.substring(2).trim()); if (ALLOWED_KEYS.contains(code)) sink.key(code); return true; }
                case 'T': sink.text(m.length() > 2 ? m.substring(2) : ""); return true;
                // One frame = one complete film-search command, not separate
                // "type" and "Go" messages whose focus can diverge.
                case 'Q': if(m.startsWith("Q ")&&m.length()<=258)sink.search(m.substring(2));return true;
                case 'E': if(m.startsWith("E ")&&m.length()<=258)sink.submitPageText(m.substring(2));return true;
                case 'L': if(m.startsWith("L ")&&m.length()<=258)sink.liveText(m.substring(2));return true;
                case 'Z': {
                    if(!m.matches("Z (?:0|[1-9][0-9]{0,7})"))return true;
                    long value=Long.parseLong(m.substring(2));
                    if(value<=86_400_000L)sink.seek(value);
                    return true;
                }
                case 'J': {
                    if(!m.matches("J (?:[1-9]|1[0-2])"))return true;
                    sink.chooseSource(Integer.parseInt(m.substring(2)));return true;
                }
                // B and X are exact, bounded edits: no arbitrary JavaScript,
                // delete key spam or remote injection into secure fields.
                case 'B': if("B".equals(m))sink.edit("BACKSPACE");return true;
                case 'X': if("X".equals(m))sink.edit("CLEAR");return true;
                case 'S': {
                    if(m.length()>18||!m.startsWith("S "))return true;
                    String[] p=m.substring(2).split(" ");
                    if(p.length==2)sink.scroll(clamp(Integer.parseInt(p[0])),clamp(Integer.parseInt(p[1])));
                    return true;
                }
                case 'A': {
                    if(m.length()>22||!m.startsWith("A "))return true;
                    String action=m.substring(2);
                    if(action.equals("PLAY")||action.equals("PAUSE")||action.equals("BACK")||
                       action.equals("FULLSCREEN")||action.equals("NEXT")||
                       action.equals("SEARCH")||action.equals("GO")||
                       action.equals("SOURCES"))
                        sink.action(action);
                    return true;
                }
                case 'P': return false;
                default: return true;
            }
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static int clamp(int v) { return Math.max(-400, Math.min(400, v)); }

    private static void writeFrame(OutputStream out, int op, byte[] data) throws IOException {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | op);
        if (data.length < 126) f.write(data.length);
        else { f.write(126); f.write(data.length >> 8); f.write(data.length & 0xff); }
        f.write(data);
        synchronized (out) { out.write(f.toByteArray()); out.flush(); }
    }

    private static void readFully(InputStream in, byte[] b) throws IOException {
        int n = 0;
        while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) throw new IOException("closed"); n += r; }
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int last4 = 0;
        while (b.size() < 8192) {
            int c = in.read();
            if (c < 0) return null;
            b.write(c);
            last4 = (last4 << 8) | c;
            if (last4 == 0x0d0a0d0a) return b.toString("ISO-8859-1");
        }
        return null;
    }

    private static void reply(OutputStream out, int code, String type, byte[] body) throws IOException {
        String text = code == 200 ? "OK" : "Forbidden";
        out.write(("HTTP/1.1 " + code + " " + text + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
