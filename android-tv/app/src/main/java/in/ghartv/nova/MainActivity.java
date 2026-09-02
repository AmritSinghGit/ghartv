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
    private final Map<String, List<Program>> epgCache = new HashMap<>();
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

    private ChannelNavigator navigator;
    private Runnable pendingEpgLoad;
    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
            mainHandler.postDelayed(this, 30_000L);
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
        navigator = new ChannelNavigator(this);
        selectedCategory = repository.lastCategory();
        setContentView(buildUi());
        Telemetry.screen(this, "guide");
        mainHandler.postDelayed(() -> Telemetry.maybeRequestConsent(this), 1200L);
        mainHandler.post(clockTicker);
        loadFromDisk(true);
        mainHandler.postDelayed(() -> UpdateManager.check(this, false), 2600L);
    }

    @Override protected void onResume() {
        super.onResume();
        TvUi.immersive(this);
        if (repository == null) return;
        if (!JioSession.load(this).isPresent()) routeToLogin();
        else loadFromDisk(false);
    }

    @Override protected void onDestroy() {
        if (pendingEpgLoad != null) mainHandler.removeCallbacks(pendingEpgLoad);
        mainHandler.removeCallbacks(clockTicker);
        executor.shutdownNow();
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
        hero.setBackground(TvUi.rounded(Color.argb(220, 7, 20, 31), 28, Color.argb(60, 83, 228, 255), 1, this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView onAir = TvUi.label(this, "●  LIVE TELEVISION", 11, TvUi.MINT, true);
        top.addView(onAir, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 28)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        heroNumber = TvUi.label(this, "---", 17, TvUi.CYAN, true);
        heroNumber.setGravity(Gravity.CENTER);
        heroNumber.setPadding(TvUi.dp(this, 14), 0, TvUi.dp(this, 14), 0);
        heroNumber.setBackground(TvUi.rounded(Color.argb(120, 0, 0, 0), 17, Color.argb(85, 83, 228, 255), 1, this));
        top.addView(heroNumber, new LinearLayout.LayoutParams(-2, TvUi.dp(this, 30)));
        hero.addView(top, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 32)));

        heroLogo = new ImageView(this);
        heroLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroLogo.setBackground(TvUi.rounded(Color.argb(48, 255, 255, 255), 22, Color.argb(38, 255, 255, 255), 1, this));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(TvUi.dp(this, 76), TvUi.dp(this, 76));
        logoParams.topMargin = TvUi.dp(this, 8);
        hero.addView(heroLogo, logoParams);

        heroTitle = TvUi.label(this, "Your live television", 27, TvUi.TEXT, true);
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
        hero.addView(actions, new LinearLayout.LayoutParams(-1, TvUi.dp(this, 46)));

        TextView hints = TvUi.label(this, "NUMBER to tune  •  CH ± to switch  •  GUIDE to come back", 10, TvUi.MUTED, false);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hintsParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 24));
        hintsParams.topMargin = TvUi.dp(this, 4);
        hero.addView(hints, hintsParams);
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
        allChannels = repository.loadAll();
        renderGuide(focus);
        updateCatalogueStatus(null);

        long age = System.currentTimeMillis() - repository.lastUpdatedAt();
        if (allChannels.isEmpty()) refreshCatalogue(true);
        else if (age > AppConfig.CATALOGUE_REFRESH_MS) refreshCatalogue(false);
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

        Channel preferred = repository.byNumber(visibleChannels, repository.lastChannel());
        if (preferred == null) preferred = visibleChannels.get(0);
        select(preferred);
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
        heroNow.setText(channel.nowTitle.isEmpty() ? "Live now" : channel.nowTitle);
        heroNext.setText(channel.nextTitle.isEmpty() ? "Loading programme guide…" : "Next: " + channel.nextTitle);
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

    private void loadEpg(Channel channel) {
        if (channel == null || channel.id.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Program> cached = epgCache.get(channel.id);
        Long cachedAt = epgCacheTime.get(channel.id);
        if (cached != null && cachedAt != null && now - cachedAt < 15 * 60_000L) {
            applyEpg(channel, cached);
            return;
        }
        executor.execute(() -> {
            try {
                List<Program> programmes = repository.api().fetchEpg(channel.id, 0);
                epgCache.put(channel.id, programmes);
                epgCacheTime.put(channel.id, System.currentTimeMillis());
                mainHandler.post(() -> applyEpg(channel, programmes));
            } catch (Exception ignored) {}
        });
    }

    private void applyEpg(Channel channel, List<Program> programmes) {
        if (selectedChannel == null || !selectedChannel.id.equals(channel.id)) return;
        long now = System.currentTimeMillis();
        Program current = null;
        Program next = null;
        for (Program programme : programmes) {
            if (programme.isLive(now)) current = programme;
            else if (programme.startEpochMs > now && next == null) next = programme;
        }
        channel.nowTitle = current == null ? "Live now" : current.title;
        channel.nextTitle = next == null ? "" : next.title;
        heroNow.setText(channel.nowTitle);
        heroNext.setText(channel.nextTitle.isEmpty() ? "Next programme not listed" : "Next: " + channel.nextTitle);
        if (current != null && current.endEpochMs > current.startEpochMs) {
            long duration = current.endEpochMs - current.startEpochMs;
            int progress = (int) Math.max(0, Math.min(1000, ((now - current.startEpochMs) * 1000L) / duration));
            heroProgress.setProgress(progress);
        } else heroProgress.setProgress(0);
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
        JioSession session = JioSession.load(this);
        String mobile = session.mobile;
        String masked = mobile.length() >= 4 ? "••••••" + mobile.substring(mobile.length() - 4) : "Connected";
        String diagnostics = Telemetry.isEnabled(this) ? "on" : "off";
        String[] actions = new String[]{
                "Update live guide",
                "Check for GharTV update",
                "Diagnostics & privacy  •  " + diagnostics,
                "Sign out of JioTV"
        };
        new AlertDialog.Builder(this)
                .setTitle("JioTV account")
                .setMessage("Connected as " + masked + "\n\nGharTV " + BuildConfig.VERSION_NAME +
                        " shows and plays the live channels returned for this Jio account.")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) refreshCatalogue(true);
                    else if (which == 1) UpdateManager.check(this, true);
                    else if (which == 2) DiagnosticsDialog.show(this);
                    else if (which == 3) confirmSignOut();
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
        String when = updated <= 0 ? "not downloaded yet" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(updated));
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
        Channel next = repository.next(allChannels, selectedChannel == null ? repository.lastChannel() : selectedChannel.number, direction);
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
