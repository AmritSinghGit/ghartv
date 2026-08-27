package in.ghartv.nova;

import org.json.JSONObject;

public final class Program {
    public String title = "Live now";
    public String description = "";
    public String posterUrl = "";
    public long startEpochMs;
    public long endEpochMs;
    public boolean catchupAvailable;

    public static Program fromJson(JSONObject o) {
        Program p = new Program();
        p.title = o.optString("showname", o.optString("title", "Live now"));
        p.description = o.optString("description", "");
        p.posterUrl = o.optString("episodePoster", "");
        p.startEpochMs = o.optLong("startEpoch", 0L);
        p.endEpochMs = o.optLong("endEpoch", 0L);
        p.catchupAvailable = o.optBoolean("stbCatchupAvailable", false);
        return p;
    }

    public boolean isLive(long now) { return startEpochMs <= now && endEpochMs > now; }
}
