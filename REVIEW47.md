# Review47 — embedded cinema experience

Owner46 confirmed that movie playback and the mouse work. This preserves those paths and stops pursuing the native-player experiment for this release. Its class/tests remain, but no Native player button or automatic direct-link handoff is exposed in normal viewing. Same lane/PR1/package/original signer/emulator.

One search row and an Options menu replace the always-visible debug/toggle rows. Connection and diagnostics are still accessible. The viewing tray is Play, Sources, Fullscreen, Mouse, More and Hide. Watchlist stays on the title page; trying another source stays in the source chooser. Dynamic source detection remains unchanged.

Actual provider fullscreen occupies the full app window, not the old reduced stage beneath fixed credit/warning text. The fullscreen Menu overlay has Resume, Sources and Exit fullscreen, plus FlixMomo attribution. Exit returns focus to Options and disables stale pointer capture; native header buttons can be selected again. The pointer fades after idle and reappears on movement. Neither inactivity nor positioning sends a click.

Fullscreen uses a user-initiated, explicit visible provider control when available. A cross-origin player may still require Mouse to reach its own fullscreen button. No iframe inspection, stream extraction, cert bypass, silent playback or new browser engine. This is the same embedded provider player, not native Media3 playback.

Preload is restricted to the first two rows of permitted artwork and the existing nearby-row memory caching. Up to12 observed title details are retained in the current Activity memory for10minutes and shown on revisit while the current action is revalidated. Cached data cannot enable Watch by itself. No speculative title-page navigation, video download, hidden WebView, session-history file or new background service. Cold provider data/TLS/network latency is not promised away.

18 new Android tests cover full-window placement, hidden browsing banners, Menu/Back/page-requested exit focus, dynamic sources, pointer click and fade, current-only native controls, two-row artwork loading, bounded/expired/defensive-copy cache, cached Watch gating and actual trusted fullscreen activation on owned media. Prior142 test cases retained. Final run evidence is separate from this prose.

Household38, live-TV guide refresh/client/player and Analytics runtime are unchanged. No public rollout, consent change, owner cleanup, cloud Obsidian write or Memory replication claim. Local launcher writes its established receipt when run. Owner47 live acceptance and measured device responsiveness remain pending.
