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
