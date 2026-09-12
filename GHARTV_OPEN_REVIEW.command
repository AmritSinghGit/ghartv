#!/bin/bash
# Sync the exact canonical GharTV review candidate, build/sign it, publish the prerelease asset,
# open it on the existing Google TV emulator, collect an owner report, and register the lane.
set -Eeuo pipefail

PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
REPOSITORY="AmritSinghGit/ghartv"
PACKAGE_ID="in.ghartv.nova"
LANE_ID="ghartv"
EXPECTED_SHA="${GHARTV_EXPECTED_SHA:-}"
RELEASE_TAG="v0.5.4-rc2"
RELEASE_ASSET="GharTV-Jio-Live-v0.5.4-rc2.apk"
RELEASE_SHA_ASSET="$RELEASE_ASSET.sha256"
AVD_NAME="${GHARTV_AVD_NAME:-GharTV_Nova_Manual_google_tv_API36}"
CACHE_DIR="$HOME/Library/Caches/GharTV-Nova"
SIGNING_ENV="$HOME/Library/Application Support/GharTV/signing/signing.env"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_ID="ghartv-v054-rc2-review-$STAMP-$$"
RESULT_DIR="${GHARTV_REVIEW_RESULTS_DIR:-$HOME/Desktop/GharTV-v0.5.4-RC2-Review-$STAMP}"
RUN_DIR="$HOME/.local/state/operon-terminal-runs/$LANE_ID/$RUN_ID"
mkdir -p "$RESULT_DIR" "$RUN_DIR"
SETUP_LOG="$RESULT_DIR/setup.log"
TRANSCRIPT="$RUN_DIR/full-transcript.log"
exec > >(tee -a "$SETUP_LOG" "$TRANSCRIPT") 2>&1

CANDIDATE_SHA=""
APK_SHA=""
SERIAL=""
EMULATOR_STATUS="not-attempted"
RELEASE_URL=""
OWNER_REPORT=""
CONTINUITY_DONE=0

section(){ printf '\n============================================================\n%s\n============================================================\n' "$*"; }
fail(){ printf '\nERROR: %s\n' "$1" >&2; exit 1; }
sha256_of(){ shasum -a 256 "$1" | awk '{print $1}'; }

sanitize(){
  local source="$1" destination="$2"
  python3 - "$source" "$destination" <<'PY'
from pathlib import Path
import re,sys
src,dst=map(Path,sys.argv[1:])
text=src.read_text(encoding='utf-8',errors='replace') if src.exists() else ''
for pattern,repl in [
 (r'(?i)(authorization|cookie|ssotoken|authtoken|refreshtoken|otp|password|passcode|secret|admin_token|ingest_key)\s*[:=]\s*[^\s,;]+',r'\1=[REDACTED]'),
 (r'(?<!\d)(?:\+?91[- ]?)?[6-9]\d{9}(?!\d)','[PHONE REDACTED]'),
 (r'(?i)Bearer\s+[A-Za-z0-9._~+/-]{12,}','Bearer [REDACTED]'),
]: text=re.sub(pattern,repl,text)
dst.write_text(text,encoding='utf-8')
PY
}

