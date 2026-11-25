# GharTV blue v85: real-Samsung Search flow repair

Date: 25 November 2026. Existing canonical worktree and branch review-v80-live-sources; no second app or repository. Original signed blue v84 remains installed; household feed remains v80.

## What real USB debugging reproduced

- Authorized Samsung Galaxy S23 Ultra with actual Chrome phone remote Connected and blue TV Paired.
- A trusted phone touch on Search sent A SEARCH, but the phone remained in TV GUIDE for at least 4.6 seconds; SEARCH mode appeared later. Code inspection found MainActivity homePhoneSink.action simply ignored SEARCH at Home, although the Film screen handled it.
- Live typing and SwiftKey were visible on the physical phone, and TV native Search reflected the entered text. A big flexing phone trackpad partly hid the input when the Android keyboard was open.
- First attempted compact-trackpad fix introduced a lost-tap race: input blur expanded the trackpad during Search pointerdown, so the button moved before pointerup. Actual headless Chrome captured only pointerdown and no Q submission. Fixed by retaining compact layout until the UI leaves SEARCH or EDITOR.
- Owner-entered search strings and screenshots remain in private temporary files only; never committed or included in telemetry.

## v85 source changes

- MainActivity phone Search from Home now immediately emits SEARCH mode and launches existing Films with a one-shot focus flag, guarding double launches and never auto-submitting a query.
- Films consumes the one-shot flag, focuses the safe native search field and supports the same flag on onNewIntent.
- Phone layout reserves visible input height when Android keyboard appears, using non-expanding trackpad sizing and VisualViewport resize. Blur on a Search tap no longer shifts the button during pointerup.
- New Android intent-focus regression and Node DOM blur/reflow regression.

## Evidence and held release gates

- Focused Android activity test: 1/1 passed.
- Phone UI Node regression tests: 10/10 passed.
- Headless Chrome mobile 390px/320px plus 390x480 keyboard-height viewport: passed, trackpad height 65px, Search received pointerdown/pointerup/click and emitted Q, no horizontal overflow.
- The initial full Android run from a build before the final phone layout patch exposed a Tor attestation failure: FilmTorNativeSmokeTest's final IsTor check returned false. Treat as a blocker, not a harmless flake or permission to bypass Tor.
- Await isolated Tor verification and a green whole Android suite of the final build before signing/installing v85 over blue v84. Preserve original install date, signer and app data. No update for Dad/family.

## Revised Tor investigation and keyboard proof

The original broad v85 Android run failed Tor attestation. A focused retry reproduced a failed WebView proxy application; this was not dismissed as flaky. A sanitized Android log revealed the exact provider exception: "Must be started before we block!" The process-wide WebView proxy was being configured on a non-main thread before provider startup was ready. The proxy application and clearing were moved onto the app main-thread executor, **without allowing direct browsing or bypassing Tor**. The existing real Tor proof then passed in isolation: OK (1 test), and the Tor-native class also passed in the subsequent final combined run at initial observation. Whole-suite gate must still complete green.

A mobile Chrome test at 390x480 verified the real input remains in the viewport (input top ~352, bottom ~398), with trackpad height 65px and no horizontal overflow. Its first run caught a new dropped-click bug when input blur expanded the layout between pointerdown and pointerup. Keeping the compact layout until the TV exits SEARCH or EDITOR fixed this: the retest observed all pointerdown, pointerup and click events, sent the Q search command, and passed Search, 12 Sources, Try Next, playback controls and usage report interactions. All 23 Node phone tests passed.

Owner v85 candidate built and signed with original certificate, private path:
~/Library/Application Support/GharTV/owner-review/candidates/GharTV-code85-blue-phone-search-keyboard-tor-owner-review-signed.apk
SHA256: a1c772d690c18cc61804871cd9710f7d011e5e11e81e482ef61c25478e6b3a5c
No household publishing authorized.

## Final release gate and blue owner installation (verified)

The final source build (including Home Search routing, SwiftKey keyboard viewport/tap stability, and main-thread Tor proxy apply/clear) passed full Android instrumentation: **OK (392 tests)**, 0 failures, Time 685.411 seconds. The separate real Tor attestation test also passed **OK (1 test)**, and phone Node suites passed 23/23. Real Chrome mobile/keyboard-height UI regression passed; Search pointerdown/up/click all fired and the correct Q command was sent.

Private owner-signed code85 APK SHA256 a1c772d690c18cc61804871cd9710f7d011e5e11e81e482ef61c25478e6b3a5c was signed using the unchanged original release signer SHA256 40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c.

Only AFTER the 392/392 proof, the installer removed the disposable orange debug package and its instrumentation package, then upgraded the existing **blue in.ghartv.nova** using adb install -r (success), no uninstall/clear. Package manager verified versionCode=85, versionName=0.7.11-phone-search-latency-review, and unchanged original firstInstallTime=2026-09-13 17:16:16. The only remaining GharTV package is in.ghartv.nova. Stable household update/latest.json was explicitly checked again as versionCode80. No Dad household update, no family release/tag.

The physical Samsung stayed USB-authorized but screen locked itself while the full 11-minute Android suite ran. **Do not claim post-upgrade v85 real-phone Home Search latency or SwiftKey input is verified until the owner unlocks the phone and a fresh pairing/real-tap check completes.** The preceding physical v84 real-phone test did verify the actual original failure and keyboard display; the v85 targeted UAT remains outstanding at this handoff point.

## Final live owner-blue E2E after successful installation

After the signed original blue v85 installation and 392/392 Android regression proof, an independent real Chrome browser session on the Mac joined the running blue TV app through its freshly generated private LAN pairing QR (the one-time pairing URL was never printed/saved). The browser sent a real Search click to the TV Home over its live phone-remote socket. The phone immediately received Search mode and its input was focused: **58 ms** between browser touch initiation and confirmed Search UI. This is a live browser-to-real-TV-app integration result, NOT a physical Samsung v85 timing claim.

For comparison, the preceding Samsung v84 live test sent A SEARCH but had not received Search mode within its first 4.6 seconds; underlying v84 Home phone sink ignored SEARCH and required later navigation. Native film search typing, real SwiftKey keyboard and TV-side typing updates were already witnessed in v84 physical screenshots and are not falsely represented as v85 physical proof.

The Samsung is ADB-connected but had re-entered its Android keyguard during the long unattended test. No lock-screen bypass or private-data extraction was attempted. A fresh v85 Samsung pairing and manual SwiftKey verification remain owner UAT acceptance items after the owner unlocks Chrome.

Retain signed blue v85 owner candidate only; no household release, no Dad rollout, no production manifest change. Real third-party provider playback/DRM is not certified by owned media fixtures.
