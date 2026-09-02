#!/bin/bash
# GharTV Jio Live v0.5.3 — opt-in diagnostics, collector deployment,
# existing-TV test, canonical main publication and in-place update request.
set -Eeuo pipefail

VERSION="0.5.3"
VERSION_NAME="0.5.3-observability"
VERSION_CODE="10"
LANE_ID="ghartv"
ENTITY_TYPE="project"
PROJECT_DIR="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
ANDROID_PROJECT="$PROJECT_DIR/android-tv"
PACKAGE_ID="in.ghartv.nova"
REPOSITORY="AmritSinghGit/ghartv"
REPO_URL="https://github.com/$REPOSITORY"
ORIGIN_HTTPS="$REPO_URL.git"
ORIGIN_SSH="git@github.com:$REPOSITORY.git"
RELEASE_TAG="v0.5.3"
INSTALL_PAGE="https://amritsinghgit.github.io/ghartv/"
DIRECT_APK_URL="$REPO_URL/releases/latest/download/GharTV-Jio-Live.apk"
UPDATE_MANIFEST_URL="https://raw.githubusercontent.com/AmritSinghGit/ghartv/main/update/latest.json"
AVD_NAME="${GHARTV_AVD_NAME:-GharTV_Nova_Manual_google_tv_API36}"
CACHE_DIR="${GHARTV_CACHE_DIR:-$HOME/Library/Caches/GharTV-Nova}"
SIGNING_DIR="$HOME/Library/Application Support/GharTV/signing"
KEYSTORE="$SIGNING_DIR/ghartv-release.jks"
SIGNING_ENV="$SIGNING_DIR/signing.env"
TELEMETRY_SUPPORT="$HOME/Library/Application Support/GharTV/telemetry"
COLLECTOR_ENV="$TELEMETRY_SUPPORT/collector.env"
WORKER_NAME="ghartv-telemetry"
D1_NAME="ghartv-telemetry"
WRANGLER_VERSION="4.119.0"
BASELINE_SHA="4f76802bce49cdf417b787461a8398a0643e5006"
EXPECTED_PAYLOAD_SHA="99bf0d41fa32e7418e56a77f65c9512421bd4a6596657edfa44fa5de7f4f3bdb"
MODE="${1:-run}"

case "$MODE" in
  run|publish|test|--verify-package) ;;
  *) printf 'Usage: %s [run|publish|test|--verify-package]\n' "$(basename "$0")" >&2; exit 2 ;;
esac

STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_ID="ghartv-v053-$STAMP-$$"
RESULT_DIR="${GHARTV_RESULTS_DIR:-$HOME/Desktop/GharTV-v0.5.3-$STAMP}"
RUN_DIR="$HOME/.local/state/operon-terminal-runs/$LANE_ID/$RUN_ID"
SETUP_LOG="$RESULT_DIR/setup.log"
TRANSCRIPT="$RUN_DIR/full-transcript.log"
PAYLOAD_ZIP=""
PAYLOAD_DIR=""
PAYLOAD_ROOT=""
WORKER_DIR=""
JAVA_HOME_SELECTED=""
SDK_ROOT=""
ADB=""
EMULATOR=""
GRADLE=""
SERIAL=""
REMOTE_BASE=""
LOCAL_BASE=""
SOURCE_COMMIT_SHA=""
COMMIT_SHA=""
RELEASE_APK=""
RELEASE_SHA=""
EMULATOR_STATUS="not-attempted"
PUBLISHED="no"
UPDATE_REQUEST_SENT="no"
LAST_ERROR=""
CONTINUITY_DONE=0
TELEMETRY_ENDPOINT=""
TELEMETRY_INGEST_KEY=""
TELEMETRY_ADMIN_TOKEN=""
TELEMETRY_D1_ID=""
TELEMETRY_DATABASE_ACTION="not-attempted"
COLLECTOR_STATUS="not-attempted"
CLOUDFLARE_SECRETS_FILE="$RUN_DIR/cloudflare-worker-secrets.json"
ADMIN_CURL_CONFIG="$RUN_DIR/admin-curl.conf"

mkdir -p "$RESULT_DIR" "$RUN_DIR" "$CACHE_DIR" "$TELEMETRY_SUPPORT"
chmod 700 "$RUN_DIR" "$TELEMETRY_SUPPORT" 2>/dev/null || true
exec > >(tee -a "$SETUP_LOG" "$TRANSCRIPT") 2>&1
printf '\033]0;%s\007' "GharTV · ghartv · v0.5.3 observability update"