write_receipt(){
  [ "$CONTINUITY_DONE" -eq 0 ] || return 0
  CONTINUITY_DONE=1
  local status="$1" blocker="${2:-}" finished receipt paste note_file="" memory="not-attempted"
  finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  receipt="$RUN_DIR/receipt.json"; paste="$RUN_DIR/PASTE_TO_CHAT.txt"
  sanitize "$TRANSCRIPT" "$RUN_DIR/sanitized-transcript.log" || true
  python3 - "$receipt" "$status" "$blocker" "$CANDIDATE_SHA" "$APK_SHA" "$SERIAL" "$RELEASE_URL" "$OWNER_REPORT" "$finished" <<'PY'
from pathlib import Path
import json,sys
path,status,blocker,sha,apk,serial,url,report,finished=sys.argv[1:]
data={
 'schema':'ghartv.continuity.receipt.v1','lane_id':'ghartv','entity_type':'project',
 'repository':'AmritSinghGit/ghartv','branch':'main','package_id':'in.ghartv.nova',
 'version':'0.5.4-rc2-living-room','version_code':11,'status':status,
 'owner_decision':'REVIEW_PENDING','candidate_sha':sha,'apk_sha256':apk,
 'emulator_serial':serial,'prerelease_url':url,'owner_report':report,'blocker':blocker,
 'finished_at':finished,'stable_update_unchanged':'0.5.3 / versionCode 10',
 'new_project_created':False,'new_branch_created':False,'new_worktree_created':False,
 'new_database_created':False,'new_worker_created':False,'new_lane_created':False,
 'next_action':'Review RC2 on emulator; publish stable v0.5.4 only after explicit approval.'
}
Path(path).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
PY
  VAULT="$HOME/Documents/Amrit Executive Memory"
  if [ -d "$VAULT" ]; then
    note_dir="$VAULT/90 System/Operon Portfolio/Handoffs/Terminal Runs/$LANE_ID"
    mkdir -p "$note_dir"; note_file="$note_dir/$RUN_ID.md"
    cat > "$note_file" <<NOTE
---
type: terminal-run-handoff
lane_id: ghartv
entity_type: project
status: $status
repository: AmritSinghGit/ghartv
branch: main
version: 0.5.4-rc2-living-room
head_sha: $CANDIDATE_SHA
created: $finished
---

# GharTV v0.5.4 RC2 review opened

- Existing canonical project: ~/Downloads/GharTV_Nova_v0.4.2
- Candidate SHA: $CANDIDATE_SHA
- Signed review APK SHA-256: $APK_SHA
- Emulator: ${SERIAL:-not opened}
- GitHub prerelease: ${RELEASE_URL:-not published}
- Owner report: ${OWNER_REPORT:-not generated}
- Status: $status
- Blocker: ${blocker:-None}
- Owner decision: REVIEW_PENDING
- Stable update remains v0.5.3 / versionCode 10.

No replacement repository, branch, worktree, signing identity, telemetry Worker/D1 store, Operon project, or lane was created.
NOTE
    chmod 600 "$note_file" 2>/dev/null || true
    if [ -x "$HOME/bin/amrit-context" ]; then
      handoff="$RUN_DIR/handoff.json"
      python3 - "$handoff" "$note_file" "$CANDIDATE_SHA" "$status" <<'PY'
from pathlib import Path
import json,sys
out,note,sha,status=sys.argv[1:]
payload={
 'schema':'amrit.context-mesh.handoff.v1','handoff_key':'ghartv-v054-rc2-review',
 'project_key':'ghartv','chat_key':'iptv-app-for-tv','title':'GharTV v0.5.4 RC2 review',
 'summary':f'Opened GharTV v0.5.4 RC2 at {sha or "unknown"}; status {status}; owner decision REVIEW_PENDING.',
 'current_truth':f'AmritSinghGit/ghartv main at {sha or "unknown"}; package in.ghartv.nova; stable update still v0.5.3.',
 'next_action':'Review in emulator; use GHARTV_PUBLISH_STABLE_V054.command publish only after approval.',
 'body_markdown':Path(note).read_text(encoding='utf-8')
}
Path(out).write_text(json.dumps(payload,indent=2)+'\n',encoding='utf-8')
PY
      "$HOME/bin/amrit-context" handoff --file "$handoff" > "$RUN_DIR/memory-handoff.txt" 2>&1 || true
      "$HOME/bin/amrit-context" sync-once > "$RUN_DIR/memory-sync.txt" 2>&1 || true
      memory="indexed-by-amrit-context"
    fi
  fi
  cat > "$paste" <<PASTE
GHARTV_V054_RC2_REVIEW
LANE_ID=ghartv
ENTITY_TYPE=project
REPOSITORY=AmritSinghGit/ghartv
BRANCH=main
PACKAGE=in.ghartv.nova
VERSION=0.5.4-rc2-living-room
STATUS=$status
OWNER_DECISION=REVIEW_PENDING
CANDIDATE_SHA=$CANDIDATE_SHA
APK_SHA256=$APK_SHA
EMULATOR_SERIAL=$SERIAL
PRERELEASE_URL=$RELEASE_URL
OWNER_REPORT=$OWNER_REPORT
STABLE_UPDATE=0.5.3 / versionCode 10
BLOCKER=$blocker
NEXT_ACTION=Review RC2; publish stable v0.5.4 only after explicit approval.
PASTE
  chmod 600 "$receipt" "$paste" 2>/dev/null || true
  printf 'Continuity receipt: %s\n' "$paste"
  [ -n "$note_file" ] && printf 'Obsidian receipt: %s\n' "$note_file"
  printf 'Memory handoff: %s\n' "$memory"
}

