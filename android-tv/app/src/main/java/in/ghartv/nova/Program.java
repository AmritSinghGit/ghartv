package in.ghartv.nova;

import org.json.JSONObject;

public final class Program {
    public String title = "Live now";
    public String description = "";
    public String posterUrl = "";
    public long startEpochMs;
    public long endEpochMs;
    public String programId = "";
    public String srno = "";
    public String showtime = "";

    public boolean catchupAvailable;

    public static Program fromJson(JSONObject o) {
        Program p = new Program();
        p.title = o.optString("showname", o.optString("title", "Live now"));
        p.description = o.optString("description", "");
        p.posterUrl = o.optString("episodePoster", "");
        p.startEpochMs = o.optLong("startEpoch", 0L);
        p.endEpochMs = o.optLong("endEpoch", 0L);
        p.catchupAvailable = o.optBoolean("stbCatchupAvailable", false);
                p.programId = o.optString("programId", o.optString("program_id", ""));
        p.srno = o.optString("srno", o.optString("serialNo", o.optString("serial_no", "")));
        p.showtime = o.optString("showtime", o.optString("showTime", ""));
return p;
    }

    public boolean isLive(long now) { return startEpochMs <= now && endEpochMs > now; }
}
