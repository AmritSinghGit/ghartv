#!/bin/bash
set -Eeuo pipefail
PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
REPO="AmritSinghGit/ghartv"; REVIEW_TAG="v0.5.4-rc4"; STABLE_TAG="v0.5.4"
fail(){ echo "ERROR: $*" >&2; exit 1; }
for cmd in git gh python3 shasum; do command -v "$cmd" >/dev/null || fail "$cmd is required"; done
[ -d "$PROJECT/.git" ] || fail "Canonical GharTV checkout missing: $PROJECT"
[ "$(git -C "$PROJECT" symbolic-ref --short HEAD)" = main ] || fail "GharTV must be on main"
[ -z "$(git -C "$PROJECT" status --porcelain)" ] || fail "Checkout is not clean; no stable publication was attempted"
git -C "$PROJECT" fetch --no-tags origin main
[ "$(git -C "$PROJECT" rev-parse HEAD)" = "$(git -C "$PROJECT" rev-parse origin/main)" ] || fail "Local main is not the exact remote main"
HEAD="$(git -C "$PROJECT" rev-parse HEAD)"
TAG_SHA="$(gh release view "$REVIEW_TAG" -R "$REPO" --json targetCommitish -q .targetCommitish)"
[ "$HEAD" = "$TAG_SHA" ] || fail "Current SHA is not the reviewed $REVIEW_TAG candidate"
printf 'Type PUBLISH 0.5.4 to advertise this update to televisions: '
IFS= read -r CONFIRM
[ "$CONFIRM" = "PUBLISH 0.5.4" ] || { echo "Publication cancelled."; exit 2; }
python3 - "$PROJECT" <<'PY'
from pathlib import Path
import json,re,sys
root=Path(sys.argv[1]); gradle=root/'android-tv/app/build.gradle.kts'
s=gradle.read_text(); s=re.sub(r'versionCode\s*=\s*\d+','versionCode = 14',s,count=1); s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "0.5.4"',s,count=1); gradle.write_text(s)
state=root/'.ghartv-owner-state.json'; d=json.loads(state.read_text()); d.update({'current_candidate':'0.5.4','version_code':14,'candidate_release':'v0.5.4','owner_decision':'OWNER_APPROVED_FOR_STABLE_PUBLICATION','stable_update':'v0.5.4 / versionCode 14'}); state.write_text(json.dumps(d,indent=2)+'\n')
manifest=root/'RELEASE_MANIFEST.json'; m=json.loads(manifest.read_text()); m.update({'release':'v0.5.4','versionName':'0.5.4','versionCode':14,'ownerState':'PUBLISHED','candidate_sha':'STABLE_SOURCE_SHA_SET_BY_GIT_COMMIT','owner_decision':'PUBLISHED'}); manifest.write_text(json.dumps(m,indent=2)+'\n')
PY
(cd "$PROJECT" && bash VALIDATE_SOURCE.command)
git -C "$PROJECT" add android-tv/app/build.gradle.kts .ghartv-owner-state.json RELEASE_MANIFEST.json
git -C "$PROJECT" commit -m "release: GharTV v0.5.4"
git -C "$PROJECT" push origin main
SOURCE_SHA="$(git -C "$PROJECT" rev-parse HEAD)"
# Require exact GitHub build success before signing/publishing.
RUN=""
for _ in $(seq 1 30); do RUN="$(gh run list -R "$REPO" --commit "$SOURCE_SHA" --workflow 'Android TV build' --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"; [ -n "$RUN" ] && break; sleep 4; done
[ -n "$RUN" ] || fail "No Android TV workflow appeared for $SOURCE_SHA"
gh run watch "$RUN" -R "$REPO" --exit-status || fail "GitHub Android build failed"
JAVA_HOME_SELECTED="/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"; [ -x "$JAVA_HOME_SELECTED/bin/java" ] || JAVA_HOME_SELECTED="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"; [ -x "$JAVA_HOME_SELECTED/bin/java" ] || fail "JDK 17 missing"
export JAVA_HOME="$JAVA_HOME_SELECTED" PATH="$JAVA_HOME_SELECTED/bin:$PATH"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"; export ANDROID_SDK_ROOT="$SDK_ROOT" ANDROID_HOME="$SDK_ROOT"
SIGNING_ENV="$HOME/Library/Application Support/GharTV/signing/signing.env"; [ -f "$SIGNING_ENV" ] || fail "Existing signing identity missing"; source "$SIGNING_ENV"; export GHARTV_SIGNING_STORE GHARTV_SIGNING_STORE_PASSWORD GHARTV_SIGNING_KEY_ALIAS GHARTV_SIGNING_KEY_PASSWORD
GRADLE="$PROJECT/android-tv/gradlew"; [ -x "$GRADLE" ] || GRADLE="$HOME/Library/Caches/GharTV-Nova/gradle-8.11.1/bin/gradle"; [ -x "$GRADLE" ] || fail "Gradle 8.11.1 missing"
export GRADLE_USER_HOME="$HOME/Library/Caches/GharTV-Nova/gradle-user-home-java17"
(cd "$PROJECT/android-tv" && "$GRADLE" --no-daemon --max-workers=2 -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleRelease)
APK="$PROJECT/android-tv/app/build/outputs/apk/release/app-release.apk"; [ -f "$APK" ] || fail "Stable APK missing"
APKSIGNER="$SDK_ROOT/build-tools/36.0.0/apksigner"; [ -x "$APKSIGNER" ] || APKSIGNER="$(find "$SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -1)"; "$APKSIGNER" verify --verbose --print-certs "$APK" >/dev/null
SHA="$(shasum -a 256 "$APK"|awk '{print $1}')"; mkdir -p "$PROJECT/release"; cp "$APK" "$PROJECT/release/GharTV-Jio-Live.apk"; printf '%s  GharTV-Jio-Live.apk\n' "$SHA" > "$PROJECT/release/GharTV-Jio-Live.apk.sha256"
NOTES="$PROJECT/release-notes-v0.5.4.md"
if gh release view "$STABLE_TAG" -R "$REPO" >/dev/null 2>&1; then
  gh release edit "$STABLE_TAG" -R "$REPO" --target "$SOURCE_SHA" --title "GharTV Jio Live v0.5.4" --latest --notes-file "$NOTES"
  gh release upload "$STABLE_TAG" "$PROJECT/release/GharTV-Jio-Live.apk" "$PROJECT/release/GharTV-Jio-Live.apk.sha256" -R "$REPO" --clobber
