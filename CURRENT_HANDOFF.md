# GharTV RC9 / code26 — TV-first preview restoration

## The regression and correction
The owner rejected the manual Preview 12s button and default-off behaviour in published Nova code24. Those were assistant design changes, not owner-approved requirements. Restore automatic muted preview after650ms of channel-card focus. Remove the added manual button and retain the familiar WATCH LIVE/Favourite hero actions, Nova branding and existing grid.

An explicit saved Off preference remains Off. Saving unrelated comfort settings no longer persists the default as an explicit preview choice. A poster is immediate while preview resolves; the video surface is created behind it so actual first-frame rendering can occur. The poster is hidden only on the first rendered frame, never merely on STATE_READY. Startup is bounded4500ms; visible preview stops12000ms after first frame. Buffering cannot reset the timer. Finished/failed previews do not repeatedly fetch until a fresh focus or selection. Find/header/chips/window loss/pause cancels pending/network/decoder work and fences late callbacks.

Grid focus is reconciled atomically with the hero channel. Initial focus waits for the saved card's layout instead of falling back to the toolbar. Background guide refresh does not steal focus or restore another last-played channel. The bounded focus request aborts if the owner moves elsewhere. Category/keyboard/first-panel Next, Simrat/family, picture settings, manual-only owner reports and configurable Still Watching are preserved.

## Candidate lineage and continuity
Same repository/branch/PR1, Nova packagein.ghartv.nova, existing GharTV_Nova_Manual_google_tv_API36 / emulator5580. User handoff GHARTV-NOVA-SELECT-20260918T070651Z-2231 verified published code24 unchanged and foreground. The new candidate is code26 (code25 is reserved signed recovery). The published code24 feed and prepared recovery are not changed or activated. No household rollback is inferred from local rejection.

TV_EXPERIENCE_CONTRACT.json is the pinned rule source. Owner-rejected defaults are corrected in OWNER_REVIEW_REQUIREMENTS.json, not repeated as requirements. The existing managed Obsidian note receives the contract via the same reviewed companion/launcher. A new Obsidian write requires owner execution; GitHub source publication is not a local write or proof of cross-chat replication. Source, build, signed APK and local execution identities remain distinct.

## Validation boundary
Pure-Java gate regression tests use the production ownership state machine. Android instrumentation uses the SAME production preview controller on a disposable CI emulator with a generated local audio/video fixture. It exercises actual first frame/mute/bounded stop, rapid focus changes, toolbar exit, timeout, explicit Off and return-to-same-card rearming. The fixture/harness are debug/test-only and are not in the release APK. This is not a live Jio or household playback test, Mac benchmark or proof of physical-TV compatibility.

## Not silently substituted
FlixMomo native catalogue-to-stream playback and Brave/Tor streaming are not completed. The existing inspection-only code is left unchanged; another diagnostic is not advertised as a movie integration. No browser/relay process is added to TV startup or preview. AI super-resolution remains unimplemented.

## Review delivery
Use the full cloud-compiled GHARTV_RC9_REVIEW.zip and its single download-and-run command. Existing signing identity and in-place5580 APK upgrade only. Development checkout, other emulators, accounts and production feed remain unchanged. Existing web/owner8790 companion is reused, with one active managed service. No broad cleanup or memory/CPU speedup claim. Review-ready means opened/byte-verified; actual playback acceptance remains the owner's decision.


## Verified RC9 delivery
Application `c20e3ef5dc8ff5dbee52c338930a5c4ac30157e8`, build35321599367/job105525107583 SUCCESS including7 actual Android controller tests with generated local media,29 production-state checks and controlled browser regression checks. Full bundle `8f85126879629717569eee799a3f5e8c11d3e8b69f19d76a5a9dde4f58eec914`. New Mac execution, live provider playback and new local Obsidian write remain pending. Publiccode24 and reserved25 unchanged. Latest actual owner selection is Nova5580/code24, not the rejected5554 One Guide screen.
