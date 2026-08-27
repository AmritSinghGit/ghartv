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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class PlayerActivity extends Activity implements ChannelNavigator.Listener {
    public static final String EXTRA_CHANNEL_JSON = "channel_json";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private ChannelRepository repository;
    private List<Channel> allChannels = new ArrayList<>();
    private List<Channel> channelScope = new ArrayList<>();
    private String scopeLabel = ChannelRepository.CATEGORY_ALL;
    private Channel channel;
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar loading;
    private LinearLayout guidePanel;
    private TextView guideChannel;
    private TextView guideScope;
    private TextView guideNow;
    private TextView guideTiming;
    private TextView guideNext;
    private ProgressBar guideProgress;
    private Button guideButton;
    private TextView numberOverlay;
    private ChannelNavigator navigator;
    private Runnable hideGuidePanel;
    private Runnable progressTicker;
    private Program currentProgram;
    private Program nextProgram;
    private long lastEpgLoadedAt;
    private String panelStatus = "Starting live television…";
    private int playbackGeneration;
    private AlertDialog activeDialog;

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
        allChannels = repository.loadAll();
        navigator = new ChannelNavigator(this);
        setContentView(buildUi());
        try {
            Channel requested = Channel.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_CHANNEL_JSON)));
            Channel cached = repository.byNumber(allChannels, requested.number);
            channel = cached == null ? requested : cached;
        } catch (Exception error) {
            Toast.makeText(this, "Channel data is missing", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        resolveScope();
        startProgressTicker();
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
        if (hideGuidePanel != null) mainHandler.removeCallbacks(hideGuidePanel);
        if (progressTicker != null) mainHandler.removeCallbacks(progressTicker);
        if (activeDialog != null) activeDialog.dismiss();
        releasePlayer();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void resolveScope() {
        scopeLabel = repository.lastCategory();
        channelScope = repository.filter(allChannels, scopeLabel, "");
        if (repository.byNumber(channelScope, channel.number) == null) {
            scopeLabel = repository.categoryForChannel(channel);
            channelScope = repository.filter(allChannels, scopeLabel, "");
        }
        if (channelScope.isEmpty()) channelScope = new ArrayList<>(allChannels);
        if (repository.byNumber(channelScope, channel.number) == null) {
            channelScope.add(channel);
            channelScope.sort((left, right) -> Integer.compare(left.number, right.number));
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        playerView.setFocusable(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(TvUi.dp(this, 68), TvUi.dp(this, 68), Gravity.CENTER);
        root.addView(loading, loadingParams);

        guidePanel = buildGuidePanel();
        guidePanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        panelParams.setMargins(TvUi.dp(this, 26), 0, TvUi.dp(this, 26), TvUi.dp(this, 24));
        root.addView(guidePanel, panelParams);

        numberOverlay = TvUi.label(this, "", 38, TvUi.TEXT, true);
        numberOverlay.setGravity(Gravity.CENTER);
        numberOverlay.setBackground(TvUi.rounded(Color.argb(242, 3, 11, 18), 24, TvUi.MINT, 2, this));
        numberOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(TvUi.dp(this, 190), TvUi.dp(this, 78), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        numberParams.topMargin = TvUi.dp(this, 60);
        root.addView(numberOverlay, numberParams);
        return root;
    }

    private LinearLayout buildGuidePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(TvUi.dp(this, 28), TvUi.dp(this, 20), TvUi.dp(this, 28), TvUi.dp(this, 18));
        panel.setBackground(TvUi.gradient(Color.argb(245, 3, 11, 18), Color.argb(245, 7, 27, 38),
                24, Color.argb(105, 83, 228, 255), 1.2f, this));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = TvUi.badge(this, "● LIVE", TvUi.MINT);
        header.addView(live, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(this, 30)));
        guideChannel = TvUi.label(this, "", 22, TvUi.TEXT, true);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(0, TvUi.dp(this, 34), 1f);
        channelParams.leftMargin = TvUi.dp(this, 15);
        header.addView(guideChannel, channelParams);
        guideScope = TvUi.label(this, "", 13, TvUi.CYAN, true);
        guideScope.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        header.addView(guideScope, new LinearLayout.LayoutParams(TvUi.dp(this, 330), TvUi.dp(this, 34)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 36)));

        guideNow = TvUi.label(this, "Starting live television…", 21, TvUi.TEXT, true);
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 40));
        nowParams.topMargin = TvUi.dp(this, 5);
        panel.addView(guideNow, nowParams);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        guideTiming = TvUi.label(this, "Programme timing not listed", 12, TvUi.MUTED, false);
        progressRow.addView(guideTiming, new LinearLayout.LayoutParams(TvUi.dp(this, 260), TvUi.dp(this, 26)));
        guideProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        guideProgress.setMax(1000);
        guideProgress.setProgressTintList(TvUi.tint(TvUi.MINT));
        guideProgress.setProgressBackgroundTintList(TvUi.tint(Color.rgb(28, 55, 67)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(0, TvUi.dp(this, 10), 1f);
        progressParams.leftMargin = TvUi.dp(this, 14);
        progressParams.rightMargin = TvUi.dp(this, 14);
        progressRow.addView(guideProgress, progressParams);
        guideNext = TvUi.label(this, "Next programme will appear here", 13, TvUi.MUTED, false);
        guideNext.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        progressRow.addView(guideNext, new LinearLayout.LayoutParams(TvUi.dp(this, 420), TvUi.dp(this, 28)));
        panel.addView(progressRow, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 34)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        Button previous = TvUi.button(this, "← Previous", false);
        previous.setOnClickListener(view -> changeChannel(-1));
        actions.addView(previous, actionParams());
        guideButton = TvUi.button(this, "Open guide", true);
        guideButton.setOnClickListener(view -> finish());
        actions.addView(guideButton, actionParams());
        Button next = TvUi.button(this, "Next →", false);
        next.setOnClickListener(view -> changeChannel(1));
        actions.addView(next, actionParams());
        panel.addView(actions, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 48)));
        return panel;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(TvUi.dp(this, 160), TvUi.dp(this, 42));
        params.leftMargin = TvUi.dp(this, 12);
        return params;
    }

    private void startChannel(Channel next) {
        if (next == null) return;
        dismissDialog();
        channel = next;
        repository.setLastChannel(next.number);
        int generation = ++playbackGeneration;
        releasePlayer();
        currentProgram = null;
        nextProgram = null;
        lastEpgLoadedAt = 0L;
        panelStatus = "Connecting to JioTV…";
        loading.setVisibility(View.VISIBLE);
        showGuidePanel(false);
        executor.execute(() -> {
            try {
                PlaybackInfo info = repository.api().fetchPlayback(next);
                mainHandler.post(() -> {
                    if (generation != playbackGeneration || isFinishing()) return;
                    loading.setVisibility(View.GONE);
                    if (info.authRequired) {
                        showAuthRequired(info.message);
                    } else if (info.subscriptionRequired) {
                        markAccess(Channel.ACCESS_SUBSCRIPTION, info.message);
                        showSubscription(info.message);
                    } else if (info.unavailable) {
                        markAccess(Channel.ACCESS_UNAVAILABLE, info.message);
                        showUnavailable(info.message, info.responseCode);
                    } else {
                        preparePlayer(info);
                        loadEpg(next);
                    }
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
                    panelStatus = "Live now";
                    markAccess(Channel.ACCESS_AVAILABLE, "Playable on this connected Jio account.");
                    updateGuidePanel();
                    scheduleHideGuidePanel();
                } else if (state == Player.STATE_BUFFERING) {
                    loading.setVisibility(View.VISIBLE);
                    panelStatus = "Buffering live television…";
                    showGuidePanel(false);
                } else if (state == Player.STATE_ENDED) {
                    loading.setVisibility(View.GONE);
                    panelStatus = "This live feed ended.";
                    showGuidePanel(false);
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
        lastEpgLoadedAt = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                List<Program> programs = repository.api().fetchEpg(selected.id, 0);
                long now = System.currentTimeMillis();
                Program current = null;
                Program following = null;
                for (Program program : programs) {
                    if (program.isLive(now)) current = program;
                    else if (program.startEpochMs > now && following == null) following = program;
                }
                Program finalCurrent = current;
                Program finalFollowing = following;
                mainHandler.post(() -> {
                    if (channel == null || !channel.id.equals(selected.id)) return;
                    currentProgram = finalCurrent;
                    nextProgram = finalFollowing;
                    channel.nowTitle = finalCurrent == null ? "Live now" : finalCurrent.title;
                    channel.nextTitle = finalFollowing == null ? "" : finalFollowing.title;
                    updateGuidePanel();
                });
            } catch (Exception ignored) {
                mainHandler.post(this::updateGuidePanel);
            }
        });
    }

    private void startProgressTicker() {
        progressTicker = new Runnable() {
            @Override public void run() {
                updateGuidePanel();
                long now = System.currentTimeMillis();
                if (currentProgram != null && currentProgram.endEpochMs > 0 && now >= currentProgram.endEpochMs
                        && now - lastEpgLoadedAt > 30_000L && channel != null) {
                    loadEpg(channel);
                }
                mainHandler.postDelayed(this, 15_000L);
            }
        };
        mainHandler.post(progressTicker);
    }

    private void updateGuidePanel() {
        if (guidePanel == null || channel == null) return;
        guideChannel.setText(channel.displayNumber() + "  " + channel.name);
        guideScope.setText(scopeLabel + "  •  CH ± stays in this view");
        if (currentProgram == null) {
            guideNow.setText(panelStatus);
            guideTiming.setText("Programme timing not listed");
            guideProgress.setProgress(0);
        } else {
            guideNow.setText(currentProgram.title);
            long now = System.currentTimeMillis();
            long duration = Math.max(1L, currentProgram.endEpochMs - currentProgram.startEpochMs);
            int progress = (int) Math.max(0, Math.min(1000, ((now - currentProgram.startEpochMs) * 1000L) / duration));
            guideProgress.setProgress(progress);
            guideTiming.setText(timeRange(currentProgram) + "  •  " + (progress / 10) + "% complete");
        }
        guideNext.setText(nextProgram == null
                ? "Next programme not listed"
                : "NEXT  " + nextProgram.title + "  •  " + startTime(nextProgram));
    }

    private String timeRange(Program program) {
        DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT);
        return format.format(new Date(program.startEpochMs)) + "–" + format.format(new Date(program.endEpochMs));
    }

    private String startTime(Program program) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(program.startEpochMs));
    }

    private void showGuidePanel(boolean interactive) {
        if (guidePanel == null) return;
        guidePanel.setVisibility(View.VISIBLE);
        updateGuidePanel();
        if (interactive) mainHandler.post(() -> guideButton.requestFocus());
        scheduleHideGuidePanel();
    }

    private void scheduleHideGuidePanel() {
        if (hideGuidePanel != null) mainHandler.removeCallbacks(hideGuidePanel);
        hideGuidePanel = () -> {
            if (guidePanel.hasFocus()) {
                mainHandler.postDelayed(hideGuidePanel, 2500L);
                return;
            }
            hideGuidePanelNow();
        };
        mainHandler.postDelayed(hideGuidePanel, 7000L);
    }

    private void hideGuidePanelNow() {
        if (guidePanel == null) return;
        guidePanel.clearFocus();
        guidePanel.setVisibility(View.GONE);
        playerView.requestFocus();
    }

    private void showSubscription(String message) {
        loading.setVisibility(View.GONE);
        hideGuidePanelNow();
        String body = message == null || message.trim().isEmpty()
                ? "Jio has marked this channel as requiring a separate subscription for the connected account."
                : message;
        activeDialog = new AlertDialog.Builder(this)
                .setTitle("Subscription required")
                .setMessage(body)
                .setPositiveButton("Next channel", (dialog, which) -> changeChannel(1))
                .setNegativeButton("Guide", (dialog, which) -> finish())
                .setNeutralButton("Retry", (dialog, which) -> startChannel(channel))
                .setCancelable(false)
                .show();
    }

    private void showUnavailable(String message, int responseCode) {
        loading.setVisibility(View.GONE);
        hideGuidePanelNow();
        String detail = message == null || message.trim().isEmpty()
                ? "Jio did not make this live feed available."
                : message;
        if (responseCode > 0 && !detail.contains("HTTP")) detail += "\n\nHTTP " + responseCode;
        activeDialog = new AlertDialog.Builder(this)
                .setTitle("Channel unavailable")
                .setMessage(detail)
                .setPositiveButton("Next channel", (dialog, which) -> changeChannel(1))
                .setNegativeButton("Guide", (dialog, which) -> finish())
                .setNeutralButton("Retry", (dialog, which) -> startChannel(channel))
                .setCancelable(false)
                .show();
    }

    private void showAuthRequired(String message) {
        loading.setVisibility(View.GONE);
        hideGuidePanelNow();
        activeDialog = new AlertDialog.Builder(this)
                .setTitle("Reconnect JioTV")
                .setMessage(message == null || message.trim().isEmpty()
                        ? "The Jio session needs to be connected again."
                        : message)
                .setPositiveButton("Reconnect Jio", (dialog, which) -> reconnectJio())
                .setNegativeButton("Guide", (dialog, which) -> finish())
                .setNeutralButton("Next channel", (dialog, which) -> changeChannel(1))
                .setCancelable(false)
                .show();
    }

    private void showPlaybackError(Throwable error) {
        String technical = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String lower = technical.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("419") || lower.contains("token")
                || lower.contains("unauthor") || lower.contains("session")) {
            showAuthRequired("The Jio session needs to be connected again before this channel can play.\n\nDetails: " + technical);
            return;
        }
        String friendly;
        if (lower.contains("403")) {
            friendly = "The live feed was refused by the provider. This can be a channel or licensing restriction rather than an internet problem.";
            markAccess(Channel.ACCESS_UNAVAILABLE, friendly);
        } else if (lower.contains("drm") || lower.contains("widevine")) {
            friendly = "This protected stream was not accepted by the TV playback system. Another channel may still work.";
        } else {
            friendly = "This live feed stopped or could not start. You can retry or continue to the next channel.";
        }
        showUnavailable(friendly + "\n\nDetails: " + technical, lower.contains("403") ? 403 : 0);
    }

    private void reconnectJio() {
        JioSession.clear(this);
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(login);
        finish();
    }

    private void markAccess(String state, String message) {
        repository.applyAccessState(channel, state, message);
        for (Channel scoped : channelScope) {
            if (scoped.id.equals(channel.id)) {
                scoped.accessState = channel.accessState;
                scoped.accessMessage = channel.accessMessage;
                scoped.accessUpdatedAt = channel.accessUpdatedAt;
            }
        }
        for (Channel cached : allChannels) {
            if (cached.id.equals(channel.id)) {
                cached.accessState = channel.accessState;
                cached.accessMessage = channel.accessMessage;
                cached.accessUpdatedAt = channel.accessUpdatedAt;
            }
        }
    }

    private void dismissDialog() {
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
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

    private void changeChannel(int direction) {
        dismissDialog();
        Channel next = repository.next(channelScope, channel == null ? 0 : channel.number, direction);
        if (next == null || (channelScope.size() == 1 && channel != null && next.number == channel.number)) {
            Toast.makeText(this, "There is no other channel in " + scopeLabel, Toast.LENGTH_SHORT).show();
            return;
        }
        startChannel(next);
    }

    private void adoptScopeForDirectTune(Channel requested) {
        scopeLabel = repository.categoryForChannel(requested);
        channelScope = repository.filter(allChannels, scopeLabel, "");
        if (channelScope.isEmpty()) channelScope = new ArrayList<>(allChannels);
    }

    @Override public void onDigits(String digits) {
        numberOverlay.setText(digits);
        numberOverlay.setVisibility(View.VISIBLE);
    }

    @Override public void onCommit(int channelNumber) {
        numberOverlay.setVisibility(View.GONE);
        Channel requested = repository.byNumber(allChannels, channelNumber);
        if (requested == null) {
            Toast.makeText(this, "Channel " + channelNumber + " is not in your live guide", Toast.LENGTH_SHORT).show();
            return;
        }
        if (repository.byNumber(channelScope, requested.number) == null) adoptScopeForDirectTune(requested);
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
                if (guidePanel.getVisibility() == View.VISIBLE) hideGuidePanelNow();
                else showGuidePanel(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (guidePanel.getVisibility() != View.VISIBLE) {
                    showGuidePanel(true);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (guidePanel.getVisibility() == View.VISIBLE && guidePanel.hasFocus()) {
                    return super.dispatchKeyEvent(event);
                }
                showGuidePanel(true);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                    showGuidePanel(false);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (guidePanel.getVisibility() == View.VISIBLE) {
                    hideGuidePanelNow();
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
