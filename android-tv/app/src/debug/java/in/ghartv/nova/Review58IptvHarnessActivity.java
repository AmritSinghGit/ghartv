package in.ghartv.nova;

/** Controlled public-playlist fixture using the production IPTV screen. */
public final class Review58IptvHarnessActivity extends IptvActivity {
    public IptvChannel opened;
    @Override protected IptvRepository createRepository(){
        return new IptvRepository(this){
            @Override public Result load(boolean force){
                String raw="#EXTM3U\n"
                    +"#EXTINF:-1 tvg-id=\"news.one\" tvg-logo=\"https://images.example/news.png\" group-title=\"News\",Owned News\nhttps://media.example/news.m3u8\n"
                    +"#EXTINF:-1 tvg-id=\"films.one\" group-title=\"Movies\",Owned Films\nhttps://media.example/films.m3u8\n"
                    +"#EXTINF:-1 tvg-id=\"music.one\" group-title=\"Music\",Owned Music\nhttps://media.example/music.m3u8\n";
                IptvPlaylistParser.Result parsed=IptvPlaylistParser.parse(raw);
                return new Result(parsed.channels,false,System.currentTimeMillis(),parsed,"");
            }
        };
    }
    @Override protected void openChannel(IptvChannel channel){opened=channel;}
}
