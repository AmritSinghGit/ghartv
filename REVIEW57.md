# GharTV Review 57 — submitted search remains browser-only

Review candidate only. Review56 and earlier candidates remain rollback evidence. No public feed, merge, deployment or owner acceptance is implied.

The exact Review56 release acceptance run exposed a real production-only defect: the film search toolbar could reappear after submission while the Tor route and provider page were loading. Review57 makes the submitted-search browser state explicit. Tor, WebView status, page-start, page-finish and failure callbacks cannot reopen that toolbar; only the Search key or Menu → Search films or series can restore it.

All Review56 behavior remains: search-first Discover, Tor-default film browsing, scoped provider/catalogue/artwork route, direct JioTV/updates/diagnostics, remote pointer, Back to TV guide and event-time route/version telemetry. TLS, Safe Browsing, sandboxing, DRM and provider access checks remain unchanged.

The full route must retain the previous 329 Android cases and add one browser-state regression: 330 full and 122 focused, plus catalogue tests, native Tor smoke, all eight fullscreen repetitions, source contract, startup/transport/network checks and original sign-and-open packaging.

Owner acceptance still requires actual provider results, player first frame where lawful and accessible, Search/Menu recovery, Back to TV guide, Jio preview and Watch Live on the owner network. Controlled Tor and owned media are not live-provider proof.
