package in.ghartv.nova;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
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
 * Explicit, muted, short living-room preview. It never autoplays on focus and
 * releases the decoder after 15 seconds to stay friendly to low-memory TVs.
 */
@UnstableApi
public final class HeroPreviewController {
    private static final long MAX_PREVIEW_MS = 15_000L;

    private final Activity activity;
    private final ChannelRepository repository;
    private final PlayerView playerView;
    private final ImageView poster;
    private final ProgressBar loading;
    private final TextView status;
    private final Button button;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ExoPlayer player;
    private Channel selected;
    private int generation;
    private Runnable autoStop;

    public HeroPreviewController(Activity activity,
                                 ChannelRepository repository,
                                 PlayerView playerView,
                                 ImageView poster,
                                 ProgressBar loading,
                                 TextView status,
                                 Button button) {
        this.activity = activity;
        this.repository = repository;
        this.playerView = playerView;
        this.poster = poster;
        this.loading = loading;
        this.status = status;
        this.button = button;
        playerView.setUseController(false);
        playerView.setKeepScreenOn(false);
        playerView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        button.setOnClickListener(view -> toggle());
        updateIdleCopy();
    }

    public void select(Channel channel) {
        boolean changed = selected == null || channel == null || selected.number != channel.number;
        selected = channel;
        if (changed) stop(false);
        updateIdleCopy();
    }

    public void toggle() {
        if (player != null) {
            stop(true);
            return;
        }
        start();
    }

    private void start() {
        Channel channel = selected;
        if (channel == null || channel.id == null || channel.id.isEmpty()) return;
        final int currentGeneration = ++generation;
        releasePlayer();
        loading.setVisibility(View.VISIBLE);
        status.setText("Starting a muted 15-second preview…");
        button.setText("■  Stop preview");
        Telemetry.event(activity, "guide_preview_request", Telemetry.data(
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
                                ? "Preview unavailable until this Jio subscription is active."
                                : info.authRequired
                                ? "Reconnect JioTV before previewing this channel."
                                : "This channel cannot be previewed right now.";
                        fail(copy);
                        return;
                    }
                    prepare(info, channel, currentGeneration);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (currentGeneration != generation || activity.isFinishing()) return;
                    fail("Preview unavailable — Watch Live may still work.");
                    Telemetry.error(activity, "guide_preview", error, Telemetry.data(
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
                    status.setText("Muted preview  •  Watch Live for full-screen sound");
                    Telemetry.event(activity, "guide_preview_ready", Telemetry.data(
                            "protocol", protocol(info),
                            "drm", info.drm,
                            "language", channel.language,
                            "category", channel.category
                    ));
                    scheduleStop();
                } else if (state == Player.STATE_BUFFERING) {
                    loading.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_ENDED) {
                    stop(false);
                }
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (currentGeneration != generation) return;
                fail("Preview stopped — Watch Live may still work.");
                Telemetry.error(activity, "guide_preview_media", error, Telemetry.data(
                        "protocol", protocol(info),
                        "drm", info.drm
                ));
            }
        });

        MediaItem.Builder item = new MediaItem.Builder().setUri(info.streamUrl);
        String lower = info.streamUrl.toLowerCase(Locale.ROOT);
        if (info.mimeType.toLowerCase(Locale.ROOT).contains("dash") || lower.contains(".mpd")) {
            item.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (info.mimeType.toLowerCase(Locale.ROOT).contains("mpegurl") || lower.contains(".m3u8")) {
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
        String stream = info.streamUrl == null ? "" : info.streamUrl.toLowerCase(Locale.ROOT);
        return info.mimeType.toLowerCase(Locale.ROOT).contains("dash") || stream.contains(".mpd") ? "dash" : "hls";
    }

    private void scheduleStop() {
        if (autoStop != null) main.removeCallbacks(autoStop);
        autoStop = () -> stop(false);
        main.postDelayed(autoStop, MAX_PREVIEW_MS);
    }

    private void fail(String message) {
        stop(false);
        status.setText(message);
    }

    public void stop(boolean ownerRequested) {
        generation++;
        if (autoStop != null) main.removeCallbacks(autoStop);
        autoStop = null;
        releasePlayer();
        loading.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        poster.setVisibility(View.VISIBLE);
        button.setText("▶  Preview");
        if (ownerRequested) status.setText("Preview stopped");
        else updateIdleCopy();
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
            status.setText("Choose a channel, then preview it here");
        } else if (selected.isSubscriptionChannel()) {
            status.setText("Subscription channel  •  Preview may require entitlement");
        } else if (selected.isUnavailable()) {
            status.setText("Needs attention  •  Preview will retry current access");
        } else {
            status.setText("Press Preview for a muted 15-second look");
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
}
