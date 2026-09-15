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
files.extend(Path(x) for x in ('GHARTV_LANE_PROGRESS.md','CURRENT_HANDOFF.md','REVIEW_060_RC5.md','tools/GharTVApkVerifier.java'))
with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files):z.write(p,str(p))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
m={'schema':'ghartv.review-manifest.v2','source_sha':source,'branch':'codex/ghartv-remove-auto-preview','pr':1,'version_name':'0.6.0-rc5-navigation-review','version_code':21,'unsigned_sha256':sha(apk),'companion_sha256':sha(bundle),'production_unchanged':True,'owner_signed_apk_sha256':None,'owner_mac_run':'NOT_EXECUTED','ai_super_resolution':'NOT_IMPLEMENTED'}
manifest=out/'review-manifest.json';manifest.write_text(json.dumps(m,indent=2)+'\n')
text=Path('tools/owner_review.command.in').read_text().replace('@SOURCE_SHA@',source).replace('@UNSIGNED_SHA@',sha(apk)).replace('@MANIFEST_SHA@',sha(manifest)).replace('@EMBEDDED_MANIFEST_REPR@',repr(manifest.read_text())).replace('@COMPANION_SHA@',sha(bundle))
assert '@SOURCE_SHA@' not in text and '@UNSIGNED_SHA@' not in text
command=out/'GHARTV_SYNC_CURRENT_AND_REPORT.command';command.write_text(text);command.chmod(0o700)
subprocess.run(['bash','-n',str(command)],check=True)
compile(text.split("<<'PY'\n",1)[1].split('\nPY\n',1)[0],str(command),'exec')
record={**m,'manifest_sha256':sha(manifest),'launcher_sha256':sha(command)}
(out/'DELIVERY.json').write_text(json.dumps(record,indent=2)+'\n')
(out/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in (apk,bundle,manifest,command)))
print('GHARTV_DELIVERY='+json.dumps(record))

# Full local-install bundle: original public artifacts only, never CI signing fixture/keys.
reference=Path('/tmp/ghartv-public-reference.apk')
assert sha(reference)=='8cff8f85403da5924865fddc687dbff11089d7c498f9863e483a7d8123c1ce13'
reference_name='GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk'
public_files={manifest.name:manifest,bundle.name:bundle,apk.name:apk,reference_name:reference}
expected={name:sha(path) for name,path in public_files.items()}
start=Path('tools/run_owner_bundle.command.in').read_text().replace('@ARTIFACT_HASHES_REPR@',repr(expected)).replace('@COMMAND_SHA@',sha(command))
assert '@ARTIFACT_HASHES_REPR@' not in start
starter=out/'RUN_GHARTV_REVIEW.command';starter.write_text(start);starter.chmod(0o700)
subprocess.run(['bash','-n',str(starter)],check=True)
compile(start.split("<<'SEED'\n",1)[1].split('\nSEED\n',1)[0],str(starter),'exec')
readme="# GharTV RC5 — actual review package\n\nVersion0.6.0-rc5-navigation-review / code21. Public TV remains RC8/code17.\nRun RUN_GHARTV_REVIEW.command after verifying the supplied checksum. This seeds the exact local artifact cache and updates the same managed launcher. No internet fetch is needed for the bundled preparation, signing or APK installation. Live channels and the collector still require connectivity. Existing signing.env and release key are reused locally; no new key/password prompt.\n\nThe development checkout stays read-only. The named existing AVD is reused. Existing settings and provider login are not cleared. The installed APK and its signature must match this candidate and the accepted public signer. It opens the same local owner/player service on8790. The local owner reader automatically finds the collector credential; its safe status card shows where the configuration belongs. Never paste credentials into a chat or public issue.\n\nBundled mode does not upload a signed APK, deploy the backend or promote an update. Existing code17 viewers will not receive code21 until owner review and explicit promotion. The screenshot/checks supplied in this package are not proof of your physical-TV playback.\n\nReview filter → channel → CH+/CH− (remains in selection); inspect NOW/NEXT tile rows and UP NEXT inside player; retry one failing channel; inspect owner config status. EPG absence is labelled, not invented. Automatic video preview remains off. The look is static navy/cyan with clearer focus and readable text, not a constant animation.\n\nThe last owner run signed RC4 successfully but its text-format certificate extraction failed before installation. This candidate uses Android's typed cryptographic verifier instead. No bypass of signature or payload checks.\n"
package=out/'GHARTV_RC5_REVIEW.zip';prefix='GHARTV_RC5_REVIEW/'
with zipfile.ZipFile(package,'w',compression=zipfile.ZIP_DEFLATED) as z:
 z.writestr(prefix+'README.md',readme)
 z.write(command,prefix+command.name);z.write(starter,prefix+starter.name)
 for name,path in public_files.items():z.write(path,prefix+'assets/'+name)
 z.writestr(prefix+'SHA256SUMS',sha(command)+'  '+command.name+'\n'+sha(starter)+'  '+starter.name+'\n'+''.join(h+'  assets/'+name+'\n' for name,h in expected.items()))
record.update(bundle_sha256=sha(package),bundle_bytes=package.stat().st_size,starter_sha256=sha(starter))
(out/'DELIVERY.json').write_text(json.dumps(record,indent=2)+'\n')
print('GHARTV_COMPLETE_REVIEW_DELIVERY='+json.dumps(record))
