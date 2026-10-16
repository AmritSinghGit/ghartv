package in.ghartv.nova;

/** Owned provider pages with the production Review56 search-first browser flow and no live network/Tor. */
public final class Review56SimpleHarnessActivity extends Review42HarnessActivity {
    @Override protected boolean pointerFirstBrowsing(){return true;}
    @Override protected boolean simplifiedDiscoverExperience(){return true;}
    public String requested="";
    @Override protected void loadProviderPage(String url){requested=url;super.loadProviderPage(url);}
}
