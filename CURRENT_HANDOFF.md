# GharTV — canonical product and TV-runtime continuation

Owner decision on 12 September 2026: approve the reviewed birthday/photo candidate for production distribution now. The microphone/whole-TV-search defect is explicitly nonblocking. This conversation takes over the existing GharTV donor/product/TV-runtime work; it does not create a second lane.

## Durable identity

| Field | Authority |
| --- | --- |
| Project repository / lane | `AmritSinghGit/ghartv` / `ghartv` |
| Existing branch | `main` |
| Existing checkout | `~/Downloads/GharTV_Nova_v0.4.2` |
| Android package | `in.ghartv.nova` |
| Existing review AVD | `GharTV_Nova_Manual_google_tv_API36` |
| Reviewed application source | `b46b2cd607c309d364d531b5fd9da618cd007f6c` |
| Application source parent | `968e43b6eaeb774f5b8ab0ede211dc5ae193cf11` |
| Application build | `0.5.4-rc5-family-photo` / versionCode `14` |
| Artifact tag | `v0.5.4-rc5` |
| APK SHA-256 | `6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199` |
| Android source build evidence | successful GitHub run `34678914925` |
| Production update publication | `e91fa93d89e2a36872da19dcceb3fcfa0d48bf96` |
| Delivery/control SHA | the exact Git commit containing this handoff/runner; record at execution |

The production update manifest was changed and read back successfully. The exact existing APK was promoted: no application rebuild, no re-signing, no new package, no provider action and no new application branch. Application source and delivery/control commits are different identities; never label the newer documentation/runner commit as the APK's build source.

APK: https://github.com/AmritSinghGit/ghartv/releases/download/v0.5.4-rc5/GharTV-Jio-Live-v0.5.4-rc5.apk

Update manifest: https://raw.githubusercontent.com/AmritSinghGit/ghartv/main/update/latest.json

GitHub release metadata still marks RC5 as a prerelease. GitHub's `releases/latest` still selects v0.5.3. The app's production channel uses the explicit RC5 URL above, not that moving GitHub-latest link. The version name remains truthful. Do not run the old FROM_RC5 stable-rebuild publisher as though the reviewed source were still main HEAD.

## What the person at the TV must do

Open **GharTV → Jio account → Check for GharTV update → Download update**. If Android requests permission, allow GharTV to install updates, return to the app and check again. Confirm the Android installer and reopen the app. Do not uninstall or clear data. Normal birthday splash duration is 4.8 seconds; the held emulator preview is not production launch behaviour.

Automatic app checks are throttled to 12 hours. The existing dialog offers **Later**. There is no already-implemented forced-update gate, push notification to an idle/offline TV, silent installation or verified remote install receipt. Publishing the update is not proof of physical-TV installation. Manual update check is the immediate path today.

## Owner command and reporting

`GHARTV_SYNC_CURRENT_AND_REPORT.command` is the continuation entry point. It:

- Verifies the existing origin/main, clean local state, fast-forward ancestry, unchanged reviewed Android source, live manifest and APK digest. It does not blindly push unknown local files.
- Reuses the same AVD; starts it only if absent and its existing image/ports are available. Verifies installed APK bytes and does not downgrade, uninstall or reset storage. Does not install on arbitrary physical TVs.
- Reads the existing collector using its existing local admin credential. The credential is not copied to the HTML, command arguments, GitHub or handoff. Authenticated requests do not follow redirects.
- Generates a private owner HTML snapshot containing observed pseudonymous installations, last-received timestamps, version adoption, actual timing fields with sample counts/P50/P95, failure and feature-event tables, and a copyable exact-candidate feedback form. Unavailable data is not zero. Export-derived tables cover at most the latest 5,000 events in a 30-day received-time window, not complete household history.
- Links to the existing Operon GharTV analytics surface when reachable; never starts a duplicate analytics service. Snapshot refresh is a repeat of this command, not a claim of continuous live refresh.
- Removes only checksum-matched known obsolete RC5 repair installers in Downloads and temporary files created by the current run. Edited/unknown downloads, old repositories/worktrees, logs, signing material, credentials and data are preserved. This is bounded cleanup, not proof that every old Mac artifact has been deleted.
- Writes per-run receipts under the existing GharTV owner state, a note to the existing Obsidian vault when present, and attempts the existing `amrit-context handoff --file` command. A zero CLI exit does not attest remote memory replication. Does not invent or renumber a global Operon session.

