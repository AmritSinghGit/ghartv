#!/bin/bash
set -Eeuo pipefail

PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
STATE="$HOME/Library/Application Support/GharTV/web-player"
HOST="127.0.0.1"
PORT="${GHARTV_WEB_PORT:-8790}"
URL="http://$HOST:$PORT"

fail(){ printf '\nGharTV web player could not start: %s\n' "$1" >&2; exit 1; }
[[ -d "$PROJECT/.git" ]] || fail "The canonical GharTV checkout was not found."
[[ -f "$PROJECT/web-player/server.mjs" ]] || fail "The web-player candidate is missing."
if [[ ! -f "$PROJECT/web-player/node_modules/hls.js/dist/hls.min.js" ]]; then
  printf 'Preparing the local browser player…\n'
  (cd "$PROJECT/web-player" && npm ci --ignore-scripts --no-audit --no-fund) || fail "The browser-player dependency could not be prepared."
fi
mkdir -p "$STATE"
chmod 700 "$STATE"

if curl -fsS --max-time 2 "$URL/api/health" > "$STATE/health.json" 2>/dev/null; then
  SERVICE="$(jq -r '.service // empty' "$STATE/health.json" 2>/dev/null || true)"
  [[ "$SERVICE" == "ghartv-web-player" ]] || fail "Port $PORT belongs to another application."
else
  if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "Port $PORT is already in use by another application."
  fi
  SHA="$(git -C "$PROJECT" rev-parse HEAD)"
  nohup env GHARTV_WEB_HOST="$HOST" GHARTV_WEB_PORT="$PORT" GHARTV_WEB_SHA="$SHA" \
    node "$PROJECT/web-player/server.mjs" > "$STATE/server.log" 2>&1 &
  PID=$!
  printf '%s\n' "$PID" > "$STATE/server.pid"
  chmod 600 "$STATE/server.pid" "$STATE/server.log"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if curl -fsS --max-time 2 "$URL/api/health" > "$STATE/health.json" 2>/dev/null; then break; fi
    sleep 0.25
  done
  curl -fsS --max-time 2 "$URL/api/health" >/dev/null || fail "The local server did not become ready. See $STATE/server.log"
fi

printf 'GharTV local web player: %s/\n' "$URL"
printf 'Private runtime state: %s\n' "$STATE"
if [[ -d "/Applications/Google Chrome.app" ]]; then
  open -a "Google Chrome" "$URL/"
else
  open "$URL/"
fi
