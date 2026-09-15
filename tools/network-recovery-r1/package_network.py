"""Package existing public RC4 bytes, without compiling or signing an APK."""
from pathlib import Path
import hashlib,json,shutil,zipfile
from repair_network import MANIFEST,M
ROOT=Path(__file__).resolve().parent
OUT=Path('/tmp/ghartv-network-release');OUT.mkdir(exist_ok=True)
PACK=OUT/'GHARTV_RC4_NETWORK_RECOVERY_R1';PACK.mkdir(exist_ok=True)
ART={
 'review-manifest.json':'52ec393fb49268f368eb2ce44d209aa433ceac3f4ed18ad5eb135248ac7265d7',
 'GharTV-review-companion.zip':M['companion_sha256'],
 'GharTV-review-unsigned.apk':M['unsigned_sha256'],
 'GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk':'8cff8f85403da5924865fddc687dbff11089d7c498f9863e483a7d8123c1ce13'
}
assets=PACK/'assets';assets.mkdir(exist_ok=True)
command=ROOT/'GHARTV_SYNC_CURRENT_AND_REPORT.command'
command_sha=hashlib.sha256(command.read_bytes()).hexdigest()
assert command_sha=='22fba74fae70ff7523543669d8362dc7661882efa225397eb28037462aa64456'
shutil.copyfile(command,PACK/command.name)
for name,h in ART.items():
 b=(ROOT/'assets'/name).read_bytes();assert hashlib.sha256(b).hexdigest()==h,name
 (assets/name).write_bytes(b)
