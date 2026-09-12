#!/bin/bash
set -Eeuo pipefail

ROOT="${GHARTV_PROJECT:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
APP="$ROOT/android-tv/app"
JAVA="$APP/src/main/java/in/ghartv/nova"
BUILD="$APP/build.gradle.kts"
MANIFEST="$APP/src/main/AndroidManifest.xml"

fail(){ printf 'VALIDATION FAILED: %s\n' "$1" >&2; exit 1; }
require_file(){ [ -f "$1" ] || fail "Missing file: $1"; }
require_text(){ grep -Fq "$2" "$1" || fail "Missing marker '$2' in $1"; }
forbid_text(){ if grep -Fq "$2" "$1"; then fail "Forbidden marker '$2' remains in $1"; fi; }

printf 'GharTV Jio Live v0.5.4 source validation\n'
printf '==========================================\n'
printf 'Root: %s\n' "$ROOT"

for file in \
  "$BUILD" "$MANIFEST" "$JAVA/MainActivity.java" "$JAVA/PlayerActivity.java" \
  "$JAVA/Channel.java" "$JAVA/PlaybackInfo.java" "$JAVA/JioApiClient.java" \
  "$JAVA/ChannelRepository.java" "$JAVA/ChannelIndex.java" "$JAVA/WatchHistoryStore.java" \
  "$JAVA/ChannelAdapter.java" "$JAVA/ChipAdapter.java" "$JAVA/TvUi.java" \
  "$JAVA/Telemetry.java" "$JAVA/UpdateManager.java" \
  "$ROOT/.ghartv-owner-state.json" "$ROOT/RELEASE_MANIFEST.json" \
  "$ROOT/PRIVACY.md" "$ROOT/TELEMETRY.md" "$ROOT/PRODUCT_REVIEW_v0.5.4-rc2.md" \
  "$ROOT/REVIEW_AND_PUBLISH_v0.5.4.md" "$ROOT/release-notes-v0.5.4-rc2.md" \
  "$ROOT/GHARTV_LOGS_AND_HEALTH.command" "$ROOT/GHARTV_CLOSE_REVIEW.command" \
  "$ROOT/GHARTV_OPEN_REVIEW.command" "$ROOT/GHARTV_PUBLISH_STABLE_V054.command" \
  "$ROOT/update/latest.json"
do require_file "$file"; done

require_text "$BUILD" 'compileSdk = 36'
require_text "$BUILD" 'targetSdk = 35'
require_text "$BUILD" 'media3-exoplayer:1.10.1'
require_text "$BUILD" 'GHARTV_SIGNING_STORE'
require_text "$MANIFEST" 'android.software.leanback'
require_text "$MANIFEST" 'android:usesCleartextTraffic="false"'

require_text "$JAVA/Channel.java" 'requiresSubscription'
require_text "$JAVA/Channel.java" 'subscriptionHint'
require_text "$JAVA/Channel.java" 'isAvailable()'
require_text "$JAVA/PlaybackInfo.java" 'normalizeAliases()'
require_text "$JAVA/JioApiClient.java" 'status == 403'
require_text "$JAVA/JioApiClient.java" 'authRequired = true'
require_text "$JAVA/ChannelIndex.java" 'VIEW_FOR_YOU = "For you"'
require_text "$JAVA/ChannelIndex.java" 'VIEW_CONTINUE = "Continue"'
require_text "$JAVA/ChannelIndex.java" 'VIEW_RECENT = "Recent"'
require_text "$JAVA/ChannelIndex.java" 'VIEW_AVAILABLE = "Working now"'
require_text "$JAVA/ChannelIndex.java" 'rankedSearch'
require_text "$JAVA/ChannelIndex.java" 'Map<Integer, Channel> byNumber'
require_text "$JAVA/WatchHistoryStore.java" 'local-only successful viewing memory'
require_text "$JAVA/WatchHistoryStore.java" 'recordReady'
require_text "$JAVA/WatchHistoryStore.java" 'continueNumbers'
require_text "$JAVA/WatchHistoryStore.java" 'accountScope'
require_text "$JAVA/ChannelRepository.java" 'new ChannelIndex'
require_text "$JAVA/ChannelRepository.java" 'lastChannelForView'
require_text "$JAVA/ChannelRepository.java" 'ACCESS_UNAVAILABLE_TTL_MS'
require_text "$JAVA/MainActivity.java" 'categoryCounts'
require_text "$JAVA/MainActivity.java" 'gridSpanCount'
require_text "$JAVA/MainActivity.java" 'Reset Continue and For you'
require_text "$JAVA/MainActivity.java" 'EXTRA_SCOPE_NUMBERS'
require_text "$JAVA/PlayerActivity.java" 'automaticNetworkRetries'
require_text "$JAVA/PlayerActivity.java" 'automaticBufferRecoveries'
require_text "$JAVA/PlayerActivity.java" 'Network paused — retrying once'
require_text "$JAVA/PlayerActivity.java" 'historyStore.recordReady'
require_text "$JAVA/PlayerActivity.java" 'Next working channel'
require_text "$JAVA/PlayerActivity.java" 'visible >= 14000L'
require_text "$JAVA/ChannelAdapter.java" 'DiffUtil.calculateDiff'
require_text "$JAVA/ChipAdapter.java" 'DiffUtil.calculateDiff'
require_text "$ROOT/GHARTV_OPEN_REVIEW.command" 'Stable televisions stay on v0.5.3'
require_text "$ROOT/GHARTV_OPEN_REVIEW.command" 'GharTV-Jio-Live-v0.5.4-rc2.apk'
require_text "$ROOT/GHARTV_PUBLISH_STABLE_V054.command" 'Type PUBLISH 0.5.4 to continue'
require_text "$ROOT/GHARTV_PUBLISH_STABLE_V054.command" 'versionCode 12'
require_text "$ROOT/PRIVACY.md" 'Local living-room suggestions'

