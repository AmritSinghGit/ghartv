# GharTV Review 37 — Discover and remote-complete detail navigation

Same ghartv repository, branch, PR1, package and existing Nova runtime. This is a compiled owner-review artifact. Public release and marketing are HELD; it is not promoted to the household feed or a public GitHub release.

## Implemented scope

Discover opens a GharTV home before any provider navigation becomes visible. It reads provider-supplied poster suggestions, titles and available metadata and renders image cards, not text-only tiles. It does not invent recommendations or a catalogue. Verification or unsupported markup produces an explicit empty/error state and an option to use the original provider page. Images come only from the configured HTTPS provider hosts or image.tmdb.org. The native renderer uses bounded memory-size images and no persistent image disk cache.

Live guide Find/Voice, the film Search/Voice actions, and supported search/voice keys in the existing live player share one validated text route. Cached live-channel matches and provider film results remain separately identified. A voice request invokes the installed Android recognition activity; GharTV receives text, not retained audio. Cancellation does not navigate. System-wide voice routing outside GharTV is not claimed.

Search results keep the original provider posters as accepted in36. Detail pages now have remote focus for Watch Now, watchlist, buttons, links, forms, selection controls and visible video frames. The native controller invokes an actual in-view click on an explicitly selected current-page control. This does not read cross-origin frames or extract media. Menu opens the compact tray; Play can select Watch Now before player choices exist, and Watchlist can invoke the provider's own action. Login-dependent changes remain subject to the provider account. Native Media3 playback of every source is not claimed.

Punjabi Plus's entry button, MovieHubActivity implementation, manifest registration/package queries and executable legacy references are removed. Ordinary Punjabi language/channel functionality remains. Removal regression checks may mention the old class to assert its absence; historical product research is retained and marked retired.

An in-app privacy/content-use notice and the review-branch privacy documentation explain third-party rights, direct connections, query/voice handling, local state and limits. Clear film site data does not erase the encrypted Jio session or silently reload the provider. Calling a build experimental or attributing content is not a licence or immunity promise.

## Inspection and acceptance boundary

The fresh public Playwright observation reached provider security verification after .app redirected to .st. It stopped there with no bypass. We did not inspect every authenticated provider page or verify a live film played. The owner's screenshots supply the visible Watch Now/watchlist observations; Android fixtures verify our handling of those patterns, not universal third-party site compatibility. PROVIDER_FLOW_OBSERVATION.json preserves the actual result.

Review37 adds actual native-coordinator tests for detail focus and user-gesture clicks, Watchlist, forms/dialog focus, native image-home rendering, stable card selection, no fabricated fallback, shared voice-result handling, retired-feature absence and Watch Now→offered-player controls. Existing36 poster/cursor tests and accepted TV preview checks are retained. Consult generated validation for the actual test result; no live speech session, copyrighted movie, owner Mac or physical TV test is implied.

## Review and signing

The same GHARTV_OPEN_REVIEW.command bundles compiled37 bytes and exact source; no local Gradle build or public release asset is required. It pins hashes, reuses the original private signer/certificate, updates in place, and reuses the same normal Nova/web/tab handling. It does not publish, uninstall, clear app data, downgrade, create a new key/VM or delete old candidates. Latest owner-side runtime remains36 until a new actual receipt is observed. No cloud operation is an Obsidian acknowledgment.

Tor is not enabled. A future opt-in transport would require separate DNS/subresource/media, cookies, leak, latency and failure-policy testing. Brave installation alone does not change this Android WebView's connection route.
