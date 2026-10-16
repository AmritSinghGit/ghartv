# GharTV Review 55 — direct-first, opt-in Tor fallback for FlixMomo

Review candidate only. No public feed, household rollout, main merge, owner acceptance, provider availability or live-playback success is implied by this source, compilation or controlled testing.

## Continuation and identity

Continues Review 54 at `bbb6a5ff2ef9388310f347bcd48c4504e2399a58`. Android code 55 is a separate candidate, version `0.6.0-rc14.6-direct-tor-fallback-review`; its authoritative SHA is the built commit in `CANDIDATE.json`. Review 54/53/52/51 artifacts and the existing signing identity remain available.

## Implemented scope

FlixMomo uses normal HTTPS first. Tor is never enabled merely because Discover opens, and no request is sent through Tor before the owner explicitly opts in from the film controls. After opt-in, one eligible FlixMomo catalogue/navigation transport failure may start the app-local Tor service, wait for a circuit and scoped WebView proxy acknowledgement, then retry the same registered provider page once. Ordinary direct browsing remains the default.

The proxy scope is restricted to the already registered FlixMomo hosts and the fixed catalogue/artwork hosts used by the film experience. Jio live-TV, GharTV updates, telemetry, private/local addresses and lookalike domains remain outside the Tor route. The feature does not set a device-wide proxy, alter Mac DNS, copy browser cookies, weaken TLS, bypass DRM or claim Tor Browser anonymity. Active film playback is not automatically switched.

The Options and privacy surfaces expose the current route, opt-in state, direct retry, Tor retry and disable/reset controls. Disabling Tor clears the app-local scoped proxy and returns subsequent film navigation to direct HTTPS. Stale service and page callbacks cannot apply a fallback after the screen has closed or a newer attempt has superseded it.

The native poster loader follows the same explicit film-route choice for approved artwork hosts; it does not proxy Jio artwork or arbitrary third-party hosts. Existing provider-origin rules, third-party-cookie choice, media-gesture requirement, safe browsing, TLS cancellation and private-address protections remain.

The preserved Review 54 slow-document correction, catalogue diagnostic and Review 53 cold-start resolver handoff remain included. The bounded launcher telemetry summary remains observational: it does not force an upload, read raw events, change consent or infer collector acknowledgement.

## Native component and dependency boundary

The candidate pins the Guardian Project Tor Android and control-library dependencies and AndroidX WebKit proxy API through the reviewed repository configuration and checksum verification. CI verifies the packaged native ABIs and performs a real app-local Tor circuit/proxy smoke test in an isolated Android 36 emulator. That smoke test proves the bundled Tor service can establish a circuit and carry an owned HTTPS request; it is not live FlixMomo catalogue or playback proof.

## Tests required before packaging

The release route retains the previous 306 Android cases and adds twelve direct/Tor policy, controller and native-smoke cases for a total of 318. The focused suite must pass all 110 cases, the full suite all 318, the native Tor smoke must pass, and each existing fullscreen repetition must pass both cases. Existing source contract, cold-start, transport and network-recovery checks remain required. Passing results must come from the exact completed CI run, not expected counts in this document.

Controlled cases cover direct-first behavior, explicit opt-in, eligible and ineligible failures, one bounded retry, circuit/proxy ordering, concurrent-screen ownership, disable/reset, stale callbacks, playback hold, fixed host scope, Jio/update/telemetry exclusion, native Tor service startup and app-scoped proxy use. They use owned pages and fixed test endpoints; no provider credentials or private viewing content are used.

## Owner review

Run the compiled bundle's `GHARTV_OPEN_REVIEW.command --candidate 55` from Terminal using the existing local signing identity and saved Nova device. No development-checkout path or local Android build is required. The command updates GharTV in place and preserves app data.

Review in this order: leave Tor disabled and try Discover/search/provider page directly; if the catalogue transport fails, enable the visible FlixMomo Tor fallback and retry; confirm actual poster cards appear; open a title and test provider navigation/video; then return to TV guide and verify preview plus Watch Live still use the normal Jio route. Record the visible route label and exact error if either route fails.

## Remaining limitations

Tor can improve route reachability but cannot guarantee a provider catalogue, poster, search result or video. A responding root or Tor circuit is not authenticated API success. Unknown third-party player/stream origins are not automatically added to the proxy scope. The owner-network catalogue/search/video journey, microphone behavior and physical-TV acceptance remain pending after CI.

Analytics remains under existing Operon Analytics ownership. This candidate does not create another dashboard, collector, database or worker, and it does not certify telemetry delivery. No public update or deployment is authorized by this review candidate.
