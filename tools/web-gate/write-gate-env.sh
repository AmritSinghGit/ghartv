#!/usr/bin/env bash
# Asks for the three Access values (input hidden) and writes /etc/ghartv/gate.env as root:ghartv 0640.
# Refuses values that do not look right; never prints them. Run by the owner from the desk card (needs sudo).
set -euo pipefail
read -rsp 'Access team domain (https://NAME.cloudflareaccess.com): ' team; echo
read -rsp 'Access application AUD tag: ' aud; echo
read -rsp 'Household emails, comma separated: ' emails; echo
team="${team%/}"; emails="$(tr 'A-Z' 'a-z' <<<"${emails// /}")"
[[ "$team" =~ ^https://[a-z0-9-]+\.cloudflareaccess\.com$ ]] || { echo "team domain must be https://NAME.cloudflareaccess.com"; exit 2; }
[[ "$aud" =~ ^[A-Za-z0-9]{32,128}$ ]] || { echo "AUD tag should be the long letters-and-digits value from the application's Overview page"; exit 2; }
[[ "$emails" =~ ^[^,@[:space:]]+@[^,@[:space:]]+\.[a-z]+(,[^,@[:space:]]+@[^,@[:space:]]+\.[a-z]+)*$ ]] || { echo "emails: name@domain, comma separated"; exit 2; }
curl -fsS -m 10 "$team/cdn-cgi/access/certs" | grep -q '"keys"' || { echo "that team domain does not answer with Access keys"; exit 2; }
printf 'ACCESS_TEAM=%s\nACCESS_AUD=%s\nALLOWED_EMAILS=%s\nPUBLIC_ORIGIN=https://ghartv.vcnow.in\n' "$team" "$aud" "$emails" \
  | sudo install -o root -g ghartv -m 640 /dev/stdin /etc/ghartv/gate.env
echo "written: /etc/ghartv/gate.env (root:ghartv 0640), $(tr ',' '\n' <<<"$emails" | wc -l) email(s)"
