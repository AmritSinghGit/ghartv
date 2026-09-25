# GharTV — Discover37 owner review, 25 September 2026

PUBLIC_AND_HOUSEHOLD_RELEASE=HELD_BY_OWNER_SUPERSEDES_PREVIOUS_CODE33_PUBLICATION_REQUEST
REVIEW=CODE37_COMPILED_ANDROID_TESTS_PASSED_OWNER_EXECUTION_PENDING
APPLICATION_SOURCE=102f0ec62cddb97131e2825fe209834dab927d5e

## Same authority, no new product or native runtime

Continue lane ghartv, AmritSinghGit/ghartv, branch codex/ghartv-remove-auto-preview, draft PR1, package in.ghartv.nova, normal AVD GharTV_Nova_Manual_google_tv_API36 / emulator-5580 and web8790. Operon OwnerOS39, Fabric56 and Analytics61 remain separately owned. Read this handoff, REVIEW_CANDIDATE.json, RELEASE_HOLD.json and managed receipt5687119492 before acting. No new Mac repo/worktree/key/emulator/runtime/database was created.

Latest verified native receipt remains GHARTV-CYAN-36-20260925T132515Z-2288: installed36/source da3f5f11ef3df071600a61193c05e44b4156be12, signed505e11701d608c662ea4633e786ef48774b3ffc2c48cc6c31aa83e4960fb65b8, normal Nova foreground and Obsidian readback. The owner accepted the improvement in original poster selection but reported missing detail-page remote controls. This is not a code37 installation or approval receipt.

## Owner release decision has changed

Owner now explicitly says to hold world/public rollout while finishing the experience. Decision comment5833335683 and main RELEASE_HOLD.json supersede the previous immediate-publication request for signed33. Do not advance update/latest.json, publish a new family APK, create a new public release or launch marketing based on the older approval. Preserve all existing signed33/36 artifacts and old production. The previous public feed was24; no feed change was made here. Review37 has an Actions artifact and an owner-chat download, not a public GitHub release. Further source work is authorized in the same branch.

## Exact compiled owner-review bundle

Source102f0ec62cddb97131e2825fe209834dab927d5e; version0.6.0-rc11-discover-review/code37.
APK GharTV-code37-review-unsigned.apk:6112882bytes; SHA256410e3dcad44f638e0ecf1ae67691359cb9a69704961f078350cb720c938d9132.
Source GHARTV_CODE37_SOURCE.zip:1285039bytes; SHA256181eb6a3f74a00ac891fe677b79a3ad95069b7a603550eaa78b404d320722696.
Existing-entry delivery GHARTV_OPEN_REVIEW.command:9416028bytes; SHA25618e8cdf3165fa1c79ee5f5de35d9f52f80cf04d23254b0e86ec49e6430838c76.
Actions run36147949654, artifact10869873729 (ghartv-code37-owner-review), archive SHA256bf2ae7ae8c55720cabd833cfdf98d6bdefa43bf06599c34eb8cdcbdd1dc11002. Actions retention is7days; current owner-chat command contains the exact APK and source, so there is no dependency on a new public release.

Replace the previously downloaded command of the same name and run:
/bin/bash "$HOME/Downloads/GHARTV_OPEN_REVIEW.command" --candidate 37

No local Gradle build. No argument also selects37;33/34/35/36/publication arguments are rejected. The compiled APK and source are embedded and checksum-verified into the existing artifact cache. Only the existing artifact-resolution function was extended; original-key reading, signing, APK verification, normal-Nova installation/window, web/tab reuse and receipt functions are AST-unchanged. Required original certificate40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c. Signing/key/passwords remain on the owner Mac. No new key, debug-key substitution, uninstall, data clear, downgrade, scrcpy, unrelated process stop or household promotion.

## Implemented Android experience

Discover now starts with GharTV's own home, covering the provider page before its first visible navigation. It renders real returned poster URLs, display titles and available metadata as image cards, at most60. No fabricated catalogue or image-less text-grid fallback. Provider verification/missing data produces an explicit state with Refresh/Open provider page; it is not called empty content or successful playback. Images use an explicit HTTPS provider/TMDB-image host allowlist and bounded rendering size, without a persistent image disk cache. Accepted36 search results continue to show the original provider posters instead of replacing them again.

Guide Find and the new Voice action, film Search/Voice and supported search/voice keys in the existing player use one validated query route. Cached live-channel matches are labelled separately from provider film results. Voice uses the installed Android recognition activity only on explicit action; GharTV receives text, cancellation does not navigate, missing voice service offers typing. System-wide voice interception or actual speech-service availability is not claimed.

FilmPageFocus adds detail-page focus and selection for Watch Now, Add/Remove watchlist, buttons, links, forms, selects and visible media frames. The native controller validates the current page and coordinates then dispatches an actual in-WebView click for the user's selected action. Watchlist prefers the current title's Add/Remove control before a generic header link. Input fields and dialogs retain editing/focus rules. This does not inspect cross-origin iframe content or extract streams.

