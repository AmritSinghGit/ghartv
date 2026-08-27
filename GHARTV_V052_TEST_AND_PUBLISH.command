#!/bin/bash
# GharTV v0.5.2 — test first, then publish to the same repository and lane.
set -Eeuo pipefail

MODE="${1:-test}"
case "$MODE" in test|publish) ;; *) printf 'Usage: %s [test|publish]\n' "$0" >&2; exit 2 ;; esac

PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
REPOSITORY="AmritSinghGit/ghartv"
PACKAGE_ID="in.ghartv.nova"
AVD_NAME="${GHARTV_AVD_NAME:-GharTV_Nova_Manual_google_tv_API36}"
SIGNING_ENV="$HOME/Library/Application Support/GharTV/signing/signing.env"
TAG="v0.5.2"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_ID="ghartv-v052-${MODE}-${STAMP}-$$"
RESULT_DIR="$HOME/Desktop/GharTV-v0.5.2-${MODE}-${STAMP}"
RUN_DIR="$HOME/.local/state/operon-terminal-runs/ghartv/$RUN_ID"
LOG="$RESULT_DIR/setup.log"
APK=""
APK_SHA=""
SDK_ROOT=""
ADB=""
EMULATOR=""
SERIAL=""
STATUS="FAILED"
BLOCKER="Command did not complete."
RECEIPT_WRITTEN=0

mkdir -p "$RESULT_DIR" "$RUN_DIR"
chmod 700 "$RUN_DIR" 2>/dev/null || true
exec > >(tee -a "$LOG") 2>&1
printf '\033]0;%s\007' "GharTV · ghartv · v0.5.2 · $MODE"

step(){ printf '\n============================================================\n%s\n============================================================\n' "$1"; }
note(){ printf '%s\n' "$1"; }
fail(){ BLOCKER="$1"; printf '\nERROR: %s\nEvidence: %s\n' "$1" "$RESULT_DIR" >&2; exit 1; }
sha256_of(){ shasum -a 256 "$1" | awk '{print $1}'; }

