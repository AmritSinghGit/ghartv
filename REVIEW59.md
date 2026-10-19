# GharTV Review 59 — Jio and public IPTV route recovery

Review 58 proved that the owner emulator could still show the cached Jio catalogue while Jio preview/Watch Live and the new IPTV-org catalogue failed on the same unstable Android DNS/network path. FlixMomo succeeded after its app-local Tor route became ready. Review 59 keeps the same application, package, branch, emulator, data and original local signer, and repairs network selection inside the app rather than creating another runtime.

Jio API requests now try system DNS first, independently bootstrapped TLS-verified DNS providers next, and the existing app-local Tor HTTP tunnel only after a DNS, connect, no-route or safe idempotent timeout failure. Jio media and preview requests apply the same recovery to the public hostnames returned by Jio, not only `*.jio.com`, because stream, manifest, segment and license hosts can use other public domains. A successful recovery route is reused first for follow-up HLS/DASH requests so each segment does not repeat a known-broken direct path.

The IPTV-org catalogue and public IPTV player use their own direct → secure-DNS → Tor recovery chain. They remain separate from Jio sessions and never receive Jio credentials. Cleartext, private/local and malformed IPTV destinations remain rejected. The FlixMomo WebView proxy remains scoped to registered film/catalogue/artwork hosts; Jio/IPTV use separate OkHttp requests to the same app-local tunnel and do not become a global Android proxy or VPN.

Route recovery does **not** retry TLS/certificate failures, HTTP responses, Jio authentication/entitlement decisions, DRM decisions or provider denials through another route. Non-idempotent requests are not replayed after an ambiguous timeout. TLS verification, Widevine, safe browsing and provider access checks remain enabled.

The automatic preview first-picture budget is 15 seconds instead of 4.5 seconds. Full Jio Watch Live can wait up to 120 seconds and public IPTV up to 90 seconds when a cold Tor circuit must be prepared. These longer bounds are deliberate recovery windows, not proof that a provider stream is available.

Automated validation adds 12 focused route-policy cases covering provider order, multi-provider DNS, local-name exclusion, JioCinema scope, direct success, secure-DNS recovery, Tor recovery, TLS and HTTP non-bypass, safe POST handling and reuse of a successful Tor route. Real Jio preview/Watch Live and real public IPTV playback on the owner emulator remain owner-acceptance requirements. Public rollout remains held.
