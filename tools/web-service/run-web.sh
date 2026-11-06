#!/usr/bin/env bash
# Run the GharTV web player (loopback 127.0.0.1:8790) from a pinned release folder, logging to a
# size-rotated file under ~/operon-logs/ghartv-web. Used by the ghartv-web service (operon svc).
# Release folder: ~/ghartv-web/<12-char sha of PR #1's branch>/web-player (built by git archive + npm ci).
set -euo pipefail
sha="${GHARTV_WEB_RELEASE:-$(cat "$HOME/ghartv-web/CURRENT")}"
dir="$HOME/ghartv-web/$sha/web-player"
[ -f "$dir/server.mjs" ] || { echo "no release at $dir"; exit 2; }
cd "$dir"
export GHARTV_DISABLE_KEYCHAIN=1 GHARTV_WEB_SHA="$sha" GHARTV_WEB_HOST=127.0.0.1 GHARTV_WEB_PORT=8790
# No exec: `operon svc adopt` records this script as the command.
nice -n 10 node server.mjs 2>&1 | python3 "$(dirname "$(readlink -f "$0")")/rotlog.py" "$HOME/operon-logs/ghartv-web/player.log"
