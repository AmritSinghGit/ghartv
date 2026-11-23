package in.ghartv.nova;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plan B for a licensed IPTV stream that gave no picture: the same channel's other playlist entries
 * (same id, else same normalised name), best health first, never the one that just failed. Pure Java.
 */
public final class StreamFallback {
    public static final long FIRST_FRAME_DEADLINE_MS = 8000L;
    public static final int MAX_TRIES = 3;

    private StreamFallback() {}

    public static String channelKey(IptvChannel c) {
        if (c.id != null && !c.id.isEmpty()) return "id:" + c.id.toLowerCase(Locale.ROOT);
        return "nm:" + (c.name == null ? "" : c.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
    }

    public static String streamKey(IptvChannel c) { return c.streamUrl == null ? "" : c.streamUrl; }

    /** Other entries of the same channel, ordered by health, dead ones dropped. */
    public static List<IptvChannel> alternates(IptvChannel failed, List<IptvChannel> all, StreamHealth health) {
        List<IptvChannel> same = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        String want = channelKey(failed);
        if (all != null) for (IptvChannel c : all) {
            if (c == null || c.streamUrl == null || c.streamUrl.isEmpty()) continue;
            if (c.streamUrl.equals(failed.streamUrl) || !channelKey(c).equals(want)) continue;
            if (urls.contains(c.streamUrl) || health.isDead(c.streamUrl)) continue;
            same.add(c); urls.add(c.streamUrl);
        }
        List<IptvChannel> out = new ArrayList<>();
        for (String u : health.order(urls)) for (IptvChannel c : same) if (c.streamUrl.equals(u)) { out.add(c); break; }
        return out;
    }

    /** Public https only, same rule as the player's own check. */
    public static boolean allowed(IptvChannel c) { return c != null && IptvPlaylistParser.isPublicHttps(c.streamUrl); }
}
