"""Package an improved web/runtime delivery, retaining the already-signed Android payload."""
from pathlib import Path
import hashlib,io,json,os,re,subprocess,sys,zipfile
R=Path(__file__).resolve().parents[2]
APP_SOURCE=None
APK_HASH=None
PRIOR_HASH='b19c0136d1934ed913c3df5aa7e442917544a4a6fb058c1fd6e88015b8bb3528'
TAG='v0.6.0-rc10.3-in-app'; NAME='GHARTV_RC10_3_REVIEW'
prior,out,source=Path(sys.argv[1]),Path(sys.argv[2]),sys.argv[3]
assert re.fullmatch('[a-f0-9]{40}',source)
APP_SOURCE=source
compiled_apk=Path(sys.argv[4]);APK_HASH=hashlib.sha256(compiled_apk.read_bytes()).hexdigest()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(prior)==PRIOR_HASH,'ORIGINAL_BUNDLE_CHANGED'
out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(prior) as z:
 root='GHARTV_RC10_2_REVIEW/'
 apk=compiled_apk.read_bytes()
 reference=z.read(root+'assets/GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk')
 assert hashlib.sha256(apk).hexdigest()==APK_HASH
 assert hashlib.sha256(reference).hexdigest()=='8cff8f85403da5924865fddc687dbff11089d7c498f9863e483a7d8123c1ce13'
(out/'GharTV-review-unsigned.apk').write_bytes(apk)
refname='GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk'
(out/refname).write_bytes(reference)
excluded={'owner-gateway.mjs','performance-desk.mjs','release-desk.mjs','support-report.mjs','provider-browser-worker.mjs','film-browser-worker.mjs','native-provider.mjs'}
paths=[]
for p in (R/'web-player').rglob('*'):
 rel=p.relative_to(R)
 if not p.is_file() or p.is_symlink() or p.suffix.lower() in ('.ttf','.otf','.woff','.woff2','.pem','.key','.jks') or p.name.startswith('.'):continue
 if p.name in excluded or 'test' in rel.parts or 'browser-tools' in rel.parts:continue
 if 'node_modules' in rel.parts and str(rel) not in ('web-player/node_modules/hls.js/dist/hls.min.js','web-player/node_modules/hls.js/LICENSE') and rel.parts[:4]!=('web-player','browser-tools','node_modules','playwright-core'):continue
 paths.append(rel)
for n in ('GHARTV_LANE_PROGRESS.md','CURRENT_HANDOFF.md','TV_EXPERIENCE_CONTRACT.json','tools/tv_local.py','tools/tv_window.py','tools/performance/host_check.py','tools/GharTVApkVerifier.java','tools/emulator_network_repair.py','tools/release_control.py','docs/privacy.html'):
 paths.append(Path(n))
