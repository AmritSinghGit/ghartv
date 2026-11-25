# GharTV blue: USB-phone review and privacy-safe usage reports

25 November 2026. Canonical app: in.ghartv.nova. Private owner candidate: version code 84, version 0.7.10-opt-in-usage-review.

## Actual Samsung USB debugging observations

- Android ADB and Chrome DevTools reached the authorized physical Samsung Galaxy S23 Ultra, not just an emulator. No passwords, browser history, complete URLs, pairing keys, movie names or personal search text were included in the project report.
- Initially the Mac could see Samsung USB hardware but Android debugging listed only the TV emulator. Enabling/authorizing USB debugging made both devices available.
- The real Samsung Chrome browser had multiple prior GharTV remote tabs. One old tab showed Reconnecting, then Offline / scan again after a TV restart. Pairing keys are one-time and cannot be reused.
- Opening a newly generated, private TV pairing QR in real Samsung Chrome produced a Connected / TV GUIDE session, while the older tab stayed offline. The pairing URL was decoded and passed privately to Chrome, not printed or saved in project artifacts.
- The phone now explains exactly how to recover: on blue GharTV open Phone, scan the NEW QR, use the same Wi-Fi. Reloading an expired pairing tab cannot restore its old key.
- The temporary orange .fixes Android test activity sometimes remained in the TV emulator task history. It is not a user-review candidate and must be uninstalled after testing.
- Real third-party stream playback, every physical keyboard/microphone permission path and Jio audio/picture are NOT established solely by phone pairing or local fixture playback. Those remain owner UAT items.

## Usage diagnostics baked into the blue canonical source

1. On the paired phone, open Improve GharTV / Usage report and deliberately tap Start this session. Recording is OFF by default. The same session can Stop, View, Copy its summary or Delete its report with a second confirmation tap.
2. On the blue TV, Phone / Usage report opens a scrollable on-device summary with a confirmed Delete control.
3. Fixed, allowlisted technical outcomes include connection/reconnection, phone Search and editor focus, throttled typing activity (NOT characters or lengths), text editing outcomes, source list/selection/error, Try Next, Play/Pause commands, actual reported media state, fullscreen, seek and unsupported controls. Requests are never automatically equated to verified picture or sound.
4. Counts and the last 36 event names with relative seconds persist only inside the TV app for up to seven days. All records can be cleared. No remote telemetry server, SDK, network upload or automatic data sharing exists.
5. Raw text, search history, keystrokes, source/movie names, viewing URLs, IP addresses, device identifiers, pairing codes, credentials and OTPs are not accepted as event names or stored.
6. A fresh phone pairing cannot view a prior paired user's report without its own explicit opt-in. Recording does not passively continue across unrelated TV activity after the phone disconnects, and stops on TV process restart/pair reset.
7. Cross-household aggregate analytics would need a separate reviewed endpoint and explicit sharing consent from each participating user. The app does NOT silently upload data about other households.

## Evidence and next review gate

- Original blue release signing certificate SHA256: 40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c.
- Signed private code84 APK (NOT yet published to family):
  ~/Library/Application Support/GharTV/owner-review/candidates/GharTV-code84-blue-optin-usage-owner-final-signed.apk
  SHA256: 4df64692b6af725038cae85fabdce190b44e9abc28e91f4edafe131c35183326.
- TV clock previously verified at 1920x1080 and 1280x720. Permanent reversible check: tools/tv-first/test_home_clock_bounds.py.
- Phone JavaScript suites: 22/22 passed. Real Chrome mobile-layout test at 390px/320px passed live Search, twelve Sources, Try Next, playback/seek/fullscreen, usage opt-in/report/stop/delete, and no horizontal overflow.
- Java live phone socket smoke: 54/54 checks, including strict diagnostic commands.
- Android app-private diagnostic/consent tests: 7/7 passing. The TV report uses the existing scrollable modal body; the destructive deletion confirmation defaults focus to Cancel. The COMPLETE final Android instrumentation suite has now returned OK (391 tests), zero failures, including all preexisting 384 playback/navigation tests plus seven new diagnostic tests.
- Signed code84 was installed IN PLACE over original blue in.ghartv.nova with adb install -r, returning Success. Android package manager verified versionCode=84, versionName=0.7.10-opt-in-usage-review, original firstInstallTime=2026-09-13 17:16:16, and same original signer. No uninstall or clear of canonical account/app data. Only the temporary orange test and instrumentation packages were removed; Android package list now contains only in.ghartv.nova. Preserved exact installed v82 APK copy at /tmp/ghartv-blue-owner-installed-v82-preserved.apk SHA256 2788c9ca32b5085b7e178a9c381c92eb43826d5f2fc108435ff0731527dbd77e.
- Stable family manifest update/latest.json was reverified as production code80; no GitHub family release/tag, Dad deployment, or update-feed publication. Private review is the only installed code84 destination.

