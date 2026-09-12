#!/bin/bash
# Promote the reviewed GharTV v0.5.4 RC2 source to the stable same-package update.
set -Eeuo pipefail

PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
REPOSITORY="AmritSinghGit/ghartv"
PACKAGE_ID="in.ghartv.nova"
LANE_ID="ghartv"
RC_TAG="v0.5.4-rc2"
STABLE_TAG="v0.5.4"
STABLE_VERSION="0.5.4"
STABLE_CODE="12"
SIGNING_ENV="$HOME/Library/Application Support/GharTV/signing/signing.env"
CACHE_DIR="$HOME/Library/Caches/GharTV-Nova"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_ID="ghartv-v054-stable-$STAMP-$$"
RESULT_DIR="${GHARTV_PUBLISH_RESULTS_DIR:-$HOME/Desktop/GharTV-v0.5.4-Publish-$STAMP}"
RUN_DIR="$HOME/.local/state/operon-terminal-runs/$LANE_ID/$RUN_ID"
mkdir -p "$RESULT_DIR" "$RUN_DIR"
SETUP_LOG="$RESULT_DIR/setup.log"
TRANSCRIPT="$RUN_DIR/full-transcript.log"
exec > >(tee -a "$SETUP_LOG" "$TRANSCRIPT") 2>&1

SOURCE_SHA=""
ANNOUNCEMENT_SHA=""
APK_SHA=""
RELEASE_URL=""
PUBLISHED="no"
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
  python3 - "$receipt" "$status" "$blocker" "$SOURCE_SHA" "$ANNOUNCEMENT_SHA" "$APK_SHA" "$RELEASE_URL" "$finished" <<'PY'
from pathlib import Path
import json,sys
path,status,blocker,source,announcement,apk,url,finished=sys.argv[1:]
data={
 'schema':'ghartv.continuity.receipt.v1','lane_id':'ghartv','entity_type':'project',
 'repository':'AmritSinghGit/ghartv','branch':'main','package_id':'in.ghartv.nova',
 'version':'0.5.4','version_code':12,'status':status,'owner_decision':'PUBLISHED_STABLE' if status=='PUBLISHED_STABLE' else 'REVIEW_PENDING',
 'source_sha':source,'announcement_sha':announcement,'apk_sha256':apk,'release_url':url,
 'blocker':blocker,'finished_at':finished,
 'new_project_created':False,'new_branch_created':False,'new_worktree_created':False,
 'new_database_created':False,'new_worker_created':False,'new_lane_created':False,
 'next_action':'Update the existing Hisense installation in place and review real-TV telemetry.' if status=='PUBLISHED_STABLE' else 'Resolve the blocker without replacing the canonical lane or signing identity.'
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
version: 0.5.4
source_sha: $SOURCE_SHA
announcement_sha: $ANNOUNCEMENT_SHA
created: $finished
---

# GharTV v0.5.4 stable publication

- Status: $status
- Existing project: ~/Downloads/GharTV_Nova_v0.4.2
- Package: in.ghartv.nova
- Stable source SHA: $SOURCE_SHA
- Update-announcement SHA: $ANNOUNCEMENT_SHA
- APK SHA-256: $APK_SHA
- Release: $RELEASE_URL
- Blocker: ${blocker:-None}

No replacement repository, branch, worktree, signing identity, telemetry database, Worker, Operon project, or lane was created.
NOTE
    chmod 600 "$note_file" 2>/dev/null || true
    if [ -x "$HOME/bin/amrit-context" ]; then
      handoff="$RUN_DIR/handoff.json"
      python3 - "$handoff" "$note_file" "$SOURCE_SHA" "$ANNOUNCEMENT_SHA" "$status" <<'PY'
from pathlib import Path
import json,sys
out,note,source,announcement,status=sys.argv[1:]
payload={
 'schema':'amrit.context-mesh.handoff.v1','handoff_key':'ghartv-v054-stable-publication',
 'project_key':'ghartv','chat_key':'iptv-app-for-tv','title':'GharTV v0.5.4 stable publication',
 'summary':f'GharTV v0.5.4 publication status: {status}.',
 'current_truth':f'AmritSinghGit/ghartv main; source {source or "pending"}; announcement {announcement or "pending"}; package in.ghartv.nova.',
 'next_action':'Update and review the existing Hisense installation; inspect owner telemetry after use.',
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
GHARTV_V054_STABLE_PUBLICATION
LANE_ID=ghartv
ENTITY_TYPE=project
REPOSITORY=AmritSinghGit/ghartv
BRANCH=main
PACKAGE=in.ghartv.nova
VERSION=0.5.4
STATUS=$status
SOURCE_SHA=$SOURCE_SHA
ANNOUNCEMENT_SHA=$ANNOUNCEMENT_SHA
APK_SHA256=$APK_SHA
RELEASE_URL=$RELEASE_URL
BLOCKER=$blocker
PASTE
  chmod 600 "$receipt" "$paste" 2>/dev/null || true
  printf 'Continuity receipt: %s\n' "$paste"
  [ -n "$note_file" ] && printf 'Obsidian receipt: %s\n' "$note_file"
  printf 'Memory handoff: %s\n' "$memory"
}

on_error(){ local code=$?; write_receipt "ACTION_REQUIRED" "Stable publication stopped at line ${BASH_LINENO[0]} (exit $code). Review $SETUP_LOG" || true; exit "$code"; }
trap on_error ERR

section "GharTV v0.5.4 — stable publication after owner approval"
[ "${1:-}" = "publish" ] || fail "Run this command with the argument 'publish' only after approving RC2"
printf 'This promotes the reviewed RC2 to every installed television using the stable update manifest.\n'
printf 'No new repository, branch, worktree, package, signing key, Worker, D1 store, or lane will be created.\n\n'
read -r -p "Type PUBLISH 0.5.4 to continue: " confirmation
[ "$confirmation" = "PUBLISH 0.5.4" ] || fail "Publication was cancelled; the stable update remains v0.5.3"

section "Verifying the exact existing authority"
[ -d "$PROJECT/.git" ] || fail "Canonical checkout is missing: $PROJECT"
[ "$(git -C "$PROJECT" symbolic-ref --short HEAD)" = "main" ] || fail "Canonical checkout is not on main"
origin="$(git -C "$PROJECT" remote get-url origin)"
case "$origin" in
  https://github.com/AmritSinghGit/ghartv|https://github.com/AmritSinghGit/ghartv.git|git@github.com:AmritSinghGit/ghartv.git) ;;
  *) fail "Unexpected origin: $origin" ;;