The existing hideable GharTV tray retains Play/Players/Not playing/Hide and now has Watchlist. Play can invoke Watch Now before source choices exist. Only actually exposed source choices are listed; the code does not invent six providers. Watchlist remains the provider's account action, subject to login, not a duplicate GharTV playlist or an unverified Saved message. The underlying media engine remains the provider player. Native Media3 playback of arbitrary sources, protected media or every iframe is NOT implemented or claimed. Existing manual Not playing and bounded observable-error behavior remain; unobservable playback is not treated as success or failure.

Punjabi Plus is removed: its entry button, MovieHubActivity source, manifest registration/package queries and executable legacy references. The release DEX independently contains no MovieHubActivity. Ordinary Punjabi channels, language filters, family personalization, TV preview, guide and playback remain. Historical product research is marked retired; Git history and regression tests checking absence are preserved.

An in-app Privacy & content use notice, review PRIVACY.md and review-branch docs/privacy.html explain independent experimental UX, third-party rights, public availability not permission, query/voice/IP handling, local storage, consent and limits. Clear film site data is explicitly confirmed, clears the film WebView state and native memory suggestions, preserves encrypted Jio account/favourites and does not silently reload the provider. No disclaimer promises immunity or transfers all legal responsibility to a provider. A monitored rights/privacy contact and qualified legal review are still public-launch gates. The public homepage/privacy deployment was not changed.

Tor is explicitly deferred. This Android Discover build uses direct HTTPS; no automatic Tor route or Brave requirement was introduced. Future optional Tor work needs separate DNS/subresource/media routing, cookie/isolation, leak, latency and fail-closed testing, not a claim that installing Brave makes Android private.

## What the actual website inspection established

Existing provider-observation workflow36142525102 used normal Chromium identity and no saved account to attempt home→search→details→watch. .app redirected to .st, which showed security verification. Observation stopped there without bypass. It did not inspect every provider page, authenticated watchlist state, iframe player or actual movie playback. PROVIDER_FLOW_OBSERVATION.json preserves the exact result. The owner's screenshots establish visible Watch Now/watchlist UI patterns; controlled tests verify our handling of them, not a licence or universal website compatibility.

## Actual validation, including limits

Final workflow36147949654 SUCCESS. Release/debug/instrumentation compiled.32 actual Android36 tests passed in87.181seconds:19 retained poster/cursor cases and13 new Discover/detail/voice/removal cases. Tests cover real native Watch Now/watchlist clicks (isTrusted), arrow/OK selection, forms/modal focus, native home image views/metadata/card focus, no invented missing artwork, cancellation/query bounds, retired class absence and Watch Now→offered player selection. All data/images were local synthetic/owned fixtures; no real movie or account was used.15 cursor geometry assertions,29 preview assertions and9 retained TV-contract checks passed.

One initial test caught a missing poster being converted to the homepage URL. It was fixed, and all32 tests reran successfully; the failing test was not removed. Watchlist header-versus-title prioritization was also corrected before final validation. Source was expanded from a hash-verified sparse delta into the same branch and normal commits; no capsule files remain in the final source.

Downloaded archive and every SHA256SUMS entry were independently checked. Release binary manifest confirms in.ghartv.nova/code37/expected version. Release DEX contains new components and not the retired activity. Embedded command APK/source roundtrip, valid/invalid command modes and unchanged signer/runtime functions were checked independently. Source archive excludes font files. The captured home screenshot did not show the native home and is NOT used as visual proof; native view hierarchy, image objects and interactions were asserted separately. Full live provider flow, real speech service, owner-Mac execution and physical-TV UX remain unverified until the owner review.

## Continuity and cleanup boundaries

GitHub source, this handoff and review metadata are synchronized. This cloud run did not write/read the owner's Obsidian vault or prove Amrit Memory replication. The same existing opener attempts local receipts/Obsidian/GitHub mirrors only when actually executed. No automatic knowledge in all arbitrary chats is claimed. Native cleanup:0files/0bytes. No branch-parity census, broad worktree/Docker prune or other-lane control was performed. Preserve old signed working candidates, original signer, user data, dirty/unpushed source and the existing canonical cleanup policy.

The review is not ready for broad streaming marketing: end-to-end owner acceptance, permitted provider/catalogue/media access, privacy/rights handling and secure verified distribution remain gates. A community around remote-first UX and permitted/user-owned media is distinct from promising free access to everything online.

Prior36 handoff blob a1ee70a11e0b00d841e47c612103369ebbbb46a7 and manifest blob ea628219498c08759001004e9986e9970861cdb3 remain in history. Do not use their public36 asset or their earlier33 promotion instruction for the current37 review.
