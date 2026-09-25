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

/** Provider-owned pages/player inside GharTV with a local remote-control pointer.
 *  No DOM injection, browser-detection changes, extracted streams or private-log upload.
 */
public final class FlixMomoActivity extends Activity {
    private static final String HOME="https://flixmomo.app/";
    private WebView browser;
    private RemoteWebCursor cursor;
    private FilmNativeControls nativeControls;
    private static final int VOICE_REQUEST=410;
    private EditText query;
    private TextView status,help;
    private Button pageButton,modeButton,scrollButton,retryButton;
    private LinearLayout chrome;
    private FrameLayout stage;
    private View custom;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean mainFrameError,pageReady,loading;
    private String lastRequested=HOME;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable slowLoad=()->{
        if(loading&&!mainFrameError)status.setText("Still connecting. Retry is available; no page or video has been verified.");
    };

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(TvUi.BG);
        root.setPadding(TvUi.dp(this,18),TvUi.dp(this,10),TvUi.dp(this,18),TvUi.dp(this,10));
        chrome=new LinearLayout(this);chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.addView(TvUi.label(this,"GharTV / FlixMomo",20,TvUi.TEXT,true));
        LinearLayout row=new LinearLayout(this);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(TvUi.TEXT);query.setHintTextColor(TvUi.MUTED);
        query.setHint("Find a film or series");query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        row.addView(query,new LinearLayout.LayoutParams(0,TvUi.dp(this,48),1));
        Button search=TvUi.button(this,"Search",true),browse=TvUi.button(this,"Browse",false),guide=TvUi.button(this,"TV guide",false);
        Button voice=TvUi.button(this,"Voice",false);voice.setOnClickListener(v->voiceSearch());
        row.addView(search);row.addView(voice);row.addView(browse);row.addView(guide);chrome.addView(row);
        LinearLayout navigation=new LinearLayout(this);
        pageButton=TvUi.button(this,"Use page",true);modeButton=TvUi.button(this,"Cursor: on",false);
        scrollButton=TvUi.button(this,"Scroll: off",false);retryButton=TvUi.button(this,"Retry",false);
        navigation.addView(pageButton);navigation.addView(modeButton);navigation.addView(scrollButton);navigation.addView(retryButton);chrome.addView(navigation);
        help=TvUi.label(this,"Use page: arrows move cursor; OK clicks. Hold arrows to move faster. Back/Menu returns here.",12,TvUi.MUTED,false);chrome.addView(help);
        status=TvUi.label(this,"Provider pages stay in GharTV · direct connection",12,TvUi.MUTED,false);chrome.addView(status);
        root.addView(chrome);stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        cursor=new RemoteWebCursor(this);stage.addView(cursor,new FrameLayout.LayoutParams(-1,-1));
        nativeControls=new FilmNativeControls(this,stage,chrome,new FilmNativeControls.Host(){
            public void navigate(String url){FlixMomoActivity.this.navigate(url,false);}
            public void usePage(){cursor.enable(true);modeButton.setText("Cursor: on");FlixMomoActivity.this.usePage();}
            public void nativeMode(){cursor.enable(false);modeButton.setText("Cursor: off");}
            public void searchToolbar(){chrome.setVisibility(View.VISIBLE);toolbar();query.requestFocus();}
        });
        query.setOnFocusChangeListener((v,focused)->{if(focused)cursor.leave();});
        search.setOnClickListener(v->search());browse.setOnClickListener(v->navigate(providerOrigin()+"/",true));guide.setOnClickListener(v->finish());
        query.setOnEditorActionListener((v,action,event)->{
            if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP){search();return true;}
            return false;
        });
        pageButton.setOnClickListener(v->usePage());
        modeButton.setOnClickListener(v->{cursor.enable(!cursor.enabled());modeButton.setText(cursor.enabled()?"Cursor: on":"Cursor: off");help.setText(cursor.enabled()?"Arrows move cursor; OK clicks; Back/Menu returns to toolbar.":"Native page focus: arrows move between links. Turn Cursor on for mouse-style control.");usePage();});
        scrollButton.setOnClickListener(v->{if(!cursor.enabled()){cursor.enable(true);modeButton.setText("Cursor: on");}cursor.scrollMode(!cursor.scrolling());scrollButton.setText(cursor.scrolling()?"Scroll: on":"Scroll: off");usePage();});
        retryButton.setOnClickListener(v->{if(browser==null)createBrowser();navigate(lastRequested,true);});
        if(!createBrowser())return;
        if(saved!=null){query.setText(saved.getString("query",""));String url=saved.getString("url",HOME);navigate(allowedTop(Uri.parse(url))?url:HOME,false);}
        else navigate(HOME,false);
        pageButton.requestFocus();
    }

    private boolean createBrowser(){
        if(browser!=null)return true;
        try{browser=new WebView(this);}catch(RuntimeException e){fail("Android System WebView is unavailable. Update that system component; live TV is unchanged.");return false;}
        stage.addView(browser,0,new FrameLayout.LayoutParams(-1,-1));cursor.target(browser);nativeControls.attach(browser);
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.setDownloadListener((u,a,d,m,n)->status.setText("Downloads are not enabled in this provider view."));
        browser.setOnFocusChangeListener((v,focused)->{if(focused)cursor.enter();else cursor.leave();});
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                if(allowedTop(request.getUrl()))return false;
                status.setText("External navigation blocked. The provider remains inside GharTV.");return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();String host=uri.getHost();
                if(!"https".equals(uri.getScheme())||host==null||privateHost(host))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){
                cursor.cancel();nativeControls.started();mainFrameError=false;pageReady=false;loading=true;
                if(allowedTop(Uri.parse(url)))lastRequested=url;
                status.setText("Opening FlixMomo…");handler.removeCallbacks(slowLoad);handler.postDelayed(slowLoad,20000);
            }
            @Override public void onPageFinished(WebView view,String url){
                loading=false;handler.removeCallbacks(slowLoad);
                if(mainFrameError){pageReady=false;return;}
                if("/dummy".equals(Uri.parse(url).getPath())){fail("FlixMomo declined this session. No provider protection has been changed.");return;}
                pageReady=allowedTop(Uri.parse(url));
                if(pageReady){status.setText("FlixMomo page loaded · native results/player controls are being read");nativeControls.finished();}
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame())fail("Provider returned HTTP "+response.getStatusCode()+". Your search is kept. Use Retry or Browse.");
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();fail("TLS verification failed. Connection stopped; no security checks were disabled.");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){
                if(!request.isForMainFrame())return;
                switch(error.getErrorCode()){
                    case WebViewClient.ERROR_HOST_LOOKUP:fail("DNS: Android cannot resolve the provider. No page/video loaded. Your query is kept; Retry does not change DNS settings.");break;
                    case WebViewClient.ERROR_CONNECT:fail("Android could not connect to the provider. Your query is kept. Check the TV network, then Retry.");break;
                    case WebViewClient.ERROR_TIMEOUT:fail("Provider request timed out. Your query is kept. Press Retry when ready.");break;
                    default:fail("Navigation failed ("+error.getErrorCode()+"). Your query is kept. Use Retry or TV guide.");break;
                }
            }
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                nativeControls.failure();cursor.leave();exitFullScreen();stage.removeView(view);view.destroy();if(browser==view)browser=null;cursor.target(null);
                fail("The provider browser stopped. Press Retry to reopen it; live TV and your query are preserved.");retryButton.requestFocus();return true;
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,android.webkit.GeolocationPermissions.Callback callback){callback.invoke(origin,false,false);}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(custom!=null){callback.onCustomViewHidden();return;}
                cursor.cancel();custom=view;customCallback=callback;chrome.setVisibility(View.GONE);browser.setVisibility(View.GONE);
                stage.addView(view,new FrameLayout.LayoutParams(-1,-1));cursor.target(view);cursor.enter();view.requestFocus();
            }
            @Override public void onHideCustomView(){exitFullScreen();}
        });return true;
    }
    private void fail(String message){if(nativeControls!=null)nativeControls.failure();mainFrameError=true;pageReady=false;loading=false;handler.removeCallbacks(slowLoad);status.setText(message);}
    static boolean providerHost(String host){return "flixmomo.app".equals(host)||"flixmomo.st".equals(host)||"www.flixmomo.st".equals(host);}
    static boolean allowedTop(Uri uri){return "https".equals(uri.getScheme())&&providerHost(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1;}
    private static boolean privateHost(String host){
        String h=host.toLowerCase(java.util.Locale.ROOT);
        return h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||h.contains(":")||h.matches("(?i)(127\\..*|10\\..*|192\\.168\\..*|169\\.254\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|0\\..*)");
    }
    private String providerOrigin(){String url=browser==null?lastRequested:browser.getUrl();Uri uri=Uri.parse(url==null?HOME:url);return allowedTop(uri)?"https://"+uri.getHost():"https://flixmomo.app";}
    private void search(){String text=query.getText().toString().trim();if(text.length()<2||text.length()>120){status.setText("Enter 2–120 characters.");return;}navigate(providerOrigin()+"/search?q="+Uri.encode(text),true);}
    private void navigate(String url,boolean page){
        if(!allowedTop(Uri.parse(url))){fail("Only the registered provider can open in this view.");return;}
        hideKeyboard();lastRequested=url;if(browser==null){fail("Provider browser is not running. Press Retry to reopen it.");return;}browser.loadUrl(url);if(page)usePage();
    }
    private void voiceSearch(){
        hideKeyboard();Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Search FlixMomo — speech handled by your TV's recognition service");
        try{startActivityForResult(intent,VOICE_REQUEST);}
        catch(ActivityNotFoundException|SecurityException e){status.setText("Voice recognition is unavailable on this TV. Type your search instead.");query.requestFocus();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=VOICE_REQUEST||result!=RESULT_OK||data==null)return;
        java.util.ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(words==null||words.isEmpty()||words.get(0)==null)return;
        String text=words.get(0).trim();if(text.length()<2||text.length()>120){status.setText("Voice text must be 2–120 characters. You can edit the search.");return;}
        query.setText(text);search();
    }
    private void hideKeyboard(){InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(query.getWindowToken(),0);query.clearFocus();}
    private void usePage(){if(nativeControls!=null)nativeControls.hideAll();hideKeyboard();if(browser!=null){browser.requestFocus();cursor.enter();}}
    private void toolbar(){chrome.setVisibility(View.VISIBLE);cursor.leave();if(custom!=null)exitFullScreen();pageButton.requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(nativeControls!=null){
            if(event.getAction()==KeyEvent.ACTION_DOWN)nativeControls.userInput();
            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)nativeControls.menu();return true;}
            if(nativeControls.ownsFocus())return super.dispatchKeyEvent(event);
        }
        if(cursor!=null){
            boolean inPage=custom!=null||browser!=null&&browser.hasFocus();
            if(inPage&&event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)toolbar();return true;}
            if(inPage&&cursor.handle(event))return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void exitFullScreen(){
        if(custom==null)return;cursor.cancel();stage.removeView(custom);custom=null;chrome.setVisibility(View.VISIBLE);
        if(browser!=null){browser.setVisibility(View.VISIBLE);cursor.target(browser);}
        WebChromeClient.CustomViewCallback callback=customCallback;customCallback=null;if(callback!=null)callback.onCustomViewHidden();
    }
    @Override public void onBackPressed(){if(nativeControls!=null&&nativeControls.back())return;if(custom!=null){exitFullScreen();usePage();return;}if(browser!=null&&browser.hasFocus()){toolbar();return;}if(browser!=null&&browser.canGoBack()){browser.goBack();usePage();return;}super.onBackPressed();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused&&cursor!=null)cursor.cancel();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("query",query.getText().toString());state.putString("url",lastRequested);super.onSaveInstanceState(state);}
    @Override protected void onPause(){if(nativeControls!=null)nativeControls.pause();if(cursor!=null)cursor.cancel();handler.removeCallbacks(slowLoad);if(browser!=null)browser.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(nativeControls!=null)nativeControls.resume();if(browser!=null)browser.onResume();}
    @Override protected void onDestroy(){if(nativeControls!=null)nativeControls.destroy();handler.removeCallbacksAndMessages(null);if(cursor!=null)cursor.leave();exitFullScreen();if(browser!=null){browser.stopLoading();stage.removeView(browser);browser.destroy();browser=null;}super.onDestroy();}
}
