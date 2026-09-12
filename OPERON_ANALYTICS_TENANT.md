# GharTV product and Operon Analytics tenant

GharTV is an independent product, repo `AmritSinghGit/ghartv`, lane `ghartv`, package `in.ghartv.nova`. It is not a VCNow product deployment.

The existing analytics registration is tenant `ghartv` in `operon.analytics`, lane `operon-analytics`, repo `AmritSinghGit/operon`, PR 61, branch `codex/opr-analytics-003-vcnow-data-control-convergence`. Do not create another analytics product, tenant, database or worktree to represent the same capability. Earlier registration candidate ANALYTICS-090-008 was observed at `52a66778d717fbb34b7da9aef95e7fd17f378dd1`; re-read actual refs before modifying that lane. This turn does not merge or deploy it.

## Current reporting

The Operon adapter design reads the existing collector with a server-held admin credential. Its intended browser access differs from the interim static single-owner reader at `docs/owner.html`: that hosted shell asks for a token kept in page memory, while the canonical Mac command creates a mode-600 private owner copy containing the existing token. Do not distribute that private page or token to customers. Prefer server-side scoped read-only credentials and owner/support roles for the commercial portal.

The existing collector/D1 and opt-in diagnostics are retained. Sampled installation IDs, last-received state, app versions, features, measured timing, errors, source quality and freshness are diagnostic observations, not billing identities or an exhaustive real-time audience count. GitHub APK downloads are not installations. The detailed export is bounded to 5,000 recent events. Missing data is unavailable, not zero or certified healthy. No precise location or successful programme history is collected by this proposal.

## Planned customer boundary, not deployed

Product/analytics tenant `ghartv` stays fixed. A future authenticated customer model adds household ID, household profiles, paired device IDs and optional reseller/organisation grouping. Enforce that boundary on the server from the authenticated principal, not a client-supplied tenant label or installation hash. Billing, media entitlements, private assets, support actions, exports and deletion must use the same ownership boundary, with consent/audit/revocation. Do not mix VCNow or other tenant data into it.

No multi-household backend, identity service, new Postgres schema, commercial billing or production tenant migration was performed here. PRODUCT_COMMERCIALIZATION.md defines the launch priorities and content-rights gates. RC2 source `b4d0304441b7d00833e4d475c16e43e1ef92b3f3` changes the Android provider directory and per-channel picture preferences only; the existing collector contract remains valid.
