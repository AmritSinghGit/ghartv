# GharTV v81 phone playback — owner review hold (24 Nov 2026)

## Lineage and status
- Continuation of the same GharTV source history on `review-v80-live-sources`. No force-push, reset, new control plane, or duplicate production app.
- Source version: `81` / `0.7.7-phone-playback-review`; intended eventual Android package `in.ghartv.nova`, original release signing identity.
- Historical: isolated orange debug package used for testing; on Nov 25 removed after signed original blue GharTV code81 replaced v80 in place. Original first-install date was preserved, data was not cleared.
- Household production GitHub update channel remains code80. Canonical blue code81 is installed on the owner emulator only, not published. Physical phone and live-provider review and owner approval are still required.
- Prior v80 signed artifact and its GitHub release remain unchanged. The previously committed `ab65fd0` and `c4b2264` work is included in this branch's history.

## What changed
- Phone Play/Pause requests now reach the single **observable**, unprotected HTML5 video on the page or inside one visible HTTPS iframe; bounded state and commands can pass through nested player frames with AndroidX WebKit's document-start bridge. No iframe DOM extraction, private inputs, cookies, media URLs, automatic popups, DRM bypass, or background recording.
- The phone displays a movie timeline panel in VIDEO/FULLSCREEN/STARTING modes. Current playback position and seekable ranges come from actual observed media. An unseekable/opaque stream shows a **disabled** slider, not an invented position or fake seek control.
- Fullscreen is clickable **before** fullscreen has been entered. Explicit fullscreen can enlarge one visible player (video or iframe) to fit the TV screen and safely restore its style. Automatic fullscreen still requires observable playback proof. Native custom fullscreens remain supported.
- Phone controls show explicit REQUESTED/UNAVAILABLE feedback rather than pretending a player obeyed a command. Video time sampling is foreground-only. The existing phone remote, search, Backspace/Clear, source list and phone-keyboard microphone dictation fallback remain wired.
- A real, locally bundled 30-second owned video under an HTTPS cross-origin iframe is used for actual Play → clock advance → Fullscreen → Pause → Seek to 15 seconds → fullscreen restore testing.

## Verified evidence
- Node deterministic suites: **19/19 passed**, covering phone controls, source selection, search, timeline, speech fallback, and playback. Java phone socket smoke passed 51 checks.
- Android debug and Android instrumentation APKs: Gradle **BUILD SUCCESSFUL** offline.
- The original signed blue package was upgraded to v81 by a successful data-preserving install; first-install date unchanged. Orange debug and test packages removed after review tests.
- Native WebView test `FilmPhoneFrameBridgeTest`: **1/1 passed**, confirms document-start bridge, cross-origin iframe state, Pause request, Fullscreen and restoration.
- Native real-media test `FilmPhoneRealMediaTest`: **1/1 passed**, confirms a locally owned video actually plays, advances, pauses, seeks and restores fullscreen in a real emulator WebView.
- Existing `FilmNativeControlsTest`: **13/13 passed**; focused combined instrument suites **14/14 passed**.
- `git diff --check` and inline phone JavaScript syntax check passed.

## Known blockers and honest limits
- Historical Review40PlaybackTest initially failed three title-fixture methods because a debug harness disabled Tor but was still using the production Tor-only navigation gate. Registered owned fixture routes were fixed and all 12/12 now pass, while real Tor enforcement is unchanged.
- An initial full 383-test instrumentation run found 31 failures BEFORE the fixture corrections. Subsequent focused reruns passed: Review49PlaybackBudgetTest 29/29, phone source selection 3/3, Tor controller 16/16, preview 6/6, simplified Search 8/8, provider migration 12/12, and pointer/navigation/native search 44/44. A clean full 383-test rerun has NOT yet been established; no zero-failure claim until then.
- Real third-party movie players, their cross-origin/DRM restrictions, and physical Android/iOS phone remote UAT have **not** been verified. Opaque/protected media remain uncontrollable by design.
- Some browser microphones do not grant SpeechRecognition access to an insecure local HTTP page. The current phone remote uses browser speech recognition when supported, otherwise phone keyboard dictation; **no actual voice recording or real iPhone microphone capture has been verified**.
- Original private release signer and config were found and used successfully. Signed code81 certificate matches the actually installed signed v80. No secrets printed; no key regeneration.
- v80 remains the family update. This v81 is a review candidate only.

