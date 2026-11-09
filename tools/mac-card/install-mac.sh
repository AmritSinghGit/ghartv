#!/usr/bin/env bash
# Install the GharTV web player on the owner's Mac: localhost only, starts at login.
# Usage: install-mac.sh <release-tarball> <expected-sha256> <12-char-release-id>
# Jio login is NOT kept in the Keychain unless you later run: enable-keychain (see MAC-CARD.md).
set -euo pipefail
tar_path="${1:?release tarball}"; want="${2:?expected sha256}"; rel="${3:?release id}"
[ "$(uname)" = Darwin ] || { echo "this runs on the Mac"; exit 2; }
got="$(shasum -a 256 "$tar_path" | cut -d' ' -f1)"
[ "$got" = "$want" ] || { echo "WRONG FILE: sha256 is $got, expected $want"; exit 2; }
node_bin="$(command -v node || true)"
[ -n "$node_bin" ] || { echo "Node is not installed: install Node 22 first (MAC-CARD.md step 1)"; exit 2; }
case "$("$node_bin" --version)" in v22.*|v23.*|v24.*) ;; *) echo "need Node 22 or newer, have $("$node_bin" --version)"; exit 2;; esac
if lsof -nP -iTCP:8790 -sTCP:LISTEN >/dev/null 2>&1 && ! launchctl list in.ghartv.web >/dev/null 2>&1; then echo "port 8790 is already used by something else"; exit 2; fi
base="$HOME/ghartv-web"; mkdir -p "$base/$rel" "$base/logs"
[ -f "$base/$rel/web-player/server.mjs" ] || tar xzf "$tar_path" -C "$base/$rel"
plist="$HOME/Library/LaunchAgents/in.ghartv.web.plist"; mkdir -p "$HOME/Library/LaunchAgents"
cat > "$plist" <<P
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>Label</key><string>in.ghartv.web</string>
<key>ProgramArguments</key><array><string>$node_bin</string><string>server.mjs</string></array>
<key>WorkingDirectory</key><string>$base/$rel/web-player</string>
<key>EnvironmentVariables</key><dict>
<key>GHARTV_WEB_HOST</key><string>127.0.0.1</string><key>GHARTV_WEB_PORT</key><string>8790</string>
<key>GHARTV_WEB_SHA</key><string>$rel</string><key>GHARTV_DISABLE_KEYCHAIN</key><string>1</string></dict>
<key>RunAtLoad</key><true/><key>KeepAlive</key><true/>
<key>StandardOutPath</key><string>$base/logs/player.log</string><key>StandardErrorPath</key><string>$base/logs/player.log</string>
</dict></plist>
P
chmod 600 "$plist"
launchctl bootout "gui/$(id -u)/in.ghartv.web" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$plist"
for i in 1 2 3 4 5 6 7 8 9 10; do
  h="$(curl -s -m3 http://127.0.0.1:8790/api/health || true)"; case "$h" in *'"ok":true'*) break;; esac; sleep 1
done
echo "$h" | grep -q '"ok":true' && echo "CHECK PASS: GharTV is running at http://127.0.0.1:8790 (this Mac only), release $rel" || { echo "CHECK FAIL: not answering; see $base/logs/player.log"; exit 1; }
echo "listening on: $(lsof -nP -iTCP:8790 -sTCP:LISTEN | awk 'NR>1{print $9}' | sort -u | tr '\n' ' ')  (must be 127.0.0.1:8790 only)"
