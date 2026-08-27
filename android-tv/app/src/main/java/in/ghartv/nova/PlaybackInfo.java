package in.ghartv.nova;

import org.json.JSONObject;

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
}
