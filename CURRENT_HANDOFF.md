# GharTV — current handoff, 24 September 2026

STATUS=CODE33_COMPILED_AND_CURSOR_DEVICE_TESTED_ORIGINAL_SIGNING_AND_NATIVE_REVIEW_PENDING
OWNER_DECISION=REVIEW_PENDING_NEW_CODE33_CHANGES_REQUIRED_PRIOR_RUNTIME
APPLICATION_SOURCE=39363ee529e9f8f3c00dcab8fdd69b88aa5e175c
EVIDENCE=CODE33_REVIEW_EVIDENCE.json

## Same project and source authority

Continue existing lane ghartv, repository AmritSinghGit/ghartv, branch codex/ghartv-remove-auto-preview, draft PR1, package in.ghartv.nova. Existing Nova AVD is GharTV_Nova_Manual_google_tv_API36 / emulator-5580; existing web port8790. Operon Owner OSPR39, FabricPR56 and AnalyticsPR61 remain separately owned integrations, not new GharTV projects. No new Mac worktree, runtime controller, database, signer or emulator was created in this delivery.

First read the actual managed receipt at PR1 comment5687119492, its timestamp, then this current handoff and live branch heads. The shared runtime receipt and previous main handoff still describe September21/code30 and must not be represented as the final Work report or current Mac state. A source commit, test runner and historical receipt are distinct from installed runtime, user review and production approval.

## Recovered Work continuation

The accepted Work continuation's published code advances are verified: a5ca4163d0f3794d9fbea1c32f3f03fd701c6d24 introduced native pointer focus, family photo management and faster preview-to-playback reuse; 3373bcca7ec8d6589fc7cdb634b0eea00b6426e7 added the platform Android photo picker, code32/version0.6.0-rc10.5-tv-photo-picker. The later shared final native task report was not available through current connectors/library retrieval. Do not invent an installed32 signature, current DNS outcome, all-branch synchronization, cleanup, Obsidian replication or private diagnostics based on these source changes.

The owner now reports that the provider website inside the Android TV app has no movable cursor and has intermittent errors. The existing code32 FlixMomoActivity had no D-pad pointer even though ordinary native buttons had pointer focus. Code33 extends that EXACT source rather than returning to old code30 or creating another lane.

## New published Android candidate

Release v0.6.0-rc10.6-remote-cursor. Exact source39363ee529e9f8f3c00dcab8fdd69b88aa5e175c, version0.6.0-rc10.6-remote-cursor, code33.

Artifact GharTV-code33-review-unsigned.apk is6094774bytes; SHA2569913212502cf72bf7a5823e283d57acfcb7076f7e07804059d98299ef1430d81. It is COMPILED UNSIGNED, not an in-place install until the original local signing identity signs it. Required certificate40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c. No key was requested, extracted, generated or uploaded. No signed33 or owner installation is claimed. The old code30 review/recovery launchers are historical and must not install these new bytes under old metadata.

Source/archive, manifest, exact byte measurements and validation are release assets. The existing TV experience workflow now compiles and tests this cursor candidate and publishes a prerelease only; no new workflow/lane, scrcpy, mirror or recovery installer was introduced. Stable feed and approved public homepage are unchanged.

## Cursor and reliability behavior

Inside Android FlixMomo, choose Use page. D-pad arrows move the visible high-contrast circular pointer; holding accelerates; OK/Enter taps the page. Scroll:on makes arrows scroll; PageUp/PageDown and edge scrolling are available. Back/Menu returns to toolbar. Cursor:off restores ordinary native link focus. Pointer targets the provider's custom fullscreen view when present. Native guide, language/category filters, automatic bounded previews, family personalization, code31 fast handoff and code32 photo picker are retained.

Search/Browse dismiss the keyboard and preserve the query. Browse stays with the already-validated provider origin after its advertised migration. Retry retains the requested address and can recreate a WebView after its renderer stops; there is no retry loop. DNS/TLS/HTTP failures stay errors when WebView finishes its own error document. Slow loading gets one bounded notice. Cursor callbacks run only while held and are removed on pause/focus loss/detach.

