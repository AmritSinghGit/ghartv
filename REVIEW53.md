# GharTV Review53 — preview regression checks and visible provider recovery

Existing lane / PR1, base 2a225e1ef2a88d5561b77b296c69849b4c9cc9ea. Preserve Review52 and Review51 signed rollback and original signing identity. Owner review, not public rollout.

## Evidence and scope
The owner's Review52 report is distinct from test/telemetry evidence. During this continuation the Mac resolved five fixed Jio/provider/update hosts while the existing Nova emulator timed out resolving every one. This does not establish a worldwide outage or an ISP policy. Preview/controller source was unchanged in the 51-to-52 diff.

The existing preview controller instrumentation was absent from the explicit release CI list and its owned clip was not generated. Review53 executes all seven existing tests with the real controller and locally generated media, alongside the retained278 and 14 new regression tests. Accepted650ms dwell/4500ms startup/12000ms preview, mute, saved-Off choice and focus/lifecycle cancellation are unchanged.

Pointer-mode page loading/timeout feedback previously wrote a detached status label. Recovery is now visible on the same provider screen with explicit Retry/Connection/Back controls and no overlay over fullscreen video. Main failures remain separate from supporting resource failures; successful explicit retry restores the pointer.

Preview exceptions expose only a bounded cause classification through the existing consent-gated telemetry and UI. No URLs, query/title content, private exception text, cookies or tokens are added.

The original bundled owner opener calls the existing independently corroborated same-AVD DNS recovery only for shared DNS failures and normal memory pressure. It uses only verified existing Mac resolvers, creates no AVD, and no longer calls an incomplete/shared-failure probe healthy. No proxy, arbitrary domain, certificate bypass, host DNS edit, account reset or data clearing.

## Verification gates
Require 299 distinct full-suite cases, 91 focused cases and all8 two-case fullscreen repetitions, source contracts and network-recovery unit tests. Controlled playback is not live-provider or owner-network acceptance. Record exact results before claiming a candidate. No analytics/runtime ownership change or public feed update is part of this repair.
