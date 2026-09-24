#!/bin/bash
# GharTV existing review entry: same key, same Nova, same web server and browser tabs.
set -euo pipefail
umask 077
if ! command -v python3 >/dev/null 2>&1; then
  printf 'Python 3 is required from your existing Mac development environment. Nothing changed.\n'
  exit 1
fi
python3 - "$@" <<'PY'
"""GharTV's existing review entry: original-key signing, one runtime, reused tabs.
Published script, immutable code33 artifact. No build, key generation or public rollout.
"""
from __future__ import annotations
import ast, datetime, fcntl, hashlib, importlib.util, io, json, os, re, shlex
import shutil, signal, socket, stat, subprocess, sys, tempfile, time, types, urllib.request, urllib.error, zipfile
from pathlib import Path

REPO='AmritSinghGit/ghartv'
SOURCE='39363ee529e9f8f3c00dcab8fdd69b88aa5e175c'
CODE=33
VERSION='0.6.0-rc10.6-remote-cursor'
APK_SHA='9913212502cf72bf7a5823e283d57acfcb7076f7e07804059d98299ef1430d81'
ZIP_SHA='f6cf152804563f1a8f140b189c8d9166e1682c2fd667b70bc32dd9324181d349'
CERT='40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c'
TAG='v0.6.0-rc10.6-remote-cursor'
PACKAGE='in.ghartv.nova'; SERIAL='emulator-5580'; AVD='GharTV_Nova_Manual_google_tv_API36'
HOME=Path.home(); STATE=HOME/'Library/Application Support/GharTV/owner-review'
CACHE=STATE/'artifact-cache'; CURRENT=STATE/'current'; WEBSTATE=HOME/'Library/Application Support/GharTV/web-player'
PUBLIC_URL='https://amritsinghgit.github.io/ghartv/'
WEB_URL='http://127.0.0.1:8790/'
SIGN_KEYS=('GHARTV_SIGNING_STORE','GHARTV_SIGNING_STORE_PASSWORD','GHARTV_SIGNING_KEY_ALIAS','GHARTV_SIGNING_KEY_PASSWORD')
RUN_ID='GHARTV-CYAN-33-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
RUN=STATE/'runs'/RUN_ID
R={'schema':'ghartv.single-review-launch.v1','run_id':RUN_ID,'lane_id':'ghartv','repository':REPO,
   'review_source':SOURCE,'version_code':CODE,'version':VERSION,'unsigned_apk_sha256':APK_SHA,
   'status':'STARTING','phase':'PRECHECK','web_player':'NOT_STARTED','emulator':'UNCHANGED',
   'owner_decision':'REVIEW_PENDING','physical_tv':'NOT_TESTED','provider_playback':'NOT_TESTED',
   'production_changed':False,'analytics_routes':'DISABLED_IN_VIEWER','cleanup_count':0,'cleanup_bytes':0,'signing_mode':'NOT_ATTEMPTED',
   'obsidian':'NOT_WRITTEN','memory_bridge':'NOT_ATTEMPTED','receipt_sync':'NOT_ATTEMPTED'}
class Hold(RuntimeError):pass

def safe(path):
    p=Path(path).absolute()
    if any(q.is_symlink() for q in (p,*p.parents)):raise Hold('SYMLINKED_PATH_PRESERVED')
    if p.exists() and p.stat().st_uid != os.getuid():raise Hold('FILE_OWNER_MISMATCH_PRESERVED')
    return p

def mkdir(p):
    p=safe(p);p.mkdir(parents=True,exist_ok=True,mode=0o700);return p

def digest(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()

def atomic(p, data):
    p=safe(p);mkdir(p.parent)
    raw=data if isinstance(data,bytes) else data.encode() if isinstance(data,str) else (json.dumps(data,indent=2)+'\n').encode()
    fd,tmp=tempfile.mkstemp(prefix='.'+p.name+'-',dir=p.parent)
    try:
        with os.fdopen(fd,'wb') as f:f.write(raw);f.flush();os.fsync(f.fileno())
        os.replace(tmp,p)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)

def load_json(p):
    p=safe(p)
    if not p.is_file() or p.stat().st_size>1000000:raise Hold('LOCAL_METADATA_INVALID')
    return json.loads(p.read_text())

