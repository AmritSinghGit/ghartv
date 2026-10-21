# GharTV Review 62 — Jio media DNS and diagnostic closure

Owner evidence on Review 61 showed that the Jio catalogue refresh now succeeds while preview and Watch Live still fail on the Nova emulator. Review 38 and Review 61 use effectively the same Media3 playback code, so the remaining difference is the device/network media plane rather than guide rendering.

Review 62 preserves IPTV and FlixMomo application source. Jio remains direct and never uses Tor. Jio API, manifest, segment and DRM hostnames use Android system DNS first and a TLS-verified public DNS fallback only after resolution failure. Media bytes, account access, TLS, DRM, subscriptions and geographic/provider restrictions are not bypassed.

The connection check now distinguishes system DNS from recovered HTTPS, probes a Jio media CDN separately, and reports whether Widevine is supported and its security level. Preview and live-player failures carry the precise stage, failure class, route, protocol and DRM flag into the existing opt-in privacy-filtered diagnostics.

Success requires owner evidence: guide refresh, preview first frame and Watch Live first frame on at least two Jio channels, plus an acknowledged diagnostic delivery check. Controlled tests are not a claim of live Jio acceptance.