Private output: `~/Library/Application Support/GharTV/owner-review/current/GHARTV_OWNER_REPORT.html`.
Run-specific receipts: `~/Library/Application Support/GharTV/owner-review/runs/`.
Latest copyable handoff: `~/Library/Application Support/GharTV/owner-review/PASTE_TO_CHAT.txt`.

No direct Mac, physical TV, local Obsidian or memory-bridge execution was available in the takeover chat. Those states must be learned from the owner's command receipt, not assumed from GitHub success.

## Analytics integration authority

Continue tenant `ghartv` in existing `operon-analytics`, repository `AmritSinghGit/operon`, branch `codex/opr-analytics-003-vcnow-data-control-convergence`, PR #61. Observed PR head at takeover: `52a66778d717fbb34b7da9aef95e7fd17f378dd1`. The PR is open and unmerged; its older description is not authority over a newer observed head. This handoff does not merge, deploy or replace that analytics candidate.

Runtime discovery remains the existing `~/Library/Application Support/Operon/operon-command-market-v0.2.0/runtime/server.url` and `/ghartv-analytics.html`. A reachable HTML page is not proof of its exact running application SHA or authenticated report coverage; the receipt states this limitation.

## Next implementation, in priority order

1. **In-app voice:** explicit GharTV microphone returns recognition text to GharTV's channel/programme search; retain remote-first focus and cancellation. Handle permission denied, no recognizer, offline/network errors, empty results and repeated use. Do not promise interception of a TV/remote's OS-reserved Assistant key; establish which button is being used on the physical TV. This does not block today's release.
2. **Update policy:** add a version-aware mandatory-update policy and visible installation/relaunch state in a future reviewed APK. The already-installed old APK cannot acquire a new blocking behaviour from an unsupported JSON key. Reserve blocking for a deliberate owner policy, include retry/help/offline behaviour, and never label an installer prompt as installation success. Ordinary sideload updates use Android's permitted installation flow; do not promise silent updates.
3. **Privacy-conscious inventory:** retain the existing random diagnostic identifier; optional owner-friendly household/device labels and optional coarse location with explicit consent and provenance. Do not silently add precise tracking, IP/SSID, account IDs or hardware serial numbers. Build a first-launch/version-seen acknowledgement with timestamps and clear offline/unknown states.
4. **Performance attribution:** carry an action/playback correlation ID across UI action, catalogue/search, authorization, manifest/segment and decoder/first-frame stages; record bounded numeric timings, HTTP classes, retry/backoff and buffering ratio. Separate device/emulator dimensions without claiming an uncertain heuristic is proof. Distinguish evidence from suspected cause.
5. **Playback quality:** report actual selected rendition, video dimensions/codec/frame rate, estimated throughput, startup/rebuffer duration and dropped frames using available player analytics. Choose a sustainable authorized rendition, rather than manufacturing bandwidth or detail. Higher re-encoding bitrate does not recover missing source detail. Upscaling would be a separate measured device-capability choice, not a claim of true higher-resolution source.
6. **Reporting:** converge the bounded snapshot fields into the existing authenticated tenant dashboard, with freshness, consent, retention/deletion, role separation and error alerts. No new reporting backend or public telemetry dump.

The existing privacy notice excludes successful viewing history, programme titles, Jio account data and exact location. Keep those boundaries unless a new explicit consent design is reviewed. Location/labels and detailed quality telemetry above are planned, not already shipped. The family photograph is already embedded in the public repository/APK: diagnostic privacy does not make that media private. A future private-household personalization design must address this without silently breaking today's accepted experience.

## Release and recovery discipline

Accepted app SHA → exact APK digest → immutable per-version download URL → owner decision → production-manifest commit → observed version receipt. Future app changes require a new candidate and monotonically higher versionCode. Keep prior signed artifacts in GitHub. Reverting the update offer can stop offering a bad update but does not downgrade already-updated TVs; recovery for them is a tested roll-forward. Do not wipe login to force rollback.

Every next handoff records source SHA, delivery SHA, artifact digest, actual runtime/version evidence, owner decision, known gaps, cleanup outcome and next action. Code/handoff facts may be public; private logs, identities, tokens, reports, photos beyond the already-approved assets and household location must not be added to GitHub.

## Essential checks on this delivery

Bash syntax and embedded Python compilation passed. Bounded synthetic checks covered percentile calculations, unavailable-not-zero states, HTML escaping, sample coverage labels, preserving edited/unrelated installers, missing credentials, private file mode and failure receipts. The report layout and feedback control were rendered in Chromium using an in-memory document. These are helper checks, not a Mac/physical-TV UAT or a live collector read. No long test suite was added to the project.
