#!/bin/bash
set -Eeuo pipefail

ROOT="${GHARTV_PROJECT:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
APP="$ROOT/android-tv/app"
JAVA="$APP/src/main/java/in/ghartv/nova"
MANIFEST="$APP/src/main/AndroidManifest.xml"
BUILD="$APP/build.gradle.kts"

fail() { printf 'VALIDATION FAILED: %s\n' "$1" >&2; exit 1; }
require_file() { [ -f "$1" ] || fail "Missing file: $1"; }
require_text() { grep -Fq "$2" "$1" || fail "Missing expected marker '$2' in $1"; }
forbid_text() { if grep -Fq "$2" "$1"; then fail "Forbidden marker '$2' remains in $1"; fi; }

printf 'GharTV Jio Live v0.5.1 source validation\n'
printf '========================================\n'
printf 'Root: %s\n' "$ROOT"

for file in \
  "$BUILD" "$MANIFEST" \
  "$JAVA/MainActivity.java" "$JAVA/LoginActivity.java" "$JAVA/PlayerActivity.java" \
  "$JAVA/JioApiClient.java" "$JAVA/ChannelRepository.java" "$JAVA/UpdateManager.java" \
  "$APP/src/main/res/xml/file_paths.xml" \
  "$ROOT/.ghartv-owner-state.json" "$ROOT/OPERON_PROJECT.md" "$ROOT/PRIVACY.md" \
  "$ROOT/SECURITY.md" "$ROOT/docs/index.html" "$ROOT/update/latest.json" \
  "$ROOT/.github/workflows/android.yml" "$ROOT/LICENSE"
do
  require_file "$file"
done

require_text "$BUILD" 'versionCode = 8'
require_text "$BUILD" 'versionName = "0.5.1-tv-test"'
require_text "$BUILD" 'compileSdk = 36'
require_text "$BUILD" 'targetSdk = 35'
require_text "$BUILD" 'media3-exoplayer:1.10.1'
require_text "$BUILD" 'GHARTV_SIGNING_STORE'
require_text "$MANIFEST" 'android.software.leanback'
require_text "$MANIFEST" 'android.hardware.touchscreen'
require_text "$MANIFEST" 'android:usesCleartextTraffic="false"'
require_text "$MANIFEST" 'android:allowBackup="false"'
require_text "$MANIFEST" 'android.permission.REQUEST_INSTALL_PACKAGES'
require_text "$MANIFEST" 'androidx.core.content.FileProvider'
require_text "$MANIFEST" '.LoginActivity'
require_text "$MANIFEST" '.PlayerActivity'
forbid_text "$MANIFEST" '.SourcesActivity'
forbid_text "$MANIFEST" '.WebActivity'
forbid_text "$MANIFEST" '<queries>'

for removed in \
  "$JAVA/M3uImporter.java" \
  "$JAVA/SourcesActivity.java" \
  "$JAVA/WebActivity.java" \
  "$APP/src/main/assets/starter_channels.json" \
  "$ROOT/BUNDLED_FREE_CHANNELS.csv" \
  "$ROOT/sample_authorised_provider_playlist.m3u"
do
  [ ! -e "$removed" ] || fail "Removed mixed-source artifact still exists: $removed"
done

if grep -RInE 'youtube\.com|youtu\.be|SOURCE_WEB|SOURCE_M3U|isWeb\(|isM3u\(|starter_channels|provider_channels|M3uImporter|WebActivity|SourcesActivity|FASTWAY|WAVES' "$APP/src/main" >/tmp/ghartv-v051-forbidden.txt 2>/dev/null; then
  cat /tmp/ghartv-v051-forbidden.txt >&2
  fail "Mixed-source implementation markers remain in the Android application"
fi

if grep -RIn 'http://' "$APP/src/main" 2>/dev/null | grep -v 'schemas.android.com/apk/res/android' >/tmp/ghartv-v051-cleartext.txt; then
  cat /tmp/ghartv-v051-cleartext.txt >&2
  fail "Cleartext URL remains in Android application source"
fi

require_text "$JAVA/MainActivity.java" 'UpdateManager.check(this, false)'
require_text "$JAVA/MainActivity.java" 'if (!JioSession.load(this).isPresent())'
require_text "$JAVA/MainActivity.java" 'routeToLogin();'
require_text "$JAVA/LoginActivity.java" 'api.sendOtp(number);'
require_text "$JAVA/LoginActivity.java" 'api.verifyOtp(number, code);'
require_text "$JAVA/LoginActivity.java" 'Verify and open live TV'
require_text "$JAVA/PlayerActivity.java" 'repository.api().fetchPlayback(next)'
require_text "$JAVA/PlayerActivity.java" 'C.WIDEVINE_UUID'
require_text "$JAVA/PlayerActivity.java" 'Reconnect Jio'
require_text "$JAVA/JioApiClient.java" 'AppConfig.OTP_SEND'
require_text "$JAVA/JioApiClient.java" 'AppConfig.OTP_VERIFY'
require_text "$JAVA/JioApiClient.java" 'AppConfig.CHANNELS_V14'
require_text "$JAVA/JioApiClient.java" 'AppConfig.PLAYBACK'
require_text "$JAVA/JioApiClient.java" 'subscriptionRequired = true'
require_text "$JAVA/ChannelRepository.java" 'private static final String JIO_CACHE'
forbid_text "$JAVA/ChannelRepository.java" 'loadStarter'
forbid_text "$JAVA/ChannelRepository.java" 'IMPORTED_CACHE'
require_text "$JAVA/UpdateManager.java" 'MessageDigest.getInstance("SHA-256")'
require_text "$JAVA/UpdateManager.java" 'FileProvider.getUriForFile'
require_text "$JAVA/AppConfig.java" 'AmritSinghGit/ghartv'
require_text "$ROOT/OPERON_PROJECT.md" 'independent **project**, not as an Operon tenant'

