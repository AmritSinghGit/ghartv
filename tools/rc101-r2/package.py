"""Repackage published RC10.1 with the corrected bootstrap; NEVER rebuild Android."""
from pathlib import Path
import hashlib,json,subprocess,sys,zipfile
R=Path(__file__).resolve().parents[2]
old=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
sha=lambda data:hashlib.sha256(data).hexdigest()
assert sha(old.read_bytes())=='943d508ac540a40c30fafb7bc8a13a2f89dd3bdc2ef9ad350e33e5d865b6ef4b'
with zipfile.ZipFile(old) as z:
 prefix='GHARTV_RC10_1_REVIEW/'
 files={n[len(prefix):]:z.read(n) for n in z.namelist() if n.startswith(prefix) and not n.endswith('/')}
 for line in files['SHA256SUMS'].decode().splitlines():
  h,n=line.split('  ',1);assert sha(files[n])==h,n
expected={n.removeprefix('assets/'):sha(data) for n,data in files.items() if n.startswith('assets/')}
core='GHARTV_SYNC_CURRENT_AND_REPORT.command'
assert sha(files[core])=='7a7bf90fef3ab626d23d51ad72f70439c60b17fd7a64914cd31e05f57e5a98ab'
text=(R/'tools/run_owner_bundle.command.in').read_text().replace('@ARTIFACT_HASHES_REPR@',repr(expected)).replace('@COMMAND_SHA@',sha(files[core]))
compile(text.split("<<'SEED'\n",1)[1].split('\nSEED\n',1)[0],'RC10.1-R2-bootstrap','exec')
files['RUN_GHARTV_REVIEW.command']=text.encode()
readme='''# GharTV Nova RC10.1 — startup repair R2

Run `bash RUN_GHARTV_REVIEW.command` to open the actual review: web player and
private reports first, then original-key signing and the existing Nova emulator.
This is not another audit and not a new Android build. All Android/application,
companion, manifest and core-launcher bytes are unchanged from published RC10.1.

The earlier startup failed before installing anything because its blanket symlink
check rejected macOS /var -> /private/var. R2 accepts only the verified OS-owned
/var and /tmp aliases. Unexpected package/output symlinks remain blocked with
an exact path and a saved, copyable failure handoff. Known earlier launchers are
preserved before replacement; interrupted cache writes cannot poison a retry.

The default command reviews Android. RUN_GHARTV_WEB.command runs just the web;
PREPARE_GHARTV_UPDATE.command signs the update without launching Android.
The web uses http://127.0.0.1:8790/; reports /owner.html; films /flixmomo.html.
Memory pressure may hold a new emulator start; the web and a prepared signed
APK remain available. No other emulator, database or container is stopped.

APK code28 is cloud-compiled and must use the original local signing identity.
No key is uploaded or generated, and no physical TV or household feed changes.
Provider search requires normal human verification in the observed environment.
Do not treat a loaded page, preparation or UI test as accepted live playback.

The public website explains the viewer experience. Engineering receipts belong
in GitHub releases/private review, not in public download copy.
'''
files['README.md']=readme.encode()
delivery_source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R,text=True).strip()
record={'schema':'ghartv.startup-repair.v1','revision':'RC10.1-STARTUP-R2','application_source':'9457654eafe86a08c402c6829c6cae3312c3e196','delivery_source':delivery_source,'version_code':28,'version_name':'0.6.0-rc10.1-web-films','apk_sha256':expected['GharTV-review-unsigned.apk'],'companion_sha256':expected['GharTV-review-companion.zip'],'core_launcher_sha256':sha(files[core]),'protected_assets':'BYTE_IDENTICAL_TO_RC10_1','production_feed_changed':False,'owner_mac_execution':'NOT_EXECUTED','default_action':'WEB_THEN_ANDROID_REVIEW'}
files['DELIVERY_R2.json']=(json.dumps(record,indent=2)+'\n').encode()
files.pop('SHA256SUMS')
files['SHA256SUMS']=''.join(sha(b)+'  '+n+'\n' for n,b in sorted(files.items())).encode()
package=out/'GHARTV_RC10_1_R2_REVIEW.zip';prefix='GHARTV_RC10_1_R2_REVIEW/'
with zipfile.ZipFile(package,'w',zipfile.ZIP_DEFLATED) as z:
 for n,data in sorted(files.items()):
  info=zipfile.ZipInfo(prefix+n,date_time=(2026,9,19,0,0,0));info.external_attr=(0o100700 if n.endswith('.command') else 0o100600)<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
with zipfile.ZipFile(package) as z:
 assert z.testzip() is None
 for n,data in files.items():assert z.read(prefix+n)==data
record.update(bundle_sha256=sha(package.read_bytes()),bundle_bytes=package.stat().st_size)
(out/'DELIVERY_R2.json').write_text(json.dumps(record,indent=2)+'\n')
for n in ('RUN_GHARTV_REVIEW.command','GHARTV_SYNC_CURRENT_AND_REPORT.command'):
 p=out/n;p.write_bytes(files[n]);subprocess.run(['bash','-n',str(p)],check=True)
print(json.dumps(record,indent=2))
