"""Focused distribution tests; no Mac, provider, or signing credentials used."""
import ast, hashlib, json, subprocess, tempfile
from pathlib import Path
from types import SimpleNamespace

path=Path(__file__).parent/'GHARTV_SYNC_CURRENT_AND_REPORT.command'
raw=path.read_text()
subprocess.run(['bash','-n',str(path)],check=True)
source=raw.split("<<'PY'\n",1)[1].split('\nPY\n',1)[0]
tree=ast.parse(source)
scope={}
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,(ast.Import,ast.ImportFrom,ast.FunctionDef,ast.ClassDef))],type_ignores=[]),'checked-network-functions','exec'),scope)
with tempfile.TemporaryDirectory(prefix='ghartv-network-check-') as tmp:
 root=Path(tmp)
 for name in ('state/current','state/cache','state/run','launcher','home/Documents/Amrit Executive Memory'):(root/name).mkdir(parents=True)
 import sys
 sys.path.insert(0,str(path.parent))
 from repair_network import MANIFEST,M
 scope.update(SELF=root/'launcher/runner.command',STATE=root/'state',CURRENT=root/'state/current',CACHE=root/'state/cache',RUN=root/'state/run',HOME=root/'home',SOURCE=M['source_sha'],REPO='AmritSinghGit/ghartv',NETWORK_RECOVERY='CYAN6-NETWORK-R1',RUN_ID='test-run',args=SimpleNamespace(bundled_review=False),r={},EMBEDDED_MANIFEST=MANIFEST,ARTIFACTS={'review-manifest.json':hashlib.sha256(MANIFEST.encode()).hexdigest()})
 forbidden=lambda *a,**k: (_ for _ in ()).throw(AssertionError('network unexpectedly called'))
 scope['call']=forbidden
 scope['download']('https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.0-rc4/review-manifest.json',root/'manifest.json')
 assert (root/'manifest.json').read_text()==MANIFEST
 assert scope['r']['last_download']['result']=='VERIFIED_EMBEDDED_MANIFEST'
 print('EMBEDDED_EXACT_MANIFEST_NO_NETWORK=PASS')
 fixture=b'public artifact test bytes';hashvalue=hashlib.sha256(fixture).hexdigest();scope['ARTIFACTS']['fixture.apk']=hashvalue
 cached=scope['CACHE']/hashvalue;cached.write_bytes(fixture)
 scope['args'].bundled_review=True
 scope['download']('https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.0-rc4/fixture.apk',root/'one.apk')
 assert (root/'one.apk').read_bytes()==fixture
 print('BUNDLED_CACHE_CHECKSUM_VERIFY_WITHOUT_NETWORK=PASS')
 cached.write_bytes(b'corrupted')
 try:scope['download']('https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.0-rc4/fixture.apk',root/'two.apk')
 except scope['Stop'] as e:assert 'BUNDLED_ARTIFACT_MISSING' in str(e)
 else:raise AssertionError('corrupt cache accepted')
 assert cached.read_bytes()==b'corrupted' and not (root/'two.apk').exists()
 cached.unlink();scope['args'].bundled_review=False
 calls=[]
 def simulated_curl(argv,**kwargs):
  calls.append(argv)
  if len(calls)==1:return subprocess.CompletedProcess(argv,28,'000|0|0|0|0|20.001|0|0','DO_NOT_LOG_proxy_password')
  out=Path(argv[argv.index('-o')+1]);out.write_bytes(fixture)
  return subprocess.CompletedProcess(argv,0,'200|.1|.2|.3|.4|.5|26|1','')
 scope['call']=simulated_curl
 scope['download']('https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.0-rc4/fixture.apk',root/'three.apk')
 assert len(calls)==2 and '--ipv4' not in calls[0] and '--ipv4' in calls[1] and '--http1.1' in calls[1]
 assert (root/'three.apk').read_bytes()==fixture
 assert 'DO_NOT_LOG' not in (scope['RUN']/'network-last.json').read_text()
 print('TIMEOUT_THEN_BOUNDED_IPV4_HTTP1_RETRY=PASS')
 cached.unlink();calls.clear()
 def fail_curl(argv,**kwargs):calls.append(argv);return subprocess.CompletedProcess(argv,28,'000|0|0|0|0|20|0|0','SECRET_PROXY_DETAIL')
 scope['call']=fail_curl
 try:scope['download']('https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.0-rc4/fixture.apk',root/'four.apk')
 except scope['Stop'] as e:assert 'fixture.apk' in str(e) and 'github.com' in str(e)
 else:raise AssertionError('timeout swallowed')
 assert len(calls)==2 and 'SECRET_PROXY' not in json.dumps(scope['r'])
 calls.clear()
 def bad_curl(argv,**kwargs):calls.append(argv);Path(argv[argv.index('-o')+1]).write_bytes(b'bad');return subprocess.CompletedProcess(argv,0,'200|0|0|0|0|0|3|0','')
 scope['call']=bad_curl
 try:scope['download']('https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.0-rc4/fixture.apk',root/'five.apk')
 except scope['Stop'] as e:assert 'CHECKSUM_MISMATCH' in str(e)
 else:raise AssertionError('wrong bytes accepted')
 assert len(calls)==1 and not (root/'five.apk').exists()
 print('NO_CHECKSUM_BYPASS_NO_SECRET_LOG_AND_TWO_ATTEMPT_CAP=PASS')
 scope['r'].update(emulator='UNCHANGED',phase='ARTIFACT_COMPANION')
 scope['recovery_note']()
 assert Path(scope['r']['obsidian_note']).is_file()
 print('OBSIDIAN_FAILURE_RECEIPT_WITHOUT_NETWORK=PASS')
 scope['args'].bundled_review=True;scope['call']=forbidden
 scope['observe_production']();scope['r'].update(emulator='RC4_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND');scope['publish_review_after_local_success']()
 assert 'NOT_FETCHED' in scope['r']['production_feed'] and 'DEFERRED' in scope['r']['review_release']
 print('BUNDLED_REVIEW_NO_GITHUB_STATUS_OR_PUBLICATION_GATE=PASS')
old=Path(__file__).parent/'original.command'
if old.exists():
 oldtree=ast.parse(old.read_text().split("<<'PY'\n",1)[1].split('\nPY\n',1)[0])
 oldfunc={n.name:ast.dump(n) for n in oldtree.body if isinstance(n,ast.FunctionDef)}
 newfunc={n.name:ast.dump(n) for n in tree.body if isinstance(n,ast.FunctionDef)}
 for n in ('payload','signing_config','configured_sign','sync_checkout','collector_check_and_deploy'):
  assert oldfunc[n]==newfunc[n],n+' altered beyond distribution scope'
 print('SIGNING_CERTIFICATE_PAYLOAD_CHECKOUT_AND_BACKEND_FUNCTIONS_PRESERVED=PASS')
print('NETWORK_RECOVERY_FOCUSED_CHECKS=PASS')
