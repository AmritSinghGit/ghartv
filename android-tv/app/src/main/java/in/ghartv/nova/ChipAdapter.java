package in.ghartv.nova;

import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public final class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.Holder> {
    public interface Listener { void onSelected(String value); }

    private final List<String> values = new ArrayList<>();
    private final Listener listener;
    private String selected = "All";

    public ChipAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<String> newValues, String selected) {
        values.clear();
        values.addAll(newValues);
        this.selected = selected;
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView chip = TvUi.label(parent.getContext(), "", 13, TvUi.TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(TvUi.dp(parent.getContext(), 18), 0, TvUi.dp(parent.getContext(), 18), 0);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(parent.getContext(), 42));
        params.setMargins(0, 0, TvUi.dp(parent.getContext(), 10), 0);
        chip.setLayoutParams(params);
        return new Holder(chip);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        String value = values.get(position);
        TextView chip = (TextView) holder.itemView;
        chip.setText(value);
        int normal = value.equals(selected) ? Color.rgb(24, 88, 104) : TvUi.SURFACE;
        TvUi.focusCard(chip, normal, TvUi.SURFACE_3, 25);
        chip.setOnClickListener(v -> listener.onSelected(value));
    }

    @Override public int getItemCount() { return values.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(TextView item) { super(item); }
    }
}
