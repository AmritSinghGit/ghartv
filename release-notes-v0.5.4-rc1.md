# SUPERSEDED — historical RC1 notes

RC1 was never the authoritative owner-review candidate after RC2 replaced its stale source assumptions. Use `release-notes-v0.5.4-rc2.md`.

# GharTV Jio Live v0.5.4 RC1 — living-room intelligence

Review-only prerelease built from opt-in diagnostics and sanitized owner-side runtime
logs. It keeps the existing package, signing identity, Jio session, repository and
`ghartv` Operon project lane.

## Improved

- Local-only `For you` and `Recent` rows based on successful playback.
- `Available`, `Subscription` and `Needs attention` are explicit guide views.
- O(1) number lookup, cached views and ranked Unicode prefix/token search.
- Category counts, remembered focus per view and a denser responsive TV grid.
- Clearer channel access badges and reduced hero-panel footprint.
- One automatic transient DNS/timeout retry before recovery actions.
- Better error wording and retained diagnostic references.

## Privacy

Successful viewing history remains local to the television and is not included in
GharTV telemetry. Existing diagnostics remain explicit opt-in and privacy-filtered.

## Review boundary

This is a prerelease. It does not change `update/latest.json`; v0.5.3 remains the
stable update offered to installed televisions until owner acceptance.
