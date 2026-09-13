package in.ghartv.nova;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A muted, decoder-bounded guide preview. Focus must remain on one channel before
 * playback begins. Moving focus or leaving the guide releases the decoder at once.
 * The PlayerView always preserves source proportions; 4:3 video is never stretched.
 */
@UnstableApi
public final class HeroPreviewController {
    private static final long AUTO_START_DELAY_MS = 1_200L;
    private static final long MAX_PREVIEW_MS = 20_000L;

    private final Activity activity;
    private final ChannelRepository repository;
    private final PlayerView playerView;
    private final ImageView poster;
    private final ProgressBar loading;
    private final TextView status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ExoPlayer player;
    private Channel selected;
    private int generation;
    private Runnable pendingStart;
    private Runnable autoStop;

    public HeroPreviewController(Activity activity, ChannelRepository repository,
                                 PlayerView playerView, ImageView poster,
                                 ProgressBar loading, TextView status) {
        this.activity = activity;
        this.repository = repository;
        this.playerView = playerView;
        this.poster = poster;
        this.loading = loading;
        this.status = status;
        playerView.setUseController(false);
        playerView.setKeepScreenOn(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        updateIdleCopy();
    }

    public void select(Channel channel) {
        boolean changed = selected == null || channel == null
                || selected.number != channel.number
                || !safe(selected.id).equals(safe(channel.id));
        selected = channel;
        if (changed) {
            stop(false);
            scheduleAutoStart();
        } else if (player == null && pendingStart == null) {
            updateIdleCopy();
        }
    }

    private void scheduleAutoStart() {
        Channel channel = selected;
        if (channel == null) {
            updateIdleCopy();
            return;
        }
        if (channel.isSubscriptionChannel() && !channel.isAvailable()) {
            failCopy("Subscription required  •  OK tries full screen", TvUi.AMBER);
            return;
        }
        if (channel.isUnavailable()) {
            failCopy("Preview unavailable  •  OK retries full screen", TvUi.ERROR);
            return;
        }
        status.setTextColor(Color.WHITE);
        status.setText("Preview starting…  •  OK opens full screen");
        final int scheduledGeneration = generation;
        pendingStart = () -> {
            pendingStart = null;
            if (scheduledGeneration == generation) start();
        };
        main.postDelayed(pendingStart, AUTO_START_DELAY_MS);
    }

    private void start() {
        Channel channel = selected;
        if (channel == null || safe(channel.id).isEmpty()) return;
        final int currentGeneration = ++generation;
        releasePlayer();
        loading.setVisibility(View.VISIBLE);
        status.setTextColor(Color.WHITE);
        status.setText("Starting muted live preview…");
        Telemetry.event(activity, "guide_preview_request", Telemetry.data(
                "automatic", true,
                "language", channel.language,
                "category", channel.category,
                "access_state", channel.accessState
        ));

        executor.execute(() -> {
            try {
                PlaybackInfo info = repository.api().fetchPlayback(channel);
                main.post(() -> {
                    if (currentGeneration != generation || activity.isFinishing()) return;
                    loading.setVisibility(View.GONE);
                    info.normalizeAliases();
                    if (info.authRequired || info.subscriptionRequired || info.unavailable
                            || safe(info.streamUrl).trim().isEmpty()) {
                        String copy = info.subscriptionRequired
                                ? "Subscription required  •  OK tries full screen"
                                : info.authRequired
                                ? "Reconnect JioTV to preview this channel"
                                : "Preview unavailable  •  OK tries full screen";
                        fail(copy, info.subscriptionRequired ? TvUi.AMBER : TvUi.ERROR);
                        return;
                    }
                    try {
                        prepare(info, channel, currentGeneration);
                    } catch (RuntimeException error) {
                        fail("Preview unavailable  •  OK tries full screen", TvUi.ERROR);
                        Telemetry.error(activity, "guide_preview_prepare", error,
                                Telemetry.data("category", channel.category, "language", channel.language));
                    }
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (currentGeneration != generation || activity.isFinishing()) return;
                    fail("Preview unavailable  •  OK tries full screen", TvUi.ERROR);
                    Telemetry.error(activity, "guide_preview_fetch", error, Telemetry.data(
                            "automatic", true,
                            "language", channel.language,
                            "category", channel.category
                    ));
                });
            }
        });
    }

    private void prepare(PlaybackInfo info, Channel channel, int currentGeneration) {
        Map<String, String> streamHeaders = jsonMap(info.streamHeaders);
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(streamHeaders.getOrDefault("User-Agent", "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME))
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(streamHeaders);
        player = new ExoPlayer.Builder(activity)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();
        player.setVolume(0f);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (currentGeneration != generation) return;
                if (state == Player.STATE_READY) {
                    loading.setVisibility(View.GONE);
                    poster.setVisibility(View.GONE);
                    playerView.setVisibility(View.VISIBLE);
                    status.setTextColor(TvUi.MINT);
                    status.setText("Muted preview  •  proportions preserved  •  OK for full screen");
                    Telemetry.event(activity, "guide_preview_ready", Telemetry.data(
                            "automatic", true,
                            "protocol", protocol(info),
                            "drm", info.drm,
                            "language", channel.language,
                            "category", channel.category
                    ));
                    scheduleStop();
                } else if (state == Player.STATE_BUFFERING) {
                    loading.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_ENDED) {
                    finishBoundedPreview();
                }
            }

            @Override public void onVideoSizeChanged(VideoSize size) {
                if (currentGeneration != generation || size.width <= 0 || size.height <= 0) return;
                double ratio = (double) size.width / (double) size.height;
                String shape = ratio < 1.5d ? "4_3_or_narrow" : "16_9_or_wide";
                Telemetry.event(activity, "guide_preview_video", Telemetry.data(
                        "source_width", size.width,
                        "source_height", size.height,
                        "source_shape", shape,
                        "display_mode", "fit"
                ));
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (currentGeneration != generation) return;
                fail("Preview stopped  •  OK tries full screen", TvUi.ERROR);
                Telemetry.error(activity, "guide_preview_media", error, Telemetry.data(
                        "automatic", true,
                        "protocol", protocol(info),
                        "drm", info.drm
                ));
            }
        });

        MediaItem.Builder item = new MediaItem.Builder().setUri(info.streamUrl);
        String lower = safe(info.streamUrl).toLowerCase(Locale.ROOT);
        String mime = safe(info.mimeType).toLowerCase(Locale.ROOT);
        if (mime.contains("dash") || lower.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        else if (mime.contains("mpegurl") || lower.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if (info.drm && !safe(info.licenseUrl).isEmpty()) {
            item.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(info.licenseUrl)
                    .setLicenseRequestHeaders(jsonMap(info.licenseHeaders))
                    .setMultiSession(false)
                    .build());
        }
        player.setMediaItem(item.build());
        player.prepare();
        player.play();
    }

    private String protocol(PlaybackInfo info) {
        String stream = safe(info.streamUrl).toLowerCase(Locale.ROOT);
        String mime = safe(info.mimeType).toLowerCase(Locale.ROOT);
        return mime.contains("dash") || stream.contains(".mpd") ? "dash" : "hls";
    }

    private void scheduleStop() {
        if (autoStop != null) main.removeCallbacks(autoStop);
        autoStop = this::finishBoundedPreview;
        main.postDelayed(autoStop, MAX_PREVIEW_MS);
    }

    private void finishBoundedPreview() {
        generation++;
        if (autoStop != null) main.removeCallbacks(autoStop);
        autoStop = null;
        releasePlayer();
        loading.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        poster.setVisibility(View.VISIBLE);
        status.setTextColor(TvUi.MUTED);
        status.setText("Preview ended  •  move focus to preview again");
    }

    private void fail(String message, int color) {
        generation++;
        if (autoStop != null) main.removeCallbacks(autoStop);
        autoStop = null;
        releasePlayer();
        loading.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        poster.setVisibility(View.VISIBLE);
        failCopy(message, color);
    }

    private void failCopy(String message, int color) {
        status.setTextColor(color);
        status.setText(message);
    }

    public void stop(boolean ownerRequested) {
        generation++;
        if (pendingStart != null) main.removeCallbacks(pendingStart);
        if (autoStop != null) main.removeCallbacks(autoStop);
        pendingStart = null;
        autoStop = null;
        releasePlayer();
        loading.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        poster.setVisibility(View.VISIBLE);
        if (ownerRequested) failCopy("Preview stopped", TvUi.MUTED);
        else updateIdleCopy();
    }

    public void release() {
        stop(false);
        executor.shutdownNow();
    }

    private void releasePlayer() {
        if (player == null) return;
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private void updateIdleCopy() {
        if (selected == null) failCopy("Highlight a channel to preview it", TvUi.MUTED);
        else failCopy("Muted preview  •  OK opens full screen", Color.WHITE);
    }

    private Map<String, String> jsonMap(JSONObject json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, json.optString(key, ""));
        }
        return map;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
