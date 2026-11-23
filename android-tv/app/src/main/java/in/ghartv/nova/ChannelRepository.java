package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical Jio guide repository with a cached living-room index.
 *
 * Channel failures are evidence, not permanent labels: transient access states
 * expire automatically so one bad evening cannot hide a channel forever.
 */
public final class ChannelRepository {
    public static final String CATEGORY_ALL = ChannelIndex.VIEW_ALL;
    public static final String CATEGORY_FAVOURITES = ChannelIndex.VIEW_FAVOURITES;
    public static final String CATEGORY_SUBSCRIPTION = ChannelIndex.VIEW_SUBSCRIPTION;
    public static final String CATEGORY_UNAVAILABLE = ChannelIndex.VIEW_JIO_ACCESS;

    private static final int CATALOGUE_SCHEMA = 4;
    private static final String JIO_CACHE = "jio_channels.json";
    private static final String META_CACHE = "catalog_meta.json";
    private static final String VIEW_SELECTION_PREFIX = "view_selection_";

    private static final long ACCESS_AVAILABLE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long ACCESS_UNAVAILABLE_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final long ACCESS_SUBSCRIPTION_TTL_MS = 24L * 60L * 60L * 1000L;

    private final Context context;
    private final JioApiClient api;
    private final WatchHistoryStore historyStore;

    private ChannelIndex index;
    private long indexSignature = Long.MIN_VALUE;
    private List<Channel> memoryChannels;
    private long memoryFileModified = Long.MIN_VALUE;

    public ChannelRepository(Context context) {
        this.context = context.getApplicationContext();
        this.api = new JioApiClient(this.context);
        this.historyStore = new WatchHistoryStore(this.context);
    }

    public JioApiClient api() { return api; }
    public WatchHistoryStore history() { return historyStore; }

    public synchronized List<Channel> loadAll() {
        File cacheFile = new File(context.getFilesDir(), JIO_CACHE);
        long modified = cacheFile.exists() ? cacheFile.lastModified() : 0L;
        if (memoryChannels != null && modified == memoryFileModified) {
            ensureIndex(memoryChannels);
            return new ArrayList<>(memoryChannels);
        }

        LinkedHashMap<Integer, Channel> byNumber = new LinkedHashMap<>();
        boolean normalized = false;
        long now = System.currentTimeMillis();
        for (Channel channel : loadChannelFile(JIO_CACHE)) {
            if (channel.id.isEmpty() || channel.number <= 0) continue;
            normalized |= normalizeAccessState(channel, now);
            byNumber.put(channel.number, channel);
        }
        List<Channel> channels = new ArrayList<>(byNumber.values());
        channels.sort(Comparator.comparingInt(value -> value.number));
        memoryChannels = channels;
        memoryFileModified = modified;
        if (normalized && !channels.isEmpty()) {
            try {
                saveChannelFile(JIO_CACHE, channels);
                memoryFileModified = new File(context.getFilesDir(), JIO_CACHE).lastModified();
            } catch (Exception ignored) {}
        }
        ensureIndex(channels);
        return new ArrayList<>(channels);
    }

    public List<Channel> refreshJio() throws Exception {
        List<Channel> fresh = api.fetchChannels();
        if (fresh.isEmpty()) throw new IllegalStateException("JioTV returned an empty television guide");
        synchronized (this) {
        List<Channel> previous = loadAll();
        Map<String, Channel> previousById = new HashMap<>();
        for (Channel channel : previous) previousById.put(channel.id, channel);

        long now = System.currentTimeMillis();
        for (Channel channel : fresh) {
            channel.requiresSubscription = channel.requiresSubscription || channel.subscriptionHint;
            channel.subscriptionHint = channel.subscriptionHint || channel.requiresSubscription;
            Channel prior = previousById.get(channel.id);
            if (prior != null) {
                normalizeAccessState(prior, now);
                channel.accessState = prior.accessState;
                channel.accessMessage = prior.accessMessage;
                channel.accessUpdatedAt = prior.accessUpdatedAt;
                channel.nowTitle = prior.nowTitle;
                channel.nextTitle = prior.nextTitle;
            }
            if (channel.requiresSubscription && Channel.ACCESS_UNKNOWN.equals(channel.accessState)) {
                channel.accessState = Channel.ACCESS_SUBSCRIPTION;
                channel.accessMessage = "This channel may require a separate JioTV subscription.";
                channel.accessUpdatedAt = now;
            }
            normalizeAccessState(channel, now);
        }
        saveChannelFile(JIO_CACHE, fresh);
        JSONObject meta = new JSONObject();
        meta.put("schema", CATALOGUE_SCHEMA);
        meta.put("updatedAt", now);
        meta.put("count", fresh.size());
        meta.put("source", "JioTV mobile catalogue");
        writeFile(META_CACHE, meta.toString());
        memoryChannels = null;
        memoryFileModified = Long.MIN_VALUE;
        invalidateIndex();
        return loadAll();
        }
    }

