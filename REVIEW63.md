# GharTV Review 63 — Jio timeouts, previews, Discover pointer

Owner report after Review62: JioTV times out, previews do not play, and the green pointer in Discover/FlixMomo vanished.

- **Jio media and previews.** Review62 moved Jio media to OkHttp with whole-call caps (live 40 s, preview 7 s) that cut off manifests and segments that were still downloading. Live media keeps Review38's connect/read budgets with no whole-call cap and cross-protocol CDN redirects; previews get 6 s connect / 8 s read with no whole-call cap (the 15 s first-picture budget still bounds them).
- **DNS.** System DNS now has a 3 s budget for names allowed to recover, a name already recovered in the last 10 minutes reuses that answer instead of waiting on the failing resolver for every connection, and each TLS DNS provider has a 4 s total budget. Jio stays direct and never uses Tor; TLS, DRM, accounts and provider restrictions are unchanged.
- **Pointer.** Review56 opened Discover with the search bar visible and refused the pointer while it was visible. Moving down from the search bar into the page, or Menu -> "Return to page / pointer", now hides the search bar and turns the pointer on. Routing is unchanged.
- **Candidate.** `.github/workflows/review63-candidate.yml` builds and runs the deterministic suites at the exact commit. `python3 tools/tv-first/install_candidate.py` downloads that build, verifies it, signs it with the existing local key and installs it on the Nova emulator.

Success still requires owner evidence on the emulator: Jio guide refresh, preview first frame and Watch Live first frame on at least two channels, and the pointer in Discover.