bootstrap='''#!/bin/bash
set -eu
umask 077
HERE="$(cd "$(dirname "$0")" && pwd)"
PY=""
for candidate in /opt/homebrew/bin/python3 /usr/local/bin/python3 /usr/bin/python3; do
 if [ -x "$candidate" ] && "$candidate" -c 'import sys;raise SystemExit(0 if sys.version_info >= (3,9) else 1)' >/dev/null 2>&1; then PY="$candidate"; break; fi
done
if [ -z "$PY" ] && command -v python3 >/dev/null 2>&1 && python3 -c 'import sys;raise SystemExit(0 if sys.version_info >= (3,9) else 1)' >/dev/null 2>&1; then PY="$(command -v python3)"; fi
if [ -z "$PY" ]; then printf 'Python 3.9 or newer is required. No app, source or signing key changed.\\n'; exit 1; fi
"$PY" - "$HERE" <<'SEED'
from pathlib import Path
import hashlib,os,shutil,sys,tempfile
root=Path(sys.argv[1]);home=Path.home()
expected=ARTIFACTS_MARKER
command_hash='COMMAND_HASH_MARKER'
cache=home/'Library/Application Support/GharTV/owner-review/artifact-cache'
managed=home/'.local/share/ghartv-launcher/current'
def safe(p):
 if any(x.is_symlink() for x in (p,*p.parents)):raise SystemExit('Symlinked output preserved; no overwrite')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
script=root/'GHARTV_SYNC_CURRENT_AND_REPORT.command';safe(script)
if sha(script)!=command_hash:raise SystemExit('Recovery launcher checksum differs; stopped')
for name,h in expected.items():
 p=root/'assets'/name;safe(p)
 if not p.is_file() or sha(p)!=h:raise SystemExit('Recovery package file missing or checksum mismatch: '+name)
for p in (cache,managed):safe(p);p.mkdir(parents=True,exist_ok=True);p.chmod(0o700)
for name,h in expected.items():
 target=cache/h;safe(target)
 if target.exists():
  if sha(target)!=h:raise SystemExit('Existing cache bytes differ; preserved without overwrite')
  continue
 with open(target,'xb') as f:f.write((root/'assets'/name).read_bytes())
 target.chmod(0o600)
target=managed/'GHARTV_SYNC_CURRENT_AND_REPORT.command';safe(target)
known={'f9d11248be07981968cdc0b0e5808cc05daa8d1faccca66cb80b2178129adab2',command_hash}
if target.exists() and sha(target) not in known:raise SystemExit('Managed launcher has advanced or was edited; preserved without overwrite')
fd,tmp=tempfile.mkstemp(prefix='.network-recovery-',dir=managed)
with os.fdopen(fd,'wb') as f:f.write(script.read_bytes())
os.chmod(tmp,0o700);os.replace(tmp,target)
print('All bundled public artifact checksums verified. No GitHub download is needed to prepare this review.')
SEED
export PATH="$(dirname "$PY"):$PATH"
exec /bin/bash "$HOME/.local/share/ghartv-launcher/current/GHARTV_SYNC_CURRENT_AND_REPORT.command" --bundled-review
'''.replace('ARTIFACTS_MARKER',repr(ART)).replace('COMMAND_HASH_MARKER',command_sha)
(PACK/'RUN_GHARTV_REVIEW.command').write_text(bootstrap)
for p in PACK.glob('*.command'):p.chmod(0o700)
readme='''# GharTV RC4 — network recovery R1

This fixes distribution of the SAME 0.6.0-rc4-owner-convergence / code20.
It does not contain a newly built or newly signed application.

1. Extract this archive into Downloads.
2. Open RUN_GHARTV_REVIEW.command in Terminal. It verifies every file, seeds the
   managed artifact cache and uses the existing canonical launcher path.
3. The launcher uses the saved ORIGINAL release key locally and the existing AVD.
   It will not ask you for a signing password, erase logins, change Git branches,
   create another emulator or promote an APK to physical TVs.
4. Review the app; first Enter copies the outcome, second finishes the terminal.

The installation files are included: review manifest, unsigned RC4 APK, browser
companion, and the accepted RC8 APK used ONLY as a signing-certificate reference.
DO NOT manually install either APK from the assets directory. The launcher
selects/signs the reviewed code20 payload and refuses downgrades/signer mismatch.

This mode skips GitHub downloads, current-feed checks, signed-APK publication
and backend deployment during local setup. It reports those as deferred, not
successful. The existing browser service is opened. Live channels, login,
telemetry, remote messages and provider services still require internet access.
The existing installed SDK/JDK17 and Python3.9+ are required; Node is used for
browser review. This ZIP does not install a toolchain or replace missing keys.

Original failure: curl exit28 means a configured download timeout. The older
receipt omitted the requested filename and timing, so the stalled network
stage cannot be established. Network mode now names the file/host, records
safe curl timing/HTTP metrics, retries transient failures at most twice and
verifies local cached artifacts. TLS verification remains enabled. Obsidian
failure evidence is written even when the first network operation fails.

Source: d2f364982ce2972d6a6c75588f206ef098edd65b
Companion source: 7706fe5b34b764ea2ce7466348229d4e6b1df3c1
Owner decision: REVIEW_PENDING
Owner-Mac signing, emulator launch, provider playback and current replication:
NOT VERIFIED by packaging this archive.
'''
(PACK/'README.md').write_text(readme)
checks={str(p.relative_to(PACK)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(PACK.rglob('*')) if p.is_file()}
(PACK/'SHA256SUMS').write_text(''.join(h+'  '+name+'\n' for name,h in checks.items()))
zip_path=OUT/(PACK.name+'.zip')
with zipfile.ZipFile(zip_path,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for p in sorted(PACK.rglob('*')):
  if p.is_file():z.write(p,str(p.relative_to(OUT)))
record={'schema':'ghartv.network-recovery.v1','revision':'CYAN6-NETWORK-R1','version':M['version_name'],'version_code':20,'application_source':M['source_sha'],'command_sha256':command_sha,'bundle_sha256':hashlib.sha256(zip_path.read_bytes()).hexdigest(),'bundle_bytes':zip_path.stat().st_size,'artifact_sha256':ART,'android_or_companion_rebuilt':False,'owner_key_read':False,'production_changed':False,'local_network_function_checks':'PASS','owner_mac_execution':'NOT_PERFORMED','bundled_review_github_publication':'DEFERRED_UNTIL_NETWORKED_RUN','backend_deployment':'DEFERRED_IN_BUNDLED_MODE'}
(OUT/'NETWORK_RECOVERY_R1.json').write_text(json.dumps(record,indent=2)+'\n')
(OUT/(zip_path.name+'.sha256')).write_text(record['bundle_sha256']+'  '+zip_path.name+'\n')
print(json.dumps(record,indent=2))
