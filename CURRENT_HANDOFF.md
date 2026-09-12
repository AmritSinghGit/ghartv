# GharTV — canonical handoff · Cyan Review 3 / Movies & picture RC2

## Authority

Continue independent project/lane `ghartv`, repository `AmritSinghGit/ghartv`, branch `main`, package `in.ghartv.nova`. This conversation continues the owner-designated GharTV Nova product/TV-runtime donor chats. Do not create another project, repo, branch, checkout, AVD, telemetry service, tenant or analytics product.

Owner direction, 12 September 2026: clarify available updates, add Punjabi films/official services and picture improvements, develop GharTV commercially, keep GitHub and the existing Obsidian lane aligned. PRODUCT_COMMERCIALIZATION.md records researched options and proposed commercial gates. No payment collection, partner contract, content licence or commercial approval has been created.

## What existing TVs can update to

Public production remains `0.5.4-rc5-family-photo`, code 14, source `b46b2cd607c309d364d531b5fd9da618cd007f6c`, APK SHA256 `6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199`, tag `v0.5.4-rc5`. Feed publication commit `e91fa93d89e2a36872da19dcceb3fcfa0d48bf96`.

Older installations can choose GharTV → Jio account → Check for GharTV update → Download update → Android Install → Open. Allow the specific installation permission when requested, return and retry. Do not uninstall or clear app data. Auto-checks have a 12-hour throttle; Download/Later and Android confirmation remain. Neither compulsory nor silent installation is implemented. Users already on code 14 have no newer public update yet. Website changes are not APK changes. Physical-TV installed version remains unverified.

## Actual new release candidate

- Version `0.5.5-rc2-movies-picture`, code 16.
- Exact application source `b4d0304441b7d00833e4d475c16e43e1ef92b3f3`.
- Previous review source `bf3c9ddc0d4538c98e16590f5d420fafffba5952`; preceding control/website head `7c964b4d5178d0b17de1d4c7be53955d80e65e90`.
- Real release compilation and package validation succeeded in GitHub run 34686776416, job 103535017009.
- Tag `v0.5.5-rc2` published with `GharTV-review-unsigned.apk` and `review-build.json`.
- Unsigned APK SHA256 `32efc94eaa635b3da1d1895570b857e9f5d2dea5b5bed1151818c48d03a7cd21`.
- **UNSIGNED IS NOT INSTALLABLE. A signature-compatible, installable RC2 has not been published or installed by this chat. Existing owner-key signing and review remain required.** Do not claim SOURCE_REVIEW_READY means the app is running.
- Owner decision REVIEW_PENDING. Production feed unchanged; no code16 promotion.

## Implemented and compiled in RC2

Movies button in the existing guide and Movies & services from the existing login screen. The directory does not require Jio login merely to open. It lists Chaupal, ZEE5 and JioHotstar, detects their known TV-app entry, and after selection/confirmation opens the official app, Play listing or catalogue website. It is a provider directory, not an embedded movie catalogue or subscription bundle. Installed is not signed in or entitled. Passwords, OTPs, cookies, title streams and other apps' history are not read by this directory.

Player → Picture → Picture shape now offers Original/Fit, Zoom/Crop and Stretch-to-screen, saved locally per channel with reset to Original. No automatic detection/crop of encoded black bars, no processing of another provider's player and no new HD/4K detail. Existing app-owned voice flow and opt-in quality diagnostics are retained. The OS-reserved remote Assistant button remains device-dependent.

Review still needed on actual TVs: provider installed/missing/browser-missing cases, store/open/return navigation, no accidental purchase, guide focus/legibility, two channels retaining different picture preferences and reset, voice permission/fallback, data-preserving in-place signing/update and playback recovery. Compile success is not physical-TV/provider entitlement success.

Not implemented: AI super-resolution, licensed native Punjabi VOD catalogue, universal provider SSO, cross-provider resume/history, cloud household isolation, commercial billing, private household asset distribution or silent installation.

## Current owner command is continuity/source review only

