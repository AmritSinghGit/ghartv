"""Open the existing GharTV TV independently of review/publication readiness.
Only the named AVD, installed package and GharTV-owned logs are touched.
No install, network setting changes, restart of a running AVD, or publication.
"""
from __future__ import annotations
import datetime as dt
import fcntl
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor

HOME = Path.home()
STATE = HOME / 'Library/Application Support/GharTV/owner-review'
RUNTIME = STATE / 'runtime-current'
PACKAGE = 'in.ghartv.nova'
AVD = 'GharTV_Nova_Manual_google_tv_API36'
SERIAL = 'emulator-5580'
SERVICES = {'jio_playback':'jiotvapi.media.jio.com', 'jio_guide':'jiotvapi.cdn.jio.com',
            'updates':'api.github.com', 'collector':'ghartv-telemetry.ghartv-47d9a0.workers.dev'}
REVISION = 'tv-open-1.0.0'
class Hold(RuntimeError): pass

def safe(p):
    p = Path(p).absolute()
    for q in (p, *p.parents):
        if q.is_symlink(): raise Hold('SYMLINK_PRESERVED')
    return p

def read(p):
    p = safe(p); s = p.stat()
    if not p.is_file() or s.st_uid != os.getuid() or s.st_size > 256000: raise Hold('LOCAL_FILE_NOT_TRUSTED')
    return json.loads(p.read_text())

def write(p, value):
    p = safe(p); p.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    raw = value.encode() if isinstance(value, str) else value if isinstance(value, bytes) else (json.dumps(value, indent=2)+'\n').encode()
    t = p.with_name(p.name+'.tmp-'+str(os.getpid()))
    with t.open('xb') as f: os.chmod(t, 0o600); f.write(raw); f.flush(); os.fsync(f.fileno())
    os.replace(t,p)

def call(args, timeout=8):
    env = {k:v for k,v in os.environ.items() if not k.startswith('GHARTV_SIGNING_')}
    return subprocess.run([str(x) for x in args], stdin=subprocess.DEVNULL, capture_output=True,
                          text=True, timeout=timeout, env=env)

def devices(text):
    return {a[0]:a[1] for line in text.splitlines() if len(a:=line.split())==2 and re.fullmatch(r'emulator-\d+',a[0])}

def foreground(text):
    return any(PACKAGE+'/' in line and any(k in line for k in ('topResumedActivity','mResumedActivity','mCurrentFocus')) for line in text.splitlines())

def sdk():
    roots = [HOME/'Library/Android/sdk']
    for key in ('ANDROID_SDK_ROOT','ANDROID_HOME'):
        if os.environ.get(key): roots.append(Path(os.environ[key]))
    for root in roots:
        root = safe(root)
        if (root/'platform-tools/adb').is_file() and (root/'emulator/emulator').is_file(): return root
    raise Hold('EXISTING_ANDROID_SDK_NOT_FOUND')

def avd_processes():
    # Select only emulator/QEMU owned by this account and the exact -avd argument.
    rows = call(['/bin/ps','-axo','pid=,uid=,command=']).stdout.splitlines(); found=[]
    for line in rows:
        parts = line.strip().split(None,2)
        if len(parts)!=3 or parts[1]!=str(os.getuid()): continue
        cmd=parts[2]
        if re.search(r'(?:^|/)qemu-system-[^ ]+|/emulator(?:\s|$)',cmd) and re.search(r'(?:-avd\s+|@)'+re.escape(AVD)+r'(?:\s|$)',cmd):
            found.append((int(parts[0]),cmd))
    return found

def verify_target(adb):
    p=call([adb,'-s',SERIAL,'emu','avd','name'])
    names=p.stdout.splitlines()
    if p.returncode or not names or names[0].strip()!=AVD: raise Hold('AVD_IDENTITY_NOT_CONFIRMED')

