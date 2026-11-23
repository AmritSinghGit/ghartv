package in.ghartv.nova;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, on-demand EPG search. It never scans all 1,300+ channels at startup.
 * Channel results are immediate; programme results are added as the selected scope is sampled.
 */
public final class ProgramSearchService {
    public interface Callback {
        void onResults(List<Channel> matches, int channelsScanned, boolean complete);
    }

    private static final int MAX_CHANNELS = 36;
    private static final int MAX_PROGRAM_RESULTS = 48;

    private ProgramSearchService() {}

    public static void search(Context context, ChannelRepository repository,
                              List<Channel> preferredScope, List<Channel> allChannels,
                              String query, Callback callback) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            callback.onResults(Collections.emptyList(), 0, true);
            return;
        }

        List<Channel> candidates = candidates(repository, preferredScope, allChannels);
        int total = Math.min(MAX_CHANNELS, candidates.size());
        if (total == 0) {
            callback.onResults(Collections.emptyList(), 0, true);
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(3);
        Handler main = new Handler(Looper.getMainLooper());
        LinkedHashMap<String, Channel> found = new LinkedHashMap<>();
        AtomicInteger completed = new AtomicInteger();

        for (int i = 0; i < total; i++) {
            Channel source = candidates.get(i);
            pool.execute(() -> {
                try {
                    List<Program> programmes = repository.api().fetchEpg(source.id, 0);
                    long now = System.currentTimeMillis();
                    for (Program programme : programmes) {
                        if (!normalize(programme.title).contains(normalized)) continue;
                        Channel result = cloneChannel(source);
                        result.searchProgrammeTitle = programme.title;
                        result.searchProgrammeStartMs = programme.startEpochMs;
                        result.searchProgrammeEndMs = programme.endEpochMs;
                        result.searchProgrammeId = programme.programId;
                        result.searchProgrammeSrno = programme.srno;
                        result.searchProgrammeShowtime = programme.showtime;
                        result.nowTitle = programme.title;
                        result.nextTitle = programme.startEpochMs > now
                                ? "Starts " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new java.util.Date(programme.startEpochMs))
                                : programme.endEpochMs < now && source.catchupAvailable
                                ? "Catch-up available"
                                : "On now";
                        synchronized (found) {
                            String key = source.id + "|" + programme.title + "|" + programme.startEpochMs;
                            if (found.size() < MAX_PROGRAM_RESULTS) found.put(key, result);
                        }
                    }
                } catch (Exception error) {
                    Telemetry.error(context, "program_search_epg", error,
                            Telemetry.data("language", source.language, "category", source.category));
                } finally {
                    int done = completed.incrementAndGet();
                    if (done == total) {
                        List<Channel> results;
                        synchronized (found) { results = new ArrayList<>(found.values()); }
                        results.sort(Comparator
                                .comparingLong((Channel value) -> distanceFromNow(value.searchProgrammeStartMs))
                                .thenComparingInt(value -> value.number));
                        pool.shutdown();
                        main.post(() -> callback.onResults(results, total, true));
                    }
                }
            });
        }
    }

    private static List<Channel> candidates(ChannelRepository repository,
                                            List<Channel> preferredScope,
                                            List<Channel> allChannels) {
        LinkedHashMap<String, Channel> out = new LinkedHashMap<>();
        Set<Integer> favourites = repository.favourites();
        if (preferredScope != null) {
            for (Channel channel : preferredScope) {
                if (favourites.contains(channel.number)) out.put(channel.id, channel);
            }
            for (Channel channel : preferredScope) out.put(channel.id, channel);
        }
        if (allChannels != null) {
            for (Channel channel : allChannels) {
                if (favourites.contains(channel.number)) out.put(channel.id, channel);
            }
            for (Channel channel : allChannels) {
                if (channel.nowTitle != null && !channel.nowTitle.isEmpty()) out.put(channel.id, channel);
            }
            for (Channel channel : allChannels) out.put(channel.id, channel);
        }
        return new ArrayList<>(out.values());
    }

    private static Channel cloneChannel(Channel channel) {
        try {
            JSONObject object = channel.toJson();
            return Channel.fromJson(object);
        } catch (Exception ignored) {
            return channel;
        }
    }

    private static long distanceFromNow(long value) {
        return Math.abs(System.currentTimeMillis() - value);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
