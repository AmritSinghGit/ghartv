# GharTV Review52 — registered provider migration and catalogue recovery

Continuation of afc57a1983994358044371ba2db44ccd0c179a9a on the existing PR1 branch. Review51 signed rollback and the original signer/Nova owner route are preserved. This is an owner review, not public rollout.

## Implemented scope
- The default reviewed provider root is https://flixmomo.bet/. Explicitly selected or successfully loaded registered HTTPS roots are remembered without title/query/cookie transfer. Unknown domains, credentials, ports and non-HTTPS roots remain rejected.
- Returning from pointer mode through provider selection, Search or Discover restarts the native catalogue reader and clears stale pointer/editor flags. The old chooser could leave the reader paused while displaying a loading panel.
- Main-document errors are shown on the visible panel. Catalogue API failures are distinguished from movie playback, and an empty later snapshot cannot replace an actionable error with a generic empty catalogue.
- No API key extraction, catalogue fabrication, stream extraction, TLS weakening, proxy, new browser runtime or arbitrary-domain auto-approval.

## Actual observations before this patch
The owner-selected Nova Review51 was running. Its actual native address chooser was exercised to select .bet; the page and its skeleton loaded, while no catalogue arrived. From the owner Mac, .app returned its migration announcement and .bet returned HTML. A separate unauthenticated catalogue API network observation timed out; this is not proof of a specific ISP policy or of all provider failures. Provider network availability is independent of the app-mode defect.

## Required verification
Retain all266 existing Android tests, add12 controlled migration/error tests, require all278, the70 focused tests and all eight two-test fullscreen repetitions. Historical debug fixtures keep their original .app origin and assertions; their owned response transport also supports the new registered-root selection. Do not treat these controlled tests as live-provider or microphone acceptance. The existing owner opener performs local original-key signing and installation only when run by the owner.

Live provider catalogue/playback and owner acceptance remain unverified until measured in the resulting candidate. A generic move banner does not grant trust to a new domain. No analytics server or public feed is changed by this repair.