def environment():
    env={k:v for k,v in os.environ.items() if not k.startswith(('GHARTV_SIGNING_','_GHARTV_')) and k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','NODE_OPTIONS','PYTHONPATH','PYTHONSTARTUP')}
    env.update(HOME=str(HOME),PATH=os.environ.get('PATH','')+':/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',GH_PROMPT_DISABLED='1')
    return env

def call(args,timeout=15,check=True,env=None):
    try:p=subprocess.run(list(map(str,args)),stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=timeout,env=env or environment())
    except subprocess.TimeoutExpired:raise Hold('COMMAND_TIMEOUT_'+Path(str(args[0])).name.upper()) from None
    if check and p.returncode:raise Hold('COMMAND_FAILED_'+Path(str(args[0])).name.upper()+'_EXIT_'+str(p.returncode))
    return p

def artifact(name,sha,tag=TAG):
    mkdir(CACHE);dest=safe(CACHE/sha)
    if dest.exists():
        if not dest.is_file() or digest(dest)!=sha:raise Hold('CACHE_BYTES_CHANGED_PRESERVED')
        return dest
    local=HOME/'Downloads'/name
    if local.is_file() and not local.is_symlink() and digest(local)==sha:
        safe(local);atomic(dest,local.read_bytes());return dest
    fd,temp=tempfile.mkstemp(prefix='.public-download-',dir=CACHE);os.close(fd)
    try:
        url=f'https://github.com/{REPO}/releases/download/{tag}/{name}'
        print('Fetching verified '+name+'…',flush=True)
        call(['/usr/bin/curl','--proto','=https','--proto-redir','=https','-fLsS','--connect-timeout','15','--max-time','180','--max-filesize','52428800','--retry','1',url,'-o',temp],timeout=200)
        if digest(temp)!=sha:raise Hold('ARTIFACT_CHECKSUM_FAILED')
        os.replace(temp,dest)
    finally:
        if os.path.exists(temp):os.unlink(temp)
    return dest

def source_module(z,name):
    module=types.ModuleType('ghartv_verified_'+Path(name).stem)
    module.__file__=str(CACHE/ZIP_SHA/name)
    exec(compile(z.read(name),name,'exec'),module.__dict__)
    return module

def signing_values():
    root=HOME/'Library/Application Support/GharTV/signing'
    file=safe(root/'signing.env');key=safe(root/'ghartv-release.jks')
    if not file.is_file() or not key.is_file():raise Hold('EXISTING_SIGNING_CONFIG_MISSING_NO_NEW_KEY_CREATED')
    if file.stat().st_mode & 0o077:raise Hold('SIGNING_ENV_MUST_BE_PRIVATE_MODE_600')
    if file.stat().st_size>16384:raise Hold('SIGNING_CONFIG_TOO_LARGE')
    values={}
    for line in file.read_text().splitlines():
        line=line.strip()
        if line.startswith('export '):line=line[7:].strip()
        name,sep,value=line.partition('=');name=name.strip()
        if not sep or name not in SIGN_KEYS:continue
        if name in values:raise Hold('DUPLICATE_SIGNING_FIELD')
        try:words=shlex.split(value,comments=True,posix=True)
        except ValueError:raise Hold('INVALID_SIGNING_CONFIG') from None
        if len(words)!=1 or not words[0] or any(c in words[0] for c in ('\0','\r','\n')):raise Hold('INVALID_SIGNING_VALUE')
        values[name]=words[0]
    if set(values)!=set(SIGN_KEYS):raise Hold('SIGNING_CONFIG_INCOMPLETE')
    location=values['GHARTV_SIGNING_STORE']
    for prefix in ('${HOME}/','$HOME/','~/'):
        if location.startswith(prefix):location=str(HOME)+'/'+location[len(prefix):];break
    if Path(location)!=key:raise Hold('SIGNING_KEY_IS_NOT_THE_EXISTING_CANONICAL_KEY')
    values['GHARTV_SIGNING_STORE']=str(key)
    return values

def signing_tools(sdk):
    roots=[p for p in (sdk/'build-tools').glob('*') if all((p/n).is_file() for n in ('apksigner','zipalign','aapt'))]
    if not roots:raise Hold('EXISTING_ANDROID_BUILD_TOOLS_NOT_FOUND')
    root=max(roots,key=lambda p:tuple(int(n) for n in re.findall(r'\d+',p.name)))
    env=environment()
    java=env.get('JAVA_HOME')
    if not java or not (Path(java)/'bin/java').is_file():
        found=call(['/usr/libexec/java_home','-v','17'],check=False)
        if found.returncode or not found.stdout.strip():raise Hold('EXISTING_JAVA_17_NOT_FOUND')
        env['JAVA_HOME']=found.stdout.strip()
    return root,env

def payload(p):
    result={}
    with zipfile.ZipFile(p) as z:
        if len(z.infolist())>10000 or sum(i.file_size for i in z.infolist())>120000000:raise Hold('APK_PAYLOAD_BOUNDS')
        seen=set()
        for i in z.infolist():
            if i.filename in seen:raise Hold('DUPLICATE_APK_ENTRY')
            seen.add(i.filename)
            if i.is_dir() or re.fullmatch(r'META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))',i.filename,re.I):continue
            result[i.filename]=hashlib.sha256(z.read(i)).hexdigest()
    return result

def apk_identity(p,tools,env,expected_code=None):
    archive=artifact('GHARTV_CODE33_SOURCE.zip',ZIP_SHA)
    with zipfile.ZipFile(archive) as z:verifier_data=z.read('tools/GharTVApkVerifier.java')
    verifier=RUN/'GharTVApkVerifier.java'
    if verifier.is_file():
        safe(verifier)
        if verifier.read_bytes()!=verifier_data:raise Hold('EXISTING_APK_VERIFIER_CHANGED')
    else:atomic(verifier,verifier_data)
    java=Path(env['JAVA_HOME'])/'bin/java'
    checked=json.loads(call([java,'-Xmx256m','-cp',tools/'lib/apksigner.jar',verifier,p],60,env=env).stdout)
    if checked.get('ok') is not True or checked.get('certificate_sha256')!=[CERT]:raise Hold('ORIGINAL_CERTIFICATE_MISMATCH_NO_INSTALL')
    badging=call([tools/'aapt','dump','badging',p],30,env=env).stdout
    row=next((line for line in badging.splitlines() if line.startswith('package:')), '')
    match=re.search(r"name='([^']+)'\s+versionCode='(\d+)'",row)
    if not match or match[1]!=PACKAGE:raise Hold('APK_PACKAGE_MISMATCH')
    code=int(match[2])
    if expected_code is not None and code!=expected_code:raise Hold('APK_VERSION_MISMATCH')
    return code


def prepare_signed(unsigned,tools,env):
    folder=mkdir(STATE/'verified-review-apks'/SOURCE)
    for p in sorted(folder.glob('*.apk'))[:50]:
        if not re.fullmatch(r'[a-f0-9]{64}\.apk',p.name):continue
        safe(p)
        if digest(p)!=p.stem:raise Hold('SIGNED_CACHE_CHANGED_PRESERVED')
        if payload(p)==payload(unsigned):
            apk_identity(p,tools,env,CODE);R['signing_mode']='REUSED_EXISTING_VERIFIED_CODE33';return p
    values=signing_values();private=env.copy()
    private['_GHARTV_STORE_PASSWORD']=values['GHARTV_SIGNING_STORE_PASSWORD']
    private['_GHARTV_KEY_PASSWORD']=values['GHARTV_SIGNING_KEY_PASSWORD']
    with tempfile.TemporaryDirectory(prefix='.sign-',dir=folder) as temp:
        aligned=Path(temp)/'aligned.apk';signed=Path(temp)/'signed.apk'
        try:
            call([tools/'zipalign','-f','4',unsigned,aligned],60,env=env)
            p=call([tools/'apksigner','sign','--ks',values['GHARTV_SIGNING_STORE'],'--ks-key-alias',values['GHARTV_SIGNING_KEY_ALIAS'],
              '--ks-pass','env:_GHARTV_STORE_PASSWORD','--key-pass','env:_GHARTV_KEY_PASSWORD',
              '--v1-signing-enabled','true','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',signed,aligned],60,False,private)
            if p.returncode:raise Hold('EXISTING_SIGNING_CONFIG_REJECTED_NO_KEY_REPLACEMENT')
            apk_identity(signed,tools,env,CODE)
            if payload(signed)!=payload(unsigned):raise Hold('SIGNED_PAYLOAD_DIFFERS_FROM_CODE33')
            h=digest(signed);dest=folder/(h+'.apk');atomic(dest,signed.read_bytes());R['signing_mode']='EXISTING_LOCAL_KEY_NO_PASSWORD_PROMPT';return dest
        finally:values.clear();private.clear()

def memory_normal():
    return call(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],5,False).stdout.strip()=='1'

