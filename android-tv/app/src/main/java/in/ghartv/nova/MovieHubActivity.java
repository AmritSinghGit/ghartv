package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Official-provider directory; it never collects provider credentials or bypasses DRM. */
public final class MovieHubActivity extends Activity {
    private static final class Provider {
        final String name, detail, packageName, catalogue;
        Provider(String name, String detail, String packageName, String catalogue) {
            this.name = name;
            this.detail = detail;
            this.packageName = packageName;
            this.catalogue = catalogue;
        }
    }

    private static final Provider[] PROVIDERS = {
            new Provider("Chaupal", "Punjabi films and series. A compatible Chaupal plan is required.",
                    "video.laminar.tv.chaupal.android", "https://www.chaupal.com/"),
            new Provider("ZEE5", "Browse the provider's Punjabi catalogue. Availability varies by plan and region.",
                    "com.graymatrix.did", "https://www.zee5.com/movies/lang/punjabi"),
            new Provider("JioHotstar", "Open the current official JioHotstar television service.",
                    "in.startv.hotstar", "https://www.hotstar.com/in")
    };

    private LinearLayout list;
    private String focusProvider = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.immersive(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(TvUi.dp(this, 36), TvUi.dp(this, 22), TvUi.dp(this, 36), TvUi.dp(this, 18));
        root.setBackgroundColor(TvUi.BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(TvUi.label(this, "Punjabi + subscriptions", 30, TvUi.TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button back = TvUi.button(this, "Back to GharTV", false);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(TvUi.dp(this, 175), TvUi.dp(this, 42)));
        root.addView(header);

        TextView note = TvUi.label(this,
                "Your subscription stays with each official provider. GharTV opens its TV app or catalogue; it does not collect that provider's phone number, OTP, password or playback token.",
                14, TvUi.MUTED, false);
        note.setPadding(0, TvUi.dp(this, 12), 0, TvUi.dp(this, 14));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView privacy = TvUi.label(this,
                "App detected does not mean signed in or subscribed. Provider availability, charges, catalogue, resolution and DRM stay under the provider's control.",
                12, TvUi.MUTED, false);
        privacy.setPadding(0, TvUi.dp(this, 10), 0, 0);
        root.addView(privacy);
        setContentView(root);
        Telemetry.screen(this, "provider_directory");
    }

    @Override protected void onResume() {
        super.onResume();
        TvUi.immersive(this);
        render();
    }

    private Intent installedTvIntent(Provider provider) {
        try { return getPackageManager().getLeanbackLaunchIntentForPackage(provider.packageName); }
        catch (RuntimeException ignored) { return null; }
    }

    private void render() {
        list.removeAllViews();
        Button initial = null;
        for (Provider provider : PROVIDERS) {
            boolean installed = installedTvIntent(provider) != null;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(TvUi.dp(this, 20), TvUi.dp(this, 13), TvUi.dp(this, 20), TvUi.dp(this, 13));
            card.setBackground(TvUi.rounded(0xFF112A32, 16, 0xFF34525B, 1, this));
            card.addView(TvUi.label(this, provider.name, 23, TvUi.TEXT, true));
            card.addView(TvUi.label(this, provider.detail, 13, TvUi.MUTED, false));
            card.addView(TvUi.label(this,
                    installed ? "TV app detected · subscription status unknown"
                            : "Compatible TV app not detected · subscription status unknown",
                    12, installed ? TvUi.MINT : TvUi.CYAN, false));

            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, TvUi.dp(this, 8), 0, 0);
            Button open = TvUi.button(this, installed ? "Open official TV app" : "Get official TV app", true);
            open.setOnClickListener(view -> {
                focusProvider = provider.name;
                confirmOpen(provider);
            });
            row.addView(open, new LinearLayout.LayoutParams(0, TvUi.dp(this, 43), 1));
            Button browse = TvUi.button(this, "Official catalogue website", false);
            browse.setOnClickListener(view -> {
                focusProvider = provider.name;
                confirmWebsite(provider);
            });
            LinearLayout.LayoutParams browseParams = new LinearLayout.LayoutParams(0, TvUi.dp(this, 43), 1);
            browseParams.leftMargin = TvUi.dp(this, 12);
            row.addView(browse, browseParams);
            card.addView(row);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = TvUi.dp(this, 12);
            list.addView(card, cardParams);
            if (initial == null || provider.name.equals(focusProvider)) initial = open;
        }
        if (initial != null) initial.requestFocus();
    }

    private void confirmOpen(Provider provider) {
        Intent launch = installedTvIntent(provider);
        new AlertDialog.Builder(this)
                .setTitle(provider.name + " · official service")
                .setMessage("GharTV will open " + (launch != null ? "the installed provider app" : "its official Play Store listing")
                        + ". Sign in there with your own account. Nothing is purchased by this action.")
                .setPositiveButton(launch != null ? "Open app" : "Open store", (dialog, which) -> {
                    if (launch != null) {
                        try { startActivity(launch); return; }
                        catch (ActivityNotFoundException | SecurityException ignored) {}
                    }
                    openUri("market://details?id=" + provider.packageName,
                            "https://play.google.com/store/apps/details?id=" + provider.packageName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmWebsite(Provider provider) {
        new AlertDialog.Builder(this)
                .setTitle("Leave GharTV for " + provider.name + "?")
                .setMessage("Open the provider's official catalogue in an available app or browser. This is not an embedded GharTV player and the provider may request its own login.")
                .setPositiveButton("Open website", (dialog, which) -> openUri(provider.catalogue, null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openUri(String target, String fallback) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target))); }
        catch (ActivityNotFoundException | SecurityException error) {
            if (fallback != null) {
                openUri(fallback, null);
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("No compatible app or browser")
                    .setMessage("Open this official address on your phone, or install the provider's compatible TV app from your television's app store:\n\n" + target)
                    .setPositiveButton("Back", null)
                    .show();
        }
    }
}
