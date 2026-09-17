# GharTV provider compatibility — source review, not movie playback

## Scope and current evidence

The owner supplied an exact public FlixMomo watch-page link and reports that it plays in Brave and Tor Browser. The requested outcome is unified search and playback inside GharTV. That outcome is **not delivered** by this change. The public homepage could be read through the web tool and says its media is hosted by third-party services. The exact watch page did not load in that lookup. A direct check in the implementation environment ended at its bounded DNS timeout; it did not receive a provider HTTP response. This does not contradict the owner's browser observation, prove site downtime, or establish whether this movie uses DRM.

This commit implements only the inspectable, bounded first step: a private, on-demand provider-page compatibility check inside the EXISTING GharTV server. It does not scrape/relay a movie, discover a verified streaming API, install Brave or Tor, or add a remote-browser streaming service. A successful page check is never called successful video playback. Main/public feed and the installed RC7 APK are unchanged by this source work. The unfinished RC8 performance build is separate; no performance or playback acceptance is inferred here.

## Implemented interface

The existing authenticated owner console links to `provider-access.html`. The page offers **Check HTTPS page**, **Check with isolated browser**, and a clearly labelled **Open original provider page** external link. Merely opening this screen makes no provider or D1 request. The input is not persisted or logged by the new code.

`GET /owner-api/providers/status` reports provider integration/configuration states without network activity. `POST /owner-api/providers/inspect` accepts `mode: https` or `mode: browser` and a registered HTTPS page URL. Both use the existing private owner nonce. Preview access is denied, POST requires the same origin, bodies are bounded, and only one check may be active. No arbitrary-host fetch/proxy endpoint is added.

Only exact `flixmomo.app` HTTPS addresses are registered, for inspection rather than playback. Credentials, custom ports, fragments, private/reserved IPs and foreign redirects are rejected. DNS answers are checked and pinned to the request while preserving certificate validation. The request reads at most 128 KiB; it reports HTTP/type/frame-policy and markup counts, never response HTML, media URLs, cookies, tokens or licence data. DNS timeout and an HTTP/request timeout are distinct results. Frame-policy absence is not treated as embedding permission.

## Optional browser configuration

Normal television does not require a browser dependency and does not start one per channel. To enable only this explicit diagnostic on an appropriately sandboxed non-root deployment, the operator supplies `GHARTV_PROVIDER_BROWSER_EXECUTABLE` as the absolute path of an already installed compatible Chromium-family executable, and installs the separately pinned `playwright-core` dependency under `web-player/browser-tools`. No browser executable, plugin, system resolver or user profile is installed or modified by this commit. Arbitrary Brave versions are not certified by a Chromium automation API. Tor Browser is not a Chromium executable and is not supported by this worker.

The worker refuses root, requests the Chromium sandbox, uses an empty disposable context and a reduced environment, and is killed after 18 seconds. No existing account/profile or collector secret is passed to it. Requests are limited to the registered origin; media, downloads, popups, websockets and unapproved third-party requests are blocked. Consequently, a third-party movie player may not initialize in this diagnostic. Its result is intentionally a page/JavaScript observation, not a complete playback simulation. This is not an OS-level guarantee against browser compromise; untrusted browsing must remain sandboxed and separate from credentials. No `--no-sandbox`, certificate ignoring or web-security disabling is present in the production worker.

## Actual validation

`node tools/provider-access/check.mjs`: **52 focused cases pass**, including exact-origin validation, public-address checks, pinned requests, bounded redirect/timeout/error states, private route authentication, same-origin writes, preview denial, single-flight limits and the actual existing-server bootstrap/link. Provider HTTP results are controlled fixtures, not live films.

`python tools/provider-access/browser-ui-check.py`: the actual served HTML renders and its explicit modes, input validation, zero automatic requests and 390px layout pass with an in-page fetch fixture. This environment blocks browser navigation to localhost; the test fetches the HTML from the real server, renders trusted content and labels that limitation. It is not a network-connected browser integration test. The production worker's refusal to run as root was also exercised. `tools/provider-access/VALIDATION.json` records the distinctions.

No live Brave/Tor session, authenticated provider request, streaming output, native Android build, Mac installation, Obsidian write or public promotion is verified by these tests. No raw logs, private viewing history or credentials are committed. The existing GharTV branch/PR/lane is reused.

## Required next proof for native playback

A provider integration must establish an authorized supported embed or media interface, its session/codec/DRM requirements and actual picture/audio in the target environment. Title discovery alone cannot satisfy that gate. No endpoint is invented here and no protected session or DRM extraction is provided. Full server-browser viewing/relay and automatic Tor routing are not implemented.

## Primary references inspected

- FlixMomo homepage: https://flixmomo.app/
- Brave protected-content documentation: https://support.brave.com/hc/en-us/articles/360023851591-How-do-I-view-DRM-protected-content
- Tor website access limits: https://support.torproject.org/tor-browser/general/website-blocking-tor-exits/
- Tor speed: https://support.torproject.org/tor-browser/general/tor-browser-speed/
- Playwright browser support: https://playwright.dev/docs/browsers
- Playwright sandbox/deployment guidance: https://playwright.dev/docs/docker
