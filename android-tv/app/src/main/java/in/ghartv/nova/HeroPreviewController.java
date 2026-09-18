package in.ghartv.nova;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Automatic muted preview belongs only to the focused channel, never to a toolbar. */
@UnstableApi
public final class HeroPreviewController {
    interface Resolver { PlaybackInfo fetch(Channel channel) throws Exception; void cancel(); }
    private final Activity activity;
    private final PlayerView playerView;
    private final ImageView poster;
    private final ProgressBar loading;
    private final TextView status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Resolver resolver;
    private final PreviewGate gate=new PreviewGate();
    private Channel selected;
    private ExoPlayer player;
    private Future<?> request;
    private Runnable pendingStart,deadline,autoStop;
    private boolean released;

    public HeroPreviewController(Activity a,ChannelRepository repository,PlayerView view,
            ImageView logo,ProgressBar spinner,TextView label){
        this(a,view,logo,spinner,label,new Resolver(){
            private final JioApiClient api=new JioApiClient(a);
            public PlaybackInfo fetch(Channel c)throws Exception{return api.fetchPlayback(c);}
            public void cancel(){api.cancelPlayback();}
        });
    }
    // Package-private injection exercises the SAME controller with local media on an Android test device.
    HeroPreviewController(Activity a,PlayerView view,ImageView logo,ProgressBar spinner,
            TextView label,Resolver resolver){
        activity=a;playerView=view;poster=logo;loading=spinner;status=label;this.resolver=resolver;
        playerView.setUseController(false);playerView.setKeepScreenOn(false);
        playerView.setFocusable(false);playerView.setFocusableInTouchMode(false);
        playerView.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        resetVisual();idleCopy();
    }
    public void select(Channel channel){
        selected=channel;
        String key=channel==null?"":safe(channel.id);
        if(gate.select(key)){cancelWork();resetVisual();}
        schedule();
    }
    public void focused(boolean value){
        boolean changed=gate.focus(value);
        changed=gate.enabled(PlaybackComfort.autoPreview(activity))||changed;
        if(changed){cancelWork();resetVisual();}
        schedule();
    }
    private void schedule(){
        if(released)return;
        if(gate.enabled(PlaybackComfort.autoPreview(activity))){cancelWork();resetVisual();}
        long token=gate.arm();
        if(token<0){if(gate.phase()==PreviewGate.Phase.IDLE)idleCopy();return;}
        copy("Preview starts when you pause here",TvUi.MUTED);
        pendingStart=()->{pendingStart=null;if(gate.begin(token))start(token);};
        main.postDelayed(pendingStart,PreviewGate.FOCUS_DELAY_MS);
    }
    private void start(long token){
        Channel channel=selected;
        if(channel==null||activity.isFinishing()||released)return;
        // Create the video surface UNDER the poster; GONE cannot deliver a first frame.
        playerView.setVisibility(View.VISIBLE);poster.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);copy("Starting preview…",TvUi.MUTED);
        deadline=()->finish(token,true,"Preview unavailable · OK still opens live TV");
        main.postDelayed(deadline,PreviewGate.FIRST_FRAME_BUDGET_MS);
        Telemetry.event(activity,"guide_preview_request",Telemetry.data("automatic",true,
                "language",channel.language,"category",channel.category));
        request=executor.submit(()->{
            try{
                PlaybackInfo info=resolver.fetch(channel);
                main.post(()->{
                    if(!gate.current(token)||released||activity.isFinishing())return;
                    info.normalizeAliases();
                    if(info.authRequired||info.subscriptionRequired||info.unavailable||safe(info.streamUrl).isEmpty()){
                        finish(token,true,info.subscriptionRequired?"Subscription required · OK for details":
                            info.authRequired?"Reconnect your account to preview":"Preview unavailable · OK still opens live TV");return;
                    }
                    try{prepare(info,token);}
                    catch(RuntimeException e){finish(token,true,"Preview unavailable · OK still opens live TV");}
                });
            }catch(Exception error){
                main.post(()->{if(gate.current(token))finish(token,true,"Preview unavailable · OK still opens live TV");});
            }
        });
    }
    private void prepare(PlaybackInfo info,long token){
        Map<String,String> headers=jsonMap(info.streamHeaders);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent(headers.getOrDefault("User-Agent","GharTV-Jio-Live/"+BuildConfig.VERSION_NAME))
            .setAllowCrossProtocolRedirects(false).setConnectTimeoutMs(4000).setReadTimeoutMs(4000)
            .setDefaultRequestProperties(headers);
        player=new ExoPlayer.Builder(activity)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(activity,http))).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
            .setMaxVideoSize(640,360).setForceLowestBitrate(true).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).build());
        player.setVolume(0f);playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){
                if(!gate.firstFrame(token))return;
                if(deadline!=null)main.removeCallbacks(deadline);deadline=null;
                poster.setVisibility(View.GONE);loading.setVisibility(View.GONE);
                copy("Muted preview · OK to watch",TvUi.MINT);
                Telemetry.event(activity,"guide_preview_ready",Telemetry.data("automatic",true,"drm",info.drm));
                autoStop=()->finish(token,false,"OK to watch live");
                main.postDelayed(autoStop,PreviewGate.VISIBLE_PREVIEW_MS);
            }
            @Override public void onPlaybackStateChanged(int state){
                if(!gate.current(token))return;
                if(state==Player.STATE_ENDED)finish(token,false,"OK to watch live");
                // READY is not proof of a rendered picture. Buffering never renews the budget.
            }
            @Override public void onPlayerError(PlaybackException error){
                finish(token,true,"Preview unavailable · OK still opens live TV");
            }
        });
        MediaItem.Builder item=new MediaItem.Builder().setUri(info.streamUrl);
        String mime=safe(info.mimeType).toLowerCase(Locale.ROOT),uri=safe(info.streamUrl).toLowerCase(Locale.ROOT);
        if(mime.contains("dash")||uri.contains(".mpd"))item.setMimeType(MimeTypes.APPLICATION_MPD);
        else if(mime.contains("mpegurl")||uri.contains(".m3u8"))item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if(info.drm&&!safe(info.licenseUrl).isEmpty())item.setDrmConfiguration(
            new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(info.licenseUrl)
                .setLicenseRequestHeaders(jsonMap(info.licenseHeaders)).setMultiSession(false).build());
        player.setMediaItem(item.build());player.prepare();player.play();
    }
    private void finish(long token,boolean error,String message){
        if(!gate.finish(token,error))return;
        cancelWork();resetVisual();copy(message,TvUi.MUTED);
    }
    private void cancelWork(){
        if(pendingStart!=null)main.removeCallbacks(pendingStart);
        if(deadline!=null)main.removeCallbacks(deadline);
        if(autoStop!=null)main.removeCallbacks(autoStop);
        pendingStart=null;deadline=null;autoStop=null;
        resolver.cancel();if(request!=null){request.cancel(true);request=null;}
        if(player!=null){playerView.setPlayer(null);player.release();player=null;}
    }
    private void resetVisual(){loading.setVisibility(View.GONE);playerView.setVisibility(View.GONE);poster.setVisibility(View.VISIBLE);}
    private void copy(String message,int colour){status.setTextColor(colour);status.setText(message);}
    private void idleCopy(){copy(selected==null?"Choose a channel":PlaybackComfort.autoPreview(activity)?
        "Highlight a channel · OK to watch":"Previews off in Playback & comfort · OK to watch",TvUi.MUTED);}
    public void stop(boolean ownerRequested){gate.focus(false);gate.cancel();cancelWork();resetVisual();idleCopy();}
    public void release(){released=true;stop(false);executor.shutdownNow();}
    PreviewGate.Phase phase(){return gate.phase();}
    boolean muted(){return player==null||(player.getVolume()==0f&&player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO));}
    private static String safe(String s){return s==null?"":s;}
    private static Map<String,String> jsonMap(JSONObject json){
        Map<String,String> map=new HashMap<>();if(json==null)return map;
        java.util.Iterator<String> keys=json.keys();while(keys.hasNext()){String k=keys.next();map.put(k,json.optString(k,""));}return map;
    }
}
