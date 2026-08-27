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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChannelRepository {
    public static final String CATEGORY_ALL = "All";
    public static final String CATEGORY_FAVOURITES = "Favourites";
    public static final String CATEGORY_SUBSCRIPTION = "Subscription";
    public static final String CATEGORY_UNAVAILABLE = "Unavailable";

    private static final int CATALOGUE_SCHEMA = 2;
    private static final String JIO_CACHE = "jio_channels.json";
    private static final String META_CACHE = "catalog_meta.json";

    private final Context context;
    private final JioApiClient api;

    public ChannelRepository(Context context) {
        this.context = context.getApplicationContext();
        this.api = new JioApiClient(this.context);
    }

    public JioApiClient api() { return api; }

    public synchronized List<Channel> loadAll() {
        LinkedHashMap<Integer, Channel> byNumber = new LinkedHashMap<>();
        for (Channel channel : loadChannelFile(JIO_CACHE)) {
            if (channel.id.isEmpty() || channel.number <= 0) continue;
            byNumber.put(channel.number, channel);
        }
        List<Channel> channels = new ArrayList<>(byNumber.values());
        channels.sort(Comparator.comparingInt(value -> value.number));
        return channels;
    }

    public synchronized List<Channel> refreshJio() throws Exception {
        List<Channel> previous = loadAll();
        Map<String, Channel> previousById = new HashMap<>();
        for (Channel channel : previous) previousById.put(channel.id, channel);

        List<Channel> fresh = api.fetchChannels();
        if (fresh.isEmpty()) throw new IllegalStateException("JioTV returned an empty television guide");
        for (Channel channel : fresh) {
            Channel prior = previousById.get(channel.id);
            if (prior != null) {
                channel.accessState = prior.accessState;
                channel.accessMessage = prior.accessMessage;
                channel.accessUpdatedAt = prior.accessUpdatedAt;
                channel.nowTitle = prior.nowTitle;
                channel.nextTitle = prior.nextTitle;
            }
            if (channel.requiresSubscription && Channel.ACCESS_UNKNOWN.equals(channel.accessState)) {
                channel.accessState = Channel.ACCESS_SUBSCRIPTION;
                channel.accessMessage = "This channel may require a separate JioTV subscription.";
            }
        }
        saveChannelFile(JIO_CACHE, fresh);
        JSONObject meta = new JSONObject();
        meta.put("schema", CATALOGUE_SCHEMA);
        meta.put("updatedAt", System.currentTimeMillis());
        meta.put("count", fresh.size());
        meta.put("source", "JioTV mobile catalogue");
        writeFile(META_CACHE, meta.toString());
        return loadAll();
    }

    public synchronized void updateAccessState(String channelId, String state, String message) {
        if (channelId == null || channelId.trim().isEmpty()) return;
        List<Channel> channels = loadChannelFile(JIO_CACHE);
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
    }

    public void applyAccessState(Channel channel, String state, String message) {
        if (channel == null) return;
        channel.accessState = state == null || state.trim().isEmpty() ? Channel.ACCESS_UNKNOWN : state;
        channel.accessMessage = message == null ? "" : message.trim();
        channel.accessUpdatedAt = System.currentTimeMillis();
        updateAccessState(channel.id, channel.accessState, channel.accessMessage);
    }

    public synchronized void clearCatalogue() {
        new File(context.getFilesDir(), JIO_CACHE).delete();
        new File(context.getFilesDir(), META_CACHE).delete();
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

    public List<String> categories(List<Channel> channels) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(CATEGORY_ALL);
        values.add(CATEGORY_FAVOURITES);
        if (containsSubscription(channels)) values.add(CATEGORY_SUBSCRIPTION);
        if (containsUnavailable(channels)) values.add(CATEGORY_UNAVAILABLE);

        String[] preferredLanguages = {"Hindi", "Punjabi", "English", "Marathi", "Bengali", "Tamil", "Telugu", "Gujarati", "Kannada", "Malayalam"};
        for (String language : preferredLanguages) {
            if (containsLanguage(channels, language)) values.add(language);
        }
        for (Channel channel : channels) {
            if (!channel.isRegularGuideChannel()) continue;
            String language = clean(channel.language);
            if (!language.isEmpty() && !"Other".equalsIgnoreCase(language)) values.add(language);
        }

        String[] preferredGenres = {"News", "Entertainment", "Movies", "Sports", "Kids", "Music", "Devotional", "Business News", "Infotainment", "Lifestyle", "Educational"};
        for (String genre : preferredGenres) {
            if (containsCategory(channels, genre)) values.add(genre);
        }
        for (Channel channel : channels) {
            if (!channel.isRegularGuideChannel()) continue;
            String category = clean(channel.category);
            if (!category.isEmpty() && !"Other".equalsIgnoreCase(category)) values.add(category);
        }
        return new ArrayList<>(values);
    }

    public List<Channel> filter(List<Channel> channels, String category, String query) {
        Set<Integer> favourites = favourites();
        String selected = category == null || category.trim().isEmpty() ? CATEGORY_ALL : category.trim();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Channel> out = new ArrayList<>();
        for (Channel channel : channels) {
            boolean categoryMatch;
            if (CATEGORY_ALL.equalsIgnoreCase(selected)) {
                categoryMatch = channel.isRegularGuideChannel();
            } else if (CATEGORY_FAVOURITES.equalsIgnoreCase(selected)) {
                categoryMatch = favourites.contains(channel.number);
            } else if (CATEGORY_SUBSCRIPTION.equalsIgnoreCase(selected)) {
                categoryMatch = channel.isSubscriptionChannel();
            } else if (CATEGORY_UNAVAILABLE.equalsIgnoreCase(selected)) {
                categoryMatch = channel.isUnavailable();
            } else {
                categoryMatch = channel.isRegularGuideChannel()
                        && (channel.language.equalsIgnoreCase(selected) || channel.category.equalsIgnoreCase(selected));
            }

            String access = channel.accessLabel().toLowerCase(Locale.ROOT);
            boolean queryMatch = normalizedQuery.isEmpty()
                    || channel.name.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || channel.language.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || channel.category.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || channel.displayNumber().contains(normalizedQuery)
                    || access.contains(normalizedQuery);
            if (categoryMatch && queryMatch) out.add(channel);
        }
        return out;
    }

    public String categoryForChannel(Channel channel) {
        if (channel == null) return CATEGORY_ALL;
        if (channel.isSubscriptionChannel()) return CATEGORY_SUBSCRIPTION;
        if (channel.isUnavailable()) return CATEGORY_UNAVAILABLE;
        String language = clean(channel.language);
        return language.isEmpty() || "Other".equalsIgnoreCase(language) ? CATEGORY_ALL : language;
    }

    public Channel byNumber(List<Channel> channels, int number) {
        for (Channel channel : channels) if (channel.number == number) return channel;
        return null;
    }

    public Channel next(List<Channel> channels, int number, int direction) {
        if (channels == null || channels.isEmpty()) return null;
        int index = -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).number == number) { index = i; break; }
        }
        if (index < 0) return direction >= 0 ? channels.get(0) : channels.get(channels.size() - 1);
        int next = (index + (direction >= 0 ? 1 : -1) + channels.size()) % channels.size();
        return channels.get(next);
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
        return added;
    }

    public void setLastChannel(int number) {
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(AppConfig.KEY_LAST_CHANNEL, number).apply();
    }

    public int lastChannel() {
        return context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getInt(AppConfig.KEY_LAST_CHANNEL, 1);
    }

    public String lastCategory() {
        return context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getString(AppConfig.KEY_LAST_CATEGORY, CATEGORY_ALL);
    }

    public void setLastCategory(String value) {
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .edit().putString(AppConfig.KEY_LAST_CATEGORY, value).apply();
    }

    private boolean containsLanguage(List<Channel> channels, String expected) {
        for (Channel channel : channels) {
            if (channel.isRegularGuideChannel() && channel.language.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    private boolean containsCategory(List<Channel> channels, String expected) {
        for (Channel channel : channels) {
            if (channel.isRegularGuideChannel() && channel.category.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    private boolean containsSubscription(List<Channel> channels) {
        for (Channel channel : channels) if (channel.isSubscriptionChannel()) return true;
        return false;
    }

    private boolean containsUnavailable(List<Channel> channels) {
        for (Channel channel : channels) if (channel.isUnavailable()) return true;
        return false;
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }

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
