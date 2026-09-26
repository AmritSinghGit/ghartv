# Review39 — native title pages and stable provider actions

Same GharTV lane/branch/PR and original signing identity. Household code38 remains published unchanged. Code39 is owner-review only; no update-feed or public marketing approval.

Implemented: prominent FlixMomo attribution even when the large toolbar is hidden; native Discover/search image cards; native current-title page with observed description, metadata and Watch/Watchlist/Original page; reversible focus shielding; one-press queued page actions with post-scroll visual-state and node hit-testing; native player choices reuse the existing tested badge-aware normalizer with stable labels after reordering and explicit partial-count reporting. The provider's video player remains in the WebView. No media URL extraction, entitlement/DRM challenge bypass or new Tor routing.

Provider identity/origins are versioned in FilmProviderPolicy. Lookalikes and unapproved top-level migrations remain rejected. Automatic signed remote policy distribution is not implemented; it requires a separately approved configuration delivery.

Not included in this APK: the secure QR phone trackpad. It requires an authenticated encrypted pairing transport, explicit TV approval and phone-browser/network testing; Review39 does not open an unencrypted control port or add a nonfunctional Pair button. Actual speech recognition uses the installed service, not a new Hindi/Punjabi model. Live-provider all-player playback, physical-TV experience and owner signing/install remain distinct from controlled Android tests.

The existing download/sign/open entry is reused, with compiled code39 APK and exact source embedded. No local Gradle build, separate signer, new emulator, scrcpy, Mac cleanup or modification of Dad's signed38. Fixtures in tests use synthetic documents and owned images, not a catalogue shipped with the product.
