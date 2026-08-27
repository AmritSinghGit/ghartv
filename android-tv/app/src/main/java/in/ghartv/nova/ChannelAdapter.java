package in.ghartv.nova;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
    public interface Listener {
        void onFocused(Channel channel, int position);
        void onPlay(Channel channel);
        void onFavourite(Channel channel);
    }

    private final List<Channel> channels = new ArrayList<>();
    private final Listener listener;
    private Set<Integer> favourites;

    public ChannelAdapter(Set<Integer> favourites, Listener listener) {
        this.favourites = favourites;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<Channel> values, Set<Integer> favourites) {
        channels.clear();
        channels.addAll(values);
        this.favourites = favourites;
        notifyDataSetChanged();
    }

    public Channel itemAt(int position) {
        if (position < 0 || position >= channels.size()) return null;
        return channels.get(position);
    }

    @Override public long getItemId(int position) {
        Channel channel = channels.get(position);
        return ((long) channel.number << 32) ^ channel.id.hashCode();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(TvUi.dp(context, 13), TvUi.dp(context, 12), TvUi.dp(context, 13), TvUi.dp(context, 10));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(context, 144));
        params.setMargins(TvUi.dp(context, 5), TvUi.dp(context, 5), TvUi.dp(context, 5), TvUi.dp(context, 5));
        card.setLayoutParams(params);
        TvUi.focusCard(card, Color.argb(235, 7, 22, 34), Color.rgb(14, 84, 96), 22);

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(TvUi.rounded(Color.argb(58, 255, 255, 255), 12, Color.TRANSPARENT, 0, context));
        top.addView(logo, new LinearLayout.LayoutParams(TvUi.dp(context, 44), TvUi.dp(context, 44)));
        top.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView number = TvUi.label(context, "", 14, TvUi.MINT, true);
        number.setGravity(Gravity.CENTER);
        number.setPadding(TvUi.dp(context, 10), 0, TvUi.dp(context, 10), 0);
        number.setBackground(TvUi.rounded(Color.argb(130, 0, 0, 0), 14, Color.argb(90, 115, 245, 194), 1, context));
        top.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(context, 28)));
        card.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(context, 46)));

        TextView name = TvUi.label(context, "", 16, TvUi.TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        nameParams.topMargin = TvUi.dp(context, 5);
        card.addView(name, nameParams);

        TextView meta = TvUi.label(context, "", 11, TvUi.MUTED, false);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(context, 18)));
        return new Holder(card, logo, number, name, meta);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Channel channel = channels.get(position);
        holder.number.setText((favourites.contains(channel.number) ? "★ " : "") + channel.displayNumber());
        holder.name.setText(channel.name);
        holder.meta.setText(channel.accessLabel() + "  •  " + channel.language + "  •  " + channel.category);
        if (channel.logoUrl.isEmpty()) {
            holder.logo.setImageDrawable(null);
            holder.logo.setContentDescription(channel.name);
        } else {
            Glide.with(holder.logo).load(channel.logoUrl).fitCenter().into(holder.logo);
        }
        holder.itemView.setContentDescription(channel.displayNumber() + " " + channel.name + ", " + channel.accessLabel().toLowerCase() + " channel");
        holder.itemView.setOnClickListener(view -> listener.onPlay(channel));
        holder.itemView.setOnLongClickListener(view -> {
            listener.onFavourite(channel);
            return true;
        });
        View.OnFocusChangeListener base = holder.itemView.getOnFocusChangeListener();
        holder.itemView.setOnFocusChangeListener((view, focused) -> {
            if (base != null) base.onFocusChange(view, focused);
            if (focused) listener.onFocused(channel, holder.getBindingAdapterPosition());
        });
    }

    @Override public int getItemCount() { return channels.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView number;
        final TextView name;
        final TextView meta;

        Holder(View itemView, ImageView logo, TextView number, TextView name, TextView meta) {
            super(itemView);
            this.logo = logo;
            this.number = number;
            this.name = name;
            this.meta = meta;
        }
    }
}
