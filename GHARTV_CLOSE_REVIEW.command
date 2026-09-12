#!/bin/bash
# Close only the local GharTV review runtime after capturing evidence.
set -Eeuo pipefail

PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
LANE="ghartv"
PACKAGE="in.ghartv.nova"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_ID="ghartv-close-$STAMP-$$"
RUN_DIR="$HOME/.local/state/operon-terminal-runs/$LANE/$RUN_ID"
VAULT="$HOME/Documents/Amrit Executive Memory"
mkdir -p "$RUN_DIR"

section(){ printf '\n============================================================\n%s\n============================================================\n' "$*"; }

section "Capturing GharTV evidence before closing"
REPORT=""
if [ -x "$PROJECT/GHARTV_LOGS_AND_HEALTH.command" ]; then
  BEFORE="$(find "$HOME/Desktop" -maxdepth 1 -type d -name 'GharTV-Owner-Report-*' -print 2>/dev/null | sort | tail -1 || true)"
  bash "$PROJECT/GHARTV_LOGS_AND_HEALTH.command" || true
  AFTER="$(find "$HOME/Desktop" -maxdepth 1 -type d -name 'GharTV-Owner-Report-*' -print 2>/dev/null | sort | tail -1 || true)"
  [ "$AFTER" != "$BEFORE" ] && REPORT="$AFTER"
fi

section "Stopping only GharTV emulator instances"
SDK=""
for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  if [ -n "$candidate" ] && [ -x "$candidate/platform-tools/adb" ]; then SDK="$candidate"; break; fi
done
STOPPED=0
if [ -n "$SDK" ]; then
  ADB="$SDK/platform-tools/adb"
  while read -r serial state _; do
    [[ "$serial" == emulator-* && "$state" == device ]] || continue
    avd="$($ADB -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | head -1 || true)"
    if [[ "$avd" == GharTV_* ]]; then
      printf 'Stopping %s (%s)\n' "$avd" "$serial"
      "$ADB" -s "$serial" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
      "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
      STOPPED=$((STOPPED+1))
    fi
  done < <("$ADB" devices | tail -n +2)
fi
[ "$STOPPED" -gt 0 ] || printf 'No running GharTV emulator needed closing.\n'

section "Stopping only this project's Gradle daemons"
if [ -x "$PROJECT/android-tv/gradlew" ]; then
  (cd "$PROJECT/android-tv" && ./gradlew --stop >/dev/null 2>&1) || true
elif [ -x "$HOME/Library/Caches/GharTV-Nova/gradle-8.11.1/bin/gradle" ]; then
  "$HOME/Library/Caches/GharTV-Nova/gradle-8.11.1/bin/gradle" --stop >/dev/null 2>&1 || true
fi
rm -rf "${TMPDIR:-/tmp}"/ghartv-v054-* 2>/dev/null || true

HEAD=""
BRANCH=""
if [ -d "$PROJECT/.git" ]; then
  HEAD="$(git -C "$PROJECT" rev-parse HEAD 2>/dev/null || true)"
  BRANCH="$(git -C "$PROJECT" symbolic-ref --short HEAD 2>/dev/null || true)"
fi

RECEIPT="$RUN_DIR/receipt.json"
PASTE="$RUN_DIR/PASTE_TO_CHAT.txt"
python3 - "$RECEIPT" "$HEAD" "$BRANCH" "$REPORT" "$STOPPED" <<'PY'
from pathlib import Path
from datetime import datetime,timezone
import json,sys
path,head,branch,report,stopped=sys.argv[1:]
data={
 "schema":"ghartv.continuity.receipt.v1",
 "lane_id":"ghartv",
 "entity_type":"project",
 "repository":"AmritSinghGit/ghartv",
 "branch":branch or "main",
 "package_id":"in.ghartv.nova",
 "head_sha":head,
 "status":"LOCAL_REVIEW_CLOSED",
 "owner_decision":"REVIEW_PENDING",
 "emulators_stopped":int(stopped),
 "owner_report":report,
 "preserved":["canonical source","release signing identity","Jio data on physical TVs","Cloudflare Worker/D1 collector","Obsidian and amrit-context lane"],
 "closed_at":datetime.now(timezone.utc).isoformat(),
 "next_action":"Review the candidate; run the stable publish command only after explicit approval."
}
Path(path).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
PY