def observe_window(z,transport):
    rows=transport.avd_processes();helper=source_module(z,'tools/tv_window.py');selection=helper.classify_processes(rows)
    if selection.get('status')!='TARGET_SELECTED':return {'status':selection.get('status','WINDOW_NOT_CONFIRMED'),'window_observed':False}
    pid=selection['pid']
    # Check PID identity again before requesting activation of that exact process.
    if not any(p==pid for p,command in transport.avd_processes()):raise Hold('NOVA_PROCESS_CHANGED')
    helper.restore_minimized_if_authorized(pid)
    # Reuse only the corrected upstream window metadata probe; never execute/download scrcpy.
    archive=artifact('GHARTV_TV_WINDOW_R1.zip','35d8615f4047c7de87b9d4f386eed225ae29ecb053c0c928dc3c07797e96abd6','v0.6.0-rc10.3.1-tv-window-r1')
    with zipfile.ZipFile(archive) as bundle:
        meta=json.loads(bundle.read('GHARTV_TV_WINDOW_R1/PACKAGE.json'));data=bundle.read('GHARTV_TV_WINDOW_R1/DisplayProbe')
        h=meta['files']['DisplayProbe']
        if hashlib.sha256(data).hexdigest()!=h:raise Hold('WINDOW_HELPER_CHECKSUM_FAILED')
    binary=safe(CACHE/('display-probe-'+h))
    if binary.exists() and digest(binary)!=h:raise Hold('WINDOW_HELPER_CHANGED_PRESERVED')
    if not binary.exists():atomic(binary,data)
    binary.chmod(0o700)
    last={'status':'WINDOW_NOT_CONFIRMED','window_observed':False}
    for _ in range(8):
        last=json.loads(call([binary,str(pid)],8).stdout)
        if last.get('window_observed'):break
        time.sleep(.5)
    return last

