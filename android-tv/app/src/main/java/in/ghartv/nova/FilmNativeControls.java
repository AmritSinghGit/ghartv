package in.ghartv.nova;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.HashSet;
import java.util.Set;

/** Original provider posters stay in the one WebView. Only the player tray is native. */
final class FilmNativeControls {
    interface Host { void navigate(String url); void usePage(); void searchToolbar(); void nativeMode(); void navigationHint(String text);
        default void pageObserved(JSONObject data){} default void pageAction(String action){} default void verificationRequired(){}
    }
    private final Activity activity;
    private final View chrome;
    private final Host host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ScrollView choiceView;
    private final LinearLayout choices,tray;
    private TextView state;
    private WebView browser;
    private JSONArray players=new JSONArray();
    private final Set<String> attempted=new HashSet<>();
    private String snapshotUrl="",playerIdentity="",selected="";
    private boolean postersReady,posterBusy,playerTrayAnnounced,pageBusy,detailFocusApplied;
    private long lastPosterKey,selectedAt;
    private int generation,probeCount;
    private boolean active=true,blocked,pending,selectedByViewer,reading,autoNext=true;
    private final Runnable probe=this::read;
    private final Runnable hide=this::hideTray;
    private final Runnable slow=()->{if(active&&selectedByViewer){state.setText("Still not playing? Choose Not playing to try another offered player.");showTray(false);}};
    FilmNativeControls(Activity a,FrameLayout stage,View chrome,Host host){
        activity=a;this.chrome=chrome;this.host=host;
        choiceView=new ScrollView(a);choiceView.setBackground(TvUi.rounded(TvUi.SURFACE,14,TvUi.MINT,1,a));
        choices=new LinearLayout(a);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(dp(16),dp(12),dp(16),dp(12));choiceView.addView(choices);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(360),dp(250),Gravity.END|Gravity.BOTTOM);cp.setMargins(dp(12),dp(12),dp(12),dp(95));stage.addView(choiceView,cp);choiceView.setVisibility(View.GONE);
        tray=new LinearLayout(a);tray.setOrientation(LinearLayout.VERTICAL);tray.setPadding(dp(12),dp(8),dp(12),dp(8));tray.setBackground(TvUi.rounded(0xee071e29,12,TvUi.MINT,1,a));
        state=TvUi.label(a,"GharTV · provider controls · Review 37",12,TvUi.TEXT,false);tray.addView(state);
        LinearLayout row=new LinearLayout(a);add(row,"Play",this::playDefault);add(row,"Players",this::showPlayers);add(row,"Watchlist",()->host.pageAction("watchlist"));add(row,"Not playing",()->next(true));
        add(row,"Search",()->{hideAll();host.searchToolbar();});add(row,"Use page",()->{hideAll();host.usePage();});add(row,"Hide",this::hideTray);tray.addView(row);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);tp.setMargins(dp(10),dp(8),dp(10),dp(10));stage.addView(tray,tp);tray.setVisibility(View.GONE);
    }
    private int dp(int n){return TvUi.dp(activity,n);}
    private Button add(LinearLayout row,String title,Runnable click){Button b=TvUi.button(activity,title,false);b.setTextSize(13);b.setOnClickListener(v->{handler.removeCallbacks(hide);click.run();});row.addView(b);return b;}
    void attach(WebView web){pageBusy=false;browser=web;generation++;reading=false;posterBusy=false;postersReady=false;}
    void started(){postersReady=false;posterBusy=false;playerTrayAnnounced=false;pageBusy=false;detailFocusApplied=false;generation++;handler.removeCallbacksAndMessages(null);reading=false;pending=false;blocked=false;probeCount=0;players=new JSONArray();snapshotUrl="";playerIdentity="";selectedByViewer=false;selected="";attempted.clear();choiceView.setVisibility(View.GONE);tray.setVisibility(View.GONE);}
    void finished(){if(active&&!blocked){handler.removeCallbacks(probe);handler.post(probe);}}
    void failure(){pageBusy=false;postersReady=false;posterBusy=false;blocked=true;generation++;reading=false;pending=false;handler.removeCallbacksAndMessages(null);hideAll();chrome.setVisibility(View.VISIBLE);}
    void pause(){pageBusy=false;posterBusy=false;active=false;generation++;reading=false;handler.removeCallbacksAndMessages(null);}
    void resume(){active=true;if(browser!=null&&!blocked){probeCount=0;finished();}}
    void destroy(){pause();browser=null;}
    boolean ownsFocus(){return choiceView.hasFocus()||tray.hasFocus();}
    boolean visible(){return tray.getVisibility()==View.VISIBLE||choiceView.getVisibility()==View.VISIBLE;}
    boolean back(){if(choiceView.getVisibility()==View.VISIBLE){choiceView.setVisibility(View.GONE);showTray(true);return true;}if(tray.getVisibility()==View.VISIBLE){hideTray();return true;}return false;}
    void menu(){if(postersReady){hideAll();host.searchToolbar();}else showTray(true);}
    void userInput(){if(tray.getVisibility()==View.VISIBLE){handler.removeCallbacks(hide);if(choiceView.getVisibility()!=View.VISIBLE)handler.postDelayed(hide,6000);}}
    void hideAll(){handler.removeCallbacks(hide);choiceView.setVisibility(View.GONE);tray.setVisibility(View.GONE);}
    private void hideTray(){if(choiceView.getVisibility()!=View.VISIBLE){tray.setVisibility(View.GONE);if(browser!=null)browser.requestFocus();}}
    private void showTray(boolean focus){if(!active)return;tray.setVisibility(View.VISIBLE);tray.bringToFront();if(focus)((LinearLayout)tray.getChildAt(1)).getChildAt(0).requestFocus();handler.removeCallbacks(hide);if(choiceView.getVisibility()!=View.VISIBLE)handler.postDelayed(hide,6000);if(players.length()==0&&!blocked)finished();}
    private JSONObject decode(String raw) throws Exception {if(raw==null||raw.length()>131072)throw new IllegalArgumentException("Page result too large");Object value=new JSONTokener(raw).nextValue();if(!(value instanceof String))throw new IllegalArgumentException("No page snapshot");return new JSONObject((String)value);}
    private void read(){
        if(!active||blocked||reading||browser==null||probeCount>=6)return;String url=browser.getUrl();if(url==null||!FlixMomoActivity.allowedTop(Uri.parse(url)))return;
        final int token=generation;final WebView source=browser;reading=true;probeCount++;
        source.evaluateJavascript(FilmPageSnapshot.read(),raw->{
            if(!active||browser!=source||token!=generation)return;reading=false;
            try{
                JSONObject data=decode(raw);if(!"SNAPSHOT".equals(data.optString("state"))){if("PROVIDER_VERIFICATION_REQUIRED".equals(data.optString("state"))){failure();host.navigationHint("Complete the provider verification on its page.");host.verificationRequired();}return;}
                String actual=data.getString("url");if(!actual.equals(source.getUrl())||!FlixMomoActivity.allowedTop(Uri.parse(actual)))return;snapshotUrl=actual;host.pageObserved(data);
                JSONArray found=data.optJSONArray("players");if(found!=null&&found.length()<=12){String identity=actual+":"+found.toString();players=found;if(!identity.equals(playerIdentity)){playerIdentity=identity;if(choiceView.getVisibility()==View.VISIBLE)renderPlayers();}}
                if(data.optBoolean("search"))enablePosters(actual);
                else if(!detailFocusApplied&&source.hasFocus()){detailFocusApplied=true;pageAction("focus");}
                if(!data.optBoolean("search")&&players.length()>0&&!playerTrayAnnounced&&!selectedByViewer){playerTrayAnnounced=true;state.setText(players.length()+" players offered · Menu opens controls");showTray(false);}
                if(selectedByViewer&&SystemClock.elapsedRealtime()-selectedAt>=5000&&autoNext&&data.optBoolean("mediaObservable")&&data.optInt("mediaError")>0&&!pending)next(false);
                if(probeCount<6){handler.removeCallbacks(probe);handler.postDelayed(probe,1800);}
            }catch(Exception ignored){host.navigationHint("Use the provider page; its player controls could not be read.");}
        });
    }
    private void enablePosters(String expectedUrl){
        final WebView source=browser;final int token=generation;if(source==null||posterBusy)return;
        source.evaluateJavascript(FilmPosterNavigation.script("scan"),raw->{
            if(!active||browser!=source||token!=generation||!expectedUrl.equals(source.getUrl()))return;
            try{JSONObject value=decode(raw);if(!"READY".equals(value.optString("state"))||value.optInt("count")<1)return;
                if(!postersReady){postersReady=true;host.nativeMode();host.navigationHint("Original posters · arrows browse · OK opens · Menu shows search");if(source.hasFocus()&&!ownsFocus())enterPosters();}
            }catch(Exception ignored){/* Never replace provider artwork with a fallback grid. */}
        });
    }
    void enterPosters(){if(!active||!postersReady||browser==null)return;final int token=generation;final WebView source=browser;source.evaluateJavascript(FilmPosterNavigation.script("focus"),raw->{if(!active||browser!=source||token!=generation)return;try{posterHint(decode(raw));}catch(Exception ignored){}});}
    private void posterHint(JSONObject value){String label=value.optString("label","");int count=value.optInt("count"),index=value.optInt("index");if(label.length()>0&&label.length()<=180&&index>=0&&index<count)host.navigationHint(label+" · "+(index+1)+" of "+count+" · OK opens · Menu shows search");}
    boolean posterKey(KeyEvent event){
        if(!active||blocked||!postersReady||browser==null||!browser.hasFocus()||ownsFocus())return false;
        String action;switch(event.getKeyCode()){case KeyEvent.KEYCODE_DPAD_LEFT:action="left";break;case KeyEvent.KEYCODE_DPAD_RIGHT:action="right";break;case KeyEvent.KEYCODE_DPAD_UP:action="up";break;case KeyEvent.KEYCODE_DPAD_DOWN:action="down";break;case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:action="activate";break;default:return false;}
        if(event.getAction()==KeyEvent.ACTION_UP)return true;if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
        long now=SystemClock.uptimeMillis();if(posterBusy||event.getRepeatCount()>0&&now-lastPosterKey<110)return true;lastPosterKey=now;posterBusy=true;
        final WebView source=browser;final int token=generation;final KeyEvent original=new KeyEvent(event);
        source.evaluateJavascript(FilmPosterNavigation.script(action),raw->{
            if(!active||browser!=source||token!=generation)return;posterBusy=false;
            try{JSONObject out=decode(raw);String result=out.optString("state");
                if("ACTIVATE".equals(result)){String url=out.optString("url");if(url.length()<=2048&&FlixMomoActivity.allowedTop(Uri.parse(url))){hideAll();host.nativeMode();chrome.setVisibility(View.GONE);host.navigate(url);}}
                else if("TOOLBAR".equals(result)){hideAll();host.searchToolbar();}
                else if("EDITING".equals(result)||"UNSUPPORTED".equals(result)){source.dispatchKeyEvent(original);source.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,original.getKeyCode()));}
                else posterHint(out);
            }catch(Exception ignored){postersReady=false;host.navigationHint("Use page controls; poster navigation is unavailable on this layout.");}
        });return true;
    }
    private void playDefault(){if(players.length()==0){host.pageAction("watch");return;}int index=0;for(int i=0;i<players.length();i++)if(players.optJSONObject(i)!=null&&players.optJSONObject(i).optBoolean("selected")){index=i;break;}select(index);}
    private void showPlayers(){probeCount=0;finished();choiceView.setVisibility(View.VISIBLE);choiceView.bringToFront();renderPlayers();}
    private void renderPlayers(){
        String focusLabel="";View focused=choiceView.findFocus();if(focused instanceof Button)focusLabel=((Button)focused).getText().toString();choices.removeAllViews();choices.addView(TvUi.label(activity,"Choose a player",18,TvUi.TEXT,true));
        if(players.length()==0)choices.addView(TvUi.label(activity,"The provider has not exposed readable choices. Use page remains available.",13,TvUi.MUTED,false));Button first=null,restore=null;
        for(int i=0;i<players.length();i++){final int n=i;JSONObject p=players.optJSONObject(i);if(p==null)continue;String name=p.optString("label");if(!name.matches("(?i)^(player|server|source)\\s*#?\\s*\\d{1,2}$"))continue;Button b=add(choices,name+(name.equals(selected)?" · selected":attempted.contains(name)?" · tried":""),()->select(n));if(first==null)first=b;if(b.getText().toString().equals(focusLabel))restore=b;}
        add(choices,autoNext?"Auto next on media error: on":"Auto next on media error: off",()->{autoNext=!autoNext;renderPlayers();});add(choices,"Close",()->{choiceView.setVisibility(View.GONE);showTray(true);});if(restore!=null)restore.requestFocus();else if(first!=null)first.requestFocus();
    }
    private void select(int index){
        if(!active||blocked||browser==null||pending||snapshotUrl.isEmpty()||index<0||index>=players.length())return;JSONObject choice=players.optJSONObject(index);if(choice==null)return;String label=choice.optString("label");if(!label.matches("(?i)^(player|server|source)\\s*#?\\s*\\d{1,2}$"))return;
        final int token=generation;final WebView source=browser;pending=true;
        source.evaluateJavascript(FilmPageSnapshot.select(snapshotUrl,index,label),raw->{
            if(!active||browser!=source||generation!=token)return;pending=false;
            try{JSONObject result=decode(raw);if(!"SELECTION_REQUESTED".equals(result.optString("state"))){state.setText("Provider choices changed. Reopen Players to refresh.");probeCount=0;finished();showTray(false);return;}
                host.nativeMode();selected=label;selectedAt=SystemClock.elapsedRealtime();attempted.add(label);selectedByViewer=true;choiceView.setVisibility(View.GONE);chrome.setVisibility(View.GONE);state.setText(label+" selected · Not playing tries another · playback not verified");showTray(false);handler.removeCallbacks(slow);handler.postDelayed(slow,20000);probeCount=0;finished();
            }catch(Exception e){state.setText("Selection not confirmed. Use page or refresh Players.");showTray(false);}
        });
    }

    void pageAction(String action){
        if(!active||blocked||browser==null||pageBusy)return;
        final WebView source=browser;final int token=generation;pageBusy=true;
        source.evaluateJavascript(FilmPageFocus.script(action),raw->{
            if(!active||source!=browser||token!=generation)return;pageBusy=false;
            try{JSONObject result=decode(raw);String state=result.optString("state");
                if(!source.getUrl().equals(result.optString("url")))return;
                if("TAP".equals(state)){
                    double w=result.optDouble("width"),h=result.optDouble("height"),x=result.optDouble("x"),y=result.optDouble("y");
                    if(!Double.isFinite(w)||!Double.isFinite(h)||!Double.isFinite(x)||!Double.isFinite(y)||w<=0||h<=0||x<0||x>w||y<0||y>h)return;
                    float px=(float)(x*source.getWidth()/w),py=(float)(y*source.getHeight()/h);
                    hideAll();source.requestFocus();long time=SystemClock.uptimeMillis();
                    android.view.MotionEvent down=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,px,py,0);
                    android.view.MotionEvent up=android.view.MotionEvent.obtain(time,time+70,android.view.MotionEvent.ACTION_UP,px,py,0);
                    try{source.dispatchTouchEvent(down);source.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}
                }else if("NOT_FOUND".equals(state)){host.navigationHint("The provider has not exposed that action yet. Use the page or retry after it loads.");}
                else if("FOCUSED".equals(state)){host.navigationHint(result.optString("label")+" · arrows move · OK selects · Menu opens GharTV controls");}
                else if("VERIFICATION_REQUIRED".equals(state)){host.verificationRequired();}
            }catch(Exception ignored){host.navigationHint("Provider control could not be confirmed; the original page is still available.");}
        });
    }
    boolean pageKey(KeyEvent event){
        if(!active||blocked||postersReady||browser==null||!browser.hasFocus()||ownsFocus())return false;
        String action;switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_DPAD_LEFT:action="left";break;case KeyEvent.KEYCODE_DPAD_RIGHT:action="right";break;
            case KeyEvent.KEYCODE_DPAD_UP:action="up";break;case KeyEvent.KEYCODE_DPAD_DOWN:action="down";break;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:action="activate";break;default:return false;
        }
        if(event.getAction()==KeyEvent.ACTION_UP)return true;if(event.getAction()!=KeyEvent.ACTION_DOWN||pageBusy)return true;
        if(event.getRepeatCount()>0&&SystemClock.uptimeMillis()-lastPosterKey<110)return true;lastPosterKey=SystemClock.uptimeMillis();
        final WebView source=browser;final int token=generation;final KeyEvent original=new KeyEvent(event);pageBusy=true;
        source.evaluateJavascript(FilmPageFocus.script("scan"),raw->{
            if(!active||source!=browser||token!=generation)return;pageBusy=false;
            source.evaluateJavascript("JSON.stringify({editing:['INPUT','TEXTAREA','SELECT','IFRAME','VIDEO'].includes(document.activeElement?.tagName)||!!document.activeElement?.isContentEditable})",answer->{
                if(!active||source!=browser||token!=generation)return;
                try{if(decode(answer).optBoolean("editing")){source.dispatchKeyEvent(original);source.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,original.getKeyCode()));return;}}catch(Exception ignored){}
                pageAction(action);
            });
        });return true;
    }

    private void next(boolean viewer){if(!active||blocked||pending)return;if(!selected.isEmpty())attempted.add(selected);for(int i=0;i<players.length();i++){JSONObject p=players.optJSONObject(i);if(p!=null&&!attempted.contains(p.optString("label"))){state.setText(viewer?"Trying the next player you requested…":"Media error; trying the next offered player…");select(i);return;}}state.setText("No untried player remains. Choose a player or return to search.");autoNext=false;showTray(true);}
}
