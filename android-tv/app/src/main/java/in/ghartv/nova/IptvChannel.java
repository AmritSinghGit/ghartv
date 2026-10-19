package in.ghartv.nova;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

/** One public IPTV-org playlist entry. It is deliberately separate from Jio channel/account data. */
public final class IptvChannel {
    public int number;
    public String id = "";
    public String name = "";
    public String group = "Other";
    public String logoUrl = "";
    public String streamUrl = "";
    public String userAgent = "";
    public String referrer = "";

    public String displayNumber() {
        return number < 1000 ? String.format(Locale.US, "%03d", number) : String.valueOf(number);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("number", number);
        value.put("id", id);
        value.put("name", name);
        value.put("group", group);
        value.put("logoUrl", logoUrl);
        value.put("streamUrl", streamUrl);
        value.put("userAgent", userAgent);
        value.put("referrer", referrer);
        return value;
    }

    public static IptvChannel fromJson(JSONObject value) {
        IptvChannel channel = new IptvChannel();
        channel.number = value.optInt("number", 0);
        channel.id = value.optString("id", "");
        channel.name = value.optString("name", "Unknown channel");
        channel.group = value.optString("group", "Other");
        channel.logoUrl = value.optString("logoUrl", "");
        channel.streamUrl = value.optString("streamUrl", "");
        channel.userAgent = value.optString("userAgent", "");
        channel.referrer = value.optString("referrer", "");
        return channel;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof IptvChannel)) return false;
        IptvChannel channel = (IptvChannel) other;
        return Objects.equals(id, channel.id) && Objects.equals(streamUrl, channel.streamUrl);
    }

    @Override public int hashCode() { return Objects.hash(id, streamUrl); }
}
