# GharTV — canonical continuation · Cyan Review 2

## Authority and immediate status

Continue the same independent project `ghartv`, repository `AmritSinghGit/ghartv`, branch `main`, Android package `in.ghartv.nova`. This chat is the owner-designated continuation of the GharTV Nova product and TV-runtime donor chats. Do not create another project, branch, checkout, AVD, telemetry service, database or analytics authority.

Production is the exact accepted birthday APK: source `b46b2cd607c309d364d531b5fd9da618cd007f6c`, version `0.5.4-rc5-family-photo`, code 14, APK SHA-256 `6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199`. Its production update manifest was published in `e91fa93d89e2a36872da19dcceb3fcfa0d48bf96`. Physical-TV installation is NOT verified. The current updater offers Download/Later and Android installation confirmation; neither a compulsory-update gate nor silent installation has been shipped.

Owner review source is `bf3c9ddc0d4538c98e16590f5d420fafffba5952`, version `0.5.5-rc1-voice-quality`, code 15. Real Android source validation and `assembleDebug` succeeded in run **34682418746**, job **103523363573** on 12 September 2026. This does not prove a release-signed APK or physical-TV success. The canonical Mac runner builds/signs with the existing local key, verifies signer continuity, publishes only a review prerelease, then replaces the app in the existing owner emulator. It NEVER changes `update/latest.json`.

Record the delivery/control commit separately from the application source. Later documentation/launcher commits do not become the APK's source identity.

## Correct the old publisher failure

The owner ran `GHARTV_RC5_EXACT_STABLE_R1.command publish` at 13:13 IST. It rejected the later reporting/handoff commits with “Application source or other files advanced after RC5.” It exited during preflight and did NOT remove or roll back the already-advertised RC5 production update. Its `UPDATE_ADVERTISED=False` described that run, not the live feed. Supplying the obsolete publisher after adding reporting commits was an assistant delivery conflict.

Stop using that downloaded publisher. In-repository RC4/RC5 stable publishers are now disabled. The single canonical owner entry is `GHARTV_SYNC_CURRENT_AND_REPORT.command`. It permits newer control-only commits while requiring the exact review Android tree, matching launcher bytes, a clean existing checkout and fast-forward ancestry. Unknown or unpushed work is preserved, not reset/stashed/deleted.

Dad's immediate route: **GharTV → Jio account → Check for GharTV update → Download update → Android Install → Open**. Allow installation by GharTV if Android asks, return and retry. Do not uninstall or clear data. Automatic checks are throttled to 12 hours and do not silently install the APK.

## Sharing and live owner analytics

Verified short share address: **https://tinyurl.com/2yju9h2t** → https://amritsinghgit.github.io/ghartv/ . Creation and exact redirect verification are recorded in run **34682565284**, job **103523772777** at 08:09:59 UTC. A requested is.gd custom alias failed; do not claim it exists. No separate detailed shortener statistics were requested. The shortener may have its own logging/privacy policy.

Owner dashboard shell: **https://amritsinghgit.github.io/ghartv/owner.html**. This static reader reuses the existing `ghartv-telemetry.ghartv-47d9a0.workers.dev` collector and D1 store. It creates no server or database. The hosted page starts locked and requires the existing private collector admin token. It keeps the token only in page memory, not URLs or browser storage, and refuses fetch redirects. The Mac runner reads the existing `~/Library/Application Support/GharTV/telemetry/collector.env` and opens a mode-600 private copy using that credential. Never share that private HTML file or upload it to GitHub.

The dashboard refreshes every **15 seconds while visible**. It displays opted-in diagnostic installation counts, reported versions/models/network types, latest receipt times, feature events, failures, measured *_ms values and available picture-quality samples. Detailed tables use at most **5,000 recent events**, disclose truncation and sample coverage, and show missing/stale data explicitly. “Reported recently” is NOT “currently watching.” Upload lag is separate from dashboard refresh. No website-visitor counter or exhaustive viewing history is implemented.

Its layout, authenticated-contract rendering using mocked responses, HTML-injection resistance, token-field clearing and lock-clearing behavior were checked in Chromium. JavaScript, shell and embedded Python syntax checks passed. These are NOT authenticated live-collector or Mac/physical-TV tests.

## Implemented in the new Android review source

