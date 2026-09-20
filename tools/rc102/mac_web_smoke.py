"""Mac upgrade from exact RC10.1 R2 to new viewer; repeated startup plus actual Safari."""
from pathlib import Path
import json,os,signal,subprocess,sys,tempfile,time,urllib.request,urllib.error,zipfile
assert sys.platform=='darwin'
new,prior,output,source,fixture=Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]),sys.argv[4],Path(sys.argv[5])
output.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='ghartv-rc102-mac-') as name:
 base=Path(name).resolve();home=base/'home';home.mkdir()
 for bundle in (prior,new):
  with zipfile.ZipFile(bundle) as z:z.extractall(base)
 env={k:v for k,v in os.environ.items() if k in ('PATH','TMPDIR','LANG','LC_ALL','SYSTEMROOT')}
 env.update(HOME=str(home),GHARTV_DISABLE_KEYCHAIN='1',GH_PROMPT_DISABLED='1')
 pidfile=home/'Library/Application Support/GharTV/web-player/server.pid';observed=[]
 try:
  for folder,expected in [('GHARTV_RC10_1_R2_REVIEW','9457654eafe86a08c402c6829c6cae3312c3e196'),('GHARTV_RC10_2_REVIEW',source),('GHARTV_RC10_2_REVIEW',source)]:
   logical=Path(str(base/folder).replace('/private/var/','/var/',1))
   run=subprocess.run(['/bin/bash',str(logical/'RUN_GHARTV_REVIEW.command'),'--web-only','--noninteractive'],env=env,text=True,capture_output=True,timeout=100)
   (output/('startup-'+str(len(observed))+'.log')).write_text(run.stdout+run.stderr)
   assert run.returncode==0,run.stdout[-6000:]+run.stderr[-1500:]
   receipt=json.loads((home/'Library/Application Support/GharTV/owner-review/current/receipt.json').read_text())
   assert receipt['status']=='WEB_REVIEW_READY_ANDROID_NOT_TOUCHED' and receipt['emulator']=='UNCHANGED',receipt
   with urllib.request.urlopen('http://127.0.0.1:8790/api/health',timeout=5) as response:health=json.load(response)
   assert health['commit']==expected
   if folder=='GHARTV_RC10_2_REVIEW':
    assert receipt['web_source']==source and health['owner_reader'] is False
    assert (home/'Library/Application Support/GharTV/owner-review/current/light-handoff.md').is_file()
    assert 'Press Enter' not in run.stdout
    for route in ['/owner.html','/owner-api/config-status','/owner-api/v1/admin/summary']:
     try:urllib.request.urlopen('http://127.0.0.1:8790'+route,timeout=5);raise AssertionError(route+' unexpectedly served')
     except urllib.error.HTTPError as error:assert error.code==404
   for route in ['/','/flixmomo.html']:
    with urllib.request.urlopen('http://127.0.0.1:8790'+route,timeout=5) as response:assert response.status==200
   observed.append({'delivery':folder,'status':receipt['status'],'health_source':health['commit'],'emulator':receipt['emulator'],'owner_routes':404 if folder=='GHARTV_RC10_2_REVIEW' else 'PREVIOUS_VERSION'})
  report={'status':'PASS','schema':'ghartv.rc102-mac-startup.v1','platform':sys.platform,'architecture':os.uname().machine,'real_var_alias':Path('/var').is_symlink(),'old_to_new_and_repeat':observed,'owner_mac':False,'apk_signed':False,'provider_playback':False,'source':source}
  (output/'MAC_WEB_VALIDATION.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
  subprocess.run([sys.executable,str(Path(__file__).with_name('safari_smoke.py')),str(output),str(fixture)],check=True,timeout=130)
 finally:
  if pidfile.is_file():
   pid=int(pidfile.read_text());cmd=subprocess.run(['ps','-p',str(pid),'-o','command='],capture_output=True,text=True).stdout
   if str(home) in cmd and 'web-player/server.mjs' in cmd:
    os.kill(pid,signal.SIGTERM)
    for _ in range(20):
     try:os.kill(pid,0);time.sleep(.1)
     except ProcessLookupError:break
