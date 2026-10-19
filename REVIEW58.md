# GharTV Review 58 — separate IPTV-org guide and Jio secure-DNS recovery

Review candidate only. Review57 and earlier candidates remain rollback evidence. No public feed, merge, deployment or owner acceptance is implied.

The owner verified Review57 FlixMomo playback on the second provider player. Review58 preserves that film browser unchanged and adds a separate **IPTV** source using the fixed public playlist `https://iptv-org.github.io/iptv/index.m3u`. Jio, IPTV and FlixMomo have distinct navigation and data paths. IPTV never receives Jio credentials, is direct rather than Tor-routed, admits public HTTPS streams only, rejects local/private/cleartext destinations, keeps a bounded verified cache and states that public stream availability and regional access vary.

The reproduced Jio failure was DNS resolution for `jiotvapi.cdn.jio.com` inside Android while the Mac resolved the same host. Review58 keeps system DNS first, then uses a TLS-verified DNS-over-HTTPS fallback only for `jio.com` hostnames if system DNS throws `UnknownHostException`. Jio API, preview and player requests share that resolver. TLS, DRM, account authorization and provider restrictions remain enforced; no Jio traffic is sent through Tor.

Controlled acceptance must retain all 330 Review57 Android cases and add 12 parser, source-isolation, secure-DNS, separate-guide and owned-first-frame tests: 342 full and 134 focused, plus catalogue timing, native Tor smoke, fullscreen repetitions, source contract and original sign-and-open packaging.

Owner acceptance still requires real Jio guide/preview/Watch Live on the owner network and representative public IPTV channels. The IPTV-org playlist is a list of third-party public links; GharTV hosts no channel and does not claim every entry is online, lawful in every territory or available in every region.
