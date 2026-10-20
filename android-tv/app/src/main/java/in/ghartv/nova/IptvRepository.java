package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/** Fixed, cached IPTV-org playlist reader. It never consumes or changes Jio credentials. */
public class IptvRepository {
    private static final String CACHE = "iptv-org-index.m3u";
    private static final String PREFS = "ghartv_iptv_org_v1";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_MODIFIED = "last_modified";
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private static final long FRESH_MS = 6L * 60L * 60L * 1000L;
    private static final Object PARSED_LOCK = new Object();
    private static ParsedCache parsedCache;
    private static final class ParsedCache {
        final long modified;
        final long length;
        final List<IptvChannel> channels;
        final int describedEntries;
        final int insecureOrPrivateSkipped;
        final int duplicateSkipped;
        ParsedCache(File file, IptvPlaylistParser.Result parsed) {
            modified = file.lastModified();
            length = file.length();
            channels = Collections.unmodifiableList(new ArrayList<>(parsed.channels));
            describedEntries = parsed.describedEntries;
            insecureOrPrivateSkipped = parsed.insecureOrPrivateSkipped;
            duplicateSkipped = parsed.duplicateSkipped;
        }
        boolean matches(File file) { return modified == file.lastModified() && length == file.length(); }
    }
    private final Context context;
    private final Call.Factory client;

    public static final class Result {
        public final List<IptvChannel> channels;
        public final boolean fromCache;
        public final long updatedAt;
        public final int describedEntries;
        public final int insecureOrPrivateSkipped;
        public final int duplicateSkipped;
        public final String warning;
        Result(List<IptvChannel> values, boolean cached, long updated, IptvPlaylistParser.Result parsed, String note) {
            channels = values; fromCache = cached; updatedAt = updated;
            describedEntries = parsed.describedEntries;
            insecureOrPrivateSkipped = parsed.insecureOrPrivateSkipped;
            duplicateSkipped = parsed.duplicateSkipped; warning = note == null ? "" : note;
        }
    }

    public IptvRepository(Context value) {
        this(value, IptvNetwork.catalogue(value.getApplicationContext()));
    }
    IptvRepository(Context value, Call.Factory factory) {
        context = value.getApplicationContext();
        client = factory;
    }

    public Result load(boolean force) throws Exception {
        File cache = new File(context.getFilesDir(), CACHE);
        long age = cache.exists() ? System.currentTimeMillis() - cache.lastModified() : Long.MAX_VALUE;
        if (!force && cache.exists()) {
            String warning = age >= 0 && age < FRESH_MS ? ""
                    : "Using the last verified local copy. Choose Refresh to update it.";
            return parseCached(cache, warning);
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Request.Builder request = new Request.Builder().url(AppConfig.IPTV_ORG_PLAYLIST)
                    .header("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain")
                    .header("User-Agent", "GharTV-IPTV/" + BuildConfig.VERSION_NAME);
            if (cache.exists()) {
                String etag = prefs.getString(KEY_ETAG, ""), modified = prefs.getString(KEY_MODIFIED, "");
                if (!etag.isEmpty()) request.header("If-None-Match", etag);
                if (!modified.isEmpty()) request.header("If-Modified-Since", modified);
            }
            try (Response response = client.newCall(request.build()).execute()) {
                if (response.code() == 304 && cache.exists()) return parseCached(cache, "Playlist unchanged; using the verified local copy.");
                if (!response.isSuccessful()) throw new IOException("IPTV-org playlist returned HTTP " + response.code());
                long length = response.body() == null ? 0L : response.body().contentLength();
                if (length > MAX_BYTES) throw new IOException("IPTV-org playlist exceeded the safety limit");
                String raw = response.body() == null ? "" : readLimited(response.body().byteStream());
                if (!raw.startsWith("#EXTM3U")) throw new IOException("IPTV-org playlist was not an M3U document");
                Result result = parse(raw, false, System.currentTimeMillis(), "");
                if (result.channels.isEmpty()) throw new IOException("IPTV-org playlist contained no secure public channels");
                writeAtomically(cache, raw);
                remember(cache, new IptvPlaylistParser.Result(result.channels, result.describedEntries,
                        result.insecureOrPrivateSkipped, result.duplicateSkipped));
                prefs.edit().putString(KEY_ETAG, value(response.header("ETag")))
                        .putString(KEY_MODIFIED, value(response.header("Last-Modified"))).apply();
                return new Result(result.channels, false, cache.lastModified(),
                        new IptvPlaylistParser.Result(result.channels, result.describedEntries,
                                result.insecureOrPrivateSkipped, result.duplicateSkipped), "");
            }
        } catch (Exception network) {
            if (cache.exists()) {
                Result cached = parseCached(cache,
                        "The public playlist could not refresh; using the last verified local copy.");
                if (!cached.channels.isEmpty()) return cached;
            }
            throw network;
        }
    }

