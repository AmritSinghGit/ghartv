# GharTV Review 64 — one click plays the movie again

Owner report (physical TV, code63): clicking a movie used to start playback at once; now it opens the provider's title page and needs extra clicks (Watch, then Play on the video).

**Cause.** Since Review51/56 Discover shows the provider's own pages under the green pointer and no longer drives them: no native title page, no automatic Watch. code49 and earlier turned a title click into Watch -> player start; code63 leaves both clicks to the viewer.

**Fix.** A pointer click that lands the provider page on a title page now runs a short, one-shot chain:
1. the title page's single unambiguous Watch action is activated (same safe selector as the older native Watch button: visible, enabled, registered host, exactly one match);
2. on the watch page the single visible player is started with a genuine touch;
3. the chain ends when playback is observed, when the 20 s start window lapses, on any failure, on Back/Menu/Search, or when the activity pauses.

Boundaries kept:
- Only a pointer click starts the chain (5 s window). Opening a title any other way (Back, search results, history, reload) never auto-plays.
- A page with several Watch actions (series with episodes) is not guessed at: the pointer stays in the viewer's hands and a short hint is shown after 25 s. If the viewer clicks Watch themselves, the player is still started for them.
- Back from the watch page returns to the title page and does not play again.
- The Sources tray and focus moves stay quiet while the chain runs. No consent screen exists in this flow, so there is nothing to remember per title or device.
- No change to Jio live, IPTV, routing/Tor, DRM, TLS, diagnostics or the update feed.

**Build.** versionCode 64, `0.6.0-rc15.5-one-click-play-review`. `.github/workflows/review64-candidate.yml` builds it and runs the focused and complete suites (Review64OneClickPlayTest: 6 tests; Review51/Review56 harnesses keep the manual pointer flow they assert).

Not claimed: physical-TV or live-provider behaviour. The provider's real Watch/Play markup can differ from the owned fixture; a series page or an embedded player the app cannot read falls back to the pointer. Owner UAT on the TV is required before any production feed change.