forbid_text "$JAVA/MainActivity.java" 'repository.access()'
forbid_text "$JAVA/PlayerActivity.java" 'repository.access()'
forbid_text "$JAVA/PlayerActivity.java" 'accessRestricted'
[ ! -e "$JAVA/ChannelAccessStore.java" ] || fail 'Duplicate ChannelAccessStore.java still exists'

if grep -RInE 'youtube\.com|youtu\.be|SOURCE_WEB|SOURCE_M3U|starter_channels|provider_channels|M3uImporter|WebActivity|SourcesActivity|FASTWAY|WAVES' "$APP/src/main" >"${TMPDIR:-/tmp}/ghartv-v054-forbidden.txt" 2>/dev/null; then
  cat "${TMPDIR:-/tmp}/ghartv-v054-forbidden.txt" >&2
  fail 'Mixed-source implementation markers remain'
fi
if grep -RIn 'http://' "$APP/src/main" 2>/dev/null | grep -v 'schemas.android.com/apk/res/android' >"${TMPDIR:-/tmp}/ghartv-v054-cleartext.txt"; then
  cat "${TMPDIR:-/tmp}/ghartv-v054-cleartext.txt" >&2
  fail 'Cleartext URL remains in Android source'
fi
if find "$ROOT" -type f \( -name '*.jks' -o -name '*.keystore' -o -name 'signing.env' -o -name '*.p12' -o -name 'collector.env' \) -print -quit | grep -q .; then
  fail 'Signing or collector credentials exist inside source tree'
fi

python3 - "$ROOT" "${GHARTV_EXPECT_UPDATE_LIVE:-0}" <<'PY'
from pathlib import Path
import json,re,sys,xml.etree.ElementTree as ET
root=Path(sys.argv[1]); require_live=sys.argv[2]=='1'
for path in (root/'android-tv/app/src/main').rglob('*.xml'):
    ET.parse(path)

build=(root/'android-tv/app/build.gradle.kts').read_text(encoding='utf-8')
code=int(re.search(r'versionCode\s*=\s*(\d+)',build).group(1))
name=re.search(r'versionName\s*=\s*"([^"]+)"',build).group(1)
state=json.loads((root/'.ghartv-owner-state.json').read_text(encoding='utf-8'))
manifest=json.loads((root/'RELEASE_MANIFEST.json').read_text(encoding='utf-8'))
update=json.loads((root/'update/latest.json').read_text(encoding='utf-8'))
assert state['lane_id']=='ghartv' and state['entity_type']=='project'
assert state['repository']=='AmritSinghGit/ghartv' and state['branch']=='main'
assert manifest['applicationId']=='in.ghartv.nova'

if code==11:
    assert name=='0.5.4-rc2-living-room'
    assert state['current_candidate']=='0.5.4-rc2-living-room'
    assert state['version_code']==11 and state['candidate_release']=='v0.5.4-rc2'
    assert manifest['release']=='v0.5.4-rc2' and manifest['versionCode']==11
    assert update['versionCode']==10 and update['versionName']=='0.5.3-observability', \
        'review candidate must not advertise itself to stable TVs'
elif code==12:
    assert name=='0.5.4'
    assert state['current_candidate']=='0.5.4' and state['version_code']==12
    assert manifest['release']=='v0.5.4' and manifest['versionCode']==12
    if require_live:
        assert update['versionCode']==12 and update['versionName']=='0.5.4'
    else:
        assert update['versionCode'] in (10,12)
else:
    raise AssertionError(f'unexpected versionCode {code}')

