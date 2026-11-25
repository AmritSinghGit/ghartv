# GharTV blue v87 — Tor Web owner review (25 November 2026)

Canonical in.ghartv.nova, existing review-v80-live-sources branch, original signing identity and app data.

## Real device observations
- Samsung Galaxy S23 Ultra USB authorized and Chrome in foreground; blue GharTV v86 open on FlixMomo.
- Android logs at 18:37:44 reported Tor control and HTTP proxy ready. This proves neither provider-page availability nor a playable movie.
- Real TV WebView showed provider net::ERR_TIMED_OUT after Retrying the page through Tor (2/2). Host access is still unverified.
- In the original source, explicit Browser opened the address bar UNFOCUSED. The toolbar then auto-hid after 6500 ms, creating the appearance that Tor never opened. A stale phone film Search state could override Tor Web EDITOR.

## Targeted v87 fixes
- Explicit Browser opens the Tor Web address bar FOCUSED, kept visible while typing, and notifies paired phone EDITOR mode.
- Tor Web EDITOR takes precedence over an obsolete film Search request. No weakening of Tor-only route, TLS, DRM or provider security.
- Existing bounded local FilmReviewJournal now captures Tor-browser open, Tor start, proxy ready, document finished, timeout and failure with relative elapsed milliseconds. No URLs, movie names, raw keystrokes, credentials or network uploads.
- Android debug, AndroidTest and unsigned release assemblies built offline successfully.
- Focused Android instrumented suites: 46/46 passed, including Browser focus beyond 6.5 seconds, input stability, Tor controller policy and private technical-journal millisecond evidence.
- Full Android suite against separate orange isolated test package was initiated; check final result before original blue installation. Do not promote on any failure.

Private signed v87 owner review APK:
~/Library/Application Support/GharTV/owner-review/candidates/GharTV-code87-blue-tor-browser-owner-review-signed.apk
SHA256: 05cefec7264e18d08bae773ea34171b5fc82762757089a7dd6889c759b9c6aba
Release cert SHA256: 40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c

## Owner acceptance (must be observed, not inferred)
1. Fresh QR pair Samsung and blue TV. On TV Browser, ensure Tor address appears and stays visible beyond seven seconds; phone editor and keyboard should follow.
2. A harmless public HTTPS page through Tor should load, or clearly report whether it fails. Tor-ready alone is not a successful page.
3. Enter film yourself. Verify genuine source selection and Try Next, visible video with sound, Play/Pause, seeking, fullscreen and Back on phone.
4. Use the existing local Recent interaction log and playback monitor to inspect technical timing. No private search/title/URL capture.
5. Original household stable update feed remains v80; do not publish to Dad without explicit owner approval.

Unverified until direct evidence: real provider video picture/sound, cross-origin player controls, full Tor web public-site network access and final owner in-place acceptance.

## 25 November — signed blue v87 UAT, recovered network, and AVD cleanup

- On blue v86, Tor Project verification over the app's HTTP proxy returned HTTP 200. The isolated v87 native Tor/WebView test then passed (1/1) after the network recovered.
- The first uninterrupted v87 instrumentation run passed through 31 complete test classes but stalled/crashed launching Review56SimpleHarnessActivity after 45 seconds, at about 13 minutes; ADB became unresponsive. This is a real emulator/test-harness failure and is NOT a clean single-run result.
- The SAME canonical TV emulator was cold-booted using the emulator console, without wiping data. The failed Review56 class plus native Tor check then passed 10/10. All six remaining IPTV/Jio/DNS/one-click classes passed 39/39.
- A deterministic class-by-class audit of the three runs found 394 passing individual tests covering all 38 source test classes. This is COMPLETE TEST COVERAGE ACROSS SEGMENTED RUNS, not a green uninterrupted 394-test run. Audit: owner-review/v87-regression-coverage-20261125.json on the Mac.
- Additional checks passed: 24/24 phone UI/protocol tests, 54/54 Java socket checks, and actual Chrome mobile-layout smoke tests.
- Signed original blue versionCode 87, versionName 0.7.13-tor-web-review, installed IN PLACE over v86. Android verified original firstInstallTime 2026-09-13 17:16:16, same signer and package, and retained data. Only temporary orange fixes/test packages were uninstalled.
- On signed blue v87 FlixMomo opened and reported Tor control plus HTTP proxy ready, but two independent Tor Project HTTPS requests through its signed-app proxy timed out (curl exit 28). Therefore reliable signed-app Tor egress remains UNSOLVED despite the passing isolated native test. Tor-only fail-closed policy must remain.
- Live signed-app Browser issue: a native EditText exists but was not focused. Its bounds subsequently collapsed to zero height; focus resided with WebView/toggle controls. The fixture test that passed focus beyond seven seconds does not prove the real async navigation path works. Do not mark one-tap Tor address editing repaired.
- Actual film picture and audio, source switching, phone seeking, fullscreen and Back remain unverified on signed v87. Do not infer real playback from fixtures or Tor startup.
- Production Dad/family update feed remains versionCode 80. No public release or household update.

### Emulator space cleanup

- Official avdmanager deleted the two obsolete TV devices GharTV_Google_TV_API_36 and GharTV_Google_TV_API36_google_tv, approximately 5.3 GiB combined. Reproducible definitions were saved at ~/Library/Application Support/GharTV/owner-review/avd-cleanup-20261125/; old snapshots and old virtual userdata were not backed up.
- Preserved the active GharTV_Nova_Manual_google_tv_API36, the recent Nova predecessor, the GharTV phone test emulator and Pixel_9.

### Remaining Dad-release gates

Fix Browser address focus and bar visibility on actual signed WebView under asynchronous provider navigation; stabilize Tor outbound HTTPS without direct-route fallback; address emulator long-run stability and get a clean continuous or formally sharded complete test gate; then observe a real owner-selected movie with picture, sound and all phone playback controls. Private v87 owner review is permitted; family promotion is not.
