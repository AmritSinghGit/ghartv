# GharTV — exact Review38 approved for household review, 26 September 2026

HOUSEHOLD_UPDATE=EXACT_SIGNED38_APPROVED_UPLOAD_AND_FEED_EXECUTION_PENDING
PUBLIC_MARKETING=HELD
ANDROID_SOURCE_UNCHANGED=b5bc564dc83db4b157c2325c9df80ffa0b80d934

## Same lane and first reads

Continue ghartv, AmritSinghGit/ghartv, codex/ghartv-remove-auto-preview, PR1, in.ghartv.nova. Same normal Nova emulator-5580, web8790 and original private signing identity. No new product, branch, Mac worktree, service, database, key or emulator. First read RELEASE_HOLD.json, native receipt5687119492, this handoff and the actual update/latest.json/release assets. A stored publication status is not a substitute for live feed readback.

## Owner's newest decision

The owner explicitly asks to distribute the just-reviewed Review38 for Dad's household review, then continue improving it. This narrowly supersedes the earlier all-release hold for this exact APK only. Main RELEASE_HOLD.json records the exact approval. Broader public marketing, code33 promotion and a newer modified build remain unapproved. The existing distribution uses a shared public GitHub-hosted update feed; it is not a private per-TV push or a silent installation.

Actual native run GHARTV-CYAN-38-20260926T160406Z-94907, updated16:04:19UTC, reports sourceb5bc564dc83db4b157c2325c9df80ffa0b80d934, version0.6.0-rc11.1-focus-filter-review/code38, signed439df956cb8c1a064291fb10db5b7566aaee26772213f7f6aeb5a7a7174aa7e5, normal Nova foreground/frontmost, existing web/tabs reused and Obsidian readback. Original certificate40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c. The owner's screenshots show navigation and provider video frames, not sustained playback on every source or physical-TV acceptance.

## Publication state and command

At the last source read, update/latest.json remains code24, contents blob2138661b25a8dee70e300ef3e42263e8424ee597. No code38 release asset exists yet. Only signed33, not signed38, is present among accessible conversation APK attachments; Library results likewise did not supply signed38 (Drive recursive listing was unsupported). The reviewed signed38 is already cached on the owner Mac. Do not request a new key, rebuild or re-sign it. No remote Mac execution or release-asset upload action is exposed in this chat.

The existing tools/release_control.py is still the legacy console implementation. Added tools/release_control_reviewed.py imports its existing verification/state helpers and uses the same release-control directory and owner-run lock for the new explicit publish-reviewed38 mode. It creates no competing runtime or installer. Exact control source commitf9ec5cb3a070c794cfd8169cf7fc4bea4dc9fa4d.

Pinned files at that source:
- tools/release_control.py SHA2561789afa2e154af305b027a8d64e0a046a5e0065d34a13099d59f0b32eb88d6c7
- tools/release_control_reviewed.py SHA2568903aaa0dd015578757977911cde2468f27dbb6199adca0f83a3d66c17a44481

The owner-chat one-command block downloads just these two small files to a private temporary directory, verifies both hashes and executes:
python3 -E -s -B release_control_reviewed.py publish-reviewed38

It reads the archived successful38 receipt and exact cached signed APK; verifies SHA256, original certificate and package/code using the existing pinned verifier/source cache and Android tools; rechecks current exact approval. It creates/reuses the exact-source prerelease, uploads the original signed APK without clobber, downloads public bytes without credentials, then conditionally updates the existing feed using its prior contents SHA. It stops on approval changes, different same-code/newer feeds, wrong tag/asset/hash or missing trusted evidence. It does not build, sign, run ADB, install, start/stop emulators, open tabs, clear data, or mark a broad latest commercial release.

Intended tagv0.6.0-rc11.1-focus-filter-review; assetGharTV-code38-household-update.apk. These are planned destinations, not claims that an upload exists. On real success it writes the existing release-control/publication.json, attempts an Obsidian publication note in the existing lane folder and a safe PR1 notice. Raw-feed propagation is reported separately. Dad must use the ordinary GharTV update action and accept Android installation; his TV is not remotely controlled.

18 local unit tests passed with simulated GitHub and disposable file bytes, including wrong approval/certificate/public bytes, tag collisions, feed races, repeat-run behavior and upload-before-feed ordering. They do not establish actual owner credentials, Mac execution or upload. See RELEASE38_CONTROL_EVIDENCE.json on the development branch and the downloadable validation/test source. Module bytes were matched to GitHub's content blob. No new Android build was performed.

## Next candidate requirements, not changes to the approved APK

See REVIEW38_FEEDBACK_AND_NEXT.md in the same development branch. Persistent visible FlixMomo attribution is needed on loading/home/detail/player surfaces; the small current provider line is easy to miss. Keep native GharTV discovery/search/detail presentation with actual metadata and a reliable embedded-browser fallback. Do not keep switching unexpectedly to an unrelated full desktop layout or invent missing title details.

Fix lost/repeated Enter presses by reconciling input readiness, key-down/up, scrolling and current page geometry. Do not retry account mutations blindly. The player-count bug has a concrete source mechanism: Review38 accepts only an entire Player/Server/Source plus number label, so nested quality/status badges shown in the screenshots can cause controls to be missed. Next parser must separate stable player number from optional badges, detect actual choices, expose partial detection honestly and preserve identity on refresh; no fixed6/10/12 assumption or fake playback health.

Requested QR phone remote: mobile browser trackpad, D-pad, scrolling, text and playback controls; short-lived pairing, explicit TV approval, authenticated/encrypted session, revocation and only in-app control. A QR code is not itself a security boundary. No open public control port, system-wide input/shell capability or automatic keyboard/clipboard capture. This is planned, not available in38.

Provider layout/origin handling should use a versioned authenticated registry and bounded parser configuration, with explicit origin verification/approval, rollback and last-known-good behavior. Never trust lookalike domains or blindly follow a move banner; never carry cookies/passwords to a new domain. Unsupported layouts should show a compatibility state and permitted browser fallback, not false counts or fabricated content. This cannot guarantee third-party uptime/safety. Tor remains deferred. Attribution is not a permission grant or liability transfer.

## Preservation and continuity

GitHub source/approval/handoff have been updated. This turn did not write the Mac's Obsidian vault or verify Amrit Memory replication; the eventual publication command reports its own actual results. No owner files deleted, no SDK/AVD cleanup, no key access, no old candidate deletion, no other-lane mutation and no app update performed here. Original38 app source and signed hash remain unchanged. Old complete build/test handoff is preserved in blob982cbbc1b183b251c01fdd545996cd37a2f48af8 and historical manifests. Do not treat the cloud release-control source commit as a new installed APK or as completed household publication.
