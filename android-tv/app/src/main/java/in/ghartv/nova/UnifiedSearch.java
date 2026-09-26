package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;

/** One typed/microphone search route. Recognition is explicit and provided by
 * the installed Android service, not a new background recorder or hosted model. */
final class UnifiedSearch {
    static final int VOICE=4837;
    static final String QUERY="ghartv.unified.query";
    private static final String LANGUAGE="ghartv.voice.language";
    private static final String[] TAGS={"auto","hi-IN","pa-IN","en-IN"};
    private static final String[] LABELS={"Automatic (when supported)","Hindi · हिन्दी","Punjabi · ਪੰਜਾਬੀ","English"};
    static String clean(String value){return value==null?"":value.replaceAll("[\\p{Cntrl}]"," ").trim();}
    static boolean valid(String q){return q!=null&&q.length()>=2&&q.length()<=120;}
    static void open(Activity activity,String raw){String q=clean(raw);if(!valid(q)){Toast.makeText(activity,"Enter 2–120 characters",Toast.LENGTH_SHORT).show();return;}activity.startActivity(new Intent(activity,FlixMomoActivity.class).putExtra(QUERY,q).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    static Intent voiceIntent(String language){
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5)
            .putExtra(RecognizerIntent.EXTRA_PROMPT,"Search GharTV: live channels, films and series. Your TV's speech service handles audio.");
        if(Arrays.asList(TAGS).contains(language)&&!"auto".equals(language))intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);
        else if(Build.VERSION.SDK_INT>=34){
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,new ArrayList<>(Arrays.asList("hi-IN","pa-IN","en-IN")));
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
        }
        return intent;
    }
    static void voice(Activity activity){
        String language=activity.getSharedPreferences(AppConfig.PREFS,Activity.MODE_PRIVATE).getString(LANGUAGE,"auto");
        try{activity.startActivityForResult(voiceIntent(language),VOICE);}
        catch(ActivityNotFoundException|SecurityException e){Toast.makeText(activity,"Voice is unavailable on this TV. Type instead.",Toast.LENGTH_LONG).show();dialog(activity);}
    }
    static String voiceText(int request,int result,Intent data){
        if(request!=VOICE||result!=Activity.RESULT_OK||data==null)return "";
        ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words==null)return "";
        for(String word:words){String q=clean(word);if(valid(q))return q;}return "";
    }
    static void dialog(Activity activity){dialog(activity,"");}
    private static void dialog(Activity activity,String initial){
        EditText input=new EditText(activity);input.setSingleLine(true);input.setText(initial);input.setHint("Channel, film or series");input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Search GharTV · type or speak")
            .setMessage("Live guide + films. Film queries go to FlixMomo. Speech accuracy and languages depend on the installed service.")
            .setView(input).setPositiveButton("Search",null).setNeutralButton("Microphone",null).setNegativeButton("Cancel",null).create();
        input.setOnEditorActionListener((v,a,event)->{if(a==EditorInfo.IME_ACTION_SEARCH){String q=clean(input.getText().toString());if(valid(q)){dialog.dismiss();open(activity,q);}else input.setError("Enter 2–120 characters");return true;}return false;});
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String q=clean(input.getText().toString());if(valid(q)){dialog.dismiss();open(activity,q);}else input.setError("Enter 2–120 characters");});
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{String saved=input.getText().toString();dialog.dismiss();
                new AlertDialog.Builder(activity).setTitle("Microphone language")
                    .setItems(LABELS,(d,which)->{activity.getSharedPreferences(AppConfig.PREFS,Activity.MODE_PRIVATE).edit().putString(LANGUAGE,TAGS[which]).apply();voice(activity);})
                    .setNegativeButton("Type instead",(d,w)->dialog(activity,saved)).show();
            });
        });dialog.show();input.requestFocus();
    }
}
