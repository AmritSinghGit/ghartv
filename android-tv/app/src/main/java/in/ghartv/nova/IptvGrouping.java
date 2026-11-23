package in.ghartv.nova;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IPTV channels grouped by playlist group, type, country or language (owner 13 Nov). Uses only
 * what the playlist carries: a missing value is "not set", never guessed. Type is a keyword
 * reading of group-title only.
 */
public final class IptvGrouping {
    public static final String NOT_SET = "not set";
    public static final String[] DIMENSIONS = {"Group", "Type", "Country", "Language"};
    private static final String[][] TYPE_WORDS = {
        {"News", "news"}, {"Sport", "sport"}, {"Films", "movie", "film", "cinema"},
        {"Children", "kid", "child", "cartoon", "animation", "family"}, {"Music", "music"},
        {"Faith", "religio", "faith", "spiritual", "devotion", "christian", "islam", "hindu", "gospel"},
    };

    private IptvGrouping() {}

    public static String next(String dimension) {
        for (int i = 0; i < DIMENSIONS.length; i++) if (DIMENSIONS[i].equals(dimension)) return DIMENSIONS[(i + 1) % DIMENSIONS.length];
        return DIMENSIONS[0];
    }

    /** News / Sport / Films / Children / Music / Faith from the group-title; "Other" if no word matches; NOT_SET if no group. */
    public static String typeOf(String group) {
        String g = group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
        if (g.isEmpty() || g.equals("other") || g.equals("undefined") || g.equals("uncategorized")) return NOT_SET;
        for (String[] row : TYPE_WORDS) for (int i = 1; i < row.length; i++) if (g.contains(row[i])) return row[0];
        return "Other";
    }

    /** The values this channel has for the dimension (several when the list gives "A;B"); one NOT_SET if none. */
    public static List<String> values(IptvChannel channel, String dimension) {
        List<String> out = new ArrayList<>();
        if ("Type".equals(dimension)) out.add(typeOf(channel.group));
        else if ("Country".equals(dimension)) split(channel.country, out);
        else if ("Language".equals(dimension)) split(channel.language, out);
        else split(channel.group == null || channel.group.equalsIgnoreCase("Other") ? "" : channel.group, out);
        if (out.isEmpty()) out.add(NOT_SET);
        return out;
    }

    private static void split(String raw, List<String> out) {
        if (raw == null) return;
        for (String part : raw.split("[;,]")) {
            String v = part.trim();
            if (!v.isEmpty() && !out.contains(v)) out.add(v);
        }
    }

    public static Map<String, Integer> counts(List<IptvChannel> channels, String dimension) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int all = 0;
        if (channels != null) for (IptvChannel c : channels) {
            all++;
            for (String v : values(c, dimension)) counts.put(v, counts.getOrDefault(v, 0) + 1);
        }
        counts.put("All", all);
        return counts;
    }

    /** "All" first, then by size, then name. */
    public static List<String> groups(List<IptvChannel> channels, String dimension) {
        Map<String, Integer> counts = counts(channels, dimension);
        List<String> out = new ArrayList<>(counts.keySet());
        out.remove("All");
        out.sort((a, b) -> {
            int c = Integer.compare(counts.get(b), counts.get(a));
            return c != 0 ? c : a.compareToIgnoreCase(b);
        });
        out.add(0, "All");
        return out;
    }

    public static List<IptvChannel> filter(List<IptvChannel> source, String dimension, String selected, String query) {
        List<IptvChannel> byText = IptvRepository.filter(source, "All", query);
        if (selected == null || "All".equals(selected.trim())) return byText;
        List<IptvChannel> out = new ArrayList<>();
        for (IptvChannel c : byText) if (values(c, dimension).contains(selected)) out.add(c);
        return out;
    }
}
