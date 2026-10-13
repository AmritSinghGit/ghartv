package in.ghartv.nova;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Foreground-only bounded start monitor. Unknown frame health never triggers automatic switching. */
final class FilmPlaybackMonitor {
    interface Host {
        WebView page();
        boolean samePlaybackScreen();
        boolean next();
        void sources();
        void cancelPage();
        void changed(String state);
    }
    final FilmStartBudget budget=new FilmStartBudget();
    final LinearLayout panel;
    private final Activity activity;private final Host host;
    private final TextView label;private final ProgressBar progress;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long token, observedAt,readTicket;private boolean reading,releasing,firstReveal;
    private boolean controlsOpen,userDismissed;
    private int supportingErrors;
    private int observations;private String lastMessage="",lastError="";
    private final Runnable pulse=this::read;
    FilmPlaybackMonitor(Activity a,Host h){
        activity=a;host=h;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(TvUi.dp(a,8),TvUi.dp(a,4),TvUi.dp(a,8),TvUi.dp(a,4));
        panel.setBackgroundColor(TvUi.SURFACE);
        label=TvUi.label(a,"",13,TvUi.TEXT,false);panel.addView(label);
        progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);panel.addView(progress,new LinearLayout.LayoutParams(-1,TvUi.dp(a,3)));
        LinearLayout row=new LinearLayout(a);panel.addView(row);
        Button[] bs={TvUi.button(a,"Cancel",false),TvUi.button(a,"Try next",false),TvUi.button(a,"Sources",false)};
        for(Button b:bs){b.setId(View.generateViewId());b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,TvUi.dp(a,38),1));}
        for(int i=0;i<bs.length;i++){bs[i].setNextFocusLeftId(bs[Math.max(0,i-1)].getId());bs[i].setNextFocusRightId(bs[Math.min(bs.length-1,i+1)].getId());bs[i].setNextFocusUpId(bs[i].getId());bs[i].setNextFocusDownId(bs[i].getId());}
        bs[0].setOnClickListener(v->cancel());bs[1].setOnClickListener(v->manualNext());bs[2].setOnClickListener(v->host.sources());
        panel.setVisibility(View.GONE);
    }
    void controlsOpen(boolean open){controlsOpen=open;render();}
    boolean ownsFocus(){return panel.isShown()&&panel.hasFocus();}
    boolean running(){return budget.running();}
    boolean healthy(){return budget.state==FilmStartBudget.State.PLAYING;}
    void begin(){
        if(budget.running())return;
        token++;reading=releasing=false;firstReveal=false;userDismissed=false;observations=0;lastError="";supportingErrors=0;budget.begin(SystemClock.elapsedRealtime());
        render();handler.removeCallbacks(pulse);handler.post(pulse);
    }
    void selected(){
        token++;reading=releasing=false;firstReveal=false;userDismissed=false;
        if(!budget.running())budget.begin(SystemClock.elapsedRealtime());else budget.selected(SystemClock.elapsedRealtime());
        render();handler.removeCallbacks(pulse);handler.postDelayed(pulse,250);
    }
    void leave(){token++;handler.removeCallbacksAndMessages(null);reading=releasing=false;if(budget.running())budget.cancel();panel.setVisibility(View.GONE);}
    void pause(){userDismissed=true;budget.paused();token++;handler.removeCallbacksAndMessages(null);reading=releasing=false;render();}
    void requestFailure(String reason){
        // A failed image, advert or iframe is supporting diagnostic evidence,
        // not proof of a failed movie. Never put it over the video or retry it.
        if(reason==null||reason.isEmpty())return;
        supportingErrors=Math.min(64,supportingErrors+1);
        lastError=reason.substring(0,Math.min(160,reason.length()));
    }
    void cancel(){
        leave();host.changed("CANCELLED");
        // Explicit Cancel returns to saved cards and unloads the provider document.
        host.cancelPage();
    }
    void manualNext(){
        if(releasing)return;
        userDismissed=false;
        if(!budget.running())budget.begin(SystemClock.elapsedRealtime());
        releaseAndNext(false);
    }
    private JSONObject decode(String raw)throws Exception{
        Object v=new JSONTokener(raw==null?"null":raw).nextValue();
        if(!(v instanceof String)||((String)v).length()>16384)throw new IllegalStateException("Observation unavailable");
        return new JSONObject((String)v);
    }
    private void read(){
        if(!budget.running()||releasing||reading||!host.samePlaybackScreen())return;
        WebView page=host.page();if(page==null){budget.unavailable("Browser unavailable");render();return;}
        long now=SystemClock.elapsedRealtime();
        if(now-budget.began>=FilmStartBudget.TOTAL_TIMEOUT_MS){budget.state=FilmStartBudget.State.EXHAUSTED;budget.reason="Start budget exhausted; choose a source manually";render();return;}
        reading=true;final long generation=token,request=++readTicket;final String url=page.getUrl();
        // Callback timeout is also bounded. No endless spinner if the renderer stops responding.
        handler.postDelayed(()->{if(token==generation&&request==readTicket&&reading){reading=false;budget.unavailable("Playback status unavailable. The video may still play; Menu opens controls.");render();}},3000);
        page.evaluateJavascript(FilmPlaybackProbe.sample(token),raw->{
            if(generation!=token||request!=readTicket||page!=host.page()||!host.samePlaybackScreen())return;
            reading=false;observations++;
            try{
                JSONObject d=decode(raw);
                if(!"MEDIA_OBSERVATION".equals(d.optString("state"))||!java.util.Objects.equals(url,d.optString("url"))||!java.util.Objects.equals(url,page.getUrl())){schedule();return;}
                observedAt=SystemClock.elapsedRealtime();
                if(!firstReveal&&(d.optBoolean("observable")||d.optInt("frames")>0)){firstReveal=true;page.evaluateJavascript(FilmPlaybackProbe.reveal(),null);}
                FilmStartBudget.State state=budget.sample(observedAt,d.optBoolean("observable"),d.optInt("frames")>0,
                    d.optBoolean("frame"),d.optBoolean("playing"),d.optBoolean("paused"),d.optDouble("position"),d.optInt("error"),d.optBoolean("protected"));
                render();
                if(state==FilmStartBudget.State.NEXT)releaseAndNext(true);
                else if(state==FilmStartBudget.State.PLAYING){host.changed("FIRST_FRAME_AND_PROGRESS");}
                else schedule();
            }catch(Exception e){schedule();}
        });
    }
    private void schedule(){if(budget.running()){handler.removeCallbacks(pulse);handler.postDelayed(pulse,300);}}
    private void releaseAndNext(boolean automatic){
        final WebView page=host.page();if(page==null||!host.samePlaybackScreen()){leave();return;}
        releasing=true;reading=false;token++;handler.removeCallbacksAndMessages(null);final long generation=token;final String url=page.getUrl();
        handler.postDelayed(()->{if(generation==token&&releasing){releasing=false;budget.unavailable("Previous player could not be confirmed stopped. Cancel before retrying.");render();}},1600);
        page.evaluateJavascript(FilmPlaybackProbe.release(url,automatic),raw->{
            if(generation!=token||page!=host.page()||!host.samePlaybackScreen())return;
            releasing=false;handler.removeCallbacksAndMessages(null);
            try{
                JSONObject d=decode(raw);
                if(!"RELEASED".equals(d.optString("state"))||!d.optBoolean("paused")){budget.unavailable("Player cannot be released safely for automatic switching. Manual controls retained.");render();return;}
                host.changed("PREVIOUS_SOURCE_RELEASED");
                budget.selected(SystemClock.elapsedRealtime());firstReveal=false;
                if(!host.next()){budget.noNext();render();return;}
                render();schedule();
            }catch(Exception e){budget.unavailable("Source release not confirmed. Cancel or use manual controls.");render();}
        });
    }
    private void render(){
        if(userDismissed||budget.state==FilmStartBudget.State.IDLE||budget.state==FilmStartBudget.State.CANCELLED||budget.state==FilmStartBudget.State.PLAYING||budget.state==FilmStartBudget.State.PAUSED){panel.setVisibility(View.GONE);return;}
        long elapsed=Math.max(0,SystemClock.elapsedRealtime()-budget.sourceBegan);
        String text=ReviewDisplay.label()+" · "+(budget.running()?"Starting source · "+(elapsed/1000)+"s · automatic budget "+budget.attempt+"/"+FilmStartBudget.MAX_ATTEMPTS+"\n":"")+budget.reason;
        // Supporting network errors remain in Options / Recent interaction log, not on video.
        if(!text.equals(lastMessage)){lastMessage=text;label.setText(text);}
        progress.setVisibility(budget.running()?View.VISIBLE:View.GONE);
        panel.setVisibility(controlsOpen?View.GONE:View.VISIBLE);
    }
    JSONObject evidence(){
        JSONObject d=new JSONObject();
        try{d.put("engine","embedded_webview");d.put("state",budget.state.name());d.put("attempts",budget.attempt);d.put("observations",observations);
            d.put("first_frame_ms",budget.firstFrameMs<0?JSONObject.NULL:budget.firstFrameMs);d.put("verified_start_ms",budget.proofMs<0?JSONObject.NULL:budget.proofMs);
            d.put("advancing_interval_required_ms",FilmStartBudget.PROGRESS_INTERVAL_MS);d.put("resource_error",lastError);d.put("supporting_resource_errors",supportingErrors);d.put("supporting_error_is_playback_failure",false);d.put("manual_controls_requested",userDismissed);d.put("observation_scope","top_document_only");}catch(Exception ignored){}
        return d;
    }
}
