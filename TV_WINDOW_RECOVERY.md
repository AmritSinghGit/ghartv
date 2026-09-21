# GharTV code30 — TV window recovery R1

## Latest owner result

Managed receipt PR1/comment5687119492 now records GHARTV-CYAN-17-20260921T103441Z-44495, updated2026-09-21T10:35:26Z: code30 signed/installed with exact bytes and Android MainActivity foreground, but MAC_WINDOW_UNCONFIRMED, window observed=false and frontmost=false. The APK succeeded; visible review did not. Obsidian and GitHub readback succeeded; amrit-memory replica remains unverified. The older code29 line in the launcher banner is stale text, not the installed package version.

Application source remains ae4c84578e26e707d1a11c5ff078f6395cf36dde. Owner-signed code30 SHA256 is 723d84553477fd5dc390c1001bfcd213919ffd434a4152905c6a243634e164ad. Do not re-sign/reinstall/rebuild it merely to show the display.

## Published recovery

Release: v0.6.0-rc10.3.1-tv-window-r1.
Recovery source: aae3184907554f402c2939ad95f3113c2d9f03cc, distinct from the unchanged application source.
Entry: OPEN_GHARTV_TV_WINDOW.command, SHA256 d13b19fd3061612dce88199cdf1d0a27d45a099487a8c66477a3e2b57866c569.
Bundle: GHARTV_TV_WINDOW_R1.zip, 24,506 bytes, SHA256 35d8615f4047c7de87b9d4f386eed225ae29ecb053c0c928dc3c07797e96abd6.

Run the downloaded entry with bash. It verifies the recovery, the current code30 receipt, the existing local transport helper and the installed APK. It opens the original Nova window when possible; otherwise it opens a local scrcpy view of the SAME Android display on emulator-5580, titled GharTV TV review - code30. This is not a second VM, web clone or additional Android virtual display. It does not install GharTV or change its version, key or data.

## Corrected failure path

The previous Swift probe only inspected CoreGraphics windows inside a successful NSRunningApplication(processIdentifier:) lookup. If QEMU was not registered as a GUI application, it returned MAC_WINDOW_UNCONFIRMED without querying windows. The new probe always queries the validated PID's windows, whether the AppKit lookup succeeds or not. Its fallback displays the existing Android screen rather than repeatedly assuming a hidden backend will become a visible GUI.

The recovery downloads the official portable scrcpy4.1 Apple Silicon archive from Genymobile/scrcpy, checks upstream SHA256 20fd47c9014dd5e0fa77091f3cb7adbda8445a360c4584aeaa0150b5b3988ff3, and verifies cached files on reuse. No Homebrew/global install or macOS security-setting bypass. The existing SDK adb and exact named Nova are used. One owned display process is reused, with PID/start-time checks. No existing VM restart or unrelated process shutdown.

If Nova stopped after the receipt, only that existing named AVD may be started, and only after a fresh normal resource-admission check. Already-running Nova can be reused despite memory WARNING. Unknown or changed source/installed digest/process/port/helper evidence causes a hold, not a forced replacement.

## Controls and privacy

Use arrow keys and Enter. Right-click or Option+B is Android Back. Click the display if macOS declines to make it frontmost. Closing the display disconnects this view and leaves Nova running. Do not drag files into the window.

The temporary upstream scrcpy helper transmits the selected Android display/control connection locally. The recovery does not record, capture the microphone, enable automatic clipboard synchronization, upload screenshots, collect provider credentials or remove DRM. Protected video surfaces can remain black and require native/physical-TV testing; seeing the guide is not playback certification.

Full private recovery evidence preserves the prior receipt before updating current state. The existing Obsidian progress note, compact light handoff and safe GitHub managed receipt are updated on actual owner execution, including an explicit EMULATOR_UI versus SCRCPY_EXISTING_NOVA distinction. Replica verification is not inferred from an Obsidian write. The recovery has not yet run on the owner's Mac.

## Actual tests and byte verification

Mac workflow35590689784:11 unit tests passed; native ARM64 probe showed an actual registered AppKit window, and separately handled an actual non-GUI process without the old short-circuit. The actual upstream Apple Silicon scrcpy executable ran, exposed all required options, and passed archive/cache verification. This Mac test did not connect to an Android device.

Display workflow35591679443: real scrcpy4.1 connected to an isolated headless Android36 cloud emulator. Its display stream decoded111 frames at320x640 while navigating the Android Settings test screen. The display test ran on Linux/X11 under Xvfb, not on the owner's Mac or Nova. Cloud-only test recording was NOT shipped. Earlier cloud attempts failed on AVD startup/config location; no success was claimed for them. The final attempt used explicit test AVD paths and passed.

The final release and all nested package-member hashes were independently verified after downloading artifact10634403104. The entry passed bash syntax validation, Python parsed successfully, and no font files or APK payload were included in this recovery. Validation is available as the release's VALIDATION.json.

## Preserved boundaries

Same ghartv lane/repo/branch/PR1, current app and original signing identity. No public homepage, film implementation, analytics lane, household update feed, provider access rule, physical TV, source checkout or other-project runtime was changed. No owner review approval or live-provider playback is claimed by cloud display tests.
