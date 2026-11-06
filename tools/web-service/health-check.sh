#!/usr/bin/env bash
# GharTV web health for the board / a scheduler: prints one line, exit 0 = healthy, 1 = something is down.
# Checks: player health on loopback (and its commit = ~/ghartv-web/CURRENT); the gate refuses an unsigned
# request (403) when it is meant to run (settings file present); the public address asks for Access sign-in.
set -uo pipefail
# The live release: /opt/ghartv-web/current/RELEASE.env once the systemd units run it, else the lane's tmux release.
want=$(sed -n 's/^GHARTV_WEB_SHA=//p' /opt/ghartv-web/current/RELEASE.env 2>/dev/null)
[ -n "$want" ] || want=$(cat "$HOME/ghartv-web/CURRENT" 2>/dev/null || echo "?")
bad=()
h=$(curl -s -m5 http://127.0.0.1:8790/api/health || true)
grep -q '"service":"ghartv-web-player"' <<<"$h" || bad+=("player down")
grep -q "\"commit\":\"$want\"" <<<"$h" || [ ${#bad[@]} -gt 0 ] || bad+=("player commit is not $want")
# Once the account ghartv exists (systemd install), its outbound firewall table must be loaded (CSO 317):
# the ghartv-nft unit stays 'active' even if the table was removed. If it is missing: stop both services.
if id ghartv >/dev/null 2>&1; then
  sudo -n nft list table inet ghartv_egress >/dev/null 2>&1 || bad+=("firewall table ghartv_egress MISSING: stop ghartv-web and ghartv-gate until it is back")
fi
# The gate is meant to run once its systemd unit exists (or, before that, the lane's settings file exists).
if systemctl cat ghartv-gate.service >/dev/null 2>&1 || [ -f "$HOME/operon-desk/ghartv-gate.env" ]; then
  if systemctl cat ghartv-gate.service >/dev/null 2>&1; then
    for u in ghartv-nft ghartv-web ghartv-gate; do systemctl is-active -q "$u" || bad+=("$u not active"); done
  fi
  g=$(curl -s -m5 -o /dev/null -w '%{http_code}' http://127.0.0.1:18792/ || true)
  [ "$g" = 403 ] || bad+=("gate answers $g, expected 403")
  p=$(curl -s -m10 -o /dev/null -w '%{http_code} %{redirect_url}' https://ghartv.vcnow.in/ || true)
  case "$p" in 302*cloudflareaccess.com*|403*) ;; *) bad+=("public address answers '$p', expected the Access sign-in");; esac
fi
if [ ${#bad[@]} -eq 0 ]; then echo "ghartv-web OK (commit $want)"; exit 0; fi
echo "ghartv-web PROBLEM: $(IFS='; '; echo "${bad[*]}")"; exit 1
