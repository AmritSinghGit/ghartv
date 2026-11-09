# The 34 red emulator tests: first triage (31 Oct 2026, read from CI run 40131440024 log; no build run)

Same 34 fail on unpatched code63 (run 40130310991) and on code64, so they are old, not caused by the patch. Failure messages in the log, grouped (each message appears about twice in the log, so counts below are of distinct message kinds, not tests):

| Cause | Evidence | Classes | Likely fix (not applied; needs a CI run to confirm) |
| --- | --- | --- | --- |
| Slow CI emulator never reaches video time > 0.35 s | "Owned player fixture must finish its initial playback attempt before the next interaction: {time:0.32..0.42, paused:false} ... state PLAYING" - the player IS playing, the fixture's 0.35 s bar is just too tight (times 0.328-0.417 seen) | Review50FeedbackTest, Review47ViewingTest (most of the 29) | wait on `PLAYING` state or lower the bar to ~0.15 s / poll up to 10 s |
| Preview expected PLAYING but was FAILED | "expected:<PLAYING> but was:<FAILED>" | PreviewControllerTest (4) | check the fake provider fixture; probably the 6 s `waitFor` too short on slow emulator, or fixture URL unreachable in CI |
| Wrong provider path | "expected:</[watch]/lantern> but was:</[movie]/lantern>" | one fixture-routing test | fixture serves /movie/ where test expects /watch/; test or fixture expectation drift |
| Count off by one | "expected:<9> but was:<10>" | FilmCatalogueObservationTest (1) | update expected catalogue count |

Next step: one CI run with the first row relaxed on a scratch branch of the lane (not PR #1) to see how many go green. Proposed as a patch in `patches/` like code64, so PR #1 stays untouched until the owner says yes.

## CORRECTION (6 Nov, from CI run 42082361743 with the 0.1 s patch: 161 tests, 55 failed)
The "time bar too tight" cause above was WRONG. Failures show the video time already above 0.46 s and not paused, so the bar was not the reason: the helper's other condition `settled[0]` (the fixture's playback attempt settled) is false, and the playback monitor reports state PAUSED / verified_start_ms null. The patch is parked (`patches/ci-relax-playback-bar/` kept only as a record; do not use). The run also had 20 more red tests in Review51PointerSearchTest, Review52ProviderMigrationTest and Review53ProviderRecoveryTest than the 25 Oct run, plus "expected movie/lantern but was empty" (40 log lines), so those may be flaky or newly red; not yet explained.
Next (read-only): find what sets `settled[0]` and why the monitor sits PAUSED on this emulator.
