# GharTV · Jio Live

A remote-first Android TV / Google TV client for live television available to a connected JioTV account.

> **TV test candidate:** `0.5.1-tv-test` (`versionCode 8`). This is an owner-review build, not an official Jio application and not yet an owner-approved stable release.

## What it does

- asks for the Jio mobile number and OTP inside the television app;
- stores the resulting session locally using Android Keystore encryption;
- loads the Jio mobile live-channel catalogue and programme guide;
- presents a large, D-pad-first television guide with search, filters and favourites;
- supports direct number tuning, Channel Up/Down, GUIDE and INFO;
- plays account-authorised HLS or DASH streams through AndroidX Media3 and configures Widevine when returned by the service;
- checks a signed-release manifest for later GharTV updates.

## What it does not do

- no YouTube channel shortcuts;
- no free-web directory;
- no Fastway/WAVES/provider launcher;
- no M3U importer;
- no embedded or repackaged JioTV APK;
- no subscription, DRM, geographic or device-policy bypass.

## Install on a television

After the first GitHub release is published, open this address on the television:

```text
https://amritsinghgit.github.io/ghartv/
```

The direct latest APK address is:

```text
https://github.com/AmritSinghGit/ghartv/releases/latest/download/GharTV-Jio-Live.apk
```

Detailed steps are in [`INSTALL_ON_HISENSE_E6N.md`](INSTALL_ON_HISENSE_E6N.md).

## Build locally

Requirements:

- JDK 17;
- Android SDK Platform 36 and Build Tools 36.0.0;
- Gradle 8.11.1;
- Android Gradle Plugin 8.10.1.

Debug build:

```bash
cd android-tv
./gradlew :app:assembleDebug
```

Release builds require the four `GHARTV_SIGNING_*` environment variables described by the release script. Signing material must remain outside the repository.

## Project identity

- Package: `in.ghartv.nova`
- Canonical local continuation path: `~/Downloads/GharTV_Nova_v0.4.2`
- Canonical lane: `ghartv`
- GitHub source authority after publication: `AmritSinghGit/ghartv`
- Operon classification: independent **project**, not a tenant

See [`OPERON_PROJECT.md`](OPERON_PROJECT.md) for the continuity boundary.

## Upstream and trademark notice

The direct account/catalogue/playback architecture was informed by the MIT-licensed `dineshintry/plugin.kodi.jiotv` project. GharTV is a separate native Android TV implementation. Jio and JioTV are trademarks of their respective owner. This repository is not commissioned, endorsed or supported by Jio.
