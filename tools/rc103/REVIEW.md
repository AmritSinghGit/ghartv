# GharTV RC10.3 — native FlixMomo navigation and visible TV review

Same independent GharTV lane, implementation branch and PR1. New web/delivery source is separate from the unchanged Android code28/source9457654eafe86a08c402c6829c6cae3312c3e196. The approved public homepage and removed analytics routes are preserved. No production update feed is changed.

## What the owner actually observed

September21 screenshot shows FlixMomo initially loading and then navigating to /dummy with a 404. It also shows the signed-in web catalogue and Punjabi filter. These are not proof of video playback. The latest automatic owner receipt, GHARTV-CYAN-16-20260921T063147Z-88259, reports exact code28 installed and Android foreground at REVIEW_OPEN. It did not measure Mac window visibility. Do not label this as the older memory-held run15 or claim the user saw the emulator window.

## Root cause and changes

Two ordinary cloud browser-automation observations reproduced /dummy. FlixMomo's public frontend contains a browser-detection redirect to that route and its search control navigates to /search?q=. GharTV's previous automated-search/isolated-browser approach was incompatible. No browser-detection flag, CAPTCHA, navigation guard or provider protection is disabled by this repair.

Search is now a native HTML GET form to the provider's verified /search?q= route. Browse is a normal provider-root link. They use the viewer's current browser, not an injected or automated Brave window. Results are displayed on FlixMomo, not scraped into invented GharTV cards. This is provider-site search, not an internal catalogue integration. Close the old automated /dummy window and use the new native buttons. Ordinary provider access may still have its own verification, region, account or playback requirements.

The optional Tor choice requests the installed Tor Browser via macOS LaunchServices. It does not run Playwright or falsely label the network verified. An absent Tor Browser produces an actionable error, never a direct fallback. When Tor is selected, direct form/link destinations are removed as well as intercepting the click. Connect inside Tor Browser when required. No other browser is closed and no normal profile is extracted. Browser-driver files are no longer required by or shipped in the viewer companion.

The TV review now calls the existing transport's Mac-window helper after exact installed-APK and Android-foreground checks. The helper selects only the owner's exact named Nova QEMU process, revalidates it, unhides and requests activation. It restores minimized target windows only when Accessibility permission was already granted; it does not prompt or change permissions. Bounded window metadata is inspected without screenshots or window titles. Headless/Android-Studio-embedded processes are reported distinctly, never killed or duplicated. Android foreground and Mac window visibility are separate receipt fields. An unconfirmed Mac window produces ANDROID_READY_WINDOW_UNCONFIRMED rather than a misleading visible-review success.

## Review

Run GHARTV_REVIEW_RC10_3.command to download and verify the complete bundle. It updates the same managed web runtime, revalidates/reuses the existing signed code28, and opens the same Nova candidate. There is no unnecessary Android rebuild or replacement key. Resource admission still prevents a new VM under memory pressure, but allows reuse of the exact already-booted Nova. The full local receipt and Obsidian note are saved; the existing GitHub mirror now includes window observations. No routine handoff paste is required.

## Evidence limits

Validation includes native form destinations and encoding, route isolation, no-direct-fallback checks, the existing Safari HLS test-media regression, actual macOS prior-to-new and repeat startup, and a native AppKit test window for focus/onscreen metadata. Native test-window proof is not physical-TV acceptance. Provider-site search results after a human opens a normal browser, licensed-provider video playback, actual Tor egress, the new owner-Mac run and physical-TV playback remain separately unverified. Public provider automation is deliberately not evaded or advertised as working.
