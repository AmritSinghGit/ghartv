#!/usr/bin/env bash
# Start the GharTV web gate (127.0.0.1:18792 -> player 127.0.0.1:8790) with its settings from a 0600 file.
# The file holds ACCESS_TEAM, ACCESS_AUD, ALLOWED_EMAILS and PUBLIC_ORIGIN (no passwords or tokens).
# Used by the ghartv-gate service (operon svc); the player on 8790 is not touched.
set -euo pipefail
env_file="${GHARTV_GATE_ENV:-$HOME/operon-desk/ghartv-gate.env}"
[ -f "$env_file" ] || { echo "missing $env_file (copy ghartv-gate.env.example there, chmod 600, fill it)"; exit 2; }
perm=$(stat -c %a "$env_file")
[ "$perm" = 600 ] || { echo "$env_file must be mode 600 (it is $perm): chmod 600 $env_file"; exit 2; }
grep -q 'REPLACE' "$env_file" && { echo "$env_file still has REPLACE placeholders"; exit 2; }
set -a; . "$env_file"; set +a
cd "$(dirname "$0")"
# No exec: `operon svc adopt` records this script (with its env file) as the command, not bare node.
nice -n 15 node gate.mjs
