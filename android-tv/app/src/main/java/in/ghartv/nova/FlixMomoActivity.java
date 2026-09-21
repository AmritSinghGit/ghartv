package in.ghartv.nova;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
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
import org.json.JSONObject;
import java.io.ByteArrayInputStream;

/** Independent provider browser. No stream extraction, credential bridge or licence bypass. */
public final class FlixMomoActivity extends Activity {
    private static final String HOME = "https://flixmomo.app/";
    private WebView browser;
    private TextView status;
    private EditText query;
    private LinearLayout chrome;
    private FrameLayout stage;
    private View custom;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean pageReady;
    private boolean mainFrameError;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(TvUi.BG);
        root.setPadding(TvUi.dp(this,18),TvUi.dp(this,12),TvUi.dp(this,18),TvUi.dp(this,12));
        chrome = new LinearLayout(this); chrome.setOrientation(LinearLayout.VERTICAL);
        TextView title=TvUi.label(this,"GharTV  /  FlixMomo",22,TvUi.TEXT,true);
        chrome.addView(title);
        LinearLayout bar=new LinearLayout(this);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(TvUi.TEXT);
        query.setHintTextColor(TvUi.MUTED);query.setHint("Search FlixMomo inside GharTV");
        bar.addView(query,new LinearLayout.LayoutParams(0,TvUi.dp(this,52),1));
        Button search=TvUi.button(this,"Search",true);bar.addView(search);
        Button home=TvUi.button(this,"Browse",false);bar.addView(home);
        Button back=TvUi.button(this,"TV guide",false);bar.addView(back);
        chrome.addView(bar);
        status=TvUi.label(this,"Independent provider browser · device network · no Jio account required",12,TvUi.MUTED,false);
        chrome.addView(status);root.addView(chrome);
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        try { browser=new WebView(this); } catch (RuntimeException error) {
            status.setText("Android System WebView is unavailable. Update the TV's browser component; live TV is unchanged.");
            back.setOnClickListener(v->finish());return;
        }
        stage.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=browser.getSettings();
        settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.setDownloadListener((u,a,d,m,n)->status.setText("Downloads are not enabled in this provider browser."));
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                if(!r.isForMainFrame())return false;
                if(allowedTop(r.getUrl()))return false;
                status.setText("External navigation blocked. Use the provider's in-page controls.");return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();String host=u.getHost();
                if(!"https".equals(u.getScheme()) || host==null || host.equals("localhost") || host.endsWith(".local") ||
                    host.matches("(?i)(127\\..*|10\\..*|192\\.168\\..*|169\\.254\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|\\[?::1\\]?|0\\..*)"))
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageStarted(WebView v,String u,Bitmap b){pageReady=false;mainFrameError=false;status.setText("Opening provider… Playback depends on its availability and permissions.");}
            @Override public void onPageFinished(WebView v,String u){
                pageReady=allowedTop(Uri.parse(u));
                if("/dummy".equals(Uri.parse(u).getPath())){pageReady=false;status.setText("FlixMomo declined this embedded session. No protection was changed.");}
                else if(pageReady && !mainFrameError)status.setText("FlixMomo in GharTV · select a result and use its player · direct connection");
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()){mainFrameError=true;status.setText("FlixMomo returned HTTP "+response.getStatusCode()+". Complete provider verification here if offered.");}
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();status.setText("TLS verification failed. Connection stopped.");}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,android.webkit.WebResourceError e){
                if(r.isForMainFrame()){pageReady=false;status.setText("Provider unavailable. Browse retries the page; Back returns to GharTV.");}
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
            @Override public void onGeolocationPermissionsShowPrompt(String o,android.webkit.GeolocationPermissions.Callback c){c.invoke(o,false,false);}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(custom!=null){callback.onCustomViewHidden();return;}
                custom=view;customCallback=callback;chrome.setVisibility(View.GONE);browser.setVisibility(View.GONE);
                stage.addView(view,new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView(){exitFullScreen();}
        });
        search.setOnClickListener(v->search());
        query.setOnEditorActionListener((v,action,event)->{search();return true;});
        home.setOnClickListener(v->browser.loadUrl(HOME));back.setOnClickListener(v->finish());
        browser.loadUrl(HOME);
    }
    static boolean allowedTop(Uri u){
        return "https".equals(u.getScheme()) && "flixmomo.app".equals(u.getHost()) && u.getUserInfo()==null && u.getPort()==-1;
    }
    private void search(){
        String text=query.getText().toString().trim();
        if(text.length()<2 || text.length()>120){status.setText("Enter 2–120 characters.");return;}
        if(browser==null){status.setText("Android System WebView is unavailable.");return;}
        // Provider-owned search and player remain inside this GharTV activity.
        // No DOM injection, stream extraction or authentication changes.
        browser.loadUrl(HOME+"search?q="+Uri.encode(text));
        browser.requestFocus();
    }
    private void exitFullScreen(){
        if(custom==null)return;stage.removeView(custom);custom=null;
        browser.setVisibility(View.VISIBLE);chrome.setVisibility(View.VISIBLE);
        if(customCallback!=null){customCallback.onCustomViewHidden();customCallback=null;}
    }
    @Override public void onBackPressed(){
        if(custom!=null){exitFullScreen();return;}
        if(browser!=null && browser.canGoBack()){browser.goBack();return;}super.onBackPressed();
    }
    @Override protected void onPause(){if(browser!=null)browser.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(browser!=null)browser.onResume();}
    @Override protected void onDestroy(){
        exitFullScreen();if(browser!=null){browser.stopLoading();stage.removeView(browser);browser.destroy();browser=null;}
        super.onDestroy();
    }
}
