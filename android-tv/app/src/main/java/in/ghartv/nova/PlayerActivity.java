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

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class PlayerActivity extends Activity implements ChannelNavigator.Listener {
    public static final String EXTRA_CHANNEL_JSON = "channel_json";
    public static final String EXTRA_SCOPE_NUMBERS = "scope_numbers";
    public static final String EXTRA_SCOPE_LABEL = "scope_label";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private ChannelRepository repository;
    private WatchHistoryStore historyStore;
    private List<Channel> allChannels = new ArrayList<>();
    private List<Channel> playbackScope = new ArrayList<>();
    private Channel channel;
    private String scopeLabel = "All channels";
    private AlertDialog errorDialog;
    private boolean recoveryPending;

    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar loading;
    private LinearLayout guidePanel;
    private TextView guideChannel;
    private TextView guideScope;
    private TextView nowTitle;
    private TextView nowTime;
    private TextView nextTitle;
    private ProgressBar programmeProgress;
    private Button rewindButton;
    private Button pauseButton;
    private Button liveButton;
    private Button forwardButton;
    private Button previousButton;
    private Button pictureButton;
    private Button guideButton;
    private Button nextButton;
    private TextView numberOverlay;

    private ChannelNavigator navigator;
    private Runnable hideGuide;
    private Runnable bufferingWatchdog;
    private long panelShownAt;
    private long panelLastInteractionAt;
    private volatile int playbackGeneration;
    private final java.util.concurrent.ThreadPoolExecutor playbackExecutor=(java.util.concurrent.ThreadPoolExecutor)Executors.newFixedThreadPool(2);
    private java.util.concurrent.Future<?> pendingPlayback;
    private long tuneElapsed;
    private PlaybackProbe probe;
    private long tuneStartedAt;
    private long playbackReadyAt;
    private long bufferingStartedAt;
    private long totalBufferingMs;
    private int bufferingCount;
    private boolean playbackSessionReported;
    private String playbackProtocol = "unknown";
    private boolean playbackDrm;
    private int automaticStreamRefreshes;
    private long lastAutomaticStreamRefreshAt;
    private int automaticNetworkRetries;
    private int automaticBufferRecoveries;
    private Program currentProgram;
    private Program nextProgram;
    private List<Program> guidePrograms = new ArrayList<>();
    private String guideStatus = "Starting live television…";

    private final Runnable progressTicker = new Runnable() {
        @Override public void run() {
            refreshGuideContent();
            mainHandler.postDelayed(this, 15_000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TvUi.immersive(this);

        if (!JioSession.load(this).isPresent()) {
            routeToLogin();
            return;
        }

        repository = new ChannelRepository(this);
        historyStore = repository.history();
        allChannels = repository.loadAll();
        navigator = new ChannelNavigator(this);

        try {
            channel = Channel.fromJson(new JSONObject(state!=null?state.getString(EXTRA_CHANNEL_JSON):getIntent().getStringExtra(EXTRA_CHANNEL_JSON)));
        } catch (Exception error) {
            Toast.makeText(this, "Channel data is missing", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        scopeLabel = cleanScopeLabel(state!=null?state.getString(EXTRA_SCOPE_LABEL):getIntent().getStringExtra(EXTRA_SCOPE_LABEL));
        playbackScope = parseScope(state!=null?state.getString(EXTRA_SCOPE_NUMBERS):getIntent().getStringExtra(EXTRA_SCOPE_NUMBERS));
        setContentView(buildUi());
        Telemetry.screen(this, "player");
        mainHandler.post(progressTicker);
        startChannel(channel, true);
    }

    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        try{if(channel!=null)out.putString(EXTRA_CHANNEL_JSON,channel.toJson().toString());}catch(Exception ignored){}
        JSONArray scope=new JSONArray();for(Channel c:playbackScope)scope.put(c.number);
        out.putString(EXTRA_SCOPE_NUMBERS,scope.toString());out.putString(EXTRA_SCOPE_LABEL,scopeLabel);
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
        reportPlaybackSession("activity_destroyed");
        playbackGeneration++;
        if (hideGuide != null) mainHandler.removeCallbacks(hideGuide);
        cancelBufferingWatchdog();
        mainHandler.removeCallbacks(progressTicker);
        releasePlayer();
        executor.shutdownNow();
        playbackExecutor.shutdownNow();
        super.onDestroy();
    }

    private void routeToLogin() {
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(login);
        finish();
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        playerView.setFocusable(true);
        playerView.setFocusableInTouchMode(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        root.addView(loading, new FrameLayout.LayoutParams(
                TvUi.dp(this, 68), TvUi.dp(this, 68), Gravity.CENTER));

        guidePanel = buildGuidePanel();
        guidePanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(this, 180), Gravity.BOTTOM);
        guideParams.setMargins(TvUi.dp(this, 34), 0, TvUi.dp(this, 34), TvUi.dp(this, 14));
        root.addView(guidePanel, guideParams);

        numberOverlay = TvUi.label(this, "", 38, TvUi.TEXT, true);
        numberOverlay.setGravity(Gravity.CENTER);
        numberOverlay.setBackground(TvUi.rounded(Color.argb(242, 3, 11, 18), 24, TvUi.MINT, 2, this));
        numberOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(
                TvUi.dp(this, 190), TvUi.dp(this, 78), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        numberParams.topMargin = TvUi.dp(this, 60);
        root.addView(numberOverlay, numberParams);

        playerView.requestFocus();
        return root;
    }

    private LinearLayout buildGuidePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(TvUi.dp(this, 18), TvUi.dp(this, 8), TvUi.dp(this, 18), TvUi.dp(this, 7));
        panel.setBackground(TvUi.gradient(
                Color.argb(188, 2, 9, 16), Color.argb(176, 9, 32, 43),
                24, Color.argb(95, 83, 228, 255), 1.2f, this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = TvUi.badge(this, "LIVE", TvUi.MINT);
        top.addView(live, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 24)));

        guideChannel = TvUi.label(this, "Loading channel…", 18, TvUi.TEXT, true);
        guideChannel.setSingleLine(true);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(0, TvUi.dp(this, 27), 1f);
        channelParams.leftMargin = TvUi.dp(this, 12);
        top.addView(guideChannel, channelParams);

        guideScope = TvUi.badge(this, scopeLabel.toUpperCase(Locale.ROOT), TvUi.CYAN);
        top.addView(guideScope, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 24)));
        panel.addView(top, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 27)));

        LinearLayout nowRow = new LinearLayout(this);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        nowTitle = TvUi.label(this, "NOW  Starting live television…", 14, TvUi.TEXT, true);
        nowTitle.setSingleLine(true);
        nowRow.addView(nowTitle, new LinearLayout.LayoutParams(0, TvUi.dp(this, 24), 1f));
        nowTime = TvUi.label(this, "", 12, TvUi.MUTED, true);
        nowTime.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        nowRow.addView(nowTime, new LinearLayout.LayoutParams(TvUi.dp(this, 165), TvUi.dp(this, 24)));
        panel.addView(nowRow, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 24)));

        programmeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        programmeProgress.setMax(1000);
        programmeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(TvUi.MINT));
        programmeProgress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.argb(48, 255, 255, 255)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 4));
        progressParams.topMargin = TvUi.dp(this, 2);
        panel.addView(programmeProgress, progressParams);

        nextTitle = TvUi.label(this, "NEXT  Programme guide loading…", 12, TvUi.MUTED, false);
        nextTitle.setSingleLine(true);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 20));
        nextParams.topMargin = TvUi.dp(this, 2);
        panel.addView(nextTitle, nextParams);

        LinearLayout transport = new LinearLayout(this);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        rewindButton = TvUi.button(this, "↶ 15s", false);
        rewindButton.setOnClickListener(view -> seekBy(-15_000L));
        transport.addView(rewindButton, transportParams());
        pauseButton = TvUi.button(this, "Pause", true);
        pauseButton.setOnClickListener(view -> togglePause());
        transport.addView(pauseButton, transportParams());
        liveButton = TvUi.button(this, "Live", false);
        liveButton.setOnClickListener(view -> goLive());
        transport.addView(liveButton, transportParams());
        forwardButton = TvUi.button(this, "15s ↷", false);
        forwardButton.setOnClickListener(view -> seekBy(15_000L));
        transport.addView(forwardButton, transportParams());
        TextView transportHint = TvUi.label(this, "Transport appears only when this channel exposes a TV time-shift window", 10, TvUi.MUTED, false);
        transportHint.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        transport.addView(transportHint, new LinearLayout.LayoutParams(0, TvUi.dp(this, 30), 1f));
        panel.addView(transport, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 31)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView hint = TvUi.label(this, "INFO toggles this panel  •  CH ± stays inside " + scopeLabel, 10, TvUi.MUTED, false);
        actions.addView(hint, new LinearLayout.LayoutParams(0, TvUi.dp(this, 33), 1f));

        previousButton = TvUi.button(this, "◀ Previous", false);
        previousButton.setOnClickListener(view -> changeChannel(-1));
        actions.addView(previousButton, actionParams());
        pictureButton = TvUi.button(this, "Picture", false);
        pictureButton.setOnClickListener(view -> PictureShape.show(this, playerView,
                channel == null ? "" : channel.id));
        actions.addView(pictureButton, actionParams());
        guideButton = TvUi.button(this, "Programmes", false);
        guideButton.setOnClickListener(view -> showProgrammeSchedule());
        actions.addView(guideButton, actionParams());
        nextButton = TvUi.button(this, "Next channel ▶", true);
        nextButton.setTextSize(11);
        nextButton.setOnClickListener(view -> changeChannel(1));
        actions.addView(nextButton, actionParams());

        for (View action : new View[]{rewindButton, pauseButton, liveButton, forwardButton,
                previousButton, pictureButton, guideButton, nextButton}) {
            bindActionFocus(action);
        }
        panel.addView(actions, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 35)));
        refreshTransportState();
        return panel;
    }

    private LinearLayout.LayoutParams transportParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(TvUi.dp(this, 96), TvUi.dp(this, 29));
        params.rightMargin = TvUi.dp(this, 7);
        return params;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(TvUi.dp(this, 115), TvUi.dp(this, 31));
        params.leftMargin = TvUi.dp(this, 7);
        return params;
    }

    private void bindActionFocus(View view) {
        View.OnFocusChangeListener base = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((target, focused) -> {
            if (base != null) base.onFocusChange(target, focused);
            if (focused && guidePanel != null && guidePanel.getVisibility() == View.VISIBLE) {
                notePanelInteraction();
            }
        });
    }

    private void startChannel(Channel next, boolean resetRecovery) {
        if (next == null) return;
        recoveryPending=false;
        if(errorDialog!=null){errorDialog.dismiss();errorDialog=null;}
        if (resetRecovery) {
            automaticStreamRefreshes = 0;
            lastAutomaticStreamRefreshAt = 0L;
            automaticNetworkRetries = 0;
            automaticBufferRecoveries = 0;
        }
        reportPlaybackSession("channel_changed");
        channel = next;
        resetPlaybackMetrics();
        Telemetry.event(this, "playback_request", Telemetry.data(
                "category", next.category,
                "language", next.language,
                "guide_scope", scopeLabel,
                "access_state", next.accessState));
        currentProgram = null;
        nextProgram = null;
        guidePrograms.clear();
        guideStatus = "Connecting to JioTV…";
        repository.setLastChannel(next.number);
        historyStore.recordTune(next);
        int generation = ++playbackGeneration;
        tuneElapsed=android.os.SystemClock.elapsedRealtime();
        if(pendingPlayback!=null)pendingPlayback.cancel(true);
        playbackExecutor.purge();
        cancelBufferingWatchdog();
        releasePlayer();
        loading.setVisibility(View.VISIBLE);
        refreshGuideContent();
        showGuide(false, null);

        loadEpg(next);
        pendingPlayback=playbackExecutor.submit(() -> {
            if(generation!=playbackGeneration||Thread.currentThread().isInterrupted())return;
            try {
                long authStart=android.os.SystemClock.elapsedRealtime();
                PlaybackInfo info = repository.api().fetchPlayback(next);
                Telemetry.event(this,"playback_authorization_timing",Telemetry.data("duration_ms",android.os.SystemClock.elapsedRealtime()-authStart));
                mainHandler.post(() -> {
                    if (generation != playbackGeneration || isFinishing()) return;
                    info.normalizeAliases();
                    if (info.authRequired) {
                        String reference = Telemetry.playbackFailure(this, next, "auth_required", info.message,
                                info.responseCode, null);
                        showAuthRequired(withReference(info.message, reference));
                        return;
                    }
                    if (info.subscriptionRequired) {
                        markAccess(Channel.ACCESS_SUBSCRIPTION, info.message);
                        String reference = Telemetry.playbackFailure(this, next, "subscription_required", info.message,
                                info.responseCode, null);
                        showAccessRequired(true, withReference(info.message, reference));
                        return;
                    }
                    if (info.unavailable) {
                        markAccess(Channel.ACCESS_UNAVAILABLE, info.message);
                        String reference = Telemetry.playbackFailure(this, next, "playback_unavailable", info.message,
                                info.responseCode, null);
                        showAccessRequired(false, withReference(info.message, reference));
                        return;
                    }
                    preparePlayer(info);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (generation != playbackGeneration || isFinishing()) return;
                    loading.setVisibility(View.GONE);
                    if (!maybeAutoRecover(error, "playback_authorization")) showPlaybackError(error);
                });
            }
        });
    }

    private void preparePlayer(PlaybackInfo info) {
        info.normalizeAliases();
        if (info.streamUrl == null || info.streamUrl.trim().isEmpty()) {
            showPlaybackError(new IllegalStateException("Jio returned no playable stream URL"));
            return;
        }
        playbackProtocol = (info.mimeType.toLowerCase(Locale.ROOT).contains("dash")
                || info.streamUrl.toLowerCase(Locale.ROOT).contains(".mpd")) ? "dash" : "hls";
        playbackDrm = info.drm;
        Map<String, String> streamHeaders = jsonMap(info.streamHeaders);
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(streamHeaders.getOrDefault("User-Agent", "GharTV-Jio-Live/" + BuildConfig.VERSION_NAME))
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(25_000)
                .setDefaultRequestProperties(streamHeaders);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        android.app.ActivityManager memory=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        int targetBytes=(memory!=null&&memory.isLowRamDevice()?16:32)*1024*1024;
        androidx.media3.exoplayer.DefaultLoadControl loadControl=new androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000,30_000,750,2000).setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false).build();
        player = new ExoPlayer.Builder(this).setLoadControl(loadControl).setMediaSourceFactory(mediaSourceFactory).build();
        probe=new PlaybackProbe(this,player,tuneElapsed);
        playerView.setPlayer(player);
        PictureShape.apply(this, playerView, channel == null ? "" : channel.id);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    cancelBufferingWatchdog();
                    endBuffering();
                    loading.setVisibility(View.GONE);
                    guideStatus = "Live now";
                    markAccess(Channel.ACCESS_AVAILABLE, "Playable on this connected Jio account.");
                    recordPlaybackReady();
                    refreshTransportState();
                    refreshGuideContent();
                    scheduleHideGuide(4500L);
                } else if (state == Player.STATE_BUFFERING) {
                    beginBuffering();
                    armBufferingWatchdog();
                    loading.setVisibility(View.VISIBLE);
                    guideStatus = "Buffering live television…";
                    refreshTransportState();
                    refreshGuideContent();
                } else if (state == Player.STATE_ENDED) {
                    cancelBufferingWatchdog();
                    endBuffering();
                    loading.setVisibility(View.GONE);
                    guideStatus = "Stream ended";
                    reportPlaybackSession("stream_ended");
                    refreshGuideContent();
                    showStreamEnded();
                }
            }

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                refreshTransportState();
            }

            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                refreshTransportState();
            }

            @Override public void onPlayerError(PlaybackException error) {
                endBuffering();
                loading.setVisibility(View.GONE);
                cancelBufferingWatchdog();
                if (!maybeAutoRecover(error, "media3_playback")) showPlaybackError(error);
            }
        });

        MediaItem.Builder item = new MediaItem.Builder().setUri(info.streamUrl);
        if (info.mimeType.toLowerCase(Locale.ROOT).contains("dash")
                || info.streamUrl.toLowerCase(Locale.ROOT).contains(".mpd")) {
            item.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (info.mimeType.toLowerCase(Locale.ROOT).contains("mpegurl")
                || info.streamUrl.toLowerCase(Locale.ROOT).contains(".m3u8")) {
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
        cancelBufferingWatchdog();
        if (player != null) {
            if(probe!=null){probe.close();probe=null;}
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        refreshTransportState();
    }

    private void loadEpg(Channel selected) {
        final int generation=playbackGeneration;
        executor.execute(()->{
            List<Program> programs=new ArrayList<>();
            try { programs.addAll(repository.api().fetchEpg(selected.id,0)); }
            catch(Exception error){Telemetry.error(this,"epg_refresh",error,Telemetry.data("stage","today"));}
            List<Program> today=new ArrayList<>(programs);
            mainHandler.post(()->applyPrograms(selected,generation,today));
            long now=System.currentTimeMillis();boolean hasNext=false;
            for(Program p:programs)if(p.startEpochMs>now){hasNext=true;break;}
            if(!hasNext&&generation==playbackGeneration){
                try{programs.addAll(repository.api().fetchEpg(selected.id,1));}catch(Exception ignored){}
                mainHandler.post(()->applyPrograms(selected,generation,programs));
            }
        });
    }
    private void applyPrograms(Channel selected,int generation,List<Program> programs){
        if(isFinishing()||generation!=playbackGeneration||channel==null||!channel.id.equals(selected.id))return;
        guidePrograms=new ArrayList<>(programs);guidePrograms.sort((a,b)->Long.compare(a.startEpochMs,b.startEpochMs));
        selectPrograms();refreshGuideContent();
    }
    private void selectPrograms(){
        int n=guidePrograms.size();long[] starts=new long[n],ends=new long[n];
        for(int i=0;i<n;i++){starts[i]=guidePrograms.get(i).startEpochMs;ends[i]=guidePrograms.get(i).endEpochMs;}
        int c=GuideTimeline.current(starts,ends,System.currentTimeMillis()),x=GuideTimeline.next(starts,ends,System.currentTimeMillis());
        currentProgram=c<0?null:guidePrograms.get(c);nextProgram=x<0?null:guidePrograms.get(x);
    }

    private void showProgrammeSchedule() {
        if (guidePrograms.isEmpty()) {
            Toast.makeText(this, "Programme guide is still loading", Toast.LENGTH_SHORT).show();
            loadEpg(channel);
            return;
        }
        long now = System.currentTimeMillis();
        int currentIndex = -1;
        for (int i = 0; i < guidePrograms.size(); i++) {
            if (guidePrograms.get(i).isLive(now)) {
                currentIndex = i;
                break;
            }
        }
        int from = Math.max(0, currentIndex < 0 ? 0 : currentIndex - 6);
        int to = Math.min(guidePrograms.size(), currentIndex < 0 ? 18 : currentIndex + 12);
        int selectedIndex = currentIndex;
        List<Program> visible = new ArrayList<>(guidePrograms.subList(from, to));
        String[] rows = new String[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            Program programme = visible.get(i);
            String badge = programme.isLive(now) ? "NOW" : programme.endEpochMs <= now ? "PAST" : "UP NEXT";
            rows[i] = badge + "  •  " + timeRange(programme) + "\n" + programme.title;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(channel.name + " programmes")
                .setItems(rows, (ignored, which) -> showProgrammeDetails(visible.get(which)))
                .setNegativeButton("Channel guide", (ignored, which) -> finish())
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            if (selectedIndex >= from && selectedIndex < to) {
                dialog.getListView().setSelection(selectedIndex - from);
            }
        });
        dialog.show();
    }

    private void showProgrammeDetails(Program programme) {
        long now = System.currentTimeMillis();
        String availability = programme.isLive(now)
                ? "Playing now."
                : programme.endEpochMs <= now
                ? "Past programme. Historical playback is not enabled in this candidate yet."
                : "Upcoming programme.";
        String details = timeRange(programme) + "\n\n"
                + (programme.description == null || programme.description.trim().isEmpty()
                ? "No description supplied by JioTV."
                : programme.description.trim())
                + "\n\n" + availability;
        new AlertDialog.Builder(this)
                .setTitle(programme.title)
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }

    private void refreshGuideContent() {
        if(!guidePrograms.isEmpty())selectPrograms();
        if (guidePanel == null || channel == null) return;
        guideChannel.setText(channel.displayNumber() + "  " + channel.name);
        guideScope.setText((scopeLabel + "  •  " + playbackScope.size()).toUpperCase(Locale.ROOT));

        long now = System.currentTimeMillis();
        if (currentProgram != null) {
            nowTitle.setText("NOW  " + currentProgram.title);
            nowTime.setText(timeRange(currentProgram));
            if (currentProgram.endEpochMs > currentProgram.startEpochMs) {
                long duration = currentProgram.endEpochMs - currentProgram.startEpochMs;
                int progress = (int) Math.max(0L, Math.min(1000L,
                        ((now - currentProgram.startEpochMs) * 1000L) / duration));
                programmeProgress.setProgress(progress);
            } else programmeProgress.setProgress(0);
        } else {
            nowTitle.setText("NOW  " + guideStatus);
            nowTime.setText("LIVE");
            programmeProgress.setProgress(0);
        }

        if (nextProgram != null) {
            nextTitle.setText("UP NEXT  " + formatTime(nextProgram.startEpochMs) + "  •  " + nextProgram.title);
        } else {
            nextTitle.setText("UP NEXT  Not listed by provider");
        }
    }

    private String timeRange(Program programme) {
        if (programme == null || programme.startEpochMs <= 0L || programme.endEpochMs <= 0L) return "LIVE";
        return formatTime(programme.startEpochMs) + " – " + formatTime(programme.endEpochMs);
    }

    private String formatTime(long epochMs) {
        if (epochMs <= 0L) return "";
        return TvUi.istTime(epochMs);
    }

    private int panelFocusRequest = 0;

    private void showGuide(boolean interactive, View preferredFocus) {
        if (guidePanel == null) return;
        refreshGuideContent();
        guidePanel.setVisibility(View.VISIBLE);
        long now = System.currentTimeMillis();
        panelShownAt = now;
        panelLastInteractionAt = now;
        if (hideGuide != null) mainHandler.removeCallbacks(hideGuide);
        // First opening must be immediately usable, not a visible panel with focus behind it.
        // Preserve a deliberate action focus during background refreshes; never steal it on a timer.
        View target = preferredFocus != null ? preferredFocus :
                (isGuideActionFocused() ? getCurrentFocus() : nextButton);
        final int focusRequest = ++panelFocusRequest;
        if (target != null && !target.requestFocus()) {
            final View pending = target;
            guidePanel.post(() -> {
                if (focusRequest == panelFocusRequest && !isFinishing()
                        && guidePanel.getVisibility() == View.VISIBLE && !isGuideActionFocused())
                    pending.requestFocus();
            });
        }
        scheduleHideGuide(interactive ? 7000L : 5000L);
    }

    private void notePanelInteraction() {
        panelLastInteractionAt = System.currentTimeMillis();
        scheduleHideGuide(7000L);
    }

    /**
     * The panel is governed by remote inactivity, not by retained focus. Android TV
     * buttons keep focus after the user stops pressing keys; treating focus as activity
     * was the reason the old strip could remain forever.
     */
    private void scheduleHideGuide(long delayMs) {
        if (hideGuide != null) mainHandler.removeCallbacks(hideGuide);
        hideGuide = () -> {
            if (guidePanel == null || guidePanel.getVisibility() != View.VISIBLE) return;
            long now = System.currentTimeMillis();
            long idle = Math.max(0L, now - panelLastInteractionAt);
            long visible = Math.max(0L, now - panelShownAt);
            if (idle >= 7000L || visible >= 14000L) {
                hideGuideNow();
                return;
            }
            long remaining = Math.min(7000L - idle, 14000L - visible);
            mainHandler.postDelayed(hideGuide, Math.max(250L, remaining));
        };
        mainHandler.postDelayed(hideGuide, Math.max(250L, delayMs));
    }

    private void hideGuideNow() {
        if (guidePanel == null) return;
        ++panelFocusRequest;
        guidePanel.setVisibility(View.GONE);
        playerView.requestFocus();
    }

    private boolean isGuideActionFocused() {
        View focused = getCurrentFocus();
        return focused == rewindButton || focused == pauseButton || focused == liveButton
                || focused == forwardButton || focused == previousButton
                || focused == pictureButton || focused == guideButton || focused == nextButton;
    }

    private void refreshTransportState() {
        if (rewindButton == null) return;
        boolean ready = player != null && player.getPlaybackState() == Player.STATE_READY;
        boolean seekable = ready && player.isCurrentMediaItemSeekable();
        rewindButton.setEnabled(seekable);
        forwardButton.setEnabled(seekable);
        liveButton.setEnabled(seekable && player.isCurrentMediaItemLive());
        pauseButton.setEnabled(ready);
        pauseButton.setText(ready && !player.isPlaying() ? "Play" : "Pause");
        rewindButton.setAlpha(seekable ? 1f : .46f);
        forwardButton.setAlpha(seekable ? 1f : .46f);
        liveButton.setAlpha(liveButton.isEnabled() ? 1f : .46f);
        pauseButton.setAlpha(ready ? 1f : .46f);
    }

    private void togglePause() {
        if (player == null || player.getPlaybackState() != Player.STATE_READY) {
            Toast.makeText(this, "The channel is not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String action;
        if (player.isPlaying()) {
            player.pause();
            action = "pause";
        } else {
            player.play();
            action = "play";
        }
        Telemetry.event(this, "transport_action", Telemetry.data(
                "action", action, "seekable", player.isCurrentMediaItemSeekable(),
                "live", player.isCurrentMediaItemLive(), "guide_scope", scopeLabel));
        refreshTransportState();
        notePanelInteraction();
    }

    private void seekBy(long deltaMs) {
        if (player == null || !player.isCurrentMediaItemSeekable()) {
            Toast.makeText(this, "This channel does not expose a rewind window", Toast.LENGTH_SHORT).show();
            return;
        }
        long target = Math.max(0L, player.getCurrentPosition() + deltaMs);
        long duration = player.getDuration();
        if (duration != C.TIME_UNSET && duration > 0L) target = Math.min(target, Math.max(0L, duration - 250L));
        player.seekTo(target);
        if (!player.isPlaying()) player.play();
        Telemetry.event(this, "transport_action", Telemetry.data(
                "action", deltaMs < 0 ? "rewind_15s" : "forward_15s",
                "seekable", true, "live", player.isCurrentMediaItemLive(),
                "guide_scope", scopeLabel));
        refreshTransportState();
        notePanelInteraction();
    }

    private void goLive() {
        if (player == null || !player.isCurrentMediaItemSeekable()) {
            Toast.makeText(this, "This channel is already live-only", Toast.LENGTH_SHORT).show();
            return;
        }
        player.seekToDefaultPosition();
        player.play();
        Telemetry.event(this, "transport_action", Telemetry.data(
                "action", "go_live", "seekable", true, "live", player.isCurrentMediaItemLive(),
                "guide_scope", scopeLabel));
        refreshTransportState();
        notePanelInteraction();
    }

    private void showAccessRequired(boolean explicitSubscription, String message) {
        if(isFinishing()||isDestroyed()||(errorDialog!=null&&errorDialog.isShowing()))return;
        loading.setVisibility(View.GONE);
        guideStatus = explicitSubscription ? "Subscription required" : "Jio access required";
        refreshGuideContent();
        showGuide(true, nextButton);
        errorDialog=new AlertDialog.Builder(this)
                .setTitle(explicitSubscription ? "Subscription required" : "Jio access required")
                .setMessage(message == null || message.trim().isEmpty()
                        ? (explicitSubscription
                            ? "Jio has marked this channel as paid for the connected account."
                            : "Jio did not authorise this channel for the connected account or TV device.")
                        : message)
                .setPositiveButton("Next working channel", (dialog, which) -> changeChannel(1, true))
                .setNegativeButton("Guide", (dialog, which) -> finish())
                .setNeutralButton("Try again", (dialog, which) -> retryCurrent())
                .setCancelable(false)
                .show();
    }

    private boolean maybeAutoRecover(Throwable error, String stage) {
        if(recoveryPending)return true;
        String technical = diagnostic(error).toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        final Channel retryChannel = channel;
        final int retryGeneration = playbackGeneration;
        boolean forbidden = technical.contains("403") || technical.contains("forbidden");
        boolean transientNetwork = technical.contains("io_network_connection") || technical.contains("behind_live_window") || technical.contains("unable to resolve host")
                || technical.contains("unknownhost")
                || technical.contains("timeout")
                || technical.contains("timed out")
                || technical.contains("connection reset")
                || technical.contains("network is unreachable")
                || technical.contains("http 500")
                || technical.contains("http 502")
                || technical.contains("http 503")
                || technical.contains("http 504");
        if (forbidden && automaticStreamRefreshes < 1) {
            recoveryPending=true;
            automaticStreamRefreshes++;
            lastAutomaticStreamRefreshAt = now;
            Telemetry.event(this, "playback_auto_recovery", Telemetry.data(
                    "reason", "fresh_authorization", "stage", stage, "attempt", automaticStreamRefreshes));
            guideStatus = "Refreshing channel authorisation…";
            refreshGuideContent();
            showGuide(false, null);
            mainHandler.postDelayed(() -> {
                if (retryChannel != null && retryGeneration == playbackGeneration && retryChannel == channel) {
                    startChannel(retryChannel, false);
                }
            }, 600L);
            return true;
        }
        if (transientNetwork && automaticNetworkRetries < 1) {
            recoveryPending=true;
            automaticNetworkRetries++;
            Telemetry.event(this, "playback_auto_recovery", Telemetry.data(
                    "reason", "network_retry", "stage", stage, "attempt", automaticNetworkRetries));
            guideStatus = "Network paused — retrying once…";
            refreshGuideContent();
            showGuide(false, null);
            mainHandler.postDelayed(() -> {
                if (retryChannel != null && retryGeneration == playbackGeneration && retryChannel == channel) {
                    startChannel(retryChannel, false);
                }
            }, 1200L);
            return true;
        }
        return false;
    }

    private void retryCurrent() {
        repository.clearTemporaryAccess(channel);
        startChannel(channel, true);
    }

    private void showStreamEnded() {
        if(isFinishing()||isDestroyed()||(errorDialog!=null&&errorDialog.isShowing()))return;
        showGuide(true, nextButton);
        errorDialog=new AlertDialog.Builder(this)
                .setTitle("Live feed ended")
                .setMessage("This live feed ended or was closed by the provider. You can reopen it or continue to the next working channel.")
                .setPositiveButton("Next working channel", (dialog, which) -> changeChannel(1, true))
                .setNegativeButton("Guide", (dialog, which) -> finish())
                .setNeutralButton("Reopen channel", (dialog, which) -> retryCurrent())
                .show();
    }

    private void armBufferingWatchdog() {
        if (bufferingWatchdog != null) mainHandler.removeCallbacks(bufferingWatchdog);
        final int generation = playbackGeneration;
        final String channelId = channel == null ? "" : channel.id;
        final long threshold = playbackReadyAt <= 0L ? 18_000L : 15_000L;
        bufferingWatchdog = () -> {
            if (generation != playbackGeneration || player == null || channel == null
                    || !channelId.equals(channel.id) || player.getPlaybackState() != Player.STATE_BUFFERING) return;
            if (automaticBufferRecoveries < 1) {
                automaticBufferRecoveries++;
                Telemetry.event(this, "playback_auto_recovery", Telemetry.data(
                        "reason", "buffer_watchdog", "attempt", automaticBufferRecoveries,
                        "threshold_ms", threshold, "guide_scope", scopeLabel));
                guideStatus = "Stream stalled — getting a fresh live feed…";
                refreshGuideContent();
                showGuide(false, null);
                final Channel retryChannel = channel;
                mainHandler.postDelayed(() -> {
                    if (retryChannel != null && generation == playbackGeneration && retryChannel == channel) {
                        startChannel(retryChannel, false);
                    }
                }, 350L);
                return;
            }
            showPlaybackError(new IllegalStateException(
                    "The live stream remained buffering after an automatic refresh"));
        };
        mainHandler.postDelayed(bufferingWatchdog, threshold);
    }

    private void cancelBufferingWatchdog() {
        if (bufferingWatchdog != null) {
            mainHandler.removeCallbacks(bufferingWatchdog);
            bufferingWatchdog = null;
        }
    }

    private void showPlaybackError(Throwable error) {
        if(isFinishing()||isDestroyed()||(errorDialog!=null&&errorDialog.isShowing()))return;
        recoveryPending=false;
        cancelBufferingWatchdog();
        loading.setVisibility(View.GONE);
        releasePlayer();
        String technical = diagnostic(error);
        String lower = technical.toLowerCase(Locale.ROOT);
        boolean auth = lower.contains("401") || lower.contains("419")
                || lower.contains("token") || lower.contains("unauthor");
        boolean forbidden = lower.contains("403") || lower.contains("forbidden");
        int httpStatus = forbidden ? 403 : (lower.contains("401") ? 401 : (lower.contains("419") ? 419 : 0));
        String reference = Telemetry.playbackFailure(this, channel, "media3_playback", technical, httpStatus, error);
        String friendly = "This channel could not start. Another channel may still work.";
        if (auth) {
            friendly = "The Jio session needs to be connected again before this channel can play.";
        } else if (forbidden) {
            friendly = "Jio did not authorise this channel after refreshing the session. It may need a subscription or may be restricted on this TV.";
            markAccess(Channel.ACCESS_UNAVAILABLE, technical);
        } else if (lower.contains("unable to resolve host") || lower.contains("unknownhost")) {
            friendly = "This TV could not resolve the Jio service address. Check the TV internet or DNS, then retry; other cached screens may still open.";
        } else if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("connection reset")) {
            friendly = "The Jio stream took too long to respond. GharTV retried once; you can retry again or continue to the next channel.";
        } else if (lower.contains("drm") || lower.contains("widevine")) {
            friendly = "This protected channel was not accepted by the TV playback system. Another channel may still work.";
        }
        guideStatus = "Channel unavailable";
        refreshGuideContent();
        showGuide(true, nextButton);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Live channel unavailable")
                .setMessage(withReference(friendly, reference))
                .setPositiveButton("Next working channel", (dialog, which) -> changeChannel(1, true))
                .setNegativeButton("Guide", (dialog, which) -> finish());
        if (auth) builder.setNeutralButton("Reconnect Jio", (dialog, which) -> reconnectJio());
        else builder.setNeutralButton("Retry", (dialog, which) -> retryCurrent());
        errorDialog=builder.show();
    }

    private void showAuthRequired(String message) {
        if(isFinishing()||isDestroyed()||(errorDialog!=null&&errorDialog.isShowing()))return;
        loading.setVisibility(View.GONE);
        hideGuideNow();
        String body = message == null || message.trim().isEmpty()
                ? "The Jio session needs to be connected again before this channel can play."
                : message;
        errorDialog=new AlertDialog.Builder(this)
                .setTitle("Reconnect JioTV")
                .setMessage(body)
                .setPositiveButton("Reconnect Jio", (dialog, which) -> reconnectJio())
                .setNegativeButton("Guide", (dialog, which) -> finish())
                .setNeutralButton("Next working channel", (dialog, which) -> changeChannel(1, true))
                .setCancelable(false)
                .show();
    }

    private void markAccess(String state, String message) {
        repository.applyAccessState(channel, state, message);
        for (Channel scoped : playbackScope) {
            if (channel != null && scoped.id.equals(channel.id)) {
                scoped.accessState = channel.accessState;
                scoped.accessMessage = channel.accessMessage;
                scoped.accessUpdatedAt = channel.accessUpdatedAt;
            }
        }
        for (Channel cached : allChannels) {
            if (channel != null && cached.id.equals(channel.id)) {
                cached.accessState = channel.accessState;
                cached.accessMessage = channel.accessMessage;
                cached.accessUpdatedAt = channel.accessUpdatedAt;
            }
        }
        repository.invalidateIndex();
    }

    private void reconnectJio() {
        JioSession.clear(this);
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(login);
        finish();
    }

    private void resetPlaybackMetrics() {
        tuneStartedAt = System.currentTimeMillis();
        playbackReadyAt = 0L;
        bufferingStartedAt = 0L;
        totalBufferingMs = 0L;
        bufferingCount = 0;
        playbackSessionReported = false;
        playbackProtocol = "unknown";
        playbackDrm = false;
    }

    private void beginBuffering() {
        if (bufferingStartedAt == 0L) {
            bufferingStartedAt = System.currentTimeMillis();
            bufferingCount++;
        }
    }

    private void endBuffering() {
        if (bufferingStartedAt > 0L) {
            totalBufferingMs += Math.max(0L, System.currentTimeMillis() - bufferingStartedAt);
            bufferingStartedAt = 0L;
        }
    }

    private void recordPlaybackReady() {
        if (playbackReadyAt != 0L) return;
        playbackReadyAt = System.currentTimeMillis();
        historyStore.recordReady(channel);
        repository.invalidateIndex();
        Telemetry.event(this, "playback_ready", Telemetry.data(
                "protocol", playbackProtocol,
                "drm", playbackDrm,
                "startup_ms", Math.max(0L, playbackReadyAt - tuneStartedAt),
                "buffer_count", bufferingCount,
                "buffer_ms", totalBufferingMs,
                "category", channel == null ? "unknown" : channel.category,
                "language", channel == null ? "unknown" : channel.language,
                "guide_scope", scopeLabel));
    }

    private void reportPlaybackSession(String reason) {
        if (playbackSessionReported || playbackReadyAt <= 0L || channel == null) return;
        endBuffering();
        playbackSessionReported = true;
        long durationMs = Math.max(0L, System.currentTimeMillis() - playbackReadyAt);
        historyStore.recordSession(channel, durationMs);
        repository.invalidateIndex();
        Telemetry.event(this, "playback_session", Telemetry.data(
                "end_reason", reason,
                "view_duration_bucket", durationBucket(durationMs),
                "protocol", playbackProtocol,
                "drm", playbackDrm,
                "buffer_count", bufferingCount,
                "buffer_ms_bucket", durationBucket(totalBufferingMs),
                "category", channel.category,
                "language", channel.language,
                "guide_scope", scopeLabel));
    }

    private String durationBucket(long milliseconds) {
        if (milliseconds < 30_000L) return "under_30s";
        if (milliseconds < 2 * 60_000L) return "30s_to_2m";
        if (milliseconds < 10 * 60_000L) return "2m_to_10m";
        if (milliseconds < 30 * 60_000L) return "10m_to_30m";
        return "over_30m";
    }

    private String withReference(String message, String reference) {
        String body = message == null || message.trim().isEmpty() ? "The channel could not start." : message;
        if (!Telemetry.isEnabled(this) || reference == null || reference.isEmpty()) return body;
        return body + "\n\nDiagnostics reference: " + reference;
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

    private List<Channel> parseScope(String raw) {
        List<Channel> scoped = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        try {
            JSONArray array = raw == null ? null : new JSONArray(raw);
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    int number = array.optInt(i, -1);
                    Channel value = repository.byNumber(allChannels, number);
                    if (value != null && seen.add(value.number)) scoped.add(value);
                }
            }
        } catch (Exception ignored) {}
        if(scoped.isEmpty()&&raw==null){
            // An explicit empty/broken scope is never widened. Only legacy absent extras may use their category.
            if("All channels".equals(scopeLabel))scoped.addAll(allChannels);
            else if(!scopeLabel.contains(" · search"))scoped.addAll(repository.filter(allChannels,scopeLabel,""));
        }
        boolean containsCurrent = false;
        for (Channel value : scoped) {
            if (channel != null && value.number == channel.number) {
                containsCurrent = true;
                break;
            }
        }
        if (scoped.isEmpty() && channel != null) scoped.add(channel);
        return scoped;
    }

    private String cleanScopeLabel(String value) {
        if (value == null || value.trim().isEmpty() || "All".equalsIgnoreCase(value.trim())) return "All channels";
        return value.trim();
    }

    private void changeChannel(int direction) {
        changeChannel(direction, false);
    }

    private void changeChannel(int direction, boolean avoidKnownFailures) {
        Telemetry.event(this, "channel_change", Telemetry.data(
                "direction", direction > 0 ? "next" : "previous",
                "guide_scope", scopeLabel,
                "avoid_known_failures", avoidKnownFailures));
        if (playbackScope.size() <= 1) {
            Toast.makeText(this, "No other channel in " + scopeLabel, Toast.LENGTH_SHORT).show();
            showGuide(true, guideButton);
            return;
        }
        int currentNumber = channel == null ? 0 : channel.number;
        Channel next = avoidKnownFailures
                ? repository.nextLikelyWorking(playbackScope, currentNumber, direction)
                : repository.next(playbackScope, currentNumber, direction);
        if (next == null) return;
        startChannel(next, true);
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
        boolean insideScope = false;
        for (Channel scoped : playbackScope) {
            if (scoped.number == requested.number) {
                insideScope = true;
                break;
            }
        }
        if(!insideScope)Toast.makeText(this,"Direct tune · CH ± returns to "+scopeLabel,Toast.LENGTH_SHORT).show();
        startChannel(requested, true);
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

        if (guidePanel != null && guidePanel.getVisibility() == View.VISIBLE
                && key != KeyEvent.KEYCODE_BACK) {
            notePanelInteraction();
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
                if (guidePanel.getVisibility() == View.VISIBLE) hideGuideNow();
                else showGuide(true, guideButton);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (guidePanel.getVisibility() != View.VISIBLE) {
                    showGuide(true, guideButton);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (guidePanel.getVisibility() != View.VISIBLE) {
                    showGuide(true, previousButton);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (guidePanel.getVisibility() != View.VISIBLE) {
                    showGuide(true, nextButton);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (guidePanel.getVisibility() != View.VISIBLE) {
                    showGuide(true, guideButton);
                    return true;
                }
                if (isGuideActionFocused()) break;
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePause();
                showGuide(false, null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-15_000L);
                showGuide(false, null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(15_000L);
                showGuide(false, null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                goLive();
                showGuide(false, null);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (guidePanel.getVisibility() == View.VISIBLE) {
                    hideGuideNow();
                    return true;
                }
                finish();
                return true;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    private String diagnostic(Throwable error){
        StringBuilder b=new StringBuilder();Throwable e=error;
        for(int i=0;e!=null&&i<6;i++,e=e.getCause()){
            if(i>0)b.append(" / ");b.append(e.getClass().getSimpleName());
            if(e instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                b.append(" HTTP ").append(((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)e).responseCode);
            if(e instanceof PlaybackException)b.append(" ").append(((PlaybackException)e).getErrorCodeName());
            if(e.getMessage()!=null)b.append(" ").append(e.getMessage().replaceAll("https?://[^\\s]+","[endpoint]").replaceAll("(?i)(token|cookie|authorization|password)[=:][^\\s]+","$1=[redacted]"));
        }
        return b.toString();
    }
    private String readable(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? (error == null ? "Unknown playback error" : error.getClass().getSimpleName())
                : message;
    }
}
