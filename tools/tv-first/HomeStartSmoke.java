public final class HomeStartSmoke {
    static int n = 0;
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); n++; }
    public static void main(String[] a) {
        check(in.ghartv.nova.HomeStart.startView(5, 3).equals("Recent"), "history wins over favourites");
        check(in.ghartv.nova.HomeStart.startView(1, 0).equals("Recent"), "one watched channel is enough");
        check(in.ghartv.nova.HomeStart.startView(0, 2).equals("Favourites"), "favourites when no history");
        check(in.ghartv.nova.HomeStart.startView(0, 0).equals("All channels"), "full guide on a fresh install");
        check(in.ghartv.nova.HomeStart.startView(-1, -1).equals("All channels"), "negative counts are empty");
        System.out.println("HOME_START=" + n + "_PASS");
    }
}
