package in.ghartv.nova;

import java.text.Normalizer;
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

/**
 * Immutable in-memory channel index for a remote-first living-room guide.
 *
 * Number tuning is O(1), guide views are precomputed, and search is ranked by
 * exact number/name, prefix, token coverage, favourites, successful playback
 * and local-only household history.
 */
public final class ChannelIndex {
    public static final String VIEW_FOR_YOU = "Home";
    public static final String VIEW_CONTINUE = "Continue";
    public static final String VIEW_RECENT = "Recent";
    public static final String VIEW_FAVOURITES = "Favourites";
    public static final String VIEW_AVAILABLE = "Working now";
    public static final String VIEW_ALL = "All channels";
    public static final String VIEW_SUBSCRIPTION = "Subscription";
    public static final String VIEW_JIO_ACCESS = "Needs attention";
    public static final String VIEW_SEARCH = "Search";

    private static final int MAX_PREFIX = 18;
    private static final int HOME_LIMIT = 64;

    private final WatchHistoryStore historyStore;
    private final Set<Integer> favourites;
    private final List<Channel> all = new ArrayList<>();
    private final Map<Integer, Channel> byNumber = new HashMap<>();
    private final Map<String, Channel> byId = new HashMap<>();
    private final Map<Integer, String> searchable = new HashMap<>();
    private final Map<String, LinkedHashSet<Integer>> prefixIndex = new HashMap<>();
    private final Map<String, List<Channel>> viewCache = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> viewCounts = new LinkedHashMap<>();
    private final List<String> views = new ArrayList<>();

    public ChannelIndex(List<Channel> channels, Set<Integer> favourites, WatchHistoryStore historyStore) {
        this.favourites = favourites == null ? Collections.emptySet() : new HashSet<>(favourites);
        this.historyStore = historyStore;
        build(channels == null ? Collections.emptyList() : channels);
    }

    private void build(List<Channel> channels) {
        LinkedHashMap<Integer, Channel> deduped = new LinkedHashMap<>();
        Set<String> seenIds = new HashSet<>();
        for (Channel channel : channels) {
            if (channel == null || channel.number <= 0 || clean(channel.id).isEmpty()) continue;
            if (!seenIds.add(channel.id)) continue;
            deduped.put(channel.number, channel);
        }
        all.addAll(deduped.values());
        all.sort(Comparator.comparingInt(value -> value.number));
        for (Channel channel : all) {
            byNumber.put(channel.number, channel);
            byId.put(channel.id, channel);
            String text = normalize(channel.displayNumber() + " " + channel.number + " " + channel.name + " "
                    + channel.language + " " + channel.category + " " + channel.accessLabel());
            searchable.put(channel.number, text);
            indexTokens(channel.number, text);
        }
        buildViews();
    }

    private void indexTokens(int number, String text) {
        for (String token : text.split(" ")) {
            if (token.isEmpty()) continue;
            if (token.length() == 1 && !Character.isDigit(token.charAt(0))) continue;
            int max = Math.min(MAX_PREFIX, token.length());
            int start = Character.isDigit(token.charAt(0)) ? 1 : 2;
            for (int length = start; length <= max; length++) {
                prefixIndex.computeIfAbsent(token.substring(0, length), ignored -> new LinkedHashSet<>()).add(number);
            }
            prefixIndex.computeIfAbsent(token, ignored -> new LinkedHashSet<>()).add(number);
        }
    }

