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
