package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.EditText;
import android.widget.Toast;
import java.util.ArrayList;

/** One explicit query route used by live guide, live player and film screen.
 * Speech is handled by the installed system activity, not recorded by GharTV. */
final class UnifiedSearch {
    static final int VOICE=4837;
    static final String QUERY="ghartv.unified.query";
    static String clean(String value){return value==null?"":value.replaceAll("[\\p{Cntrl}]"," ").trim();}
    static boolean valid(String q){return q!=null&&q.length()>=2&&q.length()<=120;}
    static void open(Activity activity,String raw){String q=clean(raw);if(!valid(q)){Toast.makeText(activity,"Enter 2–120 characters",Toast.LENGTH_SHORT).show();return;}activity.startActivity(new Intent(activity,FlixMomoActivity.class).putExtra(QUERY,q).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    static void voice(Activity activity){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1).putExtra(RecognizerIntent.EXTRA_PROMPT,"Search live channels, films and series. Your TV's speech service handles audio.");
        try{activity.startActivityForResult(i,VOICE);}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(activity,"Voice is unavailable on this TV. Type instead.",Toast.LENGTH_LONG).show();dialog(activity);}
    }
    static String voiceText(int request,int result,Intent data){if(request!=VOICE||result!=Activity.RESULT_OK||data==null)return "";ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words==null||words.isEmpty())return "";String q=clean(words.get(0));return valid(q)?q:"";}
    static void dialog(Activity activity){EditText input=new EditText(activity);input.setSingleLine(true);input.setHint("Channel, film or series");input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Search GharTV").setMessage("Live guide + FlixMomo. The film query is sent to FlixMomo.").setView(input)
                .setPositiveButton("Search",(d,w)->open(activity,input.getText().toString())).setNeutralButton("Voice",(d,w)->voice(activity)).setNegativeButton("Cancel",null).create();
        input.setOnEditorActionListener((v,a,event)->{if(a==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){dialog.dismiss();open(activity,input.getText().toString());return true;}return false;});dialog.show();input.requestFocus();
    }
}
