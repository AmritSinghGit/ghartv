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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Fast, stable-ID television cards with content-aware incremental updates. */
public final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
    public interface Listener {
        void onFocused(Channel channel, int position);
        void onPlay(Channel channel);
        void onFavourite(Channel channel);
    }

    private final List<Channel> channels = new ArrayList<>();
    private final List<Integer> contentHashes = new ArrayList<>();
    private final Listener listener;
    private Set<Integer> favourites;

    public ChannelAdapter(Set<Integer> favourites, Listener listener) {
        this.favourites = favourites == null ? new HashSet<>() : new HashSet<>(favourites);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<Channel> values, Set<Integer> favouriteValues) {
        final List<Channel> oldChannels = new ArrayList<>(channels);
        final List<Integer> oldHashes = new ArrayList<>(contentHashes);
        final List<Channel> nextChannels = values == null ? new ArrayList<>() : new ArrayList<>(values);
        final Set<Integer> nextFavourites = favouriteValues == null
                ? new HashSet<>() : new HashSet<>(favouriteValues);
        final List<Integer> nextHashes = new ArrayList<>(nextChannels.size());
        for (Channel channel : nextChannels) nextHashes.add(contentHash(channel, nextFavourites));

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldChannels.size(); }
            @Override public int getNewListSize() { return nextChannels.size(); }

            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Channel oldItem = oldChannels.get(oldPosition);
                Channel newItem = nextChannels.get(newPosition);
                return oldItem.number == newItem.number && Objects.equals(oldItem.id, newItem.id);
            }

            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return Objects.equals(oldHashes.get(oldPosition), nextHashes.get(newPosition));
            }
        }, false);

        channels.clear();
        channels.addAll(nextChannels);
        contentHashes.clear();
        contentHashes.addAll(nextHashes);
        favourites = nextFavourites;
        diff.dispatchUpdatesTo(this);
    }

    public Channel itemAt(int position) {
        if (position < 0 || position >= channels.size()) return null;
        return channels.get(position);
    }

    @Override public long getItemId(int position) {
        Channel channel = channels.get(position);
        return ((long) channel.number << 32) ^ (channel.id == null ? 0 : channel.id.hashCode());
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(TvUi.dp(context, 12), TvUi.dp(context, 10), TvUi.dp(context, 12), TvUi.dp(context, 9));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(context, 158));
        params.setMargins(TvUi.dp(context, 5), TvUi.dp(context, 5), TvUi.dp(context, 5), TvUi.dp(context, 5));
        card.setLayoutParams(params);
        TvUi.focusCard(card, Color.argb(242, 11, 18, 34), Color.rgb(22, 50, 76), 14);
        View.OnFocusChangeListener focusDecoration = card.getOnFocusChangeListener();

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(TvUi.rounded(
                Color.argb(52, 255, 255, 255), 12,
                Color.argb(25, 255, 255, 255), 1, context));
        top.addView(logo, new LinearLayout.LayoutParams(TvUi.dp(context, 42), TvUi.dp(context, 42)));

        LinearLayout badges = new LinearLayout(context);
        badges.setOrientation(LinearLayout.VERTICAL);
        badges.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView number = TvUi.label(context, "", 13, TvUi.MINT, true);
        number.setGravity(Gravity.CENTER);
        number.setPadding(TvUi.dp(context, 9), 0, TvUi.dp(context, 9), 0);
        number.setBackground(TvUi.rounded(
                Color.argb(136, 0, 0, 0), 13,
                Color.argb(88, 115, 245, 194), 1, context));
        badges.addView(number, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(context, 25)));

        TextView status = TvUi.label(context, "", 8.5f, TvUi.MUTED, true);
        status.setLetterSpacing(.08f);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(context, 16));
        statusParams.topMargin = TvUi.dp(context, 1);
        badges.addView(status, statusParams);

        LinearLayout.LayoutParams badgesParams = new LinearLayout.LayoutParams(0, TvUi.dp(context, 43), 1f);
        badgesParams.leftMargin = TvUi.dp(context, 8);
        top.addView(badges, badgesParams);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(context, 43)));

        TextView name = TvUi.label(context, "", 15, TvUi.TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        nameParams.topMargin = TvUi.dp(context, 4);
        card.addView(name, nameParams);

        TextView now = TvUi.label(context, "", 10.5f, TvUi.TEXT, false);
        now.setSingleLine(true);now.setEllipsize(TextUtils.TruncateAt.END);
        TextView next = TvUi.label(context, "", 10.5f, TvUi.CYAN, false);
        next.setSingleLine(true);next.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(now,new LinearLayout.LayoutParams(-1,TvUi.dp(context,18)));
        card.addView(next,new LinearLayout.LayoutParams(-1,TvUi.dp(context,18)));
        TextView meta = TvUi.label(context, "", 9.5f, TvUi.MUTED, false);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(context, 17)));
        return new Holder(card, logo, number, status, name, meta, now, next, focusDecoration);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Channel channel = channels.get(position);
        boolean favourite = favourites.contains(channel.number);
        holder.number.setText((favourite ? "★  " : "") + channel.displayNumber());
        holder.name.setText(channel.name);
        holder.meta.setText(channel.language + "  •  " + channel.category);
        bindProgrammes(holder,channel);

        if (channel.isSubscriptionChannel() && channel.isAvailable()) {
            holder.status.setText("INCLUDED");
            holder.status.setTextColor(TvUi.MINT);
        } else if (channel.isSubscriptionChannel()) {
            holder.status.setText("SUBSCRIPTION");
            holder.status.setTextColor(TvUi.AMBER);
        } else if (channel.isUnavailable()) {
            holder.status.setText("NEEDS ATTENTION");
            holder.status.setTextColor(TvUi.ERROR);
        } else if (channel.isAvailable()) {
            holder.status.setText("WORKING");
            holder.status.setTextColor(TvUi.MINT);
        } else {
            holder.status.setText("LIVE");
            holder.status.setTextColor(TvUi.CYAN);
        }

        if (channel.logoUrl == null || channel.logoUrl.isEmpty()) {
            Glide.with(holder.logo).clear(holder.logo);
            holder.logo.setImageDrawable(null);
            holder.logo.setContentDescription(channel.name);
        } else {
            Glide.with(holder.logo)
                    .load(channel.logoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .thumbnail(.25f)
                    .fitCenter()
                    .into(holder.logo);
        }

        String accessDescription = channel.accessLabel().isEmpty()
                ? "live channel" : channel.accessLabel().toLowerCase(java.util.Locale.ROOT);
        holder.itemView.setContentDescription(
                channel.displayNumber() + " " + channel.name + ", "
                        + channel.language + ", " + accessDescription);
        holder.itemView.setOnClickListener(view -> listener.onPlay(channel));
        holder.itemView.setOnLongClickListener(view -> {
            listener.onFavourite(channel);
            return true;
        });
        holder.itemView.setOnFocusChangeListener((view, focused) -> {
            if (holder.focusDecoration != null) holder.focusDecoration.onFocusChange(view, focused);
            if (focused) {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onFocused(channel, adapterPosition);
                }
            }
        });
    }

    private void bindProgrammes(Holder h,Channel c){
        h.now.setText("NOW  "+(c.nowTitle==null||c.nowTitle.isEmpty()?"Schedule not listed":c.nowTitle));
        h.next.setText("NEXT  "+(c.nextTitle==null||c.nextTitle.isEmpty()?"Not listed by provider":c.nextTitle));
    }
    public void updateProgramme(String id,String now,String next){
        for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(id)){
            Channel c=channels.get(i);c.nowTitle=now;c.nextTitle=next;
            int hash=contentHash(c,favourites);if(contentHashes.get(i)!=hash){contentHashes.set(i,hash);notifyItemChanged(i,"epg");}break;
        }
    }
    @Override public void onBindViewHolder(@NonNull Holder h,int position,@NonNull List<Object> payloads){
        if(!payloads.isEmpty()&&payloads.contains("epg")){bindProgrammes(h,channels.get(position));return;}
        super.onBindViewHolder(h,position,payloads);
    }
    @Override public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.logo).clear(holder.logo);
        super.onViewRecycled(holder);
    }

    @Override public int getItemCount() { return channels.size(); }

    private static int contentHash(Channel channel, Set<Integer> favourites) {
        return Objects.hash(
                channel.number,
                channel.id,
                channel.name,
                channel.language,
                channel.category,
                channel.logoUrl,
                channel.accessState,
                channel.accessMessage,channel.nowTitle,channel.nextTitle,
                channel.requiresSubscription,
                channel.subscriptionHint,
                favourites.contains(channel.number));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView number;
        final TextView status;
        final TextView name;
        final TextView meta,now,next;
        final View.OnFocusChangeListener focusDecoration;

        Holder(View itemView, ImageView logo, TextView number, TextView status,
               TextView name, TextView meta, TextView now, TextView next, View.OnFocusChangeListener focusDecoration) {
            super(itemView);
            this.logo = logo;
            this.number = number;
            this.status = status;
            this.name = name;
            this.meta = meta;this.now=now;this.next=next;
            this.focusDecoration = focusDecoration;
        }
    }
}
