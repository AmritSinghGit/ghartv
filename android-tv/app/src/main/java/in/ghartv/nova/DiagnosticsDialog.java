package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public final class DiagnosticsDialog {
    private DiagnosticsDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        boolean enabled = Telemetry.isEnabled(activity);
        int queued = Telemetry.queuedCount(activity);
        long last = Telemetry.lastUploadAt(activity);
        String lastText = last <= 0 ? "Never" : DateFormat.getDateTimeInstance().format(new Date(last));
        String message = "Technical diagnostics: " + (enabled ? "ON" : "OFF")
                + "\nReports waiting: " + queued
                + "\nLast successful send: " + lastText
                + "\nStatus: " + Telemetry.lastStatus(activity);
        String[] items = new String[]{
                enabled ? "Turn technical diagnostics off" : "Turn technical diagnostics on",
                "Send queued diagnostics now",
                "Preview queued diagnostics",
                "Delete queued diagnostics and reset ID",
                "What GharTV collects",
                "Open privacy policy"
        };
        new AlertDialog.Builder(activity)
                .setTitle("Diagnostics & privacy")
                .setMessage(message)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (enabled) confirmDisable(activity); else confirmEnable(activity);
                            break;
                        case 1:
                            if (!Telemetry.isEnabled(activity)) {
                                Toast.makeText(activity, "Turn diagnostics on first", Toast.LENGTH_LONG).show();
                            } else if (Telemetry.queuedCount(activity) == 0) {
                                Toast.makeText(activity, "No diagnostics are waiting", Toast.LENGTH_SHORT).show();
                            } else {
                                Telemetry.enqueueUpload(activity, true);
                                Toast.makeText(activity, "Diagnostics will send when the TV is online", Toast.LENGTH_LONG).show();
                            }
                            break;
                        case 2:
                            showPreview(activity);
                            break;
                        case 3:
                            confirmDelete(activity);
                            break;
                        case 4:
                            showPrivacyDetails(activity);
                            break;
                        case 5:
                            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.PRIVACY_PAGE)));
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static void confirmEnable(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Share technical diagnostics?")
                .setMessage(
                        "GharTV will send privacy-filtered technical usage and error reports. Failed channel reports can include the Jio channel ID needed to reproduce the failure.\n\n"
                                + "It never sends your mobile number, OTP, passwords, Jio tokens, cookies, stream/licence URLs, IP address, Wi-Fi name, successful channel viewing history, Android ID or TV serial number."
                )
                .setPositiveButton("Turn on", (dialog, which) -> {
                    Telemetry.setEnabled(activity, true);
                    Toast.makeText(activity, "Technical diagnostics are on", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void confirmDisable(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Turn diagnostics off?")
                .setMessage("Queued diagnostics that have not been sent will be deleted immediately.")
                .setPositiveButton("Turn off and delete", (dialog, which) -> {
                    Telemetry.setEnabled(activity, false);
                    Toast.makeText(activity, "Diagnostics are off and the local queue was deleted", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void confirmDelete(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete queued diagnostics?")
                .setMessage("This deletes every unsent local report and generates a new random diagnostic ID. It does not sign out of JioTV or remove favourites. Reports already accepted by the collector are not removed by this local action and expire automatically within 30 days.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Telemetry.clearQueuedEvents(activity, true);
                    Toast.makeText(activity, "Local diagnostics and diagnostic ID deleted", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showPreview(Activity activity) {
        TextView text = new TextView(activity);
        text.setText(Telemetry.preview(activity));
        text.setTextSize(14);
        text.setTextColor(TvUi.TEXT);
        text.setPadding(TvUi.dp(activity, 22), TvUi.dp(activity, 18), TvUi.dp(activity, 22), TvUi.dp(activity, 18));
        text.setMovementMethod(new ScrollingMovementMethod());
        text.setTextIsSelectable(true);
        new AlertDialog.Builder(activity)
                .setTitle("Diagnostics waiting to send")
                .setView(text)
                .setPositiveButton("Send now", (dialog, which) -> Telemetry.enqueueUpload(activity, true))
                .setNegativeButton("Close", null)
                .show();
    }

    public static void showPrivacyDetails(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("GharTV diagnostics")
                .setMessage(
                        "Collected only after you opt in:\n"
                                + "• app version and pseudonymous installation hash\n"
                                + "• TV manufacturer/model, Android version, language and connection type\n"
                                + "• screen and feature-use events\n"
                                + "• guide/update success or failure and timing\n"
                                + "• playback protocol, DRM flag, startup/buffering measurements\n"
                                + "• errors, short code stack frames, HTTP status and a report reference\n"
                                + "• Jio channel ID only when that channel fails\n\n"
                                + "Never collected:\n"
                                + "• mobile number or OTP\n"
                                + "• passwords, account IDs, Jio tokens or cookies\n"
                                + "• stream/manifest/licence URLs or request headers\n"
                                + "• successful channel names/IDs or programme titles\n"
                                + "• IP address, Wi-Fi name, MAC, Android ID or TV serial number\n\n"
                                + "Unsent data is visible and deletable on this TV. The collector keeps accepted events for no more than 30 days."
                )
                .setPositiveButton("Close", null)
                .show();
    }
}