with zipfile.ZipFile(out/'GharTV-review-companion.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
 for p in sorted(paths):z.write(R/p,str(p))
 z.writestr('REVIEW_RC10_3.md',(R/'tools/rc103/REVIEW.md').read_text())
m={'schema':'ghartv.review-manifest.v2','source_sha':APP_SOURCE,'web_source_sha':source,'delivery_source_sha':source,'branch':'codex/ghartv-remove-auto-preview','pr':1,'version_name':'0.6.0-rc10.3-in-app-films','version_code':29,'delivery_version':'RC10.3-IN-APP','unsigned_sha256':APK_HASH,'companion_sha256':sha(out/'GharTV-review-companion.zip'),'production_unchanged':True,'android_rebuilt':True,'owner_signed_apk_sha256':None,'owner_mac_run':'NOT_EXECUTED_THIS_DELIVERY','analytics_routes':'NOT_SERVED','ai_super_resolution':'NOT_IMPLEMENTED'}
manifest=out/'review-manifest.json';manifest.write_text(json.dumps(m,indent=2)+'\n')
text=(R/'tools/owner_review.command.in').read_text()
for name,val in {'@SOURCE_SHA@':APP_SOURCE,'@WEB_SOURCE_SHA@':source,'@UNSIGNED_SHA@':APK_HASH,'@MANIFEST_SHA@':sha(manifest),'@EMBEDDED_MANIFEST_REPR@':repr(manifest.read_text()),'@COMPANION_SHA@':m['companion_sha256']}.items():text=text.replace(name,val)
assert not re.search('@[A-Z_]+@',text)
command=out/'GHARTV_SYNC_CURRENT_AND_REPORT.command';command.write_text(text);command.chmod(0o700)
public={manifest.name:manifest,'GharTV-review-companion.zip':out/'GharTV-review-companion.zip','GharTV-review-unsigned.apk':out/'GharTV-review-unsigned.apk',refname:out/refname}
expected={n:sha(p) for n,p in public.items()}
start=(R/'tools/run_owner_bundle.command.in').read_text().replace('@ARTIFACT_HASHES_REPR@',repr(expected)).replace('@COMMAND_SHA@',sha(command))
assert not re.search('@[A-Z_]+@',start)
starter=out/'RUN_GHARTV_REVIEW.command';starter.write_text(start);starter.chmod(0o700)
for p,delimiter in ((command,'PY'),(starter,'SEED')):
 subprocess.run(['bash','-n',str(p)],check=True)
 compile(p.read_text().split("<<'"+delimiter+"'\n",1)[1].split('\n'+delimiter+'\n',1)[0],str(p),'exec')
package=out/(NAME+'.zip');prefix=NAME+'/'
members={'README.md':(R/'tools/rc103/REVIEW.md').read_bytes(),command.name:command.read_bytes(),starter.name:starter.read_bytes()}
for n,flag in [('RUN_GHARTV_WEB.command','--web-only'),('PREPARE_GHARTV_UPDATE.command','--prepare-update')]:
 members[n]=('#!/bin/bash\nset -euo pipefail\nHERE="$(cd "$(dirname "$0")" && pwd)"\nexec /bin/bash "$HERE/RUN_GHARTV_REVIEW.command" '+flag+' "$@"\n').encode()
for name,p in public.items():members['assets/'+name]=p.read_bytes()
members['SHA256SUMS']=''.join(hashlib.sha256(v).hexdigest()+'  '+k+'\n' for k,v in sorted(members.items())).encode()
with zipfile.ZipFile(package,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for name,data in sorted(members.items()):z.writestr(prefix+name,data)
with zipfile.ZipFile(package) as z:
 assert z.testzip() is None
 for line in z.read(prefix+'SHA256SUMS').decode().splitlines():
  h,n=line.split('  ',1);assert hashlib.sha256(z.read(prefix+n)).hexdigest()==h
record={**m,'bundle_sha256':sha(package),'bundle_bytes':package.stat().st_size,'launcher_sha256':sha(command),'manifest_sha256':sha(manifest),'starter_sha256':sha(starter)}
(out/'DELIVERY.json').write_text(json.dumps(record,indent=2)+'\n')
entry='''#!/bin/bash
set -euo pipefail
umask 077
d="$(mktemp -d "${TMPDIR:-/tmp}/ghartv-rc103.XXXXXX")"
cleanup(){ cd /; rm -rf -- "$d"; }
trap cleanup EXIT
curl --proto '=https' --proto-redir '=https' -fL --show-error --connect-timeout 20 --max-time 180 --retry 2 'https://github.com/AmritSinghGit/ghartv/releases/download/'''+TAG+'/'+NAME+'''.zip' -o "$d/review.zip"
printf '%s  %s\\n' '''+"'"+sha(package)+"'"+''' "$d/review.zip" | shasum -a 256 -c -
unzip -q "$d/review.zip" -d "$d"
/bin/bash "$d/'''+NAME+'''/RUN_GHARTV_REVIEW.command" "$@"
'''
(out/'GHARTV_REVIEW_RC10_3.command').write_text(entry)
subprocess.run(['bash','-n',str(out/'GHARTV_REVIEW_RC10_3.command')],check=True)
(out/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
print(json.dumps(record,indent=2))
