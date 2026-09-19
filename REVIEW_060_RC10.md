# GharTV Nova RC10.1 — television, web and independent films

Same independent GharTV lane, repository AmritSinghGit/ghartv, existing implementation branch codex/ghartv-remove-auto-preview and PR1. Package in.ghartv.nova. Android 0.6.0-rc10.1-web-films, versionCode28. Public update feed is not changed by this candidate.

## Start the review

On the Mac, run `bash RUN_GHARTV_WEB.command` for the web player, private owner reports and separate FlixMomo search. This prepares the exact bundled web runtime on the existing port8790 without starting Android or requiring signing. It verifies its source identity, preserves the developer checkout and reuses the existing private collector configuration. Reports remain manual. It does not deploy a public streaming server.

Run `bash RUN_GHARTV_REVIEW.command` to open the web first, then prepare the original-signer Android update and review the existing Nova5580. A new emulator is not started under memory pressure. A booted, name-verified Nova may be reused. No other emulator or workload is stopped.

Run `bash PREPARE_GHARTV_UPDATE.command` to open the web and prepare an in-place-update APK with the original local signing identity, without starting an emulator. The exact private APK path and SHA256 appear in the handoff. This is PREPARED, not installed or accepted. No keys or passwords are requested or generated. Signing requires the owner's existing Android SDK/JDK17/configuration. An unsigned cloud APK must never be sent as a household update.

Each command ends with a saved receipt and Enter-to-copy / Enter-to-finish controls. Existing receipts and signed preparation evidence survive a later boot failure. No automatic public promotion, silent TV installation, source reset or database cleanup occurs.

## Implemented here

Separate FlixMomo Android entry from both the guide and Jio login. Its in-app Chromium WebView uses the provider's actual search control and page/player. Includes fullscreen video, back and retry controls; external top-level navigation, downloads, native permissions, file access and invalid TLS are blocked. No guessed stream URL or DRM bypass. Android uses the device network, not the desktop Tor route.

Separate local web search at `/flixmomo.html`. On-demand search uses an installed Brave/Chromium/Chrome with an isolated profile. Results are accepted only after a provider search route or search response is observed. No fixtures are shown as real results. Embedded direct playback respects provider framing; an isolated browser player is also available on this computer.

Explicit Tor browser routing detects an existing SOCKS service on127.0.0.1:9050 or9150. Start Tor Browser or your existing Tor service first. The same browser checks the Tor Project before accessing the provider. No direct fallback, no claim of complete Tor Browser anonymity, no automatic routing of Jio or Android. Embedded direct playback is disabled when Tor is selected.

Web-first binary delivery and prepare-only original-signer update path. No npm/Gradle/source patch is required on the Mac to prepare the bundled web runtime; existing Node is required. Optional browser driver is bundled, not an entire browser or Tor distribution.

Refreshed public download-page source distinguishes the household release from the new review candidate. Static GitHub Pages is not a streaming backend.

## Existing behavior retained

RC9's automatic muted focus preview, first-rendered-frame timing, no preview loops, Punjabi/language/category/search intersection, scoped next/previous, initial lower-panel Next focus, stable provider IDs, keyboard entry, Simrat/family management, picture modes, Still Watching/Resume and integrated Jio session are retained. The original TV_EXPERIENCE_CONTRACT is unchanged. No AI super-resolution claim is made.

Owner analytics reuses the same collector and private server-side credential. Summary and bounded event sample remain independently requested and independently reported. No new viewing database, analytics service, polling loop or owner-control portal. Unknown/stale/error states are not zero or active viewers.

## Acceptance boundaries

Cloud compilation, source-contract checks, local HTTP/UI tests and deterministic security fixtures are separate evidence from live provider and physical-TV acceptance. Read DELIVERY.json and VALIDATION.json for actual outcomes. A loaded page, route check, source build or APK foreground state is not proof a movie plays. Provider availability, framing, account/region/entitlement/DRM rules still apply.

Cousin-server hosting, public streaming deployment, live licensed-provider acceptance and physical-TV update/rollback remain owner-environment checks. Code25 reserve is not a downgrade from code28. Recovery after installing this version must use an appropriately higher version signed by the same key, without clearing data.

## Normal provider verification — RC10.1
The first RC10 live search encountered the provider's security verification page because the search allow-list blocked its required challenges.cloudflare.com script. RC10.1 permits that exact verification host while retaining HTTPS and network restrictions. It does not solve or bypass a CAPTCHA. A required verification now produces a specific status and an explicit Browse FlixMomo action opens the provider in the selected isolated browser route for the owner to complete checks manually. Read VALIDATION.json for the new probe's actual result; a successful build is not a provider playback pass.
