# Owner card TEMPLATE: GharTV web player + gate as the account ghartv on dev (CSO 316/317)
The card to run is generated from this template with the full lane commit id and the file hashes:
  tools/web-service/deploy/make-card.sh <full commit id>  ->  prints the card.
Every line below is one desk line (its own shell); each line that acts carries its own check.
LANE = full commit id. W = /home/amrit/operon-worktrees/ghartv-nova. TAR = /home/amrit/ghartv-web/releases/ghartv-web-player-9e38de98e5cd.tar.gz
The tailnet proxy (tmux proxy-ghartv, dev's tailnet address port 8790) is NOT touched and never pointed at 18792 (CSO 317 point 5).
Household emails in the gate (line 6) and infra's Access policy must be the same list (today: the owner only; a new person goes in both).
