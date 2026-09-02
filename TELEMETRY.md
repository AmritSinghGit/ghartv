# GharTV privacy-filtered diagnostics

Version 0.5.3 adds an opt-in, first-party diagnostics system to the existing GharTV
project. It is intended to answer concrete product questions: which release/device
combinations fail, which Jio playback stage fails, which HTTP codes recur, how long
playback takes to become ready, how often buffering occurs, and whether update or guide
refresh flows work.

## Consent and control

Diagnostics are off until a person on the TV explicitly chooses **Share diagnostics**.
The choice can be changed from **Jio account → Diagnostics & privacy**. That screen also
supports send now, local preview, queue deletion and diagnostic-ID reset.

## Data sent

- GharTV version and pseudonymous random installation hash;
- TV manufacturer/model, Android version, locale and broad network type;
- screen and feature-use events;
- catalogue and software-update success/failure and duration;
- HLS/DASH, DRM flag, playback-startup timing and buffering counts;
- error type, stage, HTTP code, scrubbed message, short code stack frames, fingerprint and report reference;
- Jio channel ID, language and category only when that channel fails.

## Data never sent

- Jio mobile number or OTP;
- password, account ID, Jio auth/refresh/SSO token or cookie;
- stream, manifest or DRM licence URL, key or request headers;
- successful channel IDs/names, programme titles or viewing history;
- IP address, Wi-Fi name, MAC address, Android ID, advertising ID or TV serial number.

The app scrubs data before it enters its local JSONL queue. The Cloudflare Worker applies
an independent second scrub, validates the event schema, caps payload/rate sizes,
deduplicates event IDs and does not read or store `CF-Connecting-IP`.

## Retention

The collector deletes accepted events after 30 days. Unsent data remains only in the
app-private GharTV directory and is capped at 500 events / approximately 512 KiB.

## Operator access

The public app holds only a low-privilege ingest key. Summary and export routes require
an admin token that is stored outside Git and outside the APK at:

```text
~/Library/Application Support/GharTV/telemetry/collector.env
```

Run `GHARTV_TELEMETRY_REPORT.command` on the authorised Mac to open a local report.
