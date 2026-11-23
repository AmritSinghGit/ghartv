package in.ghartv.nova;

/** One viewer-facing identity. The versionName remains technical package metadata. */
final class ReviewDisplay {
    static String label(){return BuildConfig.DEBUG?"Review "+BuildConfig.VERSION_CODE:"GharTV";}
    static String title(){return BuildConfig.DEBUG?"GharTV · "+label():"GharTV";}
    static String films(){return BuildConfig.DEBUG?"GharTV Films · "+label():"GharTV · Films";}
    static String details(){return title()+" · FlixMomo";}
    static String technical(){return title()+"\nBuild "+BuildConfig.VERSION_CODE+
        " · "+BuildConfig.VERSION_NAME;}
}
