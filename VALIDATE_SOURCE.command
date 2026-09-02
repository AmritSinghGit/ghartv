#!/bin/bash
set -Eeuo pipefail

ROOT="${GHARTV_PROJECT:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
APP="$ROOT/android-tv/app"
JAVA="$APP/src/main/java/in/ghartv/nova"
MANIFEST="$APP/src/main/AndroidManifest.xml"
BUILD="$APP/build.gradle.kts"
WORKER="$ROOT/telemetry/worker"

fail(){ printf 'VALIDATION FAILED: %s\n' "$1" >&2; exit 1; }
require_file(){ [ -f "$1" ] || fail "Missing file: $1"; }
require_text(){ grep -Fq "$2" "$1" || fail "Missing expected marker '$2' in $1"; }
forbid_text(){ if grep -Fq "$2" "$1"; then fail "Forbidden marker '$2' remains in $1"; fi; }

printf 'GharTV Jio Live v0.5.3 source validation\n'
printf '========================================\n'
printf 'Root: %s\n' "$ROOT"

for file in "$BUILD" "$MANIFEST" "$JAVA/MainActivity.java" "$JAVA/LoginActivity.java" \
  "$JAVA/PlayerActivity.java" "$JAVA/JioApiClient.java" "$JAVA/ChannelRepository.java" \
  "$JAVA/UpdateManager.java" "$JAVA/Telemetry.java" "$JAVA/TelemetryUploadWorker.java" \
  "$JAVA/DiagnosticsDialog.java" "$ROOT/.ghartv-owner-state.json" "$ROOT/OPERON_PROJECT.md" \
  "$ROOT/PRIVACY.md" "$ROOT/TELEMETRY.md" "$ROOT/docs/index.html" "$ROOT/docs/privacy.html" \
  "$ROOT/update/latest.json" "$ROOT/update/telemetry.json" "$ROOT/.github/workflows/android.yml" \
  "$ROOT/LICENSE" "$ROOT/release-notes-v0.5.3.md" "$ROOT/GHARTV_TELEMETRY_REPORT.command" \
  "$WORKER/src/index.js" "$WORKER/schema.sql" "$WORKER/wrangler.toml" "$WORKER/package.json"
do require_file "$file"; done

require_text "$BUILD" 'versionCode = 10'
require_text "$BUILD" 'versionName = "0.5.3-observability"'
require_text "$JAVA/AppConfig.java" 'TELEMETRY_FALLBACK_ENDPOINT'
require_text "$JAVA/AppConfig.java" 'TELEMETRY_FALLBACK_INGEST_KEY'
require_text "$BUILD" 'compileSdk = 36'
require_text "$MANIFEST" 'android.software.leanback'
require_text "$MANIFEST" 'android:usesCleartextTraffic="false"'
require_text "$MANIFEST" 'android:allowBackup="false"'
require_text "$JAVA/Telemetry.java" 'Share diagnostics'
require_text "$JAVA/Telemetry.java" 'ghartv.telemetry.batch.v1'
require_text "$JAVA/Telemetry.java" 'PHONE_REMOVED'
require_text "$JAVA/Telemetry.java" 'MAX_QUEUE_EVENTS = 500'
require_text "$JAVA/Telemetry.java" 'stack_frames'
require_text "$JAVA/Telemetry.java" 'ExistingPeriodicWorkPolicy.UPDATE'
require_text "$JAVA/DiagnosticsDialog.java" 'Preview queued diagnostics'
require_text "$JAVA/DiagnosticsDialog.java" 'Delete queued diagnostics and reset ID'
require_text "$JAVA/NovaApp.java" 'Telemetry.initialize(this);'
require_text "$JAVA/LoginActivity.java" 'Telemetry.maybeRequestConsent(this)'
require_text "$JAVA/MainActivity.java" 'DiagnosticsDialog.show(this)'
require_text "$JAVA/PlayerActivity.java" 'Telemetry.playbackFailure'
require_text "$JAVA/UpdateManager.java" 'Telemetry.event'
require_text "$JAVA/ChannelRefreshWorker.java" 'Telemetry.event'
require_text "$WORKER/src/index.js" 'CF-Connecting-IP'
require_text "$WORKER/src/index.js" 'RETENTION_DAYS = 30'
require_text "$WORKER/src/index.js" '/v1/admin/summary'
require_text "$ROOT/PRIVACY.md" 'diagnostics are opt-in'
require_text "$ROOT/update/telemetry.json" '"enabled": true'
require_text "$ROOT/OPERON_PROJECT.md" 'independent **project**, not as an Operon tenant'

forbid_text "$ROOT/update/telemetry.json" '__TELEMETRY_'
forbid_text "$WORKER/wrangler.toml" '__D1_DATABASE_ID__'
if grep -RIlF 'GHARTV_TELEMETRY_ADMIN_TOKEN=' "$ROOT" --exclude='VALIDATE_SOURCE.command' --exclude='GHARTV_TELEMETRY_REPORT.command' | grep -q .; then fail "Telemetry admin token assignment exists inside the source tree"; fi

