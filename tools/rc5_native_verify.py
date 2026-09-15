"""One native crypto check of shipped Java verifier; disposable CI identity is never uploaded."""
from pathlib import Path
import hashlib,json,os,secrets,subprocess,tempfile,zipfile
SDK=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
helper=Path('tools/GharTVApkVerifier.java').resolve()
reference=Path('/tmp/ghartv-public-reference.apk')
unsigned=Path('android-tv/app/build/outputs/apk/release/app-release-unsigned.apk').resolve()
assert hashlib.sha256(reference.read_bytes()).hexdigest()=='8cff8f85403da5924865fddc687dbff11089d7c498f9863e483a7d8123c1ce13'
def verify(path):
 p=subprocess.run(['java','-cp',str(SDK/'lib/apksigner.jar'),str(helper),str(path)],capture_output=True,text=True,timeout=30)
 return p.returncode,json.loads(p.stdout)
code,baseline=verify(reference)
assert code==0 and baseline['ok'] and baseline['certificate_sha256']==['40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c']
with tempfile.TemporaryDirectory(prefix='ghartv-disposable-crypto-') as tmp:
 tmp=Path(tmp);key=tmp/'fixture.jks';signed=tmp/'signed.apk'
 env=dict(os.environ,GHARTV_TEST_PASS=secrets.token_hex(24))
 subprocess.run(['keytool','-genkeypair','-alias','fixture','-keyalg','RSA','-keysize','2048','-validity','1','-dname','CN=DISPOSABLE_TEST_NOT_GHARTV_RELEASE','-keystore',str(key),'-storepass:env','GHARTV_TEST_PASS','-keypass:env','GHARTV_TEST_PASS','-noprompt'],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 subprocess.run([str(SDK/'apksigner'),'sign','--ks',str(key),'--ks-key-alias','fixture','--ks-pass','env:GHARTV_TEST_PASS','--key-pass','env:GHARTV_TEST_PASS','--v4-signing-enabled','false','--out',str(signed),str(unsigned)],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 code,data=verify(signed);assert code==0 and data['ok'] and data['certificate_sha256']!=baseline['certificate_sha256']
 # Cross-check machine result against native CLI only as validation, never as the delivery parser.
 cli=subprocess.run([str(SDK/'apksigner'),'verify','--print-certs',str(signed)],capture_output=True,text=True,check=True)
 assert data['certificate_sha256'][0] in cli.stdout
 broken=tmp/'tampered.apk'
 with zipfile.ZipFile(signed) as inp,zipfile.ZipFile(broken,'w') as out:
  for item in inp.infolist():
   raw=inp.read(item)
   if item.filename=='classes.dex':raw=raw[:-1]+bytes([raw[-1]^1])
   out.writestr(item,raw)
 code,data=verify(broken);assert code!=0 and not data['ok']
 code,data=verify(unsigned);assert code!=0 and not data['ok']
print('NATIVE_ANDROID_APKSIG_REFERENCE_SIGNED_TAMPERED_UNSIGNED=PASS')
print('DISPOSABLE_KEY_AND_APK_DESTROYED_NOT_PUBLISHED=YES')