def android_review(z,transport,sdk,signed,tools,env):
    adb=sdk/'platform-tools/adb';rows=transport.avd_processes()
    state=transport.devices(call([adb,'devices'],8).stdout)
    if SERIAL not in state and not rows and not memory_normal():raise Hold('MEMORY_PRESSURE_NO_NEW_EMULATOR_SIGNED_APK_PRESERVED')
    adb,started=transport.attach_or_start(sdk,RUN);R['existing_avd_started']=started
    helper=source_module(z,'tools/tv_window.py');selection=helper.classify_processes(transport.avd_processes())
    if selection.get('status') in ('HEADLESS_EMULATOR_PRESERVED','ANDROID_STUDIO_EMBEDDED_WINDOW'):
        # Only an explicitly headless/embedded instance of this exact AVD may be
        # gracefully closed to restore its normal window. No data wipe or second AVD.
        if not memory_normal():raise Hold('EXISTING_AVD_HEADLESS_RESTART_HELD_MEMORY_PRESSURE')
        transport.verify_target(adb);call([adb,'-s',SERIAL,'emu','kill'],12)
        deadline=time.monotonic()+35
        while transport.avd_processes() and time.monotonic()<deadline:time.sleep(1)
        if transport.avd_processes():raise Hold('EXISTING_AVD_STILL_CLOSING_NO_DUPLICATE')
        adb,started=transport.attach_or_start(sdk,RUN);R['headless_avd_gracefully_reopened']=True
    transport.verify_target(adb)
    paths=call([adb,'-s',SERIAL,'shell','pm','path',PACKAGE],10,False).stdout.splitlines()
    installed_hash=None
    if paths:
        if len(paths)!=1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',paths[0]):raise Hold('INSTALLED_PACKAGE_LAYOUT_UNEXPECTED')
        installed=RUN/'prior-installed.apk';call([adb,'-s',SERIAL,'pull',paths[0][8:],installed],60)
        code=apk_identity(installed,tools,env);installed_hash=digest(installed);R['prior_installed_code']=code
        if code>CODE:raise Hold('NEWER_ANDROID_CANDIDATE_PRESERVED_NO_DOWNGRADE')
        if code==CODE and payload(installed)!=payload(signed):raise Hold('DIFFERENT_CODE33_PAYLOAD_PRESERVED')
    if installed_hash!=digest(signed):
        transport.verify_target(adb);result=call([adb,'-s',SERIAL,'install','-r',signed],120)
        if 'Success' not in result.stdout:raise Hold('INSTALL_NOT_CONFIRMED')
        R['apk_installed_this_run']=True
    else:R['apk_installed_this_run']=False
    transport.verify_target(adb)
    paths=call([adb,'-s',SERIAL,'shell','pm','path',PACKAGE],10).stdout.splitlines()
    if len(paths)!=1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',paths[0]):raise Hold('INSTALLED_PATH_NOT_CONFIRMED')
    observed=call([adb,'-s',SERIAL,'shell','sha256sum',paths[0][8:]],20).stdout.split()
    if not observed or observed[0]!=digest(signed):raise Hold('INSTALLED_BYTES_NOT_CONFIRMED')
    call([adb,'-s',SERIAL,'shell','input','keyevent','KEYCODE_WAKEUP'],10)
    call([adb,'-s',SERIAL,'shell','wm','dismiss-keyguard'],10,False)
    result=call([adb,'-s',SERIAL,'shell','am','start','-W','-n',PACKAGE+'/.MainActivity'],30)
    if 'Status: ok' not in result.stdout:raise Hold('ANDROID_ACTIVITY_OPEN_NOT_CONFIRMED')
    R['emulator']='RC33_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND'
    view=observe_window(z,transport);R['mac_window']=view.get('status');R['mac_window_observed']=view.get('window_observed',False);R['mac_window_frontmost']=view.get('app_active',False)
    if not R['mac_window_observed']:raise Hold('NORMAL_NOVA_WINDOW_NOT_CONFIRMED_NO_MIRROR_STARTED')
    return 'REUSED_NORMAL_NOVA_CODE33' if not started else 'OPENED_NORMAL_NOVA_CODE33'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def local_json(url):
    if not re.fullmatch(r'http://127\.0\.0\.1:\d{2,5}/[A-Za-z0-9_/?.=&%\-]*',url):raise Hold('LOCAL_ENDPOINT_REJECTED')
    request=urllib.request.Request(url,headers={'Accept':'application/json'})
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    with opener.open(request,timeout=3) as response:
        raw=response.read(1000001)
        if len(raw)>1000000:raise Hold('LOCAL_RESPONSE_TOO_LARGE')
        return json.loads(raw)

