# GharTV Jio Live v0.5.3 — observability update

This release adds an explicit opt-in diagnostics system to the existing GharTV application and update path.

## Added

- first-run consent with diagnostics off until accepted;
- local capped queue, offline retry and 12-hour WorkManager delivery;
- app/guide/update/playback/crash diagnostics with error report references;
- playback startup, HLS/DASH, DRM and buffering measurements;
- failed Jio channel ID only when a channel fails;
- Jio account → Diagnostics & privacy controls for preview, send, disable and delete;
- first-party Cloudflare Worker + D1 collector with a second redaction pass, rate limits, deduplication and 30-day deletion;
- owner-only local summary/export report.

## Never collected

Jio mobile number, OTP, passwords, account IDs, auth/refresh/SSO tokens, cookies, stream/manifest/licence URLs, request headers, successful channel names/IDs, programme titles, IP addresses, Wi-Fi names, MAC addresses, Android IDs or TV serial numbers.

## Continuity

Same `AmritSinghGit/ghartv` repository, `main` branch, `in.ghartv.nova` package, signing key and `ghartv` Operon project lane.
