# GharTV v89 - low-RAM WebView media-bridge performance candidate

Date 28 November 2026. Canonical package in.ghartv.nova, existing review-v80-live-sources branch.
PRIVATE SOURCE REVIEW ONLY: not authorized as a household release.

## Actual Family room TV evidence
- Skyworth SW-22AE NF Android 14, physical RAM ~1.81 GiB, physical TV still GharTV code80.
- Measured active WebView PSS ~369 MB and app ~294 MB, totaling ~663 MB. One high-CPU sample had 125% WebView renderer CPU and 67% GharTV CPU relative to one core. Some of that is third-party provider scripts. The bridge is not established as the main cause.
- 7.1 GB of data storage free; thermal status normal. CPU and RAM are the larger bottlenecks.
- No user search, video, URL or account contents collected.

## v89 source optimization
- The injected HTTPS media frame-bridge now skips iframe geometry/CSS reads in frames with no video during 850ms heartbeat.
- Even with videos present, it avoids scanning iframe geometry unless exactly one visible, non-DRM video is found.
- Same single-video safety, cross-origin rules, DRM non-bypass, Tor policy, and playback request-vs-proof separation.
- A likely reduction of useless layout work in no-video frames; not yet a measured improvement on this physical TV.

## Evidence
- 3/3 real document-start JavaScript tests passed: no idle frame geometry work, dynamic video recognition preserved, ambiguous iframe/video declined.
- Offline Android debug, AndroidTest and release compilation/packaging successful.
- Isolated headless Google TV Android emulator: 46/46 focused instrumentation tests passed, covering iframe bridge, real HTML5 video, sources, Play overlays, retry budgets.
- Test AVD stopped afterward. No physical TV install. Full release regression and real TV performance A/B remain pending.

## Reversible tuning on physical Family room TV
- Window and transition animations original 1.0 and animator duration original unset; all now 0.5. Original values saved locally.
- Only manufacturer com.smartdevice.recommendation disabled-user for user 0 after verified idle; optional recommendation UI, not a proven large RAM contributor.
- Rollback instructions saved locally at Library/Application Support/GharTV/owner-review/tv-performance.
- No app data wiped; no additional apps stopped or disabled.
- Android usage history was insufficient to establish which streaming apps the owner never uses.

## Next acceptance gates
- Full Android suite + physical TV comparison of frame latency, active player PSS, CPU and memory recovery after closing source.
- Owner selects unwanted Netflix/Hotstar/SonyLIV/MX Player/Downloader/browser apps before any staged reversible disable.
- Don't disable launcher, input methods, TV drivers, WebView engine, DRM, Google Play Services or vendor remote service.
- The public family feed remains v80 and v88 diagnostics-off candidate remains draft. This candidate is v89 private only.
