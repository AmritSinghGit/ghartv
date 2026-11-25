# Blue GharTV v86 — private landscape-keyboard review

Date: 25 November 2026. Original canonical package: in.ghartv.nova. Existing review branch, no new app.

## Reproduced with real Samsung USB debugging
- Samsung S23 Ultra was authorized and paired to blue GharTV in Chrome, landscape orientation with SwiftKey keyboard open.
- Measured visual viewport height: 83 CSS px, offsetTop 215. Input/Search y=184..220, nearly invisible above keyboard; Backspace and Clear stayed in view.
- A real Chrome DevTools trial that centered the INPUT rather than the entire editor moved the visual viewport offsetTop to 161. Actual screenshot verified that input and Search became visible.
- Implemented only the targeted fix for Search/Editor on visualViewport resize; no scroll on touch start/blur, preserving the earlier dropped-click fix.

## Evidence and release gate
- 24/24 phone interface and protocol tests passed, including new landscape keyboard and prior Search/Play/source tests.
- Chrome mobile UI smoke passed at 390px and 320px, testing 12 sources, playback controls, seek, fullscreen, and privacy diagnostics.
- Offline Gradle debug, AndroidTest and release APK builds passed.
- Signed original-package v86 review APK: ~/Library/Application Support/GharTV/owner-review/candidates/GharTV-code86-blue-landscape-keyboard-owner-review-signed.apk
- SHA256: 274b71078e7d450cb9acf1d31cdcb9e95d5e4af4182a97ba74fba503e959279d
- Original release signing certificate SHA256: 40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c
- Full final Android instrumentation suite: OK (392 tests), zero failures, Time 699.404 seconds. All existing Play-overlay, one-click, source, Tor, Jio and navigation regressions passed together.
- Signed original blue v86 installed IN PLACE over v85 via adb install -r: Success. Original firstInstallTime remains 2026-09-13 17:16:16; release certificate, package identity and existing app data preserved. Temporary orange debug/test packages removed; only in.ghartv.nova remains.
- Real Samsung in landscape reproduced the SwiftKey visual-viewport problem; the same device proved centering the INPUT restores visibility. Headless Chrome also passed 790x83 keyboard viewport, visible input/Search and a single successful Search click.
- Live signed v86 acceptance on the physical Samsung still needs the owner to scan a FRESH QR and review SwiftKey and real media playback; do not claim either passed without direct observation.
- Production family update/latest.json is still v80. No Dad publishing authorized.

## What the owner should review after blue v86 install
1. Pair the Samsung to original BLUE GharTV with fresh TV Phone QR; confirm Connected.
2. In portrait, tap phone Search once; ensure SwiftKey opens and phone typing updates the TV Search field. Submit Search.
3. Repeat in LANDSCAPE with SwiftKey visible: input and Search should remain fully above keyboard, without jumping or lost taps.
4. Select content; inspect numbered Sources and Try Next, genuine video picture plus sound, Play/Pause, seek, fullscreen, and Back. Third-party restrictions must be honestly reported.
5. Optionally inspect/stop the local opt-in technical usage report; no automatic upload.

Household promotion requires the clean final Android suite, original signature and retained install data, physical-phone Search/keyboard acceptance, usable real-stream picture/sound where supported, and explicit owner approval.
