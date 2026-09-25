# Review 37 status

Public release and marketing are on hold by owner decision. The former Punjabi Plus provider-launch feature is removed from executable code. The historical provider research below is retained as research, not active app capability or licensing approval. See RELEASE_HOLD.json and PRIVACY.md.

# GharTV — commercial product direction

Owner direction, 12 September 2026. Continue the existing independent `ghartv` project and its existing Operon Analytics tenant. This document records decisions and proposed work; it does not certify a commercial launch, content licence, customer-isolation implementation or installed release.

## Positioning

Build a Punjabi-first family television companion: easy live-channel navigation, legitimate movie discovery, a remote-friendly interface for parents, clear picture controls and consent-based support. Charge for GharTV's own software/services only after the relevant distribution terms and integration rights are cleared. Do not market the current household client as free cable, all subscriptions included, a licensed OTT aggregator or native 4K enhancement.

The initial audience should be households in a supported region with their own eligible provider accounts. A diaspora proposition needs a separate territory-by-territory rights catalogue; India's JioTV access must not be represented as worldwide. Hotels, waiting rooms, shops and public screenings require a separate commercial exhibition assessment, not a household subscription.

## Actual implementation versus roadmap

RC2 `0.5.5-rc2-movies-picture`, code 16, source `b4d0304441b7d00833e4d475c16e43e1ef92b3f3`, adds a Movies & services provider directory and per-channel Original/Fit, Zoom/Crop and Stretch, with reset to Original. It retains RC1's app-owned voice search and playback diagnostics. Its unsigned release was compiled in GitHub run 34686776416. Production remains the accepted RC5 code 14 until explicit review acceptance and promotion.

The provider directory opens the provider's official TV app, store listing or website with user confirmation. It deliberately does not scrape movie libraries, extract media URLs, share passwords, perform background logins, claim a subscription is valid, play a provider stream in our player, or purchase anything. The Movies entry is also available from the existing login screen without requiring a Jio login. Installed is not signed in; signed in is not entitled; a title's presence is not regional playback availability.

Not implemented: a licensed in-app Punjabi VOD catalogue, unified title search, cross-provider watch history, commercial billing, household account isolation, TV fleet management, AI super-resolution, mandatory or silent installation. Do not sell these as delivered.

## Punjabi movie sourcing and integrations

### Start with official provider apps

**Chaupal** is the first Punjabi-content candidate. Its official help documents Android TV installation and provider-owned QR/device-code sign-in. Mobile-only subscriptions do not work on TV. RC2 uses its official TV package `video.laminar.tv.chaupal.android` and https://www.chaupal.com/ . A partnership is not established by including a launch link.

**ZEE5** maintains a Punjabi film category, including titles such as Qismat 2, Puaada and Maurh on the retrieved page. The category can change by territory and time. RC2 uses `com.graymatrix.did` and https://www.zee5.com/movies/lang/punjabi . Public catalogue visibility does not provide commercial metadata, artwork or streaming rights.

**JioHotstar** is the current unified service formed from JioCinema and Disney+ Hotstar. RC2 uses `in.startv.hotstar` and https://www.hotstar.com/in . TV support, account eligibility and plan restrictions remain the provider's responsibility; do not treat a Jio mobile login as a universal OTT login.

**PTC Play / PTC Network** is a relevant next partnership lead for Punjabi programming, digital films and Gurbani. PTC's official page describes these offerings. Current TV-app support, territory, API/embedding terms and redistribution rights need confirmation before adding an integration. It is not implemented in RC2.

**Other official destinations**, including Prime Video and rights-holder YouTube channels, can follow after region, TV package, public link and policy checks. For YouTube, use its official player/app, preserve attribution and ads, and do not download, separate audio, resell free access or bypass restrictions. Do not label an arbitrary uploaded film as authorised solely because it is online.

### Two distinct movie product stages

