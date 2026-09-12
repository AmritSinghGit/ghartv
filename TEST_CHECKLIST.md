# GharTV v0.5.4 RC2 owner-review checklist

## Identity and preservation

- [ ] Version is `0.5.4-rc2-living-room` / code `11`.
- [ ] Android updates the existing `in.ghartv.nova` installation rather than creating a second app.
- [ ] Jio session, guide, favourites and diagnostics choice remain after installation.
- [ ] Stable `update/latest.json` remains `0.5.3-observability` / code `10` during review.

## Living-room home and indexing

- [ ] **For you** opens first after migration from the old All view.
- [ ] Continue/Recent contain only channels that reached real playback, never failed tune attempts.
- [ ] Working now contains channels proven playable on this TV/account.
- [ ] Category chips show channel counts.
- [ ] Exact number lookup and searches such as `101`, `ptc`, `punjabi news` and `sports` rank correctly.
- [ ] Leaving and returning to Punjabi, Favourites or another view restores the prior highlighted channel.
- [ ] Reset Continue and For you removes only local suggestions, not login or favourites.

## Player guide

- [ ] The Now/Next panel appears briefly and disappears after remote inactivity.
- [ ] Retained button focus cannot keep the panel visible forever.
- [ ] It shows channel, exact guide scope, Now, time range, progress and Next.
- [ ] INFO/OK/D-pad reveals it; Previous, Guide and Next are reachable.
- [ ] No permanent strip covers full-screen video.

## Scoped channel switching

- [ ] Punjabi, Hindi, Favourites, Working now and search results each retain their exact CH +/- scope.
- [ ] Direct number tuning still searches the complete Jio catalogue.
- [ ] Every error screen can skip to the next likely working channel.

## Playback and access recovery

- [ ] A 401/419 refreshes once and then requests reconnect only when necessary.
- [ ] A 403 requests a fresh playback authorisation once.
- [ ] Explicit subscription and persistent Jio account/device access remain separate.
- [ ] DNS/timeout/reset and selected 5xx failures retry once.
- [ ] A stalled buffer requests one fresh live feed, then presents recovery actions.
- [ ] Temporary failure labels expire; a later successful playback marks the channel Working.

## Physical-TV acceptance

- [ ] At least one HLS channel plays.
- [ ] At least one entitled DASH/Widevine channel plays.
- [ ] Twenty consecutive channel changes remain stable.
- [ ] Playback remains stable for thirty minutes.
- [ ] Diagnostics report references appear only after opt-in and the private owner report contains no sensitive Jio data.