    /** Persist a channel access result. Stale results are later expired automatically. */
    public synchronized void updateAccessState(String channelId, String state, String message) {
        if (channelId == null || channelId.trim().isEmpty()) return;
        List<Channel> channels = loadAll();
        boolean changed = false;
        for (Channel channel : channels) {
            if (!channelId.equals(channel.id)) continue;
            channel.accessState = state == null || state.trim().isEmpty() ? Channel.ACCESS_UNKNOWN : state;
            channel.accessMessage = message == null ? "" : message.trim();
            channel.accessUpdatedAt = System.currentTimeMillis();
            changed = true;
            break;
        }
        if (!changed) return;
        try { saveChannelFile(JIO_CACHE, channels); }
        catch (Exception ignored) {}
        memoryChannels = new ArrayList<>(channels);
        memoryFileModified = new File(context.getFilesDir(), JIO_CACHE).lastModified();
        invalidateIndex();
    }

    public void applyAccessState(Channel channel, String state, String message) {
        if (channel == null) return;
        channel.accessState = state == null || state.trim().isEmpty() ? Channel.ACCESS_UNKNOWN : state;
        channel.accessMessage = message == null ? "" : message.trim();
        channel.accessUpdatedAt = System.currentTimeMillis();
        updateAccessState(channel.id, channel.accessState, channel.accessMessage);
    }

    /** Forget only temporary access evidence for an owner-initiated retry. */
    public void clearTemporaryAccess(Channel channel) {
        if (channel == null || channel.requiresSubscription || channel.subscriptionHint) return;
        applyAccessState(channel, Channel.ACCESS_UNKNOWN, "");
    }

    public synchronized void clearCatalogue() {
        new File(context.getFilesDir(), JIO_CACHE).delete();
        new File(context.getFilesDir(), META_CACHE).delete();
        memoryChannels = null;
        memoryFileModified = Long.MIN_VALUE;
        invalidateIndex();
    }

    public long lastUpdatedAt() {
        try {
            JSONObject meta = new JSONObject(readFile(META_CACHE));
            if (meta.optInt("schema", 0) < CATALOGUE_SCHEMA) return 0L;
            return meta.optLong("updatedAt", 0L);
        } catch (Exception ignored) { return 0L; }
    }

    public int cachedCount() {
        try { return new JSONObject(readFile(META_CACHE)).optInt("count", loadAll().size()); }
        catch (Exception ignored) { return loadAll().size(); }
    }

    public synchronized List<String> categories(List<Channel> channels) {
        return ensureIndex(channels).categories();
    }

    public synchronized Map<String, Integer> categoryCounts(List<Channel> channels) {
        return ensureIndex(channels).categoryCounts();
    }

    public synchronized List<Channel> filter(List<Channel> channels, String category, String query) {
        return ensureIndex(channels).filter(category, query);
    }

    public synchronized List<Channel> search(List<Channel> channels, String query) {
        return ensureIndex(channels).search(query);
    }

    public String categoryForChannel(Channel channel) {
        if (channel == null) return ChannelIndex.VIEW_ALL;
        if (channel.isSubscriptionChannel()) return ChannelIndex.VIEW_SUBSCRIPTION;
        if (channel.isUnavailable()) return ChannelIndex.VIEW_JIO_ACCESS;
        String language = channel.language == null ? "" : channel.language.trim();
        return language.isEmpty() || "Other".equalsIgnoreCase(language)
                ? ChannelIndex.VIEW_ALL : language;
    }

    public synchronized Channel byNumber(List<Channel> channels, int number) {
        Channel found = ensureIndex(channels).byNumber(number);
        if (found != null) return found;
        if (channels == null) return null;
        for (Channel channel : channels) if (channel.number == number) return channel;
        return null;
    }