esac
[ -z "$(git -C "$PROJECT" status --porcelain)" ] || fail "Canonical checkout has uncommitted work; stopped without reset, clean, stash, or overwrite"
git -C "$PROJECT" fetch --no-tags origin main
local_head="$(git -C "$PROJECT" rev-parse HEAD)"; remote_head="$(git -C "$PROJECT" rev-parse origin/main)"
if [ "$local_head" != "$remote_head" ]; then
  git -C "$PROJECT" merge-base --is-ancestor "$local_head" "$remote_head" || fail "Local and remote main diverged"
  git -C "$PROJECT" pull --ff-only origin main
fi
[ -z "$(git -C "$PROJECT" status --porcelain)" ] || fail "Checkout became dirty during authority verification"
grep -Fq 'versionCode = 11' "$PROJECT/android-tv/app/build.gradle.kts" || fail "Current main is not the v0.5.4 RC2 candidate"
grep -Fq 'versionName = "0.5.4-rc2-living-room"' "$PROJECT/android-tv/app/build.gradle.kts" || fail "Unexpected candidate version"
python3 - "$PROJECT/update/latest.json" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text())
assert d['versionCode']==10 and d['versionName']=='0.5.3-observability', 'stable manifest was already changed'
PY

command -v gh >/dev/null 2>&1 || fail "GitHub CLI is required"
gh auth status -h github.com >/dev/null 2>&1 || gh auth login --hostname github.com --git-protocol https --web
[ "$(gh api user --jq .login)" = "AmritSinghGit" ] || fail "GitHub CLI is not authenticated as AmritSinghGit"
rc_json="$RESULT_DIR/rc-release.json"
gh release view "$RC_TAG" --repo "$REPOSITORY" --json tagName,isPrerelease,assets,targetCommitish > "$rc_json" \
  || fail "The RC2 prerelease is missing. Run the review opener and approve that build first."
python3 - "$rc_json" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['tagName']=='v0.5.4-rc2' and d['isPrerelease'] is True
assert any(x.get('name')=='GharTV-Jio-Live-v0.5.4-rc2.apk' for x in d.get('assets',[])), 'signed RC2 asset missing'
PY

