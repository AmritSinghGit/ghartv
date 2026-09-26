package in.ghartv.nova;

/** Debug-only page loader. All production Activity layout/lifecycle/key dispatch
 * runs unchanged; only the network navigation is replaced with local fixture HTML. */
public final class DiscoverFocusHarnessActivity extends FlixMomoActivity {
    public int pageLoads;
    @Override protected FilmHomeView createHomePanel(FilmHomeView.Host host){
        return new FilmHomeView(this,host,(target,url)->{
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(120,180,android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff235c64);target.setImageBitmap(bitmap);
        });
    }
    @Override protected void loadProviderPage(String url){
        pageLoads++;browser.getSettings().setBlockNetworkLoads(true);
        StringBuilder html=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>GharTV focus regression fixture</title><h1>Provider underneath — must not receive Home focus</h1><button id='hidden-button'>Hidden provider button</button>");
        for(int i=0;i<30;i++)html.append("<a style='display:inline-block;width:110px;height:200px' href='/movie/fixture-").append(i).append("'><img src='https://image.tmdb.org/t/p/w300/fixture-").append(i).append(".jpg' alt='Fixture ").append(i).append("'><h3>Fixture ").append(i).append("</h3><span>Owned test image · not a provider result</span></a>");
        browser.loadDataWithBaseURL(url,html.toString(),"text/html","UTF-8",url);
    }
}
