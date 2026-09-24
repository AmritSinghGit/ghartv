# GharTV RC10.6 / code33 — remote cursor review

Continues the SAME ghartv lane, AmritSinghGit/ghartv, branch codex/ghartv-remove-auto-preview, PR1, package in.ghartv.nova. Based on actual Work source code32 commit3373bcca7ec8d6589fc7cdb634b0eea00b6426e7; retains code31 pointer focus, family photo management, 20-second preview handoff and code32 platform photo picker.

## Review interaction

Open FlixMomo in the existing Android TV app. Select **Use page**. Arrow keys move a visible circular cursor and holding accelerates movement. **OK/Enter** clicks the page at the pointer. The pointer also works over the provider's fullscreen custom view. **Scroll: on** changes arrows to page scrolling; PageUp/PageDown and edge scrolling are available. **Back/Menu** returns to the toolbar. **Cursor: off** restores ordinary WebView link focus. The native TV guide, language/category filters, preview behavior and public website remain unchanged.

Search/Browse dismiss the keyboard and preserve the search term. **Retry** retries the same registered provider address; a terminated WebView can be recreated explicitly rather than crashing the activity. DNS/TLS/HTTP failures remain errors and cannot become a false ready message when the browser finishes an error document. Slow requests receive a bounded hint, not an automatic retry loop. Cursor animation runs only while keys are held and stops on focus loss/pause/detach.

No automation detection, TLS, provider entitlement, DRM or security headers are disabled. The cursor dispatches events only to this activity's view; it is not an accessibility service or system-wide input injector. It does not capture screenshots, record viewing history, contact diagnostics endpoints or change device/network settings. FlixMomo remains provider-owned search/results/player inside GharTV, direct connection, not a Tor relay.

## Artifact and execution boundaries

The release contains the exact compiled **unsigned** release APK, sanitized source archive, manifest, measurements and validation evidence. It cannot replace the installed app until signed with the existing original private key. Never install a debug-key version over the household app, create a replacement key, uninstall, clear data or use the old code30 recovery wrapper to install code33. The normal existing native build/signing lane is the installation authority. No scrcpy or new recovery installer is included.

Actual code33 UI tests use the production cursor and a real Android WebView with local synthetic HTML. They prove cursor movement/click/scroll/cancel behavior, not FlixMomo availability or live movie playback. Read VALIDATION.json for actual tests and results; cloud compilation is not owner-Mac installation or physical-TV acceptance. No new emulator or candidate is installed on the owner Mac by publication.

## Work handoff and storage

GitHub confirms Work published code31 and32 on September22. The shared current handoff and managed execution receipt still describe September21/code30; they do not prove the Work task's native stop point. The source improvements are preserved; current Mac DNS/emulator state, signed32 bytes, all local branch synchronization, cleanup and Obsidian readback require a native receipt. None is fabricated here.

A fresh all-remote-branches checkout measures source and shared Git history independently; dependencies, SDK/AVD images, Gradle caches, APKs, downloaded release archives, private diagnostics and deployed services are different storage categories. Do not sum overlapping worktrees/shared Git directories or claim the remote measurement is a Mac disk census.

All public source commits stay on the existing branch. Local dirty/untracked files, private keys and original photographs are not safe to bulk-add simply because the user wants branch backups. Before cleanup verify every local branch head against a remote ref and separately preserve authorized uncommitted work. Delete no unknown/dirty/active data. Keep the current working review and development candidate, and retire old regenerable artifacts only after replacement acceptance. This release makes no owner-Mac deletion or all-branches-synced claim.
