# GharTV change history

## 0.5.4 RC2 — Living-room reliability

- Corrected the player overlay so retained TV focus cannot keep it visible forever.
- Added a hard buffer watchdog and one fresh-stream recovery before failure actions.
- Added exact visible-scope CH +/- for language, genre, favourites, personal and search views.
- Added Next working channel to subscription, access, auth, ended-stream and playback recovery.
- Added current/compatibility playback-field normalisation so 403 classification is consistent.
- Added expiring access evidence so transient failures do not permanently hide channels.
- Added local-only For you, Continue and Recent from successful playback only.
- Added cached O(1) number lookup, ranked global Unicode search and category counts.
- Added per-view focus memory, responsive four-column cards and incremental adapter updates.
- Added owner logs/health, stable publish and review-close commands.
- Kept v0.5.3 as the advertised stable update during review.

## 0.5.2-tv-feedback

Physical Hisense E6N feedback correction.

- Replaced the permanent playback strip with an auto-hiding, interactive Now/Next programme panel.
- Added current programme time range, progress and next programme details.
- Added Previous, Guide and Next actions directly in the player panel.
- Preserved the selected guide scope during Channel Up/Down navigation.
- Added separate Subscription and Jio access categories and badges.
- Added Next channel to subscription, access and playback failure dialogs.
- Added one automatic Jio session/playback URL refresh for 403 responses.
- Persistently classifies only repeated account/device access failures and clears them after successful playback.
- Retained package identity, signing identity, repository, main branch, project path and continuity lane.

## 0.5.1-tv-test

First release candidate installed on the physical Hisense E6N.

- Polished Jio OTP, live guide, cards and player for television.
- Added signed same-package updates and the GitHub installation page.
- Published `AmritSinghGit/ghartv` and release `v0.5.1`.

## 0.5.0-jio-only

- Removed all web/YouTube/provider/M3U sources.
- Made Jio OTP, catalogue, EPG, guide and direct playback the only product path.

## Rejected owner-review iterations retained as history only

- 0.1 Cable Mode: provider launcher.
- 0.2 Phone Mode: mobile-app control layer.
- 0.3 One Guide: mixed provider and free-web guide.
- 0.4.2 Nova: native Jio architecture mixed with free-web/provider sources.

## 0.5.3-observability

- Added explicit opt-in technical diagnostics and crash/error references.
- Added local preview, send, disable, delete and diagnostic-ID reset controls.
- Added privacy-filtered playback/guide/update metrics and failed-channel-only identification.
- Added first-party Cloudflare Worker/D1 collector, 30-day retention and owner report.
- Preserved the Jio-only TV interface, package, signing identity, main branch and ghartv project lane.