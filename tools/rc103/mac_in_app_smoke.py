"""Native Mac proof in an isolated HOME. Provider page is observed, never modified."""
from pathlib import Path
import json,os,signal,subprocess,sys,tempfile,time,urllib.request,urllib.error,zipfile,queue,threading
assert sys.platform=='darwin'
new,prior,out,source,fixture=Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]),sys.argv[4],Path(sys.argv[5]);out.mkdir(parents=True,exist_ok=True)
script_root=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='ghartv-rc103-mac-') as name:
 base=Path(name).resolve();home=base/'home';home.mkdir()
 for bundle in (prior,new):
  with zipfile.ZipFile(bundle) as z:z.extractall(base)
 env={k:v for k,v in os.environ.items() if k in ('PATH','TMPDIR','LANG','LC_ALL')}
 env.update(HOME=str(home),GHARTV_DISABLE_KEYCHAIN='1',GH_PROMPT_DISABLED='1')
 state=home/'Library/Application Support/GharTV/owner-review';webpid=home/'Library/Application Support/GharTV/web-player/server.pid';observed=[];probe=None
 try:
  for folder,expected in [('GHARTV_RC10_2_REVIEW','0b5f94b1fdac9dfaabe6dc9780bc4df454e9ccb5'),('GHARTV_RC10_3_REVIEW',source),('GHARTV_RC10_3_REVIEW',source)]:
   logical=str(base/folder).replace('/private/var/','/var/',1)
   run=subprocess.run(['/bin/bash',logical+'/RUN_GHARTV_REVIEW.command','--web-only','--noninteractive'],env=env,text=True,capture_output=True,timeout=110)
   assert run.returncode==0,run.stdout[-6000:]+run.stderr[-1000:]
   receipt=json.loads((state/'current/receipt.json').read_text())
   assert receipt['status']=='WEB_REVIEW_READY_ANDROID_NOT_TOUCHED' and receipt['emulator']=='UNCHANGED'
   with urllib.request.urlopen('http://127.0.0.1:8790/api/health',timeout=5) as r:health=json.load(r)
   assert health['commit']==expected and health['owner_reader'] is False
   for route in ['/owner.html','/owner-api/config-status']:
    try:urllib.request.urlopen('http://127.0.0.1:8790'+route,timeout=5);raise AssertionError('Private route served')
    except urllib.error.HTTPError as error:assert error.code==404
   for route in ['/','/flixmomo.html']:
    with urllib.request.urlopen('http://127.0.0.1:8790'+route,timeout=5) as response:assert response.status==200
   observed.append({'delivery':folder,'result':receipt['status'],'source':expected,'emulator':'UNCHANGED','analytics':404})
  (out/'MAC_WEB_VALIDATION.json').write_text(json.dumps({'status':'PASS','architecture':os.uname().machine,'owner_mac':False,'upgrade_and_repeat':observed},indent=2)+'\n')
  subprocess.run([sys.executable,str(script_root/'mac_window_smoke.py'),str(out)],check=True,timeout=100)
  logical=str(base/'GHARTV_RC10_3_REVIEW').replace('/private/var/','/var/',1)
  run=subprocess.run(['/bin/bash',logical+'/RUN_GHARTV_REVIEW.command','--films-only','--noninteractive'],env=env,text=True,capture_output=True,timeout=110)
  assert run.returncode==0,run.stdout[-6000:]+run.stderr[-1000:]
  receipt=json.loads((state/'current/receipt.json').read_text());assert receipt['status']=='FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED',receipt
  assert receipt['film_window'] in ('MAC_WINDOW_FRONTMOST_OBSERVED','MAC_WINDOW_ONSCREEN_OBSERVED')
  filmpid=state/'native-films/current.pid';pid=int(filmpid.read_text())
  cmd=subprocess.run(['/bin/ps','-p',str(pid),'-o','command='],capture_output=True,text=True).stdout.strip()
  exe=state/'runtime-current/web-player/native/GharTVFilmView';assert cmd==str(exe)
  os.kill(pid,signal.SIGTERM)
  for _ in range(30):
   if subprocess.run(['/bin/ps','-p',str(pid),'-o','pid='],capture_output=True,text=True).stdout.strip()=='':break
   time.sleep(.1)
  events=[];messages=queue.Queue()
  probe=subprocess.Popen([str(exe),'--query','Dune'],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,env={'PATH':'/usr/bin:/bin','LANG':'en_US.UTF-8'})
  def reader():
   for line in probe.stdout:
    try:messages.put(json.loads(line[:8192]))
    except ValueError:pass
  threading.Thread(target=reader,daemon=True).start();deadline=time.monotonic()+40;settle=None
  while time.monotonic()<deadline:
   if settle is not None and time.monotonic()>=settle:break
   try:item=messages.get(timeout=1);events.append(item)
   except queue.Empty:continue
   if item.get('event') in ('provider_blocked','navigation_failed'):break
   if item.get('event')=='page_finished' and settle is None:settle=time.monotonic()+8
  assert any(x.get('event')=='window_ready' and x.get('windowVisible') for x in events),'NATIVE_WINDOW_NOT_READY'
  provider='NOT_CONFIRMED'
  if any(x.get('event')=='provider_blocked' for x in events):provider='PROVIDER_DECLINED_EMBEDDED_SESSION'
  elif any(x.get('httpStatus',0)>=400 for x in events):provider='PROVIDER_HTTP_ERROR_OR_VERIFICATION'
  elif any(x.get('event')=='page_finished' and x.get('providerPath')=='search' and not x.get('pageError') for x in events):provider='SEARCH_PAGE_NAVIGATION_OBSERVED_RESULTS_AND_PLAYBACK_UNVERIFIED'
  (out/'NATIVE_FILMS_VALIDATION.json').write_text(json.dumps({'status':'PASS_NATIVE_WINDOW','native_window':receipt['film_window'],'source':source,'events':events,'provider':provider,'search_results_verified':False,'playback_verified':False,'owner_mac':False},indent=2)+'\n')
  probe.terminate();probe.wait(timeout=5);probe=None
  subprocess.run([sys.executable,str(script_root/'safari_smoke.py'),str(out),str(fixture)],check=True,timeout=140)
 finally:
  if probe:probe.terminate();probe.wait(timeout=5)
  for pidfile in [state/'native-films/current.pid',webpid]:
   if not pidfile.is_file():continue
   pid=int(pidfile.read_text());cmd=subprocess.run(['/bin/ps','-p',str(pid),'-o','command='],capture_output=True,text=True).stdout
   if str(home) in cmd and ('GharTVFilmView' in cmd or 'web-player/server.mjs' in cmd):
    try:os.kill(pid,signal.SIGTERM)
    except ProcessLookupError:pass
print('MAC_UPGRADE_NATIVE_WINDOW_AND_SAFARI_CHECKS_COMPLETED')
