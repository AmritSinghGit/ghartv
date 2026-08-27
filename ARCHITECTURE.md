# GharTV Jio Live architecture

1. `LoginActivity` requests and verifies a Jio OTP.
2. `JioSession` stores the resulting session encrypted through Android Keystore.
3. `JioApiClient` loads the Jio mobile channel catalogue, display dictionary, EPG and account-authorised playback response.
4. `ChannelRepository` keeps only Jio channels, favourites and last-viewed state.
5. `MainActivity` renders a D-pad-first television guide from the local cache immediately, then refreshes in the background.
6. `PlayerActivity` resolves the selected channel and configures Media3 for HLS or DASH/Widevine.
7. `ChannelRefreshWorker` refreshes the catalogue periodically while a Jio session exists.
8. `UpdateManager` reads the repository release manifest, downloads the next APK, verifies SHA-256 and hands installation to Android for explicit approval.

Removed from the active application: starter web channels, YouTube destinations, embedded-browser playback, provider launchers, M3U import and mobile-app gesture automation.