on_error(){ local code=$?; write_receipt "ACTION_REQUIRED" "Review opener stopped at line ${BASH_LINENO[0]} (exit $code). Review $SETUP_LOG" || true; exit "$code"; }
trap on_error ERR

section "GharTV v0.5.4 RC2 — authoritative review opener"
printf 'Existing project: %s\n' "$PROJECT"
printf 'Repository/branch: %s · main\n' "$REPOSITORY"
printf 'Package/lane: %s · %s\n' "$PACKAGE_ID" "$LANE_ID"
printf 'Stable televisions stay on v0.5.3 until explicit owner publication.\n'

section "Synchronising the canonical checkout without replacement"
[ -d "$PROJECT/.git" ] || fail "Canonical checkout is missing: $PROJECT"
[ "$(git -C "$PROJECT" symbolic-ref --short HEAD)" = "main" ] || fail "Canonical checkout is not on main"
origin="$(git -C "$PROJECT" remote get-url origin)"
case "$origin" in
  https://github.com/AmritSinghGit/ghartv|https://github.com/AmritSinghGit/ghartv.git|git@github.com:AmritSinghGit/ghartv.git) ;;
  *) fail "Unexpected origin: $origin" ;;
esac
[ -z "$(git -C "$PROJECT" status --porcelain)" ] || fail "Checkout has uncommitted work; stopped without reset, clean, stash, or overwrite"
git -C "$PROJECT" fetch --no-tags origin main
remote_head="$(git -C "$PROJECT" rev-parse origin/main)"
if [ -n "$EXPECTED_SHA" ] && [ "$remote_head" != "$EXPECTED_SHA" ]; then
  fail "Remote main is $remote_head, not the exact delivered candidate $EXPECTED_SHA"
fi
local_head="$(git -C "$PROJECT" rev-parse HEAD)"
if [ "$local_head" != "$remote_head" ]; then
  git -C "$PROJECT" merge-base --is-ancestor "$local_head" "$remote_head" || fail "Local and remote main diverged"
  git -C "$PROJECT" pull --ff-only origin main
fi
CANDIDATE_SHA="$(git -C "$PROJECT" rev-parse HEAD)"
[ "$CANDIDATE_SHA" = "$remote_head" ] || fail "Local checkout did not reach remote main"
grep -Fq 'versionCode = 11' "$PROJECT/android-tv/app/build.gradle.kts" || fail "Remote main is not the RC2 candidate"
grep -Fq 'versionName = "0.5.4-rc2-living-room"' "$PROJECT/android-tv/app/build.gradle.kts" || fail "Unexpected candidate version"
GHARTV_PROJECT="$PROJECT" bash "$PROJECT/VALIDATE_SOURCE.command"

section "Verifying GitHub source build"
command -v gh >/dev/null 2>&1 || fail "GitHub CLI is required"
gh auth status -h github.com >/dev/null 2>&1 || gh auth login --hostname github.com --git-protocol https --web
[ "$(gh api user --jq .login)" = "AmritSinghGit" ] || fail "GitHub CLI is not authenticated as AmritSinghGit"
run_id=""
for _ in $(seq 1 30); do
  run_id="$(gh run list --repo "$REPOSITORY" --workflow android.yml --branch main --commit "$CANDIDATE_SHA" --limit 1 --json databaseId --jq '.[0].databaseId // empty' 2>/dev/null || true)"
  [ -n "$run_id" ] && break
  sleep 2
