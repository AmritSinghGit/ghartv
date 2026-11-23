package in.ghartv.nova;

/** Which guide view a fresh launch opens on: the family's own list first, the full numbered guide only when there is no history yet. */
public final class HomeStart {
    private HomeStart() {}

    public static String startView(int recentCount, int favouriteCount) {
        if (recentCount > 0) return ChannelIndex.VIEW_RECENT;
        if (favouriteCount > 0) return ChannelIndex.VIEW_FAVOURITES;
        return ChannelIndex.VIEW_ALL;
    }
}
