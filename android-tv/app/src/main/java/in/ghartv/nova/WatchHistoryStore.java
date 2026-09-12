package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Account-scoped, local-only successful viewing memory.
 *
 * Failed tune attempts are deliberately excluded from Recent and For you. Only
 * channels that reached player READY are eligible. Telemetry never reads this
 * store and the TV owner can reset it without affecting login or favourites.
 */
public final class WatchHistoryStore {
    private static final String PREFS_PREFIX = "ghartv_watch_history_v2_";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_ENTRIES = 100;
    private static final long MAX_RETAIN_AGE_MS = 120L * 24L * 60L * 60L * 1000L;

    private final SharedPreferences preferences;
    private Map<Integer, Entry> cache;

    public WatchHistoryStore(Context context) {
        Context app = context.getApplicationContext();
        JioSession session = JioSession.load(app);
        String identity = !session.subscriberId.isEmpty() ? session.subscriberId : session.mobile;
        if (identity == null || identity.isEmpty()) identity = "anonymous";
        preferences = app.getSharedPreferences(
                PREFS_PREFIX + accountScope(identity),
                Context.MODE_PRIVATE);
    }

    public synchronized void recordTune(Channel channel) {
        if (channel == null || channel.number <= 0) return;
        Map<Integer, Entry> values = read();
        Entry entry = values.get(channel.number);
        if (entry == null) entry = new Entry(channel.number);
        entry.lastTuneAt = System.currentTimeMillis();
        entry.tuneCount++;
        values.put(channel.number, entry);
        write(values);
    }

    public synchronized void recordReady(Channel channel) {
        if (channel == null || channel.number <= 0) return;
        Map<Integer, Entry> values = read();
        Entry entry = values.get(channel.number);
        if (entry == null) entry = new Entry(channel.number);
        entry.lastReadyAt = System.currentTimeMillis();
        entry.readyCount++;
        values.put(channel.number, entry);
        write(values);
    }

    public synchronized void recordSession(Channel channel, long watchMs) {
        if (channel == null || channel.number <= 0 || watchMs <= 0L) return;
        Map<Integer, Entry> values = read();
        Entry entry = values.get(channel.number);
        if (entry == null) entry = new Entry(channel.number);
        long now = System.currentTimeMillis();
        entry.lastReadyAt = Math.max(entry.lastReadyAt, now);
        entry.lastSessionMs = Math.min(12L * 60L * 60L * 1000L, watchMs);
        entry.watchMs = Math.min(Long.MAX_VALUE / 2L,
                entry.watchMs + Math.min(watchMs, 12L * 60L * 60L * 1000L));
        values.put(channel.number, entry);
        write(values);
    }

    /** Most recently successful channels. Failed tune attempts never appear. */
    public synchronized List<Integer> recentNumbers(int limit) {
        List<Entry> entries = successfulEntries();
        entries.sort((left, right) -> Long.compare(right.lastReadyAt, left.lastReadyAt));
        return numbers(entries, limit);
    }

    /** Channels suitable for a Continue row: recent and watched for at least 30 seconds. */
    public synchronized List<Integer> continueNumbers(int limit) {
        List<Entry> entries = successfulEntries();
        entries.removeIf(entry -> entry.lastSessionMs < 30_000L && entry.watchMs < 30_000L);
        entries.sort((left, right) -> {
            int recent = Long.compare(right.lastReadyAt, left.lastReadyAt);
            if (recent != 0) return recent;
            return Long.compare(right.watchMs, left.watchMs);
        });
        return numbers(entries, limit);
    }

    /** Household favourites by actual successful viewing, not failed attempts. */
    public synchronized List<Integer> topNumbers(int limit) {
        List<Entry> entries = successfulEntries();
        entries.sort((left, right) -> {
            int score = Long.compare(score(right), score(left));
            if (score != 0) return score;
            return Long.compare(right.lastReadyAt, left.lastReadyAt);
        });
        return numbers(entries, limit);
    }

    public synchronized long score(int channelNumber) {
        Entry entry = read().get(channelNumber);
        return entry == null || entry.lastReadyAt <= 0L ? 0L : score(entry);
    }

    public synchronized int count() {
        return successfulEntries().size();
    }

