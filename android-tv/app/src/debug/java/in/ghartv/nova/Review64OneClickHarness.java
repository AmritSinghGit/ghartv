package in.ghartv.nova;
/** Production defaults (provider pages under the pointer, one-click play) over the inherited owned documents. */
public final class Review64OneClickHarness extends Review42HarnessActivity {
    @Override protected boolean pointerFirstBrowsing(){return true;}
    @Override protected boolean simplifiedDiscoverExperience(){return true;}
    public String requested="";
    @Override protected void loadProviderPage(String url){requested=url;super.loadProviderPage(url);}
}
