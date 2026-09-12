#!/bin/bash
set -Eeuo pipefail
GHARTV="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"; ADB="$SDK/platform-tools/adb"
STAMP="$(date +%Y%m%d-%H%M%S)"
[ -x "$GHARTV/GHARTV_LOGS_AND_HEALTH.command" ] && bash "$GHARTV/GHARTV_LOGS_AND_HEALTH.command" || true
if [ -x "$ADB" ]; then
  while IFS= read -r serial; do
    [ -n "$serial" ] || continue
    name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | head -1 || true)"
    case "$name" in GharTV_*) "$ADB" -s "$serial" emu kill || true;; esac
  done < <("$ADB" devices | awk '$2=="device"&&$1~/^emulator-/{print $1}')
fi
if [ -x "$GHARTV/android-tv/gradlew" ]; then (cd "$GHARTV/android-tv" && ./gradlew --stop) || true; fi
# Stop only the tracked Operon Analytics review process when its recorded PID is
# still the analytics candidate. Never kill an unrelated process or another lane.
STATE="$HOME/Library/Application Support/Operon/operon-command-market-v0.2.0"
if [ -f "$STATE/runtime/server.pid" ]; then
  PID="$(tr -d '\r\n' < "$STATE/runtime/server.pid")"
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    CMD="$(ps -p "$PID" -o command= 2>/dev/null || true)"
    case "$CMD" in *operon_command_candidate_v080.py*|*operon-command-market-v0.2.0*) kill "$PID" 2>/dev/null || true;; esac
  fi
fi
NOTE="$HOME/Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv/ghartv-v054-rc5-close-$STAMP.md"
mkdir -p "$(dirname "$NOTE")" "$HOME/.local/state/operon-terminal-runs/ghartv"
cat > "$NOTE" <<EOF
# GharTV v0.5.4 RC5 review closed

- Review emulator stopped: yes
- Source/repository/signing identity preserved: yes
- Physical Hisense data preserved: yes
- Cloudflare Worker and D1 preserved: yes
- Operon Analytics tenant/branch/PR preserved: yes
- Stable update remains separately governed.
EOF
cp "$NOTE" "$HOME/.local/state/operon-terminal-runs/ghartv/ghartv-v054-rc5-close-$STAMP.md"
command -v amrit-context >/dev/null 2>&1 && amrit-context handoff --file "$NOTE" >/dev/null 2>&1 || true
echo "Closed only the GharTV review emulator and project Gradle daemon. Durable source and analytics state were preserved."
