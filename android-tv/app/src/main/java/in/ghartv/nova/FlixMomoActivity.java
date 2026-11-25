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
    /** Narrow owner-phone navigation: open Films and focus the search box. */
    public static final String EXTRA_PHONE_SEARCH_ON_ENTRY =
        "in.ghartv.nova.PHONE_SEARCH_ON_ENTRY";
    private static final String HOME=FilmProviderPolicy.HOME;
    static final String PROVIDER_PREFERENCES="ghartv.film-provider-origin";
    static String preferredProviderHome(android.content.Context context){
        String saved=context.getSharedPreferences(PROVIDER_PREFERENCES,MODE_PRIVATE).getString("home",null);
        String registered=FilmProviderPolicy.registeredHome(saved);
        return registered==null?HOME:registered;
    }
    protected String initialProviderHome(){return preferredProviderHome(this);}
    private void rememberProviderHome(String url){
        String root=FilmProviderPolicy.pageHome(url);
        if(root!=null&&!isFinishing()&&!isDestroyed())getSharedPreferences(PROVIDER_PREFERENCES,MODE_PRIVATE).edit().putString("home",root).apply();
    }
    
    protected WebView browser;
    // Production entry opens the original page. The older parsed-controls path is
    // retained only for historical component regression fixtures, not availability.
    protected boolean pointerFirstBrowsing(){return true;}
    // One-click play (code64). Discover shows the provider's own pages under the pointer, so
    // a movie click is a pointer click on a poster. When that click lands the page on a title
    // (or watch) page, GharTV activates the page's single unambiguous Watch action and starts
    // the visible player, once. Back, Menu, any failure, or proven playback ends the chain; a
    // page with several Watch actions (series) is left to the pointer.
    protected boolean oneClickPlayEnabled(){return true;}
    private static final long ONE_CLICK_WINDOW_MS=5000,ONE_CLICK_EXPIRE_MS=25000;
    private boolean oneClickPlay,oneClickWatchSent;
    private long lastPointerClick;
    private final Runnable oneClickExpire=()->{
        if(oneClickPlay&&!oneClickWatchSent){endOneClick();playMessage("Choose the episode or Play on this page, then press OK.");}
    };
    private static boolean watchPage(Uri uri){String p=uri==null?null:uri.getPath();return allowedTop(uri)&&p!=null&&p.toLowerCase(java.util.Locale.ROOT).contains("/watch");}
    /** Ends the chain and returns the page to ordinary pointer-only browsing. */
    private void endOneClick(){
        handler.removeCallbacks(oneClickExpire);
        if(!oneClickPlay)return;
        oneClickPlay=false;oneClickWatchSent=false;
        if(nativeControls!=null){nativeControls.quiet(false);if(pointerPage)nativeControls.pause();}
    }
    /** A pointer click that just landed the page on a title (or watch) page arms the chain once. */
    private void oneClickArrived(String url){
        if(torWebMode)return;
        if(oneClickPlay||url==null||!oneClickPlayEnabled()||!pointerPage||custom!=null||nativeControls==null)return;
        if(android.os.SystemClock.elapsedRealtime()-lastPointerClick>ONE_CLICK_WINDOW_MS)return;
        Uri page=Uri.parse(url);boolean title=FilmProviderPolicy.detail(page);
        if(BuildConfig.DEBUG)android.util.Log.i("GharTV1Click","NAV_OBSERVED title="+title+" watchPage="+watchPage(page)+" pointerPage="+pointerPage+" age="+(android.os.SystemClock.elapsedRealtime()-lastPointerClick));
        if(!title&&!watchPage(page))return;
        lastPointerClick=0;
        oneClickPlay=true;oneClickWatchSent=!title;
        nativeControls.quiet(true);nativeControls.resume();
        if(title)handler.postDelayed(oneClickExpire,ONE_CLICK_EXPIRE_MS);else armPlayback();
    }
    /** Called for every observed page while the chain is armed. */
    private void oneClickObserved(org.json.JSONObject data){
        if(!oneClickPlay||oneClickWatchSent||!pointerPage||custom!=null)return;
        Uri page=Uri.parse(data.optString("url"));
        if(watchPage(page)){
            // The viewer (or the provider) already moved on to the watch page: only start the player.
            oneClickWatchSent=true;handler.removeCallbacks(oneClickExpire);armPlayback();return;
        }
        org.json.JSONObject detail=data.optJSONObject("detail");
        if(BuildConfig.DEBUG)android.util.Log.i("GharTV1Click","SNAPSHOT detail="+(detail!=null)+" watch="+(detail!=null&&detail.optBoolean("watch"))+" ambiguous="+(detail!=null&&detail.optBoolean("watch_ambiguous")));
        if(FilmProviderPolicy.detail(page)&&detail!=null&&detail.optBoolean("watch")){
            oneClickWatchSent=true;handler.removeCallbacks(oneClickExpire);
            filmEvent("film_watch_request");armPlayback();nativeControls.pageWatch("");
        }
    }
    private boolean pointerPage;
    private String pointerTitleOrigin="";
    private boolean pageEditor;
    private boolean searchKeyHeld,voiceKeyHeld;
    private RemoteWebCursor cursor;
    private TorBrowserChrome browserBar;
    private final java.util.ArrayList<String> torTabs=new java.util.ArrayList<>();
    private final FilmControlTrace controlTrace=new FilmControlTrace();
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
    private LinearLayout providerStatusPanel,providerRecoveryActions;
    private boolean attemptObservable;
    private FilmPlaybackMonitor startMonitor;
    private long detailBegan,detailUsableMs=-1,detailPreviewMs=-1;
    private final Runnable detailWait=()->{if(detailRequested&&detailUsableMs<0&&detailPanel!=null)detailPanel.unavailable("Title information has not arrived. Retry the original page or return to results.");};
    private void observeDetail(org.json.JSONObject data){
        org.json.JSONObject detail=data.optJSONObject("detail");
        if(detailRequested&&detail!=null&&detail.optBoolean("watch")&&detailUsableMs<0){
            detailUsableMs=Math.max(0,android.os.SystemClock.elapsedRealtime()-detailBegan);
            handler.removeCallbacks(detailWait);
        }
    }
    org.json.JSONObject performanceEvidence(){
        org.json.JSONObject d=startMonitor==null?new org.json.JSONObject():startMonitor.evidence();
        try{d.put("details_usable_ms",detailUsableMs<0?org.json.JSONObject.NULL:detailUsableMs);d.put("cached_preview_ms",detailPreviewMs<0?org.json.JSONObject.NULL:detailPreviewMs);}catch(Exception ignored){}
        return d;
    }

    private long playUntil,playAfter;private boolean mediaAttempted;private double lastMediaTime=-1;
    private Button pageButton,toolbarAnchor,modeButton,scrollButton,retryButton;
    private LinearLayout chrome,browseRoot,fullMenu;
    private FrameLayout screen,fullHost;
    private TextView credit;
    private android.app.AlertDialog optionsDialog;
    private final FilmVisitCache visitCache=new FilmVisitCache();
    private boolean closingFullscreen;
    private long inputEpoch;
    private boolean backHeld,menuHeld,fullscreenKeyHeld,escapeHeld;
    private long keyEventSerial;
    private final FilmReviewJournal reviewJournal=new FilmReviewJournal();
    private void noteInput(String event){
        if("POINTER_CLICK".equals(event)){
            lastPointerClick=android.os.SystemClock.elapsedRealtime();
            if(BuildConfig.DEBUG)android.util.Log.i("GharTV1Click","POINTER_CLICK pointerPage="+pointerPage);
        }
        String layer=custom!=null?(fullMenu!=null&&fullMenu.getVisibility()==View.VISIBLE?"FULLSCREEN_MENU":"FULLSCREEN"):
            mouseActive?"POINTER":detailRequested?"DETAIL":homeRequested?"DISCOVER":
            chrome!=null&&chrome.getVisibility()==View.VISIBLE&&chrome.hasFocus()?"TOOLBAR":
            nativeControls!=null&&nativeControls.visible()?"CONTROLS":"PAGE";
        reviewJournal.record(event,layer,cursor!=null&&cursor.enabled(),cursor!=null&&cursor.isShown());
    }
    // Retain the experimental engine in source/tests, not normal viewing in47.
    private static final boolean NATIVE_PLAYER_EXPERIMENT=false;
    private FrameLayout stage;
    private View custom;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean mainFrameError,pageReady,loading,mouseActive,browserSearchSubmitted,torWebMode;
    private boolean fullscreenProofPrompted;
    private boolean verifiedImmersive;
    private String verifiedImmersiveUrl="";
    private int immersiveOldLeft,immersiveOldTop,immersiveOldRight,immersiveOldBottom;
    private void autoVerifiedFullscreen(){requestPageFullscreen(false);}
    private void requestPageFullscreen(boolean viewerRequested){
        if(verifiedImmersive||custom!=null||torWebMode||browser==null||!pointerPage||isFinishing()){
            if(viewerRequested)HouseholdPhoneSession.feedback("FULLSCREEN_UNAVAILABLE");
            return;
        }
        final WebView current=browser;
        final String url=current.getUrl();
        if(url==null)return;
        current.evaluateJavascript(viewerRequested?FilmPlaybackProbe.fullscreenRequested():
            FilmPlaybackProbe.fullscreenVerified(),result->{
            if(current!=browser||isFinishing()||custom!=null||!pointerPage||
                    torWebMode||!java.util.Objects.equals(url,current.getUrl()))return;
            if(result==null||!result.contains("IMMERSIVE_READY")){
                filmEvent("film_fullscreen_unavailable");
                if(viewerRequested)HouseholdPhoneSession.feedback("FULLSCREEN_UNAVAILABLE");
                return;
            }
            verifiedImmersive=true;verifiedImmersiveUrl=url;
            notifyPhoneMode();
            immersiveOldLeft=browseRoot.getPaddingLeft();
            immersiveOldTop=browseRoot.getPaddingTop();
            immersiveOldRight=browseRoot.getPaddingRight();
            immersiveOldBottom=browseRoot.getPaddingBottom();
            browseRoot.setPadding(0,0,0,0);
            if(browserBar!=null)browserBar.hideBar();
            chrome.setVisibility(View.GONE);credit.setVisibility(View.GONE);
            playNotice.setVisibility(View.GONE);networkNotice.setVisibility(View.GONE);
            hideProviderStatus();cursor.leave();cursor.enable(false);
            if(startMonitor!=null)startMonitor.panel.setVisibility(View.GONE);
            inputEpoch++;noteInput("VERIFIED_FULLSCREEN_ENTER");
            filmEvent("film_fullscreen_enter");
            if(viewerRequested)HouseholdPhoneSession.feedback("FULLSCREEN_REQUESTED");
            browser.requestFocus();
            TvUi.immersive(this);
        });
    }
    private void leaveVerifiedFullscreen(){
        if(!verifiedImmersive)return;
        verifiedImmersive=false;verifiedImmersiveUrl="";
        notifyPhoneMode();
        if(browser!=null)browser.evaluateJavascript(FilmPlaybackProbe.restoreFullscreen(),null);
        browseRoot.setPadding(immersiveOldLeft,immersiveOldTop,
                immersiveOldRight,immersiveOldBottom);
        if(credit!=null)credit.setVisibility(View.VISIBLE);
        inputEpoch++;noteInput("VERIFIED_FULLSCREEN_EXIT");
        filmEvent("film_fullscreen_exit");
        if(pointerPage&&browser!=null)resumePagePointer(false);
    }
    private String lastRequested=HOME;
    private final FilmConnectionState connection=new FilmConnectionState();
    private String connectionDetail="No certificate failure observed in this screen session.";
    private String connectionWarning="";private TextView networkNotice;private int filmErrors;
    private FilmTorController torController;
    private PhoneLinkServer phoneLink;
    private String phoneLinkUrl="";
    private final PhoneLinkServer.Sink filmPhoneSink=new PhoneLinkServer.Sink(){
        @Override public void move(int x,int y){runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;
            if(torWebMode&&browserBar!=null&&browserBar.showing()){
                browserBar.movePhone(x,y);return;
            }
            if(phoneCursorReady())cursor.moveRelative(x,y);
        });}
        @Override public void click(){runOnUiThread(()->{
            if(torWebMode&&browserBar!=null&&browserBar.showing()){
                browserBar.phoneClick();return;
            }
            if(!phoneCursorReady())return;
            final long t=android.os.SystemClock.uptimeMillis();
            cursor.handle(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));
            handler.postDelayed(()->{
                if(cursor!=null){
                    cursor.handle(new KeyEvent(t,android.os.SystemClock.uptimeMillis(),
                        KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));
                }
                if(pointerPage&&custom==null)handler.postDelayed(FlixMomoActivity.this::checkPageEditor,120);
            },95);
        });}
        @Override public void down(){runOnUiThread(()->{
            if(phoneCursorReady())cursor.handle(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        });}
        @Override public void up(){runOnUiThread(()->{
            if(cursor!=null)cursor.handle(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
            if(pointerPage&&custom==null)handler.postDelayed(FlixMomoActivity.this::checkPageEditor,120);
        });}
        @Override public void key(int code){runOnUiThread(()->{
            // On a website, paired-phone vertical arrows scroll, unlike the
            // physical D-pad, which continues to move the on-screen cursor.
            if((code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN)&&
                    pointerPage&&!pageEditor&&custom==null&&!verifiedImmersive){
                phoneScroll(0,code==KeyEvent.KEYCODE_DPAD_DOWN?230:-230);return;
            }
            final long t=android.os.SystemClock.uptimeMillis();
            dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0));
            dispatchKeyEvent(new KeyEvent(t,t+65,KeyEvent.ACTION_UP,code,0));
        });}
        @Override public void scroll(int dx,int dy){runOnUiThread(()->phoneScroll(dx,dy));}
        @Override public void action(String action){runOnUiThread(()->phoneAction(action));}
        @Override public void text(String value){runOnUiThread(()->phoneText(value,false));}
        @Override public void search(String term){runOnUiThread(()->phoneSubmitFilmQuery(term));}
        @Override public void submitPageText(String term){runOnUiThread(()->phoneText(term,true));}
        @Override public void edit(String command){runOnUiThread(()->phoneEdit(command));}
        @Override public void liveText(String value){runOnUiThread(()->phoneLiveText(value));}
        @Override public void chooseSource(int number){runOnUiThread(()->phoneChooseSource(number));}
        @Override public void seek(long positionMs){runOnUiThread(()->phoneSeek(positionMs));}
        @Override public void connected(boolean yes){runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;
            if(phoneConnected!=yes){
                phoneConnected=yes;updatePhoneBadge();
                phonePairEvent(yes?"connected":"disconnected");
            }
            if(yes&&pairingDialog!=null&&pairingDialog.isShowing()){
                pairingDialog.dismiss();pairingDialog=null;
                focusPhoneFilmSearch();
            }
            notifyPhoneMode();
        });}
    };
    /** Pair completion should leave a viewer ready to search, with no TV IME
     * hiding the provider; the phone gets a dedicated SEARCH state. */
    private void focusPhoneFilmSearch(){
        if(isFinishing()||isDestroyed()||custom!=null||verifiedImmersive)return;
        if(!browserSearchSubmitted&&!oneClickPlay){
            if(simplifiedDiscoverExperience())showBrowserSearch(false);
            else toolbar();
            query.requestFocus();phoneSearchWanted=true;
            hideTvSoftKeyboard();
            handler.postDelayed(this::hideTvSoftKeyboard,250L);
            notifyPhoneMode();
        }
    }
    private void hideTvSoftKeyboard(){
        android.view.inputmethod.InputMethodManager ime=
            (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(ime!=null){
            if(query!=null)ime.hideSoftInputFromWindow(query.getWindowToken(),0);
            if(browser!=null)ime.hideSoftInputFromWindow(browser.getWindowToken(),0);
        }
    }
    // Foreground-only phone playback state: the player, not an old start
    // monitor result, is authoritative. Never expose URLs or iframe contents.
    private boolean phoneMediaObservable,phoneSwitchingSource,phonePlaybackReading;
    private int phonePlaybackEpoch;
    private final FilmPhoneProgress phoneProgress=new FilmPhoneProgress();
    private long phonePlaybackTicket,phonePlaybackReadAt;
    private final Runnable phonePlaybackPulse=new Runnable(){
        @Override public void run(){
            if(isFinishing()||isDestroyed())return;
            if(phoneConnected)samplePhonePlayback();
            handler.postDelayed(this,850L);
        }
    };
    private static org.json.JSONObject decodePhonePlayback(String raw)throws Exception{
        Object encoded=new org.json.JSONTokener(raw==null?"null":raw).nextValue();
        if(!(encoded instanceof String)||((String)encoded).length()>512)
            throw new IllegalArgumentException("Media response unavailable");
        return new org.json.JSONObject((String)encoded);
    }
    // WebChromeClient fullscreen can be backed by a real Android VideoView
    // rather than a readable top-document <video>. Restrict native access to
    // that owned fullscreen view; arbitrary provider frames stay opaque.
    private static android.widget.VideoView phoneNativeVideo(View root){
        if(root instanceof android.widget.VideoView)return (android.widget.VideoView)root;
        if(!(root instanceof android.view.ViewGroup))return null;
        android.view.ViewGroup group=(android.view.ViewGroup)root;
        android.widget.VideoView result=null;
        for(int i=0;i<Math.min(64,group.getChildCount());i++){
            android.widget.VideoView candidate=phoneNativeVideo(group.getChildAt(i));
            if(candidate!=null){
                if(result!=null&&result!=candidate)return null;
                result=candidate;
            }
        }
        return result;
    }
    private boolean sampleNativeFullscreen(){
        if(custom==null)return false;
        android.widget.VideoView video=phoneNativeVideo(custom);
        if(video==null)return false;
        int rawDuration=video.getDuration();
        long duration=Math.max(0,rawDuration);
        long position=Math.max(0,video.getCurrentPosition());
        boolean seekable=duration>0&&
            (video.canSeekForward()||video.canSeekBackward());
        String state=video.isPlaying()?"playing":rawDuration<0?"starting":"paused";
        phoneMediaObservable=true;
        HouseholdPhoneSession.playback("{\"state\":\""+state+
            "\",\"fullscreen\":true,\"canFullscreen\":true,\"positionMs\":"+position+
            ",\"durationMs\":"+duration+",\"seekable\":"+seekable+
            ",\"seekStartMs\":0,\"seekEndMs\":"+(seekable?duration:0)+"}");
        return true;
    }
    private void samplePhonePlayback(){
        if(sampleNativeFullscreen())return;
        final WebView page=browser;
        if(page==null||homeRequested||detailRequested||torWebMode){
            // Phone movie controls must never read arbitrary Tor Web pages.
            phoneMediaObservable=false;
            HouseholdPhoneSession.playback("{\"state\":\"unknown\",\"fullscreen\":"+
                (custom!=null||verifiedImmersive)+"}");
            return;
        }
        long now=android.os.SystemClock.elapsedRealtime();
        if(phonePlaybackReading&&now-phonePlaybackReadAt<3000L)return;
        phonePlaybackReading=true;phonePlaybackReadAt=now;
        final long ticket=++phonePlaybackTicket;
        final int epoch=phonePlaybackEpoch;
        final String url=page.getUrl();
        page.evaluateJavascript(FilmPhonePlayback.sample(),raw->{
            if(ticket!=phonePlaybackTicket)return;
            phonePlaybackReading=false;
            if(page!=browser||epoch!=phonePlaybackEpoch||isFinishing()||isDestroyed()||
               !java.util.Objects.equals(url,page.getUrl()))return;
            try{
                org.json.JSONObject d=decodePhonePlayback(raw);
                String state=d.optString("state","unknown");
                if(!("playing".equals(state)||"paused".equals(state)||
                     "starting".equals(state)))state="unknown";
                phoneMediaObservable=d.optBoolean("observable",false);
                boolean seekable=phoneMediaObservable&&d.optBoolean("seekable",false);
                long position=Math.max(0,d.optLong("positionMs",0));
                if(nativeControls!=null&&phoneProgress.sample(epoch,
                        android.os.SystemClock.elapsedRealtime(),position,
                        phoneMediaObservable&&"playing".equals(state)))
                    nativeControls.phoneSourceClockProgress();
                long duration=Math.max(0,d.optLong("durationMs",0));
                long start=seekable?Math.max(0,d.optLong("seekStartMs",0)):0;
                long end=seekable?Math.max(start,d.optLong("seekEndMs",0)):0;
                boolean fullscreen=custom!=null||verifiedImmersive;
                boolean canFullscreen=fullscreen||(phoneMediaObservable&&!torWebMode);
                HouseholdPhoneSession.playback("{\"state\":\""+state+"\",\"fullscreen\":"+
                    fullscreen+",\"canFullscreen\":"+canFullscreen+
                    ",\"positionMs\":"+position+",\"durationMs\":"+duration+
                    ",\"seekable\":"+seekable+",\"seekStartMs\":"+start+
                    ",\"seekEndMs\":"+end+"}");
                if(phoneMediaObservable)notifyPhoneMode();
            }catch(Exception ignored){
                phoneMediaObservable=false;
                HouseholdPhoneSession.playback("{\"state\":\"unknown\",\"fullscreen\":"+
                    (custom!=null||verifiedImmersive)+"}");
            }
        });
    }
    private void phoneSeek(long positionMs){
        phonePlaybackCommand("seek",positionMs);
    }
    private void phonePlaybackAction(String action){
        phonePlaybackCommand(action,0);
    }
    private void phonePlaybackCommand(String operation,long positionMs){
        if(isFinishing()||isDestroyed()||positionMs<0||positionMs>86_400_000L)return;
        if(custom!=null){
            android.widget.VideoView video=phoneNativeVideo(custom);
            if(video!=null){
                if("seek".equals(operation)){
                    if(video.getDuration()<=0||
                       !(video.canSeekBackward()||video.canSeekForward())){
                        HouseholdPhoneSession.feedback("SEEK_UNAVAILABLE");return;
                    }
                    video.seekTo((int)Math.min(video.getDuration(),positionMs));
                }else if("pause".equals(operation))video.pause();
                else video.start();
                HouseholdPhoneSession.feedback("seek".equals(operation)?"SEEK_REQUESTED":
                    "play".equals(operation)?"PLAY_REQUESTED":"PAUSE_REQUESTED");
                handler.postDelayed(this::samplePhonePlayback,250L);
                return;
            }
        }
        final WebView page=browser;
        if(page==null||homeRequested||detailRequested||torWebMode){
            HouseholdPhoneSession.feedback("seek".equals(operation)?
                "SEEK_UNAVAILABLE":"CONTROL_UNAVAILABLE");return;
        }
        final int epoch=phonePlaybackEpoch;
        final String url=page.getUrl();
        page.evaluateJavascript(FilmPhonePlayback.command(operation,positionMs),raw->{
            if(page!=browser||epoch!=phonePlaybackEpoch||
               !java.util.Objects.equals(url,page.getUrl())||
               isFinishing()||isDestroyed())return;
            String outcome="UNAVAILABLE";
            try{outcome=decodePhonePlayback(raw).optString("result","UNAVAILABLE");}
            catch(Exception ignored){}
            if("REQUESTED".equals(outcome)){
                HouseholdPhoneSession.feedback("seek".equals(operation)?"SEEK_REQUESTED":
                    "play".equals(operation)?"PLAY_REQUESTED":"PAUSE_REQUESTED");
                handler.postDelayed(this::samplePhonePlayback,250L);
                return;
            }
            if(!"seek".equals(operation)&&custom!=null){
                // WebChromeClient custom views may route OS media keys through
                // their own player. Do not label this as verified playback.
                phoneMediaKey("play".equals(operation)?
                    KeyEvent.KEYCODE_MEDIA_PLAY:KeyEvent.KEYCODE_MEDIA_PAUSE);
                HouseholdPhoneSession.feedback("CONTROL_UNAVAILABLE");
            }else if("play".equals(operation)&&nativeControls!=null&&
                     !verifiedImmersive&&
                     (startMonitor==null||!startMonitor.healthy())){
                armPlayback();nativeControls.pageAction("media");
                HouseholdPhoneSession.feedback("PLAY_REQUESTED");
            }else HouseholdPhoneSession.feedback("seek".equals(operation)?
                "SEEK_UNAVAILABLE":"CONTROL_UNAVAILABLE");
        });
    }
    private void notifyPhoneMode(){
        if(custom!=null||verifiedImmersive)HouseholdPhoneSession.mode("FULLSCREEN");
        // Explicit Tor Web address entry outranks a stale film-search request.
        // Otherwise the paired phone can stay in SEARCH despite Browser opening.
        else if(torWebMode&&browserBar!=null&&browserBar.showing()&&
                browserBar.address().hasFocus())HouseholdPhoneSession.mode("EDITOR");
        else if(chrome!=null&&chrome.isShown()&&query!=null&&
                (query.hasFocus()||phoneSearchWanted&&!browserSearchSubmitted))
            HouseholdPhoneSession.mode("SEARCH");
        else if(pointerPage&&pageEditor)HouseholdPhoneSession.mode("EDITOR");
        else if(phoneMediaObservable||startMonitor!=null&&startMonitor.healthy())
            HouseholdPhoneSession.mode("VIDEO");
        else if(startMonitor!=null&&startMonitor.running())HouseholdPhoneSession.mode("STARTING");
        else HouseholdPhoneSession.mode("BROWSE");
    }
    private void phoneScroll(int dx,int dy){
        if(isFinishing()||isDestroyed()||cursor==null||browser==null)return;
        if(torWebMode&&browserBar!=null&&browserBar.showing()){
            browserBar.hideBar();enterPagePointer();
        }
        if(phoneCursorReady())cursor.scrollFromPhone(dx,dy);
    }
    private void phoneAction(String action){
        if(isFinishing()||isDestroyed())return;
        switch(action){
            case "BACK":onBackPressed();return;
            case "FULLSCREEN":
                if(custom!=null){exitFullScreen();return;}
                if(verifiedImmersive){leaveVerifiedFullscreen();return;}
                if(pointerPage&&!torWebMode){requestPageFullscreen(true);return;}
                HouseholdPhoneSession.feedback("FULLSCREEN_UNAVAILABLE");
                return;
            case "SOURCES":
                // Open the choice list on the phone; never obscure the TV video.
                if(nativeControls!=null)nativeControls.requestPhoneSources();
                HouseholdPhoneSession.refreshSources();
                return;
            case "NEXT":
                phonePlaybackEpoch++;phoneMediaObservable=false;
                if(verifiedImmersive)leaveVerifiedFullscreen();
                if(custom!=null){phoneSwitchingSource=true;exitFullScreen();phoneSwitchingSource=false;}
                // Phone Next is a deliberate manual choice, and must not
                // depend on an active TV tray, source-start budget or pointer
                // focus. Selecting a new source releases the old player.
                if(startMonitor!=null)startMonitor.pause();
                if(nativeControls!=null)nativeControls.nextPhoneSource();
                return;
            case "PLAY":phonePlaybackAction("play");return;
            case "PAUSE":phonePlaybackAction("pause");return;
            case "GO":
                if(torWebMode&&browserBar!=null&&browserBar.showing()
                        &&browserBar.address().hasFocus()){browserBar.goFromPhone();return;}
                if(chrome!=null&&chrome.isShown()&&query!=null&&query.hasFocus()){
                    search();return;
                }
                if(browser!=null&&pointerPage&&pageEditor){
                    long t=android.os.SystemClock.uptimeMillis();
                    browser.dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0));
                    browser.dispatchKeyEvent(new KeyEvent(t,t+40,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,0));
                }return;
            case "SEARCH":
                if(custom!=null)exitFullScreen();
                if(verifiedImmersive)leaveVerifiedFullscreen();
                toolbar();query.requestFocus();notifyPhoneMode();return;
        }
    }
    /** Atomic search: no dependency on WebView focus or a prior Type press. */
    private void phoneSubmitFilmQuery(String term){
        if(isFinishing()||isDestroyed())return;
        String cleaned=UnifiedSearch.clean(term==null?"":term);
        if(!UnifiedSearch.valid(cleaned)){
            HouseholdPhoneSession.feedback("INVALID_QUERY");return;
        }
        if(custom!=null)exitFullScreen();
        if(verifiedImmersive)leaveVerifiedFullscreen();
        toolbar();
        query.setText(cleaned);
        query.setSelection(query.length());
        query.requestFocus();
        hideTvSoftKeyboard();
        search();
        hideTvSoftKeyboard();
        handler.postDelayed(this::hideTvSoftKeyboard,300L);
        HouseholdPhoneSession.feedback("SEARCH_SENT");
    }
    private void phoneMediaKey(int code){
        long t=android.os.SystemClock.uptimeMillis();
        KeyEvent down=new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0);
        KeyEvent up=new KeyEvent(t,t+80,KeyEvent.ACTION_UP,code,0);
        if(custom!=null){
            // Prefer the fullscreen player's own focused view; the hidden
            // browser/activity does not necessarily own native media keys.
            custom.dispatchKeyEvent(down);custom.dispatchKeyEvent(up);
        }else{dispatchKeyEvent(down);dispatchKeyEvent(up);}
    }
    private Button phoneBadge;
    private android.app.Dialog pairingDialog;
    private boolean phoneConnected,phoneIntroShown,phoneSearchWanted;
    private String phonePairType="wifi";
    private void updatePhoneBadge(){
        if(phoneBadge==null)return;
        phoneBadge.setText(phoneConnected?"● Paired":"● Phone");
        phoneBadge.setTextColor(phoneConnected?0xFFB5FFE9:0xFFFFC6BE);
        phoneBadge.setBackground(TvUi.rounded(
            phoneConnected?0xFF075448:0xFF412A32,12,
            phoneConnected?0xFF46E8B5:0xFFB87772,1.1f,this));
        phoneBadge.setContentDescription(phoneConnected?
            "Phone connected. Select to manage.":
            "Phone not connected. Select to scan QR, or use the TV remote.");
    }
    private void phonePairEvent(String action){
        Telemetry.event(this,"phone_remote_"+action,Telemetry.data(
            "route","home_network","event_version_code",BuildConfig.VERSION_CODE));
    }
    private FilmTorPosterLoader torPosterLoader;
    private String lastFilmFailure="No film transport error recorded in this screen session.";
    private String filmRoute(){return torController!=null&&torController.active()?"tor":"tor_pending";}
    private void filmEvent(String name){Telemetry.event(this,name,Telemetry.data("surface","discover","route",filmRoute(),"event_version_code",BuildConfig.VERSION_CODE));}
    private void filmFailure(String stage,String reason){
        lastFilmFailure=stage+" · "+reason+" · route "+filmRoute();
        if(filmErrors++<8)Telemetry.error(this,stage,new IllegalStateException(reason),Telemetry.data("provider","flixmomo","route",filmRoute(),"event_version_code",BuildConfig.VERSION_CODE));
    }
    /** Submitted film searches own the full provider browser until Search/Menu explicitly restores the toolbar. */
    private void keepSubmittedSearchBrowserOnly(){
        if(!simplifiedDiscoverExperience()||!browserSearchSubmitted||!pointerPage||custom!=null||chrome==null)return;
        chrome.setVisibility(View.GONE);
        if(query!=null&&query.hasFocus())hideKeyboard();
    }
    private void showFilmConnection(){
        String version="unknown";try{android.content.pm.PackageInfo p=WebView.getCurrentWebViewPackage();if(p!=null)version=p.versionName;}catch(Exception ignored){}
        String route=torController==null?"Unavailable":torController.routeLabel()+(torController.enabled()?" · Tor default enabled":" · Tor default off");
        String message=ReviewDisplay.technical()+"\n\n"+connectionDetail+"\n\nFlixMomo route: "+route+"\nTV time: "+new java.util.Date()+"\nAndroid: "+android.os.Build.VERSION.RELEASE+" · WebView: "+version+"\n\nCertificate checks stay on. No passwords, cookies or search text are sent by a connection check. This film proxy never captures JioTV, IPTV, updates or diagnostics; JioTV/IPTV route recovery is separate.";
        new android.app.AlertDialog.Builder(this).setTitle("FlixMomo connection")
            .setMessage(message+"\n\nFilm and Tor web navigation never uses a direct connection.")
            .setPositiveButton("Tor status",(d,w)->showTorRoute())
            .setNegativeButton("Close",null).show();
    }
    private void chooseFilmOrigin(){
        final String[] roots={FilmProviderPolicy.HOME,"https://flixmomo.app/","https://flixmomo.st/","TV date and time settings"};
        new android.app.AlertDialog.Builder(this).setTitle("Open an already registered address")
            .setItems(roots,(d,index)->{if(index==3){try{startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS));}catch(Exception ignored){}return;}new android.app.AlertDialog.Builder(this).setTitle("Use "+roots[index]+"?")
                .setMessage("This starts a fresh navigation to an address already registered in this build. Your current search will be sent to it. Normal certificate checks apply. No cookies or passwords are copied between domains.")
                .setPositiveButton("Open",(confirm,which)->selectProviderOrigin(roots[index]))
                .setNegativeButton("Cancel",null).show();}).setNegativeButton("Close",null).show();
    }
    private void resumeCatalogueMode(){
        endOneClick();
        hideProviderStatus();
        pointerPage=false;pageEditor=false;pointerTitleOrigin="";
        if(mouseActive)leaveMouse(false);else cursor.enable(false);
        nativeControls.resume();credit.setText(FilmProviderPolicy.CREDIT+torRouteSuffix());credit.setVisibility(View.VISIBLE);
    }
    void selectProviderOrigin(String value){
        String root=FilmProviderPolicy.registeredHome(value);if(root==null)return;
        if(custom!=null)exitFullScreen();
        resumeCatalogueMode();if(startMonitor!=null)startMonitor.leave();
        handler.removeCallbacks(detailWait);handler.removeCallbacks(playDeadline);
        String text=UnifiedSearch.clean(query.getText().toString());
        playUntil=0;playNotice.setVisibility(View.GONE);detailRequested=false;detailPanel.setVisibility(View.GONE);
        homeRequested=true;nativeSearch=UnifiedSearch.valid(text)?text:"";homePanel.mode(nativeSearch);homePanel.discardSuggestions();homePanel.loading();homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();
        nativeControls.hideAll();chrome.setVisibility(View.VISIBLE);
        rememberProviderHome(root);
        navigate(root+(nativeSearch.isEmpty()?"":"search?q="+Uri.encode(nativeSearch)),false);homePanel.focusRefresh();
    }
    private boolean catalogueResourceFailure(Uri uri,FilmTorFallback.Failure failure,String reason){
        // A catalogue dependency failure is not a movie-playback failure. The
        // explicit fallback may retry only the reviewed fixed host set.
        if(uri!=null&&"api.themoviedb.org".equals(uri.getHost())){
            if(torController!=null&&torController.failure(uri.toString(),failure))return true;
            String message="The provider page opened, but its catalogue service could not be reached ("+reason+"). Page reachability is not catalogue readiness.";
            if(homeRequested&&homePanel!=null)homePanel.unavailable(message);
            else if(pointerPage)providerStatus(message,true);
        }
        return false;
    }
    void certificateFailure(Runnable cancel,SslError error){
        cancel.run(); // The failed resource is NEVER trusted or loaded.
        String url=error==null?"":error.getUrl();int code=error==null?SslError.SSL_INVALID:error.getPrimaryError();
        FilmConnectionState.Scope scope=connection.scope(url);
        connectionDetail=scope.name().replace('_',' ')+" · "+FilmConnectionState.reason(code)+"\n"+FilmConnectionState.origin(url)+"\n"+FilmConnectionState.help(code);
        filmFailure(scope==FilmConnectionState.Scope.SUBRESOURCE?"film_resource_tls":"film_document_tls",FilmConnectionState.reason(code));
        if(scope==FilmConnectionState.Scope.SUBRESOURCE){
            connectionWarning="A supporting request failed certificate validation. Connection has details; this is not a film-playback result.";
            if(startMonitor!=null)startMonitor.requestFailure("Supporting TLS: "+FilmConnectionState.reason(code));
            networkNotice.setText(connectionWarning);networkNotice.setVisibility(homeRequested||detailRequested?View.VISIBLE:View.GONE);
        }else{
            networkNotice.setText(connectionDetail);networkNotice.setVisibility(View.VISIBLE);
            fail("Secure connection could not be verified: "+FilmConnectionState.reason(code)+". Open Connection for details; no certificate bypass is available.");
        }
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable slowLoad=()->{if(loading&&!mainFrameError){if(torController!=null&&torController.failure(lastRequested,FilmTorFallback.Failure.NO_CATALOGUE_RESPONSE))return;status.setText("Still connecting. Retry is available; playback is not verified.");if(homeRequested&&homePanel!=null)homePanel.unavailable("The provider is taking longer than expected.");else if(pointerPage)providerStatus("The provider page has not finished loading. Check the connection or retry; no catalogue or playback is confirmed.",true);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        lastRequested=initialProviderHome();Telemetry.screen(this,"discover");filmEvent("discover_open");getWindow().getDecorView().setSystemUiVisibility(5894);
        screen=new FrameLayout(this);
        LinearLayout root=new LinearLayout(this);browseRoot=root;root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(TvUi.BG);
        root.setPadding(TvUi.dp(this,18),TvUi.dp(this,10),TvUi.dp(this,18),TvUi.dp(this,10));
        chrome=new LinearLayout(this);chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.addView(TvUi.label(this,ReviewDisplay.films(),18,TvUi.TEXT,true));
        LinearLayout row=new LinearLayout(this);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(TvUi.TEXT);query.setHintTextColor(TvUi.MUTED);
        query.setHint(simplifiedDiscoverExperience()?"Search films or series":"Live channel, film or series");query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        row.addView(query,new LinearLayout.LayoutParams(0,TvUi.dp(this,48),1));
        Button search=TvUi.button(this,"Search",true),voice=TvUi.button(this,"Voice",false),browse=TvUi.button(this,"Discover",false),web=TvUi.button(this,"Tor web",false),guide=TvUi.button(this,simplifiedDiscoverExperience()?"Back to TV guide":"TV guide",false);
        Button phone=TvUi.button(this,"● Phone",false);
        phoneBadge=phone;
        search.setTextSize(14);voice.setText("Mic");voice.setTextSize(14);
        web.setText("Browser");web.setTextSize(14);
        guide.setText("TV guide");guide.setTextSize(14);
        phone.setTextSize(14);phone.setPadding(0,0,0,0);
        updatePhoneBadge();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(search,new LinearLayout.LayoutParams(TvUi.dp(this,91),TvUi.dp(this,46)));
        row.addView(voice,new LinearLayout.LayoutParams(TvUi.dp(this,70),TvUi.dp(this,46)));
        if(!simplifiedDiscoverExperience())row.addView(browse);
        row.addView(web,new LinearLayout.LayoutParams(TvUi.dp(this,94),TvUi.dp(this,46)));
        row.addView(phone,new LinearLayout.LayoutParams(TvUi.dp(this,102),TvUi.dp(this,46)));
        row.addView(guide,new LinearLayout.LayoutParams(TvUi.dp(this,105),TvUi.dp(this,46)));
        chrome.addView(row);
        web.setOnClickListener(v->showTorBrowserAddress());
        phone.setOnClickListener(v->showPhoneRemote());
        pageButton=TvUi.button(this,"Options",false);if(!simplifiedDiscoverExperience())row.addView(pageButton);toolbarAnchor=simplifiedDiscoverExperience()?search:pageButton;
        // Detached compatibility references are not duplicate on-screen controls.
        modeButton=new Button(this);scrollButton=new Button(this);retryButton=TvUi.button(this,"Retry page",false);
        help=TvUi.label(this,"",12,TvUi.MUTED,false);
        status=TvUi.label(this,"",12,TvUi.MUTED,false);
        pageButton.setOnClickListener(v->showOptions());
        root.addView(chrome);
        credit=TvUi.label(this,FilmProviderPolicy.CREDIT,12,TvUi.MINT,true);credit.setPadding(0,TvUi.dp(this,5),0,TvUi.dp(this,6));root.addView(credit);
        networkNotice=TvUi.label(this,"",12,TvUi.MUTED,false);networkNotice.setVisibility(View.GONE);root.addView(networkNotice);
        playNotice=TvUi.label(this,"",12,TvUi.MUTED,false);playNotice.setVisibility(View.GONE);root.addView(playNotice);
        // Visible recovery belongs to the existing provider screen, not an overlay
        // over video. The old status/retry objects were detached during pointer loading.
        providerStatusPanel=new LinearLayout(this);providerStatusPanel.setOrientation(LinearLayout.VERTICAL);
        providerStatusPanel.addView(status,new LinearLayout.LayoutParams(-1,-2));
        providerRecoveryActions=new LinearLayout(this);providerRecoveryActions.addView(retryButton);
        Button connectionButton=TvUi.button(this,"Connection",false),resultsButton=TvUi.button(this,"Back to results",false);
        connectionButton.setOnClickListener(v->showFilmConnection());resultsButton.setOnClickListener(v->returnToCards());
        providerRecoveryActions.addView(connectionButton);providerRecoveryActions.addView(resultsButton);
        for(int i=0;i<providerRecoveryActions.getChildCount();i++)providerRecoveryActions.getChildAt(i).setId(View.generateViewId());
        for(int i=0;i<providerRecoveryActions.getChildCount();i++){View b=providerRecoveryActions.getChildAt(i);b.setNextFocusLeftId(providerRecoveryActions.getChildAt(Math.max(0,i-1)).getId());b.setNextFocusRightId(providerRecoveryActions.getChildAt(Math.min(2,i+1)).getId());}
        providerRecoveryActions.setVisibility(View.GONE);providerStatusPanel.setVisibility(View.GONE);root.addView(providerStatusPanel);
        liveResults=new android.widget.HorizontalScrollView(this);liveResults.setHorizontalScrollBarEnabled(false);liveResults.setVisibility(View.GONE);
        liveRow=new LinearLayout(this);liveResults.addView(liveRow);root.addView(liveResults,new LinearLayout.LayoutParams(-1,TvUi.dp(this,48)));
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        fullHost=new FrameLayout(this);fullHost.setBackgroundColor(android.graphics.Color.BLACK);fullHost.setVisibility(View.GONE);screen.addView(fullHost,new FrameLayout.LayoutParams(-1,-1));setContentView(screen);
        browserBar=new TorBrowserChrome(this,new TorBrowserChrome.Host(){
            public void navigate(String url){openTorWebsiteTarget(url);}
            public void back(){if(browser!=null&&browser.canGoBack())browser.goBack();
                else if(!torTabs.isEmpty())switchPreviousTab();}
            public void forward(){if(browser!=null&&browser.canGoForward())browser.goForward();}
            public void reload(){if(browser!=null&&torController!=null&&torController.active())browser.reload();}
            public void close(){browserBar.hideBar();enterPagePointer();}
            public void find(){showTorFind();}
            public void bookmarks(){showTorBookmarks();}
            public void settings(){TorNetworkSettings.show(FlixMomoActivity.this);}
            public void phone(){showPhoneRemote();}
            public void page(){browserBar.hideBar();enterPagePointer();}
        });
        screen.addView(browserBar,new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP));
        cursor=new RemoteWebCursor(this);cursor.setObserver(this::noteInput);
        cursor.setEdgeListener((x,y)->{
            if(torWebMode&&browserBar!=null&&!browserBar.showing()&&y<TvUi.dp(this,18)){
                browserBar.showBar(browser==null?null:browser.getUrl(),false);
                cursor.leave();
            }
        });
        stage.addView(cursor,new FrameLayout.LayoutParams(-1,-1));cursor.enable(false);
        nativeControls=new FilmNativeControls(this,stage,chrome,new FilmNativeControls.Host(){
            public void navigate(String url){if(oneClickPlay&&watchPage(Uri.parse(url))){oneClickWatchSent=true;handler.removeCallbacks(oneClickExpire);armPlayback();FlixMomoActivity.this.navigate(url,false);}else if(pointerFirstBrowsing())openProviderTitle(url);else if(FilmProviderPolicy.detail(Uri.parse(url)))openDetail(url);else FlixMomoActivity.this.navigate(url,false);}
            public void usePage(){FlixMomoActivity.this.usePage();}
            public void nativeMode(){cursor.enable(false);modeButton.setText("Cursor: off");}
            public void navigationHint(String text){help.setText(text);}
            public void searchToolbar(){toolbar();query.requestFocus();}
            public void fullscreen(){inputEpoch++;noteInput("FULLSCREEN_REQUEST");nativeControls.pageAction("fullscreen");}
            public void controlsVisibility(boolean visible){if(startMonitor!=null)startMonitor.controlsOpen(visible);}
            public void mouse(){enterMouse();}
            public boolean pageIsVisible(){return !homeRequested&&!detailRequested&&custom==null&&!(chrome.getVisibility()==View.VISIBLE&&chrome.hasFocus());}
            public void pageObserved(org.json.JSONObject data){oneClickObserved(data);observeDetail(data);visitCache.put(data.optJSONObject("detail"));if(homeRequested&&homePanel!=null){homePanel.render(data);if(torController!=null)torController.catalogueObserved(homePanel.cardCount());}else if(detailRequested&&detailPanel!=null)detailPanel.render(data.optJSONObject("detail"));else if(!data.optBoolean("search"))liveResults.setVisibility(View.GONE);followPlayback(data);}
            public void pageAction(String action){if("watch".equals(action))requestWatch();else if("media".equals(action)){armPlayback();mediaAttempted=true;nativeControls.pageAction("media");}else nativeControls.pageAction(action);}
            public void playerSelected(){phonePlaybackEpoch++;phoneMediaObservable=false;if(startMonitor!=null)startMonitor.selected();armPlayback();}
            public boolean autoStartSourceOnWatchPage(){
                return !torWebMode&&pointerPage&&oneClickPlay&&oneClickWatchSent&&
                    browser!=null&&watchPage(Uri.parse(browser.getUrl()));
            }
            public void phoneSourceOutcome(String code){HouseholdPhoneSession.feedback(code);}
            public void sourcesChanged(String json){HouseholdPhoneSession.sources(json);}
            public void actionOutcome(String action,String result){
                controlTrace.emit(FlixMomoActivity.this,action,result);
                noteInput(result);
                if("ACTION_PENDING".equals(result))return;
                if(("fullscreen".equals(action)&&!"CLICK_SENT_NOT_PLAYBACK_PROOF".equals(result)&&!result.startsWith("TARGET_"))||"TARGET_OBSCURED".equals(result)||"TARGET_CHANGED".equals(result)){
                    final long epoch=inputEpoch;
                    handler.post(()->{if(epoch==inputEpoch&&custom==null&&!isFinishing()&&!homeRequested&&!detailRequested&&!mouseActive&&chrome.getVisibility()!=View.VISIBLE)enterMouse();});
                }
                if("watch".equals(action)||"media".equals(action)){
                    if("AMBIGUOUS_WATCH".equals(result)||"WATCH_ACTION_CHANGED".equals(result)||"VISUAL_TIMEOUT".equals(result)||"NOT_FOUND".equals(result)||"AMBIGUOUS_MEDIA".equals(result)||"TARGET_OBSCURED".equals(result)||"TARGET_CHANGED".equals(result)){playUntil=0;endOneClick();playMessage("Playback has not started: "+result.replace('_',' ').toLowerCase(java.util.Locale.ROOT)+". Use Original page or another player.");if(startMonitor!=null)startMonitor.pause();}
                    else if("CLICK_SENT_NOT_PLAYBACK_PROOF".equals(result)){
                        playMessage("Starting player…");
                        final String pending="Starting player…";
                        handler.postDelayed(()->{
                            if(playNotice!=null&&pending.contentEquals(playNotice.getText())&&custom==null)
                                playNotice.setVisibility(View.GONE);
                        },3500L);
                    }
                }
            }
            public void verificationRequired(){if(homeRequested&&homePanel!=null)homePanel.unavailable("Provider verification is required.");else if(detailRequested)detailPanel.unavailable("Provider verification is required.");else toolbar();}
        });
        torController=torFallbackEnabledForBuild()?createTorController():null;
        torPosterLoader=torController==null?null:new FilmTorPosterLoader(this,torController);
        homePanel=createHomePanel(new FilmHomeView.Host(){
            public void open(String url){if(pointerFirstBrowsing())openProviderTitle(url);else openDetail(url);}
            public void refresh(){if(nativeSearch.isEmpty())showHome(true);else search();}
            public void provider(){String destination=providerOrigin()+(nativeSearch.isEmpty()?"/":"/search?q="+Uri.encode(nativeSearch));if(pointerFirstBrowsing())openProviderTitle(destination);else{homeRequested=false;homePanel.setVisibility(View.GONE);navigate(destination,true);}}
            public void privacy(){ReviewNotice.show(FlixMomoActivity.this,()->{
                if(browser!=null){browser.stopLoading();browser.loadUrl("about:blank");browser.clearCache(true);browser.clearHistory();browser.clearFormData();}
                visitCache.clear();com.bumptech.glide.Glide.get(FlixMomoActivity.this).clearMemory();
                nativeControls.failure();homeRequested=true;liveGeneration++;liveResults.setVisibility(View.GONE);homePanel.discardSuggestions();homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();chrome.setVisibility(View.VISIBLE);
                homePanel.unavailable("Film site data cleared. Suggestions will reload only when you choose Refresh.");
            });}
        });
        homePanel.setLoadingObserver(new FilmHomeView.LoadingObserver(){
            public boolean timeout(){return torController!=null&&torController.failure(lastRequested,FilmTorFallback.Failure.NO_CATALOGUE_RESPONSE);}
            public void readable(int count){if(torController!=null)torController.catalogueObserved(count);}
        });
        stage.addView(homePanel,new FrameLayout.LayoutParams(-1,-1));
        detailPanel=createDetailPanel(new FilmDetailView.Host(){
            public void watch(){requestWatch();}
            public void watchlist(){usePage();nativeControls.pageAction("watchlist");}
            public void original(){usePage();}
            public void back(){returnToCards();}
        });stage.addView(detailPanel,new FrameLayout.LayoutParams(-1,-1));
        startMonitor=new FilmPlaybackMonitor(this,new FilmPlaybackMonitor.Host(){
            public WebView page(){return browser;}
            public boolean samePlaybackScreen(){return !homeRequested&&!detailRequested&&!isFinishing();}
            public boolean next(boolean manual){return nativeControls.tryNextPlayer(manual);}
            public boolean canSkipEmptySource(){
                return !torWebMode&&!loading&&pageReady&&nativeControls!=null&&
                    nativeControls.hasUntriedSourceAfterCurrent();
            }
            public void sources(){if(custom!=null)exitFullScreen();usePage();nativeControls.openSources();}
            public void cancelPage(){
                playUntil=0;endOneClick();handler.removeCallbacks(playDeadline);inputEpoch++;nativeControls.pause();
                if(custom!=null)exitFullScreen();if(mouseActive)leaveMouse(false);
                if(browser!=null){browser.stopLoading();browser.loadUrl("about:blank");}
                detailRequested=false;detailPanel.setVisibility(View.GONE);homeRequested=true;homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();chrome.setVisibility(View.VISIBLE);homePanel.focusRefresh();
            }
            public void changed(String state){if("FIRST_FRAME_AND_PROGRESS".equals(state)){playNotice.setVisibility(View.GONE);nativeControls.recordVerifiedPlayback();
                nativeControls.phoneSourceVerified();endOneClick();
                // When playback starts from a previously selected source,
                // foreground focus belongs to the one visible player, not the
                // old sources tray or TV search field.
                if(pointerPage&&browser!=null&&custom==null&&!torWebMode){
                    browser.requestFocus();
                    browser.evaluateJavascript(FilmPlaybackProbe.focusVisiblePlayer(),null);
                }
                notifyPhoneMode();
                if(!torWebMode&&custom==null&&!fullscreenProofPrompted&&!isFinishing()){
                    fullscreenProofPrompted=true;
                    autoVerifiedFullscreen();
                }
            }controlTrace.emit(FlixMomoActivity.this,"media",state.equals("FIRST_FRAME_AND_PROGRESS")?"CLOCK_ADVANCED":"UNCONFIRMED");}
        });
        browseRoot.addView(startMonitor.panel,browseRoot.indexOfChild(stage),new LinearLayout.LayoutParams(-1,-2));
        homePanel.setToolbarAnchor(toolbarAnchor);
        query.setOnFocusChangeListener((v,focused)->{
            if(focused)cursor.leave();
            if(phoneConnected){
                if(focused)handler.postDelayed(this::hideTvSoftKeyboard,95L);
                notifyPhoneMode();
            }
        });
        search.setOnClickListener(v->search());voice.setOnClickListener(v->voiceSearch());browse.setOnClickListener(v->showHome(false));guide.setOnClickListener(v->finish());
        query.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH||event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP){search();return true;}return false;});
        pageButton.setOnClickListener(v->showOptions());
        modeButton.setOnClickListener(v->{cursor.enable(!cursor.enabled());modeButton.setText(cursor.enabled()?"Cursor: on":"Cursor: off");help.setText(cursor.enabled()?"Arrows move cursor; OK clicks; Menu shows controls.":"Arrows browse original posters; OK opens; Menu shows controls.");usePage();});
        scrollButton.setOnClickListener(v->{if(!cursor.enabled()){cursor.enable(true);modeButton.setText("Cursor: on");}cursor.scrollMode(!cursor.scrolling());scrollButton.setText(cursor.scrolling()?"Scroll: on":"Scroll: off");usePage();});
        retryButton.setOnClickListener(v->{if(browser==null)createBrowser();navigate(lastRequested,true);});
        if(!createBrowser())return;
        String incoming=UnifiedSearch.clean(getIntent().getStringExtra(UnifiedSearch.QUERY));
        if(UnifiedSearch.valid(incoming)){query.setText(incoming);search();}
        else if(saved!=null&&!saved.getBoolean("home",true)){query.setText(saved.getString("query",""));homeRequested=false;homePanel.setVisibility(View.GONE);String url=saved.getString("url",HOME);if(pointerFirstBrowsing())openProviderTitle(allowedTop(Uri.parse(url))?url:HOME);else navigate(allowedTop(Uri.parse(url))?url:HOME,false);}
        else showHome(false);
        // Keep the TV search text field selected BEFORE the QR dialog or
        // phone connects. The previous unconditional toolbarAnchor focus
        // overrode showBrowserSearch() and stranded phone input in BROWSE.
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if(simplifiedDiscoverExperience()&&chrome.getVisibility()==View.VISIBLE&&
                !browserSearchSubmitted){
            query.requestFocus();phoneSearchWanted=true;notifyPhoneMode();
        }else toolbarAnchor.requestFocus();
        if(getIntent().getBooleanExtra(EXTRA_PHONE_SEARCH_ON_ENTRY,false)){
            getIntent().removeExtra(EXTRA_PHONE_SEARCH_ON_ENTRY);
            // Wait until the Activity/window and the shared phone sink are
            // ready; never inject a search query or submit provider content.
            handler.post(this::focusPhoneFilmSearch);
        }
        // One optional pairing offer when the film screen opens; pressing Back or
        // "Use TV remote" continues normally, no forced phone dependency.
        handler.postDelayed(()->{
            if(autoOfferPhoneOnEntry()&&!phoneIntroShown&&!isFinishing()&&!isDestroyed()&&custom==null){
                phoneIntroShown=true;
                if(HouseholdPhoneSession.connected()){
                    HouseholdPhoneSession.attach(filmPhoneSink);
                    phoneConnected=true;updatePhoneBadge();
                    focusPhoneFilmSearch();notifyPhoneMode();
                }else{
                    // Films is its own entry: one non-blocking pairing offer
                    // every time Films opens unpaired, even when Home QR was skipped.
                    phonePairEvent("offered");showPhoneRemote();
                }
            }
        },650L);
    }
    private boolean createBrowser(){
        if(browser!=null)return true;
        try{browser=new WebView(this);}catch(RuntimeException e){fail("Android System WebView is unavailable. Update that system component; live TV is unchanged.");return false;}
        browser.setBackgroundColor(TvUi.BG);stage.addView(browser,0,new FrameLayout.LayoutParams(-1,-1));cursor.target(browser);nativeControls.attach(browser);homePanel.bindUnderlyingPage(browser);detailPanel.bindUnderlyingPage(browser);
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSafeBrowsingEnabled(true);
        // Document-start script operates in HTTPS player frames, carries ONLY
        // bounded media state, and exposes no JS-to-native object to websites.
        // Older WebViews retain top-document and native player support.
        try{
            if(androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT))
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(browser,
                    FilmPhoneFrameBridge.documentStart(),java.util.Collections.singleton("*"));
        }catch(RuntimeException unsupported){
            android.util.Log.w("GharTVPhone","WebView frame media bridge unavailable");
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.setDownloadListener((u,a,d,m,n)->status.setText("Downloads are not enabled in this provider view."));
        browser.setOnFocusChangeListener((v,focused)->{
            // Simplified Discover: moving down from the search toolbar into the page hands
            // the page to the remote pointer (Review56 left the toolbar up and the pointer off).
            if(focused&&simplifiedDiscoverExperience()&&pointerPage&&custom==null&&!mouseActive
                &&!homeRequested&&!detailRequested&&chrome.getVisibility()==View.VISIBLE
                &&!phoneSearchWanted){enterPagePointer();return;}
            // Browser focus moves during clicks/fullscreen/dialogs. Pointer mode owns
            // its own visibility; a WebView focus callback must not dismiss it.
            if(!mouseActive&&custom==null){if(focused)cursor.enter();else cursor.leave();}
        });
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                connection.observe(request.getUrl().toString(),request.isForMainFrame());
                if(NATIVE_PLAYER_EXPERIMENT&&request.isForMainFrame()&&request.hasGesture()&&NativeFilmPlayerActivity.supported(request.getUrl())){
                    // Handle only an explicit user-selected direct media navigation.
                    // No reading of iframe internals or browser media requests.
                    nativeControls.pause();
                    startActivity(new Intent(FlixMomoActivity.this,NativeFilmPlayerActivity.class).putExtra(NativeFilmPlayerActivity.EXTRA_MEDIA,request.getUrl().toString()));
                    return true;
                }
                if(!request.isForMainFrame())return false;
                String target=request.getUrl().toString();
                if(!allowedBrowserPage(request.getUrl())){
                    // External website must be explicitly chosen, not an automatic
                    // redirect/ad pop-up. No cleartext, private LAN or identity links.
                    if(request.hasGesture()){
                        android.widget.Toast.makeText(FlixMomoActivity.this,
                            "External window blocked. Use Tor Web for other sites.",
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                    // Ads, automatic popup redirects and provider scripts never
                    // get to create a new window or force an external navigation.
                    return true;
                }
                // A detached or failed Tor proxy must never fall through into WebView.
                if(torController==null||!torController.active()){
                    navigate(target,false);return true;
                }
                return false;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                connection.observe(request.getUrl().toString(),request.isForMainFrame());
                Uri uri=request.getUrl();String host=uri.getHost();
                if(!"https".equals(uri.getScheme())||host==null||privateHost(host)||
                    host.equalsIgnoreCase("api.github.com")||
                    host.equalsIgnoreCase("jiotvapi.media.jio.com")||
                    host.equalsIgnoreCase("jiotvapi.cdn.jio.com")||
                    host.equalsIgnoreCase("ghartv-telemetry.ghartv-47d9a0.workers.dev")||
                    torController==null||!torController.active())
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageCommitVisible(WebView view,String url){if(!mainFrameError&&allowedTop(Uri.parse(url))&&!mouseActive&&!pointerPage)nativeControls.documentReady();}
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){
                // A same-URL refresh is still a new document. Never let its
                // previous playback observation or pending command cross over.
                phonePlaybackEpoch++;phoneMediaObservable=false;
                phonePlaybackReading=false;phonePlaybackTicket++;
                HouseholdPhoneSession.playback("{\"state\":\"unknown\",\"fullscreen\":false}");
                if(verifiedImmersive&&!java.util.Objects.equals(verifiedImmersiveUrl,url))
                    leaveVerifiedFullscreen();
                cursor.cancel();nativeControls.started();keepSubmittedSearchBrowserOnly();if("about:blank".equals(url))return;
                if(!allowedBrowserPage(Uri.parse(url))||
                        (!ownedFixtureForDebug(url)&&(torController==null||!torController.active()))){
                    view.stopLoading();status.setText("Page stopped: Tor is required for every website.");return;
                }
                if(!torWebMode)oneClickArrived(url);
                if(!java.util.Objects.equals(url,lastRequested)&&torController!=null)
                    torController.beginNavigation(url);
                connection.started(url);
                if(detailRequested&&FilmProviderPolicy.detail(Uri.parse(url)))detailPanel.followNavigation(url);
                else if(detailRequested&&allowedBrowserPage(Uri.parse(url))&&!FilmProviderPolicy.detail(Uri.parse(url)))usePage();mainFrameError=false;pageReady=false;loading=true;if(allowedBrowserPage(Uri.parse(url)))lastRequested=url;
                status.setText("Opening FlixMomo…");if(pointerPage){if(!simplifiedDiscoverExperience()||chrome.getVisibility()!=View.VISIBLE)providerStatus("Opening the registered provider page… Menu and Back remain available.",false);nativeControls.pause();playNotice.setVisibility(View.GONE);networkNotice.setVisibility(View.GONE);}handler.removeCallbacks(slowLoad);handler.postDelayed(slowLoad,20000);
            }
            @Override public void onPageFinished(WebView view,String url){
                loading=false;handler.removeCallbacks(slowLoad);if(mainFrameError){pageReady=false;return;}
                if("/dummy".equals(Uri.parse(url).getPath())){fail("FlixMomo declined this session. No provider protection was changed.");return;}
                if(torWebMode&&browserBar!=null)browserBar.current(url);
                if(phoneConnected)handler.postDelayed(FlixMomoActivity.this::hideTvSoftKeyboard,200L);
                keepSubmittedSearchBrowserOnly();pageReady=allowedBrowserPage(Uri.parse(url));
                if(pageReady&&(ownedFixtureForDebug(url)||
                        torController!=null&&torController.active())){
                    // WebView document completion is NOT evidence of playable video.
                    if(torWebMode)noteInput("TOR_DOCUMENT_FINISHED");
                    if(torController!=null)torController.documentLoaded(url);
                    hideProviderStatus();
                    if(credit!=null&&chrome.getVisibility()==View.VISIBLE)
                        credit.setText(torWebMode?"GharTV · Tor Web · Connected":
                            "GharTV · FlixMomo · Tor connected");
                    if(!torWebMode&&java.util.Objects.equals(url,view.getUrl()))rememberProviderHome(url);
                    status.setText(torWebMode?"Tor website loaded · Menu for address or Back":
                        homeRequested?"Loading FlixMomo suggestions…":detailRequested?"Loading title information…":
                        "Original page loaded · Menu shows GharTV controls");
                    if(!torWebMode&&!mouseActive&&!pointerPage)nativeControls.documentReady();
                    if(pointerPage&&!pageEditor&&(!simplifiedDiscoverExperience()||chrome.getVisibility()!=View.VISIBLE))resumePagePointer(false);
                    if(!torWebMode&&pointerPage&&oneClickPlay)nativeControls.resume();
                }
            }
            @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){
                if(!loading&&allowedBrowserPage(Uri.parse(url))&&!java.util.Objects.equals(lastRequested,url)){
                    phonePlaybackEpoch++;phoneMediaObservable=false;
                    phonePlaybackReading=false;phonePlaybackTicket++;
                    lastRequested=url;if(torController!=null)torController.beginNavigation(url);nativeControls.started();if(!torWebMode)oneClickArrived(url);
                    if(detailRequested&&FilmProviderPolicy.detail(Uri.parse(url)))detailPanel.followNavigation(url);
                    else if(detailRequested)usePage();
                    if(!pointerPage)nativeControls.documentReady();else if(!pageEditor&&(!simplifiedDiscoverExperience()||chrome.getVisibility()!=View.VISIBLE))resumePagePointer(false);
                    if(pointerPage&&oneClickPlay)nativeControls.resume();
                }
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                int code=response.getStatusCode();FilmTorFallback.Failure kind=code==401||code==407?FilmTorFallback.Failure.HTTP_AUTH:code==403?FilmTorFallback.Failure.HTTP_DENIED:code==429?FilmTorFallback.Failure.HTTP_RATE_LIMIT:FilmTorFallback.Failure.OTHER;
                if(request.isForMainFrame())fail("Provider returned HTTP "+code+". Your search is kept. Use Retry or Browse.");
                else{if(startMonitor!=null)startMonitor.requestFailure("Embedded HTTP "+code);filmFailure("film_resource_http","HTTP_"+code);catalogueResourceFailure(request.getUrl(),kind,"HTTP "+code);}}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){certificateFailure(handler::cancel,error);}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){
                providerRequestFailed(request,error.getErrorCode());
            }
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                nativeControls.failure();cursor.leave();exitFullScreen();stage.removeView(view);view.destroy();if(browser==view)browser=null;cursor.target(null);nativeControls.attach(null);
                fail("The provider browser stopped. Retry reopens it; live TV and your query are preserved.");retryButton.requestFocus();return true;
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){
            // Google WebView recommends denying new-window creation. Blocking a
            // popup here also suppresses target=_blank even when the tap has a
            // user gesture; the user can choose Tor Web deliberately instead.
            @Override public boolean onCreateWindow(WebView view,boolean isDialog,
                    boolean isUserGesture,android.os.Message resultMsg){
                return false;
            }
            @Override public boolean onJsAlert(WebView view,String url,String message,
                    android.webkit.JsResult result){
                result.cancel();return true;
            }
            @Override public boolean onJsConfirm(WebView view,String url,String message,
                    android.webkit.JsResult result){
                result.cancel();return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,android.webkit.GeolocationPermissions.Callback callback){callback.invoke(origin,false,false);}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){enterFullScreen(view,callback);}
            @Override public void onHideCustomView(){exitFullScreen();}
        });return true;
    }

    // Shared by the real WebView callback; provider error descriptions are not retained.
    void providerRequestFailed(WebResourceRequest request,int errorCode){
        FilmTorFallback.Failure kind=errorCode==WebViewClient.ERROR_HOST_LOOKUP?FilmTorFallback.Failure.DNS:errorCode==WebViewClient.ERROR_CONNECT?FilmTorFallback.Failure.CONNECT:errorCode==WebViewClient.ERROR_TIMEOUT?FilmTorFallback.Failure.TIMEOUT:errorCode==WebViewClient.ERROR_FAILED_SSL_HANDSHAKE?FilmTorFallback.Failure.TLS:FilmTorFallback.Failure.OTHER;
        if(!request.isForMainFrame()){
            if(startMonitor!=null)startMonitor.requestFailure("Embedded request error "+errorCode);
            filmFailure("film_resource","WEBVIEW_ERROR_"+errorCode);if(catalogueResourceFailure(request.getUrl(),kind,"network error "+errorCode))return;return;
        }
        filmFailure("film_navigation","WEBVIEW_ERROR_"+errorCode);
        if(errorCode==WebViewClient.ERROR_TIMEOUT)noteInput("TOR_PAGE_TIMEOUT");
        if(torController!=null&&torController.retryPageThroughTor(request.getUrl().toString(),kind)){
            mainFrameError=true;pageReady=false;loading=false;handler.removeCallbacks(slowLoad);return;
        }
        // A Tor-only build must never invoke the old direct-first recovery.
        if(torController==null||!torController.active()){
            fail("Tor connection unavailable. No direct page was loaded.");return;
        }
        switch(errorCode){
            case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:fail("TLS handshake failed for the main page. Open Connection to check this TV; security checks remain enabled.");break;
            case WebViewClient.ERROR_HOST_LOOKUP:fail("DNS: Android cannot resolve the provider. No page/video loaded. Your query is kept.");break;
            case WebViewClient.ERROR_CONNECT:fail("Android could not connect. Check the TV network, then Retry; your query is kept.");break;
            case WebViewClient.ERROR_TIMEOUT:fail("Provider request timed out. Your query is kept. Press Retry when ready.");break;
            default:fail("Navigation failed ("+errorCode+"). Your query is kept. Use Retry or TV guide.");
        }
    }

    private void enterMouse(){
        if(startMonitor!=null)startMonitor.pause();
        if(custom!=null){resumeFullscreenPointer();return;}
        inputEpoch++;noteInput("POINTER_ENTER");
        controlTrace.begin(this);
        mouseActive=true;playUntil=0;nativeControls.pause();nativeControls.hideAll();usePage();
        cursor.scrollMode(false);cursor.enable(true);cursor.center();cursor.enter();modeButton.setText("Cursor: on");
        playMessage("Mouse: arrows move · OK clicks · Menu returns to controls");
        aimPointerAtMedia();
    }
    private void aimPointerAtMedia(){
        if(browser==null||custom!=null||!mouseActive)return;
        final WebView page=browser;final String url=page.getUrl();final long token=cursor.placementToken();
        page.postOnAnimation(()->{
            if(custom!=null||!mouseActive||page!=browser)return;
            FilmPointerPosition.read(page,raw->{
                if(custom!=null||!mouseActive||page!=browser||!java.util.Objects.equals(url,page.getUrl()))return;
                try{Object parsed=new org.json.JSONTokener(raw).nextValue();if(!(parsed instanceof String))return;
                    org.json.JSONObject point=new org.json.JSONObject((String)parsed);
                    if("POSITION_ONLY".equals(point.optString("state")))cursor.aimFraction((float)point.optDouble("x"),(float)point.optDouble("y"),token);
                }catch(Exception ignored){}
            });
        });
    }
    private void leaveMouse(boolean controls){inputEpoch++;noteInput("POINTER_EXIT");mouseActive=false;cursor.enable(false);modeButton.setText("Cursor: off");nativeControls.resume();if(controls)nativeControls.menu();}
    private void showHome(boolean force){
        if(simplifiedDiscoverExperience()){showBrowserSearch(force);return;}
        resumeCatalogueMode();if(startMonitor!=null)startMonitor.leave();playUntil=0;if(playNotice!=null)playNotice.setVisibility(View.GONE);
        detailRequested=false;if(detailPanel!=null)detailPanel.setVisibility(View.GONE);homeRequested=true;nativeSearch="";homePanel.mode("");liveGeneration++;liveResults.setVisibility(View.GONE);query.setText("");chrome.setVisibility(View.VISIBLE);
        cursor.enable(false);modeButton.setText("Cursor: off");nativeControls.hideAll();homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();
        if(force)homePanel.discardSuggestions();
        if(force||homePanel.cardCount()==0){homePanel.loading();navigate(providerOrigin()+"/",false);}
        homePanel.focusRefresh();
    }
    private void showBrowserSearch(boolean reload){
        endOneClick();
        browserSearchSubmitted=false;
        if(custom!=null)exitFullScreen();if(startMonitor!=null)startMonitor.leave();handler.removeCallbacks(detailWait);handler.removeCallbacks(playDeadline);playUntil=0;
        pointerPage=true;pageEditor=false;mouseActive=false;homeRequested=false;detailRequested=false;nativeSearch="";
        if(homePanel!=null)homePanel.setVisibility(View.GONE);if(detailPanel!=null)detailPanel.setVisibility(View.GONE);liveGeneration++;if(liveResults!=null)liveResults.setVisibility(View.GONE);
        if(nativeControls!=null){nativeControls.pause();nativeControls.hideAll();}if(cursor!=null)cursor.enable(false);hideProviderStatus();
        chrome.setVisibility(View.VISIBLE);credit.setText(ReviewDisplay.details()+torRouteSuffix()+" · Search, then use arrows and OK");credit.setVisibility(View.VISIBLE);
        if(browser!=null)browser.setVisibility(View.VISIBLE);String root=providerOrigin()+"/";pointerTitleOrigin=root;
        String current=browser==null?null:browser.getUrl();if(reload||current==null||"about:blank".equals(current)||!allowedTop(Uri.parse(current)))navigate(root,false);
        // Search must own input focus from the moment it is displayed,
        // regardless of whether the optional QR dialog has been shown.
        // The paired phone receives SEARCH immediately when it connects.
        query.requestFocus();
        phoneSearchWanted=true;
        notifyPhoneMode();
    }
    private void showLiveResults(String text){
        final int token=++liveGeneration;liveRow.removeAllViews();liveResults.setVisibility(View.VISIBLE);
        liveRow.addView(TvUi.label(this,"Searching cached live guide…",12,TvUi.MUTED,false));
        liveExecutor.execute(()->{
            try{ChannelRepository repo=new ChannelRepository(this);java.util.List<Channel> all=repo.loadAll();java.util.List<Channel> found=UnifiedSearch.channels(all,text);
                handler.post(()->{if(isFinishing()||token!=liveGeneration)return;liveRow.removeAllViews();
                    TextView label=TvUi.label(this,all.isEmpty()?"Live guide not available locally":found.isEmpty()?"Live: no cached channel matches":"Live guide · "+found.size()+" matches",12,TvUi.MUTED,false);label.setPadding(TvUi.dp(this,10),0,TvUi.dp(this,12),0);liveRow.addView(label,new LinearLayout.LayoutParams(-2,-1));
                    org.json.JSONArray scope=new org.json.JSONArray();for(Channel c:found)scope.put(c.number);
                    for(Channel c:found.subList(0,Math.min(found.size(),16))){Button button=TvUi.button(this,c.name,false);button.setOnClickListener(v->{try{startActivity(new Intent(this,PlayerActivity.class).putExtra(PlayerActivity.EXTRA_CHANNEL_JSON,c.toJson().toString()).putExtra(PlayerActivity.EXTRA_SCOPE_NUMBERS,scope.toString()).putExtra(PlayerActivity.EXTRA_SCOPE_LABEL,"All · search"));}catch(Exception e){status.setText("Could not open that cached live channel.");}});liveRow.addView(button,new LinearLayout.LayoutParams(TvUi.dp(this,145),-1));}
                });
            }catch(Exception e){handler.post(()->{if(token==liveGeneration){liveRow.removeAllViews();liveRow.addView(TvUi.label(this,"Live guide could not be read; film search remains available",12,TvUi.MUTED,false));}});}
        });
    }

    private final Runnable playDeadline=()->{
        if(playUntil==0)return;
        endOneClick();
        controlTrace.emit(this,"media",attemptObservable?"UNCONFIRMED":"IFRAME_UNOBSERVABLE");
        playUntil=0;playMessage(attemptObservable?"Playback did not reach an observed start. Use Mouse or another player.":"This player does not expose playback status to GharTV. Use Mouse if Play did not start it.");
    };
    private void playMessage(String text){playNotice.setText(text);playNotice.setVisibility(custom==null&&(!homeRequested||loading)?View.VISIBLE:View.GONE);}
    private void armPlayback(){notifyPhoneMode();fullscreenProofPrompted=false;if(startMonitor!=null)startMonitor.begin();inputEpoch++;noteInput("PLAY_REQUEST");controlTrace.begin(this);attemptObservable=false;handler.removeCallbacks(playDeadline);if(startMonitor==null)handler.postDelayed(playDeadline,150000);playUntil=android.os.SystemClock.elapsedRealtime()+150000;playAfter=android.os.SystemClock.elapsedRealtime()+700;mediaAttempted=false;lastMediaTime=-1;playMessage("Opening FlixMomo playback…");notifyPhoneMode();}
    private void requestWatch(){String expected=detailRequested?detailPanel.currentWatchLabel():"";filmEvent("film_watch_request");armPlayback();usePage();nativeControls.pageWatch(expected);}
    private void followPlayback(org.json.JSONObject data){
        if(playUntil==0||homeRequested||detailRequested)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if((startMonitor==null||startMonitor.healthy())&&data.optBoolean("mediaPlaying")&&data.optDouble("mediaTime")>lastMediaTime&&lastMediaTime>=0){playNotice.setVisibility(View.GONE);notifyPhoneMode();filmEvent("film_playback_ready");controlTrace.emit(this,"media","CLOCK_ADVANCED");playUntil=0;endOneClick();return;}
        if(data.optBoolean("mediaObservable"))attemptObservable=true;
        if(data.optBoolean("mediaObservable"))lastMediaTime=data.optDouble("mediaTime");
        if(now>playUntil){if(startMonitor==null)playDeadline.run();else{playUntil=0;endOneClick();}return;}
        org.json.JSONArray offered=data.optJSONArray("players");
        boolean playbackPage=!FilmProviderPolicy.detail(Uri.parse(data.optString("url")))||(offered!=null&&offered.length()>0)||data.optBoolean("mediaObservable");
        if(!loading&&!mediaAttempted&&now>=playAfter&&playbackPage&&data.optInt("mediaCandidates")==1){
            mediaAttempted=true;playMessage("Starting the provider’s visible player…");nativeControls.pageAction("media");
        }
    }
    private void hideProviderStatus(){
        if(providerStatusPanel==null)return;
        providerStatusPanel.setVisibility(View.GONE);
        if(providerRecoveryActions!=null){providerStatusPanel.removeView(providerRecoveryActions);providerRecoveryActions.setVisibility(View.GONE);}
    }
    private void providerStatus(String message,boolean recovery){
        status.setText(message);
        if(providerStatusPanel==null||!pointerPage||custom!=null)return;
        // Recovery controls enter the view tree only for an actual failure.
        // Successful browsing must not contain duplicate navigation controls.
        if(recovery){if(providerRecoveryActions.getParent()==null)providerStatusPanel.addView(providerRecoveryActions);}
        else providerStatusPanel.removeView(providerRecoveryActions);
        providerRecoveryActions.setVisibility(recovery?View.VISIBLE:View.GONE);
        providerStatusPanel.setVisibility(View.VISIBLE);
        if(recovery){pageEditor=false;mouseActive=false;cursor.enable(false);retryButton.requestFocus();}
    }
    private void fail(String message){
        if(playUntil>0){playUntil=0;playMessage(message);}if(nativeControls!=null)nativeControls.failure();keepSubmittedSearchBrowserOnly();mainFrameError=true;pageReady=false;loading=false;handler.removeCallbacks(slowLoad);status.setText(message);
        if(homeRequested&&homePanel!=null)homePanel.unavailable(message);
        else if(detailRequested&&detailPanel!=null)detailPanel.unavailable(message);
        else if(pointerPage){networkNotice.setVisibility(View.GONE);providerStatus(message,true);}
        else if(networkNotice!=null){networkNotice.setText(message);networkNotice.setVisibility(View.VISIBLE);}
    }
    static boolean providerHost(String host){return FilmProviderPolicy.host(host);}
    static boolean allowedTop(Uri uri){return FilmProviderPolicy.allowed(uri);}
    private static boolean privateHost(String host){String h=host.toLowerCase(java.util.Locale.ROOT);return h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||h.contains(":")||h.matches("(?i)(127\\..*|10\\..*|192\\.168\\..*|169\\.254\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|0\\..*)");}
    private String providerOrigin(){
        String root=FilmProviderPolicy.pageHome(browser==null?lastRequested:browser.getUrl());
        if(root==null)root=FilmProviderPolicy.pageHome(lastRequested);
        if(root==null)root=initialProviderHome();
        return root.substring(0,root.length()-1);
    }
    private void openDetail(String url){
        if(startMonitor!=null)startMonitor.leave();
        detailBegan=android.os.SystemClock.elapsedRealtime();detailUsableMs=detailPreviewMs=-1;
        handler.removeCallbacks(detailWait);handler.postDelayed(detailWait,20000);
        if(mouseActive)leaveMouse(false);
        filmEvent("film_detail_open");
        if(!FilmProviderPolicy.detail(Uri.parse(url))){navigate(url,true);return;}
        org.json.JSONObject card=homePanel.card(url);homeRequested=false;homePanel.setVisibility(View.GONE);liveResults.setVisibility(View.GONE);
        detailRequested=true;nativeControls.hideAll();cursor.enable(false);chrome.setVisibility(View.GONE);detailPanel.begin(url,card);org.json.JSONObject cached=visitCache.get(url);detailPanel.preview(cached);if(cached!=null)detailPreviewMs=android.os.SystemClock.elapsedRealtime()-detailBegan;navigate(url,false);
    }
    private void returnToCards(){
        if(simplifiedDiscoverExperience()){showBrowserSearch(false);return;}
        resumeCatalogueMode();if(startMonitor!=null)startMonitor.leave();handler.removeCallbacks(detailWait);detailRequested=false;detailPanel.setVisibility(View.GONE);homeRequested=true;homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();chrome.setVisibility(View.VISIBLE);homePanel.focusRefresh();
        // Already-observed cards remain usable on Back, without another provider-page load.
        // Selecting a card or explicitly refreshing still navigates to that current source.
        if(homePanel.cardCount()==0){homePanel.loading();navigate(providerOrigin()+(!nativeSearch.isEmpty()?"/search?q="+Uri.encode(nativeSearch):"/"),false);}
    }
    private void search(){torWebMode=false;String text=UnifiedSearch.clean(query.getText().toString());if(!UnifiedSearch.valid(text)){status.setText("Enter 2–120 characters.");return;}
        phoneSearchWanted=false;
        filmEvent("film_search");
        if(simplifiedDiscoverExperience()){browserSearchSubmitted=true;nativeSearch=text;openProviderTitle(providerOrigin()+"/search?q="+Uri.encode(text));handler.post(this::keepSubmittedSearchBrowserOnly);return;}
        resumeCatalogueMode();if(startMonitor!=null)startMonitor.leave();playUntil=0;playNotice.setVisibility(View.GONE);detailRequested=false;detailPanel.setVisibility(View.GONE);homeRequested=true;nativeSearch=text;homePanel.mode(text);homePanel.discardSuggestions();homePanel.setVisibility(View.VISIBLE);homePanel.bringToFront();nativeControls.hideAll();cursor.enable(false);showLiveResults(text);homePanel.loading();navigate(providerOrigin()+"/search?q="+Uri.encode(text),false);homePanel.focusRefresh();
    }
    private boolean allowedBrowserPage(Uri uri){
        return allowedTop(uri)||(torWebMode&&uri!=null&&TorWebUrlPolicy.allowed(uri.toString()));
    }
    private void navigate(String url,boolean page){
        if(!allowedBrowserPage(Uri.parse(url))){
            fail("This address is not approved for the Tor-only browser. Use an ordinary public HTTPS URL.");
            return;
        }
        hideKeyboard();lastRequested=url;
        // Fail closed: never call loadUrl() before the app-local Tor proxy
        // has acknowledged readiness. No direct/clearnet route is offered.
        boolean deferred=!ownedFixtureForDebug(url)&&
            (torController==null||torController.beginPreferredNavigation(url));
        connection.begin(url);connectionWarning="";networkNotice.setVisibility(View.GONE);
        if(pointerPage&&chrome.getVisibility()!=View.VISIBLE)providerStatus("Opening through Tor… Menu and Back remain available.",false);
        else hideProviderStatus();
        if(browser==null){fail("Tor browser stopped. Reopen GharTV to retry.");return;}
        if(deferred){showTorStatus("Connecting through Tor before loading the page…");return;}
        loadProviderPage(url);if(page)usePage();
    }
    // Disabled for shipping builds; only a debug-owned deterministic HTML test
    // fixture may override this. Third-party URLs always require Tor.
    protected boolean ownedFixtureForDebug(String url){return false;}
    /** The optional QR belongs to real viewers, not isolated UI fixtures. */
    protected boolean autoOfferPhoneOnEntry(){return true;}
    protected boolean simplifiedDiscoverExperience(){return true;}
    protected boolean torDefaultForFilms(){return true;}
    protected FilmDetailView createDetailPanel(FilmDetailView.Host host){return new FilmDetailView(this,host);}
    protected boolean torFallbackEnabledForBuild(){return true;}
    protected FilmHomeView createHomePanel(FilmHomeView.Host host){return torPosterLoader==null?new FilmHomeView(this,host):new FilmHomeView(this,host,torPosterLoader::load);}
    protected void loadProviderPage(String url){browser.loadUrl(url);}
    private FilmTorController createTorController(){return new FilmTorController(this,new FilmTorController.Listener(){
        public boolean foreground(){return !isFinishing()&&!isDestroyed();}
        public boolean mediaPlaying(){return custom!=null||(startMonitor!=null&&startMonitor.healthy());}
        public void requestConsent(long ticket,FilmTorController.ConsentResult callback){showTorConsent(callback);}
        public void status(String message){
            // Store event categories only. Never persist Tor relay details or URLs.
            if(message.startsWith("Connecting privately through Tor"))noteInput("TOR_START");
            else if(message.startsWith("Tor connected · opening page"))noteInput("TOR_PROXY_READY");
            showTorStatus(message);
        }
        public void reload(String url){reloadForTorRoute(url);}
        public void routeChanged(boolean tor){credit.setText(FilmProviderPolicy.CREDIT+torRouteSuffix());}
        public void failure(String message){noteInput("TOR_FAILED");showTorFailure(message);}
    });}
    private String torRouteSuffix(){return torController==null?" · Tor unavailable":torController.active()?" · Tor":" · Tor connecting";}
    private void showTorConsent(FilmTorController.ConsentResult callback){
        final boolean[] answered={false};
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Use Tor for FlixMomo?")
            .setMessage("The direct FlixMomo catalogue connection failed. GharTV can start an app-local Tor route and retry only the registered FlixMomo, TMDB catalogue and poster hosts. This film proxy excludes JioTV, IPTV, app updates and diagnostics. JioTV/IPTV recovery is separate. Tor can be slower and is not a guarantee of Tor Browser anonymity. Certificate and provider access checks stay enabled.")
            .setPositiveButton("Enable and retry",(d,w)->{answered[0]=true;callback.answer(true);})
            .setNegativeButton("Stay direct",(d,w)->{answered[0]=true;callback.answer(false);}).create();
        dialog.setOnCancelListener(d->{if(!answered[0])callback.answer(false);});dialog.show();
    }
    private void showTorStatus(String message){handler.removeCallbacks(slowLoad);keepSubmittedSearchBrowserOnly();if(simplifiedDiscoverExperience()&&chrome.getVisibility()==View.VISIBLE){credit.setText(ReviewDisplay.details()+torRouteSuffix()+" · "+message);credit.setVisibility(View.VISIBLE);}else if(homeRequested&&homePanel!=null)homePanel.routeStatus(message);else if(pointerPage)providerStatus(message,false);else{networkNotice.setText(message);networkNotice.setVisibility(View.VISIBLE);}}
    private void showTorFailure(String message){keepSubmittedSearchBrowserOnly();if(homeRequested&&homePanel!=null)homePanel.unavailable(message);else if(pointerPage)providerStatus(message,true);else{networkNotice.setText(message);networkNotice.setVisibility(View.VISIBLE);}}
    private void reloadForTorRoute(String url){mainFrameError=false;loading=true;keepSubmittedSearchBrowserOnly();if(homeRequested&&homePanel!=null)homePanel.loading();navigate(url,false);keepSubmittedSearchBrowserOnly();handler.post(this::keepSubmittedSearchBrowserOnly);}
    private void showTorRoute(){
        if(torController==null)return;
        String state=torController.active()?"Tor connected":"Tor connecting";
        new TvModal(this,"NETWORK & PRIVACY","Tor route",
            state+" · Film browsing is Tor-only; JioTV stays direct. "+
            "IPTV uses the Tor tunnel. You can choose a preferred exit country "+
            "in the central settings; this is a preference, not a guarantee.")
            .action("Countries & settings",false,d->{d.dismiss();TorNetworkSettings.show(this);})
            .action("Close",true,android.app.Dialog::dismiss).show();
    }
    private void showTorBrowserAddress(){
        torWebMode=true;endOneClick();
        if(browserBar==null)return;
        String current=browser==null?null:browser.getUrl();
        // The explicit Browser action is a request to ENTER an address. Do not
        // silently auto-hide the toolbar before the viewer can type (the
        // historical false-positive "Tor did not open" experience). Focusing
        // the address also puts the paired phone into live EDITOR mode.
        browserBar.showBar(TorWebUrlPolicy.allowed(current)?current:"https://",true);
        noteInput("TOR_BROWSER_OPEN");
        notifyPhoneMode();
    }
    // Verified HTML5 playback enters immersive viewing automatically.
    // Unobservable cross-origin iframe players retain their own controls.
    private void showTorFind(){
        if(!torWebMode||browser==null)return;
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setHint("Find on this page");input.setTextColor(TvUi.TEXT);
        input.setTextSize(17);
        new TvModal(this,"TOR WEB","Find in page","Search text within the current website.")
            .add(input)
            .action("Cancel",false,android.app.Dialog::dismiss)
            .action("Find",true,d->{
                String text=input.getText().toString().trim();
                if(!text.isEmpty()&&text.length()<120){
                    browser.findAllAsync(text);browser.findNext(true);
                }
                d.dismiss();if(browserBar!=null)browserBar.hideBar();enterPagePointer();
            }).show();
    }
    private void showTorBookmarks(){
        if(!torWebMode)return;
        android.content.SharedPreferences prefs=getSharedPreferences("ghartv.tor-web-bookmarks",MODE_PRIVATE);
        String saved=prefs.getString("urls","");
        java.util.ArrayList<String> entries=new java.util.ArrayList<>();
        for(String candidate:saved.split("\n"))
            if(TorWebUrlPolicy.allowed(candidate)&&entries.size()<12)entries.add(candidate);
        String current=browser==null?"":browser.getUrl();
        TvModal modal=new TvModal(this,"TOR WEB","Your bookmarks",
            "Saved privately on this TV. Up to 12 HTTPS addresses. New tab stores the current page.");
        if(TorWebUrlPolicy.allowed(current)&&!entries.contains(current)){
            Button save=TvUi.button(this,"☆  Save this page",true);
            modal.add(save);save.setOnClickListener(v->{
                entries.add(0,current);
                while(entries.size()>12)entries.remove(entries.size()-1);
                prefs.edit().putString("urls",String.join("\n",entries)).apply();
                android.widget.Toast.makeText(this,"Bookmark saved",android.widget.Toast.LENGTH_SHORT).show();
            });
        }
        for(String url:entries){
            Button item=TvUi.button(this,url,false);
            modal.add(item);item.setOnClickListener(v->openTorWebsiteTarget(url));
        }
        modal.action("New tab",false,d->{d.dismiss();newTorBrowserTab();})
             .action("Close",true,android.app.Dialog::dismiss).show();
    }
    private void newTorBrowserTab(){
        if(torTabs.size()>=3){
            android.widget.Toast.makeText(this,"Up to four browsing tabs",android.widget.Toast.LENGTH_SHORT).show();return;
        }
        String current=browser==null?"":browser.getUrl();
        if(TorWebUrlPolicy.allowed(current))torTabs.add(current);
        torWebMode=true;browserBar.showBar("https://",true);
    }
    private void switchPreviousTab(){
        if(torTabs.isEmpty())return;
        String previous=torTabs.remove(torTabs.size()-1);
        openTorWebsiteTarget(previous);
    }
    private void openTorWebsiteTarget(String url){
        if(url==null||!TorWebUrlPolicy.allowed(url))return;
        torWebMode=true;endOneClick();playUntil=0;
        if(startMonitor!=null)startMonitor.leave();
        openProviderTitle(url);
        if(browserBar!=null){browserBar.showBar(url,false);browserBar.autoHide();}
    }
    private void openProviderTitle(String url){
        if(simplifiedDiscoverExperience())browserSearchSubmitted=true;
        if(url==null||!allowedBrowserPage(Uri.parse(url))){status.setText("Address blocked. Use a public HTTPS website in Tor web.");return;}
        if(custom!=null)exitFullScreen();
        if(startMonitor!=null)startMonitor.leave();handler.removeCallbacks(detailWait);handler.removeCallbacks(playDeadline);playUntil=0;
        pointerPage=true;pointerTitleOrigin=url;detailRequested=false;homeRequested=false;pageEditor=false;
        filmEvent("film_detail_open");detailPanel.setVisibility(View.GONE);homePanel.setVisibility(View.GONE);liveResults.setVisibility(View.GONE);
        nativeControls.pause();nativeControls.hideAll();chrome.setVisibility(View.GONE);credit.setVisibility(View.GONE);playNotice.setVisibility(View.GONE);networkNotice.setVisibility(View.GONE);
        credit.setText(ReviewDisplay.details()+" · Arrows move · OK clicks · Menu");credit.setVisibility(View.VISIBLE);
        status.setText("Opening original page. Arrows move pointer; OK clicks; Menu opens navigation.");
        navigate(url,false);resumePagePointer(true);
    }
    private void resumePagePointer(boolean center){
        if(!pointerPage||browser==null||custom!=null||homeRequested||detailRequested)return;
        if(simplifiedDiscoverExperience()&&chrome.getVisibility()==View.VISIBLE)return;
        if(providerStatusPanel.isShown()&&providerRecoveryActions.isShown()){retryButton.requestFocus();return;}
        inputEpoch++;pageEditor=false;mouseActive=true;if(!oneClickPlay)nativeControls.pause();nativeControls.hideAll();
        if(!oneClickPlay){if(startMonitor!=null)startMonitor.leave();playUntil=0;playNotice.setVisibility(View.GONE);}
        cursor.target(browser);cursor.scrollMode(false);cursor.enable(true);browser.requestFocus();
        notifyPhoneMode();
        if(center)cursor.center();cursor.enter();modeButton.setText("Cursor: on");
        // When handing the page from native cards to mouse/pointer navigation,
        // the old focused poster link can still hold DOM keyboard focus.
        // Blur only a stale link/control, not an active text field or player;
        // do not click it, scroll the page or alter playback.
        if(!oneClickPlay&&!phoneMediaObservable){
            final WebView current=browser;final long epoch=inputEpoch;
            handler.postDelayed(()->{
                if(current==browser&&epoch==inputEpoch&&pointerPage&&mouseActive&&
                    !pageEditor&&custom==null&&!phoneMediaObservable)
                    current.evaluateJavascript(
                        "(()=>{const e=document.activeElement;if(e&&e.matches('a,button,[role=link],[role=button]')){e.blur();return 'BLURRED_STALE_LINK';}return 'UNCHANGED';})()",
                        null);
            },160L);
        }
    }
    /** Explicit owner request for the page pointer: hide the search toolbar first so the guard below admits it. */
    private void enterPagePointer(){
        if(simplifiedDiscoverExperience()&&chrome!=null&&chrome.getVisibility()==View.VISIBLE){
            chrome.setVisibility(View.GONE);hideKeyboard();
        }
        resumePagePointer(true);
    }
    private void pointerNavigation(){
        endOneClick();
        if(!pointerPage){showOptions();return;}
        if(custom!=null){showFullscreenMenu();return;}
        inputEpoch++;mouseActive=false;pageEditor=false;cursor.enable(false);nativeControls.pause();nativeControls.hideAll();
        String[] choices=simplifiedDiscoverExperience()?new String[]{"Return to page / pointer","Search films or series","Back to TV guide","Reload current page","Connection details","FlixMomo route","Phone mouse + keyboard","Open website via Tor","Recent technical errors"}:new String[]{"Return to page / pointer","Search GharTV · TV + films","Go back one page","Reload current page","Connection details","FlixMomo route","Phone mouse + keyboard","Open website via Tor","Recent interaction log"};
        optionsDialog=new android.app.AlertDialog.Builder(this).setTitle(ReviewDisplay.details())
            .setItems(choices,(dialog,which)->{
                if(which==0){enterPagePointer();}
                else if(which==1){toolbar();query.requestFocus();if(!simplifiedDiscoverExperience())UnifiedSearch.dialog(this);}
                else if(which==2){if(simplifiedDiscoverExperience())finish();else pointerBack();}
                else if(which==3){if(browser!=null){navigate(lastRequested,false);resumePagePointer(false);}}
                else if(which==4){showFilmConnection();}
                else if(which==5){showTorRoute();}
                else if(which==6){showPhoneRemote();}
                else if(which==7){showTorBrowserAddress();}
                else showInteractionLog();
            }).setNegativeButton("Close",(d,w)->resumePagePointer(false)).create();
        optionsDialog.setOnCancelListener(d->resumePagePointer(false));
        optionsDialog.setOnDismissListener(d->{if(pointerPage&&custom==null&&!homeRequested&&!detailRequested)resumePagePointer(false);});optionsDialog.show();
    }
    private void pointerBack(){
        endOneClick();
        if(browser==null){returnToCards();return;}
        if(pageEditor){hideKeyboard();resumePagePointer(false);return;}
        String current=browser.getUrl();
        if(java.util.Objects.equals(current,pointerTitleOrigin)||!browser.canGoBack()){
            if(torWebMode)torWebMode=false;
            if(simplifiedDiscoverExperience())showBrowserSearch(false);else returnToCards();return;
        }
        browser.goBack();resumePagePointer(false);
    }
    private void checkPageEditor(){
        final WebView page=browser;final long epoch=inputEpoch;
        if(!pointerPage||custom!=null||page==null)return;
        FilmPointerPosition.editor(page,value->{
            if(epoch!=inputEpoch||page!=browser||!pointerPage||custom!=null)return;
            if("true".equals(value)){
                pageEditor=true;mouseActive=false;cursor.leave();
                // The paired phone supplies the keyboard, so suppress the TV's
                // on-screen IME without clearing the actual website field.
                if(phoneConnected){
                    android.view.inputmethod.InputMethodManager ime=
                        (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    if(ime!=null)ime.hideSoftInputFromWindow(page.getWindowToken(),0);
                }
                notifyPhoneMode();
            }else if(pageEditor){pageEditor=false;notifyPhoneMode();}
        });
    }
    private void voiceSearch(){filmEvent("film_voice_search");hideKeyboard();UnifiedSearch.chooseVoice(this);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);String text=UnifiedSearch.voiceText(request,result,data);if(!text.isEmpty()){query.setText(text);search();}else if(request==UnifiedSearch.VOICE&&result==RESULT_OK)UnifiedSearch.unavailable(this,"No usable speech result. Type your search or choose another language.");}
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(intent.getBooleanExtra(EXTRA_PHONE_SEARCH_ON_ENTRY,false)){
            intent.removeExtra(EXTRA_PHONE_SEARCH_ON_ENTRY);
            handler.post(this::focusPhoneFilmSearch);return;
        }
        String text=UnifiedSearch.clean(intent.getStringExtra(UnifiedSearch.QUERY));
        if(UnifiedSearch.valid(text)){query.setText(text);search();}
    }
    private void hideKeyboard(){InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(query.getWindowToken(),0);query.clearFocus();}
    private void usePage(){if(pointerPage){homeRequested=false;detailRequested=false;homePanel.setVisibility(View.GONE);detailPanel.setVisibility(View.GONE);liveResults.setVisibility(View.GONE);resumePagePointer(false);return;}inputEpoch++;noteInput("USE_PAGE");credit.setVisibility(View.GONE);networkNotice.setVisibility(View.GONE);playNotice.setVisibility(View.GONE);detailRequested=false;if(detailPanel!=null)detailPanel.setVisibility(View.GONE);homeRequested=false;if(homePanel!=null)homePanel.setVisibility(View.GONE);if(nativeControls!=null){nativeControls.hideAll();if(!mouseActive)nativeControls.resume();}hideKeyboard();chrome.setVisibility(View.GONE);if(browser!=null){browser.requestFocus();cursor.enter();nativeControls.enterPosters();}}
    private void toolbar(){
        endOneClick();browserSearchSubmitted=false;phoneSearchWanted=true;
        if(custom!=null){exitFullScreen();return;}
        inputEpoch++;noteInput("TOOLBAR_OPEN");
        if(mouseActive)leaveMouse(false);nativeControls.pause();nativeControls.hideAll();cursor.enable(false);
        chrome.setVisibility(View.VISIBLE);credit.setText(ReviewDisplay.details()+torRouteSuffix()+" · Search, then use arrows and OK");credit.setVisibility(View.VISIBLE);playNotice.setVisibility(View.GONE);
        if(simplifiedDiscoverExperience()){
            query.requestFocus();
            if(phoneConnected)handler.postDelayed(()->{
                android.view.inputmethod.InputMethodManager im=
                    (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(im!=null)im.hideSoftInputFromWindow(query.getWindowToken(),0);
            },130L);
        }else toolbarAnchor.requestFocus();
        if(!pointerPage)nativeControls.resume();
    }
    private void showOptions(){
        if(pointerPage){pointerNavigation();return;}
        if(custom!=null){showFullscreenMenu();return;}
        if(mouseActive)leaveMouse(false);nativeControls.hideAll();cursor.enable(false);chrome.setVisibility(View.VISIBLE);
        String[] items={"Return to video / website","Connection details","FlixMomo route","Diagnostics","Privacy & attribution","Phone mouse + keyboard","Open website via Tor","Recent interaction log"};
        optionsDialog=new android.app.AlertDialog.Builder(this).setTitle(ReviewDisplay.details())
            .setMessage(null).setItems(items,(d,n)->{
                if(n==0)usePage();else if(n==1)showFilmConnection();else if(n==2)showTorRoute();else if(n==3)DiagnosticsDialog.show(this);else if(n==5)showPhoneRemote();else if(n==6)showTorBrowserAddress();
                else if(n==7)showInteractionLog();else ReviewNotice.show(this,()->{visitCache.clear();if(browser!=null){browser.stopLoading();browser.loadUrl("about:blank");browser.clearCache(true);browser.clearHistory();browser.clearFormData();}nativeControls.failure();com.bumptech.glide.Glide.get(this).clearMemory();homePanel.discardSuggestions();homeRequested=true;detailRequested=false;detailPanel.setVisibility(View.GONE);homePanel.setVisibility(View.VISIBLE);chrome.setVisibility(View.VISIBLE);homePanel.unavailable("Local film data cleared. Choose Refresh to load again.");});
            }).setNegativeButton("Close",null).create();
        optionsDialog.setOnDismissListener(d->{if(custom==null&&chrome.getVisibility()==View.VISIBLE)toolbarAnchor.requestFocus();});optionsDialog.show();
    }
    private void showPhoneRemote(){
        if(HouseholdPhoneSession.connected()){
            phoneConnected=true;updatePhoneBadge();
            new TvModal(this,"GHAR TV · PAIRED","Phone remote connected",
                "Your phone stays paired between the guide and FlixMomo. "+
                "Use its trackpad, keyboard, mic or video controls.")
                .action("Disconnect",false,d->{
                    HouseholdPhoneSession.disconnect();phoneConnected=false;updatePhoneBadge();d.dismiss();
                })
                .action("Continue",true,android.app.Dialog::dismiss).show();
            return;
        }
        HouseholdPhoneSession.open(this,filmPhoneSink,link->{
            if(isFinishing()||isDestroyed())return;
            if(link==null){showPhoneBridgeHelp();return;}
            phoneLinkUrl=link.url;
            if(!HouseholdPhoneSession.connected())showPhoneQr(link.url,link.emulator);
            notifyPhoneMode();
        });
    }
    private void showPhoneQr(String link,boolean viaMac){
        if(link==null||link.length()>250||phoneConnected||isFinishing())return;
        hideKeyboard();
        if(pairingDialog!=null&&pairingDialog.isShowing())pairingDialog.dismiss();
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        android.widget.ImageView qr=new android.widget.ImageView(this);
        try{qr.setImageBitmap(GharTvQr.bitmap(link,TvUi.dp(this,250)));}
        catch(Exception problem){
            android.widget.Toast.makeText(this,"Could not render pairing QR",android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        qr.setContentDescription("Scan QR to pair this GharTV remote");
        TextView hint=TvUi.label(this,
            "Open Camera · Scan QR · Move your finger · Tap to select",
            15,TvUi.TEXT,false);
        hint.setGravity(android.view.Gravity.CENTER);
        content.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(TvUi.dp(this,266),TvUi.dp(this,266));
        qp.topMargin=TvUi.dp(this,5);
        content.addView(qr,qp);
        HouseholdPhoneSession.markOffered();
        pairingDialog=new TvModal(this,"GHARTV · PHONE","Scan to control your TV",
            "Phone optional · Same home Wi-Fi · Press OK to keep using your TV remote.")
            .add(content)
            .action("Use TV remote",true,android.app.Dialog::dismiss)
            .show();
        phonePairEvent("qr_shown");
    }
    private void showPhoneBridgeHelp(){
        new TvModal(this,"LOCAL PAIRING","Start the Mac phone bridge",
            "This is the Android emulator, which is isolated from your phone's Wi-Fi. "+
            "Double-click GharTV_Phone_Bridge.command in Downloads on your Mac, "+
            "then press Phone again. Real Android TVs show their own QR automatically.")
            .action("Close",true,android.app.Dialog::dismiss)
            .show();
    }
    private boolean phoneCursorReady(){
        if(cursor==null||browser==null)return false;
        if(custom!=null){
            if(fullMenu!=null&&fullMenu.getVisibility()==View.VISIBLE)return false;
            if(!cursor.enabled())cursor.enable(true);
            cursor.enter();return true;
        }
        if(!pointerPage)return false;
        if(verifiedImmersive){
            if(!cursor.enabled())cursor.enable(true);
            cursor.target(browser);cursor.enter();return true;
        }
        if(!mouseActive)enterPagePointer();
        return cursor.enabled();
    }
    /** Live phone text replaces only the currently selected safe field. */
    private void phoneLiveText(String raw){
        if(isFinishing()||isDestroyed()||raw==null||raw.length()>256)return;
        if(custom!=null||verifiedImmersive)return;
        if(chrome!=null&&chrome.isShown()&&query!=null&&query.hasFocus()){
            if(!raw.contentEquals(query.getText())){
                query.setText(raw);
                query.setSelection(query.length());
            }
            hideTvSoftKeyboard();
            return;
        }
        if(torWebMode&&browserBar!=null&&browserBar.showing()&&
           browserBar.address().hasFocus()){
            android.widget.EditText addr=browserBar.address();
            if(!raw.contentEquals(addr.getText())){
                addr.setText(raw);addr.setSelection(addr.length());
            }
            hideTvSoftKeyboard();return;
        }
        if(browser==null||!pointerPage||!pageEditor||homeRequested||
           detailRequested)return;
        // Browser JS runs only in the current top document. Never bridge
        // into third-party iframes, verification or sensitive fields.
        final String js="(function(){try{const el=document.activeElement;"+
          "if(!el||el===document.body||el.tagName==='IFRAME')return 'NO_FIELD';"+
          "const t=(el.type||'').toLowerCase();"+
          "const n=((el.name||'')+' '+(el.id||'')+' '+(el.autocomplete||'')).toLowerCase();"+
          "if(t==='password'||t==='tel'||t==='number'||"+
          "/(otp|pass|pin|card|cvv|secret|one-time|verification|payment)/i.test(n))"+
          "return 'BLOCKED';"+
          "const value="+org.json.JSONObject.quote(raw)+";"+
          "if((el.tagName==='INPUT'&&/^(text|search|url|email)$/.test(t||'text'))||"+
          "el.tagName==='TEXTAREA'){"+
          "if(el.disabled||el.readOnly)return 'NO_FIELD';"+
          "el.value=value;el.setSelectionRange(value.length,value.length);"+
          "el.dispatchEvent(new Event('input',{bubbles:true}));return 'SYNCED';}"+
          "if(el.isContentEditable){el.textContent=value;"+
          "el.dispatchEvent(new Event('input',{bubbles:true}));return 'SYNCED';}"+
          "return 'NO_FIELD';}catch(e){return 'NO_FIELD';}})()";
        browser.evaluateJavascript(js,result->{
            if(isFinishing()||isDestroyed())return;
            if(result!=null&&result.contains("BLOCKED"))
                HouseholdPhoneSession.feedback("FIELD_BLOCKED");
        });
    }
    /** Select only a currently observed, numbered source. */
    private void phoneChooseSource(int number){
        if(number<1||number>12||nativeControls==null)return;
        phonePlaybackEpoch++;phoneMediaObservable=false;
        if(custom!=null){phoneSwitchingSource=true;exitFullScreen();phoneSwitchingSource=false;}
        if(verifiedImmersive)leaveVerifiedFullscreen();
        if(!pointerPage||homeRequested||detailRequested)return;
        // Do not change to native TV navigation or abandon the active video.
        // Phone source selection is explicitly supported while the TV
        // pointer has paused its native menu controller.
        if(startMonitor!=null)startMonitor.pause();
        nativeControls.choosePhoneSource(number);
    }
    /** Backspace or clear only the active non-sensitive text field.
     * Never inject global keys or inspect cross-origin iframe contents. */
    private void phoneEdit(String command){
        if(isFinishing()||isDestroyed()||
            !("BACKSPACE".equals(command)||"CLEAR".equals(command)))return;
        final boolean clear="CLEAR".equals(command);
        android.widget.EditText active=null;
        if(torWebMode&&browserBar!=null&&browserBar.showing()&&
           browserBar.address().hasFocus())active=browserBar.address();
        else if(chrome!=null&&chrome.isShown()&&query!=null&&query.hasFocus())
            active=query;
        if(active!=null){
            PhoneTextEdit.Result edit=PhoneTextEdit.edit(active.getText().toString(),
                active.getSelectionStart(),active.getSelectionEnd(),clear);
            active.setText(edit.value);
            active.setSelection(edit.caret);
            hideTvSoftKeyboard();
            HouseholdPhoneSession.feedback("EDIT_APPLIED");return;
        }
        if(browser==null||!pointerPage||custom!=null){
            HouseholdPhoneSession.feedback("EDIT_FIELD_REQUIRED");return;
        }
        // Strict top-document-only editing. Never reach into embeds or
        // private authentication, phone numbers, PINs or payment fields.
        final String js="(function(){try{"+
          "const el=document.activeElement;"+
          "if(!el||el===document.body||el.tagName==='IFRAME')return 'NO_FIELD';"+
          "const t=(el.type||'').toLowerCase();"+
          "const n=((el.name||'')+' '+(el.id||'')+' '+(el.autocomplete||'')).toLowerCase();"+
          "if(t==='password'||t==='tel'||t==='number'||"+
          "/(otp|pass|pin|card|cvv|secret|one-time|verification|payment)/i.test(n))"+
          "return 'BLOCKED';"+
          "const clear="+clear+";"+
          "if(el.tagName==='INPUT'||el.tagName==='TEXTAREA'){"+
          "if(el.readOnly||el.disabled||"+
          "(el.tagName==='INPUT'&&!/^(text|search|url|email)$/.test(t||'text')))return 'NO_FIELD';"+
          "let a=el.selectionStart,b=el.selectionEnd;"+
          "if(a==null||b==null)return 'NO_FIELD';"+
          "if(clear){a=0;b=el.value.length;}"+
          "else if(a===b&&a>0){"+
          "const prefix=el.value.slice(0,a);"+
          "a-=Array.from(prefix).slice(-1)[0].length;}"+
          "el.setRangeText('',a,b,'start');"+
          "el.dispatchEvent(new Event('input',{bubbles:true}));"+
          "return 'EDIT_APPLIED';}"+
          "if(el.isContentEditable){"+
          "if(clear){el.textContent='';el.dispatchEvent(new Event('input',{bubbles:true}));return 'EDIT_APPLIED';}"+
          "const sel=window.getSelection();if(!sel||sel.rangeCount!==1)return 'NO_FIELD';"+
          "const range=sel.getRangeAt(0);"+
          "if(range.collapsed){const node=range.startContainer;"+
          "if(node.nodeType!==3||range.startOffset===0)return 'NO_FIELD';"+
          "const txt=node.textContent.slice(0,range.startOffset);"+
          "range.setStart(node,range.startOffset-Array.from(txt).slice(-1)[0].length);}"+
          "range.deleteContents();"+
          "el.dispatchEvent(new Event('input',{bubbles:true}));return 'EDIT_APPLIED';}"+
          "return 'NO_FIELD';"+
          "}catch(e){return 'NO_FIELD';}})()";
        browser.evaluateJavascript(js,result->{
            if(isFinishing()||isDestroyed())return;
            if(result!=null&&result.contains("EDIT_APPLIED"))HouseholdPhoneSession.feedback("EDIT_APPLIED");
            else if(result!=null&&result.contains("BLOCKED"))HouseholdPhoneSession.feedback("FIELD_BLOCKED");
            else HouseholdPhoneSession.feedback("EDIT_FIELD_REQUIRED");
        });
    }
    /** A phone's Send targets the selected field, not an invisible top search. */
    private void phoneText(String raw){phoneText(raw,false);}
    private void phoneText(String raw,boolean submit){
        if(raw==null||raw.isEmpty()){if(submit)HouseholdPhoneSession.feedback("INVALID_QUERY");return;}
        String typed=raw.substring(0,Math.min(256,raw.length()));
        if(torWebMode&&browserBar!=null&&browserBar.showing()
                &&browserBar.address().hasFocus()){
            browserBar.address().setText(typed);
            browserBar.address().setSelection(browserBar.address().length());
            if(submit)browserBar.goFromPhone();
            phonePairEvent("typed_address");
            HouseholdPhoneSession.feedback("WEB_TEXT_SENT");return;
        }
        if(chrome!=null&&chrome.isShown()&&query!=null&&query.hasFocus()){
            query.setText(typed);
            query.setSelection(query.length());
            if(submit){search();HouseholdPhoneSession.feedback("SEARCH_SENT");}
            else HouseholdPhoneSession.feedback("WEB_TEXT_SENT");
            phonePairEvent("typed_search");return;
        }
        if(browser==null||!pointerPage||homeRequested||detailRequested||custom!=null){
            android.widget.Toast.makeText(this,
                "Select a website text field first",android.widget.Toast.LENGTH_SHORT).show();
            phonePairEvent("text_no_field");
            HouseholdPhoneSession.feedback("FIELD_REQUIRED");return;
        }
        // Never inspect or send text into opaque third-party iframes, passwords,
        // OTP, PIN, payment or other private form fields over a plain-HTTP phone
        // relay. Scope to the top document's currently focused editable field.
        String js="(function(){try{const el=document.activeElement;"+
          "if(!el||el===document.body)return 'NO_FIELD';"+
          "if(el.tagName==='IFRAME')return 'IFRAME_NOT_ACCESSIBLE';"+
          "const name=((el.name||'')+' '+(el.id||'')+' '+(el.autocomplete||'')).toLowerCase();"+
          "const type=(el.type||'').toLowerCase();"+
          "if(type==='password'||type==='tel'||type==='number'||"+
          "/(otp|pass|pin|card|cvv|secret|one-time|verification|payment)/i.test(name))return 'SENSITIVE_FIELD';"+
          "const value="+org.json.JSONObject.quote(typed)+";"+
          "const submit="+submit+";"+
          "if(el.tagName==='INPUT'||el.tagName==='TEXTAREA'){"+
          "if(el.readOnly||el.disabled)return 'NOT_EDITABLE';"+
          "const t=el.type||'text';if(!/^(text|search|url|email)$/i.test(t)&&el.tagName==='INPUT')return 'NOT_EDITABLE';"+
          "let start=el.selectionStart,end=el.selectionEnd;"+
          "if(start==null||end==null){start=end=el.value.length;}"+
          "el.setRangeText(value,0,el.value.length,'end');"+
          "el.dispatchEvent(new Event('input',{bubbles:true}));"+
          "if(submit){if(el.form&&el.form.requestSubmit)el.form.requestSubmit();"+
          "else el.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true}));}"+
          "return submit?'FIELD_SUBMITTED':'FIELD_SELECTED';}"+
          "if(el.isContentEditable){"+
          "const existing=el.textContent||'';"+
          "if(existing!==value)el.textContent=value;"+
          "const ok=true;"+
          "if(ok){el.dispatchEvent(new Event('input',{bubbles:true}));"+
          "if(submit)el.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true}));"+
          "return submit?'FIELD_SUBMITTED':'FIELD_SELECTED';}"+
          "return 'NOT_EDITABLE';}return 'NO_FIELD';"+
          "}catch(e){return 'NOT_EDITABLE';}})()";
        browser.evaluateJavascript(js,result->{
            if(isFinishing()||isDestroyed())return;
            if(result!=null&&(result.contains("FIELD_SELECTED")||result.contains("FIELD_SUBMITTED"))){
                phonePairEvent("typed_website");
                HouseholdPhoneSession.feedback("WEB_TEXT_SENT");
                return;
            }
            HouseholdPhoneSession.feedback(result!=null&&result.contains("SENSITIVE_FIELD")
                ?"FIELD_BLOCKED":"FIELD_REQUIRED");
            phonePairEvent("text_no_field");
            android.widget.Toast.makeText(this,
                "Tap the text box on the website first · phone text stays private",
                android.widget.Toast.LENGTH_LONG).show();
        });
    }
    private static java.net.InetAddress homeWifiAddress(){
        try{
            java.util.Enumeration<java.net.NetworkInterface> all=java.net.NetworkInterface.getNetworkInterfaces();
            java.net.InetAddress fallback=null;
            while(all!=null&&all.hasMoreElements()){
                java.net.NetworkInterface net=all.nextElement();
                if(!net.isUp()||net.isLoopback())continue;
                java.util.Enumeration<java.net.InetAddress> addresses=net.getInetAddresses();
                while(addresses.hasMoreElements()){
                    java.net.InetAddress address=addresses.nextElement();
                    if(!(address instanceof java.net.Inet4Address)||address.isLoopbackAddress()||!address.isSiteLocalAddress())continue;
                    if(net.getName().startsWith("wlan")||net.getName().startsWith("eth"))return address;
                    if(fallback==null)fallback=address;
                }
            }
            return fallback;
        }catch(java.net.SocketException e){return null;}
    }
    private void showInteractionLog(){
        new android.app.AlertDialog.Builder(this).setTitle("Recent interaction log")
            .setMessage(performanceEvidence().toString()+"\n\nLast film transport error: "+lastFilmFailure+"\nDiagnostics waiting locally: "+Telemetry.queuedCount(this)+"\n"+Telemetry.lastStatus(this)+"\n\n"+reviewJournal.summary()).setPositiveButton("Save locally",(d,w)->{
                boolean ok=reviewJournal.save(this,performanceEvidence());
                android.widget.Toast.makeText(this,ok?"Technical log saved locally; not uploaded":"Local save failed",android.widget.Toast.LENGTH_LONG).show();
            }).setNegativeButton("Close",null).show();
    }
    private void moveCursor(FrameLayout parent){
        cursor.cancel();android.view.ViewParent old=cursor.getParent();if(old instanceof android.view.ViewGroup)((android.view.ViewGroup)old).removeView(cursor);
        parent.addView(cursor,new FrameLayout.LayoutParams(-1,-1));
    }
    void enterFullScreen(View view,WebChromeClient.CustomViewCallback callback){
        if(browserBar!=null)browserBar.hideBar();
        if(custom!=null||isFinishing()){callback.onCustomViewHidden();return;}
        inputEpoch++;noteInput("FULLSCREEN_ENTER");
        if(startMonitor!=null)startMonitor.pause();
        nativeControls.pause();nativeControls.hideAll();handler.removeCallbacks(playDeadline);playUntil=0;
        custom=view;customCallback=callback;browseRoot.setVisibility(View.GONE);browser.setVisibility(View.GONE);
        notifyPhoneMode();
        fullHost.setVisibility(View.VISIBLE);fullHost.addView(view,new FrameLayout.LayoutParams(-1,-1));
        moveCursor(fullHost);cursor.target(view);mouseActive=true;cursor.enable(true);cursor.center();cursor.enter();
        fullMenu=new LinearLayout(this);fullMenu.setOrientation(LinearLayout.VERTICAL);fullMenu.setPadding(16,10,16,12);fullMenu.setBackgroundColor(0xee071e29);
        fullMenu.addView(TvUi.label(this,ReviewDisplay.details(),14,TvUi.MINT,true));
        LinearLayout buttons=new LinearLayout(this);fullMenu.addView(buttons);
        String[] labels={"Resume",pointerPage?"Website sources":"Sources","Exit fullscreen"};
        for(int i=0;i<labels.length;i++){final int item=i;Button b=TvUi.button(this,labels[i],i==0);b.setId(View.generateViewId());buttons.addView(b,new LinearLayout.LayoutParams(0,TvUi.dp(this,48),1));b.setOnClickListener(v->{
            if(item==0){resumeFullscreenPointer();}
            else if(item==1){exitFullScreen();if(pointerPage){resumePagePointer(false);}else{usePage();nativeControls.openSources();}}
            else exitFullScreen();
        });}
        for(int i=0;i<buttons.getChildCount();i++){View b=buttons.getChildAt(i);b.setNextFocusLeftId(buttons.getChildAt(Math.max(0,i-1)).getId());b.setNextFocusRightId(buttons.getChildAt(Math.min(buttons.getChildCount()-1,i+1)).getId());b.setNextFocusUpId(b.getId());b.setNextFocusDownId(b.getId());}
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM);fullHost.addView(fullMenu,fp);fullMenu.setVisibility(View.GONE);
        view.requestFocus();TvUi.immersive(this);
        final View current=view;
        fullHost.postOnAnimation(()->{if(custom==current&&mouseActive){cursor.enter();}});
    }
    private void resumeFullscreenPointer(){
        if(custom==null)return;inputEpoch++;
        fullMenu.setVisibility(View.GONE);mouseActive=true;cursor.target(custom);cursor.enable(true);
        custom.requestFocus();cursor.enter();noteInput("FULLSCREEN_RESUME");
    }
    private void showFullscreenMenu(){
        if(custom==null)return;inputEpoch++;noteInput("FULLSCREEN_MENU");mouseActive=false;cursor.enable(false);fullMenu.setVisibility(View.VISIBLE);fullMenu.bringToFront();((LinearLayout)fullMenu.getChildAt(1)).getChildAt(0).requestFocus();
    }

    private final HeldKeyRamp heldKeys=new HeldKeyRamp();
    /** Held-key speed (HeldKeyRamp): a tap moves at once; a held arrow key repeats slow, then faster. */
    private boolean heldDpadTooFast(KeyEvent e){
        int k=e.getKeyCode();if(k<KeyEvent.KEYCODE_DPAD_UP||k>KeyEvent.KEYCODE_DPAD_RIGHT)return false;
        if(e.getAction()==KeyEvent.ACTION_UP){heldKeys.reset();return false;}
        return e.getAction()==KeyEvent.ACTION_DOWN&&!heldKeys.allow(k,e.getDownTime(),e.getEventTime(),e.getRepeatCount());
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int navigation=event.getKeyCode();
        if(navigation==KeyEvent.KEYCODE_SEARCH||navigation==KeyEvent.KEYCODE_VOICE_ASSIST){
            if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0){if(navigation==KeyEvent.KEYCODE_SEARCH){searchKeyHeld=true;toolbar();query.requestFocus();if(!simplifiedDiscoverExperience())UnifiedSearch.dialog(this);}else{voiceKeyHeld=true;voiceSearch();}}return true;}
            if(event.getAction()==KeyEvent.ACTION_UP){searchKeyHeld=false;voiceKeyHeld=false;return true;}
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN)keyEventSerial++;
        // F already belongs to provider players for entering fullscreen. Capture
        // it only while actually fullscreen, so ordinary search typing is untouched.
        if(navigation==KeyEvent.KEYCODE_F&&(custom!=null||fullscreenKeyHeld)&&event.hasNoModifiers()){
            if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0)fullscreenKeyHeld=true;return true;}
            if(event.getAction()==KeyEvent.ACTION_UP){boolean held=fullscreenKeyHeld;fullscreenKeyHeld=false;if(held&&!event.isCanceled()&&custom!=null)exitFullScreen();return true;}
        }
        // Escape is a deterministic way back to controls, without going back to
        // the preceding website. The TV remote retains its existing Back/Menu.
        if(navigation==KeyEvent.KEYCODE_ESCAPE&&(custom!=null||(!homeRequested&&!detailRequested)||escapeHeld)&&event.hasNoModifiers()){
            if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0)escapeHeld=true;return true;}
            if(event.getAction()==KeyEvent.ACTION_UP){boolean held=escapeHeld;escapeHeld=false;if(held&&!event.isCanceled()){
                if(custom!=null)exitFullScreen();else if(pointerPage)pointerNavigation();else if(mouseActive)leaveMouse(true);else{if(startMonitor!=null)startMonitor.pause();usePage();nativeControls.menu();}
            }return true;}
        }
        if(startMonitor!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&(navigation==KeyEvent.KEYCODE_BACK||navigation==KeyEvent.KEYCODE_MEDIA_PAUSE))startMonitor.pause();
        if(navigation==KeyEvent.KEYCODE_BACK||navigation==KeyEvent.KEYCODE_MENU){
            boolean isBack=navigation==KeyEvent.KEYCODE_BACK;
            if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0){if(isBack)backHeld=true;else menuHeld=true;}return true;}
            if(event.getAction()==KeyEvent.ACTION_UP){
                boolean accepted=isBack?backHeld:menuHeld;if(isBack)backHeld=false;else menuHeld=false;
                if(!accepted||event.isCanceled())return true;
                inputEpoch++;noteInput(isBack?"BACK":"MENU");
                if(isBack){onBackPressed();return true;}
                if(custom!=null){if(fullMenu!=null&&fullMenu.getVisibility()==View.VISIBLE)resumeFullscreenPointer();else showFullscreenMenu();return true;}
                if(pointerPage&&torWebMode&&browserBar!=null){
                    if(browserBar.showing()){browserBar.hideBar();enterPagePointer();}
                    else browserBar.showBar(browser==null?null:browser.getUrl(),false);
                    return true;
                }
                if(pointerPage){pointerNavigation();return true;}
                if(mouseActive){leaveMouse(true);return true;}
                if(chrome.getVisibility()==View.VISIBLE&&chrome.hasFocus()){showOptions();return true;}
                if(homeRequested||detailRequested){toolbar();return true;}
                nativeControls.toggleMenu();return true;
            }
            return true;
        }
        if(providerStatusPanel!=null&&providerStatusPanel.isShown()&&providerRecoveryActions.isShown())return super.dispatchKeyEvent(event);
        if(pointerPage&&pageEditor&&custom==null)return super.dispatchKeyEvent(event);
        if(startMonitor!=null&&startMonitor.ownsFocus())return super.dispatchKeyEvent(event);
        if(custom==null&&heldDpadTooFast(event))return true;
        if(custom!=null){
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)exitFullScreen();return true;}
            if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)showFullscreenMenu();return true;}
            if(fullMenu!=null&&fullMenu.getVisibility()==View.VISIBLE){
                // Fullscreen's custom WebView surface must not receive menu arrows.
                // Resolve them here instead of depending on ViewRoot fallback focus.
                if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                    if(event.getAction()==KeyEvent.ACTION_DOWN){
                        LinearLayout row=(LinearLayout)fullMenu.getChildAt(1);
                        int at=0;for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i).hasFocus()){at=i;break;}
                        if(key==KeyEvent.KEYCODE_DPAD_LEFT)at=Math.max(0,at-1);
                        else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)at=Math.min(row.getChildCount()-1,at+1);
                        row.getChildAt(at).requestFocus();
                    }
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
            if(cursor.handle(event)){
                if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER))controlTrace.emit(this,"mouse","MOUSE_CLICK_SENT");
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        // The top-edge browser bar takes native focus. When Page ↓ is selected,
        // focus is returned to the WebView pointer, never the background page.
        if(torWebMode&&browserBar!=null&&browserBar.showing()&&browserBar.hasFocus())
            return super.dispatchKeyEvent(event);
        // A deliberate Down from the TV's search field enters the webpage
        // pointer. WebView focus changes alone do not silently hide search.
        if(simplifiedDiscoverExperience()&&pointerPage&&chrome!=null&&
            chrome.getVisibility()==View.VISIBLE&&query!=null&&query.hasFocus()&&
            navigation==KeyEvent.KEYCODE_DPAD_DOWN){
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){
                phoneSearchWanted=false;enterPagePointer();
            }
            return true;
        }
        // Native toolbar focus is authoritative. Mouse/page navigation must not consume its keys.
        if(chrome.getVisibility()==View.VISIBLE&&chrome.hasFocus()){
            if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){if(simplifiedDiscoverExperience())finish();else usePage();}return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)showOptions();return true;}
            if(!homeRequested)return super.dispatchKeyEvent(event);
        }
        if(mouseActive){
            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU||event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)leaveMouse(true);return true;}
            if(cursor.handle(event)){
                if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){controlTrace.emit(this,"mouse","MOUSE_CLICK_SENT");if(pointerPage)handler.postDelayed(this::checkPageEditor,80);}
                return true;
            }
        }
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
        if(nativeControls!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)nativeControls.userInput();if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP){if(homeRequested){chrome.setVisibility(View.VISIBLE);query.requestFocus();}else nativeControls.menu();}return true;}if(nativeControls.handleRemote(event,getCurrentFocus()))return true;if(nativeControls.ownsFocus())return super.dispatchKeyEvent(event);if(homePanel!=null&&homePanel.getVisibility()==View.VISIBLE)return super.dispatchKeyEvent(event);if(!cursor.enabled()&&(nativeControls.posterKey(event)||nativeControls.pageKey(event)))return true;}
        if(cursor!=null&&(custom!=null||browser!=null&&browser.hasFocus())&&cursor.handle(event))return true;
        return super.dispatchKeyEvent(event);
    }
    private void exitFullScreen(){
        if(custom==null||closingFullscreen)return;inputEpoch++;noteInput("FULLSCREEN_EXIT");closingFullscreen=true;
        View old=custom;WebChromeClient.CustomViewCallback callback=customCallback;custom=null;customCallback=null;
        cursor.cancel();fullHost.removeView(old);if(fullMenu!=null)fullHost.removeView(fullMenu);fullMenu=null;
        moveCursor(stage);fullHost.setVisibility(View.GONE);browseRoot.setVisibility(View.VISIBLE);
        mouseActive=false;cursor.enable(false);modeButton.setText("Cursor: off");
        if(browser!=null){browser.setVisibility(View.VISIBLE);cursor.target(browser);}
        if(startMonitor!=null)startMonitor.pause();
        try{if(callback!=null)callback.onCustomViewHidden();}finally{closingFullscreen=false;}
        notifyPhoneMode();
        if(!isFinishing()&&!isDestroyed()){
            if(phoneSwitchingSource&&browser!=null){
                if(nativeControls!=null)nativeControls.resume();
                browser.requestFocus();return;
            }
            if(pointerPage){resumePagePointer(false);return;}
            nativeControls.resume();toolbar();
            final long epoch=inputEpoch,keys=keyEventSerial;
            // WebView can request focus again after its fullscreen callback.
            // Restore only if no later user input or navigation has happened.
            screen.postOnAnimation(()->{
                if(custom==null&&epoch==inputEpoch&&keys==keyEventSerial&&!isFinishing()&&chrome.getVisibility()==View.VISIBLE){
                    View focus=getCurrentFocus();
                    if(focus==null||focus==browser||focus==cursor)toolbarAnchor.requestFocus();
                }
            });
        }
    }
    @Override public void onBackPressed(){
        inputEpoch++;
        if(custom!=null){exitFullScreen();return;}
        if(verifiedImmersive){leaveVerifiedFullscreen();return;}
        if(pointerPage){if(simplifiedDiscoverExperience()&&chrome.getVisibility()==View.VISIBLE&&chrome.hasFocus())finish();else pointerBack();return;}
        if(mouseActive){leaveMouse(true);return;}
        if(detailRequested){returnToCards();return;}
        if(homeRequested){finish();return;}
        if(nativeControls!=null&&nativeControls.back())return;
        // Back from the browsing toolbar goes to retained results, not back into
        // the page/toolbar toggle that previously required repeated Back presses.
        if(chrome.getVisibility()==View.VISIBLE&&chrome.hasFocus()){returnToCards();return;}
        if(browser!=null&&browser.canGoBack()){nativeControls.pause();browser.goBack();usePage();return;}
        returnToCards();
    }
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event){
        if(mouseActive&&cursor!=null)cursor.observePointer(event);
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        if(mouseActive&&cursor!=null)cursor.observePointer(event);
        return super.dispatchTouchEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(cursor==null)return;
        if(!focused){cursor.cancel();backHeld=false;menuHeld=false;fullscreenKeyHeld=false;escapeHeld=false;}
        else if(mouseActive&&(custom==null||fullMenu==null||fullMenu.getVisibility()!=View.VISIBLE))cursor.enter();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("home",homeRequested);state.putString("query",query.getText().toString());state.putString("url",lastRequested);super.onSaveInstanceState(state);}
    @Override protected void onPause(){handler.removeCallbacks(phonePlaybackPulse);HouseholdPhoneSession.detach(filmPhoneSink);endOneClick();if(startMonitor!=null)startMonitor.pause();handler.removeCallbacks(detailWait);EngagementTracker.stop(this);handler.removeCallbacks(playDeadline);playUntil=0;if(nativeControls!=null)nativeControls.pause();if(cursor!=null)cursor.cancel();handler.removeCallbacks(slowLoad);if(browser!=null)browser.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();
        HouseholdPhoneSession.attach(filmPhoneSink);
        phoneConnected=HouseholdPhoneSession.connected();updatePhoneBadge();
        notifyPhoneMode();handler.removeCallbacks(phonePlaybackPulse);handler.post(phonePlaybackPulse);
        EngagementTracker.start(this,"discover");if(nativeControls!=null&&!mouseActive&&custom==null)nativeControls.resume();if(browser!=null)browser.onResume();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);InAppVoiceSearch.permission(this,r,g);}
    @Override protected void onStop(){InAppVoiceSearch.stopped(this);super.onStop();}
    @Override protected void onDestroy(){if(verifiedImmersive)leaveVerifiedFullscreen();if(pairingDialog!=null){pairingDialog.dismiss();pairingDialog=null;}if(browserBar!=null)browserBar.dispose();HouseholdPhoneSession.detach(filmPhoneSink);InAppVoiceSearch.close(this);if(startMonitor!=null)startMonitor.leave();liveGeneration++;liveExecutor.shutdownNow();if(torPosterLoader!=null)torPosterLoader.close();if(torController!=null)torController.destroy();if(nativeControls!=null)nativeControls.destroy();handler.removeCallbacksAndMessages(null);if(cursor!=null)cursor.leave();exitFullScreen();if(browser!=null){browser.stopLoading();stage.removeView(browser);browser.destroy();browser=null;}super.onDestroy();}
}
