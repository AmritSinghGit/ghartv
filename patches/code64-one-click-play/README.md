# code64: one click plays the movie again (patch for PR1's app source)

The app source of the published code63 APK lives on `codex/ghartv-remove-auto-preview` (PR #1), not on `main`
(see `PUBLISHED_SOURCE_POINTER.md`). This patch is the code64 change against that source, kept here so the lane
branch does not carry a second copy of the app. Nothing here is a release, a feed change or an approval.

| Item | Value |
| --- | --- |
| Base source | `d0dea76361fa3f92f643f7436ada7cf65cc30a10` (published code63; android-tv is identical at PR1 head `56426dbefd00`) |
| Patch | `code64-one-click-play.patch` |
| Patch SHA-256 | `447990a0bdd1309b5ca781599b82002630ce4bb303ffe430eeffcbbd24f2da64` |
| Result | versionCode 64, `0.6.0-rc15.5-one-click-play-review` |

What it changes is written up in the patch's own `REVIEW64.md`. In short: a pointer click that lands Discover on a
title page now activates the single unambiguous Watch action and starts the visible player, once. Series pages, Back,
and titles not opened by a click stay manual.

## CI evidence (lane workflow, no PR1 push)

`.github/workflows/code64-patch-candidate.yml` checks out the exact base source, applies the patch, builds
release/debug/androidTest and runs the focused emulator suite on a GitHub Android 36 emulator.

| Run | Source | Tests | Result |
| --- | --- | --- | --- |
| 40130310991 | unpatched code63 (baseline) | 155 | 34 failed |
| 40131440024 | code63 + this patch (lane head `0e16e3f`) | 161 | the same 34 failed; all 6 `Review64OneClickPlayTest` tests pass |

The 34 baseline failures are old and not caused by this change: `Review50FeedbackTest` (16), `Review47ViewingTest` (13),
`PreviewControllerTest` (4), `FilmCatalogueObservationTest` (1). The first two fail on "video clock below 0.35 s" because
the CI emulator plays video slowly. The workflow therefore stays red until those are repaired; compare against the table
instead of expecting green. The complete suite step was not reached (the focused step stops the job). The unsigned APK
(`GharTV-code64-review-unsigned.apk`, versionCode 64) is in the run artifact `ghartv-code64-owner-review`
(14 days); it is not installable until signed with the existing GharTV key.

## Build the version to install (existing PR1 checkout, existing signing key)

Without pushing anything (the installer builds locally and signs with your existing key):

```
cd <your PR1 checkout of codex/ghartv-remove-auto-preview>
git fetch origin
git show origin/lane/ghartv-nova:patches/code64-one-click-play/code64-one-click-play.patch | git apply
git add -A && git commit -m "fix(review64): one pointer click plays the movie"
python3 tools/tv-first/install_candidate.py --local     # builds, signs with the existing key, installs on the Nova emulator
```

Or let GitHub build it: after the commit above, `git push origin codex/ghartv-remove-auto-preview` runs
`review64-candidate.yml`, then `python3 tools/tv-first/install_candidate.py`. That pushes to PR #1's branch, so it
needs the owner's say-so.

Owner UAT on the physical TV is still required; publication to the household feed is a separate decision.