    public synchronized boolean hasHistory() { return count() > 0; }

    public synchronized void clear() {
        cache = new HashMap<>();
        preferences.edit().remove(KEY_HISTORY).apply();
    }

    private List<Entry> successfulEntries() {
        long cutoff = System.currentTimeMillis() - MAX_RETAIN_AGE_MS;
        List<Entry> entries = new ArrayList<>(read().values());
        entries.removeIf(entry -> entry.lastReadyAt <= 0L || entry.lastReadyAt < cutoff);
        return entries;
    }

    private long score(Entry entry) {
        long watchMinutes = Math.min(20_000L, entry.watchMs / 60_000L);
        long recencyBonus;
        long age = Math.max(0L, System.currentTimeMillis() - entry.lastReadyAt);
        if (age < 24L * 60L * 60L * 1000L) recencyBonus = 1_000L;
        else if (age < 7L * 24L * 60L * 60L * 1000L) recencyBonus = 550L;
        else if (age < 30L * 24L * 60L * 60L * 1000L) recencyBonus = 180L;
        else recencyBonus = 0L;
        return entry.readyCount * 320L + watchMinutes + recencyBonus;
    }

    private List<Integer> numbers(List<Entry> entries, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<Integer> out = new ArrayList<>();
        for (Entry entry : entries) {
            out.add(entry.number);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private Map<Integer, Entry> read() {
        if (cache != null) return cache;
        Map<Integer, Entry> out = new HashMap<>();
        String raw = preferences.getString(KEY_HISTORY, "{}");
        try {
            JSONObject root = new JSONObject(raw == null ? "{}" : raw);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject object = root.optJSONObject(key);
                if (object == null) continue;
                int number = object.optInt("number", 0);
                if (number <= 0) continue;
                Entry entry = new Entry(number);
                entry.lastTuneAt = object.optLong("lastTuneAt", 0L);
                entry.lastReadyAt = object.optLong("lastReadyAt", 0L);
                entry.tuneCount = object.optInt("tuneCount", 0);
                entry.readyCount = object.optInt("readyCount", 0);
                entry.watchMs = object.optLong("watchMs", 0L);
                entry.lastSessionMs = object.optLong("lastSessionMs", 0L);
                out.put(number, entry);
            }
        } catch (Exception ignored) {}
        cache = out;
        return cache;
    }

    private void write(Map<Integer, Entry> values) {
        List<Entry> entries = new ArrayList<>(values.values());
        entries.sort(Comparator.comparingLong(Entry::lastActivityAt).reversed());
        JSONObject root = new JSONObject();
        Map<Integer, Entry> retainedValues = new HashMap<>();
        long cutoff = System.currentTimeMillis() - MAX_RETAIN_AGE_MS;
        int retained = 0;
        for (Entry entry : entries) {
            if (retained >= MAX_ENTRIES) break;
            if (entry.lastActivityAt() > 0L && entry.lastActivityAt() < cutoff) continue;
            try {
                JSONObject object = new JSONObject();
                object.put("number", entry.number);
                object.put("lastTuneAt", entry.lastTuneAt);
                object.put("lastReadyAt", entry.lastReadyAt);
                object.put("tuneCount", entry.tuneCount);
                object.put("readyCount", entry.readyCount);
                object.put("watchMs", entry.watchMs);
                object.put("lastSessionMs", entry.lastSessionMs);
                root.put(String.valueOf(entry.number), object);
                retainedValues.put(entry.number, entry);
                retained++;
            } catch (Exception ignored) {}
        }
        cache = retainedValues;
        preferences.edit().putString(KEY_HISTORY, root.toString()).apply();
    }

    private static String accountScope(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(24);
            for (int index = 0; index < 12; index++) {
                out.append(String.format(java.util.Locale.US, "%02x", digest[index]));
            }
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(identity.hashCode());
        }
    }

    private static final class Entry {
        final int number;
        long lastTuneAt;
        long lastReadyAt;
        int tuneCount;
        int readyCount;
        long watchMs;
        long lastSessionMs;

        Entry(int number) { this.number = number; }
        long lastActivityAt() { return Math.max(lastTuneAt, lastReadyAt); }
    }
}
