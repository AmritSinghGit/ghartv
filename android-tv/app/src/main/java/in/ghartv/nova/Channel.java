package in.ghartv.nova;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

public final class Channel {
    public int number;
    public String id = "";
    public String name = "";
    public String category = "Other";
    public String language = "Other";
    public String languageId = "6";
    public String logoUrl = "";
    public boolean catchupAvailable;
    public String nowTitle = "Live now";
    public String nextTitle = "";

    public String displayNumber() {
        if (number < 1000) return String.format(Locale.US, "%03d", number);
        return String.valueOf(number);
    }

    public boolean isJio() { return true; }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("number", number);
        object.put("id", id);
        object.put("name", name);
        object.put("category", category);
        object.put("language", language);
        object.put("languageId", languageId);
        object.put("logoUrl", logoUrl);
        object.put("catchupAvailable", catchupAvailable);
        object.put("nowTitle", nowTitle);
        object.put("nextTitle", nextTitle);
        return object;
    }

    public static Channel fromJson(JSONObject object) {
        Channel channel = new Channel();
        channel.number = object.optInt("number", 0);
        channel.id = object.optString("id", object.optString("channel_id", ""));
        channel.name = object.optString("name", object.optString("channel_name", "Unknown channel"));
        channel.category = object.optString("category", "Other");
        channel.language = object.optString("language", "Other");
        channel.languageId = object.optString("languageId", object.optString("channelLanguageId", "6"));
        channel.logoUrl = object.optString("logoUrl", "");
        channel.catchupAvailable = object.optBoolean("catchupAvailable", object.optBoolean("isCatchupAvailable", false));
        channel.nowTitle = object.optString("nowTitle", "Live now");
        channel.nextTitle = object.optString("nextTitle", "");
        return channel;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Channel)) return false;
        Channel channel = (Channel) other;
        return number == channel.number && Objects.equals(id, channel.id);
    }

    @Override public int hashCode() { return Objects.hash(number, id); }
}
