#!/usr/bin/env bash
# GharTV web health for the board / a scheduler: prints one line, exit 0 = healthy, 1 = something is down.
# Checks: player health on loopback (and its commit = ~/ghartv-web/CURRENT); the gate refuses an unsigned
# request (403) when it is meant to run (settings file present); the public address asks for Access sign-in.
set -uo pipefail
want=$(cat "$HOME/ghartv-web/CURRENT" 2>/dev/null || echo "?")
bad=()
h=$(curl -s -m5 http://127.0.0.1:8790/api/health || true)
grep -q '"service":"ghartv-web-player"' <<<"$h" || bad+=("player down")
grep -q "\"commit\":\"$want\"" <<<"$h" || [ ${#bad[@]} -gt 0 ] || bad+=("player commit is not $want")
if [ -e /etc/ghartv/gate.env ] || [ -f "$HOME/operon-desk/ghartv-gate.env" ]; then
  g=$(curl -s -m5 -o /dev/null -w '%{http_code}' http://127.0.0.1:18792/ || true)
  [ "$g" = 403 ] || bad+=("gate answers $g, expected 403")
  p=$(curl -s -m10 -o /dev/null -w '%{http_code} %{redirect_url}' https://ghartv.vcnow.in/ || true)
  case "$p" in 302*cloudflareaccess.com*|403*) ;; *) bad+=("public address answers '$p', expected the Access sign-in");; esac
fi
if [ ${#bad[@]} -eq 0 ]; then echo "ghartv-web OK (commit $want)"; exit 0; fi
echo "ghartv-web PROBLEM: $(IFS='; '; echo "${bad[*]}")"; exit 1