    public Channel next(List<Channel> channels, int number, int direction) {
        if (channels == null || channels.isEmpty()) return null;
        int[] numbers=new int[channels.size()];
        for(int i=0;i<numbers.length;i++)numbers[i]=channels.get(i).number;
        return channels.get(GuideTimeline.nextChannelIndex(numbers,number,direction));
    }

    /** Next candidate that is not already known to fail, falling back to ordinary next. */
    public Channel nextLikelyWorking(List<Channel> channels, int number, int direction) {
        if (channels == null || channels.isEmpty()) return null;
        Channel fallback = next(channels, number, direction);
        Channel cursor = fallback;
        for (int i = 0; i < channels.size(); i++) {
            if (cursor == null) break;
            if (!cursor.isUnavailable() && (!cursor.isSubscriptionChannel() || cursor.isAvailable())) return cursor;
            cursor = next(channels, cursor.number, direction);
        }
        return fallback;
    }

    public Set<Integer> favourites() {
        SharedPreferences prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
        Set<String> raw = prefs.getStringSet(AppConfig.KEY_FAVOURITES, Collections.emptySet());
        Set<Integer> out = new HashSet<>();
        for (String value : raw) {
            try { out.add(Integer.parseInt(value)); }
            catch (Exception ignored) {}
        }
        return out;
    }

    public boolean toggleFavourite(int number) {
        SharedPreferences prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
        Set<String> values = new HashSet<>(prefs.getStringSet(AppConfig.KEY_FAVOURITES, Collections.emptySet()));
        String key = String.valueOf(number);
        boolean added;
        if (values.contains(key)) { values.remove(key); added = false; }
        else { values.add(key); added = true; }
        prefs.edit().putStringSet(AppConfig.KEY_FAVOURITES, values).apply();
        invalidateIndex();
        return added;
    }

    public void setLastChannel(int number) {
        if (number <= 0) return;
        SharedPreferences prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
        int current = prefs.getInt(AppConfig.KEY_LAST_CHANNEL, 0);
        SharedPreferences.Editor edit = prefs.edit();
        if (current > 0 && current != number) {
            edit.putInt(AppConfig.KEY_PREVIOUS_CHANNEL, current);
        }
        edit.putInt(AppConfig.KEY_LAST_CHANNEL, number).apply();
    }

    public int lastChannel() {
        return context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getInt(AppConfig.KEY_LAST_CHANNEL, 1);
    }

    public int previousChannel() {
        return context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getInt(AppConfig.KEY_PREVIOUS_CHANNEL, 0);
    }

    public String lastCategory() {
        SharedPreferences prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
        String value = prefs.getString(AppConfig.KEY_LAST_CATEGORY, ChannelIndex.VIEW_ALL);
        if (value == null || value.trim().isEmpty()) return ChannelIndex.VIEW_ALL;
        String clean = value.trim();
        // One-time semantic migration from the v0.5.3 guide labels.
        if ("All".equalsIgnoreCase(clean) || "Home".equalsIgnoreCase(clean)) return ChannelIndex.VIEW_ALL;
        if ("Unavailable".equalsIgnoreCase(clean)) return ChannelIndex.VIEW_JIO_ACCESS;
        return clean;
    }

    public void setLastCategory(String value) {
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .edit().putString(AppConfig.KEY_LAST_CATEGORY,
                        ChannelIndex.canonicalView(value))
                .apply();
    }

    public void rememberViewSelection(String view, int number) {
        if (number <= 0) return;
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(VIEW_SELECTION_PREFIX + viewKey(view), number).apply();
    }

    public int lastChannelForView(String view) {
        return context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getInt(VIEW_SELECTION_PREFIX + viewKey(view), lastChannel());
    }

    public synchronized String indexSummary(List<Channel> channels) {
        ChannelIndex current = ensureIndex(channels);
        return String.format(java.util.Locale.US, "%,d indexed", current.searchableCount());
    }

    /** Count the channels currently represented by the in-memory search index. */
    public synchronized int indexedCount() {
        List<Channel> channels = loadAll();
        return ensureIndex(channels).searchableCount();
    }

    public int subscriptionCount(List<Channel> channels) {
        int count = 0;
        if (channels != null) for (Channel channel : channels) if (channel.isSubscriptionChannel()) count++;
        return count;
    }

    public int unavailableCount(List<Channel> channels) {
        int count = 0;
        if (channels != null) for (Channel channel : channels) if (channel.isUnavailable()) count++;
        return count;
    }

