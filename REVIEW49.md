# GharTV Review49 — bounded movie-start review

Same existing ghartv / ghartv-nova lane, implementation branch and PR1. Published code48 and distribution main remain unchanged. Original owner's obsolete clean checkout is preserved: source edits are based on reconciled GitHub head d6951fe85da48e461514627761b5e9835406f96a, whose parent is application48.

The existing details cache remains bounded to12 items/10minutes; cached details never enable Watch. Descriptive sampling is faster only during the first few seconds, then backs off and stops. First usable details latency and cache-preview latency are measured separately.

A foreground start strip displays elapsed time, Cancel, Try next and Sources. Top-document video starts require an actual compositor frame callback and at least one second of advancing position. The current player is brought into view once; no repeated scroll loop. Bounded automatic retry applies only to observable, releasable top-document media, at most three sources,12seconds per start and45seconds total. Previous media is paused/unloaded before selection. Unknown iframe health, protected media or advancing playback with unavailable frame evidence are left for manual control; never treated as failure or automatically interrupted. Cancel unloads the provider document and returns to retained cards. Back/pause cancels automation. Healthy playback is not retried.

Secondary HTTP/network errors are now visible as supporting-resource errors, not falsely attributed to the whole film. The existing telemetry read was blocked in the prior attempt and was not retried or rerouted. No new collector configuration or consent change.

The existing native engine was assessed and retained hidden. No documented authorized direct stream/API for the owner's selected embedded source was verified; no universal extraction, credentials copying, DRM/frame-policy bypass or speculative domain migration. Native test playback is not proof of provider native playback.

Original live-TV files, data/signing identity, mouse/fullscreen and established review flow are preserved. The opener resolves the exact existing named AVD's actual port instead of assuming5580; multiple matches stop. No duplicate emulator.

Tests retain180 earlier cases and add26 budget/first-frame/metadata/cancellation guards. Controlled media and simulated failures are identified as such; real-provider and physical-TV proof stay separate. Actual measurements are recorded in proof/review49-timing.json by Android tests, not fabricated in prose. New APK remains for owner review only.