## Owner acceptance steps

Use the real Samsung on the same Wi-Fi. Scan a new blue-TV QR and confirm Connected. Start opted-in technical recording. Open FlixMomo Search on TV; verify phone Search mode, live typing, actual Android keyboard focus, Backspace/Clear and one Search submission. On a film, verify phone Sources 1-12, Try Next, actual video picture and sound, bounded seek and fullscreen enter/exit. Inspect the report on both devices, confirm no private contents, stop and delete sample diagnostics. Mark unsupported third-party sources honestly, without DRM or site-security bypasses.

## Physical-phone end-to-end v84 review state

After the full 391/391 run, blue v84 was installed in place, launched on the existing TV emulator, and its freshly generated private QR was decoded/validated and opened via authorized USB ADB in real Samsung Chrome. The new device is present in ADB and its Chrome debugger responds, but the Samsung entered Dozing/keyguard mode during the prolonged Android instrumentation run. Consequently the newly opened v84 Chrome page did not become visible/Connected; the preexisting v82 page remained Reconnecting. This is a verified physical-device blocker, **not** proof of a new v84 protocol failure. A successful actual Samsung Connected/TV GUIDE session was observed previously in this lane on original blue v82 using the identical fresh-QR flow.

After the owner manually unlocks the Samsung (no PIN/password ever requested from or handled by the worker), keep Chrome visible, scan/open another fresh private QR from blue v84 Phone, and complete the real-touch opt-in/report D-pad/Stop test. DO NOT claim that v84 real-phone opt-in, microphone, native SwiftKey keyboard, third-party movie picture or Jio audio have passed until each is observed. The independent compiled source, socket smoke, mobile Chrome rendering, TV owner release signing, and 391 Android regression checks are green.

## Meaningful improvements to prioritize from observed experience

- **Pairing recovery:** user learns to scan a NEW TV QR after process restart; old browser tabs do not silently regain consumed security tokens. The phone now explains this.
- **Real keyboard focus:** existing Search/Editor modes and stale-video-status fix must be verified with Samsung SwiftKey on a visible, unlocked real phone. The scripted phone modes and focus tests are passing.
- **Usage observability:** opt-in counts and recent technical states distinguish command request, inability to control a provider and observed playback progress. No false playback success claims or private search/query logging.
- **Operational release hygiene:** the orange debug test variant must never be installed as the owner canonical package or interfere with blue app back-stack navigation; a signed same-package v84 owner review exists and all orange packages were removed.

## Verified physical Samsung report loop — 25 November 2026

The owner unlocked the authorized Samsung Chrome session and it reconnected to the signed canonical blue code84 TV app. The real Chrome DevTools protocol dispatched native-like touch events to the phone's **Start this session** button, and the actual paired TV returned a confirmed enabled report. The phone then requested **View report** and displayed the TV-local technical action counts; it sent one harmless D-pad Up command, then pressed **Stop recording**. The TV confirmed opt-in OFF, with the locally retained report still viewable for owner review. These are actual phone-to-TV outcomes, not simulated HTML or Java-only socket checks.

A first physical touch attempt did not receive a timely TV confirmation; retrying with the visible, unlocked Chrome tab and a confirmed paired session passed the full Start/View/D-pad/Stop flow. This illustrates why connection-status/recovery and request-vs-confirmed feedback matter. Do not record a command as successful based solely on sending it.

**Still unverified on physical hardware:** opening SwiftKey consistently on SEARCH/EDITOR transitions, voice dictation/mic permission, actual movie/provider video picture and sound, iframe/DRM restrictions, and real Jio channel playback. The phone later disconnected from USB and the emulator displayed the Android TV home screen. No claim of successful real-world film playback or native keyboard operation is warranted.

Final Android instrumentation on the shipped owner build: OK (391 tests), zero failures. Final signed original-package code84 was installed in place retaining its original 2026-09-13 first-install date. Only original blue in.ghartv.nova remains installed. Household update/latest.json stays code80, with no family push.
