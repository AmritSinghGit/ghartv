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
- Broader historical `Review40PlaybackTest` was run: 3 of its 12 methods failed on an existing native-title fixture not finding a title card (line 36), before playback started; combined three-suite result was 23 passed / 3 failed. Root cause **not established**. Don't represent all regression suites as passing or silently waive this.
- Real third-party movie players, their cross-origin/DRM restrictions, and physical Android/iOS phone remote UAT have **not** been verified. Opaque/protected media remain uncontrollable by design.
- Some browser microphones do not grant SpeechRecognition access to an insecure local HTTP page. The current phone remote uses browser speech recognition when supported, otherwise phone keyboard dictation; **no actual voice recording or real iPhone microphone capture has been verified**.
- Original release signer environment variables were unset on the Mac's coding shell and no original signing keystore located in the inspected checkout paths. Do not substitute debug signer or reinstall original app. Next signed delivery must be release code81 or later, same package and original certificate; confirm APK signature and safe install over code80 before pushing family feed.
- v80 remains the family update. This v81 is a review candidate only.

## Owner review checklist
1. Launch the separate **GharTV fixes** icon in the emulator; note its login is independent by design, with no original account migrated.
2. Pair a real phone on same private network and open a real film watch page. Confirm Play/Pause changes TV media; phone current time advances; seeking jumps the film time; Fullscreen toggles and returns without browser navigation.
3. Test original full-screen custom view as well as normal page and one iframe/nested-frame player. Confirm unsupported player gives unavailable message rather than a false success.
4. Check typing, phone Backspace/Clear, 12 observed sources, and mobile speech-to-text dictation if the browser supports it.
5. Resolve old title fixture regression, run original-signed same-package Android in-place UAT without wiping data, then decide any household promotion.