    public synchronized void invalidateIndex() {
        index = null;
        indexSignature = Long.MIN_VALUE;
    }

    private synchronized ChannelIndex ensureIndex(List<Channel> channels) {
        List<Channel> safe = channels == null ? Collections.emptyList() : channels;
        long signature = signature(safe);
        if (index == null || signature != indexSignature) {
            index = new ChannelIndex(safe, favourites(), historyStore);
            indexSignature = signature;
        }
        return index;
    }

    private long signature(List<Channel> channels) {
        long value = 1125899906842597L;
        value = value * 31L + channels.size();
        for (Channel channel : channels) {
            value = value * 31L + channel.number;
            value = value * 31L + (channel.id == null ? 0 : channel.id.hashCode());
            value = value * 31L + (channel.accessState == null ? 0 : channel.accessState.hashCode());
            value = value * 31L + (channel.requiresSubscription || channel.subscriptionHint ? 1 : 0);
        }
        value = value * 31L + favourites().hashCode();
        value = value * 31L + historyStore.recentNumbers(100).hashCode();
        value = value * 31L + historyStore.topNumbers(100).hashCode();
        return value;
    }

    private boolean normalizeAccessState(Channel channel, long now) {
        boolean changed = false;
        channel.requiresSubscription = channel.requiresSubscription || channel.subscriptionHint;
        channel.subscriptionHint = channel.subscriptionHint || channel.requiresSubscription;
        String state = channel.accessState == null ? Channel.ACCESS_UNKNOWN : channel.accessState;
        long age = channel.accessUpdatedAt <= 0L ? Long.MAX_VALUE : Math.max(0L, now - channel.accessUpdatedAt);
        if (Channel.ACCESS_AVAILABLE.equals(state) && age > ACCESS_AVAILABLE_TTL_MS) {
            state = Channel.ACCESS_UNKNOWN;
            changed = true;
        } else if (Channel.ACCESS_UNAVAILABLE.equals(state) && age > ACCESS_UNAVAILABLE_TTL_MS) {
            state = Channel.ACCESS_UNKNOWN;
            changed = true;
        } else if (Channel.ACCESS_SUBSCRIPTION.equals(state)
                && !channel.requiresSubscription && age > ACCESS_SUBSCRIPTION_TTL_MS) {
            state = Channel.ACCESS_UNKNOWN;
            changed = true;
        }
        if (channel.requiresSubscription && Channel.ACCESS_UNKNOWN.equals(state)) {
            state = Channel.ACCESS_SUBSCRIPTION;
            changed = true;
        }
        if (!state.equals(channel.accessState)) channel.accessState = state;
        if (changed && Channel.ACCESS_UNKNOWN.equals(state)) {
            channel.accessMessage = "";
            channel.accessUpdatedAt = 0L;
        }
        return changed;
    }

    private String viewKey(String view) {
        return Integer.toHexString(ChannelIndex.normalize(view).hashCode());
    }

    private List<Channel> loadChannelFile(String name) {
        try {
            String raw = readFile(name);
            if (raw.isEmpty()) return new ArrayList<>();
            JSONObject root = new JSONObject(raw);
            return parseChannels(root.optJSONArray("channels"));
        } catch (Exception ignored) { return new ArrayList<>(); }
    }

    private List<Channel> parseChannels(JSONArray array) {
        List<Channel> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) out.add(Channel.fromJson(object));
        }
        return out;
    }

    private void saveChannelFile(String name, List<Channel> channels) throws Exception {
        JSONArray array = new JSONArray();
        for (Channel channel : channels) array.put(channel.toJson());
        JSONObject root = new JSONObject();
        root.put("savedAt", System.currentTimeMillis());
        root.put("source", "JioTV");
        root.put("channels", array);
        writeFile(name, root.toString());
    }

    private String readFile(String name) throws Exception {
        File file = new File(context.getFilesDir(), name);
        if (!file.exists()) return "";
        try (InputStream input = new FileInputStream(file)) { return readStream(input); }
    }

    private void writeFile(String name, String content) throws Exception {
        File target = new File(context.getFilesDir(), name);
        File temp = new File(context.getFilesDir(), name + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) throw new IllegalStateException("Could not replace " + name);
        if (!temp.renameTo(target)) throw new IllegalStateException("Could not save " + name);
    }

    private String readStream(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