1. **Discovery and official-app hand-off:** licensed/approved metadata or a simple provider directory; badges identify the provider, region, verification date, subscription requirement and where playback occurs. A household can maintain its own local watchlist. Personal subscription status remains unknown unless a sanctioned provider integration verifies it. No cross-app cookie/token extraction.
2. **Licensed GharTV playback:** negotiate directly with a producer, distributor, aggregator or other verified rightsholder. Record chain of title, covered territories, permitted devices, licence window, SVOD/AVOD/TVOD model, concurrent-stream restrictions, DRM, HDCP, subtitles, promotional artwork/trailer rights, source quality, restoration/upscale permissions, royalties/minimum guarantees, reporting and takedown terms. Only then ingest masters into a controlled encoding/CDN/licence pipeline. No existing deal is claimed.

Metadata rights, playback rights and subscription-resale rights are separate. A title registry should carry provider/title IDs, languages, rights evidence, valid dates, territory/device constraints, approved link, entitlement mode, available renditions, last verification and missing-data status. Store no raw account tokens or stream URLs in public GitHub.

## Login model

Keep GharTV's eventual household account separate from provider accounts. Provider sign-in stays in each official app today; authorised future OAuth/device flows must use the provider's documented process. Do not collect other services' passwords or OTPs inside GharTV. A provider may share identity/entitlement only through a sanctioned agreement/API. No universal-login claim.

A commercial GharTV account can own billing, profiles, paired TVs and local-preference sync. Those services are planned, not implemented. Diagnostic consent is optional and separate from necessary billing/account records. Do not require optional viewing telemetry as a condition of paid core features.

## Picture improvement roadmap

**1. Source and display truth.** Keep source pixels, pixel aspect, selected rendition, declared source bitrate, measured bandwidth, output/display mode, dropped frames and buffering distinct. Prefer an authorised HD source when available. A wider image or a larger bit rate is not newly recovered source detail.

**2. Aspect control, now in RC2.** Original/Fit is the default; Zoom/Crop removes edges, Stretch widens objects. Save choices locally per channel. No automatic crop of logos/captions or assumption that every PTC feed shares a format. Embedded bars need separate diagnosis. These controls do not apply to video played in another provider app.

**3. Hardware-supported enhancement.** Evaluate TV/box picture processors without claiming GharTV can control them through a universal API. NVIDIA's official SHIELD documentation illustrates that AI upscaling is model/content dependent. Do not infer SHIELD support on the owner's unrelated TV or emulator. A capability can be unknown; unknown is not enabled.

**4. Bounded AI experiment.** On licensed, unprotected reference material, compare original against enhancement at a real target-device budget: frame processing, added delay, dropped frames, thermals and artefacts on faces, text and movement. At 30 fps a frame interval is about 33.3 ms; at 60 fps it is about 16.7 ms, shared with the rest of playback. Expose off/original and automatic fallback. Preserve the original master and label generated detail. Do not promise native HD/4K reconstruction.

Media3's ordinary setVideoEffects path explicitly does not work with DRM-protected content. Do not extract protected frames, reroute decrypted streams or bypass DRM to implement an effect. Provider-app launches leave rendering under the provider/device, not GharTV. Server-side remastering could be offered for material with explicit processing/distribution rights, but adds processing, storage and delivery cost; no service has been deployed.

## Priorities toward a paid product

### First: reliable family use

Finish real-device review of RC2: TV navigation, each provider's install/open/return path, microphone permission/denial/fallback, picture preference retained per channel, login preserved across in-place update, playback recovery and readable remote focus. Record actual app version and APK identity. Add Punjabi/Gurmukhi and Roman-Punjabi discovery, large-text/senior mode and clear access errors before adding many unrelated modules. Language/large-text features are proposed, not delivered in RC2.

### Then: features worth paying for

Household profiles and favourites, a local-first watchlist, content reminders, accessible captions/audio-track controls where supported, sleep timer, user-paired phone remote and consented support sessions. Cross-provider resume/progress requires a sanctioned provider integration; opening an app does not expose its viewing state. Capture only provider-permitted history.

For owner operations, prioritise release adoption, last-seen evidence, tune/start timing, rebuffer rate, crashes, supported device families and explicit stale data. Diagnose API, connectivity and decode stages separately; never infer that every buffer is the ISP's fault. Screen captures or remote actions require an explicit local consent and revocation path; no unattended household surveillance.

### Commercial foundation

