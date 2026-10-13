package in.ghartv.nova;
/** Uses the real production pointer-first flow on the inherited owned documents. */
public final class Review51PointerHarness extends Review42HarnessActivity {
    @Override protected boolean pointerFirstBrowsing(){return true;}
    public String requested="";
    @Override protected void loadProviderPage(String url){requested=url;super.loadProviderPage(url);}
}