    private void buildViews() {
        putView(VIEW_FOR_YOU, buildForYou());

        List<Channel> recent = channelsFromNumbers(historyStore.recentNumbers(40));
        if (!recent.isEmpty()) putView(VIEW_RECENT, recent);

        List<Channel> favouriteChannels = channelsFromNumbers(new ArrayList<>(favourites));
        favouriteChannels.sort(personalComparator());
        if (!favouriteChannels.isEmpty()) putView(VIEW_FAVOURITES, favouriteChannels);

        List<Channel> available = new ArrayList<>();
        List<Channel> subscription = new ArrayList<>();
        List<Channel> restricted = new ArrayList<>();
        for (Channel channel : all) {
            if (channel.isAvailable()) available.add(channel);
            if (channel.isSubscriptionChannel()) subscription.add(channel);
            if (channel.isUnavailable()) restricted.add(channel);
        }
        // Proven-working channels are ranked into Home instead of becoming another
        // top-level chip. The visible guide stays simple for family use.
        putView(VIEW_ALL, regularChannels());

        LinkedHashSet<String> languages = new LinkedHashSet<>();
        String[] preferredLanguages = {
                "Hindi", "Punjabi", "English", "Marathi", "Bengali", "Tamil",
                "Telugu", "Gujarati", "Kannada", "Malayalam", "Odia", "Assamese", "Urdu"
        };
        for (String value : preferredLanguages) if (containsLanguage(value)) languages.add(value);
        for (Channel channel : all) {
            if (needsAttention(channel)) continue;
            String value = clean(channel.language);
            if (!value.isEmpty() && !"Other".equalsIgnoreCase(value)) languages.add(value);
        }
        for (String language : languages) putView(language, selectExact(language, true));

        LinkedHashSet<String> genres = new LinkedHashSet<>();
        String[] preferredGenres = {
                "News", "Entertainment", "Movies", "Sports", "Kids", "Music",
                "Devotional", "Business News", "Infotainment", "Lifestyle", "Educational"
        };
        for (String value : preferredGenres) if (containsCategory(value)) genres.add(value);
        for (Channel channel : all) {
            if (needsAttention(channel)) continue;
            String value = clean(channel.category);
            if (!value.isEmpty() && !"Other".equalsIgnoreCase(value)) genres.add(value);
        }
        for (String genre : genres) {
            if (!viewCache.containsKey(genre)) putView(genre, selectExact(genre, false));
        }

        if (!subscription.isEmpty()) putView(VIEW_SUBSCRIPTION, sortGuide(subscription));
        if (!restricted.isEmpty()) putView(VIEW_JIO_ACCESS, sortGuide(restricted));
    }

    private List<Channel> regularChannels() {
        List<Channel> regular = new ArrayList<>();
        for (Channel channel : all) if (!needsAttention(channel)) regular.add(channel);
        return sortGuide(regular);
    }

    private List<Channel> buildForYou() {
        LinkedHashMap<Integer, Channel> values = new LinkedHashMap<>();
        List<Channel> favouriteChannels = channelsFromNumbers(new ArrayList<>(favourites));
        favouriteChannels.sort(personalComparator());
        for (Channel channel : favouriteChannels) addHome(values, channel);
        for (Integer number : historyStore.topNumbers(30)) addHome(values, byNumber.get(number));
        for (Integer number : historyStore.recentNumbers(30)) addHome(values, byNumber.get(number));
        for (Channel channel : all) {
            if (channel.isAvailable()) addHome(values, channel);
            if (values.size() >= HOME_LIMIT) break;
        }
        for (Channel channel : all) {
            if (!needsAttention(channel)) addHome(values, channel);
            if (values.size() >= HOME_LIMIT) break;
        }
        return new ArrayList<>(values.values());
    }

    private void addHome(Map<Integer, Channel> values, Channel channel) {
        if (channel != null && !needsAttention(channel)) values.put(channel.number, channel);
    }

    private List<Channel> channelsFromNumbers(List<Integer> numbers) {
        List<Channel> out = new ArrayList<>();
        if (numbers == null) return out;
        Set<Integer> seen = new HashSet<>();
        for (Integer number : numbers) {
            Channel value = byNumber.get(number);
            if (value != null && seen.add(number)) out.add(value);
        }
        return out;
    }

    private List<Channel> selectExact(String value, boolean language) {
        List<Channel> out = new ArrayList<>();
        for (Channel channel : all) {
            String field = language ? channel.language : channel.category;
            if (!needsAttention(channel) && field != null && field.equalsIgnoreCase(value)) out.add(channel);
        }
        return sortGuide(out);
    }

    private void putView(String name, List<Channel> values) {
        if (values == null || values.isEmpty() || viewCache.containsKey(name)) return;
        List<Channel> copy = Collections.unmodifiableList(new ArrayList<>(values));
        viewCache.put(name, copy);
        views.add(name);
        viewCounts.put(name, copy.size());
    }

    public List<String> categories() { return new ArrayList<>(views); }

    public Map<String, Integer> categoryCounts() { return new LinkedHashMap<>(viewCounts); }

    public List<Channel> filter(String category, String query) {
        String normalizedQuery = normalize(query);
        if (!normalizedQuery.isEmpty()) return search(normalizedQuery);
        String selected = clean(category);
        if (selected.isEmpty() || !viewCache.containsKey(selected)) selected = VIEW_FOR_YOU;
        List<Channel> base = viewCache.get(selected);
        return base == null ? new ArrayList<>(all) : new ArrayList<>(base);
    }

    /** Global search across all channels. The result itself becomes the CH+/- scope. */
    public List<Channel> search(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return Collections.emptyList();
        return rankedSearch(all, normalizedQuery);
    }

