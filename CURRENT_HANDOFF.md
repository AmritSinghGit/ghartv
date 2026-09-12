# GharTV — Cyan Review5 signing recovery / SAME RC2 application

## Observed owner evidence

Run `GHARTV-CYAN-4-20260912T133236Z-77995`, delivery/local `2723c40449607e11ec17e9314a58621ceb25bb72`: Obsidian WRITTEN_AND_READBACK_VERIFIED; bridge handoff0/sync0 with replica readback unverified. Native signing failed. Signed APK NOT_VERIFIED, emulator UNCHANGED, dashboard NOT_OPENED. This establishes successful local memory transport and failed app delivery, not an installed RC2. The pasted receipt contains no native signer stderr; exact original password/alias/keystore failure remains unknown.

The password prompt was an assistant-introduced regression. Earlier v0.5.2/v0.5.3 scripts loaded the existing signing.env and keystore automatically; Review4 deliberately ignored them and requested manual path/alias/password. Do not tell the owner their password was definitely wrong.

## Recovery published, execution pending

Canonical entry stays `GHARTV_SYNC_CURRENT_AND_REPORT.command`, now Cyan Review5. SHA256 `75dcc31ee1a76381077d6733c2538664de1a82d616959ea92fd7cfc3db41e831`. It replaces the managed runner, not the app source. No new branch/worktree/AVD/product or local Gradle rebuild.

The default review run reuses exact existing `~/Library/Application Support/GharTV/signing/signing.env` and `ghartv-release.jks`. Parse only GHARTV_SIGNING_STORE, GHARTV_SIGNING_STORE_PASSWORD, GHARTV_SIGNING_KEY_ALIAS and GHARTV_SIGNING_KEY_PASSWORD as data, never source/eval shell commands. Validate canonical key path, ownership and private config permissions. Supply both passwords only to the local apksigner child through documented environment-variable inputs. Do not read them through chat, print, retain in diagnostics, upload or pass as literal command-line arguments. No password prompt, guessing, retry loop, new key or key rotation. Invalid/missing configuration yields a specific safe error code. Native raw stderr is not retained; only allowlisted error category/exit status is saved.

Reuse an existing verified signed RC2 asset when present. Otherwise sign the pinned unsigned cloud package; verify payload equality, package/version and certificate continuity against accepted RC5. Publish only the signed REVIEW APK to the existing prerelease. Never write update/latest.json, upload secrets, clobber a different review asset or clear/uninstall a TV app.

Reuse the existing named AVD; install with -r only after checks; pull and hash the installed APK; wake and open MainActivity; require a running GharTV process and resumed GharTV activity before REVIEW_READY. Record post-install state separately if foregrounding fails. The physical TV is never selected by this launcher. Open the existing local owner report or locked hosted shell independently from signing outcome, without claiming authenticated/fresh report data.

Memory-first and final receipt behavior remain: write/readback the existing managed Obsidian lane note, invoke existing amrit-context handoff/sync-once, record actual bridge exit codes, preserve unmanaged notes, and save safe run evidence. First Enter copies; second permits closing only the matching dedicated single-tab Apple Terminal. No terminal-global changes. The old owner receipt is not proof that this new note/update has already run.

## Application identities do not change

Independent project/lane `ghartv`, repo `AmritSinghGit/ghartv`, branch main, package `in.ghartv.nova`. Checkout `~/Downloads/GharTV_Nova_v0.4.2`. Existing AVD `GharTV_Nova_Manual_google_tv_API36`, normally emulator-5580. Managed download `~/.local/share/ghartv-launcher/current`. State `~/Library/Application Support/GharTV/owner-review`.

Owner application remains **0.5.5-rc2-movies-picture / code16**, source **b4d0304441b7d00833e4d475c16e43e1ef92b3f3**, unsigned SHA256 **32efc94eaa635b3da1d1895570b857e9f5d2dea5b5bed1151818c48d03a7cd21**, tag v0.5.5-rc2. Actual Android release compilation was previously successful in run34686776416/job103535017009. GitHub release still contained only unsigned APK/build metadata at this recovery's initial read. Signed hash and actual installed candidate await owner execution.

Public production remains **0.5.4-rc5-family-photo / code14**, source **b46b2cd607c309d364d531b5fd9da618cd007f6c**, APK SHA256 **6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199**. No production promotion, silent/mandatory-update implementation or physical-TV verification.

RC2 contains the official Movies directory (Chaupal/ZEE5/JioHotstar), Movies before Jio login, per-channel Original/Fit, Zoom/Crop, Stretch/reset, retained in-app voice and optional diagnostics. Account-aware content discovery/entitlements, universal provider login and AI super-resolution remain pending. See GHARTV_LANE_PROGRESS.md and PRODUCT_COMMERCIALIZATION.md; commercial/content rights and customer isolation remain separate requirements.

## Cleanup and review

Remove only checksum-matched obsolete delivery kits/commands and this run's temporary files. Remove managed unsigned RC2 only after signed replacement. Preserve keys/config, app data, previous runtime until replacement, unknown files, Git history, Obsidian history, Docker and other lanes. This is not a whole-Mac cleanup.

When the app opens: check Movies, return from a provider, set different picture shapes on two channels and verify reset, then check in-app Voice. Verify version16 and the printed signed/installed digest. Do not treat cloud native-signing validation or an upload as proof that the Mac emulator is open.

Local checks passed syntax, bounded config parsing (quoted and shell-escaped spaces), child-only credential transport, secret-free error classification, missing/invalid/private-config guards. A single focused cloud validation exercises actual Android signing of the RC2 payload with a disposable test-only identity, never the owner's key and never published as an artifact. Refer to the actual workflow result before claiming it passed. No new persistent test suite or owner-Mac signing was run here.

Cross-lane authority stays Operon Owner Control PR39 and existing Analytics tenant ghartv/lane operon-analytics/PR61. Record the observed memory success and signing failure there; no duplicate runtime, tenant, schema, merge or deployment. New success must be established from the owner-run receipt.
