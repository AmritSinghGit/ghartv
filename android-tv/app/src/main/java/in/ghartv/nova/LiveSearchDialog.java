package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Home search: type, see Jio and public IPTV channels in one list, OK on a row plays it (two presses from the dialog). Films stay a separate button. */
final class LiveSearchDialog {
    private static List<IptvChannel> iptvCache = new ArrayList<>();

    static void show(Activity activity, List<Channel> jio, Consumer<Channel> playJio) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Channel name or number");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        ListView list = new ListView(activity);
        list.setItemsCanFocus(false);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        box.addView(list, new LinearLayout.LayoutParams(-1, TvUi.dp(activity, 300)));
        List<LiveSearch.Hit> hits = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Search live TV · Jio and public IPTV")
            .setView(box).setNeutralButton("Films", null).setNegativeButton("Close", null).create();
        Runnable refresh = () -> {
            hits.clear();
            hits.addAll(LiveSearch.search(jio, iptvCache, input.getText().toString()));
            adapter.clear();
            for (LiveSearch.Hit h : hits) adapter.add(h.name() + "  ·  " + h.source() + (h.alternates > 0 ? " (+ backup)" : ""));
            adapter.notifyDataSetChanged();
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refresh.run(); }
        });
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH && !hits.isEmpty()) { open(activity, dialog, hits.get(0), playJio); return true; }
            return false;
        });
        list.setOnItemClickListener((p, v, pos, id) -> { if (pos < hits.size()) open(activity, dialog, hits.get(pos), playJio); });
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            String q = UnifiedSearch.clean(input.getText().toString());
            dialog.dismiss();
            if (UnifiedSearch.valid(q)) UnifiedSearch.open(activity, q); else UnifiedSearch.dialog(activity);
        }));
        dialog.show();
        input.requestFocus();
        new Thread(() -> {                                    // cached playlist, no wait on the network when a copy exists
            try {
                List<IptvChannel> loaded = new IptvRepository(activity.getApplicationContext()).load(false).channels;
                activity.runOnUiThread(() -> { iptvCache = loaded; if (!activity.isFinishing()) refresh.run(); });
            } catch (Exception ignored) { /* Jio results still work */ }
        }, "live-search-iptv").start();
    }

    private static void open(Activity activity, AlertDialog dialog, LiveSearch.Hit hit, Consumer<Channel> playJio) {
        dialog.dismiss();
        if (hit.jio != null) { playJio.accept(hit.jio); return; }
        try {
            activity.startActivity(new Intent(activity, IptvPlayerActivity.class)
                .putExtra(IptvPlayerActivity.EXTRA_CHANNEL_JSON, hit.iptv.toJson().toString()));
        } catch (Exception e) { Toast.makeText(activity, "Could not open this public channel", Toast.LENGTH_LONG).show(); }
    }
}
