package in.ghartv.nova;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
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
    private static final OkHttpClient SHARED = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS).callTimeout(16, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true).connectionPool(new ConnectionPool(8,5,TimeUnit.MINUTES)).build();
    private final OkHttpClient client = SHARED;
    private final java.util.Set<okhttp3.Call> playbackCalls = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long PLAYBACK_HANDOFF_MS = 20_000L;
    private static final java.util.LinkedHashMap<String, CachedPlayback> PLAYBACK_HANDOFFS =
            new java.util.LinkedHashMap<>(8, .75f, true);
    private static final class CachedPlayback {
        final long until;
        final PlaybackInfo info;
        CachedPlayback(PlaybackInfo value) {
            until = android.os.SystemClock.elapsedRealtime() + PLAYBACK_HANDOFF_MS;
            info = value.copy();
        }
    }
    public JioApiClient(Context context) { this.context=context.getApplicationContext(); }
    public void cancelPlayback() { for(okhttp3.Call c:playbackCalls)c.cancel(); }
    public static void clearPlaybackHandoffs() { synchronized (PLAYBACK_HANDOFFS) { PLAYBACK_HANDOFFS.clear(); } }

    private static String handoffKey(JioSession session, Channel channel) {
        String owner = session.uniqueId.isEmpty() ? session.deviceId : session.uniqueId;
        return Integer.toHexString(owner.hashCode()) + ":" + channel.id;
    }

    private static PlaybackInfo takePlaybackHandoff(JioSession session, Channel channel) {
        synchronized (PLAYBACK_HANDOFFS) {
            long now = android.os.SystemClock.elapsedRealtime();
            PLAYBACK_HANDOFFS.entrySet().removeIf(entry -> entry.getValue().until <= now);
            CachedPlayback cached = PLAYBACK_HANDOFFS.remove(handoffKey(session, channel));
            if (cached == null || cached.until <= now) return null;
            PlaybackInfo info = cached.info.copy();
            try { info.licenseHeaders.put("srno", UUID.randomUUID().toString()); }
            catch (Exception ignored) {}
            return info;
        }
    }

    private static void rememberPlaybackHandoff(JioSession session, Channel channel, PlaybackInfo info) {
        if (info == null || info.authRequired || info.subscriptionRequired || info.unavailable
                || info.streamUrl == null || info.streamUrl.isEmpty()) return;
        synchronized (PLAYBACK_HANDOFFS) {
            PLAYBACK_HANDOFFS.put(handoffKey(session, channel), new CachedPlayback(info));
            while (PLAYBACK_HANDOFFS.size() > 8) {
                PLAYBACK_HANDOFFS.remove(PLAYBACK_HANDOFFS.keySet().iterator().next());
            }
        }
    }
    private final class TrackedPlayback implements java.io.Closeable {
        final okhttp3.Call call; final Response response;
        TrackedPlayback(okhttp3.Call c,Response r){call=c;response=r;}
        @Override public void close(){try{response.close();}finally{playbackCalls.remove(call);}}
    }
    private TrackedPlayback playbackExecute(Request request) throws IOException {
        if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Superseded request");
        okhttp3.Call call=client.newCall(request);playbackCalls.add(call);
        try {if(Thread.currentThread().isInterrupted())call.cancel();return new TrackedPlayback(call,call.execute());}
        catch(IOException | RuntimeException e){playbackCalls.remove(call);throw e;}
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
        if (refreshSsoToken(refreshed)) {
            refreshed = refreshAuthToken(refreshed);
        }
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
            Channel c = new Channel();
            c.id = raw.optString("channel_id", raw.optString("channelId", ""));
            if (c.id.isEmpty()) continue;
            c.name = raw.optString("channel_name", raw.optString("channelName", "Unknown channel"));
            int order = raw.optInt("channel_order", raw.optInt("channelOrder", -1));
            int requested = order >= 0 ? order + 1 : nextAvailable;
            if (requested <= 0 || requested > AppConfig.MAX_CHANNEL_NUMBER || usedNumbers.contains(requested)) {
                while (usedNumbers.contains(nextAvailable) && nextAvailable <= AppConfig.MAX_CHANNEL_NUMBER) nextAvailable++;
                requested = nextAvailable;
            }
            if (requested <= 0 || requested > AppConfig.MAX_CHANNEL_NUMBER) break;
            c.number = requested;
            usedNumbers.add(requested);
            nextAvailable = Math.max(nextAvailable, requested + 1);

            String categoryId = stringValue(raw, "channelCategoryId", "channel_category_id", "-1");
            String languageId = stringValue(raw, "channelLanguageId", "channel_language_id", "6");
            c.languageId = languageId;
            c.category = categories.getOrDefault(categoryId,
                    raw.optString("channelCategoryName", raw.optString("categoryName", "Other")));
            c.language = languages.getOrDefault(languageId,
                    raw.optString("channelLanguageName", raw.optString("languageName", "Other")));
            String logo = raw.optString("logoUrl", "");
            c.logoUrl = logo.startsWith("http") ? logo : AppConfig.LOGO_BASE + logo;
            c.catchupAvailable = raw.optBoolean("isCatchupAvailable", false);
            c.businessType = raw.optString("businessType", raw.optString("business_type", ""));
            c.requiresSubscription = premiumHint(raw);
            c.subscriptionHint = c.requiresSubscription;
            if (c.requiresSubscription) {
                c.accessState = Channel.ACCESS_SUBSCRIPTION;
                c.accessMessage = "This channel may require an active JioTV subscription.";
                c.accessUpdatedAt = System.currentTimeMillis();
            }
            channels.add(c);
        }
        return channels;
    }

    private static final java.util.LinkedHashMap<String,EpgEntry> EPG_CACHE=new java.util.LinkedHashMap<>(32,.75f,true);
    private static final class EpgEntry {final long until;final List<Program> programs;EpgEntry(List<Program> p){until=android.os.SystemClock.elapsedRealtime()+60000;programs=new ArrayList<>(p);}}
    public List<Program> fetchEpg(String channelId, int offsetDays) throws Exception {
        String cacheKey=channelId+":"+offsetDays;
        synchronized(EPG_CACHE){EpgEntry e=EPG_CACHE.get(cacheKey);if(e!=null&&e.until>android.os.SystemClock.elapsedRealtime())return new ArrayList<>(e.programs);}

        String url = String.format(AppConfig.EPG, offsetDays, channelId);
        JSONObject payload = fetchJson(url, new Headers.Builder().add("User-Agent", MOBILE_USER_AGENT).build());
        JSONArray epg = payload.optJSONArray("epg");
        List<Program> programs = new ArrayList<>();
        if (epg == null) return programs;
        for (int i = 0; i < epg.length(); i++) {
            JSONObject row=epg.optJSONObject(i);if(row==null)continue;
            Program p=Program.fromJson(row);if(p.startEpochMs>0&&p.endEpochMs>p.startEpochMs)programs.add(p);
        }
        programs.sort((a,b)->Long.compare(a.startEpochMs,b.startEpochMs));
        synchronized(EPG_CACHE){EPG_CACHE.put(cacheKey,new EpgEntry(programs));while(EPG_CACHE.size()>32)EPG_CACHE.remove(EPG_CACHE.keySet().iterator().next());}
        return programs;
    }

    public PlaybackInfo fetchPlayback(Channel channel) throws Exception {
        if (channel == null || channel.id.isEmpty()) throw new IOException("JioTV channel information is missing");
        JioSession session = JioSession.load(context);
        if (!session.isPresent()) throw new IOException("Sign in to JioTV first");
        if (!session.isValid()) session = refreshSession(session);
        if (!session.isPresent()) throw new IOException("The JioTV session has expired. Sign in again.");
        PlaybackInfo cached = takePlaybackHandoff(session, channel);
        if (cached != null) return cached;
        PlaybackInfo resolved = fetchJioPlayback(channel, session, true);
        rememberPlaybackHandoff(session, channel, resolved);
        return resolved;
    }

    private PlaybackInfo fetchJioPlayback(Channel channel, JioSession session, boolean retryAuth) throws Exception {
        Headers headers = playbackHeaders(session, channel);
        RequestBody form = new FormBody.Builder()
                .add("stream_type", "Seek")
                .add("channel_id", channel.id)
                .build();
        Request request = new Request.Builder().url(AppConfig.PLAYBACK).headers(headers).post(form).build();
        try (TrackedPlayback tracked = playbackExecute(request)) {
            Response response=tracked.response;
            int status = response.code();
            String raw = response.body() == null ? "" : response.body().string();

            if (status == 401 || status == 419) {
                if (retryAuth) {
                    JioSession refreshed = refreshSession(session);
                    return fetchJioPlayback(channel, refreshed, false);
                }
                PlaybackInfo auth = new PlaybackInfo();
                auth.statusCode = status;
                auth.responseCode = status;
                auth.authRequired = true;
                auth.message = "The JioTV session could not be refreshed. Reconnect this TV to the Jio account.";
                auth.normalizeAliases();
                return auth;
            }

            if (status == 403) {
                if (retryAuth) {
                    JioSession refreshed = refreshSession(session);
                    return fetchJioPlayback(channel, refreshed, false);
                }
                PlaybackInfo denied = new PlaybackInfo();
                denied.statusCode = status;
                denied.responseCode = status;
                boolean subscription = channel.requiresSubscription || channel.subscriptionHint || looksLikeSubscription(raw);
                denied.subscriptionRequired = subscription;
                denied.accessRestricted = !subscription;
                denied.unavailable = !subscription;
                denied.message = subscription
                        ? "This channel requires a JioTV subscription that is not active for the connected account."
                        : "Jio returned 403 for this channel after refreshing the session. It may be restricted for this account or TV device.";
                String serverMessage = responseMessage(raw, "");
                if (!serverMessage.isEmpty()) denied.message += "\n\nJio: " + serverMessage;
                denied.normalizeAliases();
                return denied;
            }

            if (!response.isSuccessful()) {
                throw new IOException(responseMessage(raw, "Jio playback API returned " + status));
            }

            JSONObject payload;
            try {
                payload = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            } catch (Exception malformed) {
                throw new IOException("Jio returned an unreadable playback response (HTTP " + status + ")", malformed);
            }

            PlaybackInfo info = new PlaybackInfo();
            info.statusCode = status;
            info.responseCode = status;
            String hls = payload.optString("result", "");
            JSONObject mpd = payload.optJSONObject("mpd");
            String dash = mpd == null ? "" : mpd.optString("result", "");
            if (looksLikeSubscription(raw) || (hls + dash).toLowerCase(Locale.ROOT).contains("paywall")) {
                info.subscriptionRequired = true;
                info.message = "This channel requires an active JioTV subscription on the connected account.";
                info.normalizeAliases();
                return info;
            }
            info.streamUrl = dash.isEmpty() ? hls : dash;
            if (info.streamUrl.isEmpty()) {
                String serverMessage = responseMessage(raw, "Jio returned no playable stream URL");
                if (looksLikeSubscription(serverMessage)) {
                    info.subscriptionRequired = true;
                    info.message = serverMessage;
                    info.normalizeAliases();
                    return info;
                }
                throw new IOException(serverMessage);
            }
            info.mimeType = dash.isEmpty() ? "application/x-mpegURL" : "application/dash+xml";
            info.licenseUrl = mpd == null ? "" : mpd.optString("key", "");
            info.drm = !info.licenseUrl.isEmpty();
            info.streamHeaders = toJson(headers);
            info.streamHeaders.put("User-Agent", PLAYER_USER_AGENT);
            String cookie = signedCookie(info.streamUrl);
            if (!dash.isEmpty()) {
                String manifestCookie = fetchManifestCookies(info.streamUrl, info.streamHeaders);
                cookie = combineCookies(cookie, manifestCookie);
            }
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
            info.normalizeAliases();
            return info;
        }
    }

    private Headers playbackHeaders(JioSession s, Channel channel) {
        return new Headers.Builder()
                .add("Appkey", "NzNiMDhlYzQyNjJm")
                .add("Devicetype", "phone")
                .add("Os", "android")
                .add("Deviceid", value(s.deviceId))
                .add("Osversion", "13")
                .add("Dm", "Google Pixel 5")
                .add("Uniqueid", value(s.uniqueId.isEmpty() ? s.deviceId : s.uniqueId))
                .add("Usergroup", "tvYR7NSNn7rymo3F")
                .add("Languageid", value(channel.languageId.isEmpty() ? "6" : channel.languageId))
                .add("Userid", value(s.userId))
                .add("Sid", UUID.randomUUID().toString())
                .add("Crmid", value(s.subscriberId))
                .add("Isott", "false")
                .add("Channel_id", channel.id)
                .add("Langid", value(channel.languageId))
                .add("Camid", "")
                .add("ssoToken", value(s.ssoToken))
                .add("Accesstoken", value(s.authToken))
                .add("Subscriberid", value(s.subscriberId))
                .add("analyticsId", value(s.deviceId))
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
            if (!id.isEmpty()) merged.putIfAbsent(id, item);
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
            JSONObject o = new JSONObject(body);
            String message = o.optString("message", "");
            if (!message.isEmpty()) return message;
            JSONArray errors = o.optJSONArray("errors");
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


    private boolean premiumHint(JSONObject raw) {
        if (raw == null) return false;
        String[] booleanKeys = {
                "isPremium", "isPaid", "premium", "paid", "subscriptionRequired",
                "requiresSubscription", "isSubscription", "isPayChannel", "payChannel"
        };
        for (String key : booleanKeys) {
            if (!raw.has(key)) continue;
            Object value = raw.opt(key);
            if (value instanceof Boolean && (Boolean) value) return true;
            if (value instanceof Number && ((Number) value).intValue() != 0) return true;
            String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) return true;
        }
        if (raw.has("isFree") && !raw.optBoolean("isFree", true)) return true;
        String[] textKeys = {
                "accessType", "payType", "channelType", "entitlementType",
                "subscriptionType", "packageType", "offerType"
        };
        for (String key : textKeys) {
            if (looksLikeSubscription(raw.optString(key, ""))) return true;
        }
        return false;
    }

    private boolean looksLikeSubscription(String value) {
        if (value == null) return false;
        String text = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return text.contains("paywall")
                || text.contains("subscription required")
                || text.contains("subscribe to")
                || text.contains("active subscription")
                || text.contains("not subscribed")
                || text.contains("premium channel")
                || text.contains("paid channel")
                || text.contains("ott pass")
                || text.contains("jiotv pro")
                || text.contains("recharge to watch");
    }

    private String responseMessage(String raw, String fallback) {
        String message = "";
        try {
            JSONObject object = raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
            String[] keys = {"message", "error", "errorMessage", "description", "statusMessage", "reason"};
            for (String key : keys) {
                Object value = object.opt(key);
                if (value == null || JSONObject.NULL.equals(value)) continue;
                if (value instanceof JSONObject) {
                    JSONObject nested = (JSONObject) value;
                    message = nested.optString("message", nested.optString("description", ""));
                } else {
                    message = String.valueOf(value);
                }
                if (!message.trim().isEmpty()) break;
            }
            if (message.trim().isEmpty()) {
                JSONArray errors = object.optJSONArray("errors");
                if (errors != null && errors.length() > 0) {
                    JSONObject last = errors.optJSONObject(errors.length() - 1);
                    if (last != null) message = last.optString("message", last.optString("description", ""));
                }
            }
        } catch (Exception ignored) {}
        message = message == null ? "" : message.trim();
        return message.isEmpty() ? fallback : message;
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
