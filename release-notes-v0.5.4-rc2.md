# GharTV Jio Live v0.5.4 RC2 — living-room reliability

This is an owner-review prerelease. It keeps the same Android package, signing identity, GitHub repository, `main` branch, Cloudflare diagnostics collector and Operon `ghartv` project lane.

## Fixed from physical-TV feedback

- The player information panel now hides on real remote inactivity. Retained button focus can no longer keep the strip visible forever.
- The panel shows Now, programme time, progress, Next, previous channel, guide and next channel.
- Channel Up/Down uses the exact visible language, genre, favourites, personal or search-result scope passed from the guide.
- Subscription and persistent Jio-access failures remain separate.
- Every failure path offers **Next working channel**, **Guide**, and a context-appropriate retry/reconnect action.
- Jio `401`/`419` sessions refresh once; `403` receives a fresh authorisation attempt; transient DNS, timeout and selected `5xx` failures retry once.
- A buffering watchdog requests one fresh live feed before presenting recovery actions.
- Temporary failure labels expire automatically instead of hiding a channel forever.

## Better living-room guide

- Local-only **For you**, **Continue**, **Recent**, **Favourites**, **Working now**, language, genre, **Subscription**, and **Needs attention** views.
- Successful playback, not failed tune attempts, powers local suggestions.
- Constant-time number tuning and a cached Unicode-normalised ranked search index.
- Global search prioritises exact numbers/names, prefixes, token coverage, favourites, proven working channels and local successful history.
- Category counts, per-view focus memory and a responsive four-column 1080p grid.
- Incremental RecyclerView updates reduce full-grid redraws.

## Privacy and release boundary

Successful viewing suggestions stay only on the TV and are not read by telemetry. Opt-in v0.5.3 diagnostics remain privacy-filtered. This prerelease does not modify `update/latest.json`; televisions on v0.5.3 do not receive it automatically.
