package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;

/** Local-only comfort preferences. Only physical input resets inactivity. No cloud polling. */
public final class PlaybackComfort {
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("ghartv_comfort_v1",Context.MODE_PRIVATE);}
    public static boolean autoPreview(Context c){return prefs(c).getBoolean("auto_preview",false);}
    public static boolean light(Context c){
        String p=prefs(c).getString("quality","Auto");
        ActivityManager m=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        return "Light".equals(p)||("Auto".equals(p)&&m!=null&&(m.isLowRamDevice()||m.getMemoryClass()<=192));
    }
    public static void settings(Activity a){
        SharedPreferences p=prefs(a);LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);
        int pad=TvUi.dp(a,22);box.setPadding(pad,pad,pad,pad);
        Switch preview=new Switch(a);preview.setText("Muted preview on focused channel (off is lightest)");preview.setChecked(autoPreview(a));box.addView(preview);
        Switch idle=new Switch(a);idle.setText("Ask: Still watching?");idle.setChecked(p.getBoolean("idle_enabled",true));box.addView(idle);
        TextView label=new TextView(a);label.setText("Minutes without input (1–240)");box.addView(label);
        EditText minutes=new EditText(a);minutes.setInputType(InputType.TYPE_CLASS_NUMBER);minutes.setText(String.valueOf(p.getInt("idle_minutes",60)));box.addView(minutes);
        Switch guide=new Switch(a);guide.setText("Return to guide instead of Resume screen");guide.setChecked(p.getBoolean("return_guide",false));box.addView(guide);
        TextView qualityLabel=new TextView(a);qualityLabel.setText("Playback profile (takes effect next channel)");box.addView(qualityLabel);
        Spinner profile=new Spinner(a);String[] choices={"Auto","Light","Quality"};profile.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,choices));
        for(int i=0;i<choices.length;i++)if(choices[i].equals(p.getString("quality","Auto")))profile.setSelection(i);box.addView(profile);
        TextView note=new TextView(a);note.setText("Light prefers up to 720p; source quality cannot be invented. Settings stay on this device.");box.addView(note);
        AlertDialog d=new AlertDialog.Builder(a).setTitle("Playback & comfort").setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            int n;try{n=Integer.parseInt(minutes.getText().toString());}catch(Exception e){minutes.setError("Enter 1–240");return;}
            if(n<1||n>240){minutes.setError("Enter 1–240");return;}
            p.edit().putBoolean("auto_preview",preview.isChecked()).putBoolean("idle_enabled",idle.isChecked()).putInt("idle_minutes",n).putBoolean("return_guide",guide.isChecked()).putString("quality",choices[profile.getSelectedItemPosition()]).apply();d.dismiss();
        }));d.show();
    }
    private final Activity a;private final Runnable stop,play,guide;private final Handler h=new Handler(Looper.getMainLooper());
    private long last=SystemClock.elapsedRealtime();private boolean active=false,rest=false;private AlertDialog dialog;
    public PlaybackComfort(Activity a,Runnable stop,Runnable play,Runnable guide){this.a=a;this.stop=stop;this.play=play;this.guide=guide;}
    public boolean resting(){return rest;}
    public void touch(){if(rest||dialog!=null)return;last=SystemClock.elapsedRealtime();schedule();}
    public void resume(){active=true;if(!rest){last=SystemClock.elapsedRealtime();schedule();}}
    public void pause(){active=false;h.removeCallbacks(check);h.removeCallbacks(expire);if(dialog!=null&&!rest){dialog.dismiss();dialog=null;}}
    public void close(){pause();if(dialog!=null){dialog.dismiss();dialog=null;}}
    private void schedule(){h.removeCallbacks(check);if(!active||rest||!prefs(a).getBoolean("idle_enabled",true))return;long wait=Math.max(1,Math.min(240,prefs(a).getInt("idle_minutes",60)))*60000L;h.postDelayed(check,Math.max(1,wait-(SystemClock.elapsedRealtime()-last)));}
    private final Runnable expire=this::sleep;
    private final Runnable check=this::prompt;
    private void prompt(){
        if(!active||rest||a.isFinishing())return;
        dialog=new AlertDialog.Builder(a).setTitle("Still watching?").setMessage("No input for a while. Streaming will stop in 30 seconds unless you choose Keep watching.")
            .setPositiveButton("Keep watching",(d,w)->{dialog=null;h.removeCallbacks(expire);touch();})
            .setNegativeButton("Rest now",(d,w)->sleep()).setCancelable(false).show();h.postDelayed(expire,30000L);
    }
    private void sleep(){h.removeCallbacks(expire);if(!active||a.isFinishing())return;rest=true;stop.run();if(dialog!=null)dialog.dismiss();dialog=null;
        a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(prefs(a).getBoolean("return_guide",false)){guide.run();return;}
        dialog=new AlertDialog.Builder(a).setTitle("Your TV is resting").setMessage("Streaming has stopped. Resume the same channel live or return to your selected guide.")
            .setPositiveButton("Resume live TV",(d,w)->{rest=false;dialog=null;a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);last=SystemClock.elapsedRealtime();play.run();schedule();})
            .setNegativeButton("Back to guide",(d,w)->guide.run()).setCancelable(false).show();
    }
}
