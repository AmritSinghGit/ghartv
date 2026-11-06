# Desk cards for the GharTV web gate (run on dev; the player on 8790 is not touched)
Prerequisite: infra's tunnel card (ghartv.vcnow.in -> http://127.0.0.1:18792) and Access app exist; CSO checked them.

## Card ghartv-gate-1 (creates the settings file once; safe to re-run: it stops if the file exists)
test ! -e ~/operon-desk/ghartv-gate.env || { echo "~/operon-desk/ghartv-gate.env already exists; edit it instead of creating it"; exit 1; }
install -m 600 -T ~/operon-worktrees/ghartv-nova/tools/web-gate/ghartv-gate.env.example ~/operon-desk/ghartv-gate.env
Tile text after the card, for the owner: On dev (Termius: ssh operon-dev-01): nano ~/operon-desk/ghartv-gate.env and replace the three REPLACE values, then Ctrl+O, Enter, Ctrl+X. Infra's Access card prints all three:
- ACCESS_TEAM = the team domain, exactly https://NAME.cloudflareaccess.com (no path, no trailing slash).
- ACCESS_AUD = the Audience (AUD) tag on the ghartv.vcnow.in Access application's Overview page.
- ALLOWED_EMAILS = the household emails, lower case, comma separated, the same list as the Access policy.

## Card ghartv-gate-2 (checks, starts it on operon's tmux, makes it a service; a second run stops at line 3)
test "$(stat -c %a ~/operon-desk/ghartv-gate.env)" = 600 && ! grep -q REPLACE ~/operon-desk/ghartv-gate.env
. ~/operon-desk/ghartv-gate.env; curl -fsS "$ACCESS_TEAM/cdn-cgi/access/certs" | grep -q '"keys"'
! ss -ltn | grep -q '127.0.0.1:18792 '
tmux -L operon new-session -d -s ghartv-gate ~/operon-worktrees/ghartv-nova/tools/web-gate/run-gate.sh
for i in $(seq 30); do curl -s -o /dev/null http://127.0.0.1:18792/ && break; sleep 1; done; test "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18792/)" = 403
~/operon-tools/bin/operon svc adopt ghartv-gate --kind service --lane ghartv-nova --url https://ghartv.vcnow.in/
curl -s http://127.0.0.1:8790/api/health | grep -q '"service":"ghartv-web-player"'
Note: the 403 proves the gate runs and the certs line proves the team domain; only the CSO's real sign-in test proves the AUD tag.
