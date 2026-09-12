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
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Muted, bounded living-room preview. A channel must remain focused briefly
 * before preview starts, so fast guide navigation does not create a stream for
 * every card. Moving focus releases the old decoder; successful preview is
 * capped at 15 seconds. Pressing the preview surface is handled by MainActivity
 * and opens the normal continuous full-screen player.
 */
@UnstableApi
public final class HeroPreviewController {
    private static final long AUTO_START_DELAY_MS = 700L;
    private static final long MAX_PREVIEW_MS = 15_000L;

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

    public HeroPreviewController(Activity activity,
                                 ChannelRepository repository,
                                 PlayerView playerView,
                                 ImageView poster,
                                 ProgressBar loading,
                                 TextView status) {
        this.activity = activity;
        this.repository = repository;
        this.playerView = playerView;
        this.poster = poster;
        this.loading = loading;
        this.status = status;
        playerView.setUseController(false);
        playerView.setKeepScreenOn(false);
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
            status.setTextColor(TvUi.AMBER);
            status.setText("Subscription required  •  press OK to try full-screen television");
            return;
        }
        if (channel.isUnavailable()) {
            status.setTextColor(TvUi.ERROR);
            status.setText("Needs attention  •  press OK to retry full-screen television");
            return;
        }
        status.setTextColor(Color.WHITE);
        status.setText("Auto preview starting…  •  press OK for continuous television");
        final int scheduledGeneration = generation;
        pendingStart = () -> {
            pendingStart = null;
            if (scheduledGeneration == generation) start();
        };
        main.postDelayed(pendingStart, AUTO_START_DELAY_MS);
    }

    private void start() {
        Channel channel = selected;
        if (channel == null || channel.id == null || channel.id.isEmpty()) return;
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
                            || info.streamUrl == null || info.streamUrl.trim().isEmpty()) {
                        String copy = info.subscriptionRequired
                                ? "Subscription required  •  press OK to try full screen"
                                : info.authRequired
                                ? "Reconnect JioTV before previewing this channel"
                                : "Preview unavailable  •  press OK to try full screen";
                        fail(copy, info.subscriptionRequired ? TvUi.AMBER : TvUi.ERROR);
                        return;
                    }
                    prepare(info, channel, currentGeneration);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (currentGeneration != generation || activity.isFinishing()) return;
                    fail("Preview unavailable  •  press OK to try full screen", TvUi.ERROR);
                    Telemetry.error(activity, "guide_preview", error, Telemetry.data(
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
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (currentGeneration != generation) return;
                if (state == Player.STATE_READY) {
                    loading.setVisibility(View.GONE);
                    poster.setVisibility(View.GONE);
                    playerView.setVisibility(View.VISIBLE);
                    status.setTextColor(TvUi.MINT);
                    status.setText("Muted 15-second preview  •  OK opens continuous television");
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

            @Override public void onPlayerError(PlaybackException error) {
                if (currentGeneration != generation) return;
                fail("Preview stopped  •  press OK to try full screen", TvUi.ERROR);
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
        if (mime.contains("dash") || lower.contains(".mpd")) {
            item.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (mime.contains("mpegurl") || lower.contains(".m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        if (info.drm && info.licenseUrl != null && !info.licenseUrl.isEmpty()) {
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
        status.setText("Preview ended  •  move channels or press OK for continuous television");
    }

    private void fail(String message, int color) {
        generation++;
        if (autoStop != null) main.removeCallbacks(autoStop);
        autoStop = null;
        releasePlayer();
        loading.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        poster.setVisibility(View.VISIBLE);
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
        if (ownerRequested) {
            status.setTextColor(TvUi.MUTED);
            status.setText("Preview stopped");
        } else {
            updateIdleCopy();
        }
    }

    public void release() {
        stop(false);
        executor.shutdownNow();
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void updateIdleCopy() {
        if (selected == null) {
            status.setTextColor(TvUi.MUTED);
            status.setText("Highlight a channel to preview it automatically");
        } else if (selected.isSubscriptionChannel() && !selected.isAvailable()) {
            status.setTextColor(TvUi.AMBER);
            status.setText("Subscription required  •  press OK to try full-screen television");
        } else if (selected.isUnavailable()) {
            status.setTextColor(TvUi.ERROR);
            status.setText("Needs attention  •  press OK to retry full-screen television");
        } else {
            status.setTextColor(Color.WHITE);
            status.setText("Auto preview  •  press OK for continuous television");
        }
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
