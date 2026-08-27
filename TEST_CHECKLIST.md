# GharTV 0.5.1 TV-test acceptance checklist

## Source and release

- [ ] `bash VALIDATE_SOURCE.command` passes.
- [ ] Release APK is signed with the persistent GharTV key.
- [ ] APK SHA-256 matches the GitHub Release checksum.
- [ ] Version is `0.5.1-tv-test` / code `8`.
- [ ] No YouTube/web/provider/M3U implementation remains.
- [ ] Operon classification is `project`, not `tenant`.

## Google TV emulator

- [ ] Existing `GharTV_Nova_Manual_google_tv_API36` AVD is reused.
- [ ] Release APK installs and opens `.LoginActivity`.
- [ ] Runtime verifier accepts Login, Guide and Player screens.
- [ ] D-pad focus is visible and no control is unreachable.
- [ ] Mobile and OTP fields work with the Google TV phone remote or keyboard.
- [ ] Catalogue cache/refresh, filters, search and favourites render correctly.

## Hisense E6N

- [ ] `amritsinghgit.github.io/ghartv` opens on the TV.
- [ ] Latest APK downloads and installs.
- [ ] Physical remote and Google TV phone remote work.
- [ ] Jio OTP login succeeds.
- [ ] Live guide loads with logos and channel numbers.
- [ ] Number tuning, Channel Up/Down, GUIDE and INFO work.
- [ ] At least one HLS channel plays.
- [ ] At least one entitled DASH/Widevine channel plays.
- [ ] Audio/video remains stable for 30 minutes.
- [ ] TV restart preserves the encrypted session and cached guide.
- [ ] Update check recognises the installed version.
