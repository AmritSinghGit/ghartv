# GharTV RC10.2 — browser and privacy review

Continue the existing GharTV lane and PR1. The approved public viewer homepage is preserved. This delivery improves the existing web runtime and launcher; Android remains code28, application source 9457654eafe86a08c402c6829c6cae3312c3e196. New web and delivery source identity must be recorded separately. Do not rebuild, replace the signing key, or promote a household update merely to change the web experience.

The September19 owner run GHARTV-CYAN-15-20260919T182056Z-63914 successfully prepared the signed APK 680ffac23649509c92faaeca3c5b7f7a90971885dc7ea622485c7d983d7be7b6. Web health passed; Android installation was held at RESOURCE_PREFLIGHT because host memory pressure was WARNING. This is not an APK-signing failure or a passed TV review.

## Required changes

Remove analytics and owner-report routes from the viewer service, not just navigation links. Keep analytics ownership with the existing Operon Analytics GharTV tenant and PR61; do not create another dashboard or database. The public owner console has been removed on main; existing collector authentication remains separate.

Prefer a provider-returned HLS alternative for native Safari playback instead of unconditionally selecting DASH. Preserve content protection. Give an explicit Play control when autoplay is blocked and a specific compatibility message when no authorized browser-compatible rendition exists.

Brave is optional. Provide a direct current-browser entry to FlixMomo. Automatic search and isolated Tor browsing remain optional; do not silently fall back from Tor to direct or claim provider verification was bypassed.

Save the same full local receipt, compact light handoff and Obsidian note without copy/paste prompts. Use the existing stable GitHub receipt at PR1 comment5687119492 and read it at the beginning of a continuation. Failed or timed-out amrit-memory replication remains pending, not successful.

Keep web review available independently of emulator admission. Preserve and reveal a verified signed APK when a new emulator is held by memory pressure. Do not stop other lanes or remove their data.

## Evidence boundaries

Native macOS startup, actual Safari generated-media playback, browser UI tests and deterministic security checks must be reported separately from live Jio/FlixMomo playback and physical-TV acceptance. Test media must not appear in the shipped app. No paid service, household-feed rollout, new server deployment, AI super-resolution or payments are authorized or established by these source changes.