## Owner review checklist
1. Review the ORIGINAL blue GharTV Jio Live v81 app now opened on your Mac emulator. Orange debug app was removed, and blue app data retained.
2. Pair a real phone on same private network and open a real film watch page. Confirm Play/Pause changes TV media; phone current time advances; seeking jumps the film time; Fullscreen toggles and returns without browser navigation.
3. Test original full-screen custom view as well as normal page and one iframe/nested-frame player. Confirm unsupported player gives unavailable message rather than a false success.
4. Check typing, phone Backspace/Clear, 12 observed sources, and mobile speech-to-text dictation if the browser supports it.
5. Android full-suite testing and signed in-place owner upgrade are complete. Owner still needs real provider, physical-phone and live Jio review before any household promotion.

## Additional fixes and checks — 24 November 2026
- Phone pairing and reconnect send the exact current TV mode, including SEARCH, even when no mode change has occurred recently. Session identity is sent before asynchronous mode updates. PhoneLinkSmoke verified 51 socket/protocol checks.
- Search now owns the TV text input as soon as the FlixMomo screen opens, independently of QR introduction. The startup method's unconditional toolbar-button focus override was removed. A deliberate TV Down key enters webpage pointer mode; passive WebView focus events no longer dismiss Search.
- Phone reconnection preserves SEARCH or EDITOR and attempts to focus the phone field. iOS Safari sometimes prohibits automatic keyboard presentation without a phone gesture; the phone touch target remains available. Only real iPhone UAT can establish behavior.
- Sources and Try Next refresh provider source choices on the phone without depending on an active TV-native menu. The controlled 12-player fixture demonstrated selecting Player 1 then 2, and Player 4 then 5.
- Source order is numerical, with groups 1–3, 4–6, 7–9 and 10–12 displayed on the phone. Only one foreground player runs at a time. We intentionally do NOT start three concurrent hidden streams or ad windows. Automatic fallback occurs only when a loaded source exposes no video and no iframe for a bounded 25-second window, and a further observed source exists. Opaque/protected players and indeterminate streams are never silently treated as failures; manual Next remains available.
- Phone progress distinguishes checking, moving media clock without verified picture, and first-frame plus time-advance proof. Recording phone audio/video is not implemented or verified.
- Original signed v80 package remains installed with data; isolated v81 debug package is distinct; production household channel remains v80. Original release-signing material and physical-phone UAT are required for v81 release.

## Final targeted checks and release hold (24 Nov)
- The final isolated Android core regression run passed **55/55**: one iframe media bridge, one actual owned 30-second playing-video test, three phone-source tests, one clock-progress test, twelve historical Review40 title/playback tests, 29 playback-budget tests and eight simplified Search tests.
- Final Node phone/protocol/source/controls tests passed **19/19**; actual Java socket smoke passed **51 checks**. The targeted pointer, old native Search and focus suite passed **44/44**. APK compilation succeeded offline and both separate emulator app identities were rechecked.
- HISTORICAL Nov24 defect RESOLVED Nov25: Android WebView swallowed a Play overlay mouse click into the video beneath it; one verified visible semantic Play button is now activated via a genuine native Enter key gesture. Overlay suite passed 9 of 9; no synthetic JS click or arbitrary advert controls.
- HISTORICAL Nov24 test race RESOLVED Nov25: wait for expected completed watch document and use native URL state during navigation. One-click suite passed all 6 of 6 and preserves real video playback assertions.
- Canonical signed blue v81 is installed for PRIVATE OWNER REVIEW, not family release. No household/public v81 release until real phone/TV UAT and owner approval.

