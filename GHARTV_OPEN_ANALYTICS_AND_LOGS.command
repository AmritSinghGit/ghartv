#!/bin/bash
set -Eeuo pipefail
GHARTV="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
STATE="$HOME/Library/Application Support/Operon/operon-command-market-v0.2.0"
if [ -x "$GHARTV/GHARTV_LOGS_AND_HEALTH.command" ]; then
  bash "$GHARTV/GHARTV_LOGS_AND_HEALTH.command" || true
fi
URL=""
[ -f "$STATE/runtime/server.url" ] && URL="$(tr -d '\r\n' < "$STATE/runtime/server.url")"
if [ -n "$URL" ] && curl -fsS --max-time 4 "${URL%/}/api/v1/health" >/dev/null 2>&1; then
  open "${URL%/}/ghartv-analytics.html"
  echo "Opened live GharTV tenant analytics: ${URL%/}/ghartv-analytics.html"
else
  echo "GharTV logs/telemetry report was generated."
  echo "Operon Analytics tenant source is registered on the existing branch/PR, but no local analytics runtime is currently materialised."
  echo "No replacement worktree was created. Reopen the canonical operon-analytics candidate when that lane is materialised again."
  command -v open >/dev/null 2>&1 && open "https://github.com/AmritSinghGit/operon/pull/61" >/dev/null 2>&1 || true
fi
