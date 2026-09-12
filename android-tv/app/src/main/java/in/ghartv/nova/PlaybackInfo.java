package in.ghartv.nova;

import org.json.JSONObject;

/** Normalized result of Jio playback authorization. */
public final class PlaybackInfo {
    public String streamUrl = "";
    public String mimeType = "";
    public String licenseUrl = "";
    public JSONObject streamHeaders = new JSONObject();
    public JSONObject licenseHeaders = new JSONObject();
    public boolean drm;
    public boolean subscriptionRequired;
    public boolean unavailable;
    public boolean authRequired;
    public int responseCode;
    public String message = "";

    /** Compatibility aliases retained for the older direct-Jio parser. */
    public boolean accessRestricted;
    public int statusCode;

    public void normalizeAliases() {
        if (responseCode <= 0 && statusCode > 0) responseCode = statusCode;
        if (statusCode <= 0 && responseCode > 0) statusCode = responseCode;
        if (!unavailable && accessRestricted) unavailable = true;
        if (!accessRestricted && unavailable && !subscriptionRequired && !authRequired) accessRestricted = true;
    }
}
