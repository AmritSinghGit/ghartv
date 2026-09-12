# Review, publish, inspect and close GharTV v0.5.4

## Review candidate

Use the exact-SHA review launcher supplied with the candidate handoff. It fast-forwards only the existing checkout, validates and signs RC2, installs it over the existing emulator application, preserves Jio login/favourites/diagnostic consent, publishes `v0.5.4-rc2` as a prerelease and records the run in the existing `ghartv` lane.

## Acceptance checks

1. Let the player panel appear and do nothing. It must disappear without needing Back.
2. Press INFO, navigate its buttons, stop pressing keys, and confirm it still hides.
3. Open Punjabi, Hindi, Favourites and a search; CH +/- must remain in that exact view.
4. Test an ordinary channel, subscription channel, repeated 403, temporary network failure and protected/Widevine channel.
5. Confirm failure screens offer Next working channel and Guide.
6. Confirm successful channels populate Recent/For you; failed attempts do not.
7. Search by exact number, partial name, language plus genre and spelling prefix.
8. Soak playback for 30 minutes and change channels at least 20 times.

## Stable publication

After explicit owner approval, run:

```bash
bash "$HOME/Downloads/GharTV_Nova_v0.4.2/GHARTV_PUBLISH_STABLE_V054.command" publish
```

The command refuses an unexpected Git state, reuses the existing signing identity, builds version code 12, waits for canonical CI, publishes `v0.5.4`, verifies the APK asset, and only then updates `update/latest.json` so installed televisions receive the update.

## Logs and health

At any later time run:

```bash
bash "$HOME/Downloads/GharTV_Nova_v0.4.2/GHARTV_LOGS_AND_HEALTH.command"
```

It creates a private Desktop report containing Cloudflare telemetry summary/export when authorised, the collector health result, emulator package/activity/logcat evidence, current GitHub state, and recent sanitized terminal/continuity evidence. Zero telemetry is reported as zero, not treated as a failure.

## Close the Mac review

Run:

```bash
bash "$HOME/Downloads/GharTV_Nova_v0.4.2/GHARTV_CLOSE_REVIEW.command"
```

It first captures the owner report, stops only GharTV emulator instances and project Gradle daemons, retains source/signing/collector authority, writes the close handoff to Obsidian and `amrit-context`, copies the handoff, and closes after confirmation.