section "Preparing the stable 0.5.4 source in place"
python3 - "$PROJECT" <<'PY'
from pathlib import Path
import json,re,sys
from datetime import datetime,timezone
root=Path(sys.argv[1])
build=root/'android-tv/app/build.gradle.kts'
text=build.read_text(encoding='utf-8')
text=text.replace('versionCode = 11','versionCode = 12',1)
text=text.replace('versionName = "0.5.4-rc2-living-room"','versionName = "0.5.4"',1)
build.write_text(text,encoding='utf-8')
state=json.loads((root/'.ghartv-owner-state.json').read_text())
state.update({'current_candidate':'0.5.4','version_code':12,'candidate_release':'v0.5.4','owner_decision':'OWNER_APPROVED_FOR_STABLE_PUBLICATION','stable_update':'v0.5.4 will be advertised only after signed asset verification','next_action':'Publish the signed stable APK, then announce versionCode 12.'})
(root/'.ghartv-owner-state.json').write_text(json.dumps(state,indent=2)+'\n')
manifest=json.loads((root/'RELEASE_MANIFEST.json').read_text())
manifest.update({'release':'v0.5.4','versionName':'0.5.4','versionCode':12,'ownerState':'OWNER APPROVED — STABLE PUBLICATION IN PROGRESS','reviewApk':'','stableDirectApk':'https://github.com/AmritSinghGit/ghartv/releases/latest/download/GharTV-Jio-Live.apk'})
(root/'RELEASE_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
notes=root/'release-notes-v0.5.4.md'
notes.write_text('''# GharTV Jio Live v0.5.4 — living-room reliability\n\nThis stable release promotes the owner-reviewed RC2 with the same Android package and signing identity.\n\n## Highlights\n\n- For you, Continue, Recent, Favourites, Working now, language, genre, Subscription and Needs attention views;\n- local-only successful viewing suggestions; failed tunes never enter Recent or For you;\n- constant-time number tuning and ranked global search;\n- category counts, per-view focus memory, responsive four-column guide and incremental card updates;\n- exact-scope Channel Up/Down, including search results;\n- truly auto-hiding Now/Next programme panel;\n- one fresh-authorisation retry for 403, one transient-network retry and one buffering recovery;\n- clearer Subscription, Jio access, DNS, timeout and DRM recovery;\n- Next working channel from every failure path;\n- existing opt-in, privacy-filtered diagnostics retained.\n\nGharTV is an independent, unofficial client. Jio controls entitlements, availability, geography, playback policy and DRM.\n''',encoding='utf-8')
readme=root/'README.md'; r=readme.read_text(encoding='utf-8')
r=re.sub(r'> \*\*Stable:\*\*.*?\n', '> **Stable:** `0.5.4` (`versionCode 12`). GharTV is not an official Jio application.\n', r, count=1)
r=r.replace('## v0.5.4 RC2 review candidate','## v0.5.4 living-room release')
r=r.replace('RC2 turns the catalogue','v0.5.4 turns the catalogue')
r=r.replace('RC2 is published as a prerelease. The stable TV update remains v0.5.3 until explicit\nowner approval. See','The owner-reviewed stable release uses the same package and signing identity. See')
readme.write_text(r,encoding='utf-8')
changelog=root/'CHANGELOG.md'; c=changelog.read_text(encoding='utf-8')
entry=f'''# Changelog\n\n## 0.5.4 — {datetime.now(timezone.utc).date().isoformat()}\n\nPromoted the owner-reviewed living-room reliability candidate to stable. See `release-notes-v0.5.4.md`.\n\n'''
if c.startswith('# Changelog\n'):
    c=entry+c[len('# Changelog\n\n'):]
else: c=entry+c
changelog.write_text(c,encoding='utf-8')
PY

GHARTV_PROJECT="$PROJECT" bash "$PROJECT/VALIDATE_SOURCE.command"

section "Using the existing Android and release-signing identity"
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
[ -f "$SIGNING_ENV" ] || fail "Existing signing.env is missing; refusing a different signing identity"
set +u
# shellcheck disable=SC1090
source "$SIGNING_ENV"
set -u
export GHARTV_SIGNING_STORE GHARTV_SIGNING_STORE_PASSWORD GHARTV_SIGNING_KEY_ALIAS GHARTV_SIGNING_KEY_PASSWORD
[ -f "$GHARTV_SIGNING_STORE" ] || fail "Existing GharTV keystore is missing"
keytool -list -keystore "$GHARTV_SIGNING_STORE" -storepass "$GHARTV_SIGNING_STORE_PASSWORD" -alias "$GHARTV_SIGNING_KEY_ALIAS" >/dev/null 2>&1 \
  || fail "Existing GharTV signing identity could not be opened"

section "Building and verifying the stable APK"
GRADLE="$PROJECT/android-tv/gradlew"
[ -x "$GRADLE" ] || GRADLE="$CACHE_DIR/gradle-8.11.1/bin/gradle"
[ -x "$GRADLE" ] || fail "Gradle 8.11.1 is missing"
export GRADLE_USER_HOME="$CACHE_DIR/gradle-user-home-java17"
(cd "$PROJECT/android-tv" && "$GRADLE" --no-daemon --stacktrace --max-workers=2 -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleRelease)
BUILT="$PROJECT/android-tv/app/build/outputs/apk/release/app-release.apk"
[ -f "$BUILT" ] || fail "Signed stable APK was not produced"
APKSIGNER="$SDK_ROOT/build-tools/36.0.0/apksigner"
[ -x "$APKSIGNER" ] || fail "apksigner is missing"
"$APKSIGNER" verify --verbose --print-certs "$BUILT" > "$RESULT_DIR/apk-signature.txt"
mkdir -p "$PROJECT/release"
cp "$BUILT" "$PROJECT/release/GharTV-Jio-Live.apk"
APK_SHA="$(sha256_of "$PROJECT/release/GharTV-Jio-Live.apk")"
printf '%s  GharTV-Jio-Live.apk\n' "$APK_SHA" > "$PROJECT/release/GharTV-Jio-Live.apk.sha256"
cp "$PROJECT/release/GharTV-Jio-Live.apk" "$RESULT_DIR/"
cp "$PROJECT/release/GharTV-Jio-Live.apk.sha256" "$RESULT_DIR/"

section "Publishing stable source to the existing main branch"
git -C "$PROJECT" add \
  android-tv/app/build.gradle.kts .ghartv-owner-state.json RELEASE_MANIFEST.json \
  release-notes-v0.5.4.md README.md CHANGELOG.md
git -C "$PROJECT" diff --cached --quiet && fail "No stable source changes were staged"
git -C "$PROJECT" commit -m "release: GharTV v0.5.4 living-room reliability"
SOURCE_SHA="$(git -C "$PROJECT" rev-parse HEAD)"
git -C "$PROJECT" push origin main
printf 'Stable source SHA: %s\n' "$SOURCE_SHA"

section "Waiting for the canonical Android TV build"
run_id=""
for _ in $(seq 1 40); do
  run_id="$(gh run list --repo "$REPOSITORY" --workflow android.yml --branch main --commit "$SOURCE_SHA" --limit 1 --json databaseId --jq '.[0].databaseId // empty' 2>/dev/null || true)"
  [ -n "$run_id" ] && break
  sleep 3
done
[ -n "$run_id" ] || fail "GitHub Actions did not register a build for $SOURCE_SHA"
gh run watch "$run_id" --repo "$REPOSITORY" --exit-status

section "Publishing and verifying release v0.5.4"
if gh release view "$STABLE_TAG" --repo "$REPOSITORY" >/dev/null 2>&1; then
  gh release upload "$STABLE_TAG" "$PROJECT/release/GharTV-Jio-Live.apk" "$PROJECT/release/GharTV-Jio-Live.apk.sha256" --clobber --repo "$REPOSITORY"
  gh release edit "$STABLE_TAG" --repo "$REPOSITORY" --target "$SOURCE_SHA" \
    --title "GharTV Jio Live v0.5.4 — living-room reliability" \
    --notes-file "$PROJECT/release-notes-v0.5.4.md" --latest
else
  gh release create "$STABLE_TAG" "$PROJECT/release/GharTV-Jio-Live.apk" "$PROJECT/release/GharTV-Jio-Live.apk.sha256" \
    --repo "$REPOSITORY" --target "$SOURCE_SHA" \
    --title "GharTV Jio Live v0.5.4 — living-room reliability" \
    --notes-file "$PROJECT/release-notes-v0.5.4.md" --latest
fi
release_json="$RESULT_DIR/release.json"
gh release view "$STABLE_TAG" --repo "$REPOSITORY" --json url,tagName,isDraft,isPrerelease,assets,targetCommitish > "$release_json"
python3 - "$release_json" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['tagName']=='v0.5.4' and not d['isDraft'] and not d['isPrerelease']
names={x['name'] for x in d.get('assets',[])}; assert {'GharTV-Jio-Live.apk','GharTV-Jio-Live.apk.sha256'} <= names
print('Release assets verified:',', '.join(sorted(names)))
PY
RELEASE_URL="$(python3 - "$release_json" <<'PY'
from pathlib import Path
import json,sys
print(json.loads(Path(sys.argv[1]).read_text())['url'])
PY
)"
DIRECT_URL="https://github.com/$REPOSITORY/releases/latest/download/GharTV-Jio-Live.apk"
for _ in $(seq 1 30); do
  if curl -fsSL --max-time 45 -o /dev/null "$DIRECT_URL"; then break; fi
  sleep 4
