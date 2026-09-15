<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->
# GharTV 0.6.0 RC5 — navigation & Now/Next review

Continue the existing GharTV project, PR1 and `codex/ghartv-remove-auto-preview`. No new checkout, runtime, provider or tenant. Public `0.5.4-rc8-pre-birthday-recovery`/code17 remains unchanged. This code21 candidate is review only.

## Last actual owner receipt

`GHARTV-CYAN-6-NET-R1-20260915T115027Z-40855`: local signing exit0, followed by "APK certificate unavailable"; no installation. Local browser health/source and Obsidian readback succeeded. Bridge handoff0, sync timed out. No private playback logs were supplied or remotely accessible. These facts must not be converted into a working APK or a diagnosed provider outage.

## This candidate

- Certificate identity uses Android's native ApkVerifier API and hashes verified X.509 bytes. No dependency on English apksigner stdout or a loosened signature check. Native signature validity, compiled-payload equality, expected public RC8 certificate and existing installed certificate all remain gates. Missing/bad signatures and mismatched keys stop; no key replacement, uninstallation or data clear.
- Guide CH+/CH- use the actual filtered list, not the all-channel catalogue. Player preserves the exact ordered scope across Activity recreation; explicit number tuning does not silently widen subsequent next/previous navigation. Retry/next-working uses the same scope. Web captures the visible category/search list when playback starts and steps only through that snapshot.
- Android tiles now have actual NOW and NEXT rows. A bounded visible-card EPG fetch updates those rows without reloading logos or the grid. Epoch seconds/milliseconds/microseconds are normalised; programme order and day rollover are handled. Empty/upstream-missing data is labelled, never made up.
- Player reads today's schedule without waiting for tomorrow. Metadata failure is not a playback failure. Stale responses after changing channels cannot overwrite the current channel. The browser opens playback independently of the EPG response rather than waiting for three schedule calls first.
- Nested Media3 error causes/status codes are examined so transient transport failures receive a bounded retry and terminal provider/DRM errors remain explicit. Duplicate error dialogs are suppressed. Browser retries a transient fatal HLS network failure once; access denial is not bypassed. No claim all channels are fixed or a measured speedup exists without device/account evidence.
- Refined midnight/cyan/lilac surfaces, restrained focus motion, explicit UP NEXT programme text separate from NEXT CHANNEL controls, readable card/overlay sizing, static backgrounds, and no CSS backdrop blur over video. Automatic guide video previews stay OFF as requested previously; the channel spotlight opens playback on OK.
- Local owner console automatically checks the saved collector config, reports present/missing/private-permission/origin states, and keeps the credential server-side. The expected file is `~/Library/Application Support/GharTV/telemetry/collector.env`, key `GHARTV_TELEMETRY_ADMIN_TOKEN`. Do not ask the owner to paste it into chat or invent/rotate a replacement. Public hosted owner shell still requires its own authentication.

## Review route

Use the full release kit and its RUN_GHARTV_REVIEW.command. It seeds only checksum-verified public artifacts and replaces the known canonical launcher. Exact source/manifest/companion/APK values come from the generated release record. The existing local signing.env supplies the same key without password prompts. The launcher does not change the development checkout. One existing AVD, one service at127.0.0.1:8790, owner page /owner.html. Bundled installation skips GitHub publication/Worker deployment; live provider playback and collector reads still need connectivity. A subsequent normal networked launcher run can publish the same locally signed review APK and check the existing backend. Never promote without owner acceptance.

The installer opens and verifies the app's foreground activity; that is not visual or physical Hisense playback validation. Compare two channels in Devotional, News or a language filter, use Next/Previous and retry, then inspect NOW/NEXT. Check owner credential presence and actual fresh events separately. No global TV log harvesting; private player/collector logs remain on the Mac and must not be pushed to GitHub.

## Continuity and limits

GharTV stays independent of VCNow; Analytics tenant `ghartv` stays on Operon PR61 and owner-control39. Update the existing managed Obsidian lane note with each actual receipt. Do not claim every replica synced when the bridge timed out. Cleanup only known redundant lane downloads and a verified superseded managed companion after replacement health; do not sweep source, keys, saved sessions or histories.

AI super-resolution, universal provider entitlement checks and native licensed movie aggregation are not implemented. Other requested RC4 features (local family-date editing, paired owner messages, hardware opt-in and IST) remain. No automatic/silent mandatory-update policy or code21 production deployment is performed by this release.
