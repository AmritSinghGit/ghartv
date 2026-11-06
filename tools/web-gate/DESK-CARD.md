# Desk cards for the GharTV web gate (runs on dev; the player on 8790 is not touched)
Prerequisite: infra's tunnel card (ghartv.vcnow.in -> http://127.0.0.1:18792) and Access app exist; CSO checked them.

## Card ghartv-gate-1 (creates the settings file; then the owner fills it)
install -m 600 -T ~/operon-worktrees/ghartv-nova/tools/web-gate/ghartv-gate.env.example ~/operon-desk/ghartv-gate.env
# then, On dev (Termius: ssh operon-dev-01): nano ~/operon-desk/ghartv-gate.env   -> replace the 3 REPLACE values from the Access app (team domain, AUD tag, household emails); Ctrl+O, Enter, Ctrl+X

## Card ghartv-gate-2 (starts it, makes it a service, checks it)
test "$(stat -c %a ~/operon-desk/ghartv-gate.env)" = 600 && ! grep -q REPLACE ~/operon-desk/ghartv-gate.env
! ss -ltn | grep -q '127.0.0.1:18792 '
tmux new-session -d -s ghartv-gate ~/operon-worktrees/ghartv-nova/tools/web-gate/run-gate.sh
for i in $(seq 30); do curl -s -o /dev/null http://127.0.0.1:18792/ && break; sleep 1; done; test "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18792/)" = 403
~/operon-tools/bin/operon svc adopt ghartv-gate --kind service --lane ghartv-nova --url https://ghartv.vcnow.in/
curl -s http://127.0.0.1:8790/api/health | grep -q '"service":"ghartv-web-player"'