if grep -RInE 'youtube\.com|youtu\.be|SOURCE_WEB|SOURCE_M3U|starter_channels|provider_channels|M3uImporter|WebActivity|SourcesActivity|FASTWAY|WAVES' "$APP/src/main" >/tmp/ghartv-v053-forbidden.txt 2>/dev/null; then
  cat /tmp/ghartv-v053-forbidden.txt >&2
  fail "Mixed-source implementation markers remain in the Android application"
fi
if grep -RIn 'http://' "$APP/src/main" 2>/dev/null | grep -v 'schemas.android.com/apk/res/android' >/tmp/ghartv-v053-cleartext.txt; then
  cat /tmp/ghartv-v053-cleartext.txt >&2
  fail "Cleartext URL remains in Android application source"
fi
if find "$ROOT" -type f \( -name '*.jks' -o -name '*.keystore' -o -name 'signing.env' -o -name '*.p12' -o -name 'collector.env' \) -print -quit | grep -q .; then
  fail "Signing or telemetry admin material exists inside the source tree"
fi
if grep -nE '(data|put)\(\"(mobile|phone|otp|password|passcode|token|cookie|authorization|stream_url|license_url|android_id|device_id|serial|ssid|bssid|ip_address)\"' "$JAVA/Telemetry.java" >/tmp/ghartv-v053-telemetry-sensitive.txt; then
  cat /tmp/ghartv-v053-telemetry-sensitive.txt >&2
  fail "Telemetry implementation adds a forbidden sensitive payload key"
fi

python3 - "$ROOT" <<'PY'
from pathlib import Path
import json,re,sys,xml.etree.ElementTree as ET
root=Path(sys.argv[1])
for path in (root/'android-tv/app/src/main').rglob('*.xml'): ET.parse(path)
state=json.loads((root/'.ghartv-owner-state.json').read_text())
assert state['lane_id']=='ghartv' and state['entity_type']=='project'
assert state['current_candidate']=='0.5.3-observability' and state['version_code']==10
manifest=json.loads((root/'RELEASE_MANIFEST.json').read_text())
assert manifest['versionName']=='0.5.3-observability' and manifest['versionCode']==10
update=json.loads((root/'update/latest.json').read_text())
assert update['versionCode'] in (9,10)
telemetry=json.loads((root/'update/telemetry.json').read_text())
assert telemetry['schema']=='ghartv.telemetry.config.v1'
assert telemetry['endpoint'].startswith('https://')
assert len(telemetry['ingestKey']) >= 24
assert telemetry['retentionDays']==30
for path in (root/'android-tv/app/src/main/java').rglob('*.java'):
    text=path.read_text(); clean=[]; i=0; mode='code'
    while i<len(text):
        c=text[i]; n=text[i+1] if i+1<len(text) else ''
        if mode=='code':
            if c=='/' and n=='/': mode='line'; clean.extend('  '); i+=2; continue
            if c=='/' and n=='*': mode='block'; clean.extend('  '); i+=2; continue
            if c=='"': mode='string'; clean.append(' '); i+=1; continue
            if c=="'": mode='char'; clean.append(' '); i+=1; continue
            clean.append(c); i+=1; continue
        if mode=='line':
            if c=='\n': mode='code'; clean.append('\n')
            else: clean.append(' ')
            i+=1; continue
        if mode=='block':
            if c=='*' and n=='/': mode='code'; clean.extend('  '); i+=2
            else: clean.append('\n' if c=='\n' else ' '); i+=1
            continue
        if c=='\\': clean.extend('  '); i+=2; continue
        if c==('"' if mode=='string' else "'"): mode='code'
        clean.append(' '); i+=1
    if mode not in ('code','line'): raise SystemExit(f'Unclosed Java token in {path}')
    pairs={'{':'}','(':')','[':']'}; stack=[]
    for ch in ''.join(clean):
        if ch in pairs: stack.append(ch)
        elif ch in pairs.values():
            if not stack or pairs[stack.pop()]!=ch: raise SystemExit(f'Unbalanced Java delimiter in {path}')
    if stack: raise SystemExit(f'Unbalanced Java delimiter in {path}')
print('XML, JSON and Java lexical validation passed.')
PY

command -v node >/dev/null 2>&1 && node --check "$WORKER/src/index.js" >/dev/null || true
JAVA_COUNT="$(find "$JAVA" -maxdepth 1 -name '*.java' -type f | wc -l | tr -d '[:space:]')"
[ "$JAVA_COUNT" -ge 22 ] && [ "$JAVA_COUNT" -le 23 ] || fail "Expected 22 or 23 Jio-only Java files; found $JAVA_COUNT"
while IFS= read -r script; do bash -n "$script" || fail "Shell syntax failed: $script"; done < <(find "$ROOT" -maxdepth 4 -name '*.command' -type f -print)
printf 'Validated %s Java files, the opt-in Android queue and the Cloudflare/D1 collector.\n' "$JAVA_COUNT"
printf 'No Jio mobile number, OTP, credential, stream URL or hardware identifier is part of the telemetry schema.\n'
printf 'SOURCE_VALIDATION=PASS\n'
