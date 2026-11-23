package in.ghartv.nova;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

/** Remote-friendly cards for the separate public IPTV source. */
public final class IptvChannelAdapter extends RecyclerView.Adapter<IptvChannelAdapter.Holder> {
    public interface Listener { void open(IptvChannel channel); }
    private final List<IptvChannel> channels=new ArrayList<>();
    private final Listener listener;
    public IptvChannelAdapter(Listener value){listener=value;setHasStableIds(true);}
    public void submit(List<IptvChannel> values){channels.clear();if(values!=null)channels.addAll(values);notifyDataSetChanged();}
    public IptvChannel itemAt(int position){return position>=0&&position<channels.size()?channels.get(position):null;}
    @Override public long getItemId(int position){IptvChannel c=channels.get(position);return ((long)c.id.hashCode()<<32)^c.streamUrl.hashCode();}
    @Override public int getItemCount(){return channels.size();}

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
        LinearLayout card=new LinearLayout(parent.getContext());card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(TvUi.dp(parent.getContext(),11),TvUi.dp(parent.getContext(),9),TvUi.dp(parent.getContext(),11),TvUi.dp(parent.getContext(),8));
        RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,TvUi.dp(parent.getContext(),132));
        params.setMargins(TvUi.dp(parent.getContext(),5),TvUi.dp(parent.getContext(),5),TvUi.dp(parent.getContext(),5),TvUi.dp(parent.getContext(),5));card.setLayoutParams(params);
        TvUi.focusCard(card,Color.argb(244,8,21,34),Color.rgb(18,69,88),14);
        LinearLayout top=new LinearLayout(parent.getContext());top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(parent.getContext());logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setBackground(TvUi.rounded(Color.argb(48,255,255,255),11,Color.argb(24,255,255,255),1,parent.getContext()));
        top.addView(logo,new LinearLayout.LayoutParams(TvUi.dp(parent.getContext(),44),TvUi.dp(parent.getContext(),44)));
        TextView number=TvUi.label(parent.getContext(),"",12,TvUi.MINT,true);number.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,TvUi.dp(parent.getContext(),44),1);np.leftMargin=TvUi.dp(parent.getContext(),8);top.addView(number,np);card.addView(top,new LinearLayout.LayoutParams(-1,TvUi.dp(parent.getContext(),44)));
        TextView name=TvUi.label(parent.getContext(),"",14,TvUi.TEXT,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams namep=new LinearLayout.LayoutParams(-1,0,1);namep.topMargin=TvUi.dp(parent.getContext(),4);card.addView(name,namep);
        TextView group=TvUi.label(parent.getContext(),"",10,TvUi.CYAN,false);group.setSingleLine(true);group.setEllipsize(TextUtils.TruncateAt.END);card.addView(group,new LinearLayout.LayoutParams(-1,TvUi.dp(parent.getContext(),18)));
        TextView source=TvUi.label(parent.getContext(),"PUBLIC IPTV · HTTPS",8.5f,TvUi.MUTED,true);source.setLetterSpacing(.05f);card.addView(source,new LinearLayout.LayoutParams(-1,TvUi.dp(parent.getContext(),16)));
        return new Holder(card,logo,number,name,group);
    }
    @Override public void onBindViewHolder(@NonNull Holder h,int position){
        IptvChannel channel=channels.get(position);h.number.setText(channel.displayNumber());h.name.setText(channel.name);h.group.setText(channel.group);
        if(channel.logoUrl.isEmpty()){Glide.with(h.logo).clear(h.logo);h.logo.setImageDrawable(null);}else Glide.with(h.logo).load(channel.logoUrl).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).thumbnail(.25f).fitCenter().into(h.logo);
        h.itemView.setContentDescription(channel.name+", "+channel.group+", public IPTV channel");h.itemView.setOnClickListener(v->listener.open(channel));
    }
    @Override public void onViewRecycled(@NonNull Holder h){Glide.with(h.logo).clear(h.logo);super.onViewRecycled(h);}
    static final class Holder extends RecyclerView.ViewHolder{final ImageView logo;final TextView number,name,group;Holder(View v,ImageView l,TextView n,TextView t,TextView g){super(v);logo=l;number=n;name=t;group=g;}}
}