def web_review(z,transport):
    owners=transport._listen_owners(8790)
    if owners:
        if len(owners)!=1 or owners[0].get('uid')!=os.getuid():raise Hold('WEB_PORT_OWNER_UNVERIFIED_NO_SECOND_SERVER')
        cmd=call(['/bin/ps','-p',owners[0]['pid'],'-o','command='],5).stdout.strip()
        needle='/web-player/server.mjs'
        start=cmd.find(str(HOME)+'/');end=cmd.find(needle,start)
        if start<0 or end<0:raise Hold('OTHER_WEB_PROCESS_PRESERVED')
        entry=safe(Path(cmd[start:end+len(needle)]))
        if not entry.is_file() or entry.read_bytes()!=z.read('web-player/server.mjs'):raise Hold('EXISTING_WEB_SOURCE_DIFFERS_PRESERVED')
        health=local_json(WEB_URL+'api/health')
        if health.get('service')!='ghartv-web-player':raise Hold('OTHER_SERVICE_ON_WEB_PORT_PRESERVED')
        if health.get('owner_reader') is not False:raise Hold('WEB_PRIVACY_REVISION_UNVERIFIED')
        R['web_source']=health.get('commit');return 'EXISTING_WEB_REUSED'
    runtime=safe(STATE/'runtime-current');marker=runtime/'.ghartv-managed.json'
    if not marker.is_file():raise Hold('EXISTING_WEB_RUNTIME_NOT_FOUND_NO_NEW_RUNTIME_CREATED')
    meta=load_json(marker);entry=runtime/'web-player/server.mjs'
    # Reopen only known existing installed viewer bytes. Do not overwrite Work changes.
    for name in z.namelist():
        if name.startswith('web-player/') and not any(x in name.split('/') for x in ('test','browser-tools','node_modules')) and not name.endswith('/'):
            p=runtime/name
            if p.is_file() and p.suffix in ('.mjs','.js','.json','.html','.css'):
                safe(p)
                if p.read_bytes()!=z.read(name):raise Hold('EXISTING_WEB_SOURCE_DIFFERS_PRESERVED')
    if not entry.is_file():raise Hold('EXISTING_WEB_ENTRY_MISSING')
    required=runtime/'web-player/node_modules/hls.js/dist/hls.min.js'
    if not required.is_file():raise Hold('EXISTING_WEB_DEPENDENCY_MISSING_NO_REINSTALL')
    node=shutil.which('node',path=environment()['PATH'])
    if not node:raise Hold('EXISTING_NODE_NOT_FOUND')
    if transport._listen_owners(8790):raise Hold('WEB_PORT_CHANGED_NO_DUPLICATE')
    mkdir(WEBSTATE)
    log=safe(WEBSTATE/('server-'+RUN_ID+'.log'))
    env=environment();env.update(GHARTV_WEB_HOST='127.0.0.1',GHARTV_WEB_PORT='8790',GHARTV_WEB_SHA=str(meta.get('source','UNKNOWN')))
    with log.open('xb') as out:
        process=subprocess.Popen([node,str(entry)],stdin=subprocess.DEVNULL,stdout=out,stderr=out,env=env,cwd=entry.parent,start_new_session=True)
    atomic(WEBSTATE/'server.pid',str(process.pid)+'\n')
    for _ in range(25):
        if process.poll() is not None:raise Hold('WEB_START_FAILED_SEE_PRIVATE_SERVER_LOG')
        try:
            health=local_json(WEB_URL+'api/health')
            if health.get('service')=='ghartv-web-player' and health.get('owner_reader') is False:
                R['web_source']=health.get('commit');return 'EXISTING_WEB_REOPENED'
        except Exception:pass
        time.sleep(.25)
    raise Hold('WEB_START_HEALTH_NOT_CONFIRMED_NO_SECOND_SERVER')

def analytics_target():
    registry=HOME/'.local/state/amrit-lane-continuity/registry.json'
    if not registry.is_file():return None
    try:
        safe(registry)
        if registry.stat().st_size>16000000:return None
        data=json.loads(registry.read_text());lanes=data.get('lanes',{});urls=[]
        for lane_id,lane in lanes.items():
            if not isinstance(lane,dict):continue
            if lane_id not in ('operon-analytics','vcnow-analytics-marketing') and lane.get('capability')!='operon.analytics':continue
            for url in lane.get('runtime_urls',[]):
                if isinstance(url,str) and re.fullmatch(r'http://(?:127\.0\.0\.1|localhost):\d{2,5}/[^\s]*',url) and not any(w in url.lower() for w in ('token=','secret=','password=','@','/owner.html')):
                    urls.append(url)
        urls=list(dict.fromkeys(urls));scoped=[u for u in urls if 'ghartv' in u.lower()]
        if len(scoped)==1:return scoped[0]
        if len(urls)==1:return urls[0]
    except Exception:pass
    return None