done
[ -n "$run_id" ] || fail "No canonical Android TV build was found for $CANDIDATE_SHA"
run_status="$(gh run view "$run_id" --repo "$REPOSITORY" --json status --jq .status 2>/dev/null || true)"
if [ "$run_status" != "completed" ]; then
  printf 'GitHub Android TV build is %s; waiting for completion…\n' "${run_status:-queued}"
  gh run watch "$run_id" --repo "$REPOSITORY" --exit-status
fi
gh run view "$run_id" --repo "$REPOSITORY" --json conclusion,status,headSha > "$RESULT_DIR/github-build.json"
python3 - "$RESULT_DIR/github-build.json" "$CANDIDATE_SHA" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['headSha']==sys.argv[2]
assert d['status']=='completed' and d['conclusion']=='success', f"GitHub build is {d['status']}/{d['conclusion']}"
PY

section "Using the existing Android and signing authority"
JAVA_HOME_SELECTED=""
for candidate in \
  "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home" \
  "$HOME/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home" \
  "$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
do
  [ -x "$candidate/bin/java" ] || continue
  major="$($candidate/bin/java -version 2>&1 | awk -F'[".]' '/version/{print $2;exit}')"
  if [ "$major" = "17" ] || [ "$major" = "21" ]; then JAVA_HOME_SELECTED="$candidate"; break; fi
done
[ -x "$JAVA_HOME_SELECTED/bin/java" ] || fail "JDK 17/21 was not found"
export JAVA_HOME="$JAVA_HOME_SELECTED" PATH="$JAVA_HOME_SELECTED/bin:$PATH"
SDK_ROOT=""
for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  if [ -n "$candidate" ] && [ -d "$candidate" ]; then SDK_ROOT="$candidate"; break; fi
done
[ -d "$SDK_ROOT/platforms/android-36" ] || fail "Android SDK Platform 36 is missing"
[ -d "$SDK_ROOT/build-tools/36.0.0" ] || fail "Android Build Tools 36.0.0 are missing"
export ANDROID_SDK_ROOT="$SDK_ROOT" ANDROID_HOME="$SDK_ROOT"
ADB="$SDK_ROOT/platform-tools/adb"; EMULATOR="$SDK_ROOT/emulator/emulator"
[ -x "$ADB" ] && [ -x "$EMULATOR" ] || fail "Android adb/emulator is missing"
[ -f "$SIGNING_ENV" ] || fail "Existing signing.env is missing; refusing a different signing identity"
set +u
# shellcheck disable=SC1090
source "$SIGNING_ENV"
set -u
export GHARTV_SIGNING_STORE GHARTV_SIGNING_STORE_PASSWORD GHARTV_SIGNING_KEY_ALIAS GHARTV_SIGNING_KEY_PASSWORD
[ -f "$GHARTV_SIGNING_STORE" ] || fail "Existing GharTV keystore is missing"
keytool -list -keystore "$GHARTV_SIGNING_STORE" -storepass "$GHARTV_SIGNING_STORE_PASSWORD" -alias "$GHARTV_SIGNING_KEY_ALIAS" >/dev/null 2>&1 \
  || fail "Existing GharTV signing identity could not be opened"

