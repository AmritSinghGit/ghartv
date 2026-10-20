package in.ghartv.nova;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Public IPTV player. It never uses Jio credentials and only uses the shared app-local
 * Tor tunnel after direct and secure-DNS route failures. */
@UnstableApi
public class IptvPlayerActivity extends Activity {
    public static final String EXTRA_CHANNEL_JSON="iptv_channel_json";
    private final Handler main=new Handler(Looper.getMainLooper());
    private PlayerView playerView;
    private ExoPlayer player;
    private TextView status,title,source;
    private ProgressBar loading;
    private Button retry;
    private IptvChannel channel;
    private Runnable firstFrameDeadline;
    volatile boolean firstFrameObserved;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);TvUi.immersive(this);
        try{channel=IptvChannel.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_CHANNEL_JSON)));}catch(Exception e){Toast.makeText(this,"Public channel data is missing",Toast.LENGTH_LONG).show();finish();return;}
        if(!IptvPlaylistParser.isPublicHttps(channel.streamUrl)){Toast.makeText(this,"Only secure public IPTV streams are opened",Toast.LENGTH_LONG).show();finish();return;}
        setContentView(build());Telemetry.screen(this,"iptv_player");prepare();
    }
    protected String resolvedStreamUrl(IptvChannel value){return value.streamUrl;}
    private View build(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);playerView=new PlayerView(this);playerView.setUseController(true);root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(TvUi.dp(this,14),TvUi.dp(this,8),TvUi.dp(this,14),TvUi.dp(this,8));top.setBackgroundColor(Color.argb(205,1,8,13));
        Button back=TvUi.button(this,"← IPTV",false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(TvUi.dp(this,104),TvUi.dp(this,42)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);title=TvUi.label(this,channel.name,17,TvUi.TEXT,true);source=TvUi.label(this,"IPTV-org · "+channel.group+" · "+IptvNetwork.routeLabel(),10.5f,TvUi.CYAN,false);copy.addView(title,new LinearLayout.LayoutParams(-1,TvUi.dp(this,24)));copy.addView(source,new LinearLayout.LayoutParams(-1,TvUi.dp(this,20)));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,TvUi.dp(this,46),1);cp.leftMargin=TvUi.dp(this,12);top.addView(copy,cp);
        retry=TvUi.button(this,"Retry",false);retry.setOnClickListener(v->prepare());top.addView(retry,new LinearLayout.LayoutParams(TvUi.dp(this,94),TvUi.dp(this,42)));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,TvUi.dp(this,62),Gravity.TOP);root.addView(top,tp);
        loading=new ProgressBar(this);root.addView(loading,new FrameLayout.LayoutParams(TvUi.dp(this,46),TvUi.dp(this,46),Gravity.CENTER));status=TvUi.label(this,"Connecting to the public stream…",12,TvUi.TEXT,true);status.setGravity(Gravity.CENTER);status.setBackgroundColor(Color.argb(190,0,0,0));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,TvUi.dp(this,42),Gravity.BOTTOM);sp.setMargins(TvUi.dp(this,20),0,TvUi.dp(this,20),TvUi.dp(this,16));root.addView(status,sp);return root;
    }
    private void prepare(){
        releasePlayer();firstFrameObserved=false;retry.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);status.setText("Connecting to the public stream…");
        Map<String,String> headers=new LinkedHashMap<>();headers.put("User-Agent",channel.userAgent.isEmpty()?"GharTV-IPTV/"+BuildConfig.VERSION_NAME:channel.userAgent);if(!channel.referrer.isEmpty())headers.put("Referer",channel.referrer);
        source.setText("IPTV-org · "+channel.group+" · "+IptvNetwork.routeLabel());
        OkHttpDataSource.Factory http=new OkHttpDataSource.Factory(IptvNetwork.media(this)).setUserAgent(headers.get("User-Agent")).setDefaultRequestProperties(headers);
        DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(data)).build();playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){firstFrameObserved=true;if(firstFrameDeadline!=null)main.removeCallbacks(firstFrameDeadline);loading.setVisibility(View.GONE);retry.setVisibility(View.GONE);source.setText("IPTV-org · "+channel.group+" · "+IptvNetwork.routeLabel());status.setText("Playing public IPTV · availability is controlled by the stream host");Telemetry.event(IptvPlayerActivity.this,"iptv_first_frame",Telemetry.data("protocol",protocol(channel.streamUrl),"route",IptvNetwork.routeLabel()));}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_BUFFERING&&!firstFrameObserved){loading.setVisibility(View.VISIBLE);status.setText("Buffering public IPTV…");}else if(state==Player.STATE_ENDED){loading.setVisibility(View.GONE);retry.setVisibility(View.VISIBLE);status.setText("This public stream ended. Retry or return to IPTV.");}}
            @Override public void onPlayerError(PlaybackException error){failed(error);}
        });
        String stream=resolvedStreamUrl(channel);MediaItem.Builder item=new MediaItem.Builder().setUri(stream);String lower=stream.toLowerCase(Locale.ROOT);if(lower.contains(".m3u8"))item.setMimeType(MimeTypes.APPLICATION_M3U8);else if(lower.contains(".mpd"))item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());player.prepare();player.play();Telemetry.event(this,"iptv_play_request",Telemetry.data("protocol",protocol(stream)));
        firstFrameDeadline=()->{if(!firstFrameObserved){releasePlayer();loading.setVisibility(View.GONE);retry.setVisibility(View.VISIBLE);source.setText("IPTV-org · "+channel.group+" · "+IptvNetwork.routeLabel());status.setText("No first picture arrived after direct, secure DNS and Tor recovery. This public stream may be offline, regional or slow.");Telemetry.event(this,"iptv_play_failed",Telemetry.data("reason","FIRST_FRAME_TIMEOUT","route",IptvNetwork.routeLabel()));}};main.postDelayed(firstFrameDeadline,90_000L);
    }
    private void failed(Throwable error){if(firstFrameDeadline!=null)main.removeCallbacks(firstFrameDeadline);loading.setVisibility(View.GONE);retry.setVisibility(View.VISIBLE);String code=NetworkFailure.classify(error);status.setText(message(code));Telemetry.error(this,"iptv_play_failed",error,Telemetry.data("reason",code,"protocol",protocol(channel.streamUrl)));}
    private String message(String code){
        if("DNS_UNAVAILABLE".equals(code))return "The public stream host could not be resolved. Check the connection or choose another channel.";
        if("TIMED_OUT".equals(code)||"HTTP_PROVIDER_TIMEOUT".equals(code))return "The public stream timed out. Retry later or choose another channel.";
        if("TLS_FAILED".equals(code))return "The public stream failed secure certificate validation. GharTV will not bypass it.";
        if("CONNECTION_FAILED".equals(code))return "The public stream host could not be reached.";
        if("HTTP_AUTH_REQUIRED".equals(code))return "The public stream host requires its own authorization. This is not a JioTV session; GharTV will not add credentials or bypass the host's access rules.";
        if("HTTP_ACCESS_DENIED".equals(code))return "The public stream host denied access (HTTP 403). It may be restricted; GharTV will not bypass the restriction.";
        if("HTTP_STREAM_UNAVAILABLE".equals(code))return "The public stream host reports that this stream is unavailable (HTTP 404/410). Choose another channel or retry later.";
        if("HTTP_RATE_LIMITED".equals(code))return "The public stream host is rate-limiting requests (HTTP 429). Wait before retrying.";
        if("HTTP_PROVIDER_ERROR".equals(code))return "The public stream provider returned a server error (HTTP 5xx). Retry later or choose another channel.";
        if("HTTP_PROVIDER_REJECTED".equals(code))return "The public stream provider rejected the request. The source may require unsupported access or request details.";
        return "No first picture was confirmed, or the public stream returned an unsupported response. The stream may be offline or unsupported.";
    }
    private String protocol(String url){String lower=url==null?"":url.toLowerCase(Locale.ROOT);return lower.contains(".mpd")?"dash":lower.contains(".m3u8")?"hls":"progressive";}
    private void releasePlayer(){if(firstFrameDeadline!=null){main.removeCallbacks(firstFrameDeadline);firstFrameDeadline=null;}if(player!=null){playerView.setPlayer(null);player.release();player=null;}}
    @Override protected void onStop(){if(player!=null)player.pause();super.onStop();}
    @Override protected void onDestroy(){releasePlayer();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getAction()==KeyEvent.ACTION_UP&&event.getKeyCode()==KeyEvent.KEYCODE_BACK){finish();return true;}return super.dispatchKeyEvent(event);}
}
