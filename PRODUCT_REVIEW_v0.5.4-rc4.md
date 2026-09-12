# GharTV v0.5.4 RC5 — product review

## Authority

- Repository: `AmritSinghGit/ghartv`
- Branch: `main`
- Baseline: `6228319fcefe0bf49685d925da426490c77f261a`
- Package: `in.ghartv.nova`
- Lane: `ghartv`
- Review tag: `v0.5.4-rc5`
- Stable update held at: `0.5.3-observability` / versionCode 10

## Owner feedback implemented

- The long vertical selected-channel tile is retained.
- A muted, explicit 15-second channel preview is embedded in that tile and releases its decoder automatically.
- The tile is height-budgeted for a 1080p TV so Watch Live, Favourite and Recall remain visible even in birthday mode. Recall now toggles to the genuinely previous successfully requested channel instead of reopening the current channel.
- Text baselines, line heights, focus targets and translucent surfaces are tightened.
- Family dates map to Mom, Amrit, Harjas, Wifey, Sis, Dad and Simrat exactly as supplied by the owner.
- Automatic, birthday-preview and standard-preview modes are available without changing the television clock.
- Voice search automatically falls back to the text-search dialog when no speech recogniser exists.
- Programme search uses a bounded Jio EPG sample rather than slowing catalogue startup.
- Pause, rewind, forward and Live controls are capability-gated; GharTV does not claim DVR/catch-up where Jio does not expose it.
- GharTV remains an independent product while tenant `ghartv` is registered in the existing Operon Analytics capability. A missing local analytics worktree cannot block the Android candidate.

## Acceptance boundary

This is a prerelease for emulator and physical-TV review. It must not update `update/latest.json` or become the latest stable GitHub release until explicit owner approval.