    private Result parseCached(File file, String warning) throws IOException {
        synchronized (PARSED_LOCK) {
            if (parsedCache != null && parsedCache.matches(file)) {
                IptvPlaylistParser.Result parsed = new IptvPlaylistParser.Result(parsedCache.channels,
                        parsedCache.describedEntries, parsedCache.insecureOrPrivateSkipped,
                        parsedCache.duplicateSkipped);
                return new Result(parsedCache.channels, true, file.lastModified(), parsed, warning);
            }
        }
        String raw = read(file);
        IptvPlaylistParser.Result parsed = IptvPlaylistParser.parse(raw);
        if (parsed.channels.isEmpty()) throw new IOException("No secure public IPTV channels are available yet");
        remember(file, parsed);
        synchronized (PARSED_LOCK) {
            IptvPlaylistParser.Result copy = new IptvPlaylistParser.Result(parsedCache.channels,
                    parsedCache.describedEntries, parsedCache.insecureOrPrivateSkipped,
                    parsedCache.duplicateSkipped);
            return new Result(parsedCache.channels, true, file.lastModified(), copy, warning);
        }
    }
    private static void remember(File file, IptvPlaylistParser.Result parsed) {
        synchronized (PARSED_LOCK) { parsedCache = new ParsedCache(file, parsed); }
    }
    static void clearParsedCacheForTests() {
        synchronized (PARSED_LOCK) { parsedCache = null; }
    }

    private Result parse(String raw, boolean cached, long updated, String warning) throws IOException {
        IptvPlaylistParser.Result parsed = IptvPlaylistParser.parse(raw);
        if (parsed.channels.isEmpty()) throw new IOException("No secure public IPTV channels are available yet");
        return new Result(Collections.unmodifiableList(new ArrayList<>(parsed.channels)), cached, updated, parsed, warning);
    }

    public static List<IptvChannel> filter(List<IptvChannel> source, String group, String query) {
        String wanted = group == null ? "All" : group.trim();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<IptvChannel> out = new ArrayList<>();
        if (source == null) return out;
        for (IptvChannel channel : source) {
            if (!"All".equals(wanted) && !wanted.equals(channel.group)) continue;
            String haystack = (channel.name + " " + channel.group + " " + channel.id).toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : q.split("\\s+")) if (!word.isEmpty() && !haystack.contains(word)) { matches = false; break; }
            if (matches) out.add(channel);
        }
        return out;
    }

    public static List<String> groups(List<IptvChannel> channels) {
        Map<String,Integer> counts = groupCounts(channels);
        List<String> groups = new ArrayList<>(counts.keySet());
        groups.remove("All");
        groups.sort((a,b) -> {
            int byCount = Integer.compare(counts.getOrDefault(b,0), counts.getOrDefault(a,0));
            return byCount != 0 ? byCount : a.compareToIgnoreCase(b);
        });
        groups.add(0, "All");
        return groups;
    }

    public static Map<String,Integer> groupCounts(List<IptvChannel> channels) {
        Map<String,Integer> counts = new LinkedHashMap<>();
        int all=0;
        if(channels!=null)for(IptvChannel channel:channels){all++;counts.put(channel.group,counts.getOrDefault(channel.group,0)+1);}
        counts.put("All",all);return counts;
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static String read(File file) throws IOException {
        try(InputStream in=new FileInputStream(file)){return readLimited(in);}
    }
    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16*1024];int total=0,n;
        while((n=in.read(buffer))>=0){if(n==0)continue;total+=n;if(total>MAX_BYTES)throw new IOException("IPTV-org playlist exceeded the safety limit");out.write(buffer,0,n);}
        return out.toString(StandardCharsets.UTF_8.name());
    }
    private static void writeAtomically(File target,String raw) throws IOException {
        File temporary=new File(target.getParentFile(),target.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temporary)){out.write(raw.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(target.exists()&&!target.delete())throw new IOException("Could not replace the IPTV cache");
        if(!temporary.renameTo(target))throw new IOException("Could not save the IPTV cache");
    }
}
