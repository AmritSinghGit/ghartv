package in.ghartv.nova;

/** Test-only media and HTML. Inherits the production Activity, callbacks and input handling. */
public final class Review40HarnessActivity extends FlixMomoActivity {
    public int loads;
    static String ownedClip(){return CLIP;}
    private final java.util.concurrent.ConcurrentHashMap<String,byte[]> fixturePages=new java.util.concurrent.ConcurrentHashMap<>();
    private android.webkit.WebView interceptedBrowser;
    private static final String CLIP="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";
    @Override protected FilmHomeView createHomePanel(FilmHomeView.Host h){return new FilmHomeView(this,h,(target,url)->image(target));}
    @Override protected FilmDetailView createDetailPanel(FilmDetailView.Host h){return new FilmDetailView(this,h){@Override void loadArtwork(android.widget.ImageView target,String url){image(target);}};}
    private void image(android.widget.ImageView target){android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(156,234,android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(0xff256378);target.setImageBitmap(b);}
    private void installFixtureTransport(){
        if(interceptedBrowser==browser)return;
        final android.webkit.WebViewClient production=browser.getWebViewClient();
        // Keep every production navigation/error callback. Only response bytes
        // are supplied locally. Unknown requests never reach a remote server.
        browser.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest r){return production.shouldOverrideUrlLoading(v,r);}
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView v,android.webkit.WebResourceRequest r){
                android.webkit.WebResourceResponse denied=production.shouldInterceptRequest(v,r);if(denied!=null)return denied;
                byte[] bytes="GET".equals(r.getMethod())?fixturePages.get(r.getUrl().toString()):null;
                return new android.webkit.WebResourceResponse(bytes==null?"text/plain":"text/html","UTF-8",bytes==null?404:200,bytes==null?"Fixture not found":"OK",java.util.Collections.singletonMap("Cache-Control","no-store"),new java.io.ByteArrayInputStream(bytes==null?new byte[0]:bytes));
            }
            @Override public void onPageStarted(android.webkit.WebView v,String u,android.graphics.Bitmap icon){production.onPageStarted(v,u,icon);}
            @Override public void onPageFinished(android.webkit.WebView v,String u){production.onPageFinished(v,u);}
            @Override public void doUpdateVisitedHistory(android.webkit.WebView v,String u,boolean reload){production.doUpdateVisitedHistory(v,u,reload);}
            @Override public void onReceivedError(android.webkit.WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError e){production.onReceivedError(v,r,e);}
            @Override public void onReceivedHttpError(android.webkit.WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceResponse e){production.onReceivedHttpError(v,r,e);}
            @Override public void onReceivedSslError(android.webkit.WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){production.onReceivedSslError(v,h,e);}
            @Override public boolean onRenderProcessGone(android.webkit.WebView v,android.webkit.RenderProcessGoneDetail d){return production.onRenderProcessGone(v,d);}
        });
        interceptedBrowser=browser;
    }
    @Override protected void loadProviderPage(String url){
        loads++;browser.getSettings().setBlockNetworkLoads(true);installFixtureTransport();
        String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:20px;background:#102631;color:white;font:18px sans-serif}button,a{padding:15px;margin:8px;display:inline-block}h2{font-size:32px}#description{max-width:550px}video{width:600px;height:335px;background:black}img{width:120px;height:180px}aside{float:right;max-width:200px}nav{display:flex}</style><title>OWNED METADATA / PLAYBACK FIXTURE</title>";
        if(url.contains("/movie/")){
            html+="<nav><h1>FLIXMOMO</h1><a href='/'>Movies</a></nav><main id='content'><section><img src='https://image.tmdb.org/t/p/w300/owned-test.jpg' alt='Paper Lantern'><div><h2>Paper Lantern</h2><p>Movie 1 hr 41 min</p>";
            if(getIntent().getBooleanExtra("anchor",false))html+="<a id='watch' href='/watch/owned'>WATCH NOW</a>";
            else html+="<button id='watch' title='Play Paper Lantern' aria-label='Watch now' onclick='startWatch(event)'>WATCH NOW</button>";
            html+="<button id='save' onclick='window.saved=(window.saved||0)+1'>ADD TO WATCHLIST</button><p id='description'>An original test story about a lantern maker visiting a quiet mountain village. This paragraph exists only for playback and descriptive-metadata regression tests.</p></div></section><aside><p><b>Genres:</b><span>Adventure, Drama</span></p><p><b>Language:</b><span>Punjabi</span></p><p><b>Release date:</b><span>2025-03-02</span></p></aside></main>";
            html+="<script>window.watchClicks=0;window.trustedWatch=false;function startWatch(event){watchClicks++;trustedWatch=event.isTrusted;history.pushState({},'', '/watch/owned');document.querySelector('#content').innerHTML=\"<button>PLAYER #1 <small>OG</small></button><video id='ownedVideo' muted playsinline onclick='this.play()'></video>\";installMedia();}</script>";
        }else if(url.contains("/watch/"))html+="<main><button>PLAYER #1 <small>OG</small></button><video id='ownedVideo' muted playsinline onclick='this.play()'></video></main>";
        else html+="<main><article><a href='/movie/owned'><img src='https://image.tmdb.org/t/p/w300/owned-test.jpg' alt='Paper Lantern'><h3>Paper Lantern</h3><span>2025 · Adventure</span></a></article></main>";
        html+="<script>function installMedia(){const raw=atob('"+CLIP+"'),bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));const v=document.querySelector('video');if(v){v.src=URL.createObjectURL(new Blob([bytes],{type:'video/mp4'}));v.addEventListener('click',e=>window.trustedMedia=e.isTrusted);}}if(document.querySelector('video'))installMedia();</script>";
        // A real URL navigation keeps Android's committed URL in sync with
        // history.pushState. loadDataWithBaseURL uses a synthetic history URL
        // that stayed on /movie/owned while the DOM moved to /watch/owned.
        fixturePages.put(url,html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        browser.loadUrl(url);
    }
}
