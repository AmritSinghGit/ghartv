package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class PlayerActivity extends Activity implements ChannelNavigator.Listener {
    public static final String EXTRA_CHANNEL_JSON = "channel_json";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private ChannelRepository repository;
    private List<Channel> channels = new ArrayList<>();
    private Channel channel;
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar loading;
    private TextView title;
    private TextView subtitle;
    private TextView guideOverlay;
    private TextView numberOverlay;
    private ChannelNavigator navigator;
    private Runnable hideOverlay;
    private int playbackGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TvUi.immersive(this);
        if (!JioSession.load(this).isPresent()) {
            Intent login = new Intent(this, LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(login);
            finish();
            return;
        }
        repository = new ChannelRepository(this);
        channels = repository.loadAll();
        navigator = new ChannelNavigator(this);
        setContentView(buildUi());
        try {
            channel = Channel.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_CHANNEL_JSON)));
        } catch (Exception error) {
            Toast.makeText(this, "Channel data is missing", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startChannel(channel);
    }

    @Override protected void onResume() {
        super.onResume();
        TvUi.immersive(this);
        if (player != null) player.play();
    }

    @Override protected void onPause() {
        if (player != null) player.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        playbackGeneration++;
        releasePlayer();
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(TvUi.dp(this, 68), TvUi.dp(this, 68), Gravity.CENTER);
        root.addView(loading, loadingParams);

        FrameLayout infoBar = new FrameLayout(this);
        infoBar.setPadding(TvUi.dp(this, 34), TvUi.dp(this, 18), TvUi.dp(this, 34), TvUi.dp(this, 18));
        infoBar.setBackgroundColor(Color.argb(210, 3, 7, 13));
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(-1, TvUi.dp(this, 112), Gravity.BOTTOM);
        root.addView(infoBar, infoParams);
        title = TvUi.label(this, "Loading channel…", 26, TvUi.TEXT, true);
        infoBar.addView(title, new FrameLayout.LayoutParams(-1, TvUi.dp(this, 52), Gravity.TOP));
        subtitle = TvUi.label(this, "Starting live television…", 14, TvUi.MUTED, false);
        FrameLayout.LayoutParams subtitleParams = new FrameLayout.LayoutParams(-1, TvUi.dp(this, 40), Gravity.BOTTOM);
        infoBar.addView(subtitle, subtitleParams);

        guideOverlay = TvUi.label(this, "", 18, TvUi.TEXT, true);
        guideOverlay.setPadding(TvUi.dp(this, 25), TvUi.dp(this, 18), TvUi.dp(this, 25), TvUi.dp(this, 18));
        guideOverlay.setBackground(TvUi.rounded(Color.argb(238, 3, 12, 20), 24, Color.argb(95, 83, 228, 255), 2, this));
        guideOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(TvUi.dp(this, 630), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        guideParams.leftMargin = TvUi.dp(this, 34);
        guideParams.topMargin = TvUi.dp(this, 34);
        root.addView(guideOverlay, guideParams);

        numberOverlay = TvUi.label(this, "", 38, TvUi.TEXT, true);
        numberOverlay.setGravity(Gravity.CENTER);
        numberOverlay.setBackground(TvUi.rounded(Color.argb(242, 3, 11, 18), 24, TvUi.MINT, 2, this));
        numberOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(TvUi.dp(this, 190), TvUi.dp(this, 78), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        numberParams.topMargin = TvUi.dp(this, 60);
        root.addView(numberOverlay, numberParams);
        return root;
    }

    private void startChannel(Channel next) {
        if (next == null) return;
        channel = next;
        repository.setLastChannel(next.number);
        int generation = ++playbackGeneration;
        releasePlayer();
        loading.setVisibility(View.VISIBLE);
        title.setText(next.displayNumber() + "  " + next.name);
        subtitle.setText("Connecting to JioTV…");
        showInfoOverlay();
        executor.execute(() -> {
            try {
                PlaybackInfo info = repository.api().fetchPlayback(next);
                mainHandler.post(() -> {
                    if (generation != playbackGeneration || isFinishing()) return;
                    if (info.subscriptionRequired) {
                        showSubscription(info.message);
                        return;
                    }
                    preparePlayer(info);
                    loadEpg(next);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (generation != playbackGeneration || isFinishing()) return;
                    loading.setVisibility(View.GONE);
                    showPlaybackError(error);
                });
            }
        });
    }

    private void preparePlayer(PlaybackInfo info) {
        Map<String, String> streamHeaders = jsonMap(info.streamHeaders);
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(streamHeaders.getOrDefault("User-Agent", "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME))
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(25_000)
                .setDefaultRequestProperties(streamHeaders);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    loading.setVisibility(View.GONE);
                    subtitle.setText("LIVE  •  " + channel.language + "  •  CH ± switches channel  •  GUIDE returns home");
                    scheduleHideInfo();
                } else if (state == Player.STATE_BUFFERING) {
                    loading.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_ENDED) {
                    loading.setVisibility(View.GONE);
                    subtitle.setText("Stream ended");
                }
            }

            @Override public void onPlayerError(PlaybackException error) {
                loading.setVisibility(View.GONE);
                showPlaybackError(error);
            }
        });

        MediaItem.Builder item = new MediaItem.Builder().setUri(info.streamUrl);
        if (info.mimeType.toLowerCase().contains("dash") || info.streamUrl.toLowerCase().contains(".mpd")) {
            item.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (info.mimeType.toLowerCase().contains("mpegurl") || info.streamUrl.toLowerCase().contains(".m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        if (info.drm && !info.licenseUrl.isEmpty()) {
            MediaItem.DrmConfiguration drm = new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(info.licenseUrl)
                    .setLicenseRequestHeaders(jsonMap(info.licenseHeaders))
                    .setMultiSession(false)
                    .build();
            item.setDrmConfiguration(drm);
        }
        player.setMediaItem(item.build());
        player.prepare();
        player.play();
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void loadEpg(Channel selected) {
        executor.execute(() -> {
            try {
                List<Program> programs = repository.api().fetchEpg(selected.id, 0);
                long now = System.currentTimeMillis();
                Program current = null;
                Program next = null;
                for (Program p : programs) {
                    if (p.isLive(now)) current = p;
                    else if (p.startEpochMs > now && next == null) next = p;
                }
                Program finalCurrent = current;
                Program finalNext = next;
                mainHandler.post(() -> {
                    if (channel == null || !channel.id.equals(selected.id)) return;
                    String nowText = finalCurrent == null ? "Live now" : finalCurrent.title;
                    String nextText = finalNext == null ? "Next programme not listed" : "Next: " + finalNext.title;
                    subtitle.setText(nowText + "  •  " + nextText);
                    guideOverlay.setText("● LIVE    " + selected.displayNumber() + "  " + selected.name + "\n\nNOW  " + nowText + "\n" + nextText + "\n\nCH ± switches  •  OK pauses  •  GUIDE returns home");
                });
            } catch (Exception ignored) {}
        });
    }

    private void showInfoOverlay() {
        guideOverlay.setText("● LIVE    " + channel.displayNumber() + "  " + channel.name + "\n\nStarting live television…\n\nCH ± switches  •  OK pauses  •  GUIDE returns home");
        guideOverlay.setVisibility(View.VISIBLE);
        scheduleHideInfo();
    }

    private void scheduleHideInfo() {
        if (hideOverlay != null) mainHandler.removeCallbacks(hideOverlay);
        hideOverlay = () -> guideOverlay.setVisibility(View.GONE);
        mainHandler.postDelayed(hideOverlay, 6000L);
    }

    private void showSubscription(String message) {
        loading.setVisibility(View.GONE);
        new AlertDialog.Builder(this)
                .setTitle("Subscription required")
                .setMessage(message.isEmpty() ? "Jio has marked this channel as paid for the current account." : message)
                .setPositiveButton("Back to guide", (d, which) -> finish())
                .setNegativeButton("Try again", (d, which) -> startChannel(channel))
                .setCancelable(false)
                .show();
    }

    private void showPlaybackError(Throwable error) {
        String technical = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String friendly = "This channel could not start. Check the internet connection and confirm that the channel is included in the connected Jio account.";
        if (technical.toLowerCase().contains("401") || technical.toLowerCase().contains("419")
                || technical.toLowerCase().contains("token") || technical.toLowerCase().contains("unauthor")) {
            friendly = "The Jio session needs to be connected again before this channel can play.";
        } else if (technical.toLowerCase().contains("drm") || technical.toLowerCase().contains("widevine")) {
            friendly = "This protected channel was not accepted by the TV playback system. Another channel may still work.";
        }
        new AlertDialog.Builder(this)
                .setTitle("Live channel unavailable")
                .setMessage(friendly + "\n\nDetails: " + technical)
                .setPositiveButton("Retry", (d, which) -> startChannel(channel))
                .setNegativeButton("Guide", (d, which) -> finish())
                .setNeutralButton("Reconnect Jio", (d, which) -> reconnectJio())
                .show();
    }

    private void reconnectJio() {
        JioSession.clear(this);
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(login);
        finish();
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

    private void changeChannel(int direction) {
        Channel next = repository.next(channels, channel == null ? 0 : channel.number, direction);
        if (next == null) return;
        if (next.isJio() && !JioSession.load(this).isPresent()) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        startChannel(next);
    }

    @Override public void onDigits(String digits) {
        numberOverlay.setText(digits);
        numberOverlay.setVisibility(View.VISIBLE);
    }

    @Override public void onCommit(int channelNumber) {
        numberOverlay.setVisibility(View.GONE);
        Channel requested = repository.byNumber(channels, channelNumber);
        if (requested == null) {
            Toast.makeText(this, "Channel " + channelNumber + " is not in your live guide", Toast.LENGTH_SHORT).show();
            return;
        }
        startChannel(requested);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) {
            navigator.append(key - KeyEvent.KEYCODE_0);
            return true;
        }
        if (key >= KeyEvent.KEYCODE_NUMPAD_0 && key <= KeyEvent.KEYCODE_NUMPAD_9) {
            navigator.append(key - KeyEvent.KEYCODE_NUMPAD_0);
            return true;
        }
        switch (key) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
                changeChannel(1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                changeChannel(-1);
                return true;
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_TV:
            case KeyEvent.KEYCODE_DVR:
                finish();
                return true;
            case KeyEvent.KEYCODE_INFO:
                if (guideOverlay.getVisibility() == View.VISIBLE) guideOverlay.setVisibility(View.GONE);
                else showInfoOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                    showInfoOverlay();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (guideOverlay.getVisibility() == View.VISIBLE) {
                    guideOverlay.setVisibility(View.GONE);
                    return true;
                }
                finish();
                return true;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }
}
