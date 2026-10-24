# code64: one click plays the movie again (patch for PR1's app source)

The app source of the published code63 APK lives on `codex/ghartv-remove-auto-preview` (PR #1), not on `main`
(see `PUBLISHED_SOURCE_POINTER.md`). This patch is the code64 change against that source, kept here so the lane
branch does not carry a second copy of the app. Nothing here is a release, a feed change or an approval.

| Item | Value |
| --- | --- |
| Base source | `d0dea76361fa3f92f643f7436ada7cf65cc30a10` (published code63; android-tv is identical at PR1 head `56426dbefd00`) |
| Patch | `code64-one-click-play.patch` |
| Patch SHA-256 | `7c88cc8bfb3b7cbcb1059635f2d6c2917f8db81c3ce798c46ae114345a7c3ab1` |
| Result | versionCode 64, `0.6.0-rc15.5-one-click-play-review` |

What it changes is written up in the patch's own `REVIEW64.md`. In short: a pointer click that lands Discover on a
title page now activates the single unambiguous Watch action and starts the visible player, once. Series pages, Back,
and titles not opened by a click stay manual.

## Test it without touching PR1

`.github/workflows/code64-patch-candidate.yml` (this branch) checks out the exact base source, applies the patch, builds
release/debug/androidTest and runs the focused and complete emulator suites. Its artifact holds the unsigned APK,
the patched source zip and `CANDIDATE.json`.

## Build the version to install (existing PR1 checkout, existing signing key)

```
git fetch origin
git show origin/lane/ghartv-nova:patches/code64-one-click-play/code64-one-click-play.patch | git apply   # on codex/ghartv-remove-auto-preview
git add -A && git commit -m "fix(review64): one pointer click plays the movie"
git push origin codex/ghartv-remove-auto-preview      # review64-candidate.yml builds + tests it
python3 tools/tv-first/install_candidate.py            # downloads, signs with the existing key, installs on the Nova emulator
```

Owner UAT on the physical TV is still required; publication to the household feed is a separate decision.
