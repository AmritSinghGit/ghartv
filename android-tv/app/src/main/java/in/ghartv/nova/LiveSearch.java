package in.ghartv.nova;

import java.util.ArrayList;
import java.util.List;

/** One ranked search over Jio live channels and public IPTV channels (licensed sources only; films are a separate route). */
public final class LiveSearch {
    public static final class Hit {
        public final Channel jio;
        public final IptvChannel iptv;
        public final int score;
        public int alternates;
        Hit(Channel j, IptvChannel i, int s) { jio = j; iptv = i; score = s; }
        public String name() { return jio != null ? jio.name : iptv.name; }
        public String source() { return jio != null ? "Jio TV" : "Public IPTV"; }
    }
    public static final int MAX_HITS = 40;

    static String norm(String v) {
        if (v == null) return "";
        return java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}\\p{M}]+", " ").trim().replaceAll(" +", " ");
    }

    /** 0 = no match; higher is better: exact 100, name prefix 80, word prefix 60, name contains 40, other fields 20. */
    static int score(String name, String other, String term, String[] words) {
        String n = norm(name);
        if (n.equals(term)) return 100;
        String hay = n + " " + norm(other);
        for (String w : words) if (!hay.contains(w)) return 0;
        if (n.startsWith(term)) return 80;
        for (String w : n.split(" ")) if (w.startsWith(words[0])) { boolean all = true; for (String x : words) if (!n.contains(x)) all = false; if (all) return 60; break; }
        boolean inName = true;
        for (String w : words) if (!n.contains(w)) inName = false;
        return inName ? 40 : 20;
    }

    public static List<Hit> search(List<Channel> jio, List<IptvChannel> iptv, String text) {
        List<Hit> out = new ArrayList<>();
        String term = norm(text);
        if (term.isEmpty()) return out;
        String[] words = term.split(" ");
        java.util.Map<String, Hit> byName = new java.util.HashMap<>();
        if (jio != null) for (Channel c : jio) {
            int s;
            if (c.displayNumber().equals(term) || String.valueOf(c.number).equals(term)) s = 100;
            else s = score(c.name, c.language + " " + c.category + " " + c.nowTitle + " " + c.nextTitle, term, words);
            if (s == 0) continue;
            if (Channel.ACCESS_UNAVAILABLE.equals(c.accessState)) s -= 30;       // never offer a known-dead channel first
            else if (c.isSubscriptionChannel()) s -= 10;
            Hit h = new Hit(c, null, s);
            out.add(h);
            byName.putIfAbsent(norm(c.name), h);
        }
        if (iptv != null) for (IptvChannel c : iptv) {
            int s = score(c.name, c.group + " " + c.language + " " + c.country, term, words);
            if (s == 0) continue;
            Hit twin = byName.get(norm(c.name));
            if (twin != null) { twin.alternates++; continue; }                   // same channel on both sources: one row
            Hit h = new Hit(null, c, s - 1);                                    // Jio wins ties
            out.add(h);
            byName.put(norm(c.name), h);
        }
        out.sort((a, b) -> b.score != a.score ? b.score - a.score : a.name().compareToIgnoreCase(b.name()));
        return out.size() > MAX_HITS ? new ArrayList<>(out.subList(0, MAX_HITS)) : out;
    }
}
