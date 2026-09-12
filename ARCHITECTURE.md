# GharTV Jio Live architecture

1. `LoginActivity` requests and verifies a Jio OTP.
2. `JioSession` stores the resulting session encrypted through Android Keystore.
3. `JioApiClient` loads the Jio mobile channel catalogue, display dictionary, EPG and account-authorised playback response; it distinguishes auth, explicit subscription and persistent access denial.
4. `ChannelRepository` is the one cached Jio catalogue/access authority. Temporary failure evidence expires automatically.
5. `ChannelIndex` provides constant-time number tuning, cached living-room views and ranked global search.
6. `WatchHistoryStore` records successful playback locally for For you, Continue and Recent. Telemetry never reads this store.
7. `MainActivity` renders a D-pad-first responsive guide from local cache immediately, remembers focus by view and passes the exact visible channel scope to playback.
8. `PlayerActivity` configures Media3 for HLS or DASH/Widevine, shows an auto-hiding Now/Next panel, retries transient failures and supplies next-working-channel recovery.
9. `ChannelRefreshWorker` refreshes the catalogue periodically while a Jio session exists.
10. `Telemetry` remains explicit opt-in and privacy-filtered; the owner can inspect it through `GHARTV_LOGS_AND_HEALTH.command`.
11. `UpdateManager` reads the stable release manifest, downloads the next APK, verifies SHA-256 and hands installation to Android for explicit approval.

Removed from the active application: starter web channels, YouTube destinations, embedded-browser playback, provider launchers, M3U import, a repackaged mobile JioTV APK and gesture automation.
