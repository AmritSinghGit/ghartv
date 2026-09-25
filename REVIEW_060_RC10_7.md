# GharTV code34 — native search navigation and player tray

Continue the same ghartv lane/branch/PR1. The approved household release remains the exact signed code33; this is a separate, unapproved review and must not replace its update feed. No new controller, native Mac worktree, emulator or signing key is created.

Typed search and explicit Voice use the existing provider search route. Voice uses the TV's installed Android recognition activity and returns text to the same search field; it is not always listening and GharTV does not record audio. Recognition availability/network behavior depends on that service. A missing voice service falls back to typing.

On a provider search page, a bounded read of visible title links renders native result buttons in a three-column grid. D-pad/OK navigates these results without a cursor. The UI only lists actual matching links that the current document exposes; no invented titles or promise of playable availability. Unsupported layouts/verification keep the provider page available. There is no catalogue API or remote scraping service.

After a title opens, a compact hideable GharTV tray provides Play, Players, Not playing, Search and Use page. Only visible, labelled Player/Server/Source number buttons/options are listed, at most12. No fixed six-player list. Play uses the provider's selected/first option. A choice revalidates the page and control label before selecting that exact existing control. It neither extracts video URLs nor replaces the provider's player.

The native tray hides after inactivity; Menu/Back restores it. Not playing tries an untried offered choice once, retaining session-local tried markers. Bounded automatic next-player only responds to a directly observable single video element's explicit media error after a settling period. It stops on navigation/network/TLS/provider-verification errors. Cross-origin player state is not inspected or called healthy; for that common case the user chooses Not playing. A20second delay is a prompt, not evidence of failure or an automatic skip. No loop after the offered choices are exhausted.

The code33 cursor remains an optional Use page fallback. Native search/selection turns it off for ordinary remote navigation. Existing live-TV guide, preview behavior, family customization, signing identity and approved public page are preserved.

## Scope and tests

The existing CI builds code34, then runs the6 existing cursor tests and6 new actual WebView snapshot/control tests with local synthetic markup. Those tests cover visible result filtering/deduplication, actual offered player enumeration, permitted UI selection, stale page/control rejection, challenge holds and foreign-origin rejection. They are not live-provider selector compatibility, full native UI or speech-service acceptance. Read VALIDATION.json for actual results; tests are not claimed until the workflow passes.

The candidate APK is unsigned until the original saved signing identity signs it. No new release-signing key, debug-key replacement, production promotion, device update or Mac cleanup occurs here. Do not use the code33 opener to install code34. The approved signed code33 hash remains7e9f088ad3443dfbb7c557656ec795031c029e975bbcc4316d0add3140991fbe; its publication is independent and still requires those exact bytes.

No anti-bot flags, CAPTCHA bypass, TLS weakening, DRM/entitlement bypass, hidden session data, cross-origin iframe introspection or protected stream extraction. Page scripts read public visible results/control labels and invoke only an explicitly selected control. Playback stays within the provider's permitted player. A native GharTV Media3 player still requires authorized media/DRM integration and is not claimed by this review.
