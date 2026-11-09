# CARD: GharTV web player on the Mac (localhost only, no server address). DRAFT for QA, then CSO. Nothing runs from this file.
(Committed copy: <DEV_HOST> is filled in from the local card; the generated card with the real name is in the owner's status folder, not in git.)
Owner words: "if it is possible that the things can run on Mac or that personal device ... so that it doesn't show up as a IP of a data server" (9 Nov).
Only this Mac can open it (http://127.0.0.1:8790). The family keeps the TV app. Jio login is NOT stored on the Mac unless step 5 is done with the number holder's consent.
Pinned: release 9e38de98e5cd (tip of PR #1's branch), tarball sha256 f21edf83bb926b2be9b1af949f11e9b8101a9771f50f9933a00391540a76d2e1.
Script sha256 (check before step 3): a9877f4eacef564df68eb40efd5f77156e60405b55883d80afc538c08dba41ee

1. On the Mac (Terminal): `node --version`  Needs v22 or newer. If it says "command not found" or an older version: `brew install node` then `node --version` again.
2. On the Mac (Terminal): `mkdir -p ~/ghartv-web/dl && cd ~/ghartv-web/dl && scp <DEV_HOST>:ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz . && scp <DEV_HOST>:ghartv-web/mac/install-mac.sh .`
3. On the Mac (Terminal): `shasum -a 256 ~/ghartv-web/dl/install-mac.sh`  Must start with a9877f4eacef. If not, stop.
4. On the Mac (Terminal): `bash ~/ghartv-web/dl/install-mac.sh ~/ghartv-web/dl/ghartv-web-player-9e38de98e5cd.tar.gz f21edf83bb926b2be9b1af949f11e9b8101a9771f50f9933a00391540a76d2e1 9e38de98e5cd`
   It must end with "CHECK PASS" and "listening on: 127.0.0.1:8790" (only that). It refuses a wrong file, a missing/old Node, or a taken port.
   Then open http://127.0.0.1:8790 in the Mac's browser, sign in with the Jio OTP, check the guide loads (it should: this is a home connection).
5. ONLY after the Jio number holder says yes (optional, keeps the Jio login in the Mac Keychain so OTP is not needed each start):
   On the Mac (Terminal): `/usr/libexec/PlistBuddy -c "Delete :EnvironmentVariables:GHARTV_DISABLE_KEYCHAIN" ~/Library/LaunchAgents/in.ghartv.web.plist`
   On the Mac (Terminal): `launchctl bootout gui/$(id -u)/in.ghartv.web && launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/in.ghartv.web.plist`
   Undo: `security delete-generic-password -a local-owner -s in.ghartv.nova.web-player.jio-session` and run the install line in step 4 again.
STOP: On the Mac (Terminal): `launchctl bootout gui/$(id -u)/in.ghartv.web`   START: `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/in.ghartv.web.plist`
REMOVE: stop as above, then `rm ~/Library/LaunchAgents/in.ghartv.web.plist` and `rm -r ~/ghartv-web`.
Log: ~/ghartv-web/logs/player.log (not rotated; small).

For QA/CSO: no sudo, no root, no new credential, no open port (loopback only); only outbound calls are the player's own (Jio, and the stream hosts). The player's request origin check (loopback + same-site only) is in server.mjs lines 187-198. Open question for CSO: the film/FlixMomo/owner pages the gate blocked on dev are reachable on the Mac's localhost by the owner; fine for one person, but confirm. Not tested on a Mac (I have none): script syntax checked, plist parsed. Jio login storage default is OFF.
