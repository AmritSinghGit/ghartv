package in.ghartv.nova;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded process-local route evidence; no URL, token, title or account data is stored. */
final class NetworkRouteState {
    static final String DIRECT = "direct";
    static final String SECURE_DNS = "secure_dns";
    static final String TOR = "tor";
    private static final long RECENT_MS = 10L * 60L * 1000L;
    private static final class Entry {
        final String route;
        final long at;
        Entry(String value) { route=value; at=android.os.SystemClock.elapsedRealtime(); }
    }
    private static final ConcurrentHashMap<String,Entry> LAST = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private NetworkRouteState() {}

    static void mark(String family, String route) {
        String key = clean(family), value = clean(route);
        if (key.isEmpty() || value.isEmpty()) return;
        Entry old = LAST.put(key, new Entry(value));
        if (old == null || !old.route.equals(value)) GENERATION.incrementAndGet();
    }

    static String recent(String family) {
        Entry value = LAST.get(clean(family));
        if (value == null || android.os.SystemClock.elapsedRealtime() - value.at >= RECENT_MS) return DIRECT;
        return value.route;
    }

    static boolean usedRecently(String prefix, String route) {
        String wanted = clean(prefix), value = clean(route);
        long now = android.os.SystemClock.elapsedRealtime();
        for (java.util.Map.Entry<String,Entry> item : LAST.entrySet()) {
            if (item.getKey().startsWith(wanted) && item.getValue().route.equals(value)
                    && now - item.getValue().at < RECENT_MS) return true;
        }
        return false;
    }

    static long generation() { return GENERATION.get(); }
    private static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