section "Building and signing RC2"
GRADLE="$PROJECT/android-tv/gradlew"
[ -x "$GRADLE" ] || GRADLE="$CACHE_DIR/gradle-8.11.1/bin/gradle"
[ -x "$GRADLE" ] || fail "Gradle 8.11.1 is missing"
export GRADLE_USER_HOME="$CACHE_DIR/gradle-user-home-java17"
(cd "$PROJECT/android-tv" && "$GRADLE" --no-daemon --stacktrace --max-workers=2 -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleRelease)
BUILT="$PROJECT/android-tv/app/build/outputs/apk/release/app-release.apk"
[ -f "$BUILT" ] || fail "Signed RC2 APK was not produced"
APKSIGNER="$SDK_ROOT/build-tools/36.0.0/apksigner"
[ -x "$APKSIGNER" ] || fail "apksigner is missing"
"$APKSIGNER" verify --verbose --print-certs "$BUILT" > "$RESULT_DIR/apk-signature.txt"
REVIEW_APK="$RESULT_DIR/$RELEASE_ASSET"
cp "$BUILT" "$REVIEW_APK"
APK_SHA="$(sha256_of "$REVIEW_APK")"
printf '%s  %s\n' "$APK_SHA" "$RELEASE_ASSET" > "$RESULT_DIR/$RELEASE_SHA_ASSET"

section "Publishing or refreshing the signed GitHub prerelease"
if gh release view "$RELEASE_TAG" --repo "$REPOSITORY" >/dev/null 2>&1; then
  gh release upload "$RELEASE_TAG" "$REVIEW_APK" "$RESULT_DIR/$RELEASE_SHA_ASSET" --clobber --repo "$REPOSITORY"
  gh release edit "$RELEASE_TAG" --repo "$REPOSITORY" --target "$CANDIDATE_SHA" --prerelease \
    --title "GharTV Jio Live v0.5.4 RC2 — living-room reliability" \
    --notes-file "$PROJECT/release-notes-v0.5.4-rc2.md"
else
  gh release create "$RELEASE_TAG" "$REVIEW_APK" "$RESULT_DIR/$RELEASE_SHA_ASSET" \
    --repo "$REPOSITORY" --target "$CANDIDATE_SHA" --prerelease \
    --title "GharTV Jio Live v0.5.4 RC2 — living-room reliability" \
    --notes-file "$PROJECT/release-notes-v0.5.4-rc2.md"
fi
release_json="$RESULT_DIR/prerelease.json"
gh release view "$RELEASE_TAG" --repo "$REPOSITORY" --json url,tagName,isDraft,isPrerelease,assets,targetCommitish > "$release_json"
python3 - "$release_json" "$CANDIDATE_SHA" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['tagName']=='v0.5.4-rc2' and d['isPrerelease'] and not d['isDraft']
assert d['targetCommitish']==sys.argv[2]
names={x['name'] for x in d.get('assets',[])}; assert {'GharTV-Jio-Live-v0.5.4-rc2.apk','GharTV-Jio-Live-v0.5.4-rc2.apk.sha256'} <= names
PY
RELEASE_URL="$(python3 - "$release_json" <<'PY'
from pathlib import Path
import json,sys
print(json.loads(Path(sys.argv[1]).read_text())['url'])
PY
)"
python3 - "$PROJECT/update/latest.json" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['versionCode']==10 and d['versionName']=='0.5.3-observability'
print('Stable update manifest retained at v0.5.3.')
PY

section "Starting or reusing the dedicated Google TV emulator"
while read -r candidate state _; do
  [[ "$candidate" == emulator-* && "$state" == device ]] || continue
  avd="$($ADB -s "$candidate" emu avd name 2>/dev/null | tr -d '\r' | head -1 || true)"
  if [[ "$avd" == GharTV_* ]]; then SERIAL="$candidate"; break; fi
done < <("$ADB" devices | tail -n +2)

if [ -z "$SERIAL" ]; then
  "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME" || fail "Existing GharTV TV AVD is missing: $AVD_NAME"
  port="5580"
  for candidate in 5580 5582 5584 5586 5588 5590; do
    if ! "$ADB" devices | grep -q "emulator-$candidate"; then port="$candidate"; break; fi
  done
  SERIAL="emulator-$port"
  nohup "$EMULATOR" -avd "$AVD_NAME" -port "$port" -dns-server 1.1.1.1,8.8.8.8 \
    -no-snapshot-load > "$RESULT_DIR/emulator.log" 2>&1 &
  printf '%s\n' "$!" > "$RESULT_DIR/emulator.pid"
