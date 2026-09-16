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
files.extend(Path(x) for x in ('GHARTV_LANE_PROGRESS.md','CURRENT_HANDOFF.md','REVIEW_060_RC7.md','tools/GharTVApkVerifier.java','tools/emulator_network_repair.py','tools/release_control.py'))
with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files):z.write(p,str(p))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
m={'schema':'ghartv.review-manifest.v2','source_sha':source,'branch':'codex/ghartv-remove-auto-preview','pr':1,'version_name':'0.6.0-rc7-network-diagnostics','version_code':23,'unsigned_sha256':sha(apk),'companion_sha256':sha(bundle),'production_unchanged':True,'owner_signed_apk_sha256':None,'owner_mac_run':'NOT_EXECUTED','ai_super_resolution':'NOT_IMPLEMENTED'}
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
readme="# GharTV RC7 — network and diagnostics review\n\n0.6.0-rc7-network-diagnostics / code23. Public TV release remains RC8/code17.\nRun RUN_GHARTV_REVIEW.command after checking the bundle and starter hashes. All APK and signing-reference bytes are bundled; no GitHub source publication or compile step is required on the Mac. Keep the original signing configuration/key, account data and existing AVD. Development checkout stays read-only.\n\nThe command signs and verifies the compiled APK and installs it into the existing named emulator. It performs an explicit, fixed-service DNS/HTTPS check inside Android and compares DNS resolution on the Mac. Only when multiple emulator DNS failures differ from the host, it may gracefully cold-boot the same AVD using already-configured, verified Mac resolvers. No system DNS changes, new public DNS, wipe-data, uninstall or new AVD. A successful HTTPS probe is not live playback or account authorisation. Review the actual network result in the handoff.\n\nThe updated companion replaces only the proven managed service on8790. It preserves Punjabi + category + search scoped navigation. An active internet viewer is protected; this review never starts a tunnel or updates Fabric itself. Latest earlier Fabric share was revoked. Cousin-server hosting remains deferred.\n\nOwner console: Refresh reports explicitly requests summary plus latest1,000 events; no periodic D1 reads. Each response has an independent outcome. Playback health has Find reference and Collect support report. Reference lookup covers latest5,000 collector events in30days, not unsent events or every record. A generated code is not proof of upload. The TV's Diagnostics & privacy shows consent, queue and last successful send.\n\nBecause you requested data-based support, the launcher makes one explicit summary+latest5,000 capture using your existing local collector configuration. Raw collector rows remain only in the private support folder. The shareable GHARTV_SUPPORT.zip contains filtered version-separated error signals and fixed-service network outcomes, not account credentials, installation/device IDs, names, IPs, stream URLs, arbitrary messages or viewing history. Nothing from that ZIP is automatically uploaded to public GitHub. Supply it privately with feedback when ready.\n\nExisting local receipt and Obsidian writes remain automatic. Only the allowlisted technical run receipt is mirrored to the existing PR with readback. Starting the bundled review never publishes an update. In Owner console, BLUE is the last checked household release, GREEN is this single local review, and development is source only. After reviewing TV, web and owner console, use Verify candidate & public release, tick the three confirmations, and choose Approve & publish exact code23. The explicit confirmation binds the run, source and signed checksum; only then can the existing GitHub authorization upload that exact signed APK and atomically advertise it on the public update/download feed without rebuilding. No cloud web hosting, cousin deployment, database update, PR merge or physical-TV installation is included. Existing household TVs offer an update on their next successful check and still require Android installation confirmation; this is not immediate push or silent installation.\n"
package=out/'GHARTV_RC7_REVIEW.zip';prefix='GHARTV_RC7_REVIEW/'
with zipfile.ZipFile(package,'w',compression=zipfile.ZIP_DEFLATED) as z:
 z.writestr(prefix+'README.md',readme)
 z.write(command,prefix+command.name);z.write(starter,prefix+starter.name)
 for name,path in public_files.items():z.write(path,prefix+'assets/'+name)
 z.writestr(prefix+'SHA256SUMS',sha(command)+'  '+command.name+'\n'+sha(starter)+'  '+starter.name+'\n'+''.join(h+'  assets/'+name+'\n' for name,h in expected.items()))
record.update(bundle_sha256=sha(package),bundle_bytes=package.stat().st_size,starter_sha256=sha(starter))
(out/'DELIVERY.json').write_text(json.dumps(record,indent=2)+'\n')
print('GHARTV_COMPLETE_REVIEW_DELIVERY='+json.dumps(record))
