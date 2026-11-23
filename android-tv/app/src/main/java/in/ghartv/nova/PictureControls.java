package in.ghartv.nova;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
/** Explicit source preference, not artificial detail or a second rendering pipeline. */
@UnstableApi public final class PictureControls {
 private PictureControls(){}
 public static void show(Activity a,PlayerView view){
  if(!(view.getPlayer() instanceof ExoPlayer))return;ExoPlayer p=(ExoPlayer)view.getPlayer();
  TextView text=TvUi.label(a,"",15,TvUi.TEXT,false);int pad=TvUi.dp(a,24);text.setPadding(pad,pad,pad,pad);
  Handler h=new Handler(Looper.getMainLooper());
  AlertDialog d=new AlertDialog.Builder(a).setTitle("Source quality · live stats")
   .setView(text).setPositiveButton("Close",null).setNeutralButton("Source preference",(x,y)->{
    new AlertDialog.Builder(a).setTitle("Quality for this playback session")
     .setItems(new String[]{"Auto adaptive (recommended)","Highest supported source (may buffer)","Data saver (prefer 480p)"},(u,w)->{
      if(p.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS))p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon().setForceLowestBitrate(false).setForceHighestSupportedBitrate(w==1).setMaxVideoSize(w==2?854:Integer.MAX_VALUE,w==2?480:Integer.MAX_VALUE).setMaxVideoBitrate(w==2?1000000:Integer.MAX_VALUE).build());
     }).setNegativeButton("Cancel",null).show();
   }).setNegativeButton("TV hardware",(x,y)->HardwareDiagnostics.show(a)).create();
  Runnable tick=new Runnable(){public void run(){if(!d.isShowing()||a.isFinishing())return;try{Format f=p.getVideoFormat();text.setText("Source: "+(f==null?"not reported":f.width+" × "+f.height)+"\nDeclared bitrate: "+(f!=null&&f.averageBitrate>0?f.averageBitrate+" bps":"not reported")+"\nBuffer ahead: "+p.getTotalBufferedDuration()+" ms\nPlaying: "+p.isPlaying()+"\n\nFraming changes do not create native HD/4K. AI super-resolution is not running. The TV may apply its own device-dependent picture processing. This panel refreshes once per second.");}catch(RuntimeException e){text.setText("Playback changed; reopen this panel.");}h.postDelayed(this,1000);}};
  d.setOnDismissListener(x->h.removeCallbacks(tick));d.show();h.post(tick);
 }
}
