package in.ghartv.nova;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Native result navigation and an ephemeral player tray over ONE existing WebView.
 * Only provider-exposed choices are listed. An iframe load is not video success.
 * Automatic next-player is bounded and only reacts to a directly observable media
 * error; cross-origin/unobservable playback requires the viewer's Not playing action.
 */
final class FilmNativeControls {
    interface Host { void navigate(String url); void usePage(); void searchToolbar(); void nativeMode(); }
    private final Activity activity;
    private final FrameLayout stage;
    private final View chrome;
    private final Host host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ScrollView resultsView,choiceView;
    private final LinearLayout results,choices,tray;
    private TextView state;
    private WebView browser;
    private JSONArray players=new JSONArray();
    private final Set<String> attempted=new HashSet<>();
    private String snapshotUrl="",playerIdentity="",selected="",resultsIdentity="";
    private int generation,probeCount;
    private long selectedAt;
    private boolean active=true,blocked,pending,selecting,selectedByViewer,reading,autoNext=true;
    private final Runnable probe=this::read;
    private final Runnable hide=this::hideTray;
    private final Runnable slow=()->{if(active&&selectedByViewer){state.setText("Still not playing? Choose Not playing to try the next offered player.");showTray(false);}};

    FilmNativeControls(Activity a,FrameLayout stage,View chrome,Host host){
        this.activity=a;this.stage=stage;this.chrome=chrome;this.host=host;
        resultsView=new ScrollView(a);resultsView.setBackgroundColor(TvUi.BG);
        results=new LinearLayout(a);results.setOrientation(LinearLayout.VERTICAL);resultsView.addView(results);
        stage.addView(resultsView,new FrameLayout.LayoutParams(-1,-1));resultsView.setVisibility(View.GONE);
        choiceView=new ScrollView(a);choiceView.setBackground(TvUi.rounded(TvUi.SURFACE,14,TvUi.MINT,1,a));
        choices=new LinearLayout(a);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(dp(16),dp(12),dp(16),dp(12));choiceView.addView(choices);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(380),dp(250),Gravity.END|Gravity.BOTTOM);cp.setMargins(dp(12),dp(12),dp(12),dp(95));stage.addView(choiceView,cp);choiceView.setVisibility(View.GONE);
        tray=new LinearLayout(a);tray.setOrientation(LinearLayout.VERTICAL);tray.setPadding(dp(12),dp(8),dp(12),dp(8));tray.setBackground(TvUi.rounded(0xee071e29,12,TvUi.MINT,1,a));
        state=TvUi.label(a,"GharTV / FlixMomo · choose a title",12,TvUi.TEXT,false);tray.addView(state);
        LinearLayout row=new LinearLayout(a);
        add(row,"Play",()->playDefault());add(row,"Players",()->showPlayers());add(row,"Not playing",()->next(true));add(row,"Search",()->{hideAll();host.searchToolbar();});add(row,"Use page",()->{hideAll();host.usePage();});add(row,"Hide",this::hideTray);
        tray.addView(row);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);tp.setMargins(dp(10),dp(8),dp(10),dp(10));stage.addView(tray,tp);tray.setVisibility(View.GONE);
    }
    private int dp(int n){return TvUi.dp(activity,n);}
    private Button add(LinearLayout row,String title,Runnable click){Button b=TvUi.button(activity,title,false);b.setTextSize(13);b.setOnClickListener(v->{handler.removeCallbacks(hide);click.run();});row.addView(b);return b;}
    void attach(WebView web){browser=web;generation++;reading=false;}
    void started(){generation++;handler.removeCallbacks(probe);handler.removeCallbacks(slow);handler.removeCallbacks(hide);reading=false;pending=false;blocked=false;probeCount=0;players=new JSONArray();snapshotUrl="";playerIdentity="";selectedByViewer=false;selected="";attempted.clear();choiceView.setVisibility(View.GONE);resultsView.setVisibility(View.GONE);}
    void finished(){if(active&&!blocked){handler.removeCallbacks(probe);handler.post(probe);}}
    void failure(){blocked=true;generation++;reading=false;handler.removeCallbacks(probe);handler.removeCallbacks(slow);handler.removeCallbacks(hide);pending=false;hideAll();chrome.setVisibility(View.VISIBLE);}
    void pause(){active=false;generation++;reading=false;handler.removeCallbacksAndMessages(null);}
    void resume(){active=true;if(browser!=null&&!blocked){probeCount=0;finished();}}
    void destroy(){pause();browser=null;}
    boolean ownsFocus(){return resultsView.hasFocus()||choiceView.hasFocus()||tray.hasFocus();}
    boolean visible(){return resultsView.getVisibility()==View.VISIBLE||tray.getVisibility()==View.VISIBLE||choiceView.getVisibility()==View.VISIBLE;}
    boolean back(){
        if(choiceView.getVisibility()==View.VISIBLE){choiceView.setVisibility(View.GONE);showTray(true);return true;}
        if(resultsView.getVisibility()==View.VISIBLE){hideAll();host.searchToolbar();return true;}
        if(tray.getVisibility()==View.VISIBLE){hideTray();return true;}
        if(players.length()>0){showTray(true);return true;}
        return false;
    }
    void menu(){showTray(true);}
    void userInput(){if(tray.getVisibility()==View.VISIBLE){handler.removeCallbacks(hide);if(choiceView.getVisibility()!=View.VISIBLE)handler.postDelayed(hide,6000);}}
    void hideAll(){handler.removeCallbacks(hide);resultsView.setVisibility(View.GONE);choiceView.setVisibility(View.GONE);tray.setVisibility(View.GONE);}
    private void hideTray(){if(choiceView.getVisibility()!=View.VISIBLE){tray.setVisibility(View.GONE);if(browser!=null)browser.requestFocus();}}
    private void showTray(boolean focus){
        if(!active)return;tray.setVisibility(View.VISIBLE);tray.bringToFront();
        if(focus){View first=((LinearLayout)tray.getChildAt(1)).getChildAt(0);first.requestFocus();}
        handler.removeCallbacks(hide);if(choiceView.getVisibility()!=View.VISIBLE)handler.postDelayed(hide,6000);
        if(players.length()==0&&!blocked)finished();
    }
    private JSONObject decode(String raw) throws Exception {
        if(raw==null||raw.length()>131072)throw new IllegalArgumentException("Page result too large");
        Object value=new JSONTokener(raw).nextValue();if(!(value instanceof String))throw new IllegalArgumentException("No page snapshot");
        return new JSONObject((String)value);
    }
    private void read(){
        if(!active||blocked||reading||browser==null||probeCount>=6)return;
        String url=browser.getUrl();if(url==null||!FlixMomoActivity.allowedTop(Uri.parse(url)))return;
        final int token=generation;final WebView source=browser;reading=true;probeCount++;
        source.evaluateJavascript(FilmPageSnapshot.read(),raw->{
            if(!active||browser!=source||token!=generation)return;reading=false;
            try{
                JSONObject data=decode(raw);
                if(!"SNAPSHOT".equals(data.optString("state"))){
                    if("PROVIDER_VERIFICATION_REQUIRED".equals(data.optString("state"))){failure();state.setText("Provider verification required. Complete it on the provider page.");host.usePage();}
                    return;
                }
                String actual=data.getString("url");
                if(!actual.equals(source.getUrl())||!FlixMomoActivity.allowedTop(Uri.parse(actual)))return;
                snapshotUrl=actual;
                JSONArray found=data.optJSONArray("players");if(found!=null&&found.length()<=12){
                    String id=actual+":"+found.toString();players=found;
                    if(!id.equals(playerIdentity)){playerIdentity=id;if(choiceView.getVisibility()==View.VISIBLE)renderPlayers();}
                }
                if(data.optBoolean("search")){JSONArray list=data.optJSONArray("results");if(list!=null&&list.length()>0)renderResults(list,actual);}
                if(!data.optBoolean("search")&&players.length()>0&&resultsView.getVisibility()!=View.VISIBLE&&tray.getVisibility()!=View.VISIBLE&&!selectedByViewer){state.setText(players.length()+" players offered by FlixMomo · Play uses its first/default choice");showTray(true);}
                if(selectedByViewer&&android.os.SystemClock.elapsedRealtime()-selectedAt>=5000&&autoNext&&data.optBoolean("mediaObservable")&&data.optInt("mediaError")>0&&!pending)next(false);
                if(probeCount<6){handler.removeCallbacks(probe);handler.postDelayed(probe,1800);}
            }catch(Exception ignored){state.setText("This page did not expose readable result/player controls. Use page remains available.");}
        });
    }
    private void renderResults(JSONArray list,String page) throws Exception {
        String identity=page+":"+list.toString();if(identity.equals(resultsIdentity)&&resultsView.getVisibility()==View.VISIBLE)return;
        resultsIdentity=identity;results.removeAllViews();
        results.addView(TvUi.label(activity,"FlixMomo results · arrows to choose · OK to open",18,TvUi.TEXT,true));
        results.addView(TvUi.label(activity,"Returned titles; playback availability is checked separately.",12,TvUi.MUTED,false));
        LinearLayout row=null;Button first=null;int count=0;
        for(int i=0;i<Math.min(list.length(),60);i++){
            JSONObject item=list.getJSONObject(i);String url=item.optString("url"),title=item.optString("title");
            Uri uri=Uri.parse(url);if(title.length()<2||title.length()>180||url.length()>2048||!FlixMomoActivity.allowedTop(uri)||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().matches("(?i)^/(movie|tv|show|watch|title)/[a-z0-9].*"))continue;
            if(count%3==0){row=new LinearLayout(activity);results.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            Button b=TvUi.button(activity,title,false);b.setAllCaps(false);b.setTextSize(15);b.setMaxLines(3);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(94),1);p.setMargins(dp(5),dp(5),dp(5),dp(5));row.addView(b,p);
            b.setOnClickListener(v->{hideAll();host.nativeMode();chrome.setVisibility(View.GONE);attempted.clear();selected="";host.navigate(url);});
            if(first==null)first=b;count++;
        }
        if(count>0){host.nativeMode();resultsView.setVisibility(View.VISIBLE);resultsView.bringToFront();choiceView.setVisibility(View.GONE);tray.setVisibility(View.GONE);if(first!=null)first.requestFocus();}
    }
    private void playDefault(){if(players.length()==0){state.setText("No player choices exposed yet. Use page, or wait and reopen Players.");probeCount=0;finished();showTray(false);return;}int index=0;for(int i=0;i<players.length();i++)if(players.optJSONObject(i)!=null&&players.optJSONObject(i).optBoolean("selected")){index=i;break;}select(index);}
    private void showPlayers(){probeCount=0;finished();choiceView.setVisibility(View.VISIBLE);choiceView.bringToFront();renderPlayers();}
    private void renderPlayers(){
        choices.removeAllViews();choices.addView(TvUi.label(activity,"Choose a player",18,TvUi.TEXT,true));
        if(players.length()==0)choices.addView(TvUi.label(activity,"No readable choices yet. The provider may need sign-in or a page interaction.",13,TvUi.MUTED,false));
        Button first=null;
        for(int i=0;i<players.length();i++){
            final int n=i;JSONObject p=players.optJSONObject(i);if(p==null)continue;String name=p.optString("label");
            if(!name.matches("(?i)^(player|server|source)\\s*#?\\s*\\d{1,2}$"))continue;
            Button b=add(choices,name+(name.equals(selected)?" · selected":attempted.contains(name)?" · tried":""),()->select(n));if(first==null)first=b;
        }
        add(choices,autoNext?"Auto next on media error: on":"Auto next on media error: off",()->{autoNext=!autoNext;renderPlayers();});
        add(choices,"Close",()->{choiceView.setVisibility(View.GONE);showTray(true);});
        if(first!=null)first.requestFocus();
    }
    private void select(int index){
        if(!active||blocked||browser==null||pending||snapshotUrl.isEmpty()||index<0||index>=players.length())return;
        JSONObject choice=players.optJSONObject(index);if(choice==null)return;String label=choice.optString("label");
        if(!label.matches("(?i)^(player|server|source)\\s*#?\\s*\\d{1,2}$"))return;
        String page=snapshotUrl;final int token=generation;final WebView source=browser;pending=true;
        source.evaluateJavascript(FilmPageSnapshot.select(page,index,label),raw->{
            if(!active||browser!=source||generation!=token)return;pending=false;
            try{
                JSONObject result=decode(raw);
                if(!"SELECTION_REQUESTED".equals(result.optString("state"))){state.setText("Provider choices changed. Reopen Players to refresh them.");probeCount=0;finished();showTray(false);return;}
                host.nativeMode();selected=label;selectedAt=android.os.SystemClock.elapsedRealtime();attempted.add(label);selectedByViewer=true;choiceView.setVisibility(View.GONE);chrome.setVisibility(View.GONE);
                state.setText(label+" requested · playback not yet confirmed · Not playing tries another");showTray(false);
                handler.removeCallbacks(slow);handler.postDelayed(slow,20000);probeCount=0;finished();
            }catch(Exception e){state.setText("Player selection could not be confirmed. Use page or refresh Players.");showTray(false);}
        });
    }
    private void next(boolean viewer){
        if(!active||blocked||pending)return;
        if(!selected.isEmpty())attempted.add(selected);
        for(int i=0;i<players.length();i++){
            JSONObject p=players.optJSONObject(i);if(p==null)continue;
            if(!attempted.contains(p.optString("label"))){state.setText(viewer?"Trying another player you requested…":"Media error reported; trying the next offered player…");select(i);return;}
        }
        state.setText("No untried player remains. You can choose a previous player or return to results.");autoNext=false;showTray(true);
    }
}
