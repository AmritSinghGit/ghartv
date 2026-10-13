package in.ghartv.nova;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Explicit, one-shot fallback to an installed Android recognition service.
 * Never records files, logs transcripts, installs a model or listens in background. */
final class InAppVoiceSearch implements RecognitionListener {
    static final int PERMISSION=4851;
    private static final WeakHashMap<Activity,InAppVoiceSearch> active=new WeakHashMap<>();
    private final Activity owner;
    private final String language;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private AlertDialog dialog;
    private boolean ended,awaitingPermission;
    private final Runnable timeout=()->fail("Speech recognition timed out. Type your search or try the microphone again.");
    static void offer(Activity owner,String language){
        close(owner);
        if(!SpeechRecognizer.isRecognitionAvailable(owner)){
            UnifiedSearch.unavailable(owner,"No Android speech service is available on this TV. Text search still searches live TV and films. GharTV has not installed a microphone service.");return;
        }
        InAppVoiceSearch session=new InAppVoiceSearch(owner,language);active.put(owner,session);
        session.dialog=new AlertDialog.Builder(owner).setTitle("Voice search · live TV + films")
            .setMessage("Your installed Android speech service handles audio and may use its network service. Nothing is recorded or saved by GharTV. Choose Listen to start.")
            .setPositiveButton("Listen",(d,w)->session.request())
            .setNeutralButton("Type instead",(d,w)->{close(owner);UnifiedSearch.dialog(owner);})
            .setNegativeButton("Cancel",(d,w)->close(owner)).create();
        session.dialog.setOnCancelListener(d->close(owner));session.dialog.show();
    }
    private InAppVoiceSearch(Activity activity,String tag){owner=activity;language=tag;}
    private void request(){
        if(ended||owner.isFinishing())return;
        if(owner.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){awaitingPermission=true;owner.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);return;}
        listen();
    }
    static boolean permission(Activity owner,int request,int[] results){
        if(request!=PERMISSION)return false;
        InAppVoiceSearch s=active.get(owner);if(s==null)return true;s.awaitingPermission=false;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)s.listen();else s.fail("Microphone permission was not granted. Type search remains available.");return true;
    }
    private void listen(){
        if(ended||owner.isFinishing())return;
        try{
            recognizer=SpeechRecognizer.createSpeechRecognizer(owner);recognizer.setRecognitionListener(this);
            dialog=new AlertDialog.Builder(owner).setTitle("Listening…")
                .setMessage("Say a channel, film or series. Tap Cancel to stop.")
                .setNegativeButton("Cancel",(d,w)->close(owner)).create();dialog.setOnCancelListener(d->close(owner));dialog.show();
            handler.postDelayed(timeout,20000);recognizer.startListening(UnifiedSearch.voiceIntent(language));
        }catch(RuntimeException e){fail("The installed speech service could not start. Type your search instead.");}
    }
    static void close(Activity owner){InAppVoiceSearch s=active.remove(owner);if(s!=null)s.dispose();}
    static void stopped(Activity owner){InAppVoiceSearch s=active.get(owner);if(s!=null&&!s.awaitingPermission)close(owner);}
    private void dispose(){if(ended)return;ended=true;handler.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(RuntimeException ignored){}recognizer=null;}}
    private void fail(String message){if(ended)return;close(owner);if(!owner.isFinishing())UnifiedSearch.unavailable(owner,message);}
    static String errorMessage(int error){
        switch(error){case SpeechRecognizer.ERROR_NO_MATCH:return "No speech match. Try again or type your search.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "Microphone permission is unavailable. Type instead.";
            case SpeechRecognizer.ERROR_NETWORK:case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "The speech service could not reach its network. Type instead.";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:return "That language is unavailable in the installed speech service. Choose another language or type instead.";
            default:return "The speech service did not return a usable result. Type instead or try again.";}
    }
    public void onReadyForSpeech(Bundle b){}
    public void onBeginningOfSpeech(){}
    public void onRmsChanged(float rms){}
    public void onBufferReceived(byte[] b){} // Never persisted or forwarded.
    public void onEndOfSpeech(){if(dialog!=null)dialog.setTitle("Recognizing…");}
    public void onError(int e){fail(errorMessage(e));}
    public void onResults(Bundle b){
        if(ended)return;ArrayList<String> list=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text=UnifiedSearch.firstResult(list);close(owner);
        if(!owner.isFinishing()){if(UnifiedSearch.valid(text))UnifiedSearch.open(owner,text);else UnifiedSearch.unavailable(owner,"No usable speech result. Type your search instead.");}
    }
    public void onPartialResults(Bundle b){}
    public void onEvent(int type,Bundle b){}
}