def attach_or_start(root, run):
    adb=root/'platform-tools/adb'; p=call([adb,'devices'])
    if p.returncode: raise Hold('ADB_UNAVAILABLE')
    connected=devices(p.stdout)
    # Also prevent duplicate AVD at a noncanonical port.
    for serial,status in connected.items():
        if status=='device' and serial!=SERIAL:
            names=call([adb,'-s',serial,'emu','avd','name']).stdout.splitlines()
            if names and names[0].strip()==AVD: raise Hold('NAMED_AVD_RUNNING_ON_OTHER_PORT_PRESERVED')
    child=None; started=False
    if SERIAL in connected:
        if connected[SERIAL]!='device': raise Hold('EXISTING_EMULATOR_OFFLINE_NOT_DUPLICATED')
        verify_target(adb)
    else:
        procs=avd_processes()
        if procs: raise Hold('EXISTING_EMULATOR_STARTING_OR_HIDDEN_NOT_DUPLICATED')
        listed=call([root/'emulator/emulator','-list-avds'],10)
        if listed.returncode or AVD not in listed.stdout.splitlines(): raise Hold('EXISTING_NAMED_AVD_NOT_FOUND')
        for port in (5580,5581):
            with socket.socket() as s:
                try: s.bind(('127.0.0.1',port))
                except OSError: raise Hold('CANONICAL_EMULATOR_PORT_BUSY') from None
        # Cold start only when no existing process/device. Never erase user data.
        log=safe(run/'emulator-start.private.log')
        with log.open('xb') as out:
            os.chmod(log,0o600)
            child=subprocess.Popen([str(root/'emulator/emulator'),'-avd',AVD,'-port','5580',
                '-no-snapshot-load','-no-snapshot-save'], stdin=subprocess.DEVNULL, stdout=out, stderr=out,
                cwd=root/'emulator', start_new_session=True)
        started=True
    deadline=time.monotonic()+150
    while time.monotonic()<deadline:
        if child and child.poll() is not None: raise Hold('EMULATOR_PROCESS_EXITED_SEE_PRIVATE_LOG')
        try:
            if call([adb,'-s',SERIAL,'shell','getprop','sys.boot_completed'],4).stdout.strip()=='1':
                verify_target(adb); return adb,started
        except subprocess.TimeoutExpired: pass
        time.sleep(2)
    raise Hold('EMULATOR_BOOT_TIMEOUT_PROCESS_PRESERVED')

def installed_identity(adb, receipt):
    paths=call([adb,'-s',SERIAL,'shell','pm','path',PACKAGE]).stdout.splitlines()
    if len(paths)!=1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',paths[0]): raise Hold('INSTALLED_GHARTV_NOT_FOUND_NO_INSTALL_ATTEMPTED')
    result=call([adb,'-s',SERIAL,'shell','sha256sum',paths[0][8:]],15)
    digest=result.stdout.split()[0] if result.stdout.split() else ''
    if not re.fullmatch('[a-f0-9]{64}',digest) or digest!=receipt.get('signed_apk_sha256'): raise Hold('INSTALLED_APK_DIFFERS_FROM_SAVED_REVIEW')
    return digest

def open_activity(adb):
    call([adb,'-s',SERIAL,'shell','input','keyevent','KEYCODE_WAKEUP'])
    call([adb,'-s',SERIAL,'shell','wm','dismiss-keyguard'])
    p=call([adb,'-s',SERIAL,'shell','am','start','-W','-a','android.intent.action.MAIN',
            '-c','android.intent.category.LAUNCHER','-n',PACKAGE+'/.MainActivity'],20)
    if p.returncode or 'Error:' in p.stdout or 'Error:' in p.stderr: raise Hold('TV_ACTIVITY_START_FAILED')
    for _ in range(8):
        if foreground(call([adb,'-s',SERIAL,'shell','dumpsys','activity','activities']).stdout):return
        time.sleep(.5)
    raise Hold('TV_ACTIVITY_NOT_FOREGROUND')

def request_window():
    procs=avd_processes()
    qemu=[pid for pid,cmd in procs if 'qemu-system-' in cmd]
    if len(qemu)!=1: return 'MAC_WINDOW_NOT_CONFIRMED'
    # AppKit activation does not grant Accessibility or send remote-control keystrokes.
    script='ObjC.import("AppKit"); var a=$.NSRunningApplication.runningApplicationWithProcessIdentifier('+str(qemu[0])+'); if(a.isNil()) false; else a.activateWithOptions(3);'
    try:
        p=call(['/usr/bin/osascript','-l','JavaScript','-e',script],5)
        return 'MAC_ACTIVATION_ACCEPTED' if p.returncode==0 and p.stdout.strip()=='true' else 'MAC_WINDOW_NOT_CONFIRMED'
    except Exception:return 'MAC_WINDOW_NOT_CONFIRMED'