done
curl -fsSL --max-time 45 -o /dev/null "$DIRECT_URL" || fail "Latest stable APK URL is not reachable"

section "Announcing versionCode 12 only after APK verification"
python3 - "$PROJECT/update/latest.json" "$APK_SHA" <<'PY'
from pathlib import Path
from datetime import datetime,timezone
import json,sys
path=Path(sys.argv[1]); d=json.loads(path.read_text())
d.update({
 'versionCode':12,'versionName':'0.5.4',
 'apkUrl':'https://github.com/AmritSinghGit/ghartv/releases/latest/download/GharTV-Jio-Live.apk',
 'sha256':sys.argv[2],
 'notes':'Living-room home with For you, Continue, Recent, Working now, ranked global search, exact-scope channel surfing, a truly auto-hiding Now/Next panel, clearer subscription/access errors and automatic live-feed recovery.',
 'publishedAt':datetime.now(timezone.utc).isoformat()
})
path.write_text(json.dumps(d,indent=2)+'\n')
PY
python3 - "$PROJECT/.ghartv-owner-state.json" "$PROJECT/RELEASE_MANIFEST.json" <<'PY'
from pathlib import Path
import json,sys
state=Path(sys.argv[1]); d=json.loads(state.read_text()); d['owner_decision']='PUBLISHED_STABLE'; d['stable_update']='v0.5.4 / versionCode 12 is live'; d['next_action']='Update the existing Hisense app in place and review real-TV telemetry.'; state.write_text(json.dumps(d,indent=2)+'\n')
manifest=Path(sys.argv[2]); m=json.loads(manifest.read_text()); m['ownerState']='PUBLISHED STABLE'; manifest.write_text(json.dumps(m,indent=2)+'\n')
PY
GHARTV_EXPECT_UPDATE_LIVE=1 GHARTV_PROJECT="$PROJECT" bash "$PROJECT/VALIDATE_SOURCE.command"
git -C "$PROJECT" add update/latest.json .ghartv-owner-state.json RELEASE_MANIFEST.json
git -C "$PROJECT" commit -m "release: announce GharTV v0.5.4 update"
ANNOUNCEMENT_SHA="$(git -C "$PROJECT" rev-parse HEAD)"
git -C "$PROJECT" push origin main
raw_manifest="$RESULT_DIR/published-update-manifest.json"
MANIFEST_URL="https://raw.githubusercontent.com/AmritSinghGit/ghartv/main/update/latest.json"
for _ in $(seq 1 30); do
  if curl -fsS --max-time 20 "$MANIFEST_URL?cache=$STAMP" > "$raw_manifest" 2>/dev/null \
    && python3 - "$raw_manifest" "$APK_SHA" <<'PY' >/dev/null 2>&1
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['versionCode']==12 and d['versionName']=='0.5.4' and d['sha256']==sys.argv[2]
PY
  then break; fi
  sleep 4
