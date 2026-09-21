# GharTV RC10.3 — in-app FlixMomo and visible TV review

This supersedes the unshipped external-tab proposal. The goal is search and playback inside GharTV, retaining FlixMomo's pages, attribution, account controls and player. No browser-detection flags, DRM, verification rules or response security headers are altered.

## Delivered components

Android code29 changes the existing FlixMomo activity to navigate directly to the provider's encoded /search?q= route. It no longer injects search scripts into the provider DOM. Search results, provider browsing, video/fullscreen controls and Back remain inside the GharTV activity. The existing TV guide and preview contract remain unchanged.

The Apple Silicon Mac review includes a compiled native GharTV FlixMomo view based on WebKit. It is launched only by the user's verified review command, not by HTTP requests. Its own Search/Browse/Back controls operate inside the GharTV window; the provider is not opened in an external browser and no browser profile is imported. The native component is ad-hoc signed, not notarized or an App Store release. The web-only viewer points to the native app instead of offering the rejected automated or external-tab substitute. Direct connection only; this is not Tor routing. A hosted browser-only edition still requires a permitted embed/API integration.

The same launcher now distinguishes the signed/installed Android package, Android activity foreground, and actual Mac window onscreen/frontmost observations. It fresh-checks the existing named Nova emulator on5580, starts only that existing AVD when resource admission allows, and brings forward its window. Headless, embedded, absent, blocked or unconfirmed states are not labelled visible-review success. No other VM, container or application is stopped.

## Run

Run GHARTV_REVIEW_RC10_3.command for the complete review: web, GharTV film window, original-key signing and the existing TV candidate. Use --tv-only to focus on TV review, --films-only to open only the native film window, or --web-only for the browser viewer. These are modes of the same delivery, not additional runtimes or products. Full local/Obsidian receipts and the existing safe GitHub mirror remain; no routine handoff paste is needed.

## Verification boundaries

Read VALIDATION.json for actual results. Cloud compilation, static/unit checks, native Mac window opening and provider-page navigation are separate from successfully playing a movie or a licensed channel. Provider human verification may still be required. Live provider video playback and physical-TV acceptance remain unverified until tested; no claim that every title is available. The prior saved code28 installation/foreground receipt is historical, not a statement that the owner's emulator is running now.

Public household feed and approved public homepage are unchanged. Analytics remains outside the viewer in the existing Operon Analytics lane. No signing key replacement, uninstall, data clearing, provider-account sharing, new database, AI upscaling, paid-service activation or production rollout is included.
