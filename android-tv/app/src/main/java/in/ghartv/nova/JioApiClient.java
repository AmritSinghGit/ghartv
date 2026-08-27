package in.ghartv.nova;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class JioApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String MOBILE_USER_AGENT = "okhttp/4.2.2";
    private static final String PLAYER_USER_AGENT = "plaYtv/7.1.5 (Linux;Android 9) ExoPlayerLib/2.11.7";

    private final Context context;
    private final OkHttpClient client;

    public JioApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(18, TimeUnit.SECONDS)
                .writeTimeout(18, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();
    }

    public void prewarm() {
        Request request = new Request.Builder().url("https://jiotvapi.media.jio.com/").head().build();
        try (Response ignored = client.newCall(request).execute()) {
            // A non-2xx response is fine: the TLS connection has still been warmed.
        } catch (Exception ignored) {}
    }

    public void sendOtp(String mobile) throws Exception {
        String normalized = normalizeMobile(mobile);
        JSONObject body = new JSONObject();
        body.put("number", Base64.encodeToString(("+91" + normalized).getBytes(StandardCharsets.US_ASCII), Base64.NO_WRAP));
        Request request = new Request.Builder()
                .url(AppConfig.OTP_SEND)
                .headers(new Headers.Builder()
                        .add("User-Agent", MOBILE_USER_AGENT)
                        .add("os", "android")
                        .add("devicetype", "phone")
                        .add("appname", "RJIL_JioTV")
                        .build())
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 204 && !response.isSuccessful()) {
                throw new IOException(messageFrom(response, "Jio did not send the OTP"));
            }
        }
    }

    public JioSession verifyOtp(String mobile, String otp) throws Exception {
        String normalized = normalizeMobile(mobile);
        JSONObject platform = new JSONObject();
        platform.put("name", "generic_x86");
        JSONObject info = new JSONObject();
        info.put("type", "android");
        info.put("platform", platform);
        info.put("androidId", UUID.randomUUID().toString());
        JSONObject deviceInfo = new JSONObject();
        deviceInfo.put("consumptionDeviceName", "unknown sdk_google_atv_x86");
        deviceInfo.put("info", info);
        JSONObject body = new JSONObject();
        body.put("number", Base64.encodeToString(("+91" + normalized).getBytes(StandardCharsets.US_ASCII), Base64.NO_WRAP));
        body.put("otp", otp);
        body.put("deviceInfo", deviceInfo);

        Request request = new Request.Builder()
                .url(AppConfig.OTP_VERIFY)
                .headers(new Headers.Builder()
                        .add("User-Agent", MOBILE_USER_AGENT)
                        .add("os", "android")
                        .add("devicetype", "phone")
                        .add("appname", "RJIL_JioTV")
                        .build())
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            JSONObject payload = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            if (!response.isSuccessful() || payload.optString("ssoToken", "").isEmpty()) {
                throw new IOException(payload.optString("message", "Jio rejected the OTP"));
            }
            JSONObject user = payload.optJSONObject("sessionAttributes") == null
                    ? null : payload.optJSONObject("sessionAttributes").optJSONObject("user");
            JioSession session = new JioSession();
            session.mobile = normalized;
            session.ssoToken = payload.optString("ssoToken", "");
            session.authToken = payload.optString("authToken", "");
            session.refreshToken = payload.optString("refreshToken", "");
            session.deviceId = payload.optString("deviceId", UUID.randomUUID().toString());
            if (user != null) {
                session.userId = user.optString("uid", "");
                session.uniqueId = user.optString("unique", session.deviceId);
                session.subscriberId = user.optString("subscriberId", "");
            }
            long exp = JioSession.jwtExpiry(session.authToken);
            session.expiryEpochSeconds = exp > 0 ? exp : (System.currentTimeMillis() / 1000L) + 864000L;
            session.save(context);
            return session;
        }
    }

    public JioSession refreshSession(JioSession session) throws Exception {
        JioSession refreshed = refreshAuthToken(session);
        if (refreshed.isValid()) return refreshed;
        if (refreshSsoToken(refreshed)) refreshed = refreshAuthToken(refreshed);
        return refreshed;
    }

    private JioSession refreshAuthToken(JioSession session) throws Exception {
        if (session.refreshToken.isEmpty()) return session;
        JSONObject body = new JSONObject();
        body.put("appName", "RJIL_JioTV");
        body.put("deviceId", session.deviceId);
        body.put("refreshToken", session.refreshToken);
        Headers headers = new Headers.Builder()
                .add("accesstoken", value(session.authToken))
                .add("uniqueid", value(session.uniqueId))
                .add("Content-Type", "application/json")
                .add("user-agent", "JioTV")
                .add("os", "android")
                .add("devicetype", "phone")
                .add("versioncode", "396")
                .build();
        Request request = new Request.Builder().url(AppConfig.TOKEN_REFRESH)
                .headers(headers).post(RequestBody.create(body.toString(), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return session;
            JSONObject payload = new JSONObject(response.body() == null ? "{}" : response.body().string());
            String auth = payload.optString("authToken", "");
            if (auth.isEmpty()) return session;
            session.authToken = auth;
            session.refreshToken = payload.optString("refreshToken", session.refreshToken);
            long exp = JioSession.jwtExpiry(auth);
            session.expiryEpochSeconds = exp > 0 ? exp : (System.currentTimeMillis() / 1000L) + 864000L;
            session.save(context);
            return session;
        }
    }

    private boolean refreshSsoToken(JioSession session) throws Exception {
        if (session.ssoToken.isEmpty()) return false;
        Headers headers = new Headers.Builder()
                .add("deviceid", value(session.deviceId))
                .add("ssotoken", value(session.ssoToken))
                .add("uniqueid", value(session.uniqueId))
                .add("User-Agent", "JioTV")
                .add("os", "android")
                .add("devicetype", "phone")
                .add("versioncode", "396")
                .build();
        Request request = new Request.Builder().url(AppConfig.SSO_REFRESH).headers(headers).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return false;
            JSONObject payload = new JSONObject(response.body() == null ? "{}" : response.body().string());
            String sso = payload.optString("ssoToken", "");
            if (sso.isEmpty()) return false;
            session.ssoToken = sso;
            session.save(context);
            return true;
        }
    }

    public List<Channel> fetchChannels() throws Exception {
        Map<String, String> categories = new HashMap<>();
        Map<String, String> languages = new HashMap<>();
        try {
            JSONObject dictionary = fetchJson(AppConfig.DICTIONARY,
                    new Headers.Builder().add("User-Agent", MOBILE_USER_AGENT).build());
            categories.putAll(stringMap(dictionary.optJSONObject("channelCategoryMapping")));
            languages.putAll(stringMap(dictionary.optJSONObject("languageIdMapping")));
        } catch (Exception ignored) {
            // Catalogue playback does not depend on the optional display dictionary.
        }

        LinkedHashMap<String, JSONObject> merged = new LinkedHashMap<>();
        Headers catalogueHeaders = new Headers.Builder().add("User-Agent", MOBILE_USER_AGENT).build();
        mergeChannelResponse(merged, fetchJson(AppConfig.CHANNELS_V14, catalogueHeaders));
        try {
            mergeChannelResponse(merged, fetchJson(AppConfig.CHANNELS_V31, catalogueHeaders));
        } catch (Exception ignored) {
            // The v1.4 catalogue remains usable if the secondary feed is unavailable.
        }

        List<Channel> channels = new ArrayList<>();
        Set<Integer> usedNumbers = new HashSet<>();
        int nextAvailable = 1;
        for (JSONObject raw : merged.values()) {
            if (!raw.optString("channelIdForRedirect", "").isEmpty()) continue;
            Channel channel = new Channel();
            channel.id = raw.optString("channel_id", raw.optString("channelId", ""));
            if (channel.id.isEmpty()) continue;
            channel.name = raw.optString("channel_name", raw.optString("channelName", "Unknown channel"));
            int order = raw.optInt("channel_order", raw.optInt("channelOrder", -1));
            int requested = order >= 0 ? order + 1 : nextAvailable;
            if (requested <= 0 || requested > AppConfig.MAX_CHANNEL_NUMBER || usedNumbers.contains(requested)) {
                while (usedNumbers.contains(nextAvailable) && nextAvailable <= AppConfig.MAX_CHANNEL_NUMBER) nextAvailable++;
                requested = nextAvailable;
            }
            if (requested <= 0 || requested > AppConfig.MAX_CHANNEL_NUMBER) break;
            channel.number = requested;
            usedNumbers.add(requested);
            nextAvailable = Math.max(nextAvailable, requested + 1);

            String categoryId = stringValue(raw, "channelCategoryId", "channel_category_id", "-1");
            String languageId = stringValue(raw, "channelLanguageId", "channel_language_id", "6");
            channel.languageId = languageId;
            channel.category = categories.getOrDefault(categoryId,
                    raw.optString("channelCategoryName", raw.optString("categoryName", "Other")));
            channel.language = languages.getOrDefault(languageId,
                    raw.optString("channelLanguageName", raw.optString("languageName", "Other")));
            String logo = raw.optString("logoUrl", "");
            channel.logoUrl = logo.startsWith("http") ? logo : AppConfig.LOGO_BASE + logo;
            channel.catchupAvailable = raw.optBoolean("isCatchupAvailable", false);
            channel.businessType = raw.optString("business_type", raw.optString("businessType", ""));
            channel.requiresSubscription = "premium".equalsIgnoreCase(channel.businessType.trim());
            if (channel.requiresSubscription) {
                channel.accessState = Channel.ACCESS_SUBSCRIPTION;
                channel.accessMessage = "This channel may require a separate JioTV subscription.";
            }
            channels.add(channel);
        }
        return channels;
    }

    public List<Program> fetchEpg(String channelId, int offsetDays) throws Exception {
        String url = String.format(AppConfig.EPG, offsetDays, channelId);
        JSONObject payload = fetchJson(url, new Headers.Builder().add("User-Agent", MOBILE_USER_AGENT).build());
        JSONArray epg = payload.optJSONArray("epg");
        List<Program> programs = new ArrayList<>();
        if (epg == null) return programs;
        for (int i = 0; i < epg.length(); i++) programs.add(Program.fromJson(epg.getJSONObject(i)));
        return programs;
    }

    public PlaybackInfo fetchPlayback(Channel channel) throws Exception {
        if (channel == null || channel.id.isEmpty()) throw new IOException("JioTV channel information is missing");
        JioSession session = JioSession.load(context);
        if (!session.isPresent()) throw new IOException("Sign in to JioTV first");
        if (!session.isValid()) session = refreshSession(session);
        if (!session.isValid()) {
            PlaybackInfo info = new PlaybackInfo();
            info.authRequired = true;
            info.message = "The JioTV session has expired. Reconnect the account once.";
            return info;
        }
        return fetchJioPlayback(channel, session, true);
    }

    private PlaybackInfo fetchJioPlayback(Channel channel, JioSession session, boolean retryAuth) throws Exception {
        Headers headers = playbackHeaders(session, channel);
        RequestBody form = new FormBody.Builder()
                .add("stream_type", "Seek")
                .add("channel_id", channel.id)
                .build();
        Request request = new Request.Builder().url(AppConfig.PLAYBACK).headers(headers).post(form).build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            String raw = response.body() == null ? "" : response.body().string();
            if ((code == 401 || code == 419 || code == 403) && retryAuth) {
                try {
                    JioSession refreshed = refreshSession(session);
                    return fetchJioPlayback(channel, refreshed, false);
                } catch (Exception ignored) {
                    // Classify the original response below instead of losing its channel-specific status.
                }
            }
            if (!response.isSuccessful()) return playbackFailure(channel, code, raw);

            JSONObject payload = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            PlaybackInfo info = new PlaybackInfo();
            info.responseCode = code;
            JSONObject mpd = payload.optJSONObject("mpd");
            JSONObject m3u8 = payload.optJSONObject("m3u8");
            JSONObject topBitrates = payload.optJSONObject("bitrates");
            JSONObject mpdBitrates = mpd == null ? null : mpd.optJSONObject("bitrates");

            String dash = firstNonEmpty(
                    mpd == null ? "" : mpd.optString("result", ""),
                    mpdBitrates == null ? "" : mpdBitrates.optString("auto", ""),
                    mpd == null ? "" : mpd.optString("auto", ""),
                    mpdBitrates == null ? "" : mpdBitrates.optString("high", ""));
            String hls = firstNonEmpty(
                    payload.optString("result", ""),
                    m3u8 == null ? "" : m3u8.optString("auto", ""),
                    topBitrates == null ? "" : topBitrates.optString("auto", ""),
                    m3u8 == null ? "" : m3u8.optString("high", ""));

            String responseText = (raw + " " + payload.optString("message", "")).toLowerCase();
            if ((hls + dash).toLowerCase().contains("paywall") || looksLikeSubscription(responseText)) {
                info.subscriptionRequired = true;
                info.message = playbackMessage(raw,
                        "This channel requires a separate JioTV subscription on the connected account.");
                return info;
            }

            info.streamUrl = dash.isEmpty() ? hls : dash;
            if (info.streamUrl.isEmpty()) {
                info.unavailable = true;
                info.message = playbackMessage(raw,
                        "Jio did not return a playable live stream for this channel.");
                return info;
            }
            info.mimeType = dash.isEmpty() ? "application/x-mpegURL" : "application/dash+xml";
            info.licenseUrl = firstNonEmpty(
                    payload.optString("keyUrl", ""),
                    mpd == null ? "" : mpd.optString("key", ""));
            info.drm = !info.licenseUrl.isEmpty();
            info.streamHeaders = toJson(headers);
            info.streamHeaders.put("User-Agent", PLAYER_USER_AGENT);
            String cookie = signedCookie(info.streamUrl);
            if (!dash.isEmpty()) cookie = combineCookies(cookie, fetchManifestCookies(info.streamUrl, info.streamHeaders));
            if (!cookie.isEmpty()) info.streamHeaders.put("Cookie", cookie);
            info.licenseHeaders = new JSONObject(info.streamHeaders.toString());
            info.licenseHeaders.put("Content-Type", "application/octet-stream");
            info.licenseHeaders.put("appName", "RJIL_JioTV");
            info.licenseHeaders.put("x-platform", "android");
            info.licenseHeaders.put("os", "android");
            info.licenseHeaders.put("devicetype", "phone");
            info.licenseHeaders.put("versionCode", "389");
            info.licenseHeaders.put("srno", UUID.randomUUID().toString());
            info.licenseHeaders.put("channelid", channel.id);
            return info;
        }
    }

    private PlaybackInfo playbackFailure(Channel channel, int code, String raw) {
        PlaybackInfo info = new PlaybackInfo();
        info.responseCode = code;
        String detected = playbackMessage(raw, "Jio playback request failed.");
        String combined = (raw + " " + detected).toLowerCase();
        if (code == 401 || code == 419) {
            info.authRequired = true;
            info.message = "The Jio session was not accepted. Reconnect the account once and try again.";
        } else if (channel.requiresSubscription || looksLikeSubscription(combined)) {
            info.subscriptionRequired = true;
            info.message = "Jio playback request failed.".equals(detected)
                    ? "This channel requires a separate JioTV subscription on the connected account."
                    : detected;
        } else {
            info.unavailable = true;
            if (code == 403) {
                info.message = "Jio is refusing this channel (HTTP 403). This usually means the channel or this exact feed is not currently available to JioTV mobile, rather than an internet or login problem.";
            } else {
                info.message = detected + " (HTTP " + code + ")";
            }
        }
        return info;
    }

    private boolean looksLikeSubscription(String value) {
        String text = value == null ? "" : value.toLowerCase();
        return text.contains("no eligible plan")
                || text.contains("subscription required")
                || text.contains("subscribe")
                || text.contains("paywall")
                || text.contains("not entitled")
                || text.contains("not subscribed")
                || text.contains("premium plan");
    }

    private String playbackMessage(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try {
            JSONObject payload = new JSONObject(raw);
            String message = firstNonEmpty(
                    payload.optString("message", ""),
                    payload.optString("Message", ""),
                    payload.optString("error", ""),
                    payload.optString("error_description", ""));
            if (!message.isEmpty()) return message;
            JSONArray errors = payload.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                JSONObject last = errors.optJSONObject(errors.length() - 1);
                if (last != null) {
                    message = firstNonEmpty(last.optString("message", ""), last.optString("description", ""));
                    if (!message.isEmpty()) return message;
                }
            }
        } catch (Exception ignored) {}
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 180) trimmed = trimmed.substring(0, 180) + "…";
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private Headers playbackHeaders(JioSession session, Channel channel) {
        return new Headers.Builder()
                .add("Appkey", "NzNiMDhlYzQyNjJm")
                .add("Devicetype", "phone")
                .add("Os", "android")
                .add("Deviceid", value(session.deviceId))
                .add("Osversion", "13")
                .add("Dm", "Google Pixel 5")
                .add("Uniqueid", value(session.uniqueId.isEmpty() ? session.deviceId : session.uniqueId))
                .add("Usergroup", "tvYR7NSNn7rymo3F")
                .add("Languageid", value(channel.languageId.isEmpty() ? "6" : channel.languageId))
                .add("Userid", value(session.userId))
                .add("Sid", UUID.randomUUID().toString())
                .add("Crmid", value(session.subscriberId))
                .add("Isott", "false")
                .add("Channel_id", channel.id)
                .add("Langid", value(channel.languageId))
                .add("Camid", "")
                .add("ssoToken", value(session.ssoToken))
                .add("Accesstoken", value(session.authToken))
                .add("Subscriberid", value(session.subscriberId))
                .add("analyticsId", value(session.deviceId))
                .add("Lbcookie", "1")
                .add("Versioncode", "389")
                .add("Content-Type", "application/x-www-form-urlencoded")
                .add("User-Agent", MOBILE_USER_AGENT)
                .build();
    }

    private JSONObject fetchJson(String url, Headers headers) throws Exception {
        Request request = new Request.Builder().url(url).headers(headers).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " for " + url);
            return new JSONObject(response.body() == null ? "{}" : response.body().string());
        }
    }

    private void mergeChannelResponse(Map<String, JSONObject> merged, JSONObject payload) {
        JSONArray result = payload.optJSONArray("result");
        if (result == null) return;
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("channel_id", item.optString("channelId", ""));
            if (id.isEmpty()) continue;
            JSONObject existing = merged.get(id);
            if (existing == null) {
                try { merged.put(id, new JSONObject(item.toString())); }
                catch (Exception ignored) { merged.put(id, item); }
                continue;
            }
            java.util.Iterator<String> keys = item.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object current = existing.opt(key);
                boolean missing = current == null || JSONObject.NULL.equals(current)
                        || (current instanceof String && ((String) current).trim().isEmpty());
                if (!missing) continue;
                try { existing.put(key, item.opt(key)); }
                catch (Exception ignored) {}
            }
        }
    }

    private Map<String, String> stringMap(JSONObject object) {
        Map<String, String> map = new HashMap<>();
        if (object == null) return map;
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, object.optString(key, "Other"));
        }
        return map;
    }

    private String normalizeMobile(String input) {
        String digits = input == null ? "" : input.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) digits = digits.substring(2);
        if (digits.length() != 10) throw new IllegalArgumentException("Enter a valid 10-digit Jio mobile number");
        return digits;
    }

    private String messageFrom(Response response, String fallback) throws IOException {
        String body = response.body() == null ? "" : response.body().string();
        try {
            JSONObject object = new JSONObject(body);
            String message = object.optString("message", "");
            if (!message.isEmpty()) return message;
            JSONArray errors = object.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                JSONObject last = errors.optJSONObject(errors.length() - 1);
                if (last != null) return last.optString("message", fallback);
            }
        } catch (Exception ignored) {}
        return fallback + " (HTTP " + response.code() + ")";
    }

    private JSONObject toJson(Headers headers) {
        JSONObject out = new JSONObject();
        try {
            for (String name : headers.names()) out.put(name, headers.get(name));
        } catch (Exception ignored) {}
        return out;
    }

    private String signedCookie(String url) {
        int index = url.indexOf("__hdnea__");
        if (index < 0) return "";
        String value = url.substring(index);
        int amp = value.indexOf('&');
        if (amp >= 0) value = value.substring(0, amp);
        return value;
    }

    private String stringValue(JSONObject object, String primary, String secondary, String fallback) {
        Object value = object.opt(primary);
        if (value == null || JSONObject.NULL.equals(value)) value = object.opt(secondary);
        if (value == null || JSONObject.NULL.equals(value)) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String fetchManifestCookies(String url, JSONObject requestHeaders) {
        Headers.Builder headers = new Headers.Builder();
        java.util.Iterator<String> keys = requestHeaders.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = requestHeaders.optString(key, "");
            if (!value.isEmpty() && !"Host".equalsIgnoreCase(key)) headers.add(key, value);
        }
        Request request = new Request.Builder().url(url).headers(headers.build()).get().build();
        try (Response response = client.newCall(request).execute()) {
            List<String> setCookies = response.headers("Set-Cookie");
            StringBuilder cookie = new StringBuilder();
            for (String setCookie : setCookies) {
                String pair = setCookie.split(";", 2)[0].trim();
                if (pair.isEmpty()) continue;
                if (cookie.length() > 0) cookie.append("; ");
                cookie.append(pair);
            }
            return cookie.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String combineCookies(String first, String second) {
        if (first == null || first.isEmpty()) return second == null ? "" : second;
        if (second == null || second.isEmpty()) return first;
        if (second.contains(first.split("=", 2)[0] + "=")) return second;
        return first + "; " + second;
    }

    private String value(String value) { return value == null ? "" : value; }
}
