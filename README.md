# GharTV

**Current review candidate:** v0.5.4 RC4 Family Preview — vertical channel tile, on-demand muted preview, family themes, voice/programme search and capability-gated transport controls. · Jio Live

A remote-first Android TV / Google TV client for live television available to a connected JioTV account.

> **Stable:** `0.5.3-observability` (`versionCode 10`). **Current owner-review candidate:** `0.5.4-rc4-family-preview` (`versionCode 13`). GharTV is not an official Jio application.

## Television experience

- Jio mobile-number and OTP connection inside the television app;
- encrypted local session storage through Android Keystore;
- Jio mobile live-channel catalogue, programme guide, search, language/genre filters and favourites;
- direct number tuning, Channel Up/Down, GUIDE and INFO;
- Channel Up/Down constrained to the exact active guide scope;
- an auto-hiding interactive player guide with Now, progress, Next, Previous, Guide and Next-channel actions;
- separate Subscription and Jio access views learned from catalogue and real playback responses;
- one automatic authorisation refresh after a playback 403;
- account-authorised HLS/DASH playback through AndroidX Media3, with Widevine configuration when returned by Jio;
- SHA-256-verified same-package updates from GitHub Releases.

## Boundaries

GharTV includes no YouTube/free-web directory, Fastway/WAVES launcher, M3U importer, repackaged JioTV APK, subscription bypass or DRM key. Jio controls entitlement, availability, geography and device policy.

## Install or update on a television

```text
https://amritsinghgit.github.io/ghartv/
```

Direct latest APK:

```text
https://github.com/AmritSinghGit/ghartv/releases/latest/download/GharTV-Jio-Live.apk
```

Existing installations can use **Jio account → Check for GharTV update**. Release candidates do not change the stable update manifest; Android offers the stable v0.5.4 update only after owner approval and publication with the same package and signing key.

Detailed steps are in [`INSTALL_ON_HISENSE_E6N.md`](INSTALL_ON_HISENSE_E6N.md).

## Build locally

Requirements: JDK 17, Android SDK 36, Build Tools 36.0.0, Gradle 8.11.1 and Android Gradle Plugin 8.10.1.

```bash
cd android-tv
./gradlew :app:assembleDebug
```

Release signing uses `GHARTV_SIGNING_*` environment variables. Signing material remains outside Git.

## Project identity

- Package: `in.ghartv.nova`
- Canonical local continuation path: `~/Downloads/GharTV_Nova_v0.4.2`
- Canonical lane: `ghartv`
- Repository: `AmritSinghGit/ghartv`
- Branch: `main`
- Operon portfolio classification: independent **project**
- Operon Analytics tenant ID: `ghartv` in the existing `operon.analytics` capability

The direct account/catalogue/playback architecture was informed by the MIT-licensed `dineshintry/plugin.kodi.jiotv` project. GharTV is a separate native Android TV implementation and is not commissioned, endorsed or supported by Jio.

## v0.5.3 technical diagnostics

GharTV now includes an explicit opt-in, first-party technical diagnostics system. Users can preview, send, disable or delete queued reports from the TV. Mobile numbers, OTPs, Jio credentials/tokens/cookies, stream/licence URLs, successful channel viewing history and hardware identifiers are excluded. See [TELEMETRY.md](TELEMETRY.md) and [PRIVACY.md](PRIVACY.md).

## v0.5.4 RC4 review candidate

RC4 keeps the long selected-channel tile, adds an explicit muted 15-second preview,
fixes vertical spacing so **Watch Live** remains visible, and provides local family
birthday themes for Mom, Amrit, Harjas, Wifey, Sis, Dad and Simrath. The normal
living-room screen hides internal RC labels.

Voice and typed search use the same indexed channel search, followed by a bounded
on-demand Jio EPG programme search. Pause, rewind, forward and Live are enabled only
when the current Media3 stream exposes the required live-window capability.

RC4 is published only as a prerelease. Stable televisions remain on v0.5.3 until
explicit owner approval. See [PRODUCT_REVIEW_v0.5.4-rc4.md](PRODUCT_REVIEW_v0.5.4-rc4.md),
[REVIEW_CHECKLIST_v0.5.4-rc4.md](REVIEW_CHECKLIST_v0.5.4-rc4.md), and
[REVIEW_AND_PUBLISH_v0.5.4.md](REVIEW_AND_PUBLISH_v0.5.4.md).
