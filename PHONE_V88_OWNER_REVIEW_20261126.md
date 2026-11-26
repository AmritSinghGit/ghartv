# Blue GharTV v88 — owner review (26 November 2026)

Canonical app: `in.ghartv.nova`, original blue release certificate, existing `review-v80-live-sources` branch. **Owner review only. No Dad or household update.**

## Changes

- Removed the entire phone remote `Improve GharTV · Usage report` panel, its buttons and legacy client event handlers. Phone screen space is reclaimed. The prior app-private TV-side report may still be viewed/deleted from the TV while its retention period applies.
- Replaced the premature `--:--`/`--:--` player timeline with a clear `Finding a playable player · no video confirmed yet` / `Waiting for video confirmation` state. The seek slider appears only for reported playback or an actual seekable range; genuinely unseekable media is labelled as such and cannot fake seeking.
- Added a branded, non-focusable Tor-loading cover above the Android WebView but below GharTV cursor/cards. It remains while retries or main-frame errors occur, and only reveals actual successfully loaded WebView content after the Android visual-state callback. Tor/TLS safeguards and no-direct-route protections remain unchanged.
- Kept v87's focused persistent Tor address bar and phone EDITOR priority.
- Automatically coalesces bounded local technical journal writes for a **private owner-review build** compiled with `-PownerReviewDiagnostics=true`. Stores timestamps in milliseconds and strict event names for phone inputs, source requests/selection, Tor load, visual readiness and confirmed advancing video. No titles, raw text, URLs, pairing tokens, IP addresses, full screen content, uploads or external collector.
- The same repository built **without** the owner flag defaults to automatic journal saving **OFF**. This is a release gate: DO NOT push the owner's trace-enabled APK into Dad's household update feed. A separately rebuilt and tested diagnostics-off family APK is required after owner acceptance.

## Test evidence

- Phone JavaScript/protocol and UI: **24/24 passed**.
- Real desktop Chrome responsive smoke: passed at mobile viewports **390px and 320px**, including Sources 1–12, Try Next, Search, keyboard, playback buttons, no report panel and honest unknown-media state. The supplemental 790x83 keyboard viewport test was passed in the earlier v86 source lane.
- Targeted Android UI including Branded WebView cover and automatic owner-only local recording: **32/32 passed**.
- Gradle owner-review debug, AndroidTest and release APK assemblies succeeded offline.
- The signed private owner APK is `~/Library/Application Support/GharTV/owner-review/candidates/GharTV-code88-blue-clean-loading-owner-review-signed.apk`.
- Signed SHA256: `4cbb4f0d5940724ae09655371d0ebfd9573fee502ab8747a9a76d39b86467eb8`.
- Original release certificate SHA256: `40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c`, verified against actual installed blue v87.
- Full remaining **364 Android tests are being verified in five memory-safe instrumented groups**. Installation is gated on results; do not claim all tests passed until log confirms each group.

## Owner acceptance before family rollout

1. Start original **blue GharTV v88** on the existing TV emulator; scan fresh Phone QR on the Samsung. Confirm Connected and keyboard focus in portrait and landscape.
2. Open FlixMomo, search for a title you choose. The Android green robot/broken WebView page must not flash before content appears. A slow or failed Tor page should show clear GharTV progress/recovery, not silently claim success.
3. Open the watch page. Check Sources, first three players, Try Next, source numbering and phase state. Before video is actually confirmed, the phone must say `Finding playable player` rather than display a false `--:--` movie timeline.
4. Confirm actual moving picture **and audible sound**, then Play/Pause, seek, fullscreen enter/exit and Back from phone. If the provider is unobservable, controls should honestly say unavailable rather than claiming success.
5. Finish the review without pressing Record/Save. While authorized ADB is connected, the latest owner-only technical journal may be read from `Android/data/in.ghartv.nova/files/ghartv-review-interactions.json` on the TV emulator; inspect event outcomes/timings, not movie text.

## Storage/operational status

- Two unused older Google TV AVDs were removed after saving their reproducible configurations; approximately **5.4 GB** of guest data recovered. Preserve active `GharTV_Nova_Manual_google_tv_API36`, recently used `GharTV_Nova_Google_TV_API36_google_tv`, separate phone emulator and `Pixel_9`.
- The AVD needed a headless/software-rendering test boot due Mac GPU/memory pressure. Restore the **visible** original blue emulator window for owner review; never wipe the original app data.
- Production family feed remains v80; Dad/household publishing requires explicit approval and a diagnostics-off release build.

## Not yet independently proven

- Live third-party film picture/sound, any opaque/DRM player controls, exact latency in the owner's next session, or universal Tor destination reachability. Do not infer those from scripts, DOM state, Tor circuit readiness or an HTTP 200 alone.
