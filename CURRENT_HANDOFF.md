# GharTV — signed42 failed Android startup; private collection check prepared

OWNER_RUNTIME=ACTION_REQUIRED_CODE42_SIGNED_ANDROID_NOT_CONFIRMED
APPLICATION_SOURCE=fab21208c75c43e918ea6efbd130831720cac9ce
CONTROL_REPAIR=ae2860af26a90071815862b3091609d81441eafc
ANALYTICS=LEGACY_SCHEMA_REPAIR_NOT_ACTIVATED_ACTUAL_COLLECTION_UNKNOWN

## First reads and current evidence

Same ghartv lane, AmritSinghGit/ghartv, branch codex/ghartv-remove-auto-preview, PR1, in.ghartv.nova, same normal Nova emulator-5580 and web8790. Read managed receipt5687119492 before acting. Current receipt GHARTV-CYAN-42-20260928T095547Z-66858 confirms signed42 SHA25688d58bf6407084b92bea4c5156d1e7b2d4811d1370d0d433cf9504550e2ff867, but ACTION_REQUIRED, no confirmed foreground or Mac window. Owner terminal error is COMMAND_FAILED_ADB_EXIT_1. Signing is complete; the exact failing ADB operation and stderr were not preserved by the original generic wrapper. Do not infer low storage, offline transport, signature mismatch or a clock failure from exit1 alone. No clock result printed before failure.

The existing web server and browser tabs were reused. REGISTERED_WORKSPACE_FOUND_AUTH_NOT_PROBED means URL discovery only. The safe mirror's prior collector_config_present=false was generated from an unpopulated launcher field; it is not evidence that the actual collector.env is missing. Do not read the dashboard's zeros as proof that collection is empty.

## Same42 launcher repair and read-only collection audit

Two small source files on the existing development branch at ae2860af26a90071815862b3091609d81441eafc:
- tools/review42_postflight.py SHA256fc0ca4c6f0bed14bb637f26f2a1edd8c844345d42761fb6d10c2fa5e15c1785d
- tools/review42_postflight_entry.py SHA2569e4f16b230f3a5613e84f62b94079f938c2e7cda48a6b942f2589b4753283645

Download both pinned files, verify both hashes, then execute python3 -E -s -B review42_postflight_entry.py --resume-and-check. Owner ZIP GharTV_42_Resume_And_Data_Check.zip contains the same files/entry and tests,15503bytes, SHA25665a5d5f99722664aef33893b43f3dcee4341eb1a2f076264b82abb3ebbd1d51d. This is NOT another APK or a new runtime installer.

The command uses the exact already-signed42 and existing source/unsigned cache, verifies original certificate/package/payload, and reuses the established normal-emulator/web/tab/receipt flow. It never reads the keystore or creates a replacement signature. If an in-place installation is still required it uses adb install --no-streaming -r, without downgrade/uninstall/clear-data flags. Each ADB step is printed. ADB failures retain exact bounded stderr/stdout privately plus a safe stage/reason. Unknown errors remain unknown, not guessed. No global ADB reset, additional emulator, unrelated shutdown, data wipe or cache purge. The previous bounded clock helper and app-scoped network probe run only after Android reaches the review stage; optional probe retrieval failure is not called confirmed telemetry. A later required ADB failure cannot be promoted to readiness merely because an earlier window was visible.

Independently, under the same owner-run lock, the command reads existing private collector configuration and makes one authenticated bounded30-day export to the original fixed HTTPS collector, with no redirect/proxy credential forwarding and normal TLS verification. It prints only aggregate row/timestamp counts, checks the registered localhost GharTV health route for adapter revision, saves a private ANALYTICS_COLLECTION_CHECK.json, and does not save raw rows. Missing configuration, unauthorized reads, network/schema errors, zero returned events, and actual returned events are distinct results. Manual delivery-check events remain separate. No telemetry consent is enabled and no test event is sent. A forbidden/unreachable report does not get labelled a known legacy adapter. Existing safe receipt mirroring excludes private counts/raw error logs.

27 local unit/guard tests passed with simulated ADB/HTTP responses and disposable files. They cover package failure classification, nonstreaming arguments, unknown errors, source-template42 identity, private file/destination/redirect restrictions, actual collector schema, no-data vs failed read, legacy timestamp mismatch, missing/forbidden report health and false-readiness prevention. Original Android APK is byte-unchanged; no new Android build was needed. No actual owner-Mac execution or real collector read has happened in this cloud turn.

## Analytics: current known defect, not a fabricated empty system

Live PR61 head remains09b12e12dc3ebcc324791119f0708b1d1a0f9a33 (Review28). Its ghartv_tenant_upgrade.py _timestamp reads event_timestamp/timestamp/created_at, not the collector's received_at/client_ts. Thus it can discard real events before displaying zero. Prepared fix8f0350a049d627dea15c8e43b1012f2d8f1ccd15 is not active on that branch/runtime. Installing42 never applied it. The new command OBSERVES this path; it does NOT secretly patch/restart the Analytics service or pretend the report is repaired.

Recovered existing saved Review29 launcher is OPERON_ANALYTICS_029.command, candidate59feca77e56987f61f4089e277b5eda305d4c30e based on09b12. Preserve and reconcile that existing prepared work, do not start a second Analytics service or overwrite its checkout. Activation of the GharTV report repair belongs to this same existing Analytics PR61/runtime. No private collector export can be inferred from repository source; local command readback is needed to establish actual event counts.

## Preservation and prior build evidence

Code42 remains the compiled100-Android-test artifact from run36392603996; app sourcefab21208..., unsignedbf02954bcd7e2a4268c04ae866fd7cf2b0cd6b34c6af5254e4d6ca0ffbe7015f, source archive670933773a79f528a2bc5c90e0576a03aaba03de0ec7d4201eb7b232c6d8ac35. Its original opener70bda9... failed the native Android stage in the latest owner run and is not a resolved-startup claim. Complete prior42 build/test/clock and playback limitations are preserved in CURRENT_HANDOFF.md Git blob e740d6e136b75e8ebc0a0956ba29138bf8b12810 and REVIEW_CANDIDATE.json.

No household feed change, new APK, physical-TV installation, owner files deleted, warehouse write, Analytics restart, cloud Obsidian write or verified Memory replica in this turn. The existing local flow writes actual local outcomes when run. Published38 remains separate; do not promote42 under38's approval. No raw screenshots, credentials, event rows or ADB logs are uploaded by this source update.
