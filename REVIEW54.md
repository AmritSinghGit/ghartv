# GharTV Review 54 — catalogue readiness and preserved Nova startup

Review candidate only. No public feed, household rollout, main merge, owner acceptance or live-playback success is implied by this source or by compilation.

## Continuation and identity

Continues the exact Review 53 application at `8bab8083357e517d8182df50de00361afbbf2cc0` through the source-preservation checkpoints `d4747a2ddfa97a237d0d6a15ec649bd839546300` and `e0947a72cca03fc0d24e82ca1738b321349f6f42`. The previously delivered 53/52/51 artifacts remain intact. Android code 54 is a separate candidate, version `0.6.0-rc14.5-catalogue-readiness-review`; its authoritative SHA is the built commit in CANDIDATE.json.

## Implemented scope

The native catalogue reader receives one document-ready notification per navigation. Its bounded observation clock is renewed only on that first usable-document notification, so a slow initial page does not exhaust the reader before its first observation. Repeated readiness, ordinary finished/Menu notifications, inactive pointer mode and failed pages cannot extend or reopen the observation window. Existing 25-second/48-sample bounds remain.

The real commit-visible, finished-document and completed-SPA-history callbacks use that notification. No provider response, title catalogue, image or stream is substituted. Existing origin policy, third-party cookie choice, media gesture, TLS, safe-browsing and private-address restrictions are unchanged.

The existing connection check adds the fixed catalogue dependency `api.themoviedb.org`, labelled `film_catalogue`, alongside the original seven service checks. A root HTTPS response is reachability evidence, not catalogue API or playback proof.

The next candidate's original owner-review packaging incorporates the preserved startup resolver handoff. It reuses the existing verified Nova when running. Starting the same stopped device uses verified resolvers already configured on the Mac, with the original memory, identity, port and race guards. No Mac restart, public DNS substitution, data wipe, second emulator or unrelated-process shutdown is introduced.

The reviewed bounded launcher telemetry summary is retained: disabled, queued-unverified, empty-unverified and unavailable-current-probe remain distinct. It does not force an upload, change consent, read the raw queue or assert collector acknowledgement. The separate event-time attribution repair is not included.

## Tests required before packaging

The existing release CI retains all 299 prior Android cases and adds seven FilmCatalogueObservationTest cases. The new class must pass independently, the focused suite must pass all 98 cases, the full suite all 306 cases, and each of the eight existing fullscreen repetitions must pass both cases. Existing transport, cold-start and network-recovery checks remain required. Passing results must be taken from the exact completed CI run, not inferred from this document or expected counts.

The new tests call the real controller and Android WebView with owned HTML; network requests are intercepted. They do not test live FlixMomo or Jio services. No owner emulator or installed app is used for CI.

## Owner review

Run the compiled bundle's `GHARTV_OPEN_REVIEW.command --candidate 54` from Terminal, using the original local signing identity and saved Nova device. No development-checkout path or local Android build is required. A memory or identity hold remains a hold, not an invitation to rerun blindly. Do not use an older source-repair helper to install this candidate.

Verify separately: a live-channel preview reaches its first frame; Watch Live plays and remains usable; Discover renders actual poster cards; typed search returns cards; the original provider page allows navigation and playback. Keep original preview preferences/timing unchanged. Record exact code, environment, observed errors and what actually succeeded. A results heading alone is not returned data.

## Remaining limitations

The owner's Review 53 screenshot showed catalogue connection error -6 after the provider header loaded. The reader repair does not prove that the external connection has recovered. The simultaneous Brave comparison used Tor and showed partial rendering; it was not an equivalent normal-browser catalogue test. No provider outage, ISP block or WebView-version defect is established. Owner-network catalogue/search/video acceptance remains pending even after controlled CI passes.

Analytics remains under its existing Operon Analytics ownership. Opening a tab does not start that server or prove private telemetry delivery. No analytics runtime, database, collector configuration, provider credentials, cookies, tokens, private viewing content or public update is changed by this candidate repair.