Canonical name remains GHARTV_SYNC_CURRENT_AND_REPORT.command, Cyan Review 3. This revision **does not read signing credentials or collector tokens, sign, install, publish release assets or promote an APK**. It verifies and fast-forwards the existing clean checkout, checks production identity, downloads/verifies the unsigned source-review package, opens the existing owner-dashboard shell and release page, writes the Obsidian lane note and returns a receipt. It preserves the running emulator and all TV data. A hosted owner-dashboard shell still requires private authentication; opening it is not authenticated telemetry success.

The previous RC1-pinned Cyan Review 2 runner is obsolete for RC2. Do not bypass its source-mismatch check. The old RC5 stable publishers remain disabled. No replacement signing key, forced downgrade, uninstall, reset, stash, blind push, branch/worktree creation or second emulator is permitted. Owner signing must be completed through a reviewed use of the existing local key before an installable RC2 is offered. Keep signed and unsigned digests distinct.

## Existing paths and continuity

Checkout `~/Downloads/GharTV_Nova_v0.4.2`; branch main; same origin. Existing AVD `GharTV_Nova_Manual_google_tv_API36`, normally emulator-5580. This continuity command does not start, stop or reinstall it. Signing identity remains under `~/Library/Application Support/GharTV/signing/`; no key has been read by this revision.

Managed command stays under `~/.local/share/ghartv-launcher/current`. Outputs use `~/Library/Application Support/GharTV/owner-review/current` plus small run receipts. Only checksum-matched obsolete downloaded installers/review kit and owned current-run temporary downloads are cleaned; source, keys, unknown files, historical receipts, data and other lanes remain. No whole-Mac cleanup claim.

Existing Obsidian vault `~/Documents/Amrit Executive Memory`, lane folder `90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv`. Write a new run-specific note containing actual receipt plus the verified current handoff and commercial direction, read back the bytes, then call existing `amrit-context handoff --file <note>` and `amrit-context sync-once`. Record exit codes separately; no claim all chats/replicas have synced. No replacement vault or overwrite of unknown notes. Preserve `OPERON_SESSION_ID` if supplied; otherwise UNBOUND.

First Enter copies the handoff; second Enter finishes this continuity process. It does not claim automatic terminal-window closure. No terminal-global repair. Failures must leave a truthful receipt.

There is no direct Mac/Obsidian execution interface in this chat. Newest local sync, note, cleanup, key signing, live dashboard authentication and running APK are **AWAITING OWNER RECEIPT**. The older owner's Obsidian/bridge success for a failed RC5 publisher is not evidence for this RC2.

## Commercial and tenant decisions

GharTV remains a separate product and tenant `ghartv` of `operon.analytics`, not VCNow. Preserve existing Operon repo/lane/PR61 and branch `codex/opr-analytics-003-vcnow-data-control-convergence`. Customer households/profiles/devices require an additional server-enforced authorisation boundary before paid multi-household use. An analytics tenant record or diagnostic hash is not an implemented billing/identity system.

The existing collector/D1 and 15-second owner reader remain. Detailed export is bounded to 5,000 events, diagnostic consent is optional, reported recently is not watching now, and missing/stale is not zero. No website traffic counter, precise location, viewing-history resale or inferred provider subscriptions.

The current household JioTV integration needs written provider/legal assessment before sale. Ordinary consumer plans are not sublicences or redistribution rights. Keep GharTV software/service fees distinct from content subscriptions, and do not advertise free cable/all movies included. Family photographs and birthday information still embedded in household/public source must be separated before a generic commercial build. Current MIT software licence was not changed; it does not grant third-party media rights.

## Website / lineage

Same public site https://amritsinghgit.github.io/ghartv/ and short link https://tinyurl.com/2yju9h2t . Owner reader https://amritsinghgit.github.io/ghartv/owner.html . Existing screenshot is an app-only crop, not the raw private desktop. Keep source/website descriptions honest about public14 versus review16.

Separate production source, review source, unsigned/signed artifact digests and control/delivery Git SHA. A new handoff commit does not change the compiled app. Historical handoffs remain in Git history. Review the unsigned-source release and commercial direction; arrange signing using the existing key, then real-device UAT and explicit owner acceptance before promotion. No commercial go-live is authorized by a compile or household APK approval.