The pointer dispatches events only to the activity's own view. No Accessibility service, system-wide input injection, provider DOM script, browser-detection change, DRM/entitlement bypass, security-header removal, provider-IP hardcoding or DNS-setting change. Direct connection remains direct; this is not Tor implementation. Actual owner-device network failure is not repaired by changing the error message.

## Actual validation

Workflow35983331379 SUCCESS. Code33 release, debug and instrumentation APKs compiled. The actual production RemoteWebCursor passed6 tests in a real Android36 WebView using local synthetic HTML: center click activates a DOM button, movement changes click target, PageDown scrolls, incomplete press cancels, disabled cursor leaves native focus, leaving hides cursor and text keys are not consumed. All6 passed in29.817seconds. Separate15 geometry assertions,29 retained preview assertions and9 source-contract rules passed.

These tests do not establish a live FlixMomo search/playback session, a provider fullscreen player, physical-TV compatibility across models or responsiveness on the owner's currently loaded Mac. Fullscreen routing and renderer recovery are implemented but not proven by those6 cursor tests. Every published artifact checksum was verified again after connector download; source archive excludes font files. The source33 release is not a signing/installation/owner-acceptance receipt.

## Project footprint, not a misleading worktree-only number

At source39363ee:320 tracked files totaling2946318bytes; shared Git directory for a fresh checkout of all visible remote branches2353049bytes; source plus shared history5299367bytes. One code33 APK6094774bytes. Lean source+sharedGit+oneAPK total11394141bytes (11.394MB decimal). Source archive1228916bytes is an alternative compressed representation, not another amount to add. APK entries unpack to14992304bytes, not a measured installed-device footprint.

Tracked Android sources619848bytes, web-player1016006bytes, telemetry81563bytes, tools592530bytes, public docs57863bytes. Two remote branches were present at measurement: canonical development and main. This is not a measurement of owner Mac storage. SDK, AVD images/userdata, Gradle/node dependencies, worktree contents, Downloads copies, private logs, Obsidian and release-history assets are excluded and require a native disjoint-path census with shared Git counted once.

An isolated Linux/Node22 idle web process used34344960 RSS bytes,47771648 after100 loopback health requests. No provider/stream requests ran. This small sample is not a streaming CPU-capacity benchmark or an owner-Mac memory measurement.

## Native adoption, backup and cleanup gate

Reuse the existing native GharTV task and writer lease. Freshly inspect installed version/source/signature before adopting33; preserve newer/dirty work. Use the original private signer and normal existing emulator or physicalTV; no debug-key replacement, uninstall, data clear or scrcpy substitute. Retain the current working candidate until new navigation/stream review succeeds.

All user-work branches must be backed up individually with explicit local-head to remote-head parity, not merely one worktree or the main branch. Worktrees share repository history and contain separate working directories; pushing a branch does not save uncommitted/untracked files. Inspect all local refs/worktrees, safely publish authorized source commits without force, read back remote SHAs, and separately preserve safe uncommitted work before deletion. Never bulk-add credentials, private media, production databases or ignored secrets to this public repository. Remote branch enumeration alone does not prove local sync. No local branch parity was claimed or changed here.

Coordinate cleanup with existing Operon/Fabric cleanup task, not a competing cleaner. Keep current working review and canonical development checkout. Retire only proven superseded regenerable artifacts after source/needed evidence preservation and replacement acceptance. Do not delete shared SDKs/AVD data, live DB volumes, keys, media, dirty/unpushed work or uncertain processes. No Git/Docker prune, force resets, blanket process kills or volume deletion. Actual Mac cleanup in this turn:0files,0bytes. No Obsidian write/readback occurred; this GitHub handoff is ready for the existing mirror but must not be labeled mirrored before acknowledgement.

## Still incomplete

Original signing/install and owner review of33; current normal-emulator/DNS state; private live diagnostics review; verified hosted browser-only provider integration; private authenticated ghartv analytics in existing AnalyticsPR61; complete owner voice/text-to-native execution round trip; local all-branch backup and measured cleanup. No paid deployment, production promotion, hosting purchase, native Tor or AI upscaling. Historical details remain in Git history and existing recovery documents; do not revive rejected scrcpy advice as the next review path.