NOTE=""
if [ -d "$VAULT" ]; then
  NOTE_DIR="$VAULT/90 System/Operon Portfolio/Handoffs/Terminal Runs/$LANE"
  mkdir -p "$NOTE_DIR"
  NOTE="$NOTE_DIR/$RUN_ID.md"
  cat > "$NOTE" <<NOTE
---
type: terminal-run-handoff
lane_id: ghartv
entity_type: project
status: LOCAL_REVIEW_CLOSED
repository: AmritSinghGit/ghartv
branch: ${BRANCH:-main}
head_sha: $HEAD
created: $(date -u +%Y-%m-%dT%H:%M:%SZ)
---

# GharTV local review closed

The local review runtime was closed without deleting the canonical project, signing identity, diagnostics collector, releases, or lane state.

- GharTV emulators stopped: $STOPPED
- Owner report: ${REPORT:-not generated}
- Owner decision: REVIEW_PENDING
- Next action: review the candidate and run stable publication only after approval.
NOTE
  chmod 600 "$NOTE" 2>/dev/null || true
  if [ -x "$HOME/bin/amrit-context" ]; then
    HANDOFF="$RUN_DIR/handoff.json"
    python3 - "$HANDOFF" "$NOTE" "$HEAD" <<'PY'
from pathlib import Path
import json,sys
out,note,head=sys.argv[1:]
payload={
 "schema":"amrit.context-mesh.handoff.v1",
 "handoff_key":"ghartv-v054-close-review",
 "project_key":"ghartv",
 "chat_key":"iptv-app-for-tv",
 "title":"GharTV v0.5.4 local review closed",
 "summary":"Stopped only the local GharTV review runtime after capturing owner evidence; canonical project and authorities retained.",
 "current_truth":f"AmritSinghGit/ghartv main at {head or 'unknown'}; owner decision REVIEW_PENDING; local emulator closed.",
 "next_action":"Review candidate; publish stable v0.5.4 only after explicit owner approval.",
 "body_markdown":Path(note).read_text(encoding='utf-8')
}
Path(out).write_text(json.dumps(payload,indent=2)+'\n',encoding='utf-8')
PY
    "$HOME/bin/amrit-context" handoff --file "$HANDOFF" > "$RUN_DIR/memory-handoff.txt" 2>&1 || true
    "$HOME/bin/amrit-context" sync-once > "$RUN_DIR/memory-sync.txt" 2>&1 || true
  fi
fi

cat > "$PASTE" <<PASTE
GHARTV_V054_CLOSE_RECEIPT
LANE_ID=ghartv
ENTITY_TYPE=project
REPOSITORY=AmritSinghGit/ghartv
BRANCH=${BRANCH:-main}
HEAD_SHA=$HEAD
STATUS=LOCAL_REVIEW_CLOSED
OWNER_DECISION=REVIEW_PENDING
EMULATORS_STOPPED=$STOPPED
OWNER_REPORT=${REPORT:-}
NEXT_ACTION=Review the candidate; publish stable v0.5.4 only after explicit approval.
PASTE
chmod 600 "$RECEIPT" "$PASTE" 2>/dev/null || true

section "GharTV local review is closed"
printf 'Canonical project retained: %s\n' "$PROJECT"
printf 'Continuity receipt: %s\n' "$PASTE"
[ -n "$NOTE" ] && printf 'Obsidian receipt: %s\n' "$NOTE"
read -r -p "Press Enter to copy the close handoff… " _
command -v pbcopy >/dev/null 2>&1 && pbcopy < "$PASTE" || true
read -r -p "Handoff copied. Press Enter to close this terminal run… " _
