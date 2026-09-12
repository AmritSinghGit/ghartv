# Review and publish GharTV v0.5.4

## Review RC5

Run `GHARTV_V054_RC5_REVIEW.command` from the canonical checkout. It continues the existing `main`, builds with the existing signing identity, publishes `v0.5.4-rc5` as a prerelease, installs over the existing emulator app without clearing data, and records the exact SHA in lane `ghartv`.

The GharTV review is independent of local Operon Analytics materialisation. The analytics tenant is registered on the existing Operon branch/PR through the GitHub authority when the historical worktree is absent. No replacement branch or worktree is created.

## Stable publication

After owner approval, run `GHARTV_PUBLISH_STABLE_V054_FROM_RC5.command`. Type `PUBLISH 0.5.4` only after physical-TV acceptance. The script reuses the existing signing key, builds versionCode 14, publishes stable release `v0.5.4`, verifies the APK, and only then changes `update/latest.json`.

Do not uninstall the existing Hisense application. Update it in place to retain Jio login, favourites, diagnostics choice and local household suggestions.
