package in.ghartv.nova;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;

/** One observer per actual player; no second decoder or channel identity in samples. */
@UnstableApi
public final class PlaybackProbe implements AnalyticsListener {
    private final Context context;private final ExoPlayer player;private final Handler handler=new Handler(Looper.getMainLooper());
    private final long tuneStart,attached=SystemClock.elapsedRealtime();private boolean first=false,closed=false;
    private long dropped=0,bandwidth=-1,lastSample=attached;
    private final Runnable tick=new Runnable(){@Override public void run(){if(closed)return;sample();handler.postDelayed(this,60000);}};
    public PlaybackProbe(Context c,ExoPlayer p,long tuneStartedElapsed){context=c.getApplicationContext();player=p;tuneStart=tuneStartedElapsed;p.addAnalyticsListener(this);handler.postDelayed(tick,60000);}
    @Override public void onRenderedFirstFrame(EventTime event,Object output,long renderTimeMs){
        if(first||closed)return;first=true;long now=SystemClock.elapsedRealtime();
        Telemetry.event(context,"first_video_frame",Telemetry.data("tune_to_frame_ms",Math.max(0,now-tuneStart),"player_to_frame_ms",Math.max(0,now-attached)));
        Telemetry.enqueueUpload(context,false);
    }
    @Override public void onDroppedVideoFrames(EventTime time,int count,long elapsed){dropped+=Math.max(0,count);}
    @Override public void onBandwidthEstimate(EventTime time,int ms,long bytes,long estimate){if(estimate>0)bandwidth=estimate;}
    private void sample(){
        try {
            long now=SystemClock.elapsedRealtime();org.json.JSONObject o=Telemetry.data("observation_ms",now-lastSample,"dropped_frames",dropped,"buffer_ahead_ms",player.getTotalBufferedDuration(),"is_playing",player.isPlaying());
            Format f=player.getVideoFormat();if(f!=null){if(f.width>0)o.put("source_width",f.width);if(f.height>0)o.put("source_height",f.height);if(f.averageBitrate>0)o.put("declared_video_bps",f.averageBitrate);if(f.frameRate>0)o.put("source_fps",f.frameRate);}
            if(bandwidth>0)o.put("bandwidth_estimate_bps",bandwidth);
            Telemetry.event(context,"playback_quality_sample",o);Telemetry.enqueueUpload(context,false);dropped=0;lastSample=now;
        } catch(RuntimeException|org.json.JSONException ignored){}
    }
    public void close(){closed=true;handler.removeCallbacks(tick);player.removeAnalyticsListener(this);}
}
