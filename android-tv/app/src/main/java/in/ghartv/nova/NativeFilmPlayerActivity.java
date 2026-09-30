package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

/** Native playback for an explicitly supplied supported media link.
 * No iframe inspection, browser network capture, cookie copying or stream extraction.
 * This screen is not proof that any particular provider offers a native media link.
 */
public final class NativeFilmPlayerActivity extends Activity {
    static final String EXTRA_MEDIA="native_media_uri";
    static final String CHECK_URI="asset:///ghartv-native-check.mp4";
    private ExoPlayer player;
    private PlayerView video;
    private LinearLayout controls;
    private TextView status,timeline;
    private Button pause;
    private Uri source;
    private boolean playing=true,firstFrame;
    private long position;
    private float speed=1f;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){
        if(player!=null){long duration=player.getDuration();timeline.setText(format(player.getCurrentPosition())+" / "+(duration==C.TIME_UNSET?"live / unknown":format(duration)));
            pause.setText(player.isPlaying()?"Pause":"Play");}
        handler.postDelayed(this,500);
    }};
    private static String format(long ms){long s=Math.max(0,ms)/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",s/60,s%60);}
    static Intent checkIntent(Activity a){return new Intent(a,NativeFilmPlayerActivity.class).putExtra(EXTRA_MEDIA,CHECK_URI);}
    static boolean supported(Uri u){
        if(u==null)return false;
        if(CHECK_URI.equals(u.toString()))return true;
        // Only explicit direct links on an already registered provider origin.
        // No broad CDN trust, arbitrary URLs or discovered request interception.
        if(!FilmProviderPolicy.allowed(u)||u.getFragment()!=null||u.toString().length()>4096)return false;
        String p=u.getPath();return p!=null&&p.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|webm|m3u8|mpd)$");
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);TvUi.immersive(this);
        try{source=Uri.parse(getIntent().getStringExtra(EXTRA_MEDIA));}catch(Exception e){finish();return;}
        if(!supported(source)){finish();return;}
        if(saved!=null){position=saved.getLong("position",0);playing=saved.getBoolean("playing",false);speed=saved.getFloat("speed",1f);}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff000000);
        video=new PlayerView(this);video.setUseController(false);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(18),dp(10),dp(18),dp(10));top.setBackgroundColor(0xdc061923);
        top.addView(TvUi.label(this,"GharTV native player",22,TvUi.TEXT,true));
        status=TvUi.label(this,CHECK_URI.equals(source.toString())?"Owned test video · verifies the native engine, not FlixMomo playback":"Explicit provider media link · native Media3 engine",14,TvUi.MINT,false);top.addView(status);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(14),dp(10),dp(14),dp(10));controls.setBackgroundColor(0xee061923);
        timeline=TvUi.label(this,"Preparing media…",16,TvUi.TEXT,false);controls.addView(timeline);
        LinearLayout row=new LinearLayout(this);controls.addView(row);
        add(row,"−10 sec",()->seekBy(-10000));pause=add(row,"Play",this::toggle);add(row,"+10 sec",()->seekBy(10000));add(row,"Speed",this::chooseSpeed);add(row,"Hide",()->controls.setVisibility(View.GONE));add(row,"Back",this::finish);
        for(int i=0;i<row.getChildCount();i++){View b=row.getChildAt(i);b.setId(View.generateViewId());}
        for(int i=0;i<row.getChildCount();i++){View b=row.getChildAt(i);b.setNextFocusLeftId(row.getChildAt(Math.max(0,i-1)).getId());b.setNextFocusRightId(row.getChildAt(Math.min(row.getChildCount()-1,i+1)).getId());b.setNextFocusUpId(b.getId());b.setNextFocusDownId(b.getId());}
        root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(root);pause.requestFocus();
    }
    private int dp(int n){return TvUi.dp(this,n);}
    private Button add(LinearLayout row,String text,Runnable action){Button b=TvUi.button(this,text,false);b.setTextSize(16);row.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));b.setOnClickListener(v->action.run());return b;}
    private void prepare(){
        if(player!=null||source==null)return;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(12000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(false);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http))).build();video.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){firstFrame=true;}
            @Override public void onPlayerError(PlaybackException error){status.setText("Native playback failed: "+error.getErrorCodeName()+". Return to the provider; no browser credentials or certificates were changed.");controls.setVisibility(View.VISIBLE);pause.requestFocus();}
        });
        player.setMediaItem(MediaItem.fromUri(source));player.seekTo(Math.max(0,position));player.setPlaybackSpeed(speed);player.prepare();player.setPlayWhenReady(playing);handler.post(tick);
    }
    private void toggle(){if(player==null)return;if(player.getPlaybackState()==Player.STATE_ENDED)player.seekTo(0);player.setPlayWhenReady(!player.getPlayWhenReady());}
    private void seekBy(long delta){if(player==null)return;if(!player.isCurrentMediaItemSeekable()){status.setText("This media does not currently permit seeking.");return;}long end=player.getDuration(),p=Math.max(0,player.getCurrentPosition()+delta);if(end!=C.TIME_UNSET)p=Math.min(end,p);player.seekTo(p);}
    private void chooseSpeed(){String[] labels={"0.5×","0.75×","1×","1.25×","1.5×","2×"};float[] values={.5f,.75f,1f,1.25f,1.5f,2f};new AlertDialog.Builder(this).setTitle("Native playback speed").setItems(labels,(d,i)->{speed=values[i];if(player!=null)player.setPlaybackSpeed(speed);}).setNegativeButton("Cancel",null).show();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int k=event.getKeyCode();
        if(k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY||k==KeyEvent.KEYCODE_MEDIA_PAUSE){if(event.getAction()==KeyEvent.ACTION_UP&&player!=null){if(k==KeyEvent.KEYCODE_MEDIA_PLAY)player.play();else if(k==KeyEvent.KEYCODE_MEDIA_PAUSE)player.pause();else toggle();}return true;}
        if(k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||k==KeyEvent.KEYCODE_MEDIA_REWIND){if(event.getAction()==KeyEvent.ACTION_UP)seekBy(k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?10000:-10000);return true;}
        if(k==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP){controls.setVisibility(View.VISIBLE);pause.requestFocus();}return true;}
        if(controls.getVisibility()!=View.VISIBLE&&(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN)){if(event.getAction()==KeyEvent.ACTION_UP){controls.setVisibility(View.VISIBLE);pause.requestFocus();}return true;}
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume(){super.onResume();prepare();}
    @Override protected void onPause(){handler.removeCallbacks(tick);if(player!=null){position=player.getCurrentPosition();playing=player.getPlayWhenReady();speed=player.getPlaybackParameters().speed;video.setPlayer(null);player.release();player=null;}super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){if(player!=null){position=player.getCurrentPosition();playing=player.getPlayWhenReady();speed=player.getPlaybackParameters().speed;}b.putLong("position",position);b.putBoolean("playing",playing);b.putFloat("speed",speed);super.onSaveInstanceState(b);}
}
