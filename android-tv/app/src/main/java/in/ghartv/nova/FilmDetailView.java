package in.ghartv.nova;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import org.json.JSONObject;
import java.util.ArrayList;

/** Native current-title page. Missing facts are not fabricated and watchlist remains provider-owned. */
class FilmDetailView extends FrameLayout {
    interface Host {void watch();void watchlist();void original();void back();}
    private final Activity activity;private final Host host;
    private final TextView title,subtitle,synopsis,facts,status;
    private final ImageView poster;private final ScrollView copy;private final ProgressBar progress;private boolean waiting;
    private final Button watch,watchlist,original,back;
    private final ArrayList<Button> actions=new ArrayList<>();
    private ViewGroup page;private boolean shielding,focusable,touchable;private int descendants,accessibility;
    private String url="",image="";
    FilmDetailView(Activity a,Host h){
        super(a);activity=a;host=h;super.setVisibility(GONE);setBackground(TvUi.gradient(0xff092d38,TvUi.BG,0,Color.TRANSPARENT,0,a));
        LinearLayout all=new LinearLayout(a);all.setOrientation(LinearLayout.VERTICAL);all.setPadding(dp(24),dp(18),dp(24),dp(18));addView(all,new LayoutParams(-1,-1));
        all.addView(TvUi.label(a,"GHARTV  /  FLIXMOMO",14,TvUi.MINT,true));
        LinearLayout body=new LinearLayout(a);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,0,1);bp.topMargin=dp(14);all.addView(body,bp);
        poster=new ImageView(a);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setContentDescription("Title artwork supplied by FlixMomo");
        body.addView(poster,new LinearLayout.LayoutParams(dp(156),dp(234)));
        LinearLayout info=new LinearLayout(a);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(24),0,0,0);body.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        title=TvUi.label(a,"Loading title…",28,TvUi.TEXT,true);title.setMaxLines(2);info.addView(title);
        subtitle=TvUi.label(a,"FlixMomo title information",12,TvUi.MINT,false);info.addView(subtitle);progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(GONE);info.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        LinearLayout row=new LinearLayout(a);watch=add(row,"Watch now",h::watch);watchlist=add(row,"Watchlist",h::watchlist);original=add(row,"Original page",h::original);back=add(row,"Back",h::back);info.addView(row);
        copy=new ScrollView(a);copy.setFillViewport(false);copy.setFocusable(true);copy.setId(View.generateViewId());copy.setSmoothScrollingEnabled(false);
        LinearLayout text=new LinearLayout(a);text.setOrientation(LinearLayout.VERTICAL);copy.addView(text);info.addView(copy,new LinearLayout.LayoutParams(-1,0,1));
        synopsis=TvUi.label(a,"Reading the provider’s description…",16,TvUi.TEXT,false);synopsis.setPadding(0,dp(12),0,dp(12));text.addView(synopsis);
        facts=TvUi.label(a,"",13,TvUi.MUTED,false);text.addView(facts);
        status=TvUi.label(a,"No video starts until you choose Watch now. Provider login may be required.",12,TvUi.MUTED,false);status.setPadding(0,dp(10),0,0);all.addView(status);
        for(Button b:actions)b.setNextFocusDownId(copy.getId());
        watch.setEnabled(false);watchlist.setEnabled(false);
    }
    private int dp(int n){return TvUi.dp(activity,n);}
    private Button add(LinearLayout row,String name,Runnable action){Button b=TvUi.button(activity,name,name.equals("Watch now"));b.setTextSize(13);b.setId(View.generateViewId());b.setOnClickListener(v->action.run());actions.add(b);row.addView(b,new LinearLayout.LayoutParams(-2,dp(46)));return b;}
    void bindUnderlyingPage(ViewGroup p){restore();page=p;shield();}
    @Override public void setVisibility(int v){super.setVisibility(v);if(v==VISIBLE)shield();else restore();}
    private void shield(){if(page==null||getVisibility()!=VISIBLE||shielding)return;focusable=page.isFocusable();touchable=page.isFocusableInTouchMode();descendants=page.getDescendantFocusability();accessibility=page.getImportantForAccessibility();page.clearFocus();page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);page.setFocusableInTouchMode(false);page.setFocusable(false);page.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);shielding=true;}
    private void restore(){if(!shielding||page==null)return;page.setDescendantFocusability(descendants);page.setFocusable(focusable);page.setFocusableInTouchMode(touchable);page.setImportantForAccessibility(accessibility);shielding=false;}
    void begin(String target,JSONObject card){waiting=true;progress.setVisibility(VISIBLE);url=target;title.setText(card==null?"Loading title…":FilmHomeView.readable(card.optString("title","Loading title…")));subtitle.setText("FlixMomo · reading this title’s details");synopsis.setText("Reading the provider’s description…");facts.setText(card==null?"":card.optString("metadata"));status.setText("Loading this title from FlixMomo… Watch is enabled when its action is available.");watch.setEnabled(false);watchlist.setEnabled(false);image="";poster.setImageDrawable(null);if(card!=null)art(card.optString("image"));copy.scrollTo(0,0);setVisibility(VISIBLE);bringToFront();original.requestFocus();}
    void preview(JSONObject cached){
        if(cached==null)return;render(cached);waiting=true;watch.setEnabled(false);watchlist.setEnabled(false);progress.setVisibility(VISIBLE);
        subtitle.setText("FlixMomo · details from this visit, refreshing");status.setText("Checking the current Watch action…");
    }
    void followNavigation(String target){if(FilmProviderPolicy.detail(Uri.parse(target)))url=target;}
    void render(JSONObject value){if(value==null||!FilmProviderPolicy.allowed(Uri.parse(value.optString("url"))))return;
        String current=Uri.parse(value.optString("url")).getPath(),expected=Uri.parse(url).getPath();if(current==null||expected==null||!current.replaceAll("/+$", "").equals(expected.replaceAll("/+$", "")))return;
        String name=value.optString("title");if(name.length()<2||name.length()>180)return;waiting=false;progress.setVisibility(GONE);title.setText(name);url=value.optString("url");
        String description=value.optString("synopsis");synopsis.setText(description.isEmpty()?"The provider has not supplied a readable description. Open Original page for the source view.":description);
        StringBuilder lines=new StringBuilder();JSONObject f=value.optJSONObject("facts");if(f!=null)for(String key:new String[]{"Type","Released","Duration","Genres","Language","Status","Provider rating"}){String s=f.optString(key);if(!s.isEmpty()&&s.length()<=180)lines.append(key).append("  ").append(s).append('\n');}
        facts.setText(lines.toString());subtitle.setText("Information supplied by FlixMomo · availability not independently verified");
        boolean wasReady=watch.isEnabled();watch.setEnabled(value.optBoolean("watch"));watchlist.setEnabled(value.optBoolean("watchlist"));art(value.optString("image"));
        status.setText("Watch uses FlixMomo’s player. Watchlist uses its account; GharTV does not claim a saved item.");
        // Do not steal an already chosen action when a later observation arrives.
        if(!wasReady&&watch.isEnabled()&&original.hasFocus())watch.requestFocus();
    }
    private void art(String source){if(!FilmHomeView.safePoster(source)||source.equals(image))return;image=source;loadArtwork(poster,source);}
    void loadArtwork(ImageView target,String source){Glide.with(activity).load(source).override(312,468).centerCrop().diskCacheStrategy(DiskCacheStrategy.NONE).into(target);}
    void unavailable(String reason){waiting=false;progress.setVisibility(GONE);status.setText(reason+" Original page and Back remain available.");}
    boolean handleRemote(KeyEvent e,View focused){int k=e.getKeyCode();if(k!=KeyEvent.KEYCODE_DPAD_LEFT&&k!=KeyEvent.KEYCODE_DPAD_RIGHT&&k!=KeyEvent.KEYCODE_DPAD_DOWN&&k!=KeyEvent.KEYCODE_DPAD_UP)return false;
        if(e.getAction()==KeyEvent.ACTION_UP)return true;if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;
        if(copy.hasFocus()){
            if(k==KeyEvent.KEYCODE_DPAD_UP&&copy.getScrollY()==0){primary().requestFocus();return true;}
            if(k==KeyEvent.KEYCODE_DPAD_DOWN)copy.scrollBy(0,dp(90));else if(k==KeyEvent.KEYCODE_DPAD_UP)copy.scrollBy(0,-dp(90));return true;
        }
        int index=actions.indexOf(focused);if(index<0){primary().requestFocus();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){copy.requestFocus();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){int delta=k==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;for(int n=index+delta;n>=0&&n<actions.size();n+=delta)if(actions.get(n).isEnabled()){actions.get(n).requestFocus();break;}}
        return true;
    }
    private Button primary(){return watch.isEnabled()?watch:original;}
    String currentTitle(){return title.getText().toString();}
}
