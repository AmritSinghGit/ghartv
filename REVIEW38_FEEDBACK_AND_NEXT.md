# Review38 owner feedback and next iteration — 26 September 2026

The owner now approves the exact already-reviewed signed38 for the existing household update channel so Dad can review it. This is NOT approval of a modified APK or a new candidate. App source b5bc564dc83db4b157c2325c9df80ffa0b80d934, signed SHA256439df956cb8c1a064291fb10db5b7566aaee26772213f7f6aeb5a7a7174aa7e5, native run GHARTV-CYAN-38-20260926T160406Z-94907. Read main RELEASE_HOLD.json for the narrowly renewed approval. Public marketing and newer-candidate distribution remain held.

The screenshots show the real normal emulator, native suggestion cards, detail/watch pages and a provider player rendering video frames. They do not establish duration, every source's health, physical-TV compatibility or broad content permissions. The generic analytics workspace is still visible behind it; a reused analytics tab is not proof of the GharTV report/tenant/authentication boundary.

## Next product direction (requirements, not implemented in38)

Keep GharTV's native discovery, search and detail shell and a capable embedded-browser fallback. Do not make the user bounce unexpectedly between a native card grid and the provider's full desktop detail layout. A native title detail view should show actual available synopsis, year, duration, type, language/genre, rating provenance and provider source, with Watch, provider Watchlist, Players and Open original page. Missing details should be labelled unavailable, never fabricated. Preserve provider login/consent requirements and use its player where no authorized native media integration exists. Avoid speculative automatic background requests for every poster.

FlixMomo attribution must remain visible on loading, suggestion, title and player surfaces, not only in a small line under Your next watch. Suggested wording: 'Suggestions and player supplied by FlixMomo. GharTV is an independent viewing interface.' Do not suggest a partnership, permission grant or all liability moving to the provider. The approved38 APK is not altered to add this text; its release notes may explain the existing provider relationship honestly.

Implement one physical Enter/OK press as one complete action. Inspect key-down/key-up, stale layout coordinates, scroll completion and pending renderer callbacks; no lost press while pageBusy is true, no first-press-only-focus trap represented as a click. Queue at most one explicit activation after readiness, show immediate feedback and never blindly retry actions that could save watchlist state or other account mutations. Test visible focus and actual action completion across page navigation, scrolling, rotation/zoom and delayed provider updates. A highlighted control alone is not a passed action.

## Source-count defect confirmed by source inspection

Review38 FilmPageSnapshot reads the entire innerText of a player control, then requires the whole string to match only Player/Server/Source plus a number. The owner's screenshots show labels carrying badges, such as PLAYER #2 4K BEST, PLAYER #3 GOOD and PLAYER #9 NEW. Those decorated labels can be rejected while plain numbered controls are counted. Thus a small detected count must not be presented as the full available-source count. This is a code-supported failure mechanism, not yet a live DOM reproduction of every displayed count.

The next adapter should separate a player's stable number/id from nested badges, preserve optional badge text as provider labels (not verified quality), recognize enabled visible choices and revalidate identity on selection. No fixed assumption of6/10/12 providers. Keep internal safety bounds and expose pagination/truncation rather than silently claiming a total. Report detected choices separately from provider-advertised total and verified playback. Refresh on a changed title/layout, preserve selection by stable identity and expose Not playing without looping through unknown or blocked sources.

## QR phone remote

A TV QR pairing flow should open a mobile browser trackpad, D-pad, scroll area, text entry, Back/Menu and explicit playback controls. Phone installation should not be required. It controls only GharTV's own view, never arbitrary device or shell actions. Pairing needs a short-lived session, explicit on-TV approval, an authenticated/encrypted channel, visible connected-device status and immediate revocation. Local-LAN operation needs cross-platform browser/network testing; a QR code by itself is neither authentication nor encryption. No public router exposure, reused permanent bearer token, keyboard logging or automatic clipboard capture. The existing D-pad must remain fully usable without the phone. This is not implemented in38.

## Layout/origin changes and safety

Use stable provider identity separate from its current web origin. Later ship a versioned, authenticated configuration for approved origins, path patterns and bounded parser rules, with rollback and a last-known-good version. Do not accept lookalike domains merely because their name contains FlixMomo or because a page displays a move banner. A new origin requires verified ownership/authoritative evidence and explicit approval; normal TLS checks remain. Do not copy provider passwords, cookies or session tokens to a new domain. Reject private-network endpoints, credentials in URLs, unsupported schemes and unapproved redirect targets.

If a layout no longer matches, state that integration needs an update and keep the permitted original page available with the remote/phone fallback. Do not fabricate metadata, misreport player counts, disable security checks or bypass a provider verification gate. Track errors with consent and privacy filtering; screenshots and provider metadata should not leak account details. This reduces breakage and unsafe redirects; it cannot guarantee every provider will always be available or safe.

Tor remains deferred and direct HTTPS must be labelled accurately. No anonymity promise. Attribution does not substitute for permissions. Community/broad marketing remains a separate owner decision.

## Scope boundary

No new Android build was made for this feedback. The release-control addition publishes only the original signed38 and uses the existing state/owner lock. All UX, parser, mobile-remote and configuration improvements belong to the next separately tested candidate on this same lane/branch/PR. Keep prior data, keys, app cache and working artifacts. No native cleanup or Obsidian synchronization is claimed until the owner-Mac execution produces an actual receipt.
