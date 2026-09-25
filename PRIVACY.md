# GharTV privacy notice

GharTV is designed for household television use. Version 0.5.3 introduces optional
privacy-filtered diagnostics so technical failures can be fixed in later releases.

## Jio data used on the television

The user enters a Jio mobile number and one-time password to connect the JioTV account.
The OTP is used for verification and is not stored. The mobile number and resulting Jio
session remain on the television; session material is encrypted through Android Keystore.
The app also stores its channel catalogue, favourites, last channel and category locally.

## Privacy-filtered diagnostics are opt-in

No diagnostics are sent until a person explicitly chooses **Share diagnostics**. The
choice can be changed at any time under **Jio account → Diagnostics & privacy**.

When enabled, GharTV can send:

- app version and a random, hashed installation identifier;
- TV manufacturer/model, Android version, locale and broad network type;
- screen/feature events and guide/update outcomes;
- HLS/DASH/DRM flags, playback startup timing and buffering counts;
- error stage/type, HTTP status, scrubbed message, short code stack frames, fingerprint and report reference;
- the Jio channel ID, language and category only when that channel fails.

GharTV never sends the Jio mobile number, OTP, passwords, account identifiers, Jio
tokens/cookies, stream/manifest/licence URLs, request headers, successful channel
viewing history, programme titles, IP address, Wi-Fi name, MAC address, Android ID,
advertising ID or TV serial number.

## Processing and retention

The app strips sensitive fields before reports enter its private local queue. The
GharTV collector validates and scrubs each event again. Accepted reports are retained
for no more than 30 days. The collector does not intentionally read or store the
connection IP address. Its infrastructure provider may process standard connection
metadata as part of operating and protecting the service.

## User controls and deletion

From **Jio account → Diagnostics & privacy**, a user can:

- turn diagnostics on or off;
- preview unsent reports;
- send queued reports;
- delete every unsent report and reset the random diagnostic ID.

This local action does not delete reports already accepted by the collector. Accepted reports are automatically deleted within 30 days; resetting the diagnostic ID prevents later reports from using the previous pseudonymous identifier.

Turning diagnostics off immediately deletes the unsent local queue. Android **Clear
data** or uninstall removes all remaining local GharTV data. Use **Jio account → Sign
out** to remove the encrypted Jio session and cached guide without uninstalling.

## Local living-room suggestions

GharTV v0.5.4 remembers successful channel numbers, recency and coarse watch-time
buckets locally on the television so it can offer **For you**, **Continue** and
**Recent**. A failed tune attempt is never promoted. This local history is account-
scoped, automatically bounded, can be reset from the Jio account menu, and is not
read or uploaded by GharTV diagnostics. Successful channel names/IDs and programme
history remain excluded from telemetry.


## Discover, shared search and voice — Review 37

This is an independent, limited-evaluation user-interface build. Public rollout and
active commercial marketing are on hold. It is not affiliated with FlixMomo or the
rights holders of listed content unless an express agreement says otherwise.

Opening **Discover** loads a provider page in the app's WebView to read its visible
poster suggestions. GharTV displays only returned title/artwork/metadata; it does not
substitute a fabricated catalogue when the provider is unavailable or asks for
verification. Poster images may be fetched directly from the provider or
`image.tmdb.org`. The native poster loader uses memory caching, not a persistent
movie download library; the embedded site itself may use ordinary WebView cache.

Typed searches and text returned from the TV's voice recognizer use the same query
route. Locally cached live-channel matches stay separate from provider film results.
The film query is sent to FlixMomo. FlixMomo and any media/image hosts receive normal
connection data, including IP address, and have their own policies. Search phrases,
film selections, provider cookies and voice audio are not added to GharTV diagnostics.

Voice starts only after a Voice button or supported remote voice action. The device's
installed recognition service handles audio and may process it online according to
its own policy. GharTV receives text, not a retained recording. Users may cancel or
use typing. System-wide voice routing outside GharTV is not configured by this app.

Provider login and watchlist changes happen in the provider page/profile. GharTV
neither claims that the provider's watchlist is its own cloud playlist nor copies
provider credentials into analytics. **Discover → Privacy & content use → Clear film
site data** removes film-browser cookies and site storage, clears the current film
browser cache/history and in-memory poster suggestions, and returns to a blank
Discover state. Refresh is required before loading suggestions again. This does not
clear the separate encrypted Jio session or live-channel favourites, and cannot
delete data already held by an independent third-party provider.

This build uses **direct HTTPS, not Tor**. It does not claim anonymous browsing,
universal source availability, entitlement bypass or verified third-party licences.
A page loading, a result appearing, or an APK being downloaded is not proof that a
video played, a device installed the app, or the media can lawfully be reused.

## Content use and contact during evaluation

Third-party artwork, titles, brands and audiovisual works remain the property of
their respective rights holders. GharTV provides an experimental interface for
navigation and user-experience evaluation; it does not acquire rights merely by
linking, attribution or calling the build a review. Public accessibility does not
mean public domain. Users and maintainers must comply with applicable law, licences,
provider terms and access requirements. This notice is not a guarantee against claims
and does not shift all responsibility to a provider.

For this limited review, direct privacy/rights concerns to the maintainer who supplied
the build. Do not send passwords, tokens or private viewing details in public GitHub
issues. A monitored public rights/privacy contact, operational complaint process and
professional legal/privacy review are launch gates, not already-delivered protections.
