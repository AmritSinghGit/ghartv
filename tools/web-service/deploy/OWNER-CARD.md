# Owner card: GharTV web player + gate as the unprivileged account ghartv (CSO 316), on dev
Runs On dev (Termius: ssh operon-dev-01), as the owner with sudo, one line at a time (desk card lines). Infra owns the units after this.
Replaces desk cards s158/s159 (gate as amrit) and the lane's tmux service ghartv-web. cloudflared (its own account, token 0600) is infra's card ghartv-1 (s163), run after this one; the tunnel points at http://127.0.0.1:18792.
Pinned inputs: release tar ~/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz, sha256 f21edf83bb926b2be9b1af949f11e9b8101a9771f50f9933a00391540a76d2e1; gate + units from lane/ghartv-nova commit LANE (filled in by the CTO when the card is cut: the QA-passed commit).

## Lines
1. sudo useradd --system --home-dir /var/lib/ghartv-web --no-create-home --shell /usr/sbin/nologin ghartv
2. echo "f21edf83bb926b2be9b1af949f11e9b8101a9771f50f9933a00391540a76d2e1  /home/amrit/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz" | sha256sum -c
3. sudo install -d -o root -g root -m 755 /opt/ghartv-web/9e38de98e5cd /opt/ghartv-web/gate && sudo tar -xzf /home/amrit/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz -C /opt/ghartv-web/9e38de98e5cd --no-same-owner && sudo chmod -R go-w,a+rX /opt/ghartv-web
4. echo GHARTV_WEB_SHA=9e38de98e5cd | sudo tee /opt/ghartv-web/9e38de98e5cd/RELEASE.env >/dev/null && sudo ln -sfn 9e38de98e5cd /opt/ghartv-web/current
5. git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-gate/gate.mjs | sudo tee /opt/ghartv-web/gate/gate.mjs >/dev/null && sudo chmod 644 /opt/ghartv-web/gate/gate.mjs
6. sudo install -d -o root -g ghartv -m 750 /etc/ghartv && test ! -e /etc/ghartv/gate.env && git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-gate/ghartv-gate.env.example | sudo install -o root -g ghartv -m 640 /dev/stdin /etc/ghartv/gate.env
   THEN the owner: sudo nano /etc/ghartv/gate.env  -> the 3 REPLACE values (team domain, AUD tag, household emails; infra's Access card prints them).
7. for f in ghartv-web.service ghartv-gate.service ghartv-nft.service; do git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-service/deploy/$f | sudo install -o root -g root -m 644 /dev/stdin /etc/systemd/system/$f; done; git -C /home/amrit/operon-worktrees/ghartv-nova show LANE:tools/web-service/deploy/ghartv.nft | sudo install -o root -g root -m 644 /dev/stdin /etc/ghartv/ghartv.nft
8. sudo nft -c -f /etc/ghartv/ghartv.nft && sudo systemd-analyze verify /etc/systemd/system/ghartv-web.service /etc/systemd/system/ghartv-gate.service /etc/systemd/system/ghartv-nft.service
9. ! grep -q REPLACE /etc/ghartv/gate.env && . <(sudo cat /etc/ghartv/gate.env) && curl -fsS "$ACCESS_TEAM/cdn-cgi/access/certs" | grep -q '"keys"'
10. tmux -L operon kill-session -t ghartv-web 2>/dev/null; true   (stops the lane's tmux player as amrit; ~3 s gap)
11. sudo systemctl daemon-reload && sudo systemctl enable --now ghartv-nft.service ghartv-web.service ghartv-gate.service
12. for i in $(seq 30); do curl -s -o /dev/null http://127.0.0.1:8790/api/health && break; sleep 1; done; curl -s http://127.0.0.1:8790/api/health | grep -q '"commit":"9e38de98e5cd"' && test "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18792/)" = 403
13. test "$(ps -o user= -p $(systemctl show -p MainPID --value ghartv-web))" = ghartv && test "$(ps -o user= -p $(systemctl show -p MainPID --value ghartv-gate))" = ghartv
14. ! sudo -u ghartv cat /home/amrit/.bashrc 2>/dev/null && ! sudo -u ghartv ls /etc/vcnow 2>/dev/null; true   (account cannot read the owner's home or company config)
15. ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/127.0.0.1/15432' 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/100.85.0.40/22' 2>/dev/null && sudo -u ghartv timeout 10 bash -c 'exec 3<>/dev/tcp/jiotvapi.cdn.jio.com/443'
    (other loopback ports and the tailnet are refused; Jio on 443 is allowed)
16. ~/operon-tools/bin/operon svc stop ghartv-web 2>/dev/null; /home/amrit/operon-worktrees/ghartv-nova/tools/web-service/health-check.sh
Note for 14/15: these run the test AS the ghartv account; the services themselves are additionally confined by the unit (ProtectHome etc.).
