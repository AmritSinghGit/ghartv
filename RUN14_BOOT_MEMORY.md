# Run14: memory pressure and failed emulator selection

## Observed owner outcome

The owner supplied GHARTV-CYAN-14-20260918T154238Z-97081 and a black Nova emulator window. The existing managed PR receipt was read back and matches that run: ACTION_REQUIRED, installed_foreground false, host memory pressure WARNING before and after, authenticated collector support-read success. The detailed private performance/port reports are NOT present in this chat's accessible file search; older run10 files must not substitute for them.

Intended APK: RC9 / code26, application source c20e3ef5dc8ff5dbee52c338930a5c4ac30157e8, locally signed digest fb48d1f7fc8d01d28cdb9f2532003e1a5c508a62bd05e65f2902377f2efd0d8a. Signing/payload/certificate steps succeeded. The run stopped at EMULATOR_SELECTION with EMULATOR=UNCHANGED, REVIEW_SCREEN=NOT_VERIFIED and BLOCKER=Hold. Do not call code26 installed or treat the black window as a verified RC9 playback defect.

The actual bundled RC9 launcher and every internal SHA256 entry were verified in the implementation container. Its outer handler preserves str(e) for its own Stop only; a Hold from tools/tv_local.py is reduced to the class name. The real stage is written separately by that helper to the owner's PORT_CHECK.json. Possible stages include transport settling, an unrelated listener, process exit and Android boot timeout. The exact stage from this Mac remains unknown until that saved report is received. Do not choose a cause from the black screenshot alone or use a blind GPU/DNS tweak.

The current host sampler reads kern.memorystatus_vm_pressure_level (1 NORMAL,2 WARNING,4 CRITICAL); the warning is not a free-RAM percentage heuristic. It establishes pressure at those observations, not which app caused it or that memory was the sole cause of emulator failure. CPU/GPU, Android boot and live-provider state remain distinct. The public production feed was independently read as code24; no household rollback or new release occurred.

## Small local relief action

Source tools/performance/run14_relief.py, exact source commit f8b2b72cc5682a793b557b0709a06e1fb51a730a, SHA256 b38a65962b7559ddd790ddd2c14bf8eb94444be9dda4270ebe3ddf43fb1aeafb (16036 bytes). Optional command argument `relieve`; default `inspect`.

This is a one-shot action, not a new Mac doctor, dashboard service or candidate installer. It reads existing run14 PORT_CHECK, performance-before/after, receipt and bounded emulator-start log; measures current Mac pressure/swap/free disk/top CPU/resident memory; and attempts bounded Android boot/package observations only on verified Nova5580. It saves a local static report and a private ZIP containing the actual evidence. No provider credentials or whole-device logs are collected. No direct remote Mac connection is claimed.

`relieve` requires the current receipt still belongs to run14/sourceRC9 and the lane lock is free. It may send one SIGTERM only to a unique owner-owned QEMU executable under the existing SDK with exact AVD GharTV_Nova_Manual_google_tv_API36 and explicit port5580. PID command/start-time is rechecked. It never signals by broad process name, terminates a group, sends SIGKILL, stops another emulator or restarts global ADB. Ambiguous/changed/unavailable state is preserved. If the process does not exit in the bounded wait, it reports that rather than escalating. Nova is left OFF: no automatic restart, installation, signing, build, network settings or web runtime restart.

This removes a running workload only when identity is proven; it does NOT promise a specific memory-pressure improvement. Disk cleanup is deliberately zero while the new review is not installed/accepted. Original APKs, keys, app/AVD data, cached artifacts, rollback copies, historical evidence and other lanes are retained. Reports say files_deleted=0, not an invented cleanup saving. Raw observations/process names remain private on the Mac; no automatic upload. A new run note is written in the existing Obsidian lane if available, without replacing the TV experience contract or installation receipt.

## Validation and pending evidence

18 local checks passed: exact process/owner/SDK/AVD/port matching, shell/other-instance rejection, one-PID-only SIGTERM decision, changed PID/ambiguous process protection, unknown recheck not treated as stopped, actual temporary-file evidence preservation and private ZIP roundtrip, changed-review no-stop, symlink preservation and HTML escaping. Process responses are fixtures; no owner's Mac was executed or stopped here. Source Git blob92359cb566ade429e13edad1dc01d0c63bd4bb54 was compared to the tested file. Shell syntax and non-Mac refusal also checked.

Next evidence needed is the newly revealed GHARTV_RUN14_DIAGNOSTICS_PRIVATE.zip shared in the conversation (not public GitHub), or the existing exact PERFORMANCE_AFTER.json and PORT_CHECK.json. Do not ask for screenshots/handoff already supplied. Identify expensive competing work from actual measurements before proposing another lane's shutdown. Then resolve the actual port/boot stage and complete the SAME code26 review; no new feature APK is justified solely by this failed selection. The masked Hold handler and late persistence of the signed candidate remain source-delivery defects to correct, not current claimed repairs.