else
  gh release create "$STABLE_TAG" "$PROJECT/release/GharTV-Jio-Live.apk" "$PROJECT/release/GharTV-Jio-Live.apk.sha256" -R "$REPO" --target "$SOURCE_SHA" --title "GharTV Jio Live v0.5.4" --latest --notes-file "$NOTES"
fi
# Only now advertise the update.
python3 - "$PROJECT/update/latest.json" "$SHA" <<'PY'
from pathlib import Path
from datetime import datetime,timezone
import json,sys
p=Path(sys.argv[1]); d=json.loads(p.read_text()); d.update({'versionCode':14,'versionName':'0.5.4','apkUrl':'https://github.com/AmritSinghGit/ghartv/releases/latest/download/GharTV-Jio-Live.apk','sha256':sys.argv[2],'notes':'Family birthday themes, vertical preview tile, voice/programme search, capability-gated live transport controls and reliability improvements.','publishedAt':datetime.now(timezone.utc).isoformat()}); p.write_text(json.dumps(d,indent=2)+'\n')
PY
git -C "$PROJECT" add update/latest.json
git -C "$PROJECT" commit -m "release: advertise GharTV v0.5.4 update"
git -C "$PROJECT" push origin main
STAMP="$(date +%Y%m%d-%H%M%S)"; NOTE="$HOME/Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv/ghartv-v054-stable-$STAMP.md"; mkdir -p "$(dirname "$NOTE")"; cat > "$NOTE" <<EOF
# GharTV v0.5.4 stable publication
- Source SHA: $SOURCE_SHA
- APK SHA-256: $SHA
- Release: https://github.com/$REPO/releases/tag/$STABLE_TAG
- Stable update: v0.5.4 / versionCode 14
- Owner decision: PUBLISHED
EOF
command -v amrit-context >/dev/null 2>&1 && amrit-context handoff --file "$NOTE" >/dev/null 2>&1 || true
echo "GharTV v0.5.4 is published. On the TV choose Jio account → Check for GharTV update."
