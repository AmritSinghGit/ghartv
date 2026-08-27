package in.ghartv.nova;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public final class JioSession {
    private static final String STORE_KEY = "jio_session";

    public String mobile = "";
    public String ssoToken = "";
    public String authToken = "";
    public String refreshToken = "";
    public String deviceId = "";
    public String userId = "";
    public String uniqueId = "";
    public String subscriberId = "";
    public long expiryEpochSeconds;

    public boolean isPresent() {
        return !ssoToken.isEmpty() && !authToken.isEmpty() && !deviceId.isEmpty();
    }

    public boolean isValid() {
        return isPresent() && expiryEpochSeconds > (System.currentTimeMillis() / 1000L) + 120L;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("mobile", mobile);
            o.put("ssoToken", ssoToken);
            o.put("authToken", authToken);
            o.put("refreshToken", refreshToken);
            o.put("deviceId", deviceId);
            o.put("userId", userId);
            o.put("uniqueId", uniqueId);
            o.put("subscriberId", subscriberId);
            o.put("expiryEpochSeconds", expiryEpochSeconds);
        } catch (Exception ignored) {}
        return o;
    }

    public static JioSession fromJson(JSONObject o) {
        JioSession s = new JioSession();
        s.mobile = o.optString("mobile", "");
        s.ssoToken = o.optString("ssoToken", "");
        s.authToken = o.optString("authToken", "");
        s.refreshToken = o.optString("refreshToken", "");
        s.deviceId = o.optString("deviceId", "");
        s.userId = o.optString("userId", "");
        s.uniqueId = o.optString("uniqueId", s.deviceId);
        s.subscriberId = o.optString("subscriberId", "");
        s.expiryEpochSeconds = o.optLong("expiryEpochSeconds", 0L);
        return s;
    }

    public static JioSession load(Context context) {
        try {
            String raw = SecureStore.get(context, STORE_KEY);
            if (raw.isEmpty()) return new JioSession();
            return fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return new JioSession();
        }
    }

    public void save(Context context) throws Exception {
        SecureStore.put(context, STORE_KEY, toJson().toString());
    }

    public static void clear(Context context) {
        SecureStore.remove(context, STORE_KEY);
    }

    public static long jwtExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return 0L;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            return payload.optLong("exp", 0L);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