fi
"$ADB" -s "$SERIAL" wait-for-device
booted=""
for _ in $(seq 1 240); do
  if [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" = "1" ]; then booted=yes; break; fi
  sleep 2
done
[ "$booted" = yes ] || fail "Google TV emulator did not finish booting"
"$ADB" -s "$SERIAL" shell pm list features | grep -q 'android.software.leanback' || fail "Selected emulator is not a television target"
EMULATOR_STATUS="verified:$SERIAL"

section "Installing RC2 in place and opening it now"
"$ADB" -s "$SERIAL" install -r -d "$REVIEW_APK"
"$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE_ID"
launch="$($ADB -s "$SERIAL" shell am start -W -n "$PACKAGE_ID/.MainActivity" 2>&1 || true)"
printf '%s\n' "$launch" | tee "$RESULT_DIR/launch.txt"
sleep 3
focused="$($ADB -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 'mResumedActivity' || true)"
if ! grep -Eq 'in\.ghartv\.nova/\.(LoginActivity|MainActivity|PlayerActivity)' <<<"$focused"; then
  "$ADB" -s "$SERIAL" shell pidof "$PACKAGE_ID" >/dev/null 2>&1 || fail "GharTV installed but its process is not active"
fi
"$ADB" -s "$SERIAL" exec-out screencap -p > "$RESULT_DIR/GharTV-v0.5.4-RC2.png" 2>/dev/null || true
"$ADB" -s "$SERIAL" shell uiautomator dump /sdcard/ghartv-v054-rc2.xml >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" pull /sdcard/ghartv-v054-rc2.xml "$RESULT_DIR/emulator-ui.xml" >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" logcat -d -v threadtime > "$RESULT_DIR/emulator-logcat.txt" 2>/dev/null || true
"$ADB" -s "$SERIAL" shell dumpsys package "$PACKAGE_ID" | grep -E 'versionCode|versionName' > "$RESULT_DIR/emulator-version.txt" || true

section "Collecting current owner logs and telemetry"
if [ -x "$PROJECT/GHARTV_LOGS_AND_HEALTH.command" ]; then
  before="$(find "$HOME/Desktop" -maxdepth 1 -type d -name 'GharTV-Owner-Report-*' -print 2>/dev/null | sort | tail -1 || true)"
  bash "$PROJECT/GHARTV_LOGS_AND_HEALTH.command" || true
  after="$(find "$HOME/Desktop" -maxdepth 1 -type d -name 'GharTV-Owner-Report-*' -print 2>/dev/null | sort | tail -1 || true)"
  [ "$after" != "$before" ] && OWNER_REPORT="$after"
fi

write_receipt "REVIEW_READY" ""
trap - ERR
section "GharTV v0.5.4 RC2 is open for owner review"
printf 'Candidate SHA:     %s\n' "$CANDIDATE_SHA"
printf 'Signed APK SHA:    %s\n' "$APK_SHA"
printf 'Emulator:          %s\n' "$SERIAL"
printf 'GitHub prerelease: %s\n' "$RELEASE_URL"
printf 'Owner report:      %s\n' "${OWNER_REPORT:-not generated}"
printf '\nReview with the TV remote or Mac arrow keys. Stable televisions have not been updated.\n'
printf 'Publish after approval: bash "%s/GHARTV_PUBLISH_STABLE_V054.command" publish\n' "$PROJECT"
printf 'Close review later:     bash "%s/GHARTV_CLOSE_REVIEW.command"\n' "$PROJECT"
printf 'Check logs later:       bash "%s/GHARTV_LOGS_AND_HEALTH.command"\n' "$PROJECT"
read -r -p "Press Enter to copy the review handoff… " _
command -v pbcopy >/dev/null 2>&1 && pbcopy < "$RUN_DIR/PASTE_TO_CHAT.txt" || true
read -r -p "Handoff copied. Press Enter to close this terminal while leaving the emulator open… " _
