# GharTV telemetry collector

This Cloudflare Worker is the single telemetry endpoint for the existing GharTV project.
It accepts only the opt-in, privacy-filtered `ghartv.telemetry.batch.v1` schema, stores
accepted events in one D1 database, exposes admin-token-protected summary/export routes,
and deletes events older than 30 days.

The Android app never sends Jio mobile numbers, OTPs, account credentials, tokens,
cookies, stream or licence URLs, IP addresses, Wi-Fi names, successful channel viewing
history, Android IDs or TV serial numbers. The Worker performs a second independent
redaction pass and never reads or stores `CF-Connecting-IP`.

Routes:

- `GET /health`
- `POST /v1/events` with `X-GharTV-Ingest-Key`
- `GET /v1/admin/summary?days=7` with `Authorization: Bearer <admin token>`
- `GET /v1/admin/export?days=7&limit=5000` with the same admin token
- `POST /v1/admin/purge` with the same admin token

`INGEST_KEY` and `ADMIN_TOKEN` are Cloudflare Worker secrets. The ingest key is also
published to the signed app/remote telemetry config and therefore is an abuse-control
key, not a private credential. The admin token is never put in the APK or Git repository.
