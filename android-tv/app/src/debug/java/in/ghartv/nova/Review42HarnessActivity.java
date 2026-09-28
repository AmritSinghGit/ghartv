package in.ghartv.nova;

/** Owned local regression fixture. No external account, media or TLS bypass. */
public final class Review42HarnessActivity extends FlixMomoActivity {
    private final java.util.Map<String,byte[]> pages=new java.util.concurrent.ConcurrentHashMap<>();
    private boolean installed;
    @Override protected FilmHomeView createHomePanel(FilmHomeView.Host h){return new FilmHomeView(this,h,(v,u)->v.setImageDrawable(new android.graphics.drawable.ColorDrawable(0xff164e60)));}
    @Override protected FilmDetailView createDetailPanel(FilmDetailView.Host h){return new FilmDetailView(this,h){@Override void loadArtwork(android.widget.ImageView v,String u){v.setImageDrawable(new android.graphics.drawable.ColorDrawable(0xff164e60));}};}
    @Override protected void loadProviderPage(String url){
        if(!installed){
            installed=true;final android.webkit.WebViewClient production=browser.getWebViewClient();browser.getSettings().setBlockNetworkLoads(true);
            browser.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest r){return production.shouldOverrideUrlLoading(v,r);}
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView v,android.webkit.WebResourceRequest r){android.webkit.WebResourceResponse deny=production.shouldInterceptRequest(v,r);if(deny!=null)return deny;byte[] data=pages.get(r.getUrl().toString());return new android.webkit.WebResourceResponse(data==null?"text/plain":"text/html","UTF-8",data==null?404:200,data==null?"No fixture":"OK",java.util.Collections.singletonMap("Cache-Control","no-store"),new java.io.ByteArrayInputStream(data==null?new byte[0]:data));}
                @Override public void onPageStarted(android.webkit.WebView v,String u,android.graphics.Bitmap b){production.onPageStarted(v,u,b);}
                @Override public void onPageFinished(android.webkit.WebView v,String u){production.onPageFinished(v,u);}
                @Override public void doUpdateVisitedHistory(android.webkit.WebView v,String u,boolean reload){production.doUpdateVisitedHistory(v,u,reload);}
                @Override public void onReceivedError(android.webkit.WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError e){production.onReceivedError(v,r,e);}
                @Override public void onReceivedHttpError(android.webkit.WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceResponse e){production.onReceivedHttpError(v,r,e);}
                @Override public void onReceivedSslError(android.webkit.WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){production.onReceivedSslError(v,h,e);}
            });
        }
        String style="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Owned title fixture</title><style>body{margin:24px;background:#102631;color:white;font:18px sans-serif}h2{font-size:28px}a,button{display:inline-block;padding:10px;margin:5px}video{width:620px;height:350px;display:block;background:black}img{width:140px;height:210px}</style>";
        String home=style+"<a href='/movie/lantern'><img src='https://image.tmdb.org/lantern.png' alt='Paper Lantern'><h2>Paper Lantern</h2></a>";
        String detail=style+"<section><h2>Paper Lantern</h2><div>Movie 1 hr 41 min</div><a href='/watch/lantern'>WATCH NOW</a><div>A locally authored story about a family rebuilding a library after a storm, written only for testing this interface.</div></section>";
        StringBuilder choices=new StringBuilder("<script>window.reselections=0;window.mediaClicks=0;</script><div>");for(int i=1;i<=12;i++)choices.append("<button onclick=\"window.reselections++;const v=document.querySelector('video');if(v){v.pause();v.currentTime=0}\">PLAYER #").append(i).append(" <span>BEST</span></button>");choices.append("</div>");
        String watch=style+choices+(getIntent().getBooleanExtra("no_media",false)?"":"<video preload='auto' loop playsinline src='data:video/mp4;base64,"+Review40HarnessActivity.ownedClip()+"' onclick='window.mediaClicks++;window.trustedMedia=event.isTrusted;this.play()'></video>");
        pages.put("https://flixmomo.app/",home.getBytes(java.nio.charset.StandardCharsets.UTF_8));pages.put("https://flixmomo.app/movie/lantern",detail.getBytes(java.nio.charset.StandardCharsets.UTF_8));pages.put("https://flixmomo.app/watch/lantern",watch.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        browser.loadUrl(url);
    }
}