## Canonical blue GharTV review update — 25 November 2026
- The orange package in.ghartv.nova.fixes is only a side-by-side debug build variant of the same source. The canonical normal release is original blue in.ghartv.nova, GharTV · Jio Live, code81 0.7.7-phone-playback-review. Do not create a second app.
- Play overlay root cause: Android WebView delivered pointerdown and touchstart to the actual Play overlay but redirected mouseup/click to the video underneath. After validation for exactly one visible semantic Play BUTTON, GharTV now uses one native Enter gesture on that verified focused button. No JavaScript button.click() or popup/DRM bypass. Native overlay safety suite passed 9/9, including actual local video progress and refusal of ambiguous/advertisement overlays.
- One-click root cause: old test fixtures evaluated JavaScript while title/watch documents were navigating, which cancels callbacks and falsely reports playback failure. The test now waits for the expected completed page before checking video. The six controlled one-click regression tests passed 6/6, including poster-to-Watch-to-video, Back non-replay and ambiguous Watch/manual controls.
- Shared native-player fixture navigation race repaired; initial three failures retested 3 of 3 green. COMPLETE final Android instrumentation run: 384 of 384 passed, zero failures.
- Existing shared source already contains QR pairing; phone Search/editor focus, reconnect and live typing; Backspace/Clear; 12 Sources and Try Next; numerical groups of 3; one foreground player and safe empty-only automatic fallback; actual media timeline/Seek, Play/Pause/Fullscreen; cross-origin iframe document-start media bridge; truthful playback verification; speech-to-text or keyboard dictation fallback; Tor-only browsing and automatic popup suppression.
- Original saved signing material securely recovered. Signed original-package code81 owner APK installed on emulator, NOT published: owner-review/candidates/GharTV-code81-blue-review-7a90d9978810-signed.apk; SHA256 26835742d14ec4f57d633d992bf8dd8dd45a56ca407cc30967102fc7dd9df641.
- Actual installed blue v80 signing certificate was directly read back and matches v81: 40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c. Its installed APK checksum is 678d20735863528f8cb98f64c3c3f61a7d883d85be0c51712ab1a23846785db8, copied unchanged to /tmp/ghartv-blue-installed-v80-original.apk. Never uninstall blue or clear its app data.
- Household publication remains code80. Do not modify update/latest.json, publish a release or send to Dad until owner approval.
- Pending owner UAT: real Android/iOS phone pairing, native keyboard/mic permissions, actual third-party player compatibility and picture/sound on TV. Opaque or protected sources are not automatically marked playable; no DRM bypass. Batches of three are sequential visibility-checked source groups, not three concurrently hidden streams.

## Final canonical blue owner delivery, 25 November 2026
- ONE SOURCE TREE: earlier orange package was a separate debug application ID from the same source, not an independent implementation. User-facing code81 review package in.ghartv.nova, label GharTV Jio Live. Production family feed remains code80.
- FULL ANDROID EMULATOR REGRESSION: final single unbroken run returned OK (384 tests), ZERO failures. Old 383/384 run had one transient JavaScript evaluation during poster navigation, resolved by reading native WebView URL while the page changes; actual video, trusted click, source safety, and Back assertions retained.
- FULL PHONE TESTS: Node suites 19/19 pass, actual phone WebSocket smoke 51 checks. Native overlay 9/9, one-click video 6/6, plus 12-source, retry, QR, Search, timeline, fullscreen and Tor tests included in complete suite.
- ORIGINAL SIGNING: APK was signed using prior private keystore from ~/Library/Application Support/GharTV/signing/. Certificate digest 40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c confirmed against the APK actually installed as v80. No keys/passwords printed.
- OWNER REVIEW APK: ~/Library/Application Support/GharTV/owner-review/candidates/GharTV-code81-blue-review-7a90d9978810-signed.apk, SHA256 26835742d14ec4f57d633d992bf8dd8dd45a56ca407cc30967102fc7dd9df641. Built from canonical source production build.
- IN-PLACE RESULT: adb install -r succeeded, now versionCode 81, versionName 0.7.7-phone-playback-review, same firstInstallTime 2026-09-13 17:16:16. Original v80 exact APK recovery copy saved at /tmp/ghartv-blue-installed-v80-original.apk (SHA256 678d20735863528f8cb98f64c3c3f61a7d883d85be0c51712ab1a23846785db8). NO UNINSTALL OR DATA CLEAR OF BLUE.
- ONLY CANONICAL INSTALLED: after final tests, the orange in.ghartv.nova.fixes and in.ghartv.nova.fixes.test packages were uninstalled. Blue MainActivity is top resumed in Android; qemu-system-aarch64 GUI window brought frontmost on Mac via System Events.
- REAL-WORLD REVIEW STILL NEEDED: a physical phone/iOS keyboard/mic permission test, real live FlixMomo source opening and TV picture/sound, any opaque/DRM embedded players (do not bypass or falsely claim success), Jio Live login/playback and desired Back/Fullscreen behavior. Sequential groups of 3 are one foreground player at a time, never three speculative concurrent hidden streams.
- RELEASE HOLD: No v81 GitHub release/tag, no update/latest.json change, no Dad/household update. Production stays v80 pending explicit owner approval.


