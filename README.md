# GharTV · Jio Live

A remote-first Android TV / Google TV client for live television available to a connected JioTV account.

> **Stable:** `0.5.3-observability` (`versionCode 10`). **Current owner-review candidate:** `0.5.4-rc2-living-room` (`versionCode 11`). GharTV is not an official Jio application.

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
- Operon classification: independent **project**, not a tenant

The direct account/catalogue/playback architecture was informed by the MIT-licensed `dineshintry/plugin.kodi.jiotv` project. GharTV is a separate native Android TV implementation and is not commissioned, endorsed or supported by Jio.

## v0.5.3 technical diagnostics

GharTV now includes an explicit opt-in, first-party technical diagnostics system. Users can preview, send, disable or delete queued reports from the TV. Mobile numbers, OTPs, Jio credentials/tokens/cookies, stream/licence URLs, successful channel viewing history and hardware identifiers are excluded. See [TELEMETRY.md](TELEMETRY.md) and [PRIVACY.md](PRIVACY.md).

## v0.5.4 RC2 review candidate

RC2 turns the catalogue into a living-room home: **For you**, **Continue**, **Recent**,
**Favourites**, proven **Working now** channels, languages, genres, Subscription and
Needs attention. It adds constant-time number lookup, ranked global search, category
counts, per-view focus memory, incremental card updates, exact-scope CH +/-, a true
remote-inactivity player overlay, buffering recovery and clearer error actions.

RC2 is published as a prerelease. The stable TV update remains v0.5.3 until explicit
owner approval. See [PRODUCT_REVIEW_v0.5.4-rc2.md](PRODUCT_REVIEW_v0.5.4-rc2.md)
and [REVIEW_AND_PUBLISH_v0.5.4.md](REVIEW_AND_PUBLISH_v0.5.4.md).
