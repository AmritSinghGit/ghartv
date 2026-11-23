package in.ghartv.nova;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Local health of a licensed stream (IPTV playlist entry) from its last tries. Pure Java: the
 * caller keeps the JSON text in preferences. A stream that failed its last DEAD_AFTER tries in a row
 * is "dead" (hidden from Home/Continue, sorted last in search); one picture makes it healthy again.
 */
public final class StreamHealth {
    public static final int DEAD_AFTER = 3;
    public static final int KEEP = 400;
    private final JSONObject rows;

    public StreamHealth(String json) {
        JSONObject parsed;
        try { parsed = json == null || json.isEmpty() ? new JSONObject() : new JSONObject(json); }
        catch (JSONException e) { parsed = new JSONObject(); }
        rows = parsed;
    }

    public String toJson() { return rows.toString(); }

    /** picture reached: failures reset, first-frame time kept. */
    public void recordPicture(String key, long firstFrameMs, long nowMs) {
        JSONObject r = row(key);
        put(r, "fails", 0); put(r, "ok", r.optInt("ok") + 1); put(r, "ms", firstFrameMs); put(r, "at", nowMs);
        rows.remove(key); put(rows, key, r); trim();
    }

    public void recordFailure(String key, long nowMs) {
        JSONObject r = row(key);
        put(r, "fails", r.optInt("fails") + 1); put(r, "at", nowMs);
        rows.remove(key); put(rows, key, r); trim();
    }

    public int failsInARow(String key) { return rows.has(key) ? rows.optJSONObject(key).optInt("fails") : 0; }
    public boolean isDead(String key) { return failsInARow(key) >= DEAD_AFTER; }

    /** Higher is better. Unknown streams score 50 (tried before dead ones, after proven ones). */
    public int score(String key) {
        if (!rows.has(key)) return 50;
        JSONObject r = rows.optJSONObject(key);
        int fails = r.optInt("fails");
        if (fails >= DEAD_AFTER) return 0;
        int s = fails > 0 ? 40 - 15 * fails : 70 + Math.min(20, r.optInt("ok") * 2);
        long ms = r.optLong("ms");
        if (fails == 0 && ms > 0) s += ms < 2000 ? 10 : ms < 5000 ? 5 : 0;
        return Math.max(1, s);
    }

    /** Candidates best first, dead ones last; stable for equal scores (playlist order kept). */
    public List<String> order(List<String> keys) {
        List<String> out = new ArrayList<>(keys);
        for (int i = 1; i < out.size(); i++) {
            String k = out.get(i); int j = i - 1;
            while (j >= 0 && score(out.get(j)) < score(k)) { out.set(j + 1, out.get(j)); j--; }
            out.set(j + 1, k);
        }
        return out;
    }

    private JSONObject row(String key) {
        JSONObject r = rows.optJSONObject(key);
        return r == null ? new JSONObject() : r;
    }

    private void trim() {
        while (rows.length() > KEEP) {
            String oldest = null; long at = Long.MAX_VALUE;
            Iterator<String> it = rows.keys();
            while (it.hasNext()) {
                String k = it.next(); long t = rows.optJSONObject(k).optLong("at");
                if (t < at) { at = t; oldest = k; }
            }
            if (oldest == null) return;
            rows.remove(oldest);
        }
    }

    private static void put(JSONObject o, String k, Object v) { try { o.put(k, v); } catch (JSONException ignored) { } }
}
