# GharTV Review 61 — Jio guide DNS recovery only

Review 60 confirmed that public IPTV playback and the existing FlixMomo/Tor browser work on the owner emulator. Those implementations are unchanged in Review 61.

The remaining owner-observed Jio failure is specifically `UnknownHostException` for `jiotvapi.cdn.jio.com` during guide refresh. Review 61 keeps the Review38-compatible direct Jio media, preview, DRM, authentication and entitlement paths. Only Jio-owned API/catalogue DNS uses Android system DNS first, followed by the existing independently bootstrapped TLS-verified secure-DNS resolvers if system resolution fails. Jio is never routed through Tor.

IPTV transport, parsing, buffering, player timeouts and channel ordering are unchanged. The existing adapter already requests `tvg-logo` artwork when a playlist entry supplies a usable HTTPS logo; channels without one retain the neutral placeholder. FlixMomo source and route behavior are unchanged.

Controlled Android tests verify the production Jio API is wired to the scoped DNS resolver while Jio media and preview remain direct. Real Jio guide, preview and Watch Live remain owner-network acceptance checks. No public rollout or merge is authorized by this review build.
