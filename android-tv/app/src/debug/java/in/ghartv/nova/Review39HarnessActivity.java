package in.ghartv.nova;

/** Local, owned fixture pages. Production lifecycle and input routing are not overridden. */
public final class Review39HarnessActivity extends FlixMomoActivity {
    public int pageLoads;
    private void image(android.widget.ImageView target){android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(156,234,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);android.graphics.Paint p=new android.graphics.Paint();p.setColor(0xff12576b);c.drawRect(0,0,156,234,p);p.setColor(0xff89edc4);p.setTextSize(15);c.drawText("OWNED",36,92,p);c.drawText("TEST IMAGE",20,125,p);target.setImageBitmap(b);}
    @Override protected FilmHomeView createHomePanel(FilmHomeView.Host host){return new FilmHomeView(this,host,(target,url)->image(target));}
    @Override protected FilmDetailView createDetailPanel(FilmDetailView.Host host){return new FilmDetailView(this,host){@Override void loadArtwork(android.widget.ImageView v,String u){image(v);}};}
    @Override protected void loadProviderPage(String url){
        pageLoads++;browser.getSettings().setBlockNetworkLoads(true);
        String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font:18px sans-serif;background:#121a23;color:white}button,a{display:inline-block;padding:16px;margin:12px}img{width:120px;height:180px}</style><title>LOCAL OWNED REVIEW39 TEST</title>";
        if(url.contains("/movie/"))html+="<main><article><h1>The Glass Mountain</h1><img src='https://image.tmdb.org/t/p/w300/owned-fixture.jpg'><p>A locally written test description of an imaginary journey through a mountain village. This is a controlled test document, not a live-provider movie or an assertion of film availability.</p><button id='watch' onclick=\"document.title=event.isTrusted?'WATCH_TRUSTED':'UNTRUSTED'\">WATCH NOW</button><button id='save'>ADD TO WATCHLIST</button><p><strong>Duration:</strong> 1 hr 42 min</p><p><strong>Language:</strong> English</p></article></main>";
        else {html+="<main><h1>LOCAL OWNED FIXTURE RESULTS</h1>";for(int i=0;i<15;i++)html+="<article style='display:inline-block'><a href='/movie/fixture-"+i+"'><img src='https://image.tmdb.org/t/p/w300/owned-fixture.jpg' alt='The Glass Mountain'><h3>The Glass Mountain "+i+"</h3><span>Owned fixture · Adventure</span></a></article>";html+="</main>";}
        browser.loadDataWithBaseURL(url,html,"text/html","UTF-8",url);
    }
}
