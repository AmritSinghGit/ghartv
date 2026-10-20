# GharTV Review 60 — Review38 Jio baseline with retained FlixMomo and IPTV

Owner testing established a strong regression boundary: the signed Review38 Jio guide, preview and Watch Live path works on the same emulator/network where Review59 Jio does not. Review60 therefore restores the Review38 direct Android/Media3 Jio transport without reverting the later navigation, diagnostics, FlixMomo Tor browser or separate public IPTV guide.

Jio API calls now use the ordinary shared OkHttp system-DNS client and Jio media/preview use Media3's direct `DefaultHttpDataSource`, matching the working Review38 design. Jio is not sent through the film Tor proxy, the IPTV route or an alternate Jio route. TLS, authentication, entitlement and Widevine checks remain unchanged. A Jio first picture is bounded to 18 seconds instead of waiting two minutes on a route chain that owner testing showed was not working.

FlixMomo keeps its existing app-local Tor behavior and the owner-accepted second-player path. Public IPTV remains separate and never receives Jio credentials. IPTV now opens any verified local playlist immediately, even when it is older than six hours, and only performs a network update after the owner chooses Refresh. Parsed playlist data is reused in memory while the file is unchanged, reducing repeated processing of roughly nine thousand public entries.

Controlled tests verify the Jio client/factories are direct OkHttp/system-DNS transports, the stale IPTV cache opens without a network call, and an explicit failed refresh falls back once to the verified cache. Real Jio guide/preview/Watch Live and individual public IPTV streams remain owner-network acceptance checks. No public feed, merge or deployment is authorized by this review candidate.
