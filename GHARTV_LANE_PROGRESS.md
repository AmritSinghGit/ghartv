---
lane: ghartv
entity_type: project
analytics_tenant: ghartv
status: RC2_SIGNING_AND_OWNER_REVIEW_PENDING
updated: 2026-09-12
repository: AmritSinghGit/ghartv
canonical_handoff: CURRENT_HANDOFF.md
---
<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->
# GharTV — cross-lane progress and next acceptance

## Authority and observed releases

GharTV is a separate product/project, not VCNow. Preserve existing repo `AmritSinghGit/ghartv`, lane `ghartv`, branch `main`, Android package `in.ghartv.nova`, checkout `~/Downloads/GharTV_Nova_v0.4.2`, existing signing identity and AVD `GharTV_Nova_Manual_google_tv_API36`. No duplicate project, branch, worktree, runtime or tenant.

Production: `0.5.4-rc5-family-photo`, code **14**, source `b46b2cd607c309d364d531b5fd9da618cd007f6c`, APK SHA-256 `6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199`. The public update feed still advertises this accepted binary. Installation on dad's physical TV is unverified. Automatic checks are not silent/mandatory installation. New source and website changes do not update installed TVs.

Owner candidate: `0.5.5-rc2-movies-picture`, code **16**, application source `b4d0304441b7d00833e4d475c16e43e1ef92b3f3`. Actual release build passed in GitHub run `34686776416`, job `103535017009`. Tag `v0.5.5-rc2` was observed with only the unsigned APK and build record. Unsigned APK SHA-256 `32efc94eaa635b3da1d1895570b857e9f5d2dea5b5bed1151818c48d03a7cd21`. It is not installable without a signature. A successful source compile is not running-app evidence.

Implemented in RC2: Movies provider directory (Chaupal, ZEE5, JioHotstar); explicit official-app/store/site launch; entry from the login screen without a Jio login; per-channel Original/Fit, Zoom/Crop, Stretch and reset; prior app-owned voice and diagnostic work retained. Provider-specific login and content playback stay in the official provider app. Account matching, subscription verification and an aggregated movie catalogue are not implemented.

## Latest owner decision — account-aware discovery, not just app shortcuts

The owner wants GharTV to show content available through the viewer's existing subscriptions and to use genuine, sustainable integration methods other aggregators use. This is the next product requirement, not acceptance of an already-completed feature.

Proposed user flow: select existing services and the route through which they were purchased; open provider-owned login/QR activation where needed; use approved catalogue data to show Punjabi titles; filter "My services"; open the correct provider/title; identify any separate rental, region, device or plan restriction. Avoid asking users to buy a subscription they already hold.

Keep four independent states: **app installed**, **service selected by user**, **account authorised by provider**, **title/TV entitlement verified**. Never turn the first two into the last two. Same phone/email is not a shared authentication token or proof of subscription. Where an official session cannot be queried, show **Account not verified** or **Selected by you**, not "connected"/"included". User confirmation must be labelled as such and include a date; verify again when stale.

For an authorised future link, keep only a provider-scoped subject/token on the appropriate protected backend/device, expiry, region, plan/device capabilities, verification source/time and revocation state. Do not auto-submit the Jio phone number to other services, harvest cookies/tokens, inspect private app storage, intercept encrypted traffic, root household TVs or bypass media protection. No existing provider account was checked in this chat.

## Genuine existing patterns checked on 12 September 2026

- **Google TV** asks users to select subscribed services for recommendations. This demonstrates user-selected services plus discovery, not universal verification of every subscription. https://support.google.com/googletv/answer/10070483?hl=en
- **Chaupal** supports existing email login or its own phone/QR/device-code activation; a mobile-only plan does not work on TV. Reuse that provider-owned flow rather than recreating it. https://help.chaupal.tv/portal/en/kb/articles/how-to-install-chaupal-app-on-your-android-tv
- **JustWatch Partner API** supplies title/offer/provider/region data and links. Its documentation says a contract precedes the partner token. Catalogue availability is not personal entitlement. Validate coverage for Chaupal/ZEE5/Punjabi titles in the target region before choosing a data contract. https://apis.justwatch.com/docs/api/
- **Airtel** documents partner-specific activation and cases where content is available only through Xstream Play, independently of native-app subscriptions. Purchase route matters; don't assume a ZEE5-branded benefit works in the standalone app. https://www.airtel.in/mobile/terms-conditions
- **Tata Play Binge** is a separate subscription bundle across partner services. This is not evidence GharTV can import arbitrary consumer accounts. https://www.tataplay.com/binge/product-selection
- **Android app sandbox** normally isolates one app's private data from another. An installed provider app does not make its token/entitlement readable. https://source.android.com/docs/security/app-sandbox

Preferred sequence: approved regional catalogue + "My services" preferences + documented deep links; provider/aggregator entitlement integration where sanctioned; native GharTV playback only with appropriate rights. No contract, partner token, licensed catalogue, SSO or commercial release is claimed.

## Review and continuity recovery

The previous Cyan Review 3 command only downloaded source. It was not a playable app review. Cyan Review 4 keeps the SAME RC2 application/unsigned bytes and supplies a local review route: first write/read back this lane note, then verify source/package, request explicit owner permission to sign/publish a REVIEW APK and replace only the named emulator. Signing uses Android's native apksigner password prompt with an owner-selected existing keystore. The helper does not read signing.env, collector.env, passwords or another app's account data. The owner can alternatively supply an already-signed APK for verification, or run `--memory-only`.

Verify signature against the accepted RC5 and signed APK contents against the exact unsigned RC2 before installation or publication. Record the measured signed digest and installed bytes. Do not regenerate a key, uninstall, clear data, downgrade or overwrite a different code-16 candidate. The production feed is not changed.

On the running review: inspect Movies from the login screen/guide, open one installed provider with its existing account, check missing-app handling, return to GharTV, set different picture shapes on two channels and verify persistence/reset. Inspect Voice permission/fallback and local Picture statistics. This review does not demonstrate the new account-aware discovery requirement or AI super-resolution.

## Cross-lane ownership

- **GharTV lane:** Android, release/signing identity, public website, household UX, provider capability/entitlement design.
- **Operon owner control PR39:** record the existing independent project and current evidence; do not create another controller/lane.
- **Operon Analytics PR61:** retain tenant `ghartv` / capability `operon.analytics` / existing branch. Current reader and collector remain; no new database, analytics service, deployment or merge. A future household identity boundary must be server enforced; diagnostic hashes are not customer auth.
- **Obsidian / amrit-context:** existing vault `~/Documents/Amrit Executive Memory`, lane folder `90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv`. Write one managed current note and a run-specific receipt, verify file bytes, then invoke existing `~/bin/amrit-context` (or PATH equivalent) handoff and sync-once. Record actual exit codes, not assumed all-chat replication.

Pending commercial gates: provider/content/metadata rights and integration clearance; separate family photo/birthdays from generic builds; authenticated household isolation, billing/support/deletion; real-device validation. GharTV may be a sellable service, but this RC2 is not cleared for charging customers. AI super-resolution, native movie playback and mandatory/silent installation remain unimplemented.

This file is the published cross-lane record. It does not itself prove the Mac Obsidian copy or other chats have received it. The owner-run receipt establishes local note readback, bridge exit status, review APK identity and emulator result separately. Preserve unknown files, credentials, histories and all other lanes; cleanup only exact obsolete installers and owned temporary files.
