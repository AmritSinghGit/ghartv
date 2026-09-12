# GharTV — Cyan Review 4 delivery / same Movies & Picture RC2

## Current authority

Independent product/project lane `ghartv`, repository `AmritSinghGit/ghartv`, branch `main`, Android package `in.ghartv.nova`. Continue the existing checkout, AVD, signing identity, Obsidian vault and Operon Analytics tenant. This is a delivery/continuity correction, not another Android build or product.

Read **GHARTV_LANE_PROGRESS.md** for the current cross-lane record, latest owner requirement for existing-account content discovery, researched integration patterns, ownership and acceptance checklist. PRODUCT_COMMERCIALIZATION.md remains the commercial roadmap; no rights agreement or paid launch is implied.

## Exact release identities

Public production: `0.5.4-rc5-family-photo`, code14; source `b46b2cd607c309d364d531b5fd9da618cd007f6c`; APK SHA256 `6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199`. The public update feed is unchanged. Automatic checks still require Android installation confirmation; mandatory/silent installation not implemented. Physical-TV install remains unverified.

Owner review: `0.5.5-rc2-movies-picture`, code16; exact application source `b4d0304441b7d00833e4d475c16e43e1ef92b3f3`; unsigned APK SHA256 `32efc94eaa635b3da1d1895570b857e9f5d2dea5b5bed1151818c48d03a7cd21`; tag `v0.5.5-rc2`; successful actual release build run34686776416/job103535017009. At the latest GitHub read, this release still contained only the unsigned APK and build record. No signed digest, running owner emulator or physical-TV installation is claimed by this publication.

Implemented in that same RC2: official Chaupal/ZEE5/JioHotstar provider directory, Movies accessible before Jio login, per-channel Fit/Crop/Stretch/reset, retained app-owned voice and opt-in diagnostics. Account-aware movie aggregation, provider entitlement checks, SSO and AI upscaling are NOT implemented.

## New owner requirement

Use existing subscriptions to bring available titles into GharTV rather than stop at provider app shortcuts. The proposed flow is approved catalogue data plus "My services" and correct title/provider links, retaining the official app's own signed-in session. Obtain sanctioned authorisation/entitlement interfaces for verified plan and title access. Same phone/email, installed app, user-selected service and verified TV/title entitlement are separate states.

Check whether the subscription is direct or through an aggregator; a benefit can be restricted to that aggregator's app. Never assume every service shares the Jio login. Google TV's service selection, JustWatch's contracted catalogue API, Chaupal's existing account/QR flow and Airtel's partner-specific activation are documented examples in GHARTV_LANE_PROGRESS.md. No public universal entitlement API or partner contract was verified for GharTV. No provider account was accessed here.

## One local delivery command, Obsidian first

Keep the canonical entry **GHARTV_SYNC_CURRENT_AND_REPORT.command**, now Cyan Review4. SHA256 **276523db8f666b57ec4bc068f741aff14f0927bd10606241294dc3811a486149**. It supersedes the previous source-only Cyan Review3 command; no second entry/runtime is created.

1. Verify current GitHub runner and lane-note bytes. Write/read back the managed current progress note in the existing Obsidian lane folder and call existing `~/bin/amrit-context` or PATH equivalent handoff/sync-once. This precedes signing and still runs when the checkout later proves dirty. A missing/failed mirror is reported and does not automatically block an otherwise safe review.
2. Verify the canonical clean checkout/origin/main ancestry and exact RC2 Android tree; fast-forward only. No reset/stash/force-push/new clone/worktree.
3. Explicitly ask the owner to type **REVIEW RC2** before signing/publishing a review asset or changing the emulator. Pressing Enter leaves review deferred. `--memory-only` skips this phase. `--signed-apk <path>` accepts an owner-supplied signed APK but still verifies it.
4. Reuse an already-published exact signed review artifact, or have the owner select the EXISTING keystore and enter its password at Android's native apksigner prompt. No signing.env, collector.env, password collection, credential upload or generated replacement key. No Gradle rebuild.
5. Verify package/version, payload equivalence to the unsigned cloud APK and signer equivalence to accepted RC5 before uploading only `GharTV-review-current.apk` to the existing prerelease. Refuse a conflicting existing signed artifact; no clobber. Never update the public manifest.
6. Reuse the named existing AVD, or start that existing AVD on free ports5580/5581. Never create another AVD or act on a physical TV. Reject a newer or different code16 app or signer mismatch. Install in place, verify installed APK bytes, launch, then replace the managed current APK file. Do not uninstall or clear storage.
7. Write final receipt/current note and record actual bridge results. First Enter copies the handoff; second permits closing only the matching managed single-tab Apple Terminal window. No global terminal changes. Dashboard is opened from an existing local page or the hosted locked shell; its authentication is not inferred.

Existing paths: checkout `~/Downloads/GharTV_Nova_v0.4.2`; AVD `GharTV_Nova_Manual_google_tv_API36`; signing folder `~/Library/Application Support/GharTV/signing`; output `~/Library/Application Support/GharTV/owner-review`; managed download `~/.local/share/ghartv-launcher/current`.

Obsidian vault `~/Documents/Amrit Executive Memory`; note `90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv/GharTV - Current Progress.md`. Never replace an unmanaged owner file with that name; create a run-specific note instead. Managed current note plus run-specific receipt avoids multiple current authorities. Local readback and bridge exit0 do not certify that every chat/replica read the note.

Cleanup only matches exact obsolete downloaded delivery bytes and owned temporary files. Current unsigned RC2 copy is removed after successful signed replacement. Preserve unknown files, signing keys, credentials, repositories, histories, Docker, private reports and other lanes.

## Validation and pending evidence

Bash and embedded Python compilation passed. Three isolated, controlled shell/Git/transport simulations passed: clean memory-only sync, dirty checkout preserving work while writing Obsidian, and preservation of an unmanaged existing note. Known obsolete ZIP removal and unknown-file preservation checked. Synthetic APK ZIP checks distinguished signature files from compiled payload changes. No new persistent test suite, Android rebuild, provider read or native signing was executed in this delivery turn.

These checks do NOT prove native Mac signing prompts, real Obsidian/bridge replication, authenticated dashboard, actual AVD installation or provider playback. Those outcomes require the owner-run receipt. Published runnable helper is not already-executed signing.

## Cross-lane registration

Preserve Operon owner control PR39 and Analytics PR61/tenant `ghartv`/lane `operon-analytics`; publish references to this handoff, not duplicate controllers, schemas or tenants. GharTV remains separate from VCNow. No merge/deployment or customer-isolation implementation was done here.

For the review, inspect Movies/open/return and provider missing-app paths, picture shape persistence/reset across two channels, Voice permission/fallback, and Picture statistics. The next product candidate must implement account-aware discovery with honest verification labels; do not mark that request completed by reviewing RC2. Production promotion and commercial rights clearance remain separate explicit decisions.