BROWSER_SCRIPT=r'''
function run(argv) {
  var targets=JSON.parse(argv[0]), names=JSON.parse(argv[1] || '["Safari"]');
  var inventory=[], denied=[], result=[], running=[];
  function origin(u){var m=/^https?:\/\/[^/]+/i.exec(u||'');return m?m[0].toLowerCase():'';}
  function matches(u,t){
    if(t.id==='public')return /^https:\/\/amritsinghgit\.github\.io\/ghartv(?:\/|[?#]|$)/i.test(u||'');
    if(t.id==='viewer')return /^http:\/\/(127\.0\.0\.1|localhost):8790(\/|$)/.test(u||'') && !/\/owner(?:\.html|-api)?(?:\/|$)/.test(u||'');
    if(t.id==='analytics'&&t.url)return origin(u)===origin(t.url)||/^http:\/\/(127\.0\.0\.1|localhost):8790\/owner\.html(?:[?#]|$)/.test(u||'');
    return false;
  }
  for(var n=0;n<names.length;n++){
    console.log('GHARTV_TAB_STAGE_INSPECT_'+names[n]);
    var app;try{app=Application(names[n]);if(!app.running())continue;}catch(uninstalled){continue;}running.push(names[n]);
    try{var wins=app.windows();for(var w=0;w<wins.length;w++){var tabs=wins[w].tabs();for(var t=0;t<tabs.length;t++){
      inventory.push({name:names[n],app:app,win:wins[w],tab:tabs[t],index:t+1,url:names[n]==='Safari'?tabs[t].url():tabs[t].url()});
    }}}catch(e){denied.push(names[n]);}
  }
  // If an active browser could not be inspected, do not risk opening duplicates elsewhere.
  if(denied.length)return JSON.stringify({status:'BROWSER_AUTOMATION_PERMISSION_NEEDED_NO_TABS_CREATED',denied:denied,results:[]});
  console.log('GHARTV_TAB_STAGE_INVENTORY_COMPLETE');
  var chosen=running.indexOf('Safari')>=0?'Safari':running.length?running[0]:'Safari';
  var app=Application(chosen);
  for(var i=0;i<targets.length;i++){
    var target=targets[i], found=null;
    for(var j=0;j<inventory.length;j++){if(matches(inventory[j].url,target)){found=inventory[j];break;}}
    if(found){
      if(target.id==='viewer'&&found.url!==target.url)found.tab.url=target.url;
      if(target.id==='analytics'&&origin(found.url)!==origin(target.url))found.tab.url=target.url;
      if(found.name==='Safari'){found.win.currentTab=found.tab;}else{found.win.activeTabIndex=found.index;}
      found.win.index=1;found.app.activate();result.push({surface:target.id,status:'REUSED_EXISTING_TAB',browser:found.name});continue;
    }
    if(!target.url){result.push({surface:target.id,status:'REGISTERED_URL_NOT_AVAILABLE_NO_TAB_CREATED'});continue;}
    console.log('GHARTV_TAB_STAGE_CREATE_'+target.id);
    if(!app.running()){app.launch();delay(1);}
    var wins=app.windows(),win;
    if(!wins.length){
      if(chosen==='Safari'){app.Document().make();win=app.windows()[0];win.currentTab.url=target.url;}
      else{win=app.Window().make();win.activeTab.url=target.url;}
    }else{
      win=wins[0];var tabs=win.tabs(),blank=null;
      for(var k=0;k<tabs.length;k++){var u=tabs[k].url();if(u===''||u==='about:blank'||u==='favorites://'||u==='chrome://newtab/'||u==='brave://newtab/'){blank={tab:tabs[k],index:k+1};break;}}
      if(blank){blank.tab.url=target.url;if(chosen==='Safari')win.currentTab=blank.tab;else win.activeTabIndex=blank.index;}
      else{var tab=app.Tab({url:target.url});win.tabs.push(tab);if(chosen==='Safari')win.currentTab=win.tabs()[win.tabs().length-1];else win.activeTabIndex=win.tabs().length;}
    }
    win.index=1;app.activate();result.push({surface:target.id,status:'OPENED_ONE_TAB',browser:chosen});
  }
  console.log('GHARTV_TAB_STAGE_DONE');
  return JSON.stringify({status:'BROWSER_TABS_RECONCILED',results:result});
}
'''