done
python3 - "$raw_manifest" "$APK_SHA" <<'PY'
from pathlib import Path
import json,sys
d=json.loads(Path(sys.argv[1]).read_text()); assert d['versionCode']==12 and d['versionName']=='0.5.4' and d['sha256']==sys.argv[2]
print('Stable TV update manifest verified.')
PY

PUBLISHED="yes"
write_receipt "PUBLISHED_STABLE" ""
trap - ERR
section "GharTV v0.5.4 is published and the update request is live"
printf 'Source SHA:       %s\n' "$SOURCE_SHA"
printf 'Announcement SHA: %s\n' "$ANNOUNCEMENT_SHA"
printf 'APK SHA-256:      %s\n' "$APK_SHA"
printf 'Release:           %s\n' "$RELEASE_URL"
printf 'On the Hisense: GharTV → Account → Check for GharTV update. Do not uninstall the existing app.\n'
printf 'Logs later: bash "%s/GHARTV_LOGS_AND_HEALTH.command"\n' "$PROJECT"
printf 'Close local review: bash "%s/GHARTV_CLOSE_REVIEW.command"\n' "$PROJECT"
read -r -p "Press Enter to copy the stable handoff… " _
command -v pbcopy >/dev/null 2>&1 && pbcopy < "$RUN_DIR/PASTE_TO_CHAT.txt" || true
read -r -p "Handoff copied. Press Enter to close this publication terminal… " _