def host_dns(host):
    try:
        p=call([sys.executable,'-c','import socket,sys;socket.getaddrinfo(sys.argv[1],443);print("RESOLVED")',host],5)
        return 'RESOLVED' if p.returncode==0 else 'UNAVAILABLE'
    except subprocess.TimeoutExpired:return 'TIMED_OUT'

def device_dns(adb,host):
    try:
        p=call([adb,'-s',SERIAL,'shell','ping','-c','1','-W','1',host],6);text=p.stdout+' '+p.stderr
    except subprocess.TimeoutExpired as e:
        text=e.stdout or b'';text=text.decode(errors='replace') if isinstance(text,bytes) else text
        return 'RESOLVED' if re.search(r'PING[^\n]*\([0-9a-fA-F:.]+\)',text) else 'TIMED_OUT'
    if re.search(r'PING[^\n]*\([0-9a-fA-F:.]+\)',text):return 'RESOLVED'
    return 'UNAVAILABLE' if re.search('unknown host|bad address|name.*not known|name resolution',text,re.I) else 'NOT_CONFIRMED'

def capture(adb,run):
    report={'schema':'ghartv.local-launch-diagnostics.v1','observed_at':dt.datetime.now(dt.timezone.utc).isoformat(),
            'no_provider_credentials_used':True,'network_settings_changed':False,'playback_verified':False}
    with ThreadPoolExecutor(max_workers=4) as pool:
        report['host_dns']=dict(zip(SERVICES,pool.map(host_dns,SERVICES.values())))
        # Independent observations always run, even when the app probe is missing.
        report['emulator_dns']=dict(zip(SERVICES,pool.map(lambda h:device_dns(adb,h),SERVICES.values()))) if adb else {k:'ADB_NOT_AVAILABLE' for k in SERVICES}
    settings={}
    for name in ('airplane_mode_on','wifi_on','private_dns_mode'):
        try:
            if not adb:raise Hold('ADB_NOT_AVAILABLE')
            settings[name]=call([adb,'-s',SERIAL,'shell','settings','get','global',name],4).stdout.strip()[:40]
        except Exception:settings[name]='NOT_READ'
    report['network_settings']=settings
    try:
        if not adb:raise Hold('ADB_NOT_AVAILABLE')
        report['emulator_clock_offset_seconds']=int(call([adb,'-s',SERIAL,'shell','date','+%s'],4).stdout.strip())-round(time.time())
    except Exception:report['emulator_clock_offset_seconds']=None
    try:pid=call([adb,'-s',SERIAL,'shell','pidof',PACKAGE]).stdout.strip() if adb else ''
    except Exception:pid=''
    if re.fullmatch(r'[1-9]\d*',pid):
        try:
            log=call([adb,'-s',SERIAL,'logcat','-d','-t','400','--pid='+pid,'-v','threadtime'],8).stdout[-524288:]
            write(run/'android-app.private.log',log)
            report['app_log_scope']='CURRENT_GHARTV_PID_LAST_400_BUFFERED_LINES_NOT_FULL_HISTORY'
        except Exception:report['app_log_scope']='APP_LOG_UNAVAILABLE'
    else:report['app_log_scope']='APP_PROCESS_NOT_FOUND'
    web=HOME/'Library/Application Support/GharTV/web-player/server.log'
    try:
        safe(web);info=web.stat()
        if web.is_file() and info.st_uid==os.getuid():
            with web.open('rb') as f:f.seek(max(0,info.st_size-131072));write(run/'web-player.private.log',f.read())
    except OSError:pass
    prior=read(STATE/'current/receipt.json').get('run_id','')
    if re.fullmatch(r'GHARTV-CYAN-[A-Za-z0-9-]+',prior):
        for name in ('NETWORK_CHECK.json','cleanup.json'):
            p=STATE/'runs'/prior/name
            if p.exists():
                try:write(run/('previous-'+name),read(p))
                except Exception:report['previous_'+name]='READ_UNAVAILABLE'
    write(run/'LOCAL_DIAGNOSTICS.json',report)
    archive=run/'GHARTV_LOCAL_LOGS_PRIVATE.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        os.chmod(archive,0o600)
        for p in run.iterdir():
            if p.is_file() and p!=archive and (p.suffix=='.json' or p.name.endswith('.private.log')):z.write(p,p.name)
    with zipfile.ZipFile(archive) as z:
        if z.testzip():raise Hold('PRIVATE_ARCHIVE_VERIFICATION_FAILED')
    return archive

