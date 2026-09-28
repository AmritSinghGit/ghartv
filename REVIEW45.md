# GharTV45 — visible Play controls and consented interaction outcomes

Same GharTV branch, package, original signing identity, normal Nova and published household38 unchanged.

Play now prioritizes an explicit visible same-document play control overlapping the one media region. It no longer rejects a legitimate semantic play button as though it were an arbitrary obstruction. Unknown overlays, unrelated navigation, multiple ambiguous controls and changed pages still prevent automatic clicks. Native focus/layout changes precede coordinate capture. This does not read cross-origin iframe contents, extract media addresses or replace the embedded provider engine with native Media3.

Consent-gated film_control_result events carry a short attempt reference, bounded action/outcome enums, engine and elapsed milliseconds only. They distinguish target found, target obscured/changed, gesture sent, clock advancing, visual timeout and unobservable iframe. No movie titles, search text, URLs, cookies, stable hardware IDs, screenshots or raw keystrokes. Each attempt sends at most16 distinct outcomes. Existing foreground-time tracker is now attached to Discover lifecycle; this is time in the app, not proven watch duration.

New controlled tests include the real native Play action on an owned video with a separate semantic play-cover button; success requires an unpaused advancing video and no source reselection. Unrelated obscuring overlays are not clicked. The old122 tests are retained. Live provider acceptance, cross-origin media health, native film engine, speed/seek control for arbitrary iframe players and QR remote remain unverified or outstanding. No new diagnostics opt-in or public rollout.