def reuse_tabs(targets):
    script=RUN/'reuse-tabs.js';atomic(script,BROWSER_SCRIPT)
    # Deliberately no `open URL` fallback; an Automation denial must not spawn duplicates.
    names=['Safari']
    for name in ('Brave Browser','Google Chrome'):
        if any((base/(name+'.app')).is_dir() for base in (Path('/Applications'),HOME/'Applications')):names.append(name)
    print('macOS may ask to let Terminal control your browser. Allow it to reuse tabs; no security settings are changed automatically.',flush=True)
    try:p=call(['/usr/bin/osascript','-l','JavaScript',script,json.dumps(targets),json.dumps(names)],75,False)
    except Hold:
        return {'status':'BROWSER_AUTOMATION_TIMED_OUT_NO_FALLBACK_TABS','results':[]}
    stages=[line for line in p.stderr.splitlines() if line.startswith('GHARTV_TAB_STAGE_')]
    error_numbers=re.findall(r'\((-?[0-9]+)\)',p.stderr)[-2:]
    atomic(RUN/'browser-stages.json',{'stages':stages,'exit_code':p.returncode,'error_numbers':error_numbers})
    if p.returncode:return {'status':'BROWSER_AUTOMATION_PERMISSION_NEEDED_NO_FALLBACK_TABS','results':[]}
    try:return json.loads(p.stdout)
    except ValueError:return {'status':'BROWSER_RESULT_UNCONFIRMED_NO_FALLBACK_TABS','results':[]}

