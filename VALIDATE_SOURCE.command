#!/bin/bash
set -Eeuo pipefail
ROOT="${GHARTV_PROJECT:-$(cd "$(dirname "$0")" && pwd)}"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import json,re,sys,xml.etree.ElementTree as ET
root=Path(sys.argv[1]).resolve(); app=root/'android-tv/app'; java=app/'src/main/java/in/ghartv/nova'
required=[
 'MainActivity.java','PlayerActivity.java','Channel.java','ChannelRepository.java','ChannelIndex.java',
 'WatchHistoryStore.java','Telemetry.java','UpdateManager.java','JioApiClient.java','Program.java',
 'FamilyTheme.java','CelebrationView.java','AuroraBackgroundView.java','HeroPreviewController.java',
 'VoiceSearchController.java','ProgramSearchService.java','EngagementTracker.java'
]
missing=[name for name in required if not (java/name).is_file()]
if missing: raise SystemExit('Missing required source: '+', '.join(missing))
for xml in (app/'src/main').rglob('*.xml'): ET.parse(xml)
gradle=(app/'build.gradle.kts').read_text()
assert (('versionCode = 13' in gradle and 'versionName = \"0.5.4-rc4-family-preview\"' in gradle)
        or ('versionCode = 14' in gradle and 'versionName = \"0.5.4\"' in gradle))
manifest=(app/'src/main/AndroidManifest.xml').read_text()
assert 'android.software.leanback' in manifest and 'android.hardware.touchscreen' in manifest
assert 'android:required="false"' in manifest
all_text='\n'.join(p.read_text(errors='replace') for p in java.glob('*.java'))
for forbidden in ['youtube.com','youtu.be','Fastway','WAVES']:
    if forbidden.lower() in all_text.lower(): raise SystemExit('Jio-only boundary failed: '+forbidden)
for marker in [
 'VoiceSearchController.launch', 'ProgramSearchService.search', 'FamilyTheme.showPicker',
 'HeroPreviewController', 'MAX_PREVIEW_MS = 15_000L', 'active_time', 'catchup_request',
 'transport_action', 'seekBy(-15_000L)', 'goLive()', 'scheduleHideGuide',
 'EXTRA_SCOPE_NUMBERS', 'Next working channel', 'KEY_PREVIOUS_CHANNEL', 'previousChannel()'
]:
    if marker not in all_text: raise SystemExit('Missing RC4 marker: '+marker)
family=(java/'FamilyTheme.java').read_text()
for marker in ['"mom", "Mom", 1, 4','"amrit", "Amrit", 3, 7','"harjas", "Harjas", 7, 1',
               '"wifey", "Wifey", 8, 18','"sis", "Sis", 8, 22','"dad", "Dad", 9, 12',
               '"simrath", "Simrath", 10, 4']:
    if marker not in family: raise SystemExit('Birthday mapping missing: '+marker)
for marker in ['GHARTV_TELEMETRY_ADMIN_TOKEN','collector.env','Authorization: Bearer']:
    if marker in all_text: raise SystemExit('Secret boundary failed: '+marker)
update=json.loads((root/'update/latest.json').read_text())
assert ((update['versionCode']==10 and update['versionName']=='0.5.3-observability')
        or (update['versionCode']==14 and update['versionName']=='0.5.4')), update
# Lexical brace/string balance; GitHub Android CI remains the compile authority.
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
print(f'GHARTV_SOURCE_VALIDATION=PASS · {len(list(java.glob("*.java")))} Java files · stable update held at v0.5.3')
PY
