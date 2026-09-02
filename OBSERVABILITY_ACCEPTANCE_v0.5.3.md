# GharTV v0.5.3 observability acceptance

## Consent

- Upgrade an existing v0.5.2 installation without clearing data.
- Confirm the first-run diagnostics dialog appears.
- Choose **Not now** and confirm no local queue is created and no upload is sent.
- Open **Jio account → Diagnostics & privacy**, enable diagnostics, and confirm the status changes to on.

## Privacy controls

- Preview the queue and confirm it contains no mobile number, OTP, Jio token, cookie, stream URL, licence URL, programme title, successful channel ID, IP, Wi-Fi name, Android ID or serial number.
- Send the queue manually and confirm the last-send status updates.
- Disable diagnostics and confirm the unsent queue is deleted.
- Delete queued diagnostics/reset the diagnostic ID and confirm the Jio session and favourites remain.

## Operational reporting

- Produce one catalogue-refresh success and one controlled failure.
- Play one HLS and one entitled DASH/Widevine channel.
- Change channels with CH+ and CH-.
- Produce one channel-specific failure and record the on-screen `GH-........` diagnostics reference.
- Run `GHARTV_TELEMETRY_REPORT.command` on the authorised Mac and confirm the report shows the version, device model, event counts and matching failure reference without sensitive data.

## Update request

- Publish release `v0.5.3` before publishing `update/latest.json` versionCode 10.
- On an installed v0.5.2 TV, restart GharTV or choose **Jio account → Check for GharTV update**.
- Confirm Android offers an in-place update signed by the existing GharTV key.
- Confirm the Jio session, favourites, category and last channel survive the update.