if find "$ROOT" -type f \( -name '*.jks' -o -name '*.keystore' -o -name 'signing.env' -o -name '*.p12' \) -print -quit | grep -q .; then
  fail "Signing material exists inside the source tree"
fi

python3 - "$ROOT" <<'PY'
from pathlib import Path
import json, re, sys, xml.etree.ElementTree as ET
root = Path(sys.argv[1])
xml_files = list((root / 'android-tv/app/src/main').rglob('*.xml'))
for path in xml_files:
    ET.parse(path)
state = json.loads((root / '.ghartv-owner-state.json').read_text(encoding='utf-8'))
assert state['lane_id'] == 'ghartv'
assert state['entity_type'] == 'project'
assert state['current_candidate'] == '0.5.1-tv-test'
assert state['version_code'] == 8
assert state['package_id'] == 'in.ghartv.nova'
manifest = json.loads((root / 'RELEASE_MANIFEST.json').read_text(encoding='utf-8'))
assert manifest['versionName'] == '0.5.1-tv-test'
assert manifest['versionCode'] == 8
assert manifest['operonEntityType'] == 'project'
update = json.loads((root / 'update/latest.json').read_text(encoding='utf-8'))
assert update['versionCode'] == 8
assert update['versionName'] == '0.5.1-tv-test'
sha = update['sha256']
assert sha == 'PENDING_RELEASE_BUILD_SHA256' or re.fullmatch(r'[0-9a-f]{64}', sha)

# Lightweight Java lexical pass: remove strings/comments before delimiter checks.
for path in (root / 'android-tv/app/src/main/java').rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    clean = []
    i = 0
    mode = 'code'
    while i < len(text):
        c = text[i]
        n = text[i+1] if i + 1 < len(text) else ''
        if mode == 'code':
            if c == '/' and n == '/': mode = 'line'; clean.extend('  '); i += 2; continue
            if c == '/' and n == '*': mode = 'block'; clean.extend('  '); i += 2; continue
            if c == '"': mode = 'string'; clean.append(' '); i += 1; continue
            if c == "'": mode = 'char'; clean.append(' '); i += 1; continue
            clean.append(c); i += 1; continue
        if mode == 'line':
            if c == '\n': mode = 'code'; clean.append('\n')
            else: clean.append(' ')
            i += 1; continue
        if mode == 'block':
            if c == '*' and n == '/': mode = 'code'; clean.extend('  '); i += 2
            else: clean.append('\n' if c == '\n' else ' '); i += 1
            continue
        if mode in ('string', 'char'):
            if c == '\\': clean.extend('  '); i += 2; continue
            ending = '"' if mode == 'string' else "'"
            if c == ending: mode = 'code'
            clean.append(' '); i += 1
    if mode not in ('code', 'line'):
        raise SystemExit(f'Unclosed Java token in {path}')
    pairs = {'{': '}', '(': ')', '[': ']'}
    stack = []
    for ch in ''.join(clean):
        if ch in pairs: stack.append(ch)
        elif ch in pairs.values():
            if not stack or pairs[stack.pop()] != ch:
                raise SystemExit(f'Unbalanced Java delimiter in {path}')
    if stack: raise SystemExit(f'Unbalanced Java delimiter in {path}')
print(f'Parsed {len(xml_files)} XML files and validated Java lexical balance.')
PY

JAVA_COUNT="$(find "$JAVA" -maxdepth 1 -name '*.java' -type f | wc -l | tr -d '[:space:]')"
[ "$JAVA_COUNT" -eq 19 ] || fail "Expected 19 Jio-only Java files; found $JAVA_COUNT"

while IFS= read -r script; do
  bash -n "$script" || fail "Shell syntax failed: $script"
done < <(find "$ROOT" -maxdepth 2 -name '*.command' -type f -print)

printf 'Validated %s Java files.\n' "$JAVA_COUNT"
printf 'Jio OTP, catalogue, EPG, Media3 HLS/DASH/Widevine, updater and TV remote paths are present.\n'
printf 'No starter web channels, YouTube destinations, provider launchers, signing secrets or M3U importer remain.\n'
printf 'SOURCE_VALIDATION=PASS\n'
