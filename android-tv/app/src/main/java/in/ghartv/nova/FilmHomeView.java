package in.ghartv.nova;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GharTV landing surface. It covers the provider before the first network load;
 * only current provider data is rendered. No fixture catalogue or video auto-play. */
final class FilmHomeView extends FrameLayout {
    interface Host { void open(String url); void refresh(); void provider(); void privacy(); }
    interface PosterLoader { void load(ImageView target,String url); }
    private final Activity activity;
    private final Host host;
    private final PosterLoader loader;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final TextView status;
    private final LinearLayout grid;
    private String identity="";
    private int cardCount;
    FilmHomeView(Activity a,Host host) {this(a,host,(view,url)->{
        if(!safePoster(url))return;
        Glide.with(a).load(url).override(240,360).centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(new ColorDrawable(TvUi.SURFACE_2)).error(new ColorDrawable(TvUi.SURFACE_2)).into(view);
    });}
    FilmHomeView(Activity a,Host host,PosterLoader loader) {
        super(a);activity=a;this.host=host;this.loader=loader;
        setBackground(TvUi.gradient(0xff09282e,TvUi.BG,0,Color.TRANSPARENT,0,a));
        scroll=new ScrollView(a);scroll.setFillViewport(true);addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(18),dp(24),dp(24));scroll.addView(content);
        content.addView(TvUi.label(a,"Your next watch.",30,TvUi.TEXT,true));
        content.addView(TvUi.label(a,"Provider suggestions · FlixMomo · Review 37",13,TvUi.MINT,true));
        TextView info=TvUi.label(a,"Search above for live channels, films and series. Browse below with your remote.",14,TvUi.MUTED,false);info.setPadding(0,dp(8),0,dp(10));content.addView(info);
        LinearLayout actions=new LinearLayout(a);button(actions,"Refresh suggestions",host::refresh);button(actions,"Open provider page",host::provider);button(actions,"Privacy & content use",host::privacy);content.addView(actions);
        status=TvUi.label(a,"Loading suggestions from the provider…",13,TvUi.MUTED,false);status.setPadding(0,dp(12),0,dp(12));content.addView(status);
        grid=new LinearLayout(a);grid.setOrientation(LinearLayout.VERTICAL);content.addView(grid);
    }
    private int dp(int value){return TvUi.dp(activity,value);}
    private void button(LinearLayout row,String label,Runnable action){Button b=TvUi.button(activity,label,false);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.rightMargin=dp(8);row.addView(b,p);}
    static boolean safePoster(String raw){try {Uri u=Uri.parse(raw);return "https".equals(u.getScheme())&&u.getPort()==-1&&u.getUserInfo()==null&&(FlixMomoActivity.providerHost(u.getHost())||"image.tmdb.org".equals(u.getHost()));}catch(Exception e){return false;}}
    void loading(){status.setText(cardCount==0?"Loading suggestions from the provider…":"Refreshing provider suggestions…");}
    void unavailable(String reason){status.setText(reason+" Use Refresh or Open provider page. No sample catalogue has been substituted.");}
    int cardCount(){return cardCount;}
    void render(JSONObject snapshot){
        if(snapshot==null)return;
        JSONArray results=snapshot.optJSONArray("results");if(results==null)return;
        List<JSONObject> allowed=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<Math.min(results.length(),60);i++){
            JSONObject item=results.optJSONObject(i);if(item==null)continue;
            String url=item.optString("url"),name=item.optString("title"),image=item.optString("image");
            Uri u=Uri.parse(url);
            if(url.length()>2048||!FlixMomoActivity.allowedTop(u)||u.getQuery()!=null||u.getFragment()!=null||u.getPath()==null||!u.getPath().matches("(?i)^/(movie|tv|show|watch|title)/[a-z0-9].*")||name.length()<2||name.length()>180||!seen.add(url))continue;
            if(!safePoster(image))continue; // No image-less text-tile fallback.
            allowed.add(item);
        }
        if(allowed.isEmpty()){unavailable("The current page has not supplied any readable poster suggestions yet.");return;}
        String next=allowed.toString();if(identity.equals(next)){status.setText(cardCount+" suggestions supplied by FlixMomo · availability is not verified");return;}
        // Do not reset a user's focus while they are browsing an already-rendered set.
        if(grid.hasFocus()&&cardCount>0){status.setText("Browsing "+cardCount+" suggestions · Refresh loads the latest set");return;}
        identity=next;grid.removeAllViews();cardCount=0;
        int columns=activity.getResources().getConfiguration().screenWidthDp>=900?5:4;
        LinearLayout row=null;
        for(JSONObject item:allowed){
            if(cardCount%columns==0){row=new LinearLayout(activity);row.setGravity(Gravity.START|Gravity.TOP);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            String url=item.optString("url"),title=readable(item.optString("title")),meta=item.optString("metadata");
            LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(7),dp(7),dp(7),dp(8));
            TvUi.focusCard(card,TvUi.SURFACE,TvUi.SURFACE_3,12);card.setClickable(true);card.setContentDescription(title+". "+meta);card.setOnClickListener(v->host.open(url));
            ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setFocusable(false);image.setContentDescription(title+" poster");
            card.addView(image,new LinearLayout.LayoutParams(-1,dp(166)));loader.load(image,item.optString("image"));
            TextView label=TvUi.label(activity,title,14,TvUi.TEXT,true);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(label,new LinearLayout.LayoutParams(-1,dp(40)));
            TextView metadata=TvUi.label(activity,meta.isEmpty()?"FlixMomo catalogue":meta.replace(item.optString("title"),"").trim(),11,TvUi.MUTED,false);metadata.setMaxLines(2);metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(metadata,new LinearLayout.LayoutParams(-1,dp(31)));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(260),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(card,p);cardCount++;
        }
        if(row!=null&&cardCount%columns!=0)for(int n=cardCount%columns;n<columns;n++)row.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
        status.setText(cardCount+" suggestions supplied by FlixMomo · availability is not verified");
    }
    static String readable(String text){String s=text.trim();return s.indexOf(' ')<0?s.replace('-',' ').replace('_',' '):s;}
    void discardSuggestions(){identity="";cardCount=0;grid.removeAllViews();}
}