step(){ printf '\n============================================================\n%s\n============================================================\n' "$1"; }
note(){ printf '%s\n' "$1"; }
fail(){ LAST_ERROR="$1"; printf '\nERROR: %s\nEvidence: %s\n' "$1" "$RESULT_DIR" >&2; exit 1; }
sha256_of(){ if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'; else sha256sum "$1" | awk '{print $1}'; fi; }
random_hex(){ if command -v openssl >/dev/null 2>&1; then openssl rand -hex 32; else python3 -c 'import secrets;print(secrets.token_hex(32))'; fi; }

sanitize_transcript(){
  python3 - "$1" "$2" <<'PY'
from pathlib import Path
import re,sys
src,dst=map(Path,sys.argv[1:3])
text=src.read_text(encoding='utf-8',errors='replace') if src.exists() else ''
patterns=[
 (re.compile(r'(?i)(otp|one[- ]time password)(\s*[:=]\s*)\d{4,8}'),r'\1\2[REDACTED]'),
 (re.compile(r'(?i)(password|passcode|secret|admin[_ -]?token|authorization|cookie)(\s*[:=]\s*)[^\s]+'),r'\1\2[REDACTED]'),
 (re.compile(r'(?<!\d)(?:\+?91[- ]?)?[6-9]\d{9}(?!\d)'),'[REDACTED_MOBILE]'),
 (re.compile(r'(?i)(authtoken|ssotoken|refreshtoken)"?\s*[:=]\s*"?[^",\s]+'),r'\1=[REDACTED]'),
]
for pattern,replacement in patterns: text=pattern.sub(replacement,text)
dst.write_text(text,encoding='utf-8')
PY
}

write_continuity_receipt(){
  local status="$1" summary="$2" next_action="$3" blocker="${4:-}"
  [ "$CONTINUITY_DONE" -eq 0 ] || return 0
  CONTINUITY_DONE=1
  local finished sanitized receipt paste vault note_dir note_file handoff_dir handoff_file memory_status
  finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  sanitized="$RUN_DIR/sanitized-transcript.log"
  receipt="$RUN_DIR/receipt.json"
  paste="$RUN_DIR/PASTE_TO_CHAT.txt"
  sanitize_transcript "$TRANSCRIPT" "$sanitized"
  python3 - "$receipt" "$status" "$summary" "$next_action" "$blocker" \
    "$REMOTE_BASE" "$COMMIT_SHA" "$RELEASE_SHA" "$EMULATOR_STATUS" "$finished" \
    "$TELEMETRY_ENDPOINT" "$TELEMETRY_DATABASE_ACTION" "$COLLECTOR_STATUS" "$UPDATE_REQUEST_SENT" <<'PY'
from pathlib import Path
import json,sys
(path,status,summary,next_action,blocker,base,head,apk_sha,emulator,finished,endpoint,db_action,collector,update_sent)=sys.argv[1:]
data={
 "schema":"ghartv.continuity.receipt.v1",
 "lane_id":"ghartv",
 "entity_type":"project",
 "project_path":"~/Downloads/GharTV_Nova_v0.4.2",
 "repository":"AmritSinghGit/ghartv",
 "branch":"main",
 "package_id":"in.ghartv.nova",
 "version":"0.5.3-observability",
 "version_code":10,
 "status":status,
 "summary":summary,
 "next_action":next_action,
 "blocker":blocker,
 "base_sha":base,
 "head_sha":head,
 "apk_sha256":apk_sha,
 "emulator_status":emulator,
 "telemetry_endpoint":endpoint,
 "telemetry_database_action":db_action,
 "collector_status":collector,
 "update_request_sent":update_sent,
 "finished_at":finished,
 "new_project_created":False,
 "new_branch_created":False,
 "new_worktree_created":False,
 "new_lane_created":False,
 "purpose_built_telemetry_store": db_action in ("created","reused"),
 "telemetry_consent":"explicit opt-in; off until accepted on the TV",
}
Path(path).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
PY

  vault="$HOME/Documents/Amrit Executive Memory"
  note_file=""
  memory_status="not-attempted"
  if [ -d "$vault" ]; then
    note_dir="$vault/90 System/Operon Portfolio/Handoffs/Terminal Runs/$LANE_ID"
    mkdir -p "$note_dir"
    note_file="$note_dir/$RUN_ID.md"
    cat > "$note_file" <<NOTE
---
type: terminal-run-handoff
lane_id: ghartv
entity_type: project
version: 0.5.3-observability
status: $status
repository: AmritSinghGit/ghartv
branch: main
package_id: in.ghartv.nova
created: $finished
---

# GharTV v0.5.3 — observability and update continuation

## Summary

$summary

## Privacy contract

Diagnostics remain off until a person on the TV explicitly opts in. GharTV excludes the Jio mobile number, OTP, passwords, account identifiers, Jio tokens/cookies, request headers, stream/manifest/licence URLs, successful channel identifiers/names, programme titles, IP address, Wi-Fi name, MAC address, Android ID, advertising ID and TV serial number. A failed Jio channel ID may be included only when that channel fails.

## Current truth

- Existing local path: ~/Downloads/GharTV_Nova_v0.4.2
- Existing lane: ghartv
- Entity type: independent Operon project, not tenant
- Repository: AmritSinghGit/ghartv
- Branch: main
- Package: in.ghartv.nova
- Version: 0.5.3-observability / code 10
- Base: ${REMOTE_BASE:-not-recorded}
- Head: ${COMMIT_SHA:-not-recorded}
- APK SHA-256: ${RELEASE_SHA:-not-recorded}
- Existing emulator: $EMULATOR_STATUS
- Collector: $COLLECTOR_STATUS
- Collector endpoint: ${TELEMETRY_ENDPOINT:-not-recorded}
- Telemetry store: $TELEMETRY_DATABASE_ACTION
- Update request sent: $UPDATE_REQUEST_SENT

## Next action

$next_action

## Blocker

${blocker:-None recorded.}

## Preservation

No replacement GharTV project, branch, worktree, package identity, repository, Operon tenant or continuity lane was created. The one dedicated diagnostics store is purpose-built for the owner-requested v0.5.3 telemetry and is not a duplicate GharTV business database. Cloudflare/GitHub credentials, telemetry admin token, public ingest token, Jio data and signing credentials are not written into this receipt.

## Evidence

- Setup log: $SETUP_LOG
- Sanitized transcript: $sanitized
- Machine receipt: $receipt
- Results directory: $RESULT_DIR
NOTE
    chmod 600 "$note_file" 2>/dev/null || true
    handoff_dir="$HOME/Documents/Operon-HQ/chat-handoffs"
    mkdir -p "$handoff_dir"
    handoff_file="$handoff_dir/GHARTV_V053_${RUN_ID}.json"
    python3 - "$handoff_file" "$status" "$summary" "$next_action" "$blocker" "$note_file" "$COMMIT_SHA" "$RELEASE_SHA" "$TELEMETRY_ENDPOINT" "$UPDATE_REQUEST_SENT" <<'PY'
from pathlib import Path
import json,sys
(path,status,summary,next_action,blocker,note,head,apk_sha,endpoint,update_sent)=sys.argv[1:]
payload={
 "schema":"amrit.context-mesh.handoff.v1",
 "handoff_key":"ghartv-v053-observability",
 "project_key":"ghartv",
 "chat_key":"iptv-app-for-tv",
 "title":"GharTV v0.5.3 Observability and Update Continuation",
 "summary":summary,
 "current_truth":f"GharTV project, repo AmritSinghGit/ghartv, main, package in.ghartv.nova, v0.5.3-observability, head {head or 'pending'}, APK {apk_sha or 'pending'}, collector {endpoint or 'pending'}, update request {update_sent}, status {status}.",
 "next_action":next_action,
 "body_markdown":Path(note).read_text(encoding='utf-8') if note else summary,
}
Path(path).write_text(json.dumps(payload,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
PY
    chmod 600 "$handoff_file" 2>/dev/null || true
    if [ -x "$HOME/bin/amrit-context" ]; then
      if "$HOME/bin/amrit-context" handoff --file "$handoff_file" > "$RUN_DIR/memory-handoff.txt" 2>&1; then
        "$HOME/bin/amrit-context" sync-once > "$RUN_DIR/memory-sync.txt" 2>&1 || true
        memory_status="indexed-by-amrit-context"
      else
        memory_status="amrit-context-handoff-failed"
      fi
    else
      memory_status="amrit-context-not-found-note-written"
    fi
  fi

  cat > "$paste" <<PASTE
GHARTV_V053_CONTINUITY_RECEIPT
LANE_ID=ghartv
ENTITY_TYPE=project
REPOSITORY=AmritSinghGit/ghartv
BRANCH=main
PACKAGE=in.ghartv.nova
VERSION=0.5.3-observability
STATUS=$status
BASE_SHA=${REMOTE_BASE:-}
HEAD_SHA=${COMMIT_SHA:-}
APK_SHA256=${RELEASE_SHA:-}
EMULATOR_STATUS=$EMULATOR_STATUS
COLLECTOR_STATUS=$COLLECTOR_STATUS
TELEMETRY_ENDPOINT=${TELEMETRY_ENDPOINT:-}
TELEMETRY_STORE=$TELEMETRY_DATABASE_ACTION
UPDATE_REQUEST_SENT=$UPDATE_REQUEST_SENT
SUMMARY=$summary
NEXT_ACTION=$next_action
BLOCKER=${blocker:-}
PASTE
  command -v pbcopy >/dev/null 2>&1 && pbcopy < "$paste" || true
  note "Continuity receipt: $paste"
  [ -n "$note_file" ] && note "Obsidian lane receipt: $note_file"
  note "Memory handoff: $memory_status"
}

cleanup(){
  [ -n "$PAYLOAD_ZIP" ] && rm -f "$PAYLOAD_ZIP" 2>/dev/null || true
  [ -n "$PAYLOAD_DIR" ] && rm -rf "$PAYLOAD_DIR" 2>/dev/null || true
  rm -f "$CLOUDFLARE_SECRETS_FILE" "$ADMIN_CURL_CONFIG" 2>/dev/null || true
}

on_exit(){
  local rc=$?
  trap - EXIT
  cleanup
  if [ "$rc" -ne 0 ] && [ "$CONTINUITY_DONE" -eq 0 ]; then
    write_continuity_receipt "FAILED" \
      "GharTV v0.5.3 stopped before completing the opt-in diagnostics update." \
      "Review the setup log and rerun this same command against the existing ghartv/main/package implementation; do not create another project, branch, worktree or lane." \
      "${LAST_ERROR:-Command exited with status $rc.}"
  fi
  exit "$rc"
}
trap on_exit EXIT

extract_payload(){
  PAYLOAD_ZIP="$(mktemp "${TMPDIR:-/tmp}/ghartv-v053.XXXXXX.zip")"
  PAYLOAD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ghartv-v053.XXXXXX")"
  python3 - "$0" "$PAYLOAD_ZIP" <<'PY'
from pathlib import Path
import base64,sys
source=Path(sys.argv[1]).read_bytes()
marker=b'\n__GHARTV_V053_PAYLOAD__\n'
if marker not in source: raise SystemExit('Embedded GharTV v0.5.3 payload marker is missing')
payload=b''.join(source.split(marker,1)[1].split())
Path(sys.argv[2]).write_bytes(base64.b64decode(payload,validate=True))
PY
  local actual
  actual="$(sha256_of "$PAYLOAD_ZIP")"
  [ "$actual" = "$EXPECTED_PAYLOAD_SHA" ] || fail "Embedded payload checksum mismatch: $actual"
  unzip -q "$PAYLOAD_ZIP" -d "$PAYLOAD_DIR"
  PAYLOAD_ROOT="$PAYLOAD_DIR/GharTV_Jio_Live_v0.5.3_observability"
  WORKER_DIR="$PAYLOAD_ROOT/telemetry/worker"
  [ -d "$PAYLOAD_ROOT" ] || fail "Embedded source overlay is missing"
  [ -f "$PAYLOAD_ROOT/apply_v053.py" ] || fail "v0.5.3 patcher is missing"
  [ -f "$WORKER_DIR/src/index.js" ] || fail "telemetry collector source is missing"
  python3 -m py_compile "$PAYLOAD_ROOT/apply_v053.py"
  bash -n "$PAYLOAD_ROOT/GHARTV_TELEMETRY_REPORT.command"
  if command -v node >/dev/null 2>&1; then node --check "$WORKER_DIR/src/index.js" >/dev/null; fi
  [ ! -f "$WORKER_DIR/wrangler.toml" ] || fail "Embedded source unexpectedly contains an account-specific wrangler.toml"
  grep -Fq '__D1_DATABASE_ID__' "$WORKER_DIR/wrangler.toml.template" || fail "D1 template placeholder is missing"
  note "Embedded v0.5.3 overlay verified: $actual"
}

allowed_v053_path(){
  case "$1" in
    .ghartv-owner-state.json|.gitignore|README.md|CHANGELOG.md|PRIVACY.md|TELEMETRY.md|RELEASE_MANIFEST.json|VALIDATE_SOURCE.command|GHARTV_TELEMETRY_REPORT.command|GHARTV_V053_OBSERVABILITY_BUILD_TEST_AND_PUBLISH.command|OBSERVABILITY_ACCEPTANCE_v0.5.3.md|release-notes-v0.5.3.md|docs/index.html|docs/privacy.html|update/latest.json|update/telemetry.json|telemetry/worker/*|android-tv/app/build.gradle.kts|android-tv/app/src/main/java/in/ghartv/nova/AppConfig.java|android-tv/app/src/main/java/in/ghartv/nova/NovaApp.java|android-tv/app/src/main/java/in/ghartv/nova/LoginActivity.java|android-tv/app/src/main/java/in/ghartv/nova/MainActivity.java|android-tv/app/src/main/java/in/ghartv/nova/PlayerActivity.java|android-tv/app/src/main/java/in/ghartv/nova/UpdateManager.java|android-tv/app/src/main/java/in/ghartv/nova/ChannelRefreshWorker.java|android-tv/app/src/main/java/in/ghartv/nova/Telemetry.java|android-tv/app/src/main/java/in/ghartv/nova/TelemetryUploadWorker.java|android-tv/app/src/main/java/in/ghartv/nova/DiagnosticsDialog.java) return 0 ;;
    *) return 1 ;;
  esac
}

assert_only_v053_changes(){
  local path bad=0
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    path="${path#* -> }"
    if ! allowed_v053_path "$path"; then printf 'Unexpected changed path: %s\n' "$path" >&2; bad=1; fi
  done < <(git -C "$PROJECT_DIR" status --porcelain --untracked-files=all | sed 's/^...//')
  [ "$bad" -eq 0 ] || fail "The existing checkout contains changes outside the bounded v0.5.3 paths"
}

verify_existing_authority(){
  step "Verifying the existing GharTV authority — no duplicate Git state"
  [ "$(uname -s)" = "Darwin" ] || fail "Live execution is supported on the existing macOS development environment only"
  [ -d "$PROJECT_DIR/.git" ] || fail "Existing Git checkout not found at $PROJECT_DIR; refusing to create another project"
  [ -f "$ANDROID_PROJECT/app/build.gradle.kts" ] || fail "Existing Android project is incomplete"
  grep -Fq 'applicationId = "in.ghartv.nova"' "$ANDROID_PROJECT/app/build.gradle.kts" || fail "Package identity changed"
  local branch origin dirty relation current_code
  branch="$(git -C "$PROJECT_DIR" symbolic-ref --short HEAD 2>/dev/null || true)"
  [ "$branch" = "main" ] || fail "Existing checkout is on '$branch'; no branch will be created or switched"
  origin="$(git -C "$PROJECT_DIR" remote get-url origin 2>/dev/null || true)"
  [ "$origin" = "$ORIGIN_HTTPS" ] || [ "$origin" = "$ORIGIN_SSH" ] || fail "Existing origin is not $REPOSITORY: $origin"
  dirty="$(git -C "$PROJECT_DIR" status --porcelain --untracked-files=all)"
  [ -z "$dirty" ] || assert_only_v053_changes
  git -C "$PROJECT_DIR" fetch --no-tags origin main
  REMOTE_BASE="$(git -C "$PROJECT_DIR" rev-parse origin/main)"
  LOCAL_BASE="$(git -C "$PROJECT_DIR" rev-parse HEAD)"
  git -C "$PROJECT_DIR" merge-base --is-ancestor "$BASELINE_SHA" "$REMOTE_BASE" || fail "Remote main no longer descends from the accepted v0.5.2 baseline"
  if [ "$LOCAL_BASE" != "$REMOTE_BASE" ]; then
    if git -C "$PROJECT_DIR" merge-base --is-ancestor "$LOCAL_BASE" "$REMOTE_BASE"; then
      [ -z "$dirty" ] || fail "Remote main is ahead while bounded local v0.5.3 changes exist; stopped without overwriting them"
      git -C "$PROJECT_DIR" pull --ff-only origin main
      LOCAL_BASE="$(git -C "$PROJECT_DIR" rev-parse HEAD)"
    elif git -C "$PROJECT_DIR" merge-base --is-ancestor "$REMOTE_BASE" "$LOCAL_BASE"; then
      local path bad=0
      while IFS= read -r path; do
        allowed_v053_path "$path" || { printf 'Unexpected local-ahead path: %s\n' "$path" >&2; bad=1; }
      done < <(git -C "$PROJECT_DIR" diff --name-only "$REMOTE_BASE..$LOCAL_BASE")
      [ "$bad" -eq 0 ] || fail "Local main contains non-v0.5.3 commits not present on GitHub"
    else
      fail "Local and remote main have diverged; stopped without merge, rebase or reset"
    fi
  fi
  current_code="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\).*/\1/p' "$ANDROID_PROJECT/app/build.gradle.kts" | head -1)"
  [ -n "$current_code" ] || fail "Could not read existing versionCode"
  [ "$current_code" -le "$VERSION_CODE" ] || fail "A newer GharTV versionCode $current_code already exists"
  note "Remote base: $REMOTE_BASE"
  note "Local head:  $LOCAL_BASE"
  note "Repository: $REPOSITORY"
  note "Branch/package/lane retained: main · $PACKAGE_ID · $LANE_ID"
}

ensure_node_and_cloudflare_auth(){
  step "Connecting the first-party GharTV diagnostics collector"
  if ! command -v node >/dev/null 2>&1 || ! command -v npx >/dev/null 2>&1; then
    command -v brew >/dev/null 2>&1 || fail "Node.js/npx are missing and Homebrew is unavailable"
    brew install node
  fi
  node --version
  if ! wrangler whoami --json > "$RESULT_DIR/cloudflare-whoami.json" 2>/dev/null; then
    note "A Cloudflare authorization page will open. Sign in or create the owner account, approve Wrangler, then return to Terminal."
    if ! wrangler login --use-keyring; then wrangler login; fi
    wrangler whoami --json > "$RESULT_DIR/cloudflare-whoami.json"
  fi
  note "Cloudflare authorization verified. Credentials are not copied into GharTV or Git."
}

wrangler(){
  (cd "$WORKER_DIR" && WRANGLER_SEND_METRICS=false npx --yes "wrangler@$WRANGLER_VERSION" "$@")
}

read_existing_collector_config(){
  if [ -f "$COLLECTOR_ENV" ]; then
    # shellcheck disable=SC1090
    source "$COLLECTOR_ENV"
    TELEMETRY_ENDPOINT="${GHARTV_TELEMETRY_ENDPOINT:-}"
    TELEMETRY_INGEST_KEY="${GHARTV_TELEMETRY_INGEST_KEY:-}"
    TELEMETRY_ADMIN_TOKEN="${GHARTV_TELEMETRY_ADMIN_TOKEN:-}"
    TELEMETRY_D1_ID="${GHARTV_TELEMETRY_D1_ID:-}"
  fi
  [ -n "$TELEMETRY_INGEST_KEY" ] || TELEMETRY_INGEST_KEY="$(random_hex)"
  [ -n "$TELEMETRY_ADMIN_TOKEN" ] || TELEMETRY_ADMIN_TOKEN="$(random_hex)"
  [[ "$TELEMETRY_INGEST_KEY" =~ ^[A-Fa-f0-9]{64}$ ]] || fail "Existing public ingest token has an unexpected format"
  [[ "$TELEMETRY_ADMIN_TOKEN" =~ ^[A-Fa-f0-9]{64}$ ]] || fail "Existing telemetry admin token has an unexpected format"
}

parse_d1_id(){
  python3 - "$1" "$D1_NAME" <<'PY'
from pathlib import Path
import json,sys
p=Path(sys.argv[1]); name=sys.argv[2]
try: data=json.loads(p.read_text(encoding='utf-8'))
except Exception: raise SystemExit(1)
if isinstance(data,dict):
    items=data.get('result') or data.get('databases') or data.get('d1_databases') or []
else: items=data
for item in items or []:
    if str(item.get('name') or item.get('database_name') or '')==name:
        value=item.get('uuid') or item.get('database_id') or item.get('id')
        if value: print(value); raise SystemExit(0)
raise SystemExit(1)
PY
}

write_wrangler_config(){
  python3 - "$WORKER_DIR/wrangler.toml.template" "$WORKER_DIR/wrangler.toml" "$TELEMETRY_D1_ID" <<'PY'
from pathlib import Path
import sys
src,dst,db_id=sys.argv[1:]
text=Path(src).read_text(encoding='utf-8')
if '__D1_DATABASE_ID__' not in text: raise SystemExit('D1 placeholder missing')
Path(dst).write_text(text.replace('__D1_DATABASE_ID__',db_id),encoding='utf-8')
PY
}

write_collector_env(){
  python3 - "$COLLECTOR_ENV" "$TELEMETRY_ENDPOINT" "$TELEMETRY_INGEST_KEY" "$TELEMETRY_ADMIN_TOKEN" "$TELEMETRY_D1_ID" <<'PY'
from pathlib import Path
import shlex,sys
path=Path(sys.argv[1]); endpoint,ingest,admin,db_id=sys.argv[2:]
values={
 'GHARTV_TELEMETRY_ENDPOINT':endpoint,
 'GHARTV_TELEMETRY_INGEST_KEY':ingest,
 'GHARTV_TELEMETRY_ADMIN_TOKEN':admin,
 'GHARTV_TELEMETRY_D1_ID':db_id,
 'GHARTV_TELEMETRY_D1_NAME':'ghartv-telemetry',
 'GHARTV_TELEMETRY_WORKER':'ghartv-telemetry',
}
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(''.join(f'{k}={shlex.quote(v)}\n' for k,v in values.items()),encoding='utf-8')
path.chmod(0o600)
PY
}

deploy_telemetry_collector(){
  ensure_node_and_cloudflare_auth
  read_existing_collector_config
  local list_json create_log secret_json deploy_log health_json test_batch test_response admin_summary candidate
  list_json="$RESULT_DIR/cloudflare-d1-list.json"
  wrangler d1 list --json > "$list_json"
  TELEMETRY_D1_ID="$(parse_d1_id "$list_json" 2>/dev/null || true)"
  if [ -z "$TELEMETRY_D1_ID" ]; then
    step "Creating the single purpose-built GharTV diagnostics store"
    create_log="$RESULT_DIR/cloudflare-d1-create.txt"
    wrangler d1 create "$D1_NAME" --location=apac > "$create_log" 2>&1
    TELEMETRY_DATABASE_ACTION="created"
    for _ in $(seq 1 20); do
      wrangler d1 list --json > "$list_json"
      TELEMETRY_D1_ID="$(parse_d1_id "$list_json" 2>/dev/null || true)"
      [ -n "$TELEMETRY_D1_ID" ] && break
      sleep 3
    done
    [ -n "$TELEMETRY_D1_ID" ] || fail "Cloudflare created the D1 database but its ID could not be resolved"
  else
    TELEMETRY_DATABASE_ACTION="reused"
    note "Reusing existing D1 diagnostics store: $D1_NAME"
  fi

  write_wrangler_config
  wrangler d1 execute "$D1_NAME" --remote --file="$WORKER_DIR/schema.sql" --yes --config "$WORKER_DIR/wrangler.toml" > "$RESULT_DIR/cloudflare-schema.txt"

  secret_json="$CLOUDFLARE_SECRETS_FILE"
  python3 - "$secret_json" "$TELEMETRY_INGEST_KEY" "$TELEMETRY_ADMIN_TOKEN" <<'PY'
from pathlib import Path
import json,sys
Path(sys.argv[1]).write_text(json.dumps({'INGEST_KEY':sys.argv[2],'ADMIN_TOKEN':sys.argv[3]}),encoding='utf-8')
Path(sys.argv[1]).chmod(0o600)
PY
  deploy_log="$RESULT_DIR/cloudflare-deploy.txt"
  wrangler deploy --config "$WORKER_DIR/wrangler.toml" --secrets-file "$secret_json" --keep-vars --message "GharTV v0.5.3 privacy-filtered diagnostics" 2>&1 | tee "$deploy_log"
  rm -f "$secret_json"
  candidate="$(grep -Eo 'https://[A-Za-z0-9._-]+\.workers\.dev' "$deploy_log" | tail -1 || true)"
  [ -n "$candidate" ] && TELEMETRY_ENDPOINT="$candidate"
  [ -n "$TELEMETRY_ENDPOINT" ] || fail "Worker deployed but its workers.dev endpoint could not be determined"
  TELEMETRY_ENDPOINT="${TELEMETRY_ENDPOINT%/}"

  # Persist the exact collector keys before public-route verification so an
  # interrupted continuation reuses the same keys rather than losing them.
  write_collector_env

  local worker_route_json worker_route_status health_headers health_error health_status health_ready attempt
  worker_route_json="$RESULT_DIR/cloudflare-worker-subdomain.json"
  worker_route_status="$(curl -sS --connect-timeout 10 --max-time 30 \
    -o "$worker_route_json" -w '%{http_code}' \
    -X POST \
    -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
    -H 'Content-Type: application/json' \
    --data '{"enabled":true,"previews_enabled":false}' \
    "https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/workers/scripts/$WORKER_NAME/subdomain" 2>"$RESULT_DIR/cloudflare-worker-subdomain.err" || true)"

  if [ "$worker_route_status" = "200" ] && python3 - "$worker_route_json" <<'PY_ROUTE'
from pathlib import Path
import json, sys
try:
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
except Exception:
    raise SystemExit(1)
result = data.get("result") or {}
assert data.get("success") is True and result.get("enabled") is True
PY_ROUTE
  then
    note "workers.dev route explicitly enabled for $WORKER_NAME."
  else
    note "WARNING: Could not independently confirm the worker-level route (HTTP ${worker_route_status:-unknown}); continuing with deployment and public health verification."
  fi

  health_json="$RESULT_DIR/collector-health.json"
  health_headers="$RESULT_DIR/collector-health.headers"
  health_error="$RESULT_DIR/collector-health.err"
  health_ready=0
  note "Waiting for valid collector health JSON on the production workers.dev route…"
  for attempt in $(seq 1 120); do
    : > "$health_json"
    : > "$health_headers"
    : > "$health_error"
    health_status="$(curl -sS --connect-timeout 10 --max-time 25 \
      -o "$health_json" -D "$health_headers" -w '%{http_code}' \
      "$TELEMETRY_ENDPOINT/health" 2>"$health_error" || true)"
    if [ "$health_status" = "200" ] && python3 - "$health_json" <<'PY_HEALTH'
from pathlib import Path
import json, sys
p = Path(sys.argv[1])
if not p.exists() or p.stat().st_size == 0:
    raise SystemExit(1)
try:
    data = json.loads(p.read_text(encoding="utf-8"))
except Exception:
    raise SystemExit(1)
assert data.get("ok") is True and data.get("service") == "ghartv-telemetry"
PY_HEALTH
    then
      health_ready=1
      break
    fi
    if [ $((attempt % 6)) -eq 1 ]; then
      note "Still waiting for collector route: attempt $attempt/120, HTTP ${health_status:-000}."
      if [ -s "$health_error" ]; then tail -n 2 "$health_error" | sed 's/^/  /'; fi
    fi
    sleep 5
  done

  if [ "$health_ready" -ne 1 ]; then
    note "Last health response headers:"
    tail -n 20 "$health_headers" 2>/dev/null || true
    note "Last health response body:"
    head -c 1000 "$health_json" 2>/dev/null || true
    printf '\n'
    note "Last curl error:"
    tail -n 20 "$health_error" 2>/dev/null || true
    fail "The existing Worker was deployed, but its public health route did not become usable within 10 minutes. No app source, APK release, or update request was published."
  fi
  note "Collector health verified: $TELEMETRY_ENDPOINT/health"

  test_batch="$RUN_DIR/collector-test.json"
  test_response="$RESULT_DIR/collector-test-response.json"
  python3 - "$test_batch" <<'PY'
from pathlib import Path
import json,sys,time,uuid
now=int(time.time()*1000)
payload={
 'schema':'ghartv.telemetry.batch.v1','sent_at':now,
 'install_hash':'0123456789abcdef0123456789abcdef','session_id':'collector-test',
 'app_version':'0.5.3-observability','version_code':10,
 'device':{'manufacturer':'test','model':'collector-preflight','android_release':'test','android_api':0,'locale':'en-IN','network':'test'},
 'events':[{'id':str(uuid.uuid4()),'reference':'GH-COLLECT','timestamp':now,'name':'collector_test','screen':'preflight','attributes':{'result':'success'}}]
}
Path(sys.argv[1]).write_text(json.dumps(payload),encoding='utf-8')
PY
  curl -fsS --max-time 30 -H 'Content-Type: application/json' \
    -H "X-GharTV-Ingest-Key: $TELEMETRY_INGEST_KEY" \
    --data-binary "@$test_batch" "$TELEMETRY_ENDPOINT/v1/events" > "$test_response"
  python3 - "$test_response" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text())
assert d.get('ok') is True and int(d.get('accepted',0))==1
PY
  admin_summary="$RESULT_DIR/collector-admin-summary.json"
  printf 'header = "Authorization: Bearer %s"\n' "$TELEMETRY_ADMIN_TOKEN" > "$ADMIN_CURL_CONFIG"
  chmod 600 "$ADMIN_CURL_CONFIG"
  curl -fsS --max-time 30 --config "$ADMIN_CURL_CONFIG" \
    "$TELEMETRY_ENDPOINT/v1/admin/summary?days=1" > "$admin_summary"
  rm -f "$ADMIN_CURL_CONFIG"
  python3 - "$admin_summary" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d.get('ok') is True
PY
  wrangler d1 execute "$D1_NAME" --remote --command="DELETE FROM telemetry_events WHERE event_name='collector_test';" --yes --config "$WORKER_DIR/wrangler.toml" > "$RESULT_DIR/cloudflare-test-cleanup.txt"
  rm -f "$test_batch"
  write_collector_env
  COLLECTOR_STATUS="deployed-and-verified"
  note "Collector endpoint: $TELEMETRY_ENDPOINT"
  note "D1 diagnostics store: $D1_NAME ($TELEMETRY_DATABASE_ACTION)"
  note "Owner report credentials: $COLLECTOR_ENV (mode 600; never committed)"
}

apply_v053_source(){
  step "Applying v0.5.3 to the exact existing GharTV project"
  [ -f "$WORKER_DIR/wrangler.toml" ] || fail "Account-specific Worker configuration was not generated"
  python3 "$PAYLOAD_ROOT/apply_v053.py" "$PROJECT_DIR" "$PAYLOAD_ROOT" "$TELEMETRY_ENDPOINT" "$TELEMETRY_INGEST_KEY"
  cp "$0" "$PROJECT_DIR/GHARTV_V053_OBSERVABILITY_BUILD_TEST_AND_PUBLISH.command"
  chmod +x "$PROJECT_DIR/GHARTV_V053_OBSERVABILITY_BUILD_TEST_AND_PUBLISH.command" "$PROJECT_DIR/VALIDATE_SOURCE.command" "$PROJECT_DIR/GHARTV_TELEMETRY_REPORT.command"
  GHARTV_PROJECT="$PROJECT_DIR" bash "$PROJECT_DIR/VALIDATE_SOURCE.command"
  assert_only_v053_changes
  git -C "$PROJECT_DIR" diff --binary > "$RESULT_DIR/GharTV-v0.5.3-before-commit.patch" || true
  note "In-place source correction applied: $VERSION_NAME"
}

configure_java(){
  local candidate major
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
  if [ -z "$JAVA_HOME_SELECTED" ] && [ -x /usr/libexec/java_home ]; then JAVA_HOME_SELECTED="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"; fi
  [ -x "$JAVA_HOME_SELECTED/bin/java" ] || fail "JDK 17/21 was not found"
  export JAVA_HOME="$JAVA_HOME_SELECTED" PATH="$JAVA_HOME_SELECTED/bin:$PATH"
  note "Java: $(java -version 2>&1 | head -1)"
}

configure_android(){
  local candidate wrapper_props
  for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -n "$candidate" ] && [ -d "$candidate" ] && SDK_ROOT="$candidate" && break
  done
  [ -n "$SDK_ROOT" ] || fail "Android SDK was not found"
  export ANDROID_SDK_ROOT="$SDK_ROOT" ANDROID_HOME="$SDK_ROOT"
  ADB="$SDK_ROOT/platform-tools/adb"; EMULATOR="$SDK_ROOT/emulator/emulator"
  [ -x "$ADB" ] || fail "adb is missing"
  [ -d "$SDK_ROOT/platforms/android-36" ] || fail "Android SDK Platform 36 is missing"
  [ -d "$SDK_ROOT/build-tools/36.0.0" ] || fail "Android Build Tools 36.0.0 are missing"
  wrapper_props="$ANDROID_PROJECT/gradle/wrapper/gradle-wrapper.properties"
  if [ -x "$ANDROID_PROJECT/gradlew" ] && [ -f "$wrapper_props" ] && grep -Fq 'gradle-8.11.1-' "$wrapper_props"; then GRADLE="$ANDROID_PROJECT/gradlew"
  elif [ -x "$CACHE_DIR/gradle-8.11.1/bin/gradle" ]; then GRADLE="$CACHE_DIR/gradle-8.11.1/bin/gradle"
  else fail "Gradle 8.11.1 was not found in the existing project or cache"; fi
  note "Android SDK: $SDK_ROOT"
  note "Gradle: $GRADLE"
}