def persist(z=None):
    R['time_utc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
    atomic(RUN/'receipt.json',R)
    # Only the exact owner-run lock holder writes the existing current receipt.
    atomic(CURRENT/'receipt.json',R)
    note='<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->\n# GharTV current review\n\n'+json.dumps(R,indent=2)+'\n'
    atomic(CURRENT/'light-handoff.md',note)
    vault=HOME/'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
    if vault.is_dir():
        target=vault/'GharTV - Current Progress.md';safe(target)
        if target.is_file():atomic(RUN/'prior-obsidian-note.md',target.read_bytes())
        atomic(target,note);R['obsidian']='WRITTEN_AND_READBACK_VERIFIED' if target.read_text()==note else 'READBACK_FAILED'
    else:R['obsidian']='EXISTING_VAULT_NOT_FOUND'
    # Preserve ordinary receipt mirroring without making analytics/provider requests.
    node=shutil.which('node',path=environment()['PATH'])
    sync=STATE/'runtime-current/web-player/review-sync.mjs'
    if z is not None and node and sync.is_file():
        try:
            if sync.read_bytes()!=z.read('web-player/review-sync.mjs'):raise Hold('MIRROR_SOURCE_CHANGED')
            atomic(CURRENT/'receipt.json',R)
            reply=call([node,sync,'--publish'],25,False)
            obj=json.loads(reply.stdout) if reply.returncode==0 else {}
            R['receipt_sync']=obj.get('status','PENDING')
        except Exception:R['receipt_sync']='PENDING_MIRROR_NOT_CONFIRMED'
    bridge=HOME/'bin/amrit-context'
    if bridge.is_file() and os.access(bridge,os.X_OK) and R.get('obsidian')=='WRITTEN_AND_READBACK_VERIFIED':
        try:
            p=call([bridge,'handoff','--file',CURRENT/'light-handoff.md'],15,False)
            R['memory_bridge']='HANDOFF_EXIT_'+str(p.returncode)+'_REPLICA_UNVERIFIED'
        except Exception:R['memory_bridge']='PENDING_NO_REPLICA_CLAIM'
    atomic(CURRENT/'receipt.json',R);atomic(RUN/'receipt.json',R)
    final_note='<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->\n# GharTV current review\n\n'+json.dumps(R,indent=2)+'\n'
    atomic(CURRENT/'light-handoff.md',final_note)
    if vault.is_dir():atomic(vault/'GharTV - Current Progress.md',final_note)

def main():
    if sys.platform!='darwin':print('Run this command on your Mac. No action taken here.');return 2
    os.umask(0o077)
    if not STATE.is_dir():print('Existing GharTV review state is not present. Nothing was installed or created.');return 2
    mkdir(RUN);mkdir(CURRENT)
    lock=safe(STATE/'owner-run.lock').open('a')
    try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError:print('Another existing GharTV action is running. No duplicate launch.');return 2
    with lock:
        prior=CURRENT/'receipt.json'
        if prior.is_file():
            old=load_json(prior)
            if isinstance(old.get('version_code'),int) and old['version_code']>CODE:print('A newer review is recorded. Code33 will not replace it.');return 2
            atomic(RUN/'previous-receipt.json',prior.read_bytes())
        print('\nGharTV code33 · sign, reopen and reuse · no new project or tabs per rerun\n',flush=True)
        z=None;targets=[{'id':'public','url':PUBLIC_URL}]
        try:
            source=artifact('GHARTV_CODE33_SOURCE.zip',ZIP_SHA);z=zipfile.ZipFile(source)
            transport=source_module(z,'tools/tv_local.py')
            try:
                print('1/4  Signing with your existing local GharTV key…',flush=True)
                sdk=transport.sdk();tools,env=signing_tools(sdk);unsigned=artifact('GharTV-code33-review-unsigned.apk',APK_SHA)
                signed=prepare_signed(unsigned,tools,env);R['signed_apk_sha256']=digest(signed);R['prepared_signed_apk_path']=str(signed)
                R['signing_certificate_sha256']=CERT;R['prepared_signed_apk']='PERSISTED_AND_HASH_VERIFIED_BEFORE_EMULATOR_SELECTION'
                atomic(CURRENT/'prepared-review-artifact.json',{'source':SOURCE,'version_code':CODE,'sha256':digest(signed),'path':str(signed),'certificate_sha256':CERT})
                print('Signed code33 verified. Key/passwords remain on this Mac.',flush=True)
                print('2/4  Reusing or reopening the normal Nova emulator…',flush=True)
                R['android_result']=android_review(z,transport,sdk,signed,tools,env)
            except Exception as e:
                R['android_result']=str(e) if isinstance(e,(Hold,transport.Hold)) else 'ANDROID_'+type(e).__name__.upper()
                R['blocker']='ANDROID_REVIEW_'+R['android_result'];print('Android: '+R['android_result'],flush=True)
            try:
                print('3/4  Reusing or reopening the existing GharTV web server…',flush=True)
                R['web_result']=web_review(z,transport);R['web_player']='HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED';targets.append({'id':'viewer','url':WEB_URL})
            except Exception as e:
                R['web_result']=str(e) if isinstance(e,(Hold,transport.Hold)) else 'WEB_'+type(e).__name__.upper()
                R['blocker']=R.get('blocker') or 'WEB_REVIEW_'+R['web_result'];print('Web: '+R['web_result'],flush=True)
            analytics=analytics_target();R['analytics']='REGISTERED_WORKSPACE_FOUND_AUTH_NOT_PROBED' if analytics else 'PRIVATE_ANALYTICS_NOT_REGISTERED_NO_PUBLIC_OWNER_TAB'
            if analytics:targets.append({'id':'analytics','url':analytics})
            print('4/4  Finding your existing browser tabs before opening anything…',flush=True)
            R['browser_tabs']=reuse_tabs(targets)
            if R.get('mac_window_observed'):
                try:
                    final_window=observe_window(z,transport)
                    R['normal_tv_after_browser']=final_window.get('status','NOT_CONFIRMED')
                    R['mac_window_frontmost']=final_window.get('app_active',False)
                except Exception:R['normal_tv_after_browser']='ACTIVATION_NOT_CONFIRMED_EXISTING_WINDOW_PRESERVED'
            good=R.get('mac_window_observed') is True and R.get('web_player','').startswith('HEALTH_AND_SOURCE_VERIFIED')
            R['status']='REVIEW_READY' if good and R['browser_tabs'].get('status')=='BROWSER_TABS_RECONCILED' and analytics else 'ACTION_REQUIRED'
            if good and not analytics:R['blocker']='PRIVATE_ANALYTICS_ROUTE_NOT_REGISTERED_TV_AND_WEB_READY'
            R['phase']='REVIEW_OPEN' if good else 'PARTIAL_REVIEW'
        except Exception as e:
            R['status']='ACTION_REQUIRED';R['blocker']=str(e) if isinstance(e,Hold) else type(e).__name__.upper()
        finally:
            try:persist(z)
            except Exception:R['continuity']='PENDING_LOCAL_RECEIPT';atomic(RUN/'receipt.json',R)
            if z:z.close()
        print('\nGharTV result: '+R['status'])
        print('Android: '+R.get('android_result','NOT_OPENED'))
        print('Web: '+R.get('web_result','NOT_OPENED'))
        print('Private analytics: '+R.get('analytics','NOT_RESOLVED'))
        print('Browser: '+R.get('browser_tabs',{}).get('status','NOT_OPENED'))
        for item in R.get('browser_tabs',{}).get('results',[]):print('  '+item['surface']+': '+item['status'])
        if R.get('blocker'):print('Note: '+R['blocker'])
        if R.get('prepared_signed_apk_path'):print('Signed update APK: '+R['prepared_signed_apk_path'])
        print('Obsidian: '+R['obsidian']+'; GitHub receipt: '+R['receipt_sync'])
        print('No production update, unrelated shutdown or old-candidate deletion was performed.')
        print('Private receipt: '+str(RUN/'receipt.json'))
        return 0 if R['status']=='REVIEW_READY' else 1

if __name__=='__main__':raise SystemExit(main())

PY
