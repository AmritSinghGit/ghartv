package in.ghartv.nova;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.speech.RecognizerIntent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayInputStream;

/** Single provider page with original artwork, remote poster focus and player tray.
 * No result replacement, stream extraction, security weakening or private-log upload. */
public class FlixMomoActivity extends Activity {
    private static final String HOME=FilmProviderPolicy.HOME;
    
    protected WebView browser;
    private RemoteWebCursor cursor;
    private FilmNativeControls nativeControls;
    private FilmHomeView homePanel;
    private FilmDetailView detailPanel;private boolean detailRequested;private String nativeSearch="";
    private boolean homeRequested=true;
    private android.widget.HorizontalScrollView liveResults;
    private LinearLayout liveRow;
    private final java.util.concurrent.ExecutorService liveExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();
    private int liveGeneration;
    private EditText query;
    private TextView status,help,playNotice;
    private long playUntil,playAfter;private boolean mediaAttempted;private double lastMediaTime=-1;
    private Button pageButton,modeButton,scrollButton,retryButton;
    private LinearLayout chrome;
    private FrameLayout stage;
    private View custom;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean mainFrameError,pageReady,loading;
    private String lastRequested=HOME;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable slowLoad=()->{if(loading&&!mainFrameError){status.setText("Still connecting. Retry is available; playback is not verified.");if(homeRequested&&homePanel!=null)homePanel.unavailable("The provider is taking longer than expected.");}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(TvUi.BG);
        root.setPadding(TvUi.dp(this,18),TvUi.dp(this,10),TvUi.dp(this,18),TvUi.dp(this,10));
        chrome=new LinearLayout(this);chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.addView(TvUi.label(this,"GharTV Discover / FlixMomo · Review "+BuildConfig.VERSION_CODE,18,TvUi.TEXT,true));
        LinearLayout row=new LinearLayout(this);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(TvUi.TEXT);query.setHintTextColor(TvUi.MUTED);
        query.setHint("Live channel, film or series");query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        row.addView(query,new LinearLayout.LayoutParams(0,TvUi.dp(this,48),1));
        Button search=TvUi.button(this,"Search",true),voice=TvUi.button(this,"Voice",false),browse=TvUi.button(this,"Discover",false),guide=TvUi.button(this,"TV guide",false);
        row.addView(search);row.addView(voice);row.addView(browse);row.addView(guide);chrome.addView(row);
        LinearLayout navigation=new LinearLayout(this);
        pageButton=TvUi.button(this,"Use page",true);modeButton=TvUi.button(this,"Cursor: off",false);scrollButton=TvUi.button(this,"Scroll: off",false);retryButton=TvUi.button(this,"Retry",false);
        navigation.addView(pageButton);navigation.addView(modeButton);navigation.addView(scrollButton);navigation.addView(retryButton);chrome.addView(navigation);
        help=TvUi.label(this,"Original posters · arrows browse · OK opens · Menu shows controls · Cursor is optional",12,TvUi.MUTED,false);chrome.addView(help);
        status=TvUi.label(this,"Provider pages stay in GharTV · direct connection",12,TvUi.MUTED,false);chrome.addView(status);
        root.addView(chrome);
        TextView credit=TvUi.label(this,FilmProviderPolicy.CREDIT,12,TvUi.MINT,true);credit.setPadding(0,TvUi.dp(this,5),0,TvUi.dp(this,6));root.addView(credit);
        playNotice=TvUi.label(this,"",12,TvUi.MUTED,false);playNotice.setVisibility(View.GONE);root.addView(playNotice);
        liveResults=new android.widget.HorizontalScrollView(this);liveResults.setHorizontalScrollBarEnabled(false);liveResults.setVisibility(View.GONE);
        liveRow=new LinearLayout(this);liveResults.addView(liveRow);root.addView(liveResults,new LinearLayout.LayoutParams(-1,TvUi.dp(this,48)));
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        cursor=new RemoteWebCursor(this);stage.addView(cursor,new FrameLayout.LayoutParams(-1,-1));cursor.enable(false);
        nativeControls=new FilmNativeControls(this,stage,chrome,new FilmNativeControls.Host(){
            public void navigate(String url){if(FilmProviderPolicy.detail(Uri.parse(url)))openDetail(url);else FlixMomoActivity.this.navigate(url,false);}
            public void usePage(){FlixMomoActivity.this.usePage();}
            public void nativeMode(){cursor.enable(false);modeButton.setText("Cursor: off");}
            public void navigationHint(String text){help.setText(text);}
            public void searchToolbar(){toolbar();query.requestFocus();}
            public boolean pageIsVisible(){return !homeRequested&&!detailRequested;}
            public void pageObserved(org.json.JSONObject data){if(homeRequested&&homePanel!=null)homePanel.render(data);else if(detailRequested&&detailPanel!=null)detailPanel.render(data.optJSONObject("detail"));else if(!data.optBoolean("search"))liveResults.setVisibility(View.GONE);followPlayback(data);}
            public void pageAction(String action){if("watch".equals(action))requestWatch();else nativeControls.pageAction(action);}
            public void playerSelected(){armPlayback();}
            public void actionOutcome(String action,String result){
                if("watch".equals(action)||"media".equals(action)){
                    if("VISUAL_TIMEOUT".equals(result)||"NOT_FOUND".equals(result)||"AMBIGUOUS_MEDIA".equals(result)){playUntil=0;playMessage("Playback has not started: "+result.replace('_',' ').toLowerCase(java.util.Locale.ROOT)+". Use Original page or another player.");}
                    else if("media".equals(action))playMessage("Play sent to FlixMomo. Video playback is not yet confirmed.");
                }
            }
            public void verificationRequired(){if(homeRequested&&homePanel!=null)homePanel.unavailable("Provider verification is required.");else if(detailRequested)detailPanel.unavailable("Provider verification is required.");else toolbar();}
        });
        homePanel=createHomePanel(new FilmHomeView.Host(){
            public void open(String url){openDetail(url);}
            public void refresh(){if(nativeSearch.isEmpty())showHome(true);else search();}
            public void provider(){homeRequested=false;homePanel.setVisibility(View.GONE);usePage();}
            public void privacy(){ReviewNotice.show(FlixMomoActivity.this,()->{
                if(browser!=null){browser.stopLoading();browser.loadUrl("about:blank");browser.clearCache(true);browser.clearHistory();browser.clearFormData();}
                com.bumptech.glide.Glide.get(FlixMomoActivity.this).clearMemory();
                nativeControls.failure();homeRequested=true;liveGeneration++;liveResults.setVisibility(View.GONE);homePanel.discardSuggestions();homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();chrome.setVisibility(View.VISIBLE);
                homePanel.unavailable("Film site data cleared. Suggestions will reload only when you choose Refresh.");
            });}
        });
        stage.addView(homePanel,new FrameLayout.LayoutParams(-1,-1));
        detailPanel=createDetailPanel(new FilmDetailView.Host(){
            public void watch(){requestWatch();}
            public void watchlist(){usePage();nativeControls.pageAction("watchlist");}
            public void original(){usePage();}
            public void back(){returnToCards();}
        });stage.addView(detailPanel,new FrameLayout.LayoutParams(-1,-1));
        homePanel.setToolbarAnchor(pageButton);
        query.setOnFocusChangeListener((v,focused)->{if(focused)cursor.leave();});
        search.setOnClickListener(v->search());voice.setOnClickListener(v->voiceSearch());browse.setOnClickListener(v->showHome(false));guide.setOnClickListener(v->finish());
        query.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH||event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP){search();return true;}return false;});
        pageButton.setOnClickListener(v->usePage());
        modeButton.setOnClickListener(v->{cursor.enable(!cursor.enabled());modeButton.setText(cursor.enabled()?"Cursor: on":"Cursor: off");help.setText(cursor.enabled()?"Arrows move cursor; OK clicks; Menu shows controls.":"Arrows browse original posters; OK opens; Menu shows controls.");usePage();});
        scrollButton.setOnClickListener(v->{if(!cursor.enabled()){cursor.enable(true);modeButton.setText("Cursor: on");}cursor.scrollMode(!cursor.scrolling());scrollButton.setText(cursor.scrolling()?"Scroll: on":"Scroll: off");usePage();});
        retryButton.setOnClickListener(v->{if(browser==null)createBrowser();navigate(lastRequested,true);});
        if(!createBrowser())return;
        String incoming=UnifiedSearch.clean(getIntent().getStringExtra(UnifiedSearch.QUERY));
        if(UnifiedSearch.valid(incoming)){query.setText(incoming);search();}
        else if(saved!=null&&!saved.getBoolean("home",true)){query.setText(saved.getString("query",""));homeRequested=false;homePanel.setVisibility(View.GONE);String url=saved.getString("url",HOME);navigate(allowedTop(Uri.parse(url))?url:HOME,false);}
        else showHome(false);
        pageButton.requestFocus();
    }
    private boolean createBrowser(){
        if(browser!=null)return true;
        try{browser=new WebView(this);}catch(RuntimeException e){fail("Android System WebView is unavailable. Update that system component; live TV is unchanged.");return false;}
        browser.setBackgroundColor(TvUi.BG);stage.addView(browser,0,new FrameLayout.LayoutParams(-1,-1));cursor.target(browser);nativeControls.attach(browser);homePanel.bindUnderlyingPage(browser);detailPanel.bindUnderlyingPage(browser);
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.setDownloadListener((u,a,d,m,n)->status.setText("Downloads are not enabled in this provider view."));
        browser.setOnFocusChangeListener((v,focused)->{if(focused)cursor.enter();else cursor.leave();});
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame()||allowedTop(request.getUrl()))return false;
                if(request.hasGesture())status.setText("That link opens outside the registered provider. This page was kept open.");return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||privateHost(host))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){
                cursor.cancel();nativeControls.started();if("about:blank".equals(url))return;
                if(detailRequested&&FilmProviderPolicy.detail(Uri.parse(url)))detailPanel.followNavigation(url);
                else if(detailRequested&&allowedTop(Uri.parse(url))&&!FilmProviderPolicy.detail(Uri.parse(url)))usePage();mainFrameError=false;pageReady=false;loading=true;if(allowedTop(Uri.parse(url)))lastRequested=url;
                status.setText("Opening FlixMomo…");handler.removeCallbacks(slowLoad);handler.postDelayed(slowLoad,20000);
            }
            @Override public void onPageFinished(WebView view,String url){
                loading=false;handler.removeCallbacks(slowLoad);if(mainFrameError){pageReady=false;return;}
                if("/dummy".equals(Uri.parse(url).getPath())){fail("FlixMomo declined this session. No provider protection was changed.");return;}
                pageReady=allowedTop(Uri.parse(url));if(pageReady){status.setText("Original page loaded · artwork stays here · Menu shows GharTV controls");nativeControls.finished();}
            }
            @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){
                if(!loading&&allowedTop(Uri.parse(url))&&!java.util.Objects.equals(lastRequested,url)){
                    lastRequested=url;nativeControls.started();
                    if(detailRequested&&FilmProviderPolicy.detail(Uri.parse(url)))detailPanel.followNavigation(url);
                    else if(detailRequested)usePage();
                    nativeControls.finished();
                }
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame())fail("Provider returned HTTP "+response.getStatusCode()+". Your search is kept. Use Retry or Browse.");}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();fail("TLS verification failed. Connection stopped; security checks remain enabled.");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){
                if(!request.isForMainFrame())return;
                switch(error.getErrorCode()){
                    case WebViewClient.ERROR_HOST_LOOKUP:fail("DNS: Android cannot resolve the provider. No page/video loaded. Your query is kept.");break;
                    case WebViewClient.ERROR_CONNECT:fail("Android could not connect. Check the TV network, then Retry; your query is kept.");break;
                    case WebViewClient.ERROR_TIMEOUT:fail("Provider request timed out. Your query is kept. Press Retry when ready.");break;
                    default:fail("Navigation failed ("+error.getErrorCode()+"). Your query is kept. Use Retry or TV guide.");
                }
            }
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                nativeControls.failure();cursor.leave();exitFullScreen();stage.removeView(view);view.destroy();if(browser==view)browser=null;cursor.target(null);nativeControls.attach(null);
                fail("The provider browser stopped. Retry reopens it; live TV and your query are preserved.");retryButton.requestFocus();return true;
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,android.webkit.GeolocationPermissions.Callback callback){callback.invoke(origin,false,false);}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(custom!=null){callback.onCustomViewHidden();return;}cursor.cancel();custom=view;customCallback=callback;chrome.setVisibility(View.GONE);browser.setVisibility(View.GONE);stage.addView(view,new FrameLayout.LayoutParams(-1,-1));cursor.target(view);cursor.enter();view.requestFocus();}
            @Override public void onHideCustomView(){exitFullScreen();}
        });return true;
    }

    private void showHome(boolean force){playUntil=0;if(playNotice!=null)playNotice.setVisibility(View.GONE);
        detailRequested=false;if(detailPanel!=null)detailPanel.setVisibility(View.GONE);homeRequested=true;nativeSearch="";homePanel.mode("");liveGeneration++;liveResults.setVisibility(View.GONE);query.setText("");chrome.setVisibility(View.VISIBLE);
        cursor.enable(false);modeButton.setText("Cursor: off");nativeControls.hideAll();homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();
        if(force)homePanel.discardSuggestions();
        if(force||homePanel.cardCount()==0){homePanel.loading();navigate(providerOrigin()+"/",false);}
        homePanel.focusRefresh();
    }
    private void showLiveResults(String text){
        final int token=++liveGeneration;liveRow.removeAllViews();liveResults.setVisibility(View.VISIBLE);
        liveRow.addView(TvUi.label(this,"Searching cached live guide…",12,TvUi.MUTED,false));
        liveExecutor.execute(()->{
            try{ChannelRepository repo=new ChannelRepository(this);java.util.List<Channel> all=repo.loadAll();java.util.List<Channel> found=repo.filter(all,"All",text);
                handler.post(()->{if(isFinishing()||token!=liveGeneration)return;liveRow.removeAllViews();
                    TextView label=TvUi.label(this,all.isEmpty()?"Live guide not available locally":found.isEmpty()?"Live: no cached channel matches":"Live guide · "+found.size()+" matches",12,TvUi.MUTED,false);label.setPadding(TvUi.dp(this,10),0,TvUi.dp(this,12),0);liveRow.addView(label,new LinearLayout.LayoutParams(-2,-1));
                    org.json.JSONArray scope=new org.json.JSONArray();for(Channel c:found)scope.put(c.number);
                    for(Channel c:found.subList(0,Math.min(found.size(),16))){Button button=TvUi.button(this,c.name,false);button.setOnClickListener(v->{try{startActivity(new Intent(this,PlayerActivity.class).putExtra(PlayerActivity.EXTRA_CHANNEL_JSON,c.toJson().toString()).putExtra(PlayerActivity.EXTRA_SCOPE_NUMBERS,scope.toString()).putExtra(PlayerActivity.EXTRA_SCOPE_LABEL,"All · search"));}catch(Exception e){status.setText("Could not open that cached live channel.");}});liveRow.addView(button,new LinearLayout.LayoutParams(TvUi.dp(this,145),-1));}
                });
            }catch(Exception e){handler.post(()->{if(token==liveGeneration){liveRow.removeAllViews();liveRow.addView(TvUi.label(this,"Live guide could not be read; film search remains available",12,TvUi.MUTED,false));}});}
        });
    }

    private void playMessage(String text){playNotice.setText(text);playNotice.setVisibility(View.VISIBLE);}
    private void armPlayback(){playUntil=android.os.SystemClock.elapsedRealtime()+20000;playAfter=android.os.SystemClock.elapsedRealtime()+700;mediaAttempted=false;lastMediaTime=-1;playMessage("Opening FlixMomo playback…");}
    private void requestWatch(){armPlayback();usePage();nativeControls.pageAction("watch");}
    private void followPlayback(org.json.JSONObject data){
        if(playUntil==0||homeRequested||detailRequested)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(data.optBoolean("mediaPlaying")&&data.optDouble("mediaTime")>lastMediaTime&&lastMediaTime>=0){playMessage("Video is playing in FlixMomo’s embedded player.");playUntil=0;return;}
        if(data.optBoolean("mediaObservable"))lastMediaTime=data.optDouble("mediaTime");
        if(now>playUntil){playMessage("Playback is not confirmed. Use Play, another provider player, or Original page.");playUntil=0;return;}
        org.json.JSONArray offered=data.optJSONArray("players");
        boolean playbackPage=!FilmProviderPolicy.detail(Uri.parse(data.optString("url")))||(offered!=null&&offered.length()>0)||data.optBoolean("mediaObservable");
        if(!loading&&!mediaAttempted&&now>=playAfter&&playbackPage&&data.optInt("mediaCandidates")==1){
            mediaAttempted=true;playMessage("Starting the provider’s visible player…");nativeControls.pageAction("media");
        }
    }
    private void fail(String message){if(playUntil>0){playUntil=0;playMessage(message);}if(nativeControls!=null)nativeControls.failure();mainFrameError=true;pageReady=false;loading=false;handler.removeCallbacks(slowLoad);status.setText(message);if(homeRequested&&homePanel!=null)homePanel.unavailable(message);if(detailRequested&&detailPanel!=null)detailPanel.unavailable(message);}
    static boolean providerHost(String host){return FilmProviderPolicy.host(host);}
    static boolean allowedTop(Uri uri){return FilmProviderPolicy.allowed(uri);}
    private static boolean privateHost(String host){String h=host.toLowerCase(java.util.Locale.ROOT);return h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||h.contains(":")||h.matches("(?i)(127\\..*|10\\..*|192\\.168\\..*|169\\.254\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|0\\..*)");}
    private String providerOrigin(){String url=browser==null?lastRequested:browser.getUrl();Uri uri=Uri.parse(url==null?HOME:url);return allowedTop(uri)?"https://"+uri.getHost():"https://flixmomo.app";}
    private void openDetail(String url){
        if(!FilmProviderPolicy.detail(Uri.parse(url))){navigate(url,true);return;}
        org.json.JSONObject card=homePanel.card(url);homeRequested=false;homePanel.setVisibility(View.GONE);liveResults.setVisibility(View.GONE);
        detailRequested=true;nativeControls.hideAll();cursor.enable(false);chrome.setVisibility(View.GONE);detailPanel.begin(url,card);navigate(url,false);
    }
    private void returnToCards(){detailRequested=false;detailPanel.setVisibility(View.GONE);homeRequested=true;homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();chrome.setVisibility(View.VISIBLE);homePanel.focusRefresh();
        // Retained cards are immediately reviewable. Do not reread a title as search suggestions.
        if(!nativeSearch.isEmpty()){query.setText(nativeSearch);navigate(providerOrigin()+"/search?q="+Uri.encode(nativeSearch),false);}else navigate(providerOrigin()+"/",false);
    }
    private void search(){playUntil=0;playNotice.setVisibility(View.GONE);String text=UnifiedSearch.clean(query.getText().toString());if(!UnifiedSearch.valid(text)){status.setText("Enter 2–120 characters.");return;}
        detailRequested=false;detailPanel.setVisibility(View.GONE);homeRequested=true;nativeSearch=text;homePanel.mode(text);homePanel.discardSuggestions();homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();nativeControls.hideAll();cursor.enable(false);showLiveResults(text);homePanel.loading();navigate(providerOrigin()+"/search?q="+Uri.encode(text),false);homePanel.focusRefresh();
    }
    private void navigate(String url,boolean page){if(!allowedTop(Uri.parse(url))){fail("Only the registered provider can open here.");return;}hideKeyboard();lastRequested=url;if(browser==null){fail("Provider browser stopped. Press Retry to reopen it.");return;}loadProviderPage(url);if(page)usePage();}
    protected FilmDetailView createDetailPanel(FilmDetailView.Host host){return new FilmDetailView(this,host);}
    protected FilmHomeView createHomePanel(FilmHomeView.Host host){return new FilmHomeView(this,host);}
    protected void loadProviderPage(String url){browser.loadUrl(url);}
    private void voiceSearch(){hideKeyboard();UnifiedSearch.voice(this);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);String text=UnifiedSearch.voiceText(request,result,data);if(!text.isEmpty()){query.setText(text);search();}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String text=UnifiedSearch.clean(intent.getStringExtra(UnifiedSearch.QUERY));if(UnifiedSearch.valid(text)){query.setText(text);search();}}
    private void hideKeyboard(){InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(query.getWindowToken(),0);query.clearFocus();}
    private void usePage(){detailRequested=false;if(detailPanel!=null)detailPanel.setVisibility(View.GONE);homeRequested=false;if(homePanel!=null)homePanel.setVisibility(View.GONE);if(nativeControls!=null)nativeControls.hideAll();hideKeyboard();chrome.setVisibility(View.GONE);if(browser!=null){browser.requestFocus();cursor.enter();nativeControls.enterPosters();}}
    private void toolbar(){chrome.setVisibility(View.VISIBLE);cursor.leave();if(custom!=null)exitFullScreen();pageButton.requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(detailRequested&&detailPanel!=null&&detailPanel.getVisibility()==View.VISIBLE){
            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)usePage();return true;}
            if(detailPanel.handleRemote(event,getCurrentFocus()))return true;
            return super.dispatchKeyEvent(event);
        }
        if(homeRequested&&homePanel!=null&&homePanel.getVisibility()==View.VISIBLE){
            if(homePanel.handleRemote(event,getCurrentFocus()))return true;
            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP){chrome.setVisibility(View.VISIBLE);query.requestFocus();}return true;}
            // The underlying WebView must never receive navigation intended for visible cards.
            if(event.getKeyCode()!=KeyEvent.KEYCODE_VOICE_ASSIST&&event.getKeyCode()!=KeyEvent.KEYCODE_SEARCH)return super.dispatchKeyEvent(event);
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
            if(event.getKeyCode()==KeyEvent.KEYCODE_VOICE_ASSIST){voiceSearch();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_SEARCH){toolbar();query.requestFocus();return true;}
        }
        if(nativeControls!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)nativeControls.userInput();if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP){if(homeRequested){chrome.setVisibility(View.VISIBLE);query.requestFocus();}else nativeControls.menu();}return true;}if(nativeControls.ownsFocus())return super.dispatchKeyEvent(event);if(homePanel!=null&&homePanel.getVisibility()==View.VISIBLE)return super.dispatchKeyEvent(event);if(!cursor.enabled()&&(nativeControls.posterKey(event)||nativeControls.pageKey(event)))return true;}
        if(cursor!=null&&(custom!=null||browser!=null&&browser.hasFocus())&&cursor.handle(event))return true;
        return super.dispatchKeyEvent(event);
    }
    private void exitFullScreen(){if(custom==null)return;cursor.cancel();stage.removeView(custom);custom=null;chrome.setVisibility(View.VISIBLE);if(browser!=null){browser.setVisibility(View.VISIBLE);cursor.target(browser);}WebChromeClient.CustomViewCallback callback=customCallback;customCallback=null;if(callback!=null)callback.onCustomViewHidden();}
    @Override public void onBackPressed(){if(detailRequested){returnToCards();return;}if(homeRequested){finish();return;}if(nativeControls!=null&&nativeControls.back())return;if(custom!=null){exitFullScreen();usePage();return;}if(browser!=null&&browser.canGoBack()){browser.goBack();usePage();return;}if(browser!=null&&browser.hasFocus()){toolbar();return;}super.onBackPressed();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused&&cursor!=null)cursor.cancel();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("home",homeRequested);state.putString("query",query.getText().toString());state.putString("url",lastRequested);super.onSaveInstanceState(state);}
    @Override protected void onPause(){if(nativeControls!=null)nativeControls.pause();if(cursor!=null)cursor.cancel();handler.removeCallbacks(slowLoad);if(browser!=null)browser.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(nativeControls!=null)nativeControls.resume();if(browser!=null)browser.onResume();}
    @Override protected void onDestroy(){liveGeneration++;liveExecutor.shutdownNow();if(nativeControls!=null)nativeControls.destroy();handler.removeCallbacksAndMessages(null);if(cursor!=null)cursor.leave();exitFullScreen();if(browser!=null){browser.stopLoading();stage.removeView(browser);browser.destroy();browser=null;}super.onDestroy();}
}
