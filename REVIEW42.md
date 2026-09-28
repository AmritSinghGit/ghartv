# GharTV Review42 — playback controls, real title copy and measured emulator clock

Same lane, branch, PR1, package, original signing key and normal Nova emulator.
Household38 is unchanged;42 is an owner-review candidate, not rollout approval.

The latest41 screenshots show: live TV `Chain validation failed`, a TV clock around11:29pm while the Mac shows12:28pm, source controls with focus remaining in the page, and a generic site description replacing the movie overview. No user screenshot or incidental private account information is included in this source or artifact.

## Changes
- Play operates the currently loaded media rather than reselecting/recreating its source. Players still selects a different offered source explicitly.
- A paused player's initial native control tray receives focus; directional keys stay in the controls. A focused tray does not vanish on its idle timer; Hide/Use page remain explicit exits. Enter on a focused video is not treated as text editing.
- Visible DIV/span-based current-title overview is preferred over generic JSON-LD marketing copy. Generic-only descriptions are unavailable, not synopsis. DIV sidebar fields, lazy artwork and bounded related-section exclusion are handled.
- Existing opener measures UTC epoch difference on the exact emulator. Only for a measured drift of2minutes–7days, with two existing HTTPS reference hosts agreeing with the Mac, it uses Android's normal alarm shell command to correct that emulator's time and verifies readback. No adb root, CA/DNS change, new emulator, physical-TV or host-time writes. Refused/unverified changes are reported without escalation. A corrected already-running app may be restarted without deleting data.
- Before opening the guide the opener requests the existing fixed-host DNS/HTTPS probe and saves only that app's allowlisted diagnostic tag privately. The app now distinguishes expired/not-yet-valid certificates beneath wrapper exceptions and shows verified HTTPS clock offsets. These measurements are not proof of signed-in live playback.

## Evidence boundary
12 clock-helper unit tests with simulated process responses and16 retained player normalizer tests passed locally.12 new Android cases cover generic JSON-LD vs visible description, DIV facts/artwork, related metadata, real owned-video Play without source reselection, tray focus/timeout, video Enter and certificate root cause. The full88 previous Android tests are retained. Final compile/test result must come from this run, not this prose.

The live Jio client, native PlayerActivity, repository and MainActivity are byte-identical between published38 and41. That rules out edits to those four files, not every runtime/dependency/network cause. The screenshot's time mismatch is a lead; the owner UTC readings and certificate causes remain unknown until the opener runs. Correct date/time may repair validity errors, but cannot make an untrusted/invalid server certificate acceptable. All TLS checks remain enabled.

Film video remains in the provider's embedded browser; no native media extraction, third-party protection bypass, QR remote or Tor. Tests use owned local media, not a commercial provider movie. The existing Analytics schema repair is still a separate PR61 activation; this APK does not deploy that Python service. No app telemetry opt-in is silently enabled, no events are fabricated and no unrelated cleanup/restart is performed.
