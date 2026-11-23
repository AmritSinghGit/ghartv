package in.ghartv.nova;
/** Uses the real production pointer-first flow on the inherited owned documents. */
public final class Review51PointerHarness extends Review42HarnessActivity {
    @Override protected boolean pointerFirstBrowsing(){return true;}
    // These tests drive the manual pointer flow step by step; Review64OneClickPlayTest covers one-click.
    @Override protected boolean oneClickPlayEnabled(){return false;}
    public String requested="";
    @Override protected void loadProviderPage(String url){requested=url;super.loadProviderPage(url);}
}
