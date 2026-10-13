package in.ghartv.nova;

/** One viewer-facing identity. The versionName remains technical package metadata. */
final class ReviewDisplay {
    static String label(){return "Review " + BuildConfig.VERSION_CODE;}
    static String title(){return "GharTV · " + label();}
    static String films(){return "GharTV Films · " + label();}
    static String details(){return title() + " · FlixMomo";}
    static String technical(){return label() + "\nPackage version: " + BuildConfig.VERSION_NAME;}
}