# Lightweight Java lexical validation without an Android classpath.
for path in (root/'android-tv/app/src/main/java').rglob('*.java'):
    text=path.read_text(encoding='utf-8'); clean=[]; i=0; mode='code'
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
print(f'XML, metadata and Java lexical validation passed for {name}.')
PY

# Compile the pure-Java index against small stubs to catch API/type regressions.
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ghartv-v054-index.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/in/ghartv/nova"
cp "$JAVA/ChannelIndex.java" "$TMP/in/ghartv/nova/"
cat > "$TMP/in/ghartv/nova/Channel.java" <<'JAVA'
package in.ghartv.nova;
public final class Channel {
 public static final String ACCESS_UNKNOWN="unknown", ACCESS_AVAILABLE="available", ACCESS_SUBSCRIPTION="subscription", ACCESS_UNAVAILABLE="unavailable";
 public int number; public String id="",name="",category="Other",language="Other",accessState=ACCESS_UNKNOWN; public boolean requiresSubscription,subscriptionHint;
 public String displayNumber(){return number<1000?String.format(java.util.Locale.US,"%03d",number):String.valueOf(number);}
 public boolean isSubscriptionChannel(){return requiresSubscription||subscriptionHint||ACCESS_SUBSCRIPTION.equals(accessState);}
 public boolean isUnavailable(){return ACCESS_UNAVAILABLE.equals(accessState)&&!isSubscriptionChannel();}
 public boolean isAvailable(){return ACCESS_AVAILABLE.equals(accessState);}
 public String accessLabel(){return isUnavailable()?"NEEDS ATTENTION":isSubscriptionChannel()?"SUBSCRIPTION":isAvailable()?"WORKING":"LIVE";}
}
JAVA
cat > "$TMP/in/ghartv/nova/WatchHistoryStore.java" <<'JAVA'
package in.ghartv.nova; import java.util.*;
public final class WatchHistoryStore {
 public List<Integer> recentNumbers(int n){return List.of(101);} public List<Integer> continueNumbers(int n){return List.of(101);}
 public List<Integer> topNumbers(int n){return List.of(101);} public long score(int n){return n==101?1000L:0L;}
}
JAVA
cat > "$TMP/IndexSmoke.java" <<'JAVA'
import in.ghartv.nova.*; import java.util.*;
public final class IndexSmoke {
 public static void main(String[] args){
  Channel c=new Channel(); c.number=101; c.id="a"; c.name="PTC Punjabi"; c.language="Punjabi"; c.category="News"; c.accessState=Channel.ACCESS_AVAILABLE;
  Channel d=new Channel(); d.number=102; d.id="b"; d.name="English Sports"; d.language="English"; d.category="Sports";
  ChannelIndex i=new ChannelIndex(List.of(c,d),Set.of(101),new WatchHistoryStore());
  if(i.byNumber(101)==null||i.filter("For you","ptc").isEmpty()||i.search("101").get(0).number!=101||i.count("Punjabi")!=1) throw new AssertionError();
 }
}
JAVA
javac -d "$TMP/out" $(find "$TMP" -name '*.java')
java -cp "$TMP/out" IndexSmoke

# Compile each quoted Python heredoc in operational scripts before delivery.
python3 - "$ROOT" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1]); blocks=0
for path in root.rglob('*.command'):
    text=path.read_text(encoding='utf-8',errors='replace')
    lines=text.splitlines(); i=0
    while i<len(lines):
        match=re.search(r"<<'([A-Za-z_][A-Za-z0-9_]*)'",lines[i])
        if not match: i+=1; continue
        marker=match.group(1); body=[]; i+=1
        while i<len(lines) and lines[i]!=marker:
            body.append(lines[i]); i+=1
        if i>=len(lines): raise SystemExit(f'Unclosed heredoc {marker} in {path}')
        if marker=='PY':
            compile('\n'.join(body)+'\n',str(path), 'exec'); blocks+=1
        i+=1
print(f'Compiled {blocks} quoted Python heredoc block(s).')
PY

JAVA_COUNT="$(find "$JAVA" -maxdepth 1 -name '*.java' -type f | wc -l | tr -d '[:space:]')"
[ "$JAVA_COUNT" -eq 24 ] || fail "Expected 24 Jio-only Java files; found $JAVA_COUNT"
while IFS= read -r script; do bash -n "$script" || fail "Shell syntax failed: $script"; done < <(find "$ROOT" -maxdepth 3 -name '*.command' -type f -print)
printf 'Validated %s Java files.\n' "$JAVA_COUNT"
printf 'Living-room index, local-only suggestions, responsive guide, scoped surfing and recovery paths are present.\n'
printf 'Stable update boundary is valid.\n'
printf 'SOURCE_VALIDATION=PASS\n'
