#!/usr/bin/env bash
# Prints the owner card for one exact lane commit: file hashes first, then the desk lines (CSO 317).
set -euo pipefail
L="${1:?full lane commit id}"; DATA_PEER="${DATA_PEER:?set DATA_PEER to the data server tailnet name (kept out of git)}"; [[ "$L" =~ ^[0-9a-f]{40}$ ]] || { echo "need the full 40-character commit id"; exit 2; }
W=/home/amrit/operon-worktrees/ghartv-nova
TAR=/home/amrit/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz
TARSUM=f21edf83bb926b2be9b1af949f11e9b8101a9771f50f9933a00391540a76d2e1
files=(tools/web-gate/gate.mjs tools/web-gate/write-gate-env.sh tools/web-service/deploy/ghartv-web.service
       tools/web-service/deploy/ghartv-gate.service tools/web-service/deploy/ghartv-nft.service tools/web-service/deploy/ghartv.nft
       tools/web-service/health-check.sh)
h() { git -C "$W" show "$L:$1" | sha256sum | cut -c1-64; }
echo "# GharTV account ghartv: owner card for lane commit $L"
echo "# Run On dev (Termius: ssh operon-dev-01) with the desk, one line at a time. Pinned inputs (sha256):"
echo "#   $TARSUM  $(basename "$TAR")"
for f in "${files[@]}"; do echo "#   $(h "$f")  $f"; done
G="git -C $W show $L"
chk() { echo "test \"\$($G:$1 | sha256sum | cut -c1-64)\" = $(h "$1")"; }
cat <<CARD
sudo useradd --system --user-group --home-dir /var/lib/ghartv --no-create-home --shell /usr/sbin/nologin ghartv && test "\$(id -nG ghartv)" = ghartv
echo "$TARSUM  $TAR" | sha256sum -c && sudo install -d -o root -g root -m 755 /opt/ghartv-web/9e38de98e5cd /opt/ghartv-web/gate && sudo tar -xzf $TAR -C /opt/ghartv-web/9e38de98e5cd --no-same-owner && sudo chmod -R go-w,a+rX /opt/ghartv-web && echo GHARTV_WEB_SHA=9e38de98e5cd | sudo tee /opt/ghartv-web/9e38de98e5cd/RELEASE.env >/dev/null && sudo ln -sfn 9e38de98e5cd /opt/ghartv-web/current && test "\$(sudo stat -c '%U %a' /opt/ghartv-web/current/web-player/server.mjs)" = "root 644"
$(chk tools/web-gate/gate.mjs) && $G:tools/web-gate/gate.mjs | sudo install -o root -g root -m 644 /dev/stdin /opt/ghartv-web/gate/gate.mjs
sudo install -d -o root -g ghartv -m 750 /etc/ghartv && $(chk tools/web-service/deploy/ghartv.nft) && D=\$(tailscale ip -4 $DATA_PEER) && [[ "\$D" =~ ^100\.[0-9]+\.[0-9]+\.[0-9]+\$ ]] && $G:tools/web-service/deploy/ghartv.nft | sed "s/@DATA01_TAILNET_IP@/\$D/" | sudo install -o root -g root -m 644 /dev/stdin /etc/ghartv/ghartv.nft && ! grep -q @ /etc/ghartv/ghartv.nft && sudo nft -c -f /etc/ghartv/ghartv.nft
$(chk tools/web-service/deploy/ghartv-web.service) && $(chk tools/web-service/deploy/ghartv-gate.service) && $(chk tools/web-service/deploy/ghartv-nft.service) && for f in ghartv-web.service ghartv-gate.service ghartv-nft.service; do $G:tools/web-service/deploy/\$f | sudo install -o root -g root -m 644 /dev/stdin /etc/systemd/system/\$f; done && sudo systemd-analyze verify /etc/systemd/system/ghartv-web.service /etc/systemd/system/ghartv-gate.service /etc/systemd/system/ghartv-nft.service
test ! -e /etc/ghartv/gate.env && $(chk tools/web-gate/write-gate-env.sh) && bash <($G:tools/web-gate/write-gate-env.sh) && test "\$(sudo stat -c '%U:%G %a' /etc/ghartv/gate.env)" = "root:ghartv 640"
sudo install -d -m 755 /etc/systemd/journald.conf.d && printf '[Journal]\nMaxRetentionSec=30day\n' | sudo install -m 644 /dev/stdin /etc/systemd/journald.conf.d/30-retention.conf && sudo systemctl restart systemd-journald
~/operon-tools/bin/operon svc stop ghartv-web; for i in \$(seq 20); do ss -ltn | grep -q '127.0.0.1:8790 ' || break; sleep 1; done; ! ss -ltn | grep -q '127.0.0.1:8790 ' && sudo systemctl daemon-reload && sudo systemctl enable --now ghartv-nft.service ghartv-web.service ghartv-gate.service && sudo -n nft list table inet ghartv_egress >/dev/null && for i in \$(seq 30); do curl -s -o /dev/null http://127.0.0.1:8790/api/health && break; sleep 1; done && curl -s http://127.0.0.1:8790/api/health | grep -q '"commit":"9e38de98e5cd"' && test "\$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18792/)" = 403 && test "\$(ps -o user= -p \$(systemctl show -p MainPID --value ghartv-web))" = ghartv && test "\$(ps -o user= -p \$(systemctl show -p MainPID --value ghartv-gate))" = ghartv && test "\$(ss -ltn | grep -c '127.0.0.1:8790 ')" = 1 && test "\$(ss -ltn | grep -c '127.0.0.1:18792 ')" = 1 && ss -ltn | grep -q "\$(tailscale ip -4):8790 " && S=\$(sudo ss -ltnuHe) && test -n "\$S" && ! grep "uid:\$(id -u ghartv)" <<<"\$S" | grep -vqE ' 127\.0\.0\.1:(8790|18792) '
! sudo -u ghartv cat /home/amrit/.bashrc 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/127.0.0.1/15432' 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c "exec 3<>/dev/tcp/\$(tailscale ip -4)/22" 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/10.0.0.1/443' 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/127.0.0.1/22' 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/8.8.8.8/53' 2>/dev/null && ! sudo -u ghartv timeout 5 bash -c 'exec 3<>/dev/tcp/1.1.1.1/80' 2>/dev/null && sudo -u ghartv timeout 10 bash -c 'exec 3<>/dev/tcp/jiotvapi.cdn.jio.com/443'
$(chk tools/web-service/health-check.sh) && bash <($G:tools/web-service/health-check.sh)
CARD
echo "# The tailscale socket is hidden from the services by their units (InaccessiblePaths); sudo -u tests run outside that sandbox, so they do not test it."
echo "# Line 7 stops the lane's own tmux player (as amrit) and starts the units; the tailnet proxy stays (last check proves it still listens)."
echo "# Start/stop afterwards: sudo systemctl start|stop ghartv-web ghartv-gate (operon svc cannot adopt units yet). If health-check says the firewall table is missing: sudo systemctl stop ghartv-gate ghartv-web until it is back."
