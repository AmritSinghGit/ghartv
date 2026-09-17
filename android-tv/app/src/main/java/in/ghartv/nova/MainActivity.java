package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements ChannelNavigator.Listener {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ExecutorService epgExecutor = Executors.newFixedThreadPool(2);
    private final java.util.Set<String> epgPending = new java.util.HashSet<>();
    private final Map<String, List<Program>> epgCache = new HashMap<>();
    private final Runnable visibleEpgTask = this::loadVisibleEpg;
    private boolean guideActive;
    private final Map<String, Long> epgCacheTime = new HashMap<>();

    private ChannelRepository repository;
    private List<Channel> allChannels = new ArrayList<>();
    private List<Channel> visibleChannels = new ArrayList<>();
    private ChannelAdapter channelAdapter;
    private ChipAdapter chipAdapter;
    private RecyclerView channelGrid;
    private Channel selectedChannel;
    private String selectedCategory = "All";
    private String searchQuery = "";
    private boolean catalogueBusy;
    private boolean redirectingToLogin;
    private boolean guideHealthyReported;

    private TextView heroNumber;
    private ImageView heroLogo;
    private TextView heroTitle;
    private TextView heroSource;
    private TextView heroNow;
    private TextView heroNext;
    private ProgressBar heroProgress;
    private Button playButton;
    private Button favouriteButton;
    private Button accountButton;
    private TextView catalogueStatus;
    private TextView clock;
    private TextView numberOverlay;
    private TextView emptyState;
    private ProgressBar guideLoading;
    private PlayerView heroPreviewView;
    private ProgressBar heroPreviewLoading;
    private TextView heroPreviewStatus;
    private HeroPreviewController heroPreviewController;

    private ChannelNavigator navigator;
    private Runnable pendingEpgLoad;
    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(TvUi.istTime(System.currentTimeMillis()));
            mainHandler.postDelayed(this, 30_000L);
        }
    };

    private boolean initialResume=true;
    private int diskLoadGeneration;
    private final long screenStarted=android.os.SystemClock.elapsedRealtime();
    private boolean guideMeasured;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TvUi.immersive(this);
        NetworkDiagnostics.reviewProbe(this);
        Telemetry.beginLaunch(this, "main_created");
        Telemetry.launchStage(this, "session_check");

        if (!JioSession.load(this).isPresent()) {
            Telemetry.launchStage(this, "route_login");
            Telemetry.markLaunchHealthy(this);
            routeToLogin();
            return;
        }

        Telemetry.launchStage(this, "repository_create");
        repository = new ChannelRepository(this);
        navigator = new ChannelNavigator(this);
        selectedCategory = repository.lastCategory();
        FamilyTheme.applyPreviewIntent(this);
        Telemetry.launchStage(this, "ui_build");
        setContentView(buildUi());
        LocalPerformance.sampleStartupFrames(this);
        Telemetry.screen(this, "guide");
        Telemetry.launchStage(this, "guide_content_set");
        mainHandler.postDelayed(() -> Telemetry.maybeRequestConsent(this), 1200L);
        mainHandler.post(clockTicker);
        mainHandler.post(()->loadFromDisk(true));
        mainHandler.postDelayed(() -> UpdateManager.check(this, false), 2600L);
    }

    @Override protected void onResume() {
        super.onResume();
        TvUi.immersive(this);
        if (repository == null) return;
        guideActive=true;
        if (!JioSession.load(this).isPresent()) routeToLogin();
        else if(initialResume)initialResume=false;else loadFromDisk(false);
    }

    @Override protected void onPause() {
        guideActive=false;
        mainHandler.removeCallbacks(visibleEpgTask);
        if (heroPreviewController != null) heroPreviewController.stop(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (pendingEpgLoad != null) mainHandler.removeCallbacks(pendingEpgLoad);
        mainHandler.removeCallbacks(clockTicker);
        if (heroPreviewController != null) heroPreviewController.release();
        executor.shutdownNow();
        epgExecutor.shutdownNow();
        mainHandler.removeCallbacks(visibleEpgTask);
        super.onDestroy();
    }

    private void routeToLogin() {
        if (redirectingToLogin) return;
        redirectingToLogin = true;
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(login);
        finish();
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new AuroraBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(TvUi.dp(this, 28), TvUi.dp(this, 14), TvUi.dp(this, 28), TvUi.dp(this, 14));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        shell.addView(buildHeader(), new LinearLayout.LayoutParams(-1, TvUi.dp(this, 54)));

        View celebration = FamilyTheme.banner(this);
        LinearLayout.LayoutParams celebrationParams = new LinearLayout.LayoutParams(
                -1, FamilyTheme.isBirthday(this) ? TvUi.dp(this, 40) : 0);
        celebrationParams.bottomMargin = FamilyTheme.isBirthday(this) ? TvUi.dp(this, 4) : 0;
        shell.addView(celebration, celebrationParams);

        catalogueStatus = TvUi.label(this, "Connecting to JioTV…", 13, TvUi.MUTED, true);
        catalogueStatus.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(catalogueStatus, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 28)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyParams.topMargin = TvUi.dp(this, 6);
        shell.addView(body, bodyParams);

        LinearLayout hero = buildHero();
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(0, -1, .36f);
        heroParams.rightMargin = TvUi.dp(this, 16);
        body.addView(hero, heroParams);
        body.addView(buildGuide(), new LinearLayout.LayoutParams(0, -1, .64f));

        numberOverlay = TvUi.label(this, "", 30, TvUi.TEXT, true);
        numberOverlay.setGravity(Gravity.CENTER);
        numberOverlay.setBackground(TvUi.rounded(Color.argb(244, 3, 11, 18), 24, TvUi.MINT, 2, this));
        numberOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams overlay = new FrameLayout.LayoutParams(TvUi.dp(this, 170), TvUi.dp(this, 60), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        overlay.topMargin = TvUi.dp(this, 70);
        root.addView(numberOverlay, overlay);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = TvUi.label(this, "GHAR TV", 24, TvUi.TEXT, true);
        brand.setLetterSpacing(.11f);
        header.addView(brand, new LinearLayout.LayoutParams(-2, -1));

        TextView live = TvUi.label(this, "JIO LIVE", 11, TvUi.MINT, true);
        live.setGravity(Gravity.CENTER);
        live.setPadding(TvUi.dp(this, 14), 0, TvUi.dp(this, 14), 0);
        live.setBackground(TvUi.rounded(Color.argb(70, 115, 245, 194), 16, TvUi.MINT, 1, this));
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(-2, TvUi.dp(this, 28));
        liveParams.leftMargin = TvUi.dp(this, 14);
        header.addView(live, liveParams);
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        Button search = actionButton("Find");
        search.setOnClickListener(view -> showSearch());
        header.addView(search, headerButtonParams());

        Button refresh = actionButton("Update guide");
        refresh.setOnClickListener(view -> refreshCatalogue(true));
        header.addView(refresh, headerButtonParams());

        Button movies = actionButton("Punjabi +");
        movies.setOnClickListener(view -> startActivity(new Intent(this, MovieHubActivity.class)));
        header.addView(movies, headerButtonParams());

        accountButton = actionButton("Jio account");
        accountButton.setOnClickListener(view -> showAccountMenu());
        LinearLayout.LayoutParams accountParams = new LinearLayout.LayoutParams(TvUi.dp(this, 145), TvUi.dp(this, 38));
        accountParams.leftMargin = TvUi.dp(this, 10);
        header.addView(accountButton, accountParams);

        clock = TvUi.label(this, "", 17, TvUi.TEXT, true);
        clock.setGravity(Gravity.CENTER | Gravity.END);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(TvUi.dp(this, 82), -1);
        clockParams.leftMargin = TvUi.dp(this, 14);
        header.addView(clock, clockParams);
        return header;
    }

    private LinearLayout.LayoutParams headerButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(TvUi.dp(this, 108), TvUi.dp(this, 40));
        params.leftMargin = TvUi.dp(this, 10);
        return params;
    }

    private LinearLayout buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(TvUi.dp(this, 20), TvUi.dp(this, 16), TvUi.dp(this, 20), TvUi.dp(this, 14));
        hero.setBackground(TvUi.rounded(Color.argb(245, 12, 18, 36), 18, Color.argb(60, 83, 228, 255), 1, this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView onAir = TvUi.label(this, "●  LIVE / YOUR SELECTION", 11, TvUi.MINT, true);
        top.addView(onAir, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 28)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        heroNumber = TvUi.label(this, "---", 17, TvUi.CYAN, true);
        heroNumber.setGravity(Gravity.CENTER);
        heroNumber.setPadding(TvUi.dp(this, 14), 0, TvUi.dp(this, 14), 0);
        heroNumber.setBackground(TvUi.rounded(Color.argb(120, 0, 0, 0), 17, Color.argb(85, 83, 228, 255), 1, this));
        top.addView(heroNumber, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 30)));
        hero.addView(top, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 32)));

        FrameLayout previewHost = new FrameLayout(this);
        previewHost.setClipToOutline(true);
        previewHost.setBackground(TvUi.rounded(Color.rgb(2, 9, 15), 18,
                Color.argb(70, 110, 231, 255), 1, this));
        previewHost.setOnClickListener(view -> play(selectedChannel));

        heroPreviewView = new PlayerView(this);
        previewHost.addView(heroPreviewView, new FrameLayout.LayoutParams(-1, -1));

        heroLogo = new ImageView(this);
        heroLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroLogo.setPadding(TvUi.dp(this, 8), TvUi.dp(this, 8), TvUi.dp(this, 8), TvUi.dp(this, 8));
        heroLogo.setBackground(TvUi.rounded(Color.argb(46, 255, 255, 255), 18,
                Color.argb(38, 255, 255, 255), 1, this));
        previewHost.addView(heroLogo, new FrameLayout.LayoutParams(
                TvUi.dp(this, 88), TvUi.dp(this, 88), Gravity.CENTER));

        heroPreviewLoading = new ProgressBar(this);
        previewHost.addView(heroPreviewLoading, new FrameLayout.LayoutParams(
                TvUi.dp(this, 34), TvUi.dp(this, 34), Gravity.CENTER));

        heroPreviewStatus = TvUi.label(this,
                "Channel spotlight  •  press OK to watch",
                9, Color.WHITE, true);
        heroPreviewStatus.setGravity(Gravity.CENTER_VERTICAL);
        heroPreviewStatus.setPadding(TvUi.dp(this, 9), 0, TvUi.dp(this, 9), 0);
        heroPreviewStatus.setBackground(TvUi.rounded(Color.argb(180, 0, 0, 0), 12,
                Color.TRANSPARENT, 0, this));
        FrameLayout.LayoutParams previewStatusParams = new FrameLayout.LayoutParams(
                -1, TvUi.dp(this, 25), Gravity.BOTTOM);
        previewStatusParams.setMargins(TvUi.dp(this, 6), 0, TvUi.dp(this, 6), TvUi.dp(this, 6));
        previewHost.addView(heroPreviewStatus, previewStatusParams);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 142));
        previewParams.topMargin = TvUi.dp(this, 6);
        hero.addView(previewHost, previewParams);

        heroTitle = TvUi.label(this, "Your live television", 22, TvUi.TEXT, true);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = TvUi.dp(this, 8);
        hero.addView(heroTitle, titleParams);

        heroSource = TvUi.label(this, "JioTV • connected to your account", 12, TvUi.CYAN, true);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 22));
        sourceParams.topMargin = TvUi.dp(this, 2);
        hero.addView(heroSource, sourceParams);

        heroNow = TvUi.label(this, "Live now", 16, TvUi.TEXT, true);
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(-1, -2);
        nowParams.topMargin = TvUi.dp(this, 10);
        hero.addView(heroNow, nowParams);

        heroNext = TvUi.label(this, "Choose a channel to see what is on next", 12, TvUi.MUTED, false);
        heroNext.setMaxLines(2);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, -2);
        nextParams.topMargin = TvUi.dp(this, 3);
        hero.addView(heroNext, nextParams);

        heroProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        heroProgress.setMax(1000);
        heroProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(TvUi.MINT));
        heroProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.argb(42, 255, 255, 255)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 4));
        progressParams.topMargin = TvUi.dp(this, 8);
        hero.addView(heroProgress, progressParams);
        hero.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        playButton = actionButton("▶  WATCH LIVE");
        playButton.setTextSize(14);
        playButton.setOnClickListener(view -> play(selectedChannel));
        TvUi.focusCard(playButton, Color.rgb(23, 112, 115), Color.rgb(34, 156, 151), 22);
        actions.addView(playButton, new LinearLayout.LayoutParams(0, TvUi.dp(this, 46), 1f));

        favouriteButton = actionButton("☆  Favourite");
        favouriteButton.setOnClickListener(view -> toggleFavourite(selectedChannel));
        LinearLayout.LayoutParams favouriteParams = new LinearLayout.LayoutParams(TvUi.dp(this, 126), TvUi.dp(this, 46));
        favouriteParams.leftMargin = TvUi.dp(this, 8);
        actions.addView(favouriteButton, favouriteParams);
        Button previewButton=TvUi.button(this,"Preview 12s",false);
        previewButton.setOnClickListener(v->{if(heroPreviewController!=null)heroPreviewController.previewNow();});
        hero.addView(previewButton,new LinearLayout.LayoutParams(-1,TvUi.dp(this,30)));
        hero.addView(actions, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 46)));

        TextView hints = TvUi.label(this, "NUMBER to tune  •  CH ± to switch  •  GUIDE to come back", 10, TvUi.MUTED, false);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hintsParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 24));
        hintsParams.topMargin = TvUi.dp(this, 4);
        hero.addView(hints, hintsParams);
        heroPreviewController = new HeroPreviewController(
                this, repository, heroPreviewView, heroLogo, heroPreviewLoading, heroPreviewStatus);
        return hero;
    }

    private View buildGuide() {
        LinearLayout guide = new LinearLayout(this);
        guide.setOrientation(LinearLayout.VERTICAL);
        guide.setPadding(TvUi.dp(this, 14), TvUi.dp(this, 10), TvUi.dp(this, 8), TvUi.dp(this, 8));
        guide.setBackground(TvUi.rounded(Color.argb(198, 5, 16, 26), 28, Color.argb(48, 255, 255, 255), 1, this));

        LinearLayout guideTitleRow = new LinearLayout(this);
        guideTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = TvUi.label(this, "LIVE CHANNELS", 16, TvUi.TEXT, true);
        title.setLetterSpacing(.08f);
        guideTitleRow.addView(title, new LinearLayout.LayoutParams(-2, -1));
        guideTitleRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        guideLoading = new ProgressBar(this);
        guideLoading.setIndeterminate(true);
        guideLoading.setVisibility(View.GONE);
        guideTitleRow.addView(guideLoading, new LinearLayout.LayoutParams(TvUi.dp(this, 26), TvUi.dp(this, 26)));
        guide.addView(guideTitleRow, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 34)));

        RecyclerView chips = new RecyclerView(this);
        chips.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        chipAdapter = new ChipAdapter(value -> {
            selectedCategory = value;
            repository.setLastCategory(value);
            Telemetry.event(this, "guide_filter", Telemetry.data("category", value));
            renderGuide(true);
        });
        chips.setAdapter(chipAdapter);
        guide.addView(chips, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 44)));

        FrameLayout gridHost = new FrameLayout(this);
        channelGrid = new RecyclerView(this);
        GridLayoutManager gridManager = new GridLayoutManager(this, 3);
        gridManager.setInitialPrefetchItemCount(9);
        channelGrid.setLayoutManager(gridManager);
        channelGrid.setItemViewCacheSize(18);
        channelAdapter = new ChannelAdapter(repository.favourites(), new ChannelAdapter.Listener() {
            @Override public void onFocused(Channel channel, int position) { select(channel); }
            @Override public void onPlay(Channel channel) { play(channel); }
            @Override public void onFavourite(Channel channel) { toggleFavourite(channel); }
        });
        channelGrid.setAdapter(channelAdapter);
        channelGrid.setItemAnimator(null);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus,newFocus)->{
            boolean inGrid=false;android.view.ViewParent parent=newFocus==null?null:newFocus.getParent();
            while(parent!=null){if(parent==channelGrid){inGrid=true;break;}parent=parent.getParent();}
            if(heroPreviewController!=null)heroPreviewController.focused(guideActive&&inGrid);
        });
        channelGrid.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrollStateChanged(RecyclerView v,int state){if(state==RecyclerView.SCROLL_STATE_IDLE)scheduleVisibleEpg();}
        });
        gridHost.addView(channelGrid, new FrameLayout.LayoutParams(-1, -1));

        emptyState = TvUi.label(this, "", 18, TvUi.MUTED, true);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(TvUi.dp(this, 36), TvUi.dp(this, 36), TvUi.dp(this, 36), TvUi.dp(this, 36));
        emptyState.setVisibility(View.GONE);
        gridHost.addView(emptyState, new FrameLayout.LayoutParams(-1, -1));
        guide.addView(gridHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        return guide;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(TvUi.TEXT);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(TvUi.dp(this, 8), 0, TvUi.dp(this, 8), 0);
        TvUi.focusCard(button, Color.rgb(10, 31, 45), Color.rgb(18, 75, 91), 18);
        return button;
    }

    private void loadFromDisk(boolean focus) {
        JioSession session = JioSession.load(this);
        if (!session.isPresent()) {
            routeToLogin();
            return;
        }
        String mobile = session.mobile;
        String suffix = mobile.length() >= 4 ? mobile.substring(mobile.length() - 4) : "connected";
        accountButton.setText("Jio ••••" + suffix);
        final int loadId=++diskLoadGeneration;
        executor.execute(()->{
        final List<Channel> loaded=repository.loadAll();
        mainHandler.post(()->{
        if(isFinishing()||isDestroyed()||loadId!=diskLoadGeneration)return;
        allChannels = loaded;
        Telemetry.launchStage(this, "catalogue_loaded");
        renderGuide(focus);
        if(!guideMeasured){guideMeasured=true;LocalPerformance.record(this,"guide_ready_ms",android.os.SystemClock.elapsedRealtime()-screenStarted);}
        updateCatalogueStatus(null);

        long age = System.currentTimeMillis() - repository.lastUpdatedAt();
        if (allChannels.isEmpty()) refreshCatalogue(true);
        else if (age > AppConfig.CATALOGUE_REFRESH_MS) refreshCatalogue(false);
        });});
    }

    private void refreshCatalogue(boolean ownerInitiated) {
        if (catalogueBusy) return;
        if (!JioSession.load(this).isPresent()) {
            routeToLogin();
            return;
        }
        long refreshStartedAt = System.currentTimeMillis();
        catalogueBusy = true;
        guideLoading.setVisibility(View.VISIBLE);
        catalogueStatus.setText("Updating your live channel guide…");
        emptyState.setVisibility(allChannels.isEmpty() ? View.VISIBLE : View.GONE);
        if (allChannels.isEmpty()) emptyState.setText("Getting your live channels ready…");
        executor.execute(() -> {
            try {
                List<Channel> refreshed = repository.refreshJio();
                mainHandler.post(() -> {
                    catalogueBusy = false;
                    guideLoading.setVisibility(View.GONE);
                    allChannels = refreshed;
                    renderGuide(true);
                    updateCatalogueStatus(null);
                    Telemetry.event(this, "catalogue_refresh", Telemetry.data(
                            "result", "success",
                            "manual", ownerInitiated,
                            "duration_ms", System.currentTimeMillis() - refreshStartedAt,
                            "channel_count", refreshed.size()));
                    if (ownerInitiated) Toast.makeText(this, "Live guide updated", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    catalogueBusy = false;
                    guideLoading.setVisibility(View.GONE);
                    String message = readable(error);
                    String reference = Telemetry.error(this, "catalogue_refresh", error, Telemetry.data(
                            "manual", ownerInitiated,
                            "duration_ms", System.currentTimeMillis() - refreshStartedAt));
                    updateCatalogueStatus(message + (Telemetry.isEnabled(this) ? "  •  " + reference : ""));
                    if (allChannels.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        emptyState.setText("Your live guide could not load yet.\n\n" + message + "\n\nChoose Update guide to try again.");
                    }
                    if (ownerInitiated) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderGuide(boolean requestFocus) {
        List<String> categories = repository.categories(allChannels);
        if (!categories.contains(selectedCategory)) selectedCategory = "All";
        chipAdapter.submit(categories, selectedCategory);
        visibleChannels = repository.filter(allChannels, selectedCategory, searchQuery);
        channelAdapter.submit(visibleChannels, repository.favourites());
        boolean empty = visibleChannels.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        channelGrid.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            emptyState.setText(allChannels.isEmpty() ? "Getting your live channels ready…" : "No channels match this view.");
            select(null);
            return;
        }

        if (!guideHealthyReported) {
            guideHealthyReported = true;
            Telemetry.launchStage(this, "guide_rendered");
            Telemetry.markLaunchHealthy(this);
        }

        Channel preferred = repository.byNumber(visibleChannels, repository.lastChannel());
        if (preferred == null) preferred = visibleChannels.get(0);
        select(preferred);
        scheduleVisibleEpg();
        if (requestFocus) {
            final int position = Math.max(0, visibleChannels.indexOf(preferred));
            channelGrid.post(() -> {
                channelGrid.scrollToPosition(position);
                RecyclerView.ViewHolder holder = channelGrid.findViewHolderForAdapterPosition(position);
                if (holder != null) holder.itemView.requestFocus();
                else channelGrid.requestFocus();
            });
        }
    }

    private void select(Channel channel) {
        selectedChannel = channel;
        if (heroPreviewController != null) heroPreviewController.select(channel);
        if (channel == null) {
            heroNumber.setText("---");
            heroTitle.setText("No channel selected");
            heroSource.setText("JioTV • your account");
            heroNow.setText("Live now");
            heroNext.setText("Choose a live channel from the guide");
            heroLogo.setImageDrawable(null);
            heroProgress.setProgress(0);
            playButton.setEnabled(false);
            favouriteButton.setEnabled(false);
            return;
        }
        playButton.setEnabled(true);
        favouriteButton.setEnabled(true);
        heroNumber.setText(channel.displayNumber());
        heroTitle.setText(channel.name);
        heroSource.setText("JioTV  •  " + channel.language + "  •  " + channel.category);
        heroNow.setText("NOW  " + (channel.nowTitle.isEmpty()?"Live now":channel.nowTitle));
        heroNext.setText(channel.nextTitle.isEmpty() ? "NEXT  Loading schedule…" : "NEXT  " + channel.nextTitle);
        favouriteButton.setText(repository.favourites().contains(channel.number) ? "★  Favourite" : "☆  Favourite");
        if (channel.logoUrl.isEmpty()) heroLogo.setImageDrawable(null);
        else Glide.with(heroLogo).load(channel.logoUrl).fitCenter().into(heroLogo);
        scheduleEpg(channel);
    }

    private void scheduleEpg(Channel channel) {
        if (pendingEpgLoad != null) mainHandler.removeCallbacks(pendingEpgLoad);
        pendingEpgLoad = () -> loadEpg(channel);
        mainHandler.postDelayed(pendingEpgLoad, 280L);
    }

    private void scheduleVisibleEpg(){mainHandler.removeCallbacks(visibleEpgTask);mainHandler.postDelayed(visibleEpgTask,450L);}
    private void loadVisibleEpg(){
        if(!guideActive||isFinishing()||channelGrid==null)return;
        GridLayoutManager grid=(GridLayoutManager)channelGrid.getLayoutManager();
        int first=grid.findFirstVisibleItemPosition(),last=grid.findLastVisibleItemPosition();
        if(first<0)first=0;if(last<first)last=Math.min(first+7,visibleChannels.size()-1);
        for(int i=first;i<=last&&i<first+8&&i<visibleChannels.size();i++)loadEpg(visibleChannels.get(i));
    }
    private void loadEpg(Channel channel) {
        if(channel==null||channel.id.isEmpty()||isFinishing())return;
        List<Program> cached=epgCache.get(channel.id);Long at=epgCacheTime.get(channel.id);
        if(cached!=null&&at!=null&&System.currentTimeMillis()-at<120_000L){applyEpg(channel,cached);return;}
        if(!epgPending.add(channel.id))return;
        epgExecutor.execute(()->{
            if(!guideActive||isFinishing()){mainHandler.post(()->epgPending.remove(channel.id));return;}
            try{
                List<Program> programmes=new ArrayList<>(repository.api().fetchEpg(channel.id,0));
                List<Program> today=new ArrayList<>(programmes);
                mainHandler.post(()->{
                    if(isFinishing())return;
                    epgCache.put(channel.id,today);epgCacheTime.put(channel.id,System.currentTimeMillis());
                    epgPending.remove(channel.id);applyEpg(channel,today);
                });
                boolean future=false;long time=System.currentTimeMillis();
                for(Program p:programmes)if(p.startEpochMs>time){future=true;break;}
                if(!future&&selectedChannel!=null&&selectedChannel.id.equals(channel.id)){
                    try{
                        programmes.addAll(repository.api().fetchEpg(channel.id,1));
                        mainHandler.post(()->{if(!isFinishing()){epgCache.put(channel.id,programmes);applyEpg(channel,programmes);}});
                    }catch(Exception ignored){}
                }
            }catch(Exception error){mainHandler.post(()->{
                epgPending.remove(channel.id);epgCache.put(channel.id,new ArrayList<>());epgCacheTime.put(channel.id,System.currentTimeMillis()-90_000L);
                if(selectedChannel!=null&&selectedChannel.id.equals(channel.id))heroNext.setText("NEXT  Schedule unavailable · retry on selection");
            });}
        });
    }
    private void applyEpg(Channel channel,List<Program> programmes){
        long now=System.currentTimeMillis();long[] starts=new long[programmes.size()],ends=new long[programmes.size()];
        for(int i=0;i<programmes.size();i++){starts[i]=programmes.get(i).startEpochMs;ends[i]=programmes.get(i).endEpochMs;}
        int current=GuideTimeline.current(starts,ends,now),next=GuideTimeline.next(starts,ends,now);
        channel.nowTitle=current<0?"Live · schedule not listed":programmes.get(current).title;
        channel.nextTitle=next<0?"":programmes.get(next).title;
        for(Channel row:visibleChannels)if(row.id.equals(channel.id)){row.nowTitle=channel.nowTitle;row.nextTitle=channel.nextTitle;}
        channelAdapter.updateProgramme(channel.id,channel.nowTitle,channel.nextTitle);
        if(selectedChannel==null||!selectedChannel.id.equals(channel.id))return;
        heroNow.setText("NOW  "+channel.nowTitle);
        heroNext.setText(next<0?"NEXT  Not listed by provider":"NEXT  "+programmes.get(next).title);
        if(current>=0)heroProgress.setProgress((int)Math.max(0,Math.min(1000,(now-starts[current])*1000L/(ends[current]-starts[current]))));
        else heroProgress.setProgress(0);
    }

    private void play(Channel channel) {
        if (channel == null) return;
        if (!JioSession.load(this).isPresent()) {
            routeToLogin();
            return;
        }
        repository.setLastChannel(channel.number);
        Telemetry.event(this, "tune_request", Telemetry.data(
                "category", channel.category,
                "language", channel.language,
                "guide_scope", selectedCategory,
                "access_state", channel.accessState));
        Intent player = new Intent(this, PlayerActivity.class);
        try { player.putExtra(PlayerActivity.EXTRA_CHANNEL_JSON, channel.toJson().toString()); }
        catch (Exception error) {
            Toast.makeText(this, "Could not open this channel", Toast.LENGTH_SHORT).show();
            return;
        }
        org.json.JSONArray numbers=new org.json.JSONArray();
        for(Channel item:visibleChannels)numbers.put(item.number);
        player.putExtra(PlayerActivity.EXTRA_SCOPE_NUMBERS,numbers.toString());
        player.putExtra(PlayerActivity.EXTRA_SCOPE_LABEL,selectedCategory + (searchQuery.isEmpty()?"":" · search"));
        startActivity(player);
    }

    private void toggleFavourite(Channel channel) {
        if (channel == null) return;
        boolean added = repository.toggleFavourite(channel.number);
        favouriteButton.setText(added ? "★  Favourite" : "☆  Favourite");
        channelAdapter.submit(visibleChannels, repository.favourites());
        if ("Favourites".equals(selectedCategory)) renderGuide(false);
        Toast.makeText(this, added ? "Added to favourites" : "Removed from favourites", Toast.LENGTH_SHORT).show();
    }

    private void showSearch() {
        if(heroPreviewController!=null)heroPreviewController.stop(false);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(searchQuery);
        input.setHint("Channel name, number, language, or category");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Find a live channel")
                .setView(input)
                .setPositiveButton("Search", (dialog, which) -> {
                    searchQuery = input.getText().toString().trim();
                    Telemetry.event(this, "guide_search", Telemetry.data("active", !searchQuery.isEmpty()));
                    renderGuide(true);
                })
                .setNeutralButton("Clear", (dialog, which) -> {
                    searchQuery = "";
                    Telemetry.event(this, "guide_search", Telemetry.data("active", false));
                    renderGuide(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
        input.requestFocus();
    }

    private void showAccountMenu() {
        if(heroPreviewController!=null)heroPreviewController.stop(false);
        JioSession session = JioSession.load(this);
        String mobile = session.mobile;
        String masked = mobile.length() >= 4 ? "••••••" + mobile.substring(mobile.length() - 4) : "Connected";
        String diagnostics = Telemetry.isEnabled(this) ? "on" : "off";
        String[] actions = new String[]{
                "Update live guide",
                "Check for GharTV update",
                "Appearance  •  " + FamilyTheme.modeLabel(this),
                "Owner messages  •  " + RemoteControl.status(this),
                "Diagnostics & privacy  •  " + diagnostics,
                "Hardware & picture diagnostics",
                "Sign out of JioTV",
                "Playback & comfort · preview / still watching / quality"
        };
        new AlertDialog.Builder(this)
                .setTitle("JioTV account  •  " + masked)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) refreshCatalogue(true);
                    else if (which == 1) UpdateManager.check(this, true);
                    else if (which == 2) FamilyTheme.showPicker(this);
                    else if (which == 3) RemoteControl.showPairing(this);
                    else if (which == 4) DiagnosticsDialog.show(this);
                    else if (which == 5) HardwareDiagnostics.show(this);
                    else if (which == 6) confirmSignOut();
                    else if (which == 7) PlaybackComfort.settings(this);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out of JioTV?")
                .setMessage("This removes the encrypted Jio session and downloaded channel guide from this TV. Favourites will remain.")
                .setPositiveButton("Sign out", (dialog, which) -> {
                    JioSession.clear(this);
                    repository.clearCatalogue();
                    routeToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateCatalogueStatus(String error) {
        if (error != null && !error.isEmpty()) {
            catalogueStatus.setText("Live TV needs attention  •  " + error);
            catalogueStatus.setTextColor(Color.rgb(255, 142, 154));
            return;
        }
        long updated = repository.lastUpdatedAt();
        String when = updated <= 0 ? "not downloaded yet" : TvUi.istDateTime(updated);
        catalogueStatus.setText(String.format(Locale.US, "LIVE  •  %,d channels  •  guide updated %s  •  GharTV %s", allChannels.size(), when, BuildConfig.VERSION_NAME));
        catalogueStatus.setTextColor(TvUi.MUTED);
    }

    private String readable(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void changeChannel(int direction) {
        Telemetry.event(this, "channel_change", Telemetry.data(
                "direction", direction > 0 ? "next" : "previous",
                "guide_scope", selectedCategory));
        Channel next = repository.next(visibleChannels, selectedChannel == null ? repository.lastChannel() : selectedChannel.number, direction);
        if (next != null) play(next);
    }

    @Override public void onDigits(String digits) {
        numberOverlay.setText(digits);
        numberOverlay.setVisibility(View.VISIBLE);
    }

    @Override public void onCommit(int channelNumber) {
        numberOverlay.setVisibility(View.GONE);
        Channel requested = repository.byNumber(allChannels, channelNumber);
        if (requested == null) {
            Toast.makeText(this, "Channel " + channelNumber + " is not in your JioTV guide", Toast.LENGTH_SHORT).show();
            return;
        }
        play(requested);
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
            case KeyEvent.KEYCODE_SEARCH:
                showSearch();
                return true;
            case KeyEvent.KEYCODE_REFRESH:
                refreshCatalogue(true);
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                showAccountMenu();
                return true;
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_TV:
            case KeyEvent.KEYCODE_DVR:
                channelGrid.requestFocus();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }
}
