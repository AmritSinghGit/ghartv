"""Create exact review artifacts after the checked application source commit exists."""
from pathlib import Path
import hashlib,json,subprocess,zipfile
source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
out=Path('/tmp/ghartv-owner-candidate');out.mkdir(exist_ok=True)
built=Path('android-tv/app/build/outputs/apk/release/app-release-unsigned.apk')
if not built.is_file():raise SystemExit('Expected unsigned release APK; no signing key belongs in this build job')
apk=out/'GharTV-review-unsigned.apk';apk.write_bytes(built.read_bytes())
bundle=out/'GharTV-review-companion.zip'
files=[]
for root in ('docs','web-player','telemetry/worker'):
 for p in Path(root).rglob('*'):
  if not p.is_file() or p.is_symlink():continue
  if any(x in p.parts for x in ('.git','.wrangler','test','node_modules')):
   if str(p) not in ('web-player/node_modules/hls.js/dist/hls.min.js','web-player/node_modules/hls.js/LICENSE'):continue
  if p.name in ('wrangler.toml','.dev.vars','.env','collector.env') or p.suffix in ('.jks','.key','.pem'):continue
  files.append(p)
files.extend(Path(x) for x in ('GHARTV_LANE_PROGRESS.md','CURRENT_HANDOFF.md','REVIEW_060_RC4.md'))
with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files):z.write(p,str(p))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
m={'schema':'ghartv.review-manifest.v2','source_sha':source,'branch':'codex/ghartv-remove-auto-preview','pr':1,'version_name':'0.6.0-rc4-owner-convergence','version_code':20,'unsigned_sha256':sha(apk),'companion_sha256':sha(bundle),'production_unchanged':True,'owner_signed_apk_sha256':None,'owner_mac_run':'NOT_EXECUTED','ai_super_resolution':'NOT_IMPLEMENTED'}
manifest=out/'review-manifest.json';manifest.write_text(json.dumps(m,indent=2)+'\n')
text=Path('tools/owner_review.command.in').read_text().replace('@SOURCE_SHA@',source).replace('@UNSIGNED_SHA@',sha(apk)).replace('@MANIFEST_SHA@',sha(manifest))
assert '@SOURCE_SHA@' not in text and '@UNSIGNED_SHA@' not in text
command=out/'GHARTV_SYNC_CURRENT_AND_REPORT.command';command.write_text(text);command.chmod(0o700)
subprocess.run(['bash','-n',str(command)],check=True)
compile(text.split("<<'PY'\n",1)[1].split('\nPY\n',1)[0],str(command),'exec')
record={**m,'manifest_sha256':sha(manifest),'launcher_sha256':sha(command)}
(out/'DELIVERY.json').write_text(json.dumps(record,indent=2)+'\n')
(out/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in (apk,bundle,manifest,command)))
print('GHARTV_DELIVERY='+json.dumps(record))
