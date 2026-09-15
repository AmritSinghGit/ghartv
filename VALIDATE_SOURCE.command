#!/bin/bash
set -Eeuo pipefail
ROOT="${GHARTV_PROJECT:-$(cd "$(dirname "$0")" && pwd)}"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import json,re,sys,xml.etree.ElementTree as ET
root=Path(sys.argv[1]).resolve(); app=root/'android-tv/app'; java=app/'src/main/java/in/ghartv/nova'; res=app/'src/main/res'
required=[
 'MainActivity.java','PlayerActivity.java','Channel.java','ChannelRepository.java','ChannelIndex.java',
 'WatchHistoryStore.java','Telemetry.java','UpdateManager.java','JioApiClient.java','Program.java',
 'HeroPreviewController.java','MovieHubActivity.java','PictureShape.java','RemoteControl.java'
]
missing=[name for name in required if not (java/name).is_file()]
if missing: raise SystemExit('Missing required source: '+', '.join(missing))
for xml in (app/'src/main').rglob('*.xml'): ET.parse(xml)
gradle=(app/'build.gradle.kts').read_text()
assert 'versionCode = 22' in gradle and 'versionName = "0.6.0-rc6-family-focus"' in gradle, gradle
manifest=(app/'src/main/AndroidManifest.xml').read_text()
assert 'android.software.leanback' in manifest and 'android.hardware.touchscreen' in manifest
assert 'android:required="false"' in manifest
assert 'android:name=".SplashActivity"' not in manifest and 'android.intent.category.LEANBACK_LAUNCHER' in manifest
all_text='\n'.join(p.read_text(errors='replace') for p in java.glob('*.java'))
for forbidden in ['youtube.com','youtu.be','Fastway','WAVES']:
    if forbidden.lower() in all_text.lower(): raise SystemExit('Jio-only boundary failed: '+forbidden)
for marker in [
 'Update live guide', 'EXTRA_SCOPE_NUMBERS', 'Next working channel', 'KEY_PREVIOUS_CHANNEL',
 'previousChannel()', 'public synchronized int indexedCount()', 'previous_launch_incomplete',
 'RESIZE_MODE_FIT', 'Rajvinder', 'Manu', 'Simrat', 'Owner messages'
]:
    if marker not in all_text: raise SystemExit('Missing RC1 marker: '+marker)
# Check the active product surface only. Historical delivery scripts may retain
# frozen evidence from earlier candidates and must not block the current source.
spell_paths = list(java.glob('*.java'))
for name in [
    'FAMILY_THEME.md', 'FAMILY_PHOTO_NOTICE.md', 'PRODUCT_REVIEW_v0.5.4-rc5.md',
    'release-notes-v0.5.4-rc5.md', 'README.md'
]:
    candidate = root / name
    if candidate.is_file():
        spell_paths.append(candidate)
for p in spell_paths:
    try: text=p.read_text()
    except UnicodeDecodeError: continue
    old_name='Sim'+'rath'
    if old_name in text: raise SystemExit('Old spelling remains in active RC1 surface: '+str(p))
for marker in ['GHARTV_TELEMETRY_ADMIN_TOKEN','collector.env','Authorization: Bearer']:
    if marker in all_text: raise SystemExit('Secret boundary failed: '+marker)
update=json.loads((root/'update/latest.json').read_text())
# A branch runner sees the historical RC5 snapshot; GitHub's PR merge runner
# sees main's separately approved RC8 feed. Reject everything except those two
# exact immutable identities, and never infer that this RC1 candidate is live.
approved_feeds = {
    (14, '0.5.4-rc5-family-photo',
     '6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199',
     'b46b2cd607c309d364d531b5fd9da618cd007f6c'),
    (17, '0.5.4-rc8-pre-birthday-recovery',
     '8cff8f85403da5924865fddc687dbff11089d7c498f9863e483a7d8123c1ce13',
     'de3106e3e97a9147b06347a3d66c4e5923cdbbcc'),
}
identity = (update['versionCode'], update['versionName'], update['sha256'], update['sourceCommit'])
assert identity in approved_feeds, update
assert update['channel']=='production', update
# Lexical balance supplements the real Android build; it is not a compiler.
for path in java.glob('*.java'):
    s=path.read_text(); depth=0; quote=None; esc=False; line=False; block=False; i=0
    while i<len(s):
        c=s[i]; n=s[i+1] if i+1<len(s) else ''
        if line:
            if c=='\n': line=False
        elif block:
            if c=='*' and n=='/': block=False; i+=1
        elif quote:
            if esc: esc=False
            elif c=='\\': esc=True
            elif c==quote: quote=None
        else:
            if c=='/' and n=='/': line=True; i+=1
            elif c=='/' and n=='*': block=True; i+=1
            elif c in ('"',"'"): quote=c
            elif c=='{': depth+=1
            elif c=='}': depth-=1
            if depth<0: raise SystemExit(f'Brace underflow: {path}')
        i+=1
    if depth or quote or block: raise SystemExit(f'Lexical balance failed: {path}')
print(f'GHARTV_SOURCE_VALIDATION=PASS · {len(list(java.glob("*.java")))} Java files · 0.6.0 RC6 family / first-focus review')
PY
