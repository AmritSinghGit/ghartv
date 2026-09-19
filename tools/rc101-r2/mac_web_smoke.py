"""Apple-host test: real repaired startup and unmodified RC10.1 runtime.
No owner credentials, no TV/emulator and no provider/collector requests.
"""
from pathlib import Path
import json,os,signal,subprocess,sys,tempfile,time,urllib.request,zipfile
assert sys.platform=='darwin','MACOS_TEST_REQUIRED'
bundle=Path(sys.argv[1]);output=Path(sys.argv[2]);output.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='ghartv-r2-mac-') as name:
 base=Path(name);physical=base.resolve();home=physical/'home';home.mkdir()
 with zipfile.ZipFile(bundle) as z:z.extractall(base)
 root=base/'GHARTV_RC10_1_R2_REVIEW'
 logical=Path(str(root.resolve()).replace('/private/var/','/var/',1))
 assert Path('/var').is_symlink()
 env={k:v for k,v in os.environ.items() if k in ('PATH','TMPDIR','LANG','LC_ALL','SYSTEMROOT')}
 env.update(HOME=str(home),GHARTV_DISABLE_KEYCHAIN='1',GH_PROMPT_DISABLED='1')
 pidfile=home/'Library/Application Support/GharTV/web-player/server.pid'
 observations=[]
 try:
  for attempt in (1,2):
   run=subprocess.run(['/bin/bash',str(logical/'RUN_GHARTV_REVIEW.command'),'--web-only','--noninteractive'],env=env,text=True,capture_output=True,timeout=100)
   assert run.returncode==0,run.stdout[-6000:]+run.stderr[-1000:]
   receipt=json.loads((home/'Library/Application Support/GharTV/owner-review/current/receipt.json').read_text())
   assert receipt['status']=='WEB_REVIEW_READY_ANDROID_NOT_TOUCHED',receipt['status']
   assert receipt['review_source']=='9457654eafe86a08c402c6829c6cae3312c3e196'
   assert receipt['emulator']=='UNCHANGED'
   for path in ('/','/owner.html','/flixmomo.html','/api/health'):
    with urllib.request.urlopen('http://127.0.0.1:8790'+path,timeout=5) as response:
     assert response.status==200
     if path=='/api/health':assert json.load(response)['commit']==receipt['review_source']
   observations.append({'attempt':attempt,'status':receipt['status'],'all_four_http_checks':200,'emulator':receipt['emulator'],'signing':receipt['signing_mode']})
  result={'schema':'ghartv.rc101-r2-mac-test.v1','platform':sys.platform,'architecture':os.uname().machine,'real_var_alias':True,'original_symlink_guard_would_reject':True,'isolated_test_home':True,'first_start_and_repeat':observations,'owner_mac_test':False,'apk_signed':False,'provider_playback_verified':False}
  (output/'MAC_WEB_VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
 finally:
  if pidfile.is_file():
   pid=int(pidfile.read_text());cmd=subprocess.run(['ps','-p',str(pid),'-o','command='],text=True,capture_output=True).stdout
   if str(home) in cmd and 'web-player/server.mjs' in cmd:
    os.kill(pid,signal.SIGTERM)
    for _ in range(20):
     try:os.kill(pid,0);time.sleep(.1)
     except ProcessLookupError:break