load_existing_signing_identity(){
  step "Reusing the exact GharTV signing identity"
  [ -f "$KEYSTORE" ] || fail "Existing release keystore is missing; refusing to generate a different identity"
  [ -f "$SIGNING_ENV" ] || fail "Existing signing.env is missing; refusing to generate a different identity"
  # shellcheck disable=SC1090
  source "$SIGNING_ENV"
  export GHARTV_SIGNING_STORE GHARTV_SIGNING_STORE_PASSWORD GHARTV_SIGNING_KEY_ALIAS GHARTV_SIGNING_KEY_PASSWORD
  [ "${GHARTV_SIGNING_STORE:-}" = "$KEYSTORE" ] || fail "Signing configuration points to a different keystore"
  keytool -list -keystore "$KEYSTORE" -storepass "$GHARTV_SIGNING_STORE_PASSWORD" -alias "$GHARTV_SIGNING_KEY_ALIAS" >/dev/null 2>&1 || fail "Existing signing key could not be opened"
  note "Existing GharTV release identity verified."
}

build_release(){
  step "Compiling and signing GharTV v0.5.3"
  export GRADLE_USER_HOME="$CACHE_DIR/gradle-user-home-java17"
  (cd "$ANDROID_PROJECT" && "$GRADLE" --no-daemon --stacktrace --max-workers=2 -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleRelease)
  local built apksigner="" candidate
  built="$ANDROID_PROJECT/app/build/outputs/apk/release/app-release.apk"
  [ -f "$built" ] || fail "Gradle did not produce the signed release APK"
  for candidate in "$SDK_ROOT/build-tools/36.0.0/apksigner" "$SDK_ROOT"/build-tools/*/apksigner; do [ -x "$candidate" ] && apksigner="$candidate" && break; done
  [ -x "$apksigner" ] || fail "apksigner was not found"
  "$apksigner" verify --verbose --print-certs "$built" > "$RESULT_DIR/apk-signature.txt"
  RELEASE_APK="$PROJECT_DIR/release/GharTV-Jio-Live.apk"
  mkdir -p "$PROJECT_DIR/release"
  cp "$built" "$RELEASE_APK"
  RELEASE_SHA="$(sha256_of "$RELEASE_APK")"
  printf '%s  GharTV-Jio-Live.apk\n' "$RELEASE_SHA" > "$PROJECT_DIR/release/GharTV-Jio-Live.apk.sha256"
  cp "$RELEASE_APK" "$RESULT_DIR/GharTV-Jio-Live-v0.5.3.apk"
  cp "$PROJECT_DIR/release/GharTV-Jio-Live.apk.sha256" "$RESULT_DIR/"
  python3 - "$PROJECT_DIR/update/latest.json" "$RELEASE_SHA" <<'PY'
from pathlib import Path
from datetime import datetime,timezone
import json,sys
p=Path(sys.argv[1]); d=json.loads(p.read_text(encoding='utf-8'))
d['sha256']=sys.argv[2]; d['publishedAt']=datetime.now(timezone.utc).isoformat()
p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
PY
  GHARTV_PROJECT="$PROJECT_DIR" bash "$PROJECT_DIR/VALIDATE_SOURCE.command"
  note "Signed APK: $RELEASE_APK"
  note "APK SHA-256: $RELEASE_SHA"
}

find_running_tv(){
  local device name
  "$ADB" start-server >/dev/null 2>&1 || true
  while read -r device _; do
    [[ "$device" == emulator-* ]] || continue
    name="$("$ADB" -s "$device" emu avd name 2>/dev/null | head -1 | tr -d '\r' || true)"
    if [ "$name" = "$AVD_NAME" ]; then SERIAL="$device"; return 0; fi
  done < <("$ADB" devices | tail -n +2)
  return 1
}

wait_for_tv(){
  local state boot
  for _ in $(seq 1 300); do
    state="$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
    boot="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$state" = "device" ] && [ "$boot" = "1" ]; then
      "$ADB" -s "$SERIAL" shell pm list features 2>/dev/null | grep -q android.software.leanback || return 1
      return 0
    fi
    sleep 2
  done
  return 1
}

update_existing_emulator(){
  [ "${GHARTV_SKIP_EMULATOR:-0}" != "1" ] || { EMULATOR_STATUS="skipped-by-request"; return 0; }
  step "Opening the opt-in diagnostics candidate on the existing Google TV emulator"
  if ! find_running_tv; then
    if [ ! -x "$EMULATOR" ] || ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
      EMULATOR_STATUS="existing-avd-not-found-publication-continued"; note "Existing AVD is unavailable; publication can continue."; return 0
    fi
    "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save > "$RESULT_DIR/emulator.log" 2>&1 &
    for _ in $(seq 1 180); do find_running_tv && break; sleep 2; done
  fi
  if [ -z "$SERIAL" ] || ! wait_for_tv; then EMULATOR_STATUS="existing-avd-not-ready-publication-continued"; note "Existing TV AVD did not become ready."; return 0; fi
  local output launch focus running_name
  if ! output="$("$ADB" -s "$SERIAL" install -r -d "$RELEASE_APK" 2>&1)"; then
    if grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$output"; then
      running_name="$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null | head -1 | tr -d '\r' || true)"
      if [[ "$running_name" == GharTV_* ]]; then
        note "Disposable emulator uses an older debug signature; replacing that emulator package only."
        "$ADB" -s "$SERIAL" uninstall "$PACKAGE_ID" >/dev/null 2>&1 || true
        "$ADB" -s "$SERIAL" install "$RELEASE_APK" >/dev/null
      else EMULATOR_STATUS="signature-mismatch-publication-continued"; note "$output"; return 0; fi
    else EMULATOR_STATUS="install-failed-publication-continued"; note "$output"; return 0; fi
  else note "$output"; fi
  launch="$("$ADB" -s "$SERIAL" shell am start -W -n "$PACKAGE_ID/.MainActivity" 2>&1 || true)"
  printf '%s\n' "$launch"
  sleep 5
  focus="$( { "$ADB" -s "$SERIAL" shell dumpsys activity activities 2>/dev/null; "$ADB" -s "$SERIAL" shell dumpsys window windows 2>/dev/null; } | tr -d '\r' )"
  if grep -Eq "${PACKAGE_ID//./\\.}/\\.(LoginActivity|MainActivity|PlayerActivity)|${PACKAGE_ID//./\\.}/in\\.ghartv\\.nova\\.(LoginActivity|MainActivity|PlayerActivity)" <<<"$focus" \
      || grep -Eq "Status: ok|Activity: $PACKAGE_ID/\\.(LoginActivity|MainActivity|PlayerActivity)" <<<"$launch"; then
    EMULATOR_STATUS="updated-and-open:$SERIAL"
  elif "$ADB" -s "$SERIAL" shell pidof "$PACKAGE_ID" >/dev/null 2>&1; then EMULATOR_STATUS="updated-process-active:$SERIAL"
  else EMULATOR_STATUS="updated-but-not-focused-publication-continued"; fi
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$RESULT_DIR/GharTV-v0.5.3-consent.png" 2>/dev/null || true
  "$ADB" -s "$SERIAL" shell uiautomator dump /sdcard/ghartv-v053.xml >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" pull /sdcard/ghartv-v053.xml "$RESULT_DIR/emulator-ui.xml" >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" logcat -d -v threadtime > "$RESULT_DIR/emulator-logcat.txt" 2>/dev/null || true
  "$ADB" -s "$SERIAL" shell dumpsys package "$PACKAGE_ID" | grep -E 'versionCode|versionName' > "$RESULT_DIR/emulator-version.txt" || true
  note "Emulator status: $EMULATOR_STATUS"
  note "Do not opt in automatically: review the consent screen and choose Share diagnostics or Not now yourself."
}

ensure_github(){
  step "Verifying GitHub authority"
  if ! command -v gh >/dev/null 2>&1; then command -v brew >/dev/null 2>&1 || fail "GitHub CLI is missing"; brew install gh; fi
  if ! gh auth status -h github.com >/dev/null 2>&1; then gh auth login --hostname github.com --git-protocol https --web; fi
  local auth_text login visibility
  auth_text="$(gh auth status -h github.com 2>&1 || true)"
  if ! grep -q "workflow" <<<"$auth_text"; then gh auth refresh -h github.com -s workflow; fi
  gh auth setup-git >/dev/null 2>&1 || true
  login="$(gh api user --jq .login)"; [ "$login" = "AmritSinghGit" ] || fail "GitHub is authenticated as '$login'"
  visibility="$(gh repo view "$REPOSITORY" --json visibility --jq .visibility)"; [ "$visibility" = "PUBLIC" ] || fail "Existing repository is not public"
  note "GitHub account/repository verified: $login · $REPOSITORY"
}

assert_no_secret_staged(){
  if git -C "$PROJECT_DIR" diff --cached --name-only | grep -Eq '(^|/)(collector\.env|signing\.env|.*\.(jks|keystore|p12|pem|key))$'; then fail "Credential/signing material was staged"; fi
  if git -C "$PROJECT_DIR" grep --cached -F "$TELEMETRY_ADMIN_TOKEN" -- . >/dev/null 2>&1; then fail "Telemetry admin token was found in staged source"; fi
}

push_if_needed(){
  local remote_now local_now
  git -C "$PROJECT_DIR" fetch --no-tags origin main
  remote_now="$(git -C "$PROJECT_DIR" rev-parse origin/main)"; local_now="$(git -C "$PROJECT_DIR" rev-parse HEAD)"
  if [ "$remote_now" = "$local_now" ]; then return 0; fi
  git -C "$PROJECT_DIR" merge-base --is-ancestor "$remote_now" "$local_now" || fail "Remote main changed incompatibly during publication"
  git -C "$PROJECT_DIR" push -u origin main
}

publish_source_release_and_update_request(){
  ensure_github
  step "Publishing v0.5.3 without exposing an update before the APK exists"
  local final_manifest="$RUN_DIR/update-latest-v053.json" previous_manifest="$RUN_DIR/update-latest-before-v053.json"
  local head_code release_json pages_payload raw_manifest
  cp "$PROJECT_DIR/update/latest.json" "$final_manifest"
  head_code="$(git -C "$PROJECT_DIR" show HEAD:update/latest.json 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin).get("versionCode",0))' 2>/dev/null || printf '0')"

  if [ "$head_code" -lt 10 ]; then
    git -C "$PROJECT_DIR" show HEAD:update/latest.json > "$previous_manifest"
    cp "$previous_manifest" "$PROJECT_DIR/update/latest.json"
    GHARTV_PROJECT="$PROJECT_DIR" bash "$PROJECT_DIR/VALIDATE_SOURCE.command"
    git -C "$PROJECT_DIR" add -A
    assert_no_secret_staged
    if ! git -C "$PROJECT_DIR" diff --cached --quiet; then git -C "$PROJECT_DIR" commit -m "feat: GharTV v0.5.3 opt-in diagnostics"; fi
    SOURCE_COMMIT_SHA="$(git -C "$PROJECT_DIR" rev-parse HEAD)"
    push_if_needed
  else
    git -C "$PROJECT_DIR" add -A
    assert_no_secret_staged
    if ! git -C "$PROJECT_DIR" diff --cached --quiet; then git -C "$PROJECT_DIR" commit -m "feat: GharTV v0.5.3 opt-in diagnostics"; fi
    SOURCE_COMMIT_SHA="$(git -C "$PROJECT_DIR" rev-parse HEAD)"
    push_if_needed
  fi
  note "v0.5.3 source published at: $SOURCE_COMMIT_SHA"

  step "Publishing the signed v0.5.3 APK"
  release_json="$RESULT_DIR/github-release.json"
  if gh release view "$RELEASE_TAG" --repo "$REPOSITORY" >/dev/null 2>&1; then
    gh release upload "$RELEASE_TAG" "$RELEASE_APK" "$PROJECT_DIR/release/GharTV-Jio-Live.apk.sha256" --clobber --repo "$REPOSITORY"
    gh release edit "$RELEASE_TAG" --repo "$REPOSITORY" --target "$SOURCE_COMMIT_SHA" \
      --title "GharTV Jio Live v0.5.3 — opt-in diagnostics" --notes-file "$PROJECT_DIR/release-notes-v0.5.3.md"
  else
    gh release create "$RELEASE_TAG" "$RELEASE_APK" "$PROJECT_DIR/release/GharTV-Jio-Live.apk.sha256" \
      --repo "$REPOSITORY" --target "$SOURCE_COMMIT_SHA" \
      --title "GharTV Jio Live v0.5.3 — opt-in diagnostics" --notes-file "$PROJECT_DIR/release-notes-v0.5.3.md"
  fi
  gh release view "$RELEASE_TAG" --repo "$REPOSITORY" --json url,tagName,assets,isDraft,isPrerelease,targetCommitish > "$release_json"
  python3 - "$release_json" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['tagName']=='v0.5.3' and not d['isDraft'] and not d['isPrerelease']
names={x['name'] for x in d.get('assets',[])}; assert {'GharTV-Jio-Live.apk','GharTV-Jio-Live.apk.sha256'} <= names
print('Release assets verified:', ', '.join(sorted(names)))
PY

  step "Sending the v0.5.3 update request to installed televisions"
  cp "$final_manifest" "$PROJECT_DIR/update/latest.json"
  git -C "$PROJECT_DIR" add update/latest.json
  if ! git -C "$PROJECT_DIR" diff --cached --quiet; then git -C "$PROJECT_DIR" commit -m "release: announce GharTV v0.5.3 update"; fi
  COMMIT_SHA="$(git -C "$PROJECT_DIR" rev-parse HEAD)"
  push_if_needed
  UPDATE_REQUEST_SENT="yes"

  pages_payload="$RESULT_DIR/pages.json"; printf '{"source":{"branch":"main","path":"/docs"}}' > "$pages_payload"
  if gh api "repos/$REPOSITORY/pages" >/dev/null 2>&1; then gh api --method PUT "repos/$REPOSITORY/pages" --input "$pages_payload" >/dev/null 2>&1 || true
  else gh api --method POST "repos/$REPOSITORY/pages" --input "$pages_payload" >/dev/null 2>&1 || true; fi
  gh api --method POST "repos/$REPOSITORY/pages/builds" >/dev/null 2>&1 || true

  raw_manifest="$RESULT_DIR/published-update-manifest.json"
  for _ in $(seq 1 30); do
    if curl -fsS --max-time 20 "$UPDATE_MANIFEST_URL?cache=$STAMP" > "$raw_manifest" 2>/dev/null \
      && python3 - "$raw_manifest" <<'PY' >/dev/null 2>&1
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['versionCode']==10 and d['versionName']=='0.5.3-observability' and len(d['sha256'])==64
PY
    then break; fi
    sleep 4
  done
  python3 - "$raw_manifest" "$RELEASE_SHA" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['versionCode']==10 and d['sha256']==sys.argv[2]
PY
  for _ in $(seq 1 20); do
    if curl -fsSL --max-time 45 -o /dev/null "$DIRECT_APK_URL"; then
      printf 'Direct latest APK download verified at %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$RESULT_DIR/direct-apk-verified.txt"
      break
    fi
    sleep 5
  done
  [ -f "$RESULT_DIR/direct-apk-verified.txt" ] || fail "The release exists but the direct latest APK URL did not become reachable"
  PUBLISHED="yes"
  cat > "$RESULT_DIR/PUBLISHED_URLS.txt" <<URLS
Repository: $REPO_URL
Source commit: $SOURCE_COMMIT_SHA
Announcement commit: $COMMIT_SHA
Release: $REPO_URL/releases/tag/$RELEASE_TAG
Install page: $INSTALL_PAGE
Direct latest APK: $DIRECT_APK_URL
Update manifest: $UPDATE_MANIFEST_URL
Telemetry endpoint: $TELEMETRY_ENDPOINT
APK SHA-256: $RELEASE_SHA
URLS
  cp "$RESULT_DIR/PUBLISHED_URLS.txt" "$PROJECT_DIR/PUBLISHED_URLS.txt"
  note "Release: $REPO_URL/releases/tag/$RELEASE_TAG"
  note "Update request: versionCode 10 is live"
}

finish_test(){
  step "GharTV v0.5.3 is open for consent and diagnostics testing"
  note "Collector: $TELEMETRY_ENDPOINT"
  note "Owner report: bash '$PROJECT_DIR/GHARTV_TELEMETRY_REPORT.command'"
  note "This test mode has not published v0.5.3 or sent an update request."
  write_continuity_receipt "LOCAL_OBSERVABILITY_TEST_CANDIDATE" \
    "GharTV v0.5.3 was applied, compiled, release-signed and opened on the existing Google TV emulator with the opt-in collector deployed and verified." \
    "Review the consent screen, opt in, generate controlled guide/playback errors, send the queue, run the owner report, then rerun this same command with publish to release v0.5.3 and send the update request." \
    "Owner acceptance and public update remain pending."
}

finish_publish(){
  step "GharTV v0.5.3 is published and the update request is live"
  note "On the Hisense: restart GharTV or choose Jio account → Check for GharTV update."
  note "After updating, choose Share diagnostics only after reviewing the consent text."
  note "Owner report: bash '$PROJECT_DIR/GHARTV_TELEMETRY_REPORT.command'"
  note "Install page: $INSTALL_PAGE"
  write_continuity_receipt "PUBLISHED_OBSERVABILITY_UPDATE_CANDIDATE" \
    "GharTV v0.5.3 added explicit opt-in privacy-filtered diagnostics, deployed and verified the one purpose-built collector, published the same-package signed APK, and sent the versionCode 10 update request through the existing manifest." \
    "Update the existing Hisense installation in place; test consent off/on, preview/delete/send controls, guide/update/playback reporting, error references and the owner report; retain v0.5.3 as owner-acceptance pending until real-TV evidence is reviewed." \
    "Telemetry is disabled until TV consent; owner acceptance remains pending the physical-TV observability test."
  command -v open >/dev/null 2>&1 && open "$REPO_URL/releases/tag/$RELEASE_TAG" >/dev/null 2>&1 || true
}

step "GharTV Jio Live v0.5.3 — opt-in diagnostics and update request"
note "Existing project: $PROJECT_DIR"
note "Existing lane: $LANE_ID"
note "Entity type: $ENTITY_TYPE, not tenant"
note "Canonical repository/branch: $REPOSITORY · main"
note "Package/signing identity retained: $PACKAGE_ID"
note "Diagnostics default: OFF until explicit TV consent"

command -v python3 >/dev/null 2>&1 || fail "python3 is required"
command -v unzip >/dev/null 2>&1 || fail "unzip is required"
extract_payload

if [ "$MODE" = "--verify-package" ]; then
  CONTINUITY_DONE=1
  note "GHARTV_V053_PACKAGE_VALIDATION=PASS"
  exit 0
fi

verify_existing_authority
deploy_telemetry_collector
apply_v053_source
configure_java
configure_android
load_existing_signing_identity
build_release
update_existing_emulator

if [ "$MODE" = "test" ]; then finish_test; exit 0; fi
publish_source_release_and_update_request
finish_publish
exit 0

__GHARTV_V053_PAYLOAD__
UEsDBAoAAAAAAAwIIV0AAAAAAAAAAAAAAAAlAAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNl
cnZhYmlsaXR5L1BLAwQUAAAACABiByFdmILI8PQEAADOCAAAMQAAAEdoYXJUVl9KaW9fTGl2ZV92
MC41LjNfb2JzZXJ2YWJpbGl0eS9URUxFTUVUUlkubWRNVctu20YU3fMrLhCgC4MS3QbZpCvXdmwn
dm1IioOuoiF5KU5NzjDzkKwuuuwH9BP7JT13RrINBHDEmfs6jzvv6KpXbvVIk9Nb1exnnR4CO26p
1WpjrA+68UXxyM5ra+h0/mH+nlTbelKG7BRm2pTUaefDbFIu7N+Gkd/7wCMFS6Fn4meNz2ZzqFhM
zv7JTZjTTSDtSZvApkVhXFfG79hRY03jODCas21sAv2IjBTW+I+063XTk+OBleeq5a1uuGjsWGuj
0hXqlB7Kw73P2tI0qH2tmifyQW04Hfvj+fVq9YByLXukbKIrqbc7GqzZFC9hQT3hGN3VjDqMi6rd
54u2Q+9Ux65jJxPaBjmQXJkWBRjTO4pTqzCKdbSJuuXCcefY99QNdudpZ93TvCjevaNz9M4mpFgA
EJwdiuLiDarKIUvXUTRBD6RoAjegBv8EZVDJz9OgGx2GPTW9tR5dn5wse4l7w87JybxYIQBXAB01
4LOWX8psQELn7IgowU01jUUt+u+ff+ltHz8dNYNMtOpVIA+2gIMavC18nCbrAkQAVsnYXQk4GzUg
CFwxfoLMiI7AoPCV5n1tb3ZzAYA9hwzKhQpKMoWimB0Vuz1oUgInz7G1Zj/aCAbxBd1rA6KHIamB
euX7XxGLuFGZ2KkmRMeuGsE5VHJmWmd1e8x56JVT7tpZhQk4CEcU9hNLouOsuNCxkmSz6CHyLZr0
cqFBy4PdxJzEQyI7JZeyDHxsGva+EhUiNk8fXWpWoq9vl9XF2fK6pIvFHTSiNuWLgGeYy4U4UdCj
qC01+aK9RFbqgJ2D2qThMmu+fNV5KQPEugbVI/pIh74HYelUrkPxnVM4FH9DEw5sH2TpWKjFH5Rk
0yQ8RCkiHsMD3VwAQOgois+SjjHyxro9RApRwhGiVQjmGJC8+IZpAxjdC9+SerS1Bh8mjjVOMNb9
6kHKTsp78NKWLzKV4km2MfTVwWTVcnkP5z6hrpW1Yp90JjHAxGMpktAdVoucCt6wj8xFXxe3JT3x
Xr47TtuHetgeKknhmcQuDm9G95XJqGFpbQAgNkWAF2FCJBHlC0k9ViHwkCQ3D7JP0SVCvunZJ00S
X9Ld2fnrwVGfMpxqAU7QXvLAJcgKUXuQD3NlfICkOFtNU2bZUyuo1txZSE0HArAYAf/zB1N+Xt7/
fpsdKV5mOh9sbCE73P8G2QNzZBs0+wJ7QmNPT7KsAbfHMhR9S52StmrQom+fN/423Wh6HlUJEUwe
fO0H2KlyyQT6LwBVYOlH2VgpLscAxmwJi0/GhrRsZVSBjWl9/mmGPWnwfACF2c3DOotngcfCiIUy
AI0dBlxBWNoySAWR8BQg+uxTUljcjt6fAqC9n9PXvHoTWg5NY4Vkyeq0XAtgMEtLD80ftlCrXSqx
T/3iHcOYEwpA3R9OT491KoHP2Wc9IhT5Pvz8C33Rv+Wu77HBlXSpkpxy71OsAUnisLdDe+hDga9d
agFugLnEmNAkJDqnZRxHdegDT0ByqI0hPWk/ItoU6lQ7yizJCsmC6DhhCnRj8HiY6Epnlx9/C5Nn
D18w0ceiWK/XgZ9D8Xd1q2uHctXZlLmTLbvMS7/K2FQYlUcOuPTCxJzNVpIUxSIaWl9dny1Wj99X
l7eXd5erxR/fF5cP94vVHA8sPNmuj6+amNk67dHmnWrkEbaTbN+DfPNCmhf/A1BLAwQKAAAAAAAA
AiFdAAAAAAAAAAAAAAAAMAAAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS9h
bmRyb2lkLXR2L1BLAwQKAAAAAAAAAiFdAAAAAAAAAAAAAAAANAAAAEdoYXJUVl9KaW9fTGl2ZV92
MC41LjNfb2JzZXJ2YWJpbGl0eS9hbmRyb2lkLXR2L2FwcC9QSwMECgAAAAAAAAIhXQAAAAAAAAAA
AAAAADgAAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvYW5kcm9pZC10di9h
cHAvc3JjL1BLAwQKAAAAAAAAAiFdAAAAAAAAAAAAAAAAPQAAAEdoYXJUVl9KaW9fTGl2ZV92MC41
LjNfb2JzZXJ2YWJpbGl0eS9hbmRyb2lkLXR2L2FwcC9zcmMvbWFpbi9QSwMECgAAAAAAAAIhXQAA
AAAAAAAAAAAAAEIAAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvYW5kcm9p
ZC10di9hcHAvc3JjL21haW4vamF2YS9QSwMECgAAAAAAAAIhXQAAAAAAAAAAAAAAAEUAAABHaGFy
VFZfSmlvX0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvYW5kcm9pZC10di9hcHAvc3JjL21haW4v
amF2YS9pbi9QSwMECgAAAAAAAAIhXQAAAAAAAAAAAAAAAEwAAABHaGFyVFZfSmlvX0xpdmVfdjAu
NS4zX29ic2VydmFiaWxpdHkvYW5kcm9pZC10di9hcHAvc3JjL21haW4vamF2YS9pbi9naGFydHYv
UEsDBAoAAAAAADUCIV0AAAAAAAAAAAAAAABRAAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNl
cnZhYmlsaXR5L2FuZHJvaWQtdHYvYXBwL3NyYy9tYWluL2phdmEvaW4vZ2hhcnR2L25vdmEvUEsD
BBQAAAAIAGIHIV0b1J7KWiAAAP2GAABfAAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZh
YmlsaXR5L2FuZHJvaWQtdHYvYXBwL3NyYy9tYWluL2phdmEvaW4vZ2hhcnR2L25vdmEvVGVsZW1l
dHJ5LmphdmHcPNty28aS7/6KMWsrB4xJSHYcV2LHUVEkZDOmSB2RyuVYPiwIGIqIQIAHF8qMparz
tB+wtV+wn5Yv2e65AANgAFJO9uzW4kEiMT09PT09fZserm3nxr6mxAvM66UdJRszCDf2q0ePvNU6
jBJiB24Ueq5pr9dmz0m8jZdsX2kbfRolA8/2w+tKuxMGCQ0Ss4//Pya17VOggLpnEV3QiAYOjSuQ
AWVYAipIObUDoD7Swo1pchtGN01tfXttX3m+l3iascLYPE4939U1vIXPvmZcaBqF4RpbSk0fTRzQ
PAZ+h4vFWeh7ToWRAgYmGCeR7QVJhSgBYX304sQLrs9o5IWu5/wELxtRyg47AQVjZts1rYGYBHTm
rShiOqf/SGlcWU8BqBLXDIkQ2UpKkDC6Nn+Nw8D8YToZ96LIzkkuNFkfHbpOvDDQN0+ufgVhydH+
am9s0wthaRcoZe45tV1lIWXziedT7cthsE6TaRJRe6Vtn6RJPYDSWTduACAO7IEYJHSaAI/syO3z
73ERMqZOGoH8m6c0joFvA+9aZS+DSRPPNxnjRp62rR/6Pm4kEDdN6zChkZ2EkaapBt8odOwy11jD
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
dGvr/+GPCzuepebdF4atIfRL2n5VMKoRsmSefXQv6lUoNFZxcB6Z5ZExuJUmFpY7A3iCR3jxtxBV
Ibli91zQljHUOmxxNGazRRwns1n3upOMp/OPG/XBcBa9jpLBjdY0ycZRmqTzz7doOFpkyaUmxyV1
PHdPu4979D/zGPwCUEsDBBQAAAAIAGIHIV3alKZUGwgAADIfAABnAAAAR2hhclRWX0ppb19MaXZl
X3YwLjUuM19vYnNlcnZhYmlsaXR5L2FuZHJvaWQtdHYvYXBwL3NyYy9tYWluL2phdmEvaW4vZ2hh
cnR2L25vdmEvRGlhZ25vc3RpY3NEaWFsb2cuamF2YdVZ624buRX+n6dg9aMYYZVxkt0CRVJ34bXs
RIVvtcdOi6YIqJkzEmsOOUtypFWzBvosfbQ+Sc8hZyzJGslS7KDo/LFC8lx4rt9hSp7e8hEwoeLR
mBs3iZWe8HcvXoii1MYxrjKjRRbzsowPUicmws3etW5KMK4vuNSjlf1UKwfKxQP/Z2VbgYuvjVhZ
d/CLiwtwY53FV6nRUgo1OtUTKJDLqV9foZmKbITsEiS9ETBdu6+5dfNb/oNPeBDX5w6OtSn4XE2/
WTkh/SYSldVQipTlQnHJUsmtZXjxkdLWidQGG7AvLxh+pRETJFrdj7rsy92LcCaws47jNpuglsyO
9TRqzM14/aNbM6VP5Cxq1tn+PlOVlOzXX+/PxsIeCyXsGE0WdbvMgKuMendPP9RaAlcMFB9KyNg+
S0CiYZ0h0qOwei+hOycUyrGfK6ge0ISlQ10p10YltRoxtJRbIqKF61Jqnh20Ul05I2o6cijSehZ/
2Gev2I+scwYTMB32ls2dFqNz6V+JKGCg0KQqhagb534zUjD1ZyNi010VVIC1lAz7rJNAOlYiRQdn
c9e9ZR32HYsam6EK52ckv3N+fNzp3nNrvu9Y55O6BAoiy6ZcOJQRWARrtROc0BVtlaaoTF5JZkFl
gaqxQzvdFcZPVau4bOKws97Af/s7Ew4Ki/cmCzWLX1bkLFw8wWhirs1ITOe5N8qmI6rTW+HeucKb
NrG1eFrpadvxCwMTTPEWirbTfTQJJmILeywNmB4WHBv02yg/jrlj77E4JjcsxSIEqWuVcF6CCgmf
zlipMadnnftTd3Ojk40XqmX8UyVkBmbunxXWMSqXCCch6iwUEvbbRlpL7BHJaQjnqA7r9kMD8nzk
/d9jUeZV6rHpWKTjLnv5R7YaBfTZqXDpmEX1ufZD9KXcAnv1du0+fVTL6tDqooVVLkzRF5YWFoKW
gURW9XYoUG0h3fYNDfDb9Ue8jq8f1/E3G0vkJis0n287ccFvgfL4nrZXJ8tyimCDMdZ1ejXVydHZ
++TD55Pzs/fd2DeIR259FyxGmj9Sp6mBvHqa/md6OacMNBXv4QWuPpxfJjvdYAu97u8Hyt8wdJUF
BZ2p4BFpmy+4mHhTIUNdxjzBnHdjYFgbBDkNIQo8wWVPDuM3m8OYlKjr5nMmz/ebpTY57Uvwc8r9
YZvb+hLZB8eFbG2CXyv8d5uF3yMxRCHGNWDOY5CAg6PwJz44TAbnZ59vBkcfewxxcFxyYyE6KMtD
stsovrgc3Bwc/vXzxcH7o273aZpnkPNKus2qb+CxGqF37X3lDEaIZyfwU+WcVlHnUGpLmUFAtY1i
ITsaXFxj50VgvFz+NyLkJ7XZqzFVsFbw8uMj3XatZTs1hphXj7p9v8yFdGAQlswFVh6IEjQBY7RB
gOJhZMyOMYzxZDrmSoFs1jEiFULzVFYZ+Hr0J6Hvzwz6aAzIiL8mAqOzKg3HcuRWGYg/qU+q82h1
RJw5cMgLYbe/gGUzXRlW6CHqhJ4thmB67Dy56LESp6KpNhmCClLF6VtQ+DvV+lYA/rAOY6zYQ5AE
CNHZ9eUJLg4uGM8yxGL4+6N4eSyY4gX0FtFwcyeqYATZx8I6bbBAH4QBjy6L5kIzWzDo+FqrePVy
7V680FYshq3vywRXt8dG82aE/B7ChI2daAM8aIXRFKNet10a7Lb5SqOTfK6EbeDct8vYVQCV54+l
aufPq9OAI7Q/5hOMZ+3YELC/Y6i7kLVDwAJKTSxjoigA6RzIWbxGTHss5blP68DnGeMq54iVdg6s
/sNwqtWj4iA1BZzHUwjmbHP13eDN/ybcAtL4dtG2dpp8NOQSrFi1JS2jSjpjlfIRFswdCrr3wQgU
GE7nuNfW4KIuFqRhsYsZVuRMg/XhasUII6xy6EYqu1gFfeso9IRK/QSLNQ562ESaRwkusQpnZJ8U
Sorq4cx7vh5zkZhigjgHJvUBvEFQlmyFg4pvU7+UAs/yyumC/MGlnGHS4GHFvn/FMj6z2+ZJ/wmp
kUrgJqT10QStap9aeU/0StVV2bIP/h8SYxH4b0yL5uWUufDiRoHXrLWhZ/9qSolBdpv7oXw4ZKwh
uRL/hOj1D2t2D7XUJkom1yJOjv6StJy6QMBAT5z+TFYueO7Nmy76Y2X59e9bl9efbhG6/AbtIf2a
9+lo3b0H9gooxXxXfBCYz/ZAVE/gBPsIrq1JP+9Z0m277PSvdP5Jri0/t5zEv9nM0P6W/mAI/HZ9
oYb4i2+RXzstHIYajDVXKyylPMcRgfA206VDqP92S8D+n3/9m/GyZNhpbFOrSwtVptWs0JVFVmgt
Kbmv5GNuxzswxqsWXFU52ganCLNXaCyFcyxey+wxydWoagYa7NEKQuNwsxJ2EGdTQ3iMmOTASeTL
Cgdx8IV+Bz6jSmSwV5WZr5JhuKBOWU9DAQCJAlNnB6al5LMhT2+x/GqnsYP2WP/ylOWSj2jc4cZV
5d6wynOo/6+BW5RV7Ki6nwZpfhoTSkjR3sQbpeYGRyXc+JAkFz7+q9CpeIMoDKBkGrZ2kPZgivSB
WD95IUpudshsdusJ0v+vTYMwINsljpcGTXIYjpq7eGg+kyLgoSdQvNTSgEo86xF1l7AMsyxmgsjB
uqWhNgAwrIQWpwrEWpgRu3BeHXxpIrZ7qDdxxlAbod8LHOap/uzCet2sfXpw+NgwvbWrrwOwxTTj
9Do6EVZgu5uPP9T86KHbI8rkJmbJEva8BSjtHJuGNGc57iiNsUAPNGiUObr8yhF/py5z9+K/UEsD
BAoAAAAAAAACIV0AAAAAAAAAAAAAAAAvAAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZh
YmlsaXR5L3RlbGVtZXRyeS9QSwMECgAAAAAABwUhXQAAAAAAAAAAAAAAADYAAABHaGFyVFZfSmlv
X0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvdGVsZW1ldHJ5L3dvcmtlci9QSwMEFAAAAAgAYgch
XTAsgwmfAgAAngQAAD8AAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvdGVs
ZW1ldHJ5L3dvcmtlci9SRUFETUUubWR9VNFu00AQfPdXrITEk52kQgipoqDQppEJtFEbKDzVl/Mm
PnK+s+7OKebrmbOT0grES6RcZmdnZ3bzguaVcKuvFFhzzcF1JK3WLIN1SbKqlKdzbdtyo4VjurNu
x47wGComr8xW85NKNmVjlQm0sa5H8E/lA1DHJo2zP0A9SvJAQkpugidrdNeDbRMyZVKA1F7ILtso
HdhxScUW1WE/emw0Wosgq9H+pCAvK65FSh562ScDKWp4zwbkyoCf6eKEShHEWnhOIaqxnj2JslYm
C3bHJoOwAGEo9G1dC9eNI8oFcrYN7NNEmJJK9MeXI7fVJccxhaFXE/B3fhQdY5qa0llVkmgaMgA7
8nDG00dlqbZrBc9MW6/Z+ZSuV0t8QrZt4ZvEuOBWQuOxV4bW0tqdggbM6FjUBG+1kmwk05ebT3jP
l5ilxPg+ou5UdqnIiLovaWGI95tWk4RQw5r2ih8QSYJo4VmXPsrNL3zkRkyeHSQcRY4oDnWIvmGH
cGu4B5S0cEWZkhvMB90J5AsZlDXUCA8Mfh4MgPCyZx9youL8Mju30CPjemT5soB3N73Xp0mSUTGf
rWhcsdChKuL35fUtHvYn48H8gh5UqKj4lg2bleVmyz5kC+6Kx3Kg+4zHh0zfx4zO3hxrp22orFO/
RBR8Sh8YG+7obV8ymP/uH1zDXhyoXmpVq3D2ejKZHFj7u4D39ITmmf6BpWndlv9XkhT51Xx2u7pf
zL4XvZHF9OJzfnW/ul7MrvCCa/z7MBGJ43BITPWW0I67eLBYKZs07VorX2HNgz2c8NZwv6ljxzVu
4Nn/gNmobd8bUMfInXsmQ2Ldes4ACM7qBB1SMhYnPdwuWP4s8qDlyWiRYliKpg3xQKOO6XIRt2Ou
cHGM81RxNUfJb1BLAwQUAAAACACuBiFdSTHJuaIAAAD5AAAAQgAAAEdoYXJUVl9KaW9fTGl2ZV92
MC41LjNfb2JzZXJ2YWJpbGl0eS90ZWxlbWV0cnkvd29ya2VyL3BhY2thZ2UuanNvbk2NQQ6CMBBF
95yiYS0Voi507UWa9gcq0DbTAhLC3W2LJi7nzXszW8FYacSI8sHKthMU5ipgwIhAa7VY6kHlKUmO
9CxC8gJNyGgGeW1NSmt+45dDDKvL10arpgEH85K0Cz7iLY4RyA6yT5axCqyq8sw8ybM2Cm/+8jmM
poIb7JrUhYRpBxD7orjf83WF+QmHGBqp8fflV6T6ypvmzutcFXvxAVBLAwQUAAAACADuBCFd3Zeo
XsAAAAAQAQAATAAAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS90ZWxlbWV0
cnkvd29ya2VyL3dyYW5nbGVyLnRvbWwudGVtcGxhdGV1jzFPxDAMhXf/isojUqG9k4CFoacy3Hxs
VWQ5jSmBNkVO6HH/ngQEG/L4vU/vOfAi1UOF0wtr2uoksyyS9IKwsA+FRB1vfHDyef0aEcZ1eefk
rZ99upDj9G3vmt1t3dzXTYtwXvVNNJKTLaOkHwIwrDaKbvyjGZDAdhaX+TPPsQSS+mnKmoFR1xAz
GbC9q/bVVTk0OTK4thSy5SjRGLB5lQ9T6e8PCL+Iwr8v/UV8qUaivqW+e+oO3emRjj0RwhdQSwME
FAAAAAgAWQIhXU+vH2NXAQAAnQQAAEAAAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2VydmFi
aWxpdHkvdGVsZW1ldHJ5L3dvcmtlci9zY2hlbWEuc3FsnVRBbsIwELznFT6CxA96otStotJQBVeC
k2XspbgYO7IdaH/fQKCEsGkrcotmPTPezGSU0yGjhA3vx5SkjySbMEJn6ZRNSQQDG4j+i8MWbAyk
l5DqObxwrQijM0Ze8/RlmM/JM50PDrAHCXoLiotI0ozRJ5ofSLO38biekEbvGSpCHNc2RGEMX4mw
qkUu8QAhaGd/LFyitT0rNoChoij4Fvz+PAYfIS6dgg53QXoA9PBG2HIpZCw9eBSvSA1qyirvtOK+
WrgIuO/jiCh0hy/jpDDoWQtx5/wagzwswYOVuGaMXi/KCIF/hPa+kv5dkozq8KTZA521wqPVJz8H
qBmKSXaVrF4Dr3j/Tdv41hjrGR6QGxVOWcHom2G5WeAYdlSgWYS2wEkBK6627xAi32mr3O5U279a
VU/zRSnX0NXceqHSlbZroiyUiL+Vv/G/IK0LXjjo7wP2DVBLAwQKAAAAAABZAiFdAAAAAAAAAAAA
AAAAOgAAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS90ZWxlbWV0cnkvd29y
a2VyL3NyYy9QSwMEFAAAAAgA2QYhXQ6J8ANFEAAAOjYAAEIAAABHaGFyVFZfSmlvX0xpdmVfdjAu
NS4zX29ic2VydmFiaWxpdHkvdGVsZW1ldHJ5L3dvcmtlci9zcmMvaW5kZXguanPVW3tz2zYS/9+f
AtH0LmRMPey4vkau45FtJVFrSx5JSZpTXB5FQhZrilT5cOKz9N1vFw8ClETZaTt3c03HloDF7mLx
w2J3AbtRmKTksvWLfdo7/2Sffhq2B+SY7H9/SF6Qvcb+wdGOq0haw7N3dvtDuztEou8bemdrOOx3
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
Q96YQBFBlZy/DPbVsl9wRjP28uOERILZq6OCEQOhE4hsks3TCaCqkZvRLTxNnIP64EzQUKqG+Ruj
GqEQjxzTCVxEGU2xr0/A70Ah5MPwR6Bf3hIo9ZBxFK76F/ffctO045Z5CB6q4nlSZhCCjTz+2oFb
JmBZ1si59URyeaGQN+gEO0qalPDMJbTbsCW3UVyvzN0B7hR4p64/Sa6ZvWXbvr4G6AQ2Hz3PXCXg
1nf70dn5s/KZPwntfgNCqpHNkwR/TGk5Z5ufPV+4QLz849v/CeEIuMF+jv5fUEsDBAoAAAAAAHQC
IV0AAAAAAAAAAAAAAAAqAAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5L2Rv
Y3MvUEsDBBQAAAAIAGIHIV20+MZ10wUAAKgKAAA2AAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19v
YnNlcnZhYmlsaXR5L2RvY3MvcHJpdmFjeS5odG1sZVbbbhs3EH3vV7AyUkTF6rayYnslCwhqNE3R
NEHqts/c5axEmEtuSa5kVdBrP6A/0/d+Sr+kZ3YlW0kAIwrJmcOZc2aGu/hauSLuahLrWJnlgv8V
RtrVbY9sD2uSarmoKEpRrKUPFG97TSwH173jrpUV3fY2mra187EnCmcjWVhttYrrW0UbXdCgXSTa
6qilGYRCGrqdfArhXe5iOAPQVtFjUjpj3BamUUdDyzcI4v43UXu9kcVuMep2FyHu8JN55+K+cMZ5
3LGmijIl/cN8MMhX2cU4HV+NFRa1tGSwvp4UqcQ60mPMLsqrsihLLKsmksoublR+Vcx4rS2Or6bl
rEixLHbSZhezKV2W5eHbfe4eB0H/qe0qy51X5AfYOeRO7faV9Ctts/E8l8XDyrvGqsxLxQys+BdZ
viy0LwwJGcVk/EKMk4uxupzN0iR6aUMtPWzEdPyin2ykf8l59Odtflm35tD78xKUZZNX9eNoMnw1
E2EXIlWDRicDWdeGBt1GEgA5COR1eaiktvtWlAzpvbwZj+vH5CbdbPvzY9SyiW5eS6U4sxmOxVhc
4+ewnuz5Pk6assvr+nFutKXBmvRqjShO/mPYT67ZPj0SMYiuzqaXcDjPgOnsH+rE6P35ditC/xCi
d3a1/yLlwzCXakV7pUNt5C7Ttg0iN654mHc6ZBPEHJzRShwhoeOn7HU7R9lYkSZkNzc3iPCUODgV
kxQbbcrbLsfr8fgwbItof6YsByD9s7KT6UzRKvGrXL68TtLr5DJNhjev+t3OZJrMpsnVBFtpv/9l
yJ3bNEnhmM5myXCafh5p+uos0JSFOFKfXrJaB7n/ItfDYtR1ymLUNTbXKboQ1YAeQkqiMDKE217L
bu/Ubf/+Izbj4Ww4hTuMMBUmyw9dBw5K7UMUKOqVdSHqIgB6slzUJ9+zE4FyFotO0qUrS9HYqI2Q
oiYfnBX4i2sScHJ1DEJbjrY1Hor7Ne0EPWrcFZ3wtCFpRKRibTWGiSilNo2nwBiej15b5R14BJi0
SrxxbmVaaESltghkuBjVy4XSm1PKraA88dLlz4D3IkBFJJMuFw0Go9HLH7UTlcs1gGxT5TBxXry/
/7AY4ZANagBtoVFIhCwKVEUUb++wiO6BbGDrwrkHTeHJA/mRrBJRSatLQnKwufv4ThgMTVuQ+PXj
T62fpz8aPmbVQNazf1MUFELZGB7PFhm00zSM2ntr71COVUWiHZMtEk9qFIxYg0rnd09Ibz8IlBIo
hN/vevC9boES8e71d8kTm2/vkJkCOVEHBnl7x5BglacKWO9o6TBHzNoIBLeU/sKDzFmzE7KMxETY
L/g9Vgzgg4aOrBumoHKVqAM1ytld5RquixClMTKy0VqG9VMOcAaRTSmLiGLwo8opMs/RH4ETgSkh
eejigtw7qTgaS0ULyG/hE2BJkpEGTQATq0YrGjW1kpEgSGhMTARC8bGpO6ymLMEDeKlIBvhVyPBM
q8I3eU5KkPfOg+awxouJuxUxTPEgSs/aJeKH+/sPvBUbLEoAkseDBwJbSogfWvzgLq6RJ3wuz1MR
sDBM9nZN3FR4Xk4n3Cl8t2t77dg3okDr55wVSkY1BakzDaHQd5h+3plOrXr5vgbqqZH52lO1//fX
3+LurOG/kVU9f36sjx7cwaDVfjIbMA8SlraGDgLF3tApVY6WkLh124TLDZoSFODwP7U70oNvlPbw
GR10YILgRtbms0uPcAHDiAvyhDUUrw1aU+04N6rj+S3gyzq2rNwG+/kO1+nQVRXM46l46bHWsOWX
tEK14pQV0TC2eNOFkrvQTSGw+pH4oweeJ45fn67FLLLHWz2+lvDKKFGCBsvDyDMP8hmO56TAo9/e
j/FvwCZs+VMKmikgIYQucCY14HsM/ys1GXV0fnZSDqxwotoeY2sTYFZYBh4fdN45zxOEjnnhT4o1
KvW2Nxz1OEdWHeqf3ga3tYb7r5Yr1LFctl6j7jkadW/TqP0u/ep/UEsDBAoAAAAAAHQCIV0AAAAA
AAAAAAAAAAAsAAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5L3VwZGF0ZS9Q
SwMEFAAAAAgAdAIhXRbgYIDDAAAAEgEAAEMAAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2Vy
dmFiaWxpdHkvdXBkYXRlL3RlbGVtZXRyeS5qc29uLnRlbXBsYXRlXY6xCoMwFEV3v0KcS2zp5iYY
SrG10sbBKUR9NQGNkjwFKf33arVLh7tczrncl+O6ni0ltMILXK+WwuBIEBpoAc1Eyk4/VU3Gg7db
SNCiaKCaUTQDbFXVd0rjonPO6IVeKbvnnCZRejsnjPNVVboGizFM/+A5OdEH4zHNf2hv1CjKKTPN
wkrE3ga+L1qj0M4zslZI5sihIKrz18/+JhGJbbPOGEDQqDodicnOS8f9tx76SiBU4fY4S6OQ0YiH
y1Pn7XwAUEsDBBQAAAAIACMHIV1dbunSSwMAAHMGAABHAAAAR2hhclRWX0ppb19MaXZlX3YwLjUu
M19vYnNlcnZhYmlsaXR5L09CU0VSVkFCSUxJVFlfQUNDRVBUQU5DRV92MC41LjMubWRlVFtuGzsM
/fcqCAS4H+7Y6QNdQODkNi3SxnCcFPerljW0R41GnOoxqTfQBXSJXcklpbHjOv4aS9Qhec4hz+BD
o/zyAfrX0/fTd0DrgL5Xa2NN3IHSGruonMbR6OwMZuQCujgaTeC+23pVIygH+NOEaNy2QLwF40JU
1qpoyMGTiQ2lCNqi8hJUq6imDMBYG+NbiA0Cf4Q48clBbdTWEcPpIN+WtqC6jp+G/KYhCgjj8ReK
4OhpPOb8NegByhFY0srCj4QJwQTQHlXEOkfxbeosqVoupA1BvO3QMd4nQ9IrJRfhz6/fcHlUxj/Q
edMrvRuPK0Cn1haPy6z+KkG64e5j4tyNclsMEAnITTN/8wIk0dGTDULk3GNv8Cm/LHUf45mYgxVz
Kg20xMIguNSu0Vdwu5xXILVHekRXcSg9Gqy4Au67hfvFTQXWaGT9yp/OE8vWtgjRRCuRiRUOYZNs
rtehhY+XFXxk3K9m8q8Bp1oOu3C1J1PzHZBn8jxLM1QhLN4hl/zcQKtcYgPsXjBjFcscJHjgKHVs
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
tvS/tqmen+hOf4snp7+3TcBwXBYWZ7l10uwed856x9mJrraK5PyW5MBpbXBkVYBu5VOHvSacY0lY
I/GMmVaV2TDNzakhZ5QAhKq5AciFnqgr8KHVV7B+FOTSgZh0o081c2zUEJPIwYGcLI4mtPqXohdL
xy5Ld3sFzsB66SAzukXbhzu34T3AW9Do5vIKJJZR1JLVtpcw1TLqcgmP4K4f9wKvOCQ90oIJQIsJ
YaTES7QHonPLytw/B8q8su44nQIwuO1ZiHFLYqsk3byD8YB9FkGiIqjpBbda3objzvzgG9oTUeMD
PHDu3HgZm5d714mBa73xIuGIlPzzIFIwa1fEmlOgNtuqv2ue0R6OPexdDFodaT+hll5hYtW3XxJR
tiThLax12xxPYJxbO+EPL15A94A9g2Sr3uz3zz7Y73Ze7B/2m8MhaHaecFXvD3o/dlqjw3+Kan7V
0tKmO912v3fahWxyAw/z/R9QSwECHgMKAAAAAAAMCCFdAAAAAAAAAAAAAAAAJQAAAAAAAAAAABAA
7UUAAAAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5L1BLAQIeAxQAAAAIAGIH
IV2Ygsjw9AQAAM4IAAAxAAAAAAAAAAEAAACkgUMAAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29i
c2VydmFiaWxpdHkvVEVMRU1FVFJZLm1kUEsBAh4DCgAAAAAAAAIhXQAAAAAAAAAAAAAAADAAAAAA
AAAAAAAQAO1FhgUAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS9hbmRyb2lk
LXR2L1BLAQIeAwoAAAAAAAACIV0AAAAAAAAAAAAAAAA0AAAAAAAAAAAAEADtRdQFAABHaGFyVFZf
SmlvX0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvYW5kcm9pZC10di9hcHAvUEsBAh4DCgAAAAAA
AAIhXQAAAAAAAAAAAAAAADgAAAAAAAAAAAAQAO1FJgYAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNf
b2JzZXJ2YWJpbGl0eS9hbmRyb2lkLXR2L2FwcC9zcmMvUEsBAh4DCgAAAAAAAAIhXQAAAAAAAAAA
AAAAAD0AAAAAAAAAAAAQAO1FfAYAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0
eS9hbmRyb2lkLXR2L2FwcC9zcmMvbWFpbi9QSwECHgMKAAAAAAAAAiFdAAAAAAAAAAAAAAAAQgAA
AAAAAAAAABAA7UXXBgAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5L2FuZHJv
aWQtdHYvYXBwL3NyYy9tYWluL2phdmEvUEsBAh4DCgAAAAAAAAIhXQAAAAAAAAAAAAAAAEUAAAAA
AAAAAAAQAO1FNwcAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS9hbmRyb2lk
LXR2L2FwcC9zcmMvbWFpbi9qYXZhL2luL1BLAQIeAwoAAAAAAAACIV0AAAAAAAAAAAAAAABMAAAA
AAAAAAAAEADtRZoHAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvYW5kcm9p
ZC10di9hcHAvc3JjL21haW4vamF2YS9pbi9naGFydHYvUEsBAh4DCgAAAAAANQIhXQAAAAAAAAAA
AAAAAFEAAAAAAAAAAAAQAO1FBAgAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0
eS9hbmRyb2lkLXR2L2FwcC9zcmMvbWFpbi9qYXZhL2luL2doYXJ0di9ub3ZhL1BLAQIeAxQAAAAI
AGIHIV0b1J7KWiAAAP2GAABfAAAAAAAAAAEAAACkgXMIAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4z
X29ic2VydmFiaWxpdHkvYW5kcm9pZC10di9hcHAvc3JjL21haW4vamF2YS9pbi9naGFydHYvbm92
YS9UZWxlbWV0cnkuamF2YVBLAQIeAxQAAAAIADUCIV0cMdemawEAADkDAABrAAAAAAAAAAEAAACk
gUopAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2VydmFiaWxpdHkvYW5kcm9pZC10di9hcHAv
c3JjL21haW4vamF2YS9pbi9naGFydHYvbm92YS9UZWxlbWV0cnlVcGxvYWRXb3JrZXIuamF2YVBL
AQIeAxQAAAAIAGIHIV3alKZUGwgAADIfAABnAAAAAAAAAAEAAACkgT4rAABHaGFyVFZfSmlvX0xp
dmVfdjAuNS4zX29ic2VydmFiaWxpdHkvYW5kcm9pZC10di9hcHAvc3JjL21haW4vamF2YS9pbi9n
aGFydHYvbm92YS9EaWFnbm9zdGljc0RpYWxvZy5qYXZhUEsBAh4DCgAAAAAAAAIhXQAAAAAAAAAA
AAAAAC8AAAAAAAAAAAAQAO1F3jMAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0
eS90ZWxlbWV0cnkvUEsBAh4DCgAAAAAABwUhXQAAAAAAAAAAAAAAADYAAAAAAAAAAAAQAO1FKzQA
AEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS90ZWxlbWV0cnkvd29ya2VyL1BL
AQIeAxQAAAAIAGIHIV0wLIMJnwIAAJ4EAAA/AAAAAAAAAAEAAACkgX80AABHaGFyVFZfSmlvX0xp
dmVfdjAuNS4zX29ic2VydmFiaWxpdHkvdGVsZW1ldHJ5L3dvcmtlci9SRUFETUUubWRQSwECHgMU
AAAACACuBiFdSTHJuaIAAAD5AAAAQgAAAAAAAAABAAAApIF7NwAAR2hhclRWX0ppb19MaXZlX3Yw
LjUuM19vYnNlcnZhYmlsaXR5L3RlbGVtZXRyeS93b3JrZXIvcGFja2FnZS5qc29uUEsBAh4DFAAA
AAgA7gQhXd2XqF7AAAAAEAEAAEwAAAAAAAAAAQAAAKSBfTgAAEdoYXJUVl9KaW9fTGl2ZV92MC41
LjNfb2JzZXJ2YWJpbGl0eS90ZWxlbWV0cnkvd29ya2VyL3dyYW5nbGVyLnRvbWwudGVtcGxhdGVQ
SwECHgMUAAAACABZAiFdT68fY1cBAACdBAAAQAAAAAAAAAABAAAApIGnOQAAR2hhclRWX0ppb19M
aXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5L3RlbGVtZXRyeS93b3JrZXIvc2NoZW1hLnNxbFBLAQIe
AwoAAAAAAFkCIV0AAAAAAAAAAAAAAAA6AAAAAAAAAAAAEADtRVw7AABHaGFyVFZfSmlvX0xpdmVf
djAuNS4zX29ic2VydmFiaWxpdHkvdGVsZW1ldHJ5L3dvcmtlci9zcmMvUEsBAh4DFAAAAAgA2QYh
XQ6J8ANFEAAAOjYAAEIAAAAAAAAAAQAAAKSBtDsAAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2Jz
ZXJ2YWJpbGl0eS90ZWxlbWV0cnkvd29ya2VyL3NyYy9pbmRleC5qc1BLAQIeAxQAAAAIADcHIV0z
PCeMnQkAAD4VAABEAAAAAAAAAAEAAADtgVlMAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2Vy
dmFiaWxpdHkvR0hBUlRWX1RFTEVNRVRSWV9SRVBPUlQuY29tbWFuZFBLAQIeAwoAAAAAAHQCIV0A
AAAAAAAAAAAAAAAqAAAAAAAAAAAAEADtRVhWAABHaGFyVFZfSmlvX0xpdmVfdjAuNS4zX29ic2Vy
dmFiaWxpdHkvZG9jcy9QSwECHgMUAAAACABiByFdtPjGddMFAACoCgAANgAAAAAAAAABAAAApIGg
VgAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5L2RvY3MvcHJpdmFjeS5odG1s
UEsBAh4DCgAAAAAAdAIhXQAAAAAAAAAAAAAAACwAAAAAAAAAAAAQAO1Fx1wAAEdoYXJUVl9KaW9f
TGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS91cGRhdGUvUEsBAh4DFAAAAAgAdAIhXRbgYIDDAAAA
EgEAAEMAAAAAAAAAAQAAAKSBEV0AAEdoYXJUVl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0
eS91cGRhdGUvdGVsZW1ldHJ5Lmpzb24udGVtcGxhdGVQSwECHgMUAAAACAAjByFdXW7p0ksDAABz
BgAARwAAAAAAAAABAAAApIE1XgAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5
L09CU0VSVkFCSUxJVFlfQUNDRVBUQU5DRV92MC41LjMubWRQSwECHgMUAAAACABiByFdVM7PUBsF
AACMCgAALwAAAAAAAAABAAAApIHlYQAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmls
aXR5L1BSSVZBQ1kubWRQSwECHgMUAAAACABiByFdjf23jpoLAADnHgAAPAAAAAAAAAABAAAA7YFN
ZwAAR2hhclRWX0ppb19MaXZlX3YwLjUuM19vYnNlcnZhYmlsaXR5L1ZBTElEQVRFX1NPVVJDRS5j
b21tYW5kUEsBAh4DFAAAAAgADAghXfxyBfrjAQAA/gIAADUAAAAAAAAAAQAAAKSBQXMAAEdoYXJU
Vl9KaW9fTGl2ZV92MC41LjNfb2JzZXJ2YWJpbGl0eS9SRUFEX01FX0ZJUlNULm1kUEsBAh4DFAAA
AAgArgYhXbvQUyFgKAAAZM4AADIAAAAAAAAAAQAAAO2Bd3UAAEdoYXJUVl9KaW9fTGl2ZV92MC41
LjNfb2JzZXJ2YWJpbGl0eS9hcHBseV92MDUzLnB5UEsFBgAAAAAfAB8AMA0AACeeAAAAAA==