def main_action(action, expected=None):
    if sys.platform!='darwin':return {'ok':False,'error':'MAC_ONLY_NO_ACTION'}
    run_id='GHARTV-CYAN-12-OPEN-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
    run=safe(STATE/'runs'/run_id);run.mkdir(parents=True,mode=0o700)
    adb=None
    result={'ok':False,'schema':'ghartv.tv-open.v1','tool_revision':REVISION,'run_id':run_id,
            'apk_changed':False,'production_changed':False,'playback_verified':False,'stage':'START'}
    try:
        with safe(STATE/'owner-run.lock').open('a') as lock:
            try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:raise Hold('ANOTHER_OWNER_ACTION_RUNNING') from None
            receipt=read(STATE/'current/receipt.json')
            source=receipt.get('review_source','')
            if not re.fullmatch('[a-f0-9]{40}',source) or (expected and source!=expected):raise Hold('REVIEW_SOURCE_CHANGED')
            result['stage']='OPEN_EMULATOR'
            root=sdk();adb=root/'platform-tools/adb'
            adb,started=attach_or_start(root,run)
            result.update(started_existing_avd=started,apk_sha256=installed_identity(adb,receipt))
            result['stage']='OPEN_APP';open_activity(adb)
            result.update(ok=True,foreground=True,window=request_window(),stage='APP_OPEN')
            result.update(version=receipt.get('version'),version_code=receipt.get('version_code'),source=source)
            # Opening doesn't rewrite the installation receipt or remove a rejection.
            result['owner_decision']=receipt.get('owner_decision','REVIEW_PENDING')
    except Exception as e:
        result['error']=str(e) if isinstance(e,Hold) else 'LOCAL_OPEN_'+type(e).__name__.upper()
    # Capture is still attempted after a failed launch; never call a failed open a successful review.
    if action=='capture':
        try:
            result['private_report']=str(capture(adb,run));result['capture']='PRIVATE_ARCHIVE_VERIFIED'
            observed=read(run/'LOCAL_DIAGNOSTICS.json')
            result['network_summary']={'mac_resolved':sum(v=='RESOLVED' for v in observed.get('host_dns',{}).values()),
                'emulator_resolved':sum(v=='RESOLVED' for v in observed.get('emulator_dns',{}).values()),
                'emulator_timed_out':sum(v=='TIMED_OUT' for v in observed.get('emulator_dns',{}).values()),
                'services_checked':len(SERVICES)}
        except Exception as e:result['capture']='INCOMPLETE_'+type(e).__name__
    write(run/'OPEN_RESULT.json',result);write(STATE/'current/tv-open.json',result)
    # Note stores local evidence only; no raw logs are automatically uploaded.
    note=HOME/'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
    if note.is_dir():
        write(note/(run_id+'.md'),'# GharTV local open/log capture\n\n```json\n'+json.dumps(result,indent=2)+'\n```\nNo production approval. Private logs remain on this Mac.\n')
    return result

if __name__=='__main__':
    action='capture' if 'capture' in sys.argv[1:] else 'open'
    result=main_action(action)
    print(json.dumps(result,indent=None if '--json' in sys.argv else 2))
    if '--json' not in sys.argv and sys.stdin.isatty():
        if result.get('private_report'):
            subprocess.run(['/usr/bin/open','-R',result['private_report']],check=False)
        try:input('\nPress Enter to finish (the TV stays open): ')
        except EOFError:pass
    sys.exit(0 if result['ok'] else 1)