## Canonical blue v83 home clock and USB-phone review — 25 November 2026

### User-observed defect and code corrections
- The original blue GharTV home header showed a truncated time (visually 11:3...) on 1920x1080 because an 82dp clock and a row of fixed-width buttons exceeded the available width.
- In MainActivity, the time is pinned at the far right with a reserved, non-ellipsized 110dp field. The actions use a bounded horizontal scroll area, and their common widths/padding are compact enough to fit the normal 1920x1080 layout. On 1280x720 the controls can scroll, but time remains fully shown.
- The exact same production-signed original app identity in.ghartv.nova is used. v82 was installed over v81 using adb install -r and kept the original firstInstallTime 2026-09-13 17:16:16 and Jio account data. v83 incorporates the phone UI status correction below; this is the final owner review candidate (not a new app).
- New permanent TV-emulator regression script tools/tv-first/test_home_clock_bounds.py validates fully visible home time at both 1920x1080 and 1280x720, observes actual native accessibility bounds (220px clock width in both), checks main header button bounds on 1080p, and restores the original resolution even on failure. Passed both sizes.
- A real headless Google Chrome mobile-browser test exercised the actual phone HTML at 390x844 and 320x640, verifying Search input focus, live update and submit, all 12 source buttons, selecting player 4, Try Next, Play/Pause, fullscreen toggle, seek to 45 seconds, and no horizontal overflow. This is browser automation, not proof of physical Samsung touch input.
- Chrome revealed one genuine bug: after changing from VIDEO to SEARCH, a stale video playback-state message could overwrite the phone's Search/Editor hint. Corrected with a non-video mode guard in phone/index.html; Search explicitly announces live typing. Added regression to tools/test-phone-ui.mjs that injects stale playback updates and asserts typing guidance survives. Final phone Node tests: 20/20 passed. Java PhoneLinkServer smoke: 51 checks passed.

### Owner APK and USB-phone UAT gate
- Existing production signer reused from ~/Library/Application Support/GharTV/signing/, never printed. Signed canonical code83 version 0.7.9-home-clock-phone-review owner APK:
  ~/Library/Application Support/GharTV/owner-review/candidates/GharTV-code83-blue-home-clock-phone-owner-review-signed.apk
  SHA256 16b0af25f4e97690a4fa9898c8b93a39a98e57c070a0f9ac374b2715621122b4
  Original release certificate SHA256 40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c.
- The Mac reports Samsung_Android over USB, but Android Debug Bridge has shown ONLY emulator-5554, no physical authorized phone. Physical Chrome UI, native Samsung keyboard opening, dictation/microphone permissions, real finger gestures and latency are therefore NOT yet USB-automated or verified. To authorize, owner enables Developer options / USB debugging on the Samsung and taps Allow for this Mac; no passwords, keys or user records need to be shared. The paired phone TV indicator was observed GREEN after the same existing AVD resumed, but USB debug access was still absent.
- Physical owner UAT must use the paired actual phone and original blue TV app to exercise Search focus/live typing and keyboard persistence, Backspace/Clear, Sources and Try Next through all 12 choices in numbered groups of three, Play/Pause, actual picture and audio, seek, fullscreen enter/exit, browser microphone or phone keyboard dictation, and real FlixMomo player visibility. The app reports opaque/cross-origin/DRM players as unverified rather than bypassing them. A tested owned video fixture is not proof that third-party streams will play.
- Initial full code83 instrumentation returned 377/384 passing and seven failures (not a release-ready result): six navigation-time WebView JavaScript callbacks during document changes in Review50/51/52/53 and one owned-video setup timeout in Review48. Repaired shared navigation-sensitive tests to wait for the actual final URL and loaded document before reading JavaScript, without weakening playback, focus, trusted-action, source safety or Back assertions. All previously failing seven tests passed together in targeted rerun (7/7); a NEW full code83 rerun is in progress. Installation gate is a complete OK (384 tests).
- Signed code83 APK embedded phone/index.html checksum equals corrected source SHA256 aa386f764a2129d8e009515d2b75267d933456195de23e79f7eee6d9975b024f. Original release signing certificate reverified from final APK.
- Household production manifest was rechecked as code80 / 0.7.6-live-sources-review. NO v83 family release/tag, updater feed edit, or message to Dad is authorized until owner UAT/explicit approval. No data reset, duplicate repository/worktree or new control plane.
