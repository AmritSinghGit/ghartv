package in.ghartv.nova;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Horizontal television views with stable IDs, counts and incremental updates. */
public final class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.Holder> {
    public interface Listener { void onSelected(String value); }

    private final List<String> values = new ArrayList<>();
    private final Listener listener;
    private final Map<String, Integer> counts = new HashMap<>();
    private String selected = ChannelIndex.VIEW_FOR_YOU;

    public ChipAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<String> newValues, String selected) {
        submit(newValues, selected, Collections.emptyMap());
    }

    public void submit(List<String> newValues, String selected, Map<String, Integer> newCounts) {
        final List<String> oldValues = new ArrayList<>(values);
        final Map<String, Integer> oldCounts = new HashMap<>(counts);
        final String oldSelected = this.selected;
        final List<String> nextValues = newValues == null ? new ArrayList<>() : new ArrayList<>(newValues);
        final Map<String, Integer> nextCounts = newCounts == null ? new HashMap<>() : new HashMap<>(newCounts);
        final String nextSelected = selected == null ? ChannelIndex.VIEW_FOR_YOU : selected;

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldValues.size(); }
            @Override public int getNewListSize() { return nextValues.size(); }

            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return Objects.equals(oldValues.get(oldPosition), nextValues.get(newPosition));
            }

            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                String oldValue = oldValues.get(oldPosition);
                String newValue = nextValues.get(newPosition);
                return Objects.equals(oldCounts.get(oldValue), nextCounts.get(newValue))
                        && Objects.equals(oldValue, oldSelected) == Objects.equals(newValue, nextSelected);
            }
        }, false);

        values.clear();
        values.addAll(nextValues);
        counts.clear();
        counts.putAll(nextCounts);
        this.selected = nextSelected;
        diff.dispatchUpdatesTo(this);
    }

    @Override public long getItemId(int position) { return values.get(position).hashCode(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView chip = TvUi.label(parent.getContext(), "", 12.5f, TvUi.TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(TvUi.dp(parent.getContext(), 17), 0, TvUi.dp(parent.getContext(), 17), 0);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(parent.getContext(), 40));
        params.setMargins(0, 0, TvUi.dp(parent.getContext(), 9), 0);
        chip.setLayoutParams(params);
        return new Holder(chip);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        String value = values.get(position);
        TextView chip = (TextView) holder.itemView;
        Integer count = counts.get(value);
        chip.setText(count == null ? value : value + "  " + count);
        chip.setContentDescription(count == null ? value : value + ", " + count + " channels");
        boolean active = value.equals(selected);
        int normal = active ? Color.rgb(20, 100, 105) : Color.rgb(7, 24, 36);
        int focused = active ? Color.rgb(31, 146, 141) : Color.rgb(18, 75, 91);
        TvUi.focusCard(chip, normal, focused, 21);
        holder.focusDecoration = chip.getOnFocusChangeListener();
        chip.setTextColor(active ? TvUi.MINT : TvUi.TEXT);
        chip.setOnClickListener(v -> listener.onSelected(value));
        chip.setOnFocusChangeListener((view, hasFocus) -> {
            if (holder.focusDecoration != null) holder.focusDecoration.onFocusChange(view, hasFocus);
            if (hasFocus) holder.lastFocusedAt = System.currentTimeMillis();
        });
    }

    @Override public int getItemCount() { return values.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        View.OnFocusChangeListener focusDecoration;
        long lastFocusedAt;
        Holder(TextView item) { super(item); }
    }
}
