# Owner card: GharTV web player + gate as the unprivileged account ghartv (CSO 316), on dev
Runs On dev (Termius: ssh operon-dev-01), as the owner with sudo, one line at a time (desk card lines). Infra owns the units after this.
Replaces desk cards s158/s159 (gate as amrit) and the lane's tmux service ghartv-web. cloudflared (its own account, token 0600) is infra's card ghartv-1 (s163), run after this one; the tunnel points at http://127.0.0.1:18792.
Pinned inputs: release tar ~/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz, sha256 f21edf83bb926b2be9b1af949f11e9b8101a9771f50f9933a00391540a76d2e1; gate + units from lane/ghartv-nova commit LANE (filled in by the CTO when the card is cut: the QA-passed commit).

## Lines
1. sudo useradd --system --user-group --home-dir /var/lib/ghartv --no-create-home --shell /usr/sbin/nologin ghartv && ! id -nG ghartv | grep -qw sudo
2. echo "f21edf83bb926b2be9b1af949f11e9b8101a9771f50f9933a00391540a76d2e1  /home/amrit/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz" | sha256sum -c
3. sudo install -d -o root -g root -m 755 /opt/ghartv-web/9e38de98e5cd /opt/ghartv-web/gate && sudo tar -xzf /home/amrit/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz -C /opt/ghartv-web/9e38de98e5cd --no-same-owner && sudo chmod -R go-w,a+rX /opt/ghartv-web
4. echo GHARTV_WEB_SHA=9e38de98e5cd | sudo tee /opt/ghartv-web/9e38de98e5cd/RELEASE.env >/dev/null && sudo ln -sfn 9e38de98e5cd /opt/ghartv-web/current
5. git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-gate/gate.mjs | sudo tee /opt/ghartv-web/gate/gate.mjs >/dev/null && sudo chmod 644 /opt/ghartv-web/gate/gate.mjs
6. sudo install -d -o root -g ghartv -m 750 /etc/ghartv && test ! -e /etc/ghartv/gate.env && bash <(git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-gate/write-gate-env.sh)
   (asks, input hidden, for the 3 values from infra's Access card s164: team domain, AUD tag, household emails; checks them, proves the team domain answers with Access keys, writes /etc/ghartv/gate.env root:ghartv 0640; the player cannot see /etc/ghartv)
7. for f in ghartv-web.service ghartv-gate.service ghartv-nft.service; do git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-service/deploy/$f | sudo install -o root -g root -m 644 /dev/stdin /etc/systemd/system/$f; done; git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-service/deploy/ghartv.nft | sudo install -o root -g root -m 644 /dev/stdin /etc/ghartv/ghartv.nft
8. sudo nft -c -f /etc/ghartv/ghartv.nft && sudo systemd-analyze verify /etc/systemd/system/ghartv-web.service /etc/systemd/system/ghartv-gate.service /etc/systemd/system/ghartv-nft.service
9. test "$(sudo stat -c '%U:%G %a' /etc/ghartv/gate.env)" = "root:ghartv 640" && test "$(sudo stat -c '%U %a' /opt/ghartv-web/current/web-player/server.mjs)" = "root 644"
10. tmux -L operon kill-session -t ghartv-web 2>/dev/null; true   (stops the lane's tmux player as amrit; ~3 s gap)
11. sudo systemctl daemon-reload && sudo systemctl enable --now ghartv-nft.service ghartv-web.service ghartv-gate.service
12. for i in $(seq 30); do curl -s -o /dev/null http://127.0.0.1:8790/api/health && break; sleep 1; done; curl -s http://127.0.0.1:8790/api/health | grep -q '"commit":"9e38de98e5cd"' && test "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18792/)" = 403
13. test "$(ps -o user= -p $(systemctl show -p MainPID --value ghartv-web))" = ghartv && test "$(ps -o user= -p $(systemctl show -p MainPID --value ghartv-gate))" = ghartv
14. ! sudo -u ghartv cat /home/amrit/.bashrc 2>/dev/null && ! sudo -u ghartv ls /etc/vcnow 2>/dev/null; true   (account cannot read the owner's home or company config)
15. ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/127.0.0.1/15432' 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/100.85.0.40/22' 2>/dev/null && sudo -u ghartv timeout 10 bash -c 'exec 3<>/dev/tcp/jiotvapi.cdn.jio.com/443'
    (other loopback ports and the tailnet are refused; Jio on 443 is allowed)
16. test "$(ss -ltn | grep -c '127.0.0.1:8790 ')" = 1 && test "$(ss -ltn | grep -c '127.0.0.1:18792 ')" = 1   (nothing listening twice)
17. sudo test -d /var/lib/ghartv/web -a -d /var/lib/ghartv/gate && sudo journalctl -u ghartv-web -u ghartv-gate --since -5min | tail -5
18. /home/amrit/operon-worktrees/ghartv-nova/tools/web-service/health-check.sh
Logs: both units log to journald (player output is already redacted by the player; CSO 313/314 asked journald 30 days), read with: sudo journalctl -u ghartv-web -u ghartv-gate. State folders /var/lib/ghartv/web and /var/lib/ghartv/gate (0700, owned by ghartv) hold no logs, only what the player writes there (today nothing; slice 3's token file later).
Product Desk start/stop: operon svc adopt records tmux sessions only, it cannot adopt a systemd unit; until operon svc supports units, start/stop is: sudo systemctl restart|stop|start ghartv-web ghartv-gate (CTO to decide: a small svc 'unit' kind, or desk cards). Remove the old record: the lane's tmux service ghartv-web (svc list) is retired by line 10.
Note for 14/15: these run the test AS the ghartv account; the services themselves are additionally confined by the unit (ProtectHome etc.).
