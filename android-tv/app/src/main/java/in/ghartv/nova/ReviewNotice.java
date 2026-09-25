package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

/** Accurate review notice, not a warranty or an assertion of legal immunity. */
final class ReviewNotice {
    static final String TEXT="GharTV is an independent experimental interface for reviewing television and media-discovery user experience. This build is for evaluation, not a public commercial streaming launch.\n\n"
        +"Third-party titles, artwork, brands and audiovisual works belong to their respective rights holders. Displaying a catalogue entry does not establish that its media is licensed, available, or free to use. GharTV is not affiliated with those providers unless expressly stated. This notice does not replace permission, provider terms, or applicable law.\n\n"
        +"Films: opening Discover loads public provider suggestions. Searching sends your entered or recognized text to FlixMomo. Provider pages and media hosts receive normal connection data, including your IP address. Public poster images may also load directly from the provider or image.tmdb.org. GharTV does not upload these search terms to its diagnostics collector.\n\n"
        +"Voice is activated only when you choose Voice or a supported remote voice key. The TV's installed recognition service handles the audio and may process it online under its own policy. GharTV receives text and does not record/store the audio. Cancel or use typing at any time.\n\n"
        +"Provider login, cookies and local browser storage remain in this app's WebView profile; we do not copy them into GharTV diagnostics. Provider watchlist changes may require that provider's account and are not a separate GharTV playlist. Poster rendering uses a temporary memory cache, not a downloaded movie library.\n\n"
        +"Live-TV account/session and favourites retain the existing local protections. Optional technical diagnostics remain off until consent; manage them in Jio account → Diagnostics & privacy. Existing accepted reports are subject to the stated collector retention policy, not deleted merely by switching diagnostics off.\n\n"
        +"Connection: direct HTTPS. Tor is not enabled in this build. The app does not bypass DRM, subscriptions, access checks or security verification. A third-party site being accessible is not a guarantee of rights or safety.\n\n"
        +"For this review, send feedback or rights/privacy concerns to the maintainer who supplied the build. Do not include passwords or account tokens. A monitored public contact and legal/privacy review are required before public launch.";
    static void show(Activity activity,Runnable clear){new AlertDialog.Builder(activity).setTitle("Privacy & content use · Review 37").setMessage(TEXT).setPositiveButton("Close",null)
        .setNeutralButton("Clear film site data",(d,w)->new AlertDialog.Builder(activity).setTitle("Clear film site data?").setMessage("This signs out of websites used in GharTV's film browser and clears its cookies/site storage. It does not clear your JioTV session or live-channel favourites.")
            .setPositiveButton("Clear",(a,b)->{CookieManager.getInstance().removeAllCookies(removed->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();clear.run();});}).setNegativeButton("Cancel",null).show()).show();}
}