1. App-owned Voice Search activity. Existing GharTV voice action launches it explicitly, not the TV-wide Assistant. Listening is user-initiated and bounded to 15 seconds; on-device recognition is preferred where available, with a disclosed configured-provider fallback and usable text entry. Raw audio and search words are not sent to GharTV telemetry. A physical remote's OS-reserved Assistant button is not assumed interceptable.
2. Picture button within the existing player panel: Auto adaptive, Highest-supported source, or Data saver preference. Fit/fill uses the existing renderer. No second player/decoder or stream proxy is created.
3. Live local picture statistics: source pixels, display mode, declared bitrate, estimated media bandwidth, dropped frames and buffer ahead. Optional technical samples are emitted through the existing opt-in telemetry path each 60 seconds. The observer-to-first-frame metric is not full tuning latency.

**NOT implemented:** AI super-resolution, a new transcoding/upscaling service, mandatory/silent updates, precise device location, website-visit analytics or automatic causal assignment of every buffering problem. A larger output image or higher bitrate is not native source detail. Higher-source mode may buffer and cannot exceed the provider's authorized renditions or decoder support.

## Privacy and wider distribution

The family photograph and birthday mappings are already embedded in the public source/APK from the accepted candidate. Diagnostic consent does not make those packaged assets private. Before wider rollout, separate private household personalization from the public build and review provider distribution/authorization requirements. No family photo, account identifiers, passwords, tokens, stream URLs, precise location or successful programme history should enter owner analytics.

## Canonical local and cross-lane identities

- Checkout: `~/Downloads/GharTV_Nova_v0.4.2`.
- Existing AVD: `GharTV_Nova_Manual_google_tv_API36`; prefer the already-running instance, otherwise its existing port 5580. Never create a second AVD or touch a physical TV through a guessed address.
- Existing signing: `~/Library/Application Support/GharTV/signing/signing.env`; never regenerate or upload the key.
- Owner outputs: `~/Library/Application Support/GharTV/owner-review/current` plus small evidence runs. One managed launcher at `~/.local/share/ghartv-launcher/current/GHARTV_SYNC_CURRENT_AND_REPORT.command`.
- Existing Obsidian vault: `~/Documents/Amrit Executive Memory`, handoffs under `90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv`. Do not create a replacement vault. Mirror run receipt into the existing `~/.local/state/operon-terminal-runs/ghartv`.
- Existing memory route: `amrit-context handoff --file <note>`. Record exit status, not an unsupported claim that every replica/chat is synced. The owner's previous log reports Obsidian written and bridge commands exit 0; remote readback of all replicas is unverified.
- Analytics authority remains Operon lane `operon-analytics`, tenant `ghartv`, PR **61**, branch `codex/opr-analytics-003-vcnow-data-control-convergence`, last observed SHA `52a66778d717fbb34b7da9aef95e7fd17f378dd1`. No Analytics merge/deployment or replacement worktree is authorized by this reader delivery.

## Owner command behavior and cleanup

Default: reconcile existing checkout, verify production feed, remove checksum-matched obsolete installers, build/sign/publish REVIEW APK, open existing emulator, open the live owner dashboard and save handoff. `--dashboard-only` skips build/install. Failures still save a receipt, and a verified checkout can still open the dashboard after a review-build failure. One exclusive owner-run lock prevents simultaneous runs.

Cleanup is bounded to known obsolete command bytes, this run's temporary downloads and the previous managed current APK only after successful replacement. Unknown files, worktrees, Git history, keys, credentials, data, Obsidian history, Docker and other lanes are preserved. Do not claim the whole Mac was cleaned.

First Enter copies the handoff; second Enter permits closing only the managed matching single-tab Apple Terminal window. Other terminals are left open. Hand off production source, review source, delivery/local SHA, APK digest, emulator result, collector authentication, dashboard, Obsidian, bridge, cleanup count and blocker separately.

## Browser chat versus desktop-app visibility

GitHub and Obsidian do not control ChatGPT account-history synchronization. Check the same OpenAI account and workspace, Chat rather than Codex/Work filtering, All recents and history search; then update/restart or sign out/in as appropriate. Never delete ChatGPT application data or duplicate this development conversation to conceal a history-visibility issue. There is no direct Mac/OpenAI account administration tool in this chat, so the issue is not remotely repaired by these commits.

## Next acceptance

Run the canonical command and return its handoff. On the emulator, check Voice → Start listening/text fallback and full-screen player → Picture → modes/live stats. On dad's TV, separately confirm installed version and birthday/playback behavior. Only promote code 15 after owner acceptance of this review; do not overwrite production with an unreviewed build.
