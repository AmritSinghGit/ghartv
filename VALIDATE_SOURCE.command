#!/bin/bash
set -Eeuo pipefail
ROOT="${GHARTV_PROJECT:-$(cd "$(dirname "$0")" && pwd)}"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import json,re,sys,xml.etree.ElementTree as ET
root=Path(sys.argv[1]).resolve(); app=root/'android-tv/app'; java=app/'src/main/java/in/ghartv/nova'; res=app/'src/main/res'
required=[
 'SplashActivity.java','MainActivity.java','PlayerActivity.java','Channel.java','ChannelRepository.java','ChannelIndex.java',
 'WatchHistoryStore.java','Telemetry.java','UpdateManager.java','JioApiClient.java','Program.java',
 'FamilyTheme.java','CelebrationView.java','AuroraBackgroundView.java','HeroPreviewController.java',
 'MovieHubActivity.java','PictureShape.java','VoiceSearchController.java','VoiceSearchActivity.java','PlaybackQualityController.java','ProgramSearchService.java','EngagementTracker.java'
]
missing=[name for name in required if not (java/name).is_file()]
if missing: raise SystemExit('Missing required source: '+', '.join(missing))
for photo in ['family_dad_simrat_splash.webp','family_dad_simrat_backdrop.webp']:
    p=res/'drawable-nodpi'/photo
    if not p.is_file() or p.stat().st_size < 5000: raise SystemExit('Missing family photo asset: '+photo)
for xml in (app/'src/main').rglob('*.xml'): ET.parse(xml)
gradle=(app/'build.gradle.kts').read_text()
assert 'versionCode = 16' in gradle and 'versionName = "0.5.5-rc2-movies-picture"' in gradle, gradle
manifest=(app/'src/main/AndroidManifest.xml').read_text()
assert 'android.software.leanback' in manifest and 'android.hardware.touchscreen' in manifest
assert 'android:required="false"' in manifest
assert 'android:name=".SplashActivity"' in manifest and 'android.intent.category.LEANBACK_LAUNCHER' in manifest
all_text='\n'.join(p.read_text(errors='replace') for p in java.glob('*.java'))
for forbidden in ['youtube.com','youtu.be','Fastway','WAVES']:
    if forbidden.lower() in all_text.lower(): raise SystemExit('Jio-only boundary failed: '+forbidden)
for marker in [
 'VoiceSearchController.launch', 'ProgramSearchService.search', 'FamilyTheme.showPicker',
 'HeroPreviewController', 'AUTO_START_DELAY_MS = 700L', 'MAX_PREVIEW_MS = 15_000L',
 'Auto preview', 'press OK for continuous', 'SplashActivity', 'splashPhotoRes', 'backdropPhotoRes',
 'active_time', 'catchup_request', 'transport_action', 'seekBy(-15_000L)', 'goLive()',
 'scheduleHideGuide', 'EXTRA_SCOPE_NUMBERS', 'Next working channel', 'KEY_PREVIOUS_CHANNEL',
 'previousChannel()', 'public synchronized int indexedCount()'
]:
    if marker not in all_text: raise SystemExit('Missing RC5 marker: '+marker)
if 'repository.indexedCount()' not in (java/'MainActivity.java').read_text():
    raise SystemExit('MainActivity index count contract missing')
family=(java/'FamilyTheme.java').read_text()
for marker in ['"mom", "Mom", 1, 4','"amrit", "Amrit", 3, 7','"harjas", "Harjas", 7, 1',
               '"wifey", "Wifey", 8, 18','"sis", "Sis", 8, 22','"dad", "Dad", 9, 12',
               '"simrat", "Simrat", 10, 4']:
    if marker not in family: raise SystemExit('Birthday mapping missing: '+marker)
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
    if old_name in text: raise SystemExit('Old spelling remains in active RC5 surface: '+str(p))
for marker in ['GHARTV_TELEMETRY_ADMIN_TOKEN','collector.env','Authorization: Bearer']:
    if marker in all_text: raise SystemExit('Secret boundary failed: '+marker)
update=json.loads((root/'update/latest.json').read_text())
assert update['versionCode']==14 and update['versionName']=='0.5.4-rc5-family-photo', update
assert update['sha256']=='6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199', update
assert update['sourceCommit']=='b46b2cd607c309d364d531b5fd9da618cd007f6c', update
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
print(f'GHARTV_SOURCE_VALIDATION=PASS · {len(list(java.glob("*.java")))} Java files · voice/quality RC1 source · production exact RC5 code 14 preserved')
PY
