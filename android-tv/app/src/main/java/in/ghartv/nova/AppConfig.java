package in.ghartv.nova;

public final class AppConfig {
    private AppConfig() {}

    public static final String APP_NAME = "GharTV Jio Live";
    public static final String PREFS = "ghartv_nova";
    public static final String KEY_FAVOURITES = "favourites";
    public static final String KEY_LAST_CHANNEL = "last_channel";
    public static final String KEY_LAST_CATEGORY = "last_category";
    public static final String KEY_LAST_UPDATE_CHECK = "last_update_check";

    public static final String CHANNELS_V14 = "https://jiotvapi.cdn.jio.com/apis/v1.4/getMobileChannelList/get/?langId=6&devicetype=phone&os=android&usertype=JIO&version=396";
    public static final String CHANNELS_V31 = "https://jiotvapi.cdn.jio.com/apis/v3.1/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=JIO&version=389";
    public static final String DICTIONARY = "https://jiotvapi.cdn.jio.com/apis/v1.3/dictionary/dictionary?langId=6";
    public static final String EPG = "https://jiotvapi.cdn.jio.com/apis/v1.3/getepg/get?offset=%d&channel_id=%s&langId=6";
    public static final String OTP_SEND = "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send";
    public static final String OTP_VERIFY = "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify";
    public static final String PLAYBACK = "https://jiotvapi.media.jio.com/playback/apis/v1.1/geturl";
    public static final String TOKEN_REFRESH = "https://auth.media.jio.com/tokenservice/apis/v1/refreshtoken?langId=6";
    public static final String SSO_REFRESH = "https://tv.media.jio.com/apis/v2.0/loginotp/refresh?langId=6";
    public static final String LOGO_BASE = "https://jiotv.catchup.cdn.jio.com/dare_images/images/";

    public static final String GITHUB_REPOSITORY = "https://github.com/AmritSinghGit/ghartv";
    public static final String INSTALL_PAGE = "https://amritsinghgit.github.io/ghartv/";
    public static final String UPDATE_MANIFEST = "https://raw.githubusercontent.com/AmritSinghGit/ghartv/main/update/latest.json";

    public static final int MAX_CHANNEL_NUMBER = 9999;
    public static final long CATALOGUE_REFRESH_MS = 24L * 60L * 60L * 1000L;
    public static final long UPDATE_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;
}
