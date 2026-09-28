# GharTV Review44 — loading, first-row artwork, explicit Mouse

Same existing lane/branch/PR/package and original-key owner workflow. Public38 unchanged.

Fixes the missed first image pass when cards are inserted after the outer panel was already laid out. First-row images start independently of child geometry; later rows remain bounded to the viewport. Grid layout and visibility changes schedule remaining nearby images. Early empty snapshots stay Loading with progress; repeated Refresh/Open actions are disabled during loading and recover after a bounded timeout or error. Search/Back/privacy remain reachable. Seed title/artwork/metadata appear while its actual page is read. Back to observed cards no longer reloads the provider page.

Mouse is an explicit native-tray fallback using the existing RemoteWebCursor over this app only. Native scans/actions are paused while pointing; arrows move, OK clicks, edge-hold scrolls; Back/Menu exits pointer mode and returns to native controls. No native stream extraction, altered certificates, new provider domains, background media interception, global input or telemetry opt-in. This is not native film playback.

Twelve new Android cases retain all110 previous tests. They exercise the already-laid-out panel, initially hidden panel, refresh replacement, truthful loading/timeout/failure states, seed details, explicit Mouse dispatch with actual owned-video clock/gesture, arrow movement and Back focus, and Back without provider reload. Final test counts must come from the actual CI result. No claim of live provider success or owner-device speedup.

Separate Analytics work belongs to existing Operon PR61. The APK does not deploy the Python report. A combined owner command may invoke a separately hash-verified, source-published same-runtime report activation; its result must be reported independently. No counts fabricated or GitHub logs treated as viewing evidence.