    private List<Channel> rankedSearch(List<Channel> base, String query) {
        Set<Integer> allowed = new HashSet<>();
        for (Channel channel : base) allowed.add(channel.number);

        LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
        String compact = query.replace(" ", "");
        try {
            int number = Integer.parseInt(compact);
            if (byNumber.containsKey(number)) candidates.add(number);
        } catch (Exception ignored) {}

        String[] tokens = query.split(" ");
        Set<Integer> intersection = null;
        boolean unknownToken = false;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            String key = token.length() > MAX_PREFIX ? token.substring(0, MAX_PREFIX) : token;
            LinkedHashSet<Integer> values = prefixIndex.get(key);
            if (values == null) {
                unknownToken = true;
                continue;
            }
            if (intersection == null) intersection = new LinkedHashSet<>(values);
            else intersection.retainAll(values);
            candidates.addAll(values);
        }
        if (intersection != null && !intersection.isEmpty()) {
            LinkedHashSet<Integer> ordered = new LinkedHashSet<>(intersection);
            ordered.addAll(candidates);
            candidates = ordered;
        }
        if (candidates.isEmpty() || unknownToken) {
            for (Channel channel : base) candidates.add(channel.number);
        }

        List<Scored> scored = new ArrayList<>();
        for (Integer number : candidates) {
            if (!allowed.contains(number)) continue;
            Channel channel = byNumber.get(number);
            if (channel == null) continue;
            int score = score(channel, query, tokens);
            if (score > 0) scored.add(new Scored(channel, score));
        }
        scored.sort((left, right) -> {
            int score = Integer.compare(right.score, left.score);
            if (score != 0) return score;
            int personal = Long.compare(historyStore.score(right.channel.number), historyStore.score(left.channel.number));
            if (personal != 0) return personal;
            return Integer.compare(left.channel.number, right.channel.number);
        });
        List<Channel> out = new ArrayList<>();
        for (Scored value : scored) out.add(value.channel);
        return out;
    }

    private int score(Channel channel, String query, String[] tokens) {
        String number = String.valueOf(channel.number);
        String display = channel.displayNumber();
        String name = normalize(channel.name);
        String language = normalize(channel.language);
        String category = normalize(channel.category);
        String allText = searchable.getOrDefault(channel.number, "");
        int score = 0;
        if (number.equals(query) || display.equals(query)) score += 20_000;
        else if (number.startsWith(query) || display.startsWith(query)) score += 8_000;
        if (name.equals(query)) score += 16_000;
        else if (name.startsWith(query)) score += 12_000;
        else if (name.contains(query)) score += 8_000;
        if (language.equals(query) || category.equals(query)) score += 5_000;
        else if (language.startsWith(query) || category.startsWith(query)) score += 3_000;
        int matched = 0;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (allText.contains(token)) {
                score += 900;
                matched++;
            }
        }
        if (matched == tokens.length && tokens.length > 1) score += 2_400;
        if (favourites.contains(channel.number)) score += 500;
        if (channel.isAvailable()) score += 300;
        if (channel.isSubscriptionChannel()) score -= 120;
        if (channel.isUnavailable()) score -= 180;
        score += (int) Math.min(600L, historyStore.score(channel.number) / 10L);
        return score;
    }

    public Channel byNumber(int number) { return byNumber.get(number); }
    public Channel byId(String id) { return id == null ? null : byId.get(id); }
    public int count(String category) { return viewCounts.getOrDefault(category, 0); }
    public int searchableCount() { return all.size(); }

    private boolean containsLanguage(String value) {
        for (Channel channel : all) if (!needsAttention(channel) && channel.language.equalsIgnoreCase(value)) return true;
        return false;
    }

    private boolean containsCategory(String value) {
        for (Channel channel : all) if (!needsAttention(channel) && channel.category.equalsIgnoreCase(value)) return true;
        return false;
    }

    private List<Channel> sortGuide(List<Channel> values) {
        List<Channel> out = new ArrayList<>(values);
        out.sort(Comparator.comparingInt(channel -> channel.number));
        return out;
    }

    private boolean needsAttention(Channel channel) {
        return channel != null && (channel.isUnavailable() || (channel.isSubscriptionChannel() && !channel.isAvailable()));
    }

    private Comparator<Channel> personalComparator() {
        return (left, right) -> {
            int score = Long.compare(historyStore.score(right.number), historyStore.score(left.number));
            if (score != 0) return score;
            return Integer.compare(left.number, right.number);
        };
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private static final class Scored {
        final Channel channel;
        final int score;
        Scored(Channel channel, int score) { this.channel = channel; this.score = score; }
    }
}
