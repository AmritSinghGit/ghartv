# GharTV phone playback repair — owner-review source only (23 November 2026)

**Lane:** ghartv / existing v80 review branch `review-v80-live-sources`
**Review source checkout:** `~/GharTV/review/20261123-live-sources-v80`
**App identity:** `in.ghartv.nova`, versionCode 80, versionName `0.7.6-live-sources-review` (intentionally unchanged from signed owner-review v80).
**Distribution:** NOT published, NOT signed with household key, NOT installed. Existing signed emulator v80 remains unchanged.

## Release state (verified 23 Nov 2026)
Public `main/update/latest.json` offers signed versionCode **78** (`0.7.4-phone-search-review`) from 2026-11-23T09:38:03Z. Its feed explicitly says silent/mandatory install unsupported. Therefore public availability != installed on every TV. The signed v80 already on emulator is owner-review only; do not promote old v80 bytes as if they contained these later fixes.

## Changes made in this review source
- `FilmPhonePlayback.java`: observe a *single visible top-level HTML5 video*, report paused/playing/starting, actual playback position, duration and seek window; request play, pause or bounded seek. Do not fabricate success when media is in an opaque/cross-origin iframe.
- `PhoneLinkServer.java` / `HouseholdPhoneSession.java`: bounded phone seek command `Z <milliseconds>`, action feedback, deduplicated mode notifications, explicit source-list refresh even when same list.
- `FlixMomoActivity.java`: foreground-only player-state polling; make fullscreen/normal Play/Pause and phone seek use a directly observable HTML5 video or an owned Android `VideoView` in the custom fullscreen hierarchy, with fallback scoped media keys for other custom surfaces but no false verification; invalidate stale callbacks on source switch, navigation or same-URL refresh; keep paired phone state and select source without relocating TV focus; fullscreen phone button toggles entry/exit.
- `FilmNativeControls.java` / `FilmSourceCycle.java` / `FilmPlaybackMonitor.java`: manual Next traverses all up to 12 **observed** numbered controls and wraps to the first after exhaustion. Automatic retry never loops. Both TV and phone Next share source rotation.
- `FilmPlaybackProbe.java`: do not scroll the document when already in fullscreen.
- `phone/index.html`: phone seek slider and live current position/seek-end labels, available only for confirmed seekable media; buttons reflect observed state; no unverified stream claims.

## Checks run
- `node --test tools/test-phone-playback.mjs tools/test-phone-protocol.mjs tools/test-source-cycle.mjs`: **8 passed** (pause/resume, timestamp/seek windows, seek bounds, opaque video, message validation, all 12 sources and manual wrap).
- Mobile page inline JavaScript checked with `node --check`: passed.
- `git diff --check`: passed.
- `cd android-tv && ./gradlew :app:assembleDebug :app:assembleAndroidTest --offline --no-daemon --console=plain`: BUILD SUCCESSFUL; this **builds** Android instrumentation tests but does not execute them.
- The existing original-signed v80 installation was **not replaced**, reset, opened, or signed.

## Gating limitations
1. Provider video inside cross-origin/opaque iframes or native custom views might not expose a readable media element, position, or working direct Play/Pause/Seek; video keys may be ignored. Explicitly report unavailable rather than faking sync. No DRM/captcha bypass.
2. Real FlixMomo film watch page with 12 observed sources and actual first frame/seek/fullscreen, plus an actual iPhone/Android and household TV, still need owner UAT. Mocked twelve-source traversal is not evidence 12 streams play.
3. Household signing variables `GHARTV_SIGNING_STORE`, `GHARTV_SIGNING_STORE_PASSWORD`, `GHARTV_SIGNING_KEY_ALIAS`, `GHARTV_SIGNING_KEY_PASSWORD` were **not available in this remote shell**. Do not generate a replacement key or run an in-place debug update. Existing signed v80 APK remains untouched.
4. Publishing needs versionCode increment, exact tested APK SHA-256, original signing certificate verification, user/owner approval, rollback evidence, production feed update and physical-TV acceptance. No claim of deployment or 100% fleet uptake.

## Next owner review
Use the existing original signer and established release method to prepare an exact signed **new version**, then review on the same package and household TV. Check phone paired in FlixMomo; fullscreen Play → Pause → Play, scrub from phone and see TV timeline advance, Source 1→12→1 via explicit Try Next, phone source refresh without TV focus shifting, reload same watch page without reverting player state, and live typing in safe search fields. Do not modify the currently installed v80 as part of preparing this source-only candidate.
