# GharTV — Review48 input stability, 1 October 2026

REVIEW48=COMPILED_180_ANDROID_TESTS_PASSED_BUNDLE_VERIFIED_OWNER_REVIEW_PENDING
APPLICATION_SOURCE=d8d6267670f0fb027d01a857569f6d179a927072
CURRENT_PLAYBACK=EMBEDDED_PROVIDER_PLAYER_NATIVE_EXPERIMENT_HIDDEN
HOUSEHOLD38=UNCHANGED_NEXT_PUBLICATION_REQUIRES_NEXT_REVIEW_APPROVAL

## Same authority and actual acceptance

Continue AmritSinghGit/ghartv, codex/ghartv-remove-auto-preview, PR1, in.ghartv.nova, normal Nova GharTV_Nova_Manual_google_tv_API36/emulator-5580, web8790 and original signing identity. No duplicate lane/branch/worktree/emulator/controller/service. Guide refresh remains owner-confirmed; no live-TV core changes.

Latest managed receipt5687119492 is GHARTV-CYAN-47-20261001T075710Z-56385, source900a213bed2938f8be17dca19f455fc223d06259, signed03b333f8acf9e6d299f542ebbb05257fdffcaf46658ab95ccca0597f51d098bd. Normal Nova and Obsidian readback are confirmed; the user subsequently reports unstable Play, repeated Back presses, lost pointer/fullscreen, eventually working only after struggling. Installation readiness is not acceptance. User explicitly authorizes relevant app logs, requests pointer idle hide after4–5seconds and immediate return on movement, and says publication only AFTER the next review. Do not treat this as present rollout approval.

## Exact completed build, recovered without duplicate work

Application source d8d6267670f0fb027d01a857569f6d179a927072, code48/version0.6.0-rc13.2-input-stability-review.
Successful workflow36837805721, candidate job110289498770. Trigger146d0bdb2d6506670eff217366f1c6d8420637a5 was expanded to the application source above.
Artifact11150189285 / ghartv-code48-owner-review,15571433bytes, SHA2563cf20386d21bc5c1d212aa27e2902217fe09d7c4434b96442af54de12ee8ab33, expires2026-10-15T08:53:30Z.
Unsigned APK6629422bytes, SHA2567892bdb6b1f3e893c5b1cf343e385d14744c30226514c01284d34b66be54c9e4.
Source ZIP SHA256630c6cfc11156b43108ae744526d586c377385456b561d733eeb4f15fa6032b4.
Bundled GHARTV_OPEN_REVIEW.command10320234bytes, SHA256d99250d6f2e24985dca7b63b8649ef281de86697665121d8b3364377a021cd63.

Download the existing exact run/artifact, verify that opener hash, run --candidate48 (default also48). Do not run old Downloads entries. It embeds the compiled APK/source; no local Gradle. Fallback GharTV_Review48_Download.zip7805321bytes, SHA256164dbbd6229ff39799008ab71a5efab2fd03af727cde8c6d0cff8a23bc2d93fe, extracted entry GharTV_Review48/GHARTV_OPEN_REVIEW.command.

22 of23 opener function ASTs are byte-structure-identical to47. Only main adds a bounded app-only pre-update trace read and requires explicit successful Android outcome before REVIEW_READY. Original signing, certificate/payload verification, same AVD, nonstreaming in-place install, existing clock/network preflight, web/tab reuse and receipt functions remain. Certificate40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c. No signing keys were accessed by cloud verification.

## Delivered interaction fixes

Pointer inactivity timeout is5000ms. Fading only changes visibility, not enabled state. Arrow/physical movement reveals it, held direction does not fade, and short D-pad taps move immediately without waiting for an animation frame. Physical movement does not create an extra click. WebView focus loss cannot hide a pointer owned by pointer/fullscreen mode.

Back/Menu now use paired down/up events. Canceled, duplicate or stray releases cannot trigger another transition; held repeats are consumed. Menu can show/hide the fullscreen menu without exiting the film. Back from fullscreen restores Options; another deliberate Back from that toolbar returns to retained results rather than toggling into the page again.

Pending Play/fullscreen activations are coalesced instead of queued as extra toggles. Auto-tray initial focus is suppressed during explicit Play. Navigating to toolbar or fullscreen invalidates delayed callbacks, so an earlier failed click cannot subsequently reenter Mouse or bounce the screen back. Existing Sources, pointer click, provider fullscreen, loading/cache and target/TLS protections remain. This repairs tested interaction-state behavior, not a universal provider-media guarantee.

## Local action evidence under owner authorization

FilmReviewJournal records128 most-recent technical transitions in Activity memory with at most512 allowlisted local Android-log entries. Fields: version, sequence, elapsed time, action/state enums, interface layer and pointer enabled/visible booleans. It does NOT record title, query text, URL, credentials, screenshot, pointer coordinates or raw keystrokes. There is no new network sender or telemetry-consent change.

Options -> Recent interaction log displays the last actions and offers Save locally (app-local ghartv-review-interactions.json). The existing review opener can read only the running exact-AVD app PID and GharTVReview tag, filters schema/fields, then writes PRIOR_UI_INTERACTIONS.json in the existing private run folder before replacing the app. It is NOT automatically uploaded to GitHub. Log buffers are bounded; a process restart/long session may limit what is recoverable. Review47 never emitted this new local trace. An empty first-upgrade capture is expected and is not proof of no user actions.

This turn retrieved the47 installation receipt, not a recording of the reported Play/Back sequence. No private collector export, screen recording or raw system logs were fetched. Current48 logs start when48 runs. Do not claim retrospective reproduction of the exact owner inputs.

## Actual verification

CI log confirms180/180 Android tests in413.819seconds,160 retained plus20 new. All14 older Android test files are byte-identical. New tests exercise timed4.1second visibility/5.3second fading, wake/held motion, physical hover without click, immediate short taps, paired/canceled/repeated Back/Menu, stale callback guard, pending Play deduplication, tray suppression, bounded journal and a real owned-video Play -> fullscreen -> Menu -> Resume -> exit sequence without unintended pause/restart.

Recovery independently checked downloaded archive and every root checksum, embedded APK/source bytes and cache roundtrip, shell/Python syntax, binary manifest package/version, default48/explicit48 and rejected old/publication modes. Eight Python capture guards were rerun locally with simulated process responses. Android suite was not rerun in the recovery container. MainActivity/JioApiClient/PlayerActivity/ChannelRepository match47; source contains no font files; debug tests/fixtures and retired MovieHub absent from release. Native experiment remains hidden, not removed.

No owner48 signing/install, live-provider48 acceptance, physical-TV validation or measured overall speedup is claimed. The controlled tests use owned media, not the user's film.

## Continuity and release gate

Evidence-only development commitd6951fe85da48e461514627761b5e9835406f96a contains REVIEW48_EVIDENCE.json; it does not change the compiled application source. Main REVIEW_CANDIDATE.json is reconciled to48. Same-lane native launcher will write actual run/Obsidian receipts when executed; this cloud turn did not write the vault or verify Amrit Memory replication.

No owner cleanup, data wipe, new browser/emulator, Analytics restart/change, native-player expansion, Tor, certificate bypass, public APK release or feed update. Dad's published38 stays available. Only after the owner accepts the next review may its exact signed bytes be considered for the household feed. Prior47 handoff e32863197bf384bbf51ee710deb4f8b7cbc7f693 and manifest97e3bacf1c9d73a501df8d417bdb89f95671b201 remain in history.
