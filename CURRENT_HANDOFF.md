# GharTV — household Review38 published; next candidate started

HOUSEHOLD_UPDATE=EXACT_SIGNED38_PUBLISHED_PUBLIC_APK_AND_FEED_VERIFIED
PUBLIC_MARKETING=HELD
ANDROID_RELEASE_SOURCE=b5bc564dc83db4b157c2325c9df80ffa0b80d934
NEXT_CANDIDATE=SOURCE_WORK_STARTED_NOT_YET_COMPILED_OR_READY_TO_INSTALL

## Same authority

Same ghartv lane, AmritSinghGit/ghartv, branch codex/ghartv-remove-auto-preview, PR1, package in.ghartv.nova. Existing normal Nova GharTV_Nova_Manual_google_tv_API36/emulator-5580, web8790, original signing identity and existing cleanup/mirror policies. No new lane, native worktree, emulator, service, key or database. Read this handoff, actual update/latest.json, RELEASE38_PUBLICATION.json, RELEASE_HOLD.json and native receipt5687119492. Next-candidate source work is not a change to the approved APK.

## The failed Mac command uploaded successfully

The owner's command passed both helper checksums and reached stage2, then printed OPERATION_FAILED_GH_EXIT_1. Readback found the exact signed APK already uploaded in draft release397315098, asset591028859 (6157069bytes, SHA256439df956cb8c1a064291fb10db5b7566aaee26772213f7f6aeb5a7a7174aa7e5). The script then attempted its by-tag read for the draft, which returned404. This was our publisher's draft-state handling defect, not a signing or upload failure. The earlier simulated tests did not model draft lookup/temporary asset URLs properly.

Do not ask the owner to reupload, re-sign, rebuild or rerun the old publisher. The existing release was resumed by its numeric id in the existing smooth-source-snapshot workflow. No new release or asset was created and no bytes were substituted. The old pinned f9ec5cb3 publication script should not be reused for another candidate; future publishing must retain numeric release/asset identities across draft transitions.

## Actual publication completed

Workflow36256603907 at control source581fca70fb920181f0a64cd685bf329f90e7bf13 succeeded. It verified the uploaded APK's SHA256, original signing certificate40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c, package and code38, and rechecked the owner's exact approval. It published existing draft397315098 at2026-09-26T16:45:48Z, then downloaded the APK without credentials and checked its bytes. Only afterwards did it conditionally advance main/update/latest.json at commit9c4d25dcfb5f9cd0274abb69732f9930c85fe655. The raw public feed also read back code38.

Version0.6.0-rc11.1-focus-filter-review, code38, original app sourceb5bc564dc83db4b157c2325c9df80ffa0b80d934. Signed SHA256439df956cb8c1a064291fb10db5b7566aaee26772213f7f6aeb5a7a7174aa7e5. Tagv0.6.0-rc11.1-focus-filter-review, assetGharTV-code38-household-update.apk. Release remains labelled prerelease/household review, not a broad latest commercial launch.

RELEASE38_PUBLICATION.json is the persisted actual receipt; proof artifact10910827742 has digest e185f30818b9b741767c868e7268a4540b8327c2ef10804f27330f52b759aab6. This session independently reread the GitHub release and main feed after the workflow. The model sandbox's separate network download could not resolve github.com; do not claim that failed attempt supplied a second APK verification. The CI did complete authenticated and unauthenticated byte verification, and the stored proof reports both accurately.

The most recent actual owner installation is still runGHARTV-CYAN-38-20260926T160406Z-94907, with normal Nova foreground and Obsidian readback. The cloud publication did not run on the Mac, write Obsidian or install anything on Dad's physical TV. There is no need for another owner terminal command to finish this publication.

## What Dad should do

In the existing app, open the Jio/account button at the top of the guide, choose Check for GharTV update, then Download update and accept Android installation. Do not uninstall the app. If Android asks to allow GharTV to install unknown apps, grant that per-app permission, return and repeat the update check. This setting is not enabled remotely.

Inspected production code24 source59c130abc1283e66607315db916973553164064d: MainActivity schedules UpdateManager.check(false)2600ms after guide creation. Automatic successful checks have a12hour interval and failures a5minute backoff; the manual account action uses check(true) and bypasses those interval gates. An available newer version shows GharTV update available, Download update and Later. It is not a server-pushed popup, guaranteed immediate prompt while watching or silent installation. Dad's installed version/network/permission state has not been independently read, so manual checking is the practical route. Once updated, version should show0.6.0-rc11.1-focus-filter-review/code38.

## Next candidate actually started, not declared finished

The first implementation module is tools/provider-access/player-options.mjs, with16 passing local Node tests in player-options.test.mjs. Source c32b5fe11536daa005afff24d4c247f09fe615b8; progress record NEXT_CANDIDATE_PROGRESS.json at0e692f5ff55e38bac12485e8e96ecbf36a5ced02. It normalizes observed player controls separately from OG/4K/BEST/GOOD/NEW badges, handles varying detected totals, deduplicates stable ids, preserves selections across reordering and flags truncation. It rejects unrelated origins/links, hidden or disabled choices and stale-page activation; it does not invent verified playback or source-quality claims.

Those16 tests use synthetic control observations and execute pure logic in Node22.16.0. This is not yet integrated into the Android DOM reader, not a live-player test, and not a compiled next APK. It does not alter the published38 app. Remaining requested work is retained in REVIEW38_FEEDBACK_AND_NEXT.md: persistent prominent attribution, native actual-metadata title details, reliable one-press activation, secure QR phone trackpad, versioned approved provider configuration and full Android/TV/live-provider acceptance. Tor and broad marketing remain deferred. Do not describe a prototype normalizer as all-feedback completion.

## Preservation and continuity

GitHub source/approval/release state and handoff are updated. This cloud turn made no owner-Mac deletions, key reads, signing, builds of38, emulator actions or vault writes. Existing signed33/38 and private data are preserved. New candidates need separate exact-byte approval before a household rollout. Public availability and attribution do not grant content rights or guarantee provider reliability.

Prior full build/tests/publication preparation remain in handoff blob81ff0185ec9ca2083d9cf206a9377c73f240c869 and earlier history. Do not revive its now-obsolete upload-pending instruction or old command as the current publication state.
