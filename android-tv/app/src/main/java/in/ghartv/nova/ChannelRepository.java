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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ChannelRepository {
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
        List<Channel> fresh = api.fetchChannels();
        if (fresh.isEmpty()) throw new IllegalStateException("JioTV returned an empty television guide");
        saveChannelFile(JIO_CACHE, fresh);
        JSONObject meta = new JSONObject();
        meta.put("updatedAt", System.currentTimeMillis());
        meta.put("count", fresh.size());
        meta.put("source", "JioTV mobile catalogue");
        writeFile(META_CACHE, meta.toString());
        return loadAll();
    }

    public synchronized void clearCatalogue() {
        new File(context.getFilesDir(), JIO_CACHE).delete();
        new File(context.getFilesDir(), META_CACHE).delete();
    }

    public long lastUpdatedAt() {
        try { return new JSONObject(readFile(META_CACHE)).optLong("updatedAt", 0L); }
        catch (Exception ignored) { return 0L; }
    }

    public int cachedCount() {
        try { return new JSONObject(readFile(META_CACHE)).optInt("count", loadAll().size()); }
        catch (Exception ignored) { return loadAll().size(); }
    }

    public List<String> categories(List<Channel> channels) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add("All");
        values.add("Favourites");

        String[] preferredLanguages = {"Hindi", "Punjabi", "English", "Marathi", "Bengali", "Tamil", "Telugu", "Gujarati", "Kannada", "Malayalam"};
        for (String language : preferredLanguages) {
            if (containsLanguage(channels, language)) values.add(language);
        }
        for (Channel channel : channels) {
            String language = clean(channel.language);
            if (!language.isEmpty() && !"Other".equalsIgnoreCase(language)) values.add(language);
        }

        String[] preferredGenres = {"News", "Entertainment", "Movies", "Sports", "Kids", "Music", "Devotional", "Business News", "Infotainment", "Lifestyle", "Educational"};
        for (String genre : preferredGenres) {
            if (containsCategory(channels, genre)) values.add(genre);
        }
        for (Channel channel : channels) {
            String category = clean(channel.category);
            if (!category.isEmpty() && !"Other".equalsIgnoreCase(category)) values.add(category);
        }
        return new ArrayList<>(values);
    }

    public List<Channel> filter(List<Channel> channels, String category, String query) {
        Set<Integer> favourites = favourites();
        String selected = category == null || category.trim().isEmpty() ? "All" : category.trim();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Channel> out = new ArrayList<>();
        for (Channel channel : channels) {
            boolean categoryMatch;
            if ("All".equalsIgnoreCase(selected)) categoryMatch = true;
            else if ("Favourites".equalsIgnoreCase(selected)) categoryMatch = favourites.contains(channel.number);
            else categoryMatch = channel.language.equalsIgnoreCase(selected) || channel.category.equalsIgnoreCase(selected);

            boolean queryMatch = normalizedQuery.isEmpty()
                    || channel.name.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || channel.language.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || channel.category.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || channel.displayNumber().contains(normalizedQuery);
            if (categoryMatch && queryMatch) out.add(channel);
        }
        return out;
    }

    public Channel byNumber(List<Channel> channels, int number) {
        for (Channel channel : channels) if (channel.number == number) return channel;
        return null;
    }

    public Channel next(List<Channel> channels, int number, int direction) {
        if (channels.isEmpty()) return null;
        int index = 0;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).number == number) { index = i; break; }
        }
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
                .getString(AppConfig.KEY_LAST_CATEGORY, "All");
    }

    public void setLastCategory(String value) {
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .edit().putString(AppConfig.KEY_LAST_CATEGORY, value).apply();
    }

    private boolean containsLanguage(List<Channel> channels, String expected) {
        for (Channel channel : channels) if (channel.language.equalsIgnoreCase(expected)) return true;
        return false;
    }

    private boolean containsCategory(List<Channel> channels, String expected) {
        for (Channel channel : channels) if (channel.category.equalsIgnoreCase(expected)) return true;
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