write_receipt(){
  [ "$RECEIPT_WRITTEN" -eq 0 ] || return 0
  RECEIPT_WRITTEN=1
  local vault note_dir note_file memory_status
  vault="$HOME/Documents/Amrit Executive Memory"
  note_dir="$vault/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv"
  note_file="$note_dir/$RUN_ID.md"
  mkdir -p "$note_dir"
  cat > "$note_file" <<NOTE
---
type: terminal-run-handoff
lane_id: ghartv
entity_type: project
version: 0.5.2-tv-feedback
status: $STATUS
run_id: $RUN_ID
created: $(date -u +%Y-%m-%dT%H:%M:%SZ)
---

# GharTV v0.5.2 — $MODE receipt

- Existing project: \`~/Downloads/GharTV_Nova_v0.4.2\`
- Repository: \`AmritSinghGit/ghartv\`
- Branch: \`main\`
- Package: \`in.ghartv.nova\`
- New branch/worktree/database/lane: none
- Status: **$STATUS**
- APK SHA-256: \`${APK_SHA:-not-built}\`
- Evidence: \`$RESULT_DIR\`

## Physical-TV corrections

Auto-hiding interactive Now/Next panel; category-scoped CH+/CH−; Subscription and Unavailable groups; Next-channel recovery; channel-specific HTTP 403 classification.

## Blocker

${BLOCKER:-None.}
NOTE
  chmod 600 "$note_file" 2>/dev/null || true
  memory_status="note-written"
  if [ -x "$HOME/bin/amrit-context" ]; then
    if "$HOME/bin/amrit-context" handoff --file "$note_file" > "$RUN_DIR/memory-handoff.txt" 2>&1; then
      memory_status="indexed-by-amrit-context"
    else
      memory_status="amrit-context-handoff-failed"
    fi
  fi
  cat > "$RUN_DIR/PASTE_TO_CHAT.txt" <<PASTE
GHARTV_V052_RECEIPT
STATUS=$STATUS
MODE=$MODE
LANE=ghartv
REPOSITORY=$REPOSITORY
BRANCH=main
PACKAGE=$PACKAGE_ID
APK_SHA256=${APK_SHA:-not-built}
EVIDENCE=$RESULT_DIR
MEMORY=$memory_status
PASTE
  command -v pbcopy >/dev/null 2>&1 && pbcopy < "$RUN_DIR/PASTE_TO_CHAT.txt" || true
  note "Continuity receipt: $RUN_DIR/PASTE_TO_CHAT.txt"
  note "Obsidian receipt: $note_file"
  note "Memory handoff: $memory_status"
}

on_exit(){
  local rc=$?
  trap - EXIT
  if [ "$rc" -eq 0 ] && [ "$STATUS" = "FAILED" ]; then STATUS="TESTED_NOT_PUBLISHED"; BLOCKER="None."; fi
  write_receipt
  exit "$rc"
}
trap on_exit EXIT

verify_and_sync(){
  step "Reusing the exact existing GharTV project"
  [ -d "$PROJECT/.git" ] || fail "Existing Git checkout was not found at $PROJECT"
  cd "$PROJECT"
  [ "$(git symbolic-ref --short HEAD 2>/dev/null || true)" = "main" ] || fail "GharTV is not on canonical main; no branch was created."
  local origin dirty
  origin="$(git remote get-url origin 2>/dev/null || true)"
  case "$origin" in
    https://github.com/AmritSinghGit/ghartv.git|git@github.com:AmritSinghGit/ghartv.git) ;;
    *) fail "Origin is not the canonical GharTV repository: $origin" ;;
  esac
  dirty="$(git status --porcelain --untracked-files=normal)"
  [ -z "$dirty" ] || { printf '%s\n' "$dirty"; fail "The existing checkout has uncommitted source changes. Preserve/review them before updating."; }
  git fetch --no-tags origin main
  git pull --ff-only origin main
  [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || fail "Local main did not converge to origin/main."
  bash ./VALIDATE_SOURCE.command
  note "Source: $(git rev-parse HEAD)"
}

configure_java(){
  local candidate major
  for candidate in \
    "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" \
    "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  do
    [ -x "$candidate/bin/java" ] || continue
    major="$($candidate/bin/java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
    case "$major" in 17|21) export JAVA_HOME="$candidate"; export PATH="$JAVA_HOME/bin:$PATH"; return 0 ;; esac
  done
  fail "Java 17 or 21 was not found."
}

configure_android(){
  for SDK_ROOT in "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -n "$SDK_ROOT" ] && [ -d "$SDK_ROOT" ] && break
  done
  [ -d "$SDK_ROOT" ] || fail "Android SDK was not found."
  export ANDROID_SDK_ROOT="$SDK_ROOT" ANDROID_HOME="$SDK_ROOT"
  ADB="$SDK_ROOT/platform-tools/adb"
  EMULATOR="$SDK_ROOT/emulator/emulator"
  [ -x "$ADB" ] || fail "adb is missing."
  [ -x "$EMULATOR" ] || fail "Android Emulator is missing."
}

load_signing(){
  [ -f "$SIGNING_ENV" ] || fail "Existing GharTV signing configuration is missing: $SIGNING_ENV"
  set -a
  # shellcheck disable=SC1090
  source "$SIGNING_ENV"
  set +a
  [ -f "${GHARTV_SIGNING_STORE:-}" ] || fail "Existing GharTV release keystore is missing."
  for variable in GHARTV_SIGNING_STORE_PASSWORD GHARTV_SIGNING_KEY_ALIAS GHARTV_SIGNING_KEY_PASSWORD; do
    [ -n "${!variable:-}" ] || fail "Signing variable $variable is missing."
  done
}

build_release(){
  step "Building the signed GharTV v0.5.2 candidate"
  cd "$PROJECT/android-tv"
  chmod +x ./gradlew
  ./gradlew --no-daemon --stacktrace --max-workers=2 -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleRelease
  local built apksigner
  built="$PROJECT/android-tv/app/build/outputs/apk/release/app-release.apk"
  [ -f "$built" ] || fail "Gradle did not produce the release APK."
  apksigner=""
  for candidate in "$SDK_ROOT"/build-tools/*/apksigner; do [ -x "$candidate" ] && apksigner="$candidate"; done
  [ -x "$apksigner" ] || fail "apksigner was not found."
  "$apksigner" verify --verbose --print-certs "$built" > "$RESULT_DIR/apk-signature.txt"
  mkdir -p "$PROJECT/release"
  APK="$PROJECT/release/GharTV-Jio-Live.apk"
  cp "$built" "$APK"
  APK_SHA="$(sha256_of "$APK")"
  printf '%s  GharTV-Jio-Live.apk\n' "$APK_SHA" > "$PROJECT/release/GharTV-Jio-Live.apk.sha256"
  cp "$APK" "$RESULT_DIR/GharTV-Jio-Live-v0.5.2.apk"
  cp "$PROJECT/release/GharTV-Jio-Live.apk.sha256" "$RESULT_DIR/"
  note "Signed APK: $APK"
  note "SHA-256: $APK_SHA"
}

find_running_tv(){
  local device name
  "$ADB" start-server >/dev/null 2>&1 || true
  while read -r device _; do
    [[ "$device" == emulator-* ]] || continue
    name="$($ADB -s "$device" emu avd name 2>/dev/null | head -1 | tr -d '\r' || true)"
    if [ "$name" = "$AVD_NAME" ]; then SERIAL="$device"; return 0; fi
  done < <("$ADB" devices | tail -n +2)
  return 1
}

start_tv(){
  "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME" || fail "Existing AVD $AVD_NAME was not found; no new AVD was created."
  step "Starting the existing Google TV emulator"
  nohup "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save -netdelay none -netspeed full > "$RESULT_DIR/emulator.log" 2>&1 &
  for _ in $(seq 1 120); do find_running_tv && return 0; sleep 2; done
  fail "The existing Google TV AVD did not appear in adb."
}

wait_tv(){
  step "Waiting for Google TV"
  local complete state
  for _ in $(seq 1 300); do
    state="$($ADB -s "$SERIAL" get-state 2>/dev/null || true)"
    complete="$($ADB -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$state" = device ] && [ "$complete" = 1 ]; then
      "$ADB" -s "$SERIAL" shell pm list features | grep -q android.software.leanback || fail "Target is not Android TV/Google TV."
      return 0
    fi
    sleep 2
  done
  fail "Google TV did not finish booting."
}

install_test(){
  step "Installing v0.5.2 without clearing the Jio session"
  "$ADB" -s "$SERIAL" install -r -d "$APK"
  "$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE_ID" >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" shell am start -W -n "$PACKAGE_ID/.MainActivity"
  sleep 4
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$RESULT_DIR/GharTV-v0.5.2-home.png" || true
  "$ADB" -s "$SERIAL" logcat -d -v threadtime > "$RESULT_DIR/emulator-logcat.txt" || true
  note "GharTV v0.5.2 is open on $SERIAL. Existing login/favourites were preserved."
}

publish_release(){
  step "Publishing v0.5.2 to the same main branch and release channel"
  command -v gh >/dev/null 2>&1 || fail "GitHub CLI is missing."
  gh auth status -h github.com >/dev/null 2>&1 || fail "Run: gh auth login --hostname github.com --git-protocol https --web"
  [ "$(gh api user --jq .login)" = "AmritSinghGit" ] || fail "GitHub CLI is not authenticated as AmritSinghGit."
  cd "$PROJECT"
  [ -z "$(git status --porcelain --untracked-files=normal)" ] || fail "Tracked source changed during testing; publication stopped."
  python3 - "$PROJECT/update/latest.json" "$APK_SHA" <<'PY'
from pathlib import Path
from datetime import datetime,timezone
import json,sys
p=Path(sys.argv[1]); data=json.loads(p.read_text())
data.update(versionCode=9,versionName='0.5.2-tv-feedback',sha256=sys.argv[2],
 notes='Interactive Now/Next player guide, category-scoped channel surfing, subscription/unavailable groups, Next-channel recovery and corrected Jio HTTP 403 handling.',
 publishedAt=datetime.now(timezone.utc).isoformat())
p.write_text(json.dumps(data,indent=2)+'\n')
PY
  python3 - "$PROJECT/docs/index.html" <<'PY'
from pathlib import Path
p=Path(__import__('sys').argv[1]); text=p.read_text()
text=text.replace('Current TV test candidate: v0.5.1','Current physical-TV feedback candidate: v0.5.2')
p.write_text(text)
PY
  bash ./VALIDATE_SOURCE.command
  git add update/latest.json docs/index.html
  if ! git diff --cached --quiet; then git commit -m "release: GharTV Jio Live v0.5.2 physical TV feedback"; fi
  git push origin main
  printf '%s  GharTV-Jio-Live.apk\n' "$APK_SHA" > "$PROJECT/release/GharTV-Jio-Live.apk.sha256"
  if gh release view "$TAG" --repo "$REPOSITORY" >/dev/null 2>&1; then
    gh release upload "$TAG" "$APK#GharTV-Jio-Live.apk" "$PROJECT/release/GharTV-Jio-Live.apk.sha256#GharTV-Jio-Live.apk.sha256" --clobber --repo "$REPOSITORY"
    gh release edit "$TAG" --repo "$REPOSITORY" --title "GharTV Jio Live v0.5.2 — physical TV feedback" --notes-file "$PROJECT/release-notes-v0.5.2.md" --latest
  else
    gh release create "$TAG" "$APK#GharTV-Jio-Live.apk" "$PROJECT/release/GharTV-Jio-Live.apk.sha256#GharTV-Jio-Live.apk.sha256" --repo "$REPOSITORY" --target main --title "GharTV Jio Live v0.5.2 — physical TV feedback" --notes-file "$PROJECT/release-notes-v0.5.2.md" --latest
  fi
  gh release view "$TAG" --repo "$REPOSITORY" --json tagName,url,assets > "$RESULT_DIR/release.json"
  python3 - "$RESULT_DIR/release.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1])); names={a['name'] for a in r.get('assets',[])}
assert {'GharTV-Jio-Live.apk','GharTV-Jio-Live.apk.sha256'} <= names, names
print('Release assets verified:',', '.join(sorted(names)))
PY
  STATUS="PUBLISHED_TV_FEEDBACK_CANDIDATE"
  BLOCKER="Owner acceptance remains pending physical Hisense verification."
  note "Release: https://github.com/$REPOSITORY/releases/tag/$TAG"
  note "TV page: https://amritsinghgit.github.io/ghartv/"
  note "Direct APK: https://github.com/$REPOSITORY/releases/latest/download/GharTV-Jio-Live.apk"
}

key(){ "$ADB" -s "$SERIAL" shell input keyevent "$1" >/dev/null 2>&1 || true; }
shot(){ local f="$RESULT_DIR/GharTV-$(date +%H%M%S).png"; "$ADB" -s "$SERIAL" exec-out screencap -p > "$f"; note "Screenshot: $f"; }
logs(){ local f="$RESULT_DIR/logcat-$(date +%H%M%S).txt"; "$ADB" -s "$SERIAL" logcat -d -v threadtime > "$f"; note "Logs: $f"; }
status(){ "$ADB" -s "$SERIAL" shell dumpsys package "$PACKAGE_ID" | grep -E 'versionName|versionCode' | head; "$ADB" -s "$SERIAL" shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head; }

remote_loop(){
  cat <<'HELP'

GharTV v0.5.2 test remote
-------------------------
  up/down/left/right   Move focus
  ok                   Select / show interactive player guide
  back                 Hide player guide, then return
  info                 Toggle Now/Next guide
  guide                Return to channel guide
  ch+ / ch-             Switch inside the selected language/category
  shot / logs / status Save evidence
  publish              Publish v0.5.2 after you approve this emulator test
  quit                 Leave emulator running
  stop                 Stop emulator
HELP
  local command
  while true; do
    printf '\nGharTV v0.5.2 [%s] > ' "$SERIAL"
    IFS= read -r command || command=quit
    case "$command" in
      up) key 19 ;; down) key 20 ;; left) key 21 ;; right) key 22 ;; ok|enter) key 23 ;;
      back) key 4 ;; home) key 3 ;; info) key 165 ;; guide) key 172 ;; ch+) key 166 ;; ch-) key 167 ;;
      shot) shot ;; logs) logs ;; status) status ;;
      publish) publish_release; write_receipt; return 0 ;;
      quit|q|exit) STATUS="TESTED_NOT_PUBLISHED"; BLOCKER="Awaiting owner approval to publish v0.5.2."; return 0 ;;
      stop) "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true; STATUS="TESTED_NOT_PUBLISHED"; BLOCKER="Awaiting owner approval to publish v0.5.2."; return 0 ;;
      help|h|'?') remote_loop; return 0 ;;
      *) note "Unknown command. Type help." ;;
    esac
  done
}

step "GharTV v0.5.2 — existing-lane physical-TV correction"
note "Project: $PROJECT"
note "Repository: $REPOSITORY"
note "Branch: main"
note "Lane: ghartv (project, not tenant)"
note "No branch, worktree, database, package identity or continuity lane will be created."
verify_and_sync
configure_java
configure_android
load_signing
build_release
find_running_tv || start_tv
wait_tv
install_test
if [ "$MODE" = publish ]; then publish_release; else STATUS="TESTED_NOT_PUBLISHED"; BLOCKER="Awaiting owner approval to publish v0.5.2."; remote_loop; fi
write_receipt