Add server-enforced household boundaries, device enrolment/revocation, authenticated owner/support roles, audit logs, read-only reporting tokens, rate limits, tested backup/restore and account/data deletion. The current shared diagnostic collector and hashed installation ID are not a full multi-tenant commercial identity system. Avoid embedding a collector-wide admin key in customer-facing pages. The current private owner HTML is an interim single-owner tool.

Introduce a GharTV subscription only after rights, licensing, privacy and billing review. Keep its price separate from provider subscriptions. An initial offer could combine premium family features with optional installation/support; pricing is a hypothesis to validate with an opt-in household pilot, not an active tariff or revenue forecast. Affiliate/referral fees require a real partner agreement. Direct film rentals or subscriptions require content rights and the associated royalty/reporting model. Do not add an unreviewed checkout or collect money in this RC.

## Architecture and tenant boundary

- Product/project/repository: GharTV / `ghartv` / `AmritSinghGit/ghartv`.
- Operon reusable analytics tenant: `ghartv`, existing lane `operon-analytics`, PR 61. Do not create `ghartv2` or a new analytics product.
- Future customer boundary: server-owned household ID, optional reseller/organisation ID, household profiles and paired devices. This is below the GharTV product tenant, not VCNow data.
- Derive household authorisation from authenticated identity on every request, not a client-supplied tenant string or diagnostic hash. Enforce separate data/object prefixes, queries, support roles and billing ownership. Prevent cross-household reads, exports and control commands.
- Diagnostic installation IDs remain pseudonymous and optional. Do not silently map them to precise location, billing identities or successful viewing history. Record consent/version/retention separately.

## Go / no-go for charging customers

Do not launch the existing household JioTV client for sale merely because the APK runs. Jio's published terms restrict personal viewing and restrict revenue generation, retransmission, integration and access outside its authorised application. Get provider-specific written approval/legal review or remove/replace the unapproved integration in the commercial distribution. Paying for a normal subscription is not a sublicence. This is a practical launch gate, not a legal opinion on every possible architecture.

Before taking payment: settle provider/content/software dependency rights; remove private household assets from the generic build; decide public/private source licensing; verify genuine device compatibility and in-place signer continuity; complete privacy/consent/customer isolation and support/refund obligations; decide compliant billing/distribution; measure support and delivery costs. A Play listing review and backend deployment are not established in this record.

The repository currently carries an MIT licence. That permits selling the covered software with its notice but also gives recipients broad rights to copy and redistribute it; it does not give rights to third-party films, logos or provider streams. No licence was changed in this turn. Use legitimate added service value as the business model and seek legal advice on any future licensing changes.

Suggested pilot, not deployed: a small set of consenting households across low/mid-range real televisions. Collect opt-in crash/rebuffer/tune-time evidence and structured usability feedback; confirm install success and fewer support calls. Expand only after household rights/privacy checks, not based on download counts or an emulator screenshot.

## Sources checked 12 September 2026

- Chaupal TV installation and TV-plan distinction: https://help.chaupal.tv/portal/en/kb/articles/how-to-install-chaupal-app-on-your-android-tv
- Chaupal official TV app: https://play.google.com/store/apps/details?id=video.laminar.tv.chaupal.android
- ZEE5 Punjabi category: https://www.zee5.com/movies/lang/punjabi
- ZEE5 app: https://play.google.com/store/apps/details?id=com.graymatrix.did
- JioCinema/Hotstar integration: https://www.ril.com/ar2024-25/media-and-entertainment.html
- JioHotstar app: https://play.google.com/store/apps/details?id=in.startv.hotstar
- PTC Play official overview: https://www.ptcnetwork.com/ptc-play-app/
- JioTV use/restrictions: https://www.jio.com/jcms/en-in/jiotv-premium-plan-recharge-terms-and-conditions/
- ZEE5 terms: https://www.zee5.com/termsofuse
- YouTube API policies: https://developers.google.com/youtube/terms/developer-policies-guide
- Media3 effects/DRM limits: https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer#setVideoEffects(java.util.List)
- Hardware-dependent upscaling: https://www.nvidia.com/en-eu/shield/support/shield-tv-pro/
- Existing software licence: LICENSE in this repository.

These are public-source checks, not signed partner commitments, credentialed entitlement checks, commercial permissions or current availability guarantees for every title/territory.
