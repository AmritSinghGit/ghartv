# GharTV v81 phone playback — owner review hold (24 Nov 2026)

## Lineage and status
- Continuation of the same GharTV source history on `review-v80-live-sources`. No force-push, reset, new control plane, or duplicate production app.
- Source version: `81` / `0.7.7-phone-playback-review`; intended eventual Android package `in.ghartv.nova`, original release signing identity.
- Isolated **debug** emulator package: `in.ghartv.nova.fixes`, installed on `emulator-5554` with independent sandbox. Original signed `in.ghartv.nova` v80 and its data were **not modified**.
- The production GitHub update channel remains code80. Do **not** put this unsigned debug APK on the family feed; release needs original signer, original-package in-place UAT, verified source/APK hash, and owner review.
- Prior v80 signed artifact and its GitHub release remain unchanged. The previously committed `ab65fd0` and `c4b2264` work is included in this branch's history.

## What changed
- Phone Play/Pause requests now reach the single **observable**, unprotected HTML5 video on the page or inside one visible HTTPS iframe; bounded state and commands can pass through nested player frames with AndroidX WebKit's document-start bridge. No iframe DOM extraction, private inputs, cookies, media URLs, automatic popups, DRM bypass, or background recording.
- The phone displays a movie timeline panel in VIDEO/FULLSCREEN/STARTING modes. Current playback position and seekable ranges come from actual observed media. An unseekable/opaque stream shows a **disabled** slider, not an invented position or fake seek control.
- Fullscreen is clickable **before** fullscreen has been entered. Explicit fullscreen can enlarge one visible player (video or iframe) to fit the TV screen and safely restore its style. Automatic fullscreen still requires observable playback proof. Native custom fullscreens remain supported.
- Phone controls show explicit REQUESTED/UNAVAILABLE feedback rather than pretending a player obeyed a command. Video time sampling is foreground-only. The existing phone remote, search, Backspace/Clear, source list and phone-keyboard microphone dictation fallback remain wired.
- A real, locally bundled 30-second owned video under an HTTPS cross-origin iframe is used for actual Play → clock advance → Fullscreen → Pause → Seek to 15 seconds → fullscreen restore testing.

## Verified evidence
- Node deterministic suites: **17/17 passed** — playback Java-generated JavaScript, direct/nested iframe message validation, current play/seek, phone remote UI interactions, voice fallback, seek protocol and 12-source selection.
- Android debug and Android instrumentation APKs: Gradle **BUILD SUCCESSFUL** offline.
- Isolated installed v81 debug app launched through the emulator to its own login screen; original signed v80 remains installed and unchanged.
- Native WebView test `FilmPhoneFrameBridgeTest`: **1/1 passed**, confirms document-start bridge, cross-origin iframe state, Pause request, Fullscreen and restoration.
- Native real-media test `FilmPhoneRealMediaTest`: **1/1 passed**, confirms a locally owned video actually plays, advances, pauses, seeks and restores fullscreen in a real emulator WebView.
- Existing `FilmNativeControlsTest`: **13/13 passed**; focused combined instrument suites **14/14 passed**.
- `git diff --check` and inline phone JavaScript syntax check passed.

## Known blockers and honest limits
- Historical Review40PlaybackTest initially failed three title-fixture methods because a debug harness disabled Tor but was still using the production Tor-only navigation gate. Registered owned fixture routes were fixed and all 12/12 now pass, while real Tor enforcement is unchanged.
- An initial full 383-test instrumentation run found 31 failures BEFORE the fixture corrections. Subsequent focused reruns passed: Review49PlaybackBudgetTest 29/29, phone source selection 3/3, Tor controller 16/16, preview 6/6, simplified Search 8/8, provider migration 12/12, and pointer/navigation/native search 44/44. A clean full 383-test rerun has NOT yet been established; no zero-failure claim until then.
- Real third-party movie players, their cross-origin/DRM restrictions, and physical Android/iOS phone remote UAT have **not** been verified. Opaque/protected media remain uncontrollable by design.
- Some browser microphones do not grant SpeechRecognition access to an insecure local HTTP page. The current phone remote uses browser speech recognition when supported, otherwise phone keyboard dictation; **no actual voice recording or real iPhone microphone capture has been verified**.
- Original release signer environment variables were unset on the Mac's coding shell and no original signing keystore located in the inspected checkout paths. Do not substitute debug signer or reinstall original app. Next signed delivery must be release code81 or later, same package and original certificate; confirm APK signature and safe install over code80 before pushing family feed.
- v80 remains the family update. This v81 is a review candidate only.

## Owner review checklist
1. Launch the orange GharTV Slow Review icon in the emulator. Its debug package has a separate login; do not uninstall or migrate the original app.
2. Pair a real phone on same private network and open a real film watch page. Confirm Play/Pause changes TV media; phone current time advances; seeking jumps the film time; Fullscreen toggles and returns without browser navigation.
3. Test original full-screen custom view as well as normal page and one iframe/nested-frame player. Confirm unsupported player gives unavailable message rather than a false success.
4. Check typing, phone Backspace/Clear, 12 observed sources, and mobile speech-to-text dictation if the browser supports it.
5. Verify every affected historical regression, complete a full-suite clean run, run original-signed same-package in-place TV UAT preserving data, and only then consider household promotion.

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
- Legacy playback-overlay Review45 still fails two cases where an Android WebView reports a valid visible semantic Play button and the native controller records CLICK_SENT_NOT_PLAYBACK_PROOF, but the owned button's trusted click handler is not invoked. This is a real open blocker, NOT a verified click. No synthetic JavaScript .click() fallback has been added because that would not supply a trusted gesture.
- Historical Review64 one-click tests also still have WebView fixture evaluation timeouts; root cause remains unverified. Both issues are recorded explicitly rather than weakening assertions or claiming a clean historical full-suite rerun.
- Source review can be committed/pushed safely, but **do not create a household/public v81 APK** until remaining regressions, real phone/TV UAT, identical release signing and in-place data retention are verified.
