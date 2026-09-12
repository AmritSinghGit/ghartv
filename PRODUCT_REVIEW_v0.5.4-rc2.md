# GharTV v0.5.4 RC2 product review

## Authority

- Repository: `AmritSinghGit/ghartv`
- Branch: `main`
- Baseline: `b9f16b936b666d191843f2a08fa29b9c299c02c3`
- Package: `in.ghartv.nova`
- Lane: `ghartv` (independent Operon project)
- Stable release retained during review: `v0.5.3`

No second project, repository, branch, worktree, signing identity, Worker, D1 database, package or continuity lane is introduced.

## Evidence boundary

The private Cloudflare event export is available only from the authorised owner Mac through `collector.env`. The review command retrieves it when present and reports zero events honestly when no household has opted in or sent data. Delivery/build logs are kept separate from television product errors.

## Product problems addressed

| Problem | RC2 behaviour |
|---|---|
| Player strip remains visible | Inactivity timer ignores retained focus and enforces a hard maximum visibility window. |
| No useful programme strip | Now, time range, progress, Next and remote actions are interactive. |
| CH +/- leaves Punjabi or another selected view | The guide passes the exact visible channel-number scope to the player. |
| Subscription screen traps the viewer | Next working channel, Guide and Try again are always available. |
| Every 403 looks like a recharge problem | Explicit subscription, expired session and persistent account/device denial are classified separately. |
| Channel later stalls or says unavailable | One network retry and one buffering/fresh-stream recovery occur before the error screen. |
| Failed channels stay hidden forever | Temporary access evidence expires; owner retry clears non-premium temporary state. |
| Too many channels and slow discovery | O(1) number lookup, cached views and ranked global search. |
| Catalogue feels like a developer grid | For you/Continue/Recent/Working views, counts, focus restoration and responsive TV cards. |
| No later evidence | Existing opt-in diagnostics plus a single owner logs-and-health command. |

## Acceptance decision

RC2 remains `REVIEW_PENDING`. Stable publication is a separate owner action after emulator and Hisense acceptance. The stable publication command bumps version code to 12, signs with the existing key, publishes `v0.5.4`, and only then updates the stable manifest.
