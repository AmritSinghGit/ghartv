# Review46: actual native direct-media engine and explicit pointer placement

This is not a verified native FlixMomo movie integration. No native media link has been established for the iframe in the owner's screenshots. A pointer can operate that embedded player but does not supply a Media3 media source. The blocked stream-extraction/interception approach is not retried.

Implemented here: an internal, non-exported Media3 activity independent of JioSession, for explicit supported HTTPS media links on already registered provider origins. MP4/WebM/HLS/DASH link detection is applied only to a main-frame user-gesture navigation. There is no browser request capture, iframe traversal, session-cookie copying, license bypass or automatic trust of another origin. This is direct-link support, not a resolver for opaque browser players.

The native screen has Play/Pause, seek back/forward, selectable playback speed, timeline and remote controls, and releases its decoder while paused. An explicitly labelled Engine check opens only a generated owned test clip. It is not inserted into Discover, not a movie suggestion and not evidence that FlixMomo native playback works.

The existing Mouse fallback now points initially at the visible media rectangle when it is unambiguous. It never clicks on entry; the user moves and presses OK. Manual input invalidates delayed automatic positioning. A rejected automatic play target can show the pointer rather than requiring navigation through another menu. Ambiguous regions use manual positioning. Pointer playback is still labelled embedded playback.

Live guide refresh, JioApiClient, PlayerActivity, ChannelRepository and MainActivity are unchanged. No Analytics source, collector setting, telemetry consent, published38 feed, system browser or Tor change. Same lane, branch, PR1, package, key and Nova emulator. No new runtime or worktree on the owner Mac.

Expected validation: previous130 Android tests plus12 native/pointer tests. Only the actual successful run certifies completion. Native tests must decode the owned clip, render a frame, advance time, pause/resume, seek, change speed and release/recreate the decoder without a WebView. Final source, APK/entry hashes and test outcome come from the manifest, not this planning text.
