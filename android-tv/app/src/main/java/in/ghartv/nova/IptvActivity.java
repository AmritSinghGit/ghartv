package in.ghartv.nova;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate public IPTV-org browser. It does not reuse Jio sessions, guide data or playback APIs. */
public class IptvActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private IptvRepository repository;
    private final List<IptvChannel> all=new ArrayList<>(),visible=new ArrayList<>();
    private IptvChannelAdapter adapter;
    private ChipAdapter groups;
    private RecyclerView grid,groupList;
    private EditText query;
    private TextView status,empty;
    private ProgressBar loading;
    private String selectedGroup="All";
    private boolean busy;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);TvUi.immersive(this);
        repository=createRepository();Telemetry.screen(this,"iptv");setContentView(build());load(false);
    }
    protected IptvRepository createRepository(){return new IptvRepository(this);}
    protected void openChannel(IptvChannel channel){
        Intent intent=new Intent(this,IptvPlayerActivity.class);try{intent.putExtra(IptvPlayerActivity.EXTRA_CHANNEL_JSON,channel.toJson().toString());startActivity(intent);}catch(Exception e){Toast.makeText(this,"Could not open this public channel",Toast.LENGTH_LONG).show();}
    }
    private View build(){
        FrameLayout screen=new FrameLayout(this);screen.addView(new AuroraBackgroundView(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(TvUi.dp(this,24),TvUi.dp(this,12),TvUi.dp(this,24),TvUi.dp(this,12));screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=TvUi.label(this,"GHAR TV",23,TvUi.TEXT,true);brand.setLetterSpacing(.11f);header.addView(brand,new LinearLayout.LayoutParams(-2,-1));
        TextView badge=TvUi.label(this,"PUBLIC IPTV",10,TvUi.MINT,true);badge.setGravity(Gravity.CENTER);badge.setPadding(TvUi.dp(this,12),0,TvUi.dp(this,12),0);badge.setBackground(TvUi.rounded(Color.argb(65,115,245,194),15,TvUi.MINT,1,this));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,TvUi.dp(this,28));bp.leftMargin=TvUi.dp(this,12);header.addView(badge,bp);
        header.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        Button jio=button("Jio TV"),refresh=button("Refresh"),films=button("FlixMomo");jio.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});refresh.setOnClickListener(v->load(true));films.setOnClickListener(v->startActivity(new Intent(this,FlixMomoActivity.class)));
        header.addView(jio,headerButton(82));header.addView(refresh,headerButton(92));header.addView(films,headerButton(104));root.addView(header,new LinearLayout.LayoutParams(-1,TvUi.dp(this,48)));
        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);query=new EditText(this);query.setSingleLine(true);query.setHint("Search public IPTV channels");query.setTextColor(TvUi.TEXT);query.setHintTextColor(TvUi.MUTED);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchRow.addView(query,new LinearLayout.LayoutParams(0,TvUi.dp(this,46),1));Button search=button("Search");search.setOnClickListener(v->applyFilter(true));LinearLayout.LayoutParams sp=headerButton(94);sp.leftMargin=TvUi.dp(this,8);searchRow.addView(search,sp);root.addView(searchRow,new LinearLayout.LayoutParams(-1,TvUi.dp(this,48)));
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH||event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP){applyFilter(true);return true;}return false;});
        TextView notice=TvUi.label(this,"IPTV-org public playlist · HTTPS streams only · availability and regional access vary · GharTV hosts no channels",10.5f,TvUi.MUTED,false);notice.setSingleLine(true);notice.setEllipsize(TextUtils.TruncateAt.END);root.addView(notice,new LinearLayout.LayoutParams(-1,TvUi.dp(this,24)));
        groupList=new RecyclerView(this);groupList.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));groups=new ChipAdapter(value->{selectedGroup=value;applyFilter(false);});groupList.setAdapter(groups);root.addView(groupList,new LinearLayout.LayoutParams(-1,TvUi.dp(this,45)));
        LinearLayout state=new LinearLayout(this);state.setGravity(Gravity.CENTER_VERTICAL);status=TvUi.label(this,"Loading public playlist…",11.5f,TvUi.CYAN,true);state.addView(status,new LinearLayout.LayoutParams(0,TvUi.dp(this,28),1));loading=new ProgressBar(this);state.addView(loading,new LinearLayout.LayoutParams(TvUi.dp(this,28),TvUi.dp(this,28)));root.addView(state,new LinearLayout.LayoutParams(-1,TvUi.dp(this,32)));
        FrameLayout body=new FrameLayout(this);grid=new RecyclerView(this);grid.setLayoutManager(new GridLayoutManager(this,5));adapter=new IptvChannelAdapter(this::openChannel);grid.setAdapter(adapter);body.addView(grid,new FrameLayout.LayoutParams(-1,-1));empty=TvUi.label(this,"",17,TvUi.TEXT,true);empty.setGravity(Gravity.CENTER);empty.setVisibility(View.GONE);body.addView(empty,new FrameLayout.LayoutParams(-1,-1));root.addView(body,new LinearLayout.LayoutParams(-1,0,1));return screen;
    }
    private Button button(String text){Button button=TvUi.button(this,text,false);button.setTextSize(12);TvUi.focusCard(button,Color.rgb(10,31,45),Color.rgb(18,75,91),18);return button;}
    private LinearLayout.LayoutParams headerButton(int width){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(TvUi.dp(this,width),TvUi.dp(this,40));p.leftMargin=TvUi.dp(this,7);return p;}
    private void load(boolean force){if(busy)return;busy=true;loading.setVisibility(View.VISIBLE);status.setText(force?"Refreshing IPTV-org…":"Loading IPTV-org…");empty.setVisibility(View.GONE);executor.execute(()->{try{IptvRepository.Result result=repository.load(force);runOnUiThread(()->render(result));}catch(Exception error){runOnUiThread(()->failed(error));}});}
    private void render(IptvRepository.Result result){busy=false;loading.setVisibility(View.GONE);all.clear();all.addAll(result.channels);Map<String,Integer> counts=IptvRepository.groupCounts(all);groups.submit(IptvRepository.groups(all),selectedGroup,counts);applyFilter(false);String source=result.fromCache?"verified local copy":"live playlist";status.setText(String.format(java.util.Locale.US,"%,d secure public channels · %s · %s · %,d insecure/private entries excluded%s",all.size(),source,IptvNetwork.routeLabel(),result.insecureOrPrivateSkipped,result.warning.isEmpty()?"":" · "+result.warning));Telemetry.event(this,"iptv_catalogue",Telemetry.data("result","success","channel_count",all.size(),"cache",result.fromCache,"excluded",result.insecureOrPrivateSkipped,"route",IptvNetwork.routeLabel()));}
    private void failed(Throwable error){busy=false;loading.setVisibility(View.GONE);visible.clear();adapter.submit(visible);empty.setVisibility(View.VISIBLE);empty.setText("The public IPTV playlist could not load after direct, secure DNS and Tor recovery.\n\nChoose Refresh to try again.\n\nJio TV and FlixMomo remain separate.");String code=NetworkFailure.classify(error);status.setText("IPTV-org unavailable · "+friendly(code));Telemetry.error(this,"iptv_catalogue",error,Telemetry.data("reason",code));}
    private String friendly(String code){if("DNS_UNAVAILABLE".equals(code))return "DNS unavailable";if("TIMED_OUT".equals(code))return "request timed out";if("TLS_FAILED".equals(code))return "secure connection rejected";if("CONNECTION_FAILED".equals(code))return "connection failed";return "playlist unavailable";}
    void applyFilter(boolean focus){visible.clear();visible.addAll(IptvRepository.filter(all,selectedGroup,query==null?"":query.getText().toString()));adapter.submit(visible);empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);empty.setText("No secure public channels match this search.");if(focus&&!visible.isEmpty()){grid.scrollToPosition(0);grid.post(()->{RecyclerView.ViewHolder h=grid.findViewHolderForAdapterPosition(0);if(h!=null)h.itemView.requestFocus();});}}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
