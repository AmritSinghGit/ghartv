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
REVISION = 'tv-open-1.1.0-port-reopen'
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

def _listen_owners(port):
    """Inspect listeners, not outbound sockets or TCP TIME_WAIT records."""
    import shutil
    executable = '/usr/sbin/lsof' if Path('/usr/sbin/lsof').is_file() else shutil.which('lsof')
    if not executable: raise Hold('LSOF_NOT_AVAILABLE_PORT_OWNER_UNCHECKED')
    p = call([executable, '-nP', '-a', '-iTCP:'+str(port), '-sTCP:LISTEN', '-Fpcu'], 4)
    if p.returncode not in (0, 1) or (p.returncode == 1 and p.stderr.strip()):
        raise Hold('PORT_OWNER_CHECK_FAILED_NO_PROCESS_STOPPED')
    rows=[]; row=None
    for line in p.stdout.splitlines():
        if line.startswith('p') and line[1:].isdigit():
            if row: rows.append(row)
            row={'pid':int(line[1:]), 'port':port}
        elif row is not None and line.startswith('u') and line[1:].isdigit():row['uid']=int(line[1:])
        elif row is not None and line.startswith('c'):
            row['executable']=re.sub(r'[^A-Za-z0-9_.-]', '_', line[1:])[:64]
    if row: rows.append(row)
    if p.returncode == 0 and not rows:raise Hold('PORT_OWNER_RESPONSE_UNRECOGNIZED')
    return rows

def _reusable_pair():
    """SO_REUSEADDR tolerates retired TCP sockets. Never enable SO_REUSEPORT."""
    import errno
    checks=[]
    for port in (5580,5581):
        for family,address in ((socket.AF_INET,'127.0.0.1'),(socket.AF_INET6,'::1')):
            if family==socket.AF_INET6 and not socket.has_ipv6:continue
            try:
                with socket.socket(family,socket.SOCK_STREAM) as probe:
                    probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                    if family==socket.AF_INET6:probe.setsockopt(socket.IPPROTO_IPV6,socket.IPV6_V6ONLY,1)
                    probe.bind((address,port))
                checks.append({'port':port,'family':4 if family==socket.AF_INET else 6,'state':'REUSABLE'})
            except OSError as e:
                if family==socket.AF_INET6 and e.errno in (errno.EAFNOSUPPORT,errno.EADDRNOTAVAIL,errno.ENOPROTOOPT):continue
                checks.append({'port':port,'family':4 if family==socket.AF_INET else 6,
                               'state':'IN_USE' if e.errno==errno.EADDRINUSE else 'BIND_FAILED','errno':e.errno})
    return checks

def _port_conflict(owners,procs):
    own={pid for pid,command in procs}
    for row in owners:
        if row['pid'] not in own or row.get('uid') != os.getuid():
            return ('PORT_'+str(row['port'])+'_LISTENER_'+row.get('executable','UNKNOWN')+
                    '_PID_'+str(row['pid'])+'_PRESERVED')
    return None

def attach_or_start(root,run):
    """Reconcile close/reopen races before starting this one configured AVD."""
    adb=root/'platform-tools/adb'; child=None; started=False
    observation={'revision':REVISION,'ports':[5580,5581], 'processes_killed':0,
                 'adb_server_restarted':False,'alternate_port_used':False,
                 'reusable_address_probe':True,'observations':[]}
    def record(stage,**values):
        observation['stage']=stage
        observation['observations']=(observation['observations']+[{'stage':stage,**values}])[-8:]
        write(run/'PORT_CHECK.json',observation)
    # Do not immediately mistake a closing/booting transport for a second device.
    settle_until=time.monotonic()+30
    while True:
        if time.monotonic()>=settle_until:
            record('WAIT_EXPIRED_NO_DUPLICATE')
            raise Hold('EMULATOR_STILL_CLOSING_OR_UNRESPONSIVE_NO_DUPLICATE_STARTED')
        p=call([adb,'devices'],5)
        if p.returncode:raise Hold('ADB_UNAVAILABLE')
        connected=devices(p.stdout)
        for serial,status in connected.items():
            if status=='device' and serial!=SERIAL:
                names=call([adb,'-s',serial,'emu','avd','name'],4).stdout.splitlines()
                if names and names[0].strip()==AVD:
                    record('SAME_AVD_ON_OTHER_PORT_PRESERVED')
                    raise Hold('NAMED_AVD_RUNNING_ON_OTHER_PORT_PRESERVED')
        if connected.get(SERIAL)=='device':
            try:
                verify_target(adb)
                record('REUSE_EXISTING_TRANSPORT')
                break
            except (Hold,subprocess.TimeoutExpired):
                # During shutdown a formerly online transport can disappear.
                # A different, responsive AVD is never reused or stopped.
                name=call([adb,'-s',SERIAL,'emu','avd','name'],4)
                first=name.stdout.splitlines()
                if name.returncode==0 and first and first[0].strip() not in ('',AVD):
                    record('DIFFERENT_AVD_PRESERVED');raise Hold('CANONICAL_PORT_HAS_DIFFERENT_AVD_PRESERVED')
        procs=avd_processes()
        owners=_listen_owners(5580)+_listen_owners(5581)
        conflict=_port_conflict(owners,procs)
        if conflict:
            record('OTHER_LISTENER_PRESERVED',listeners=owners)
            raise Hold(conflict)
        checks=_reusable_pair() if not owners and not procs and SERIAL not in connected else []
        clear=bool(checks) and all(x['state']=='REUSABLE' for x in checks)
        if clear:
            record('PORTS_REUSABLE_NO_EXISTING_INSTANCE',bindings=checks)
            listed=call([root/'emulator/emulator','-list-avds'],10)
            if listed.returncode or AVD not in listed.stdout.splitlines():
                raise Hold('EXISTING_NAMED_AVD_NOT_FOUND')
            # Final read immediately before spawn. The emulator's own bind/AVD
            # locks remain authoritative if another launcher races us.
            if avd_processes() or _listen_owners(5580) or _listen_owners(5581):
                record('STATE_CHANGED_RECHECK');continue
            if SERIAL in devices(call([adb,'devices'],5).stdout):
                record('TRANSPORT_APPEARED_RECHECK');continue
            log=safe(run/'emulator-start.private.log')
            with log.open('xb') as out:
                os.chmod(log,0o600)
                child=subprocess.Popen([str(root/'emulator/emulator'),'-avd',AVD,'-port','5580',
                    '-no-snapshot-load','-no-snapshot-save'],stdin=subprocess.DEVNULL,
                    stdout=out,stderr=out,cwd=root/'emulator',start_new_session=True)
            started=True;record('STARTED_EXISTING_AVD');break
        record('WAITING_FOR_EXISTING_TRANSPORT_OR_SHUTDOWN',transport=connected.get(SERIAL,'ABSENT'),
               matching_processes=len(procs),listeners=owners,bindings=checks)
        if time.monotonic()>=settle_until:
            raise Hold('EMULATOR_STILL_CLOSING_OR_UNRESPONSIVE_NO_DUPLICATE_STARTED')
        time.sleep(1)
    deadline=time.monotonic()+150
    while time.monotonic()<deadline:
        if child and child.poll() is not None:
            record('EMULATOR_PROCESS_EXITED');raise Hold('EMULATOR_PROCESS_EXITED_SEE_PRIVATE_LOG')
        try:
            if call([adb,'-s',SERIAL,'shell','getprop','sys.boot_completed'],4).stdout.strip()=='1':
                verify_target(adb);record('EXISTING_AVD_BOOT_VERIFIED');return adb,started
        except subprocess.TimeoutExpired:pass
        time.sleep(2)
    record('BOOT_TIMEOUT_PROCESS_PRESERVED')
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

def request_window_details():
    import importlib.util
    path=Path(__file__).with_name('tv_window.py')
    spec=importlib.util.spec_from_file_location('ghartv_tv_window',path)
    helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    return helper.request_window_details(avd_processes())

def request_window():
    return request_window_details()['status']

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
    run_id='GHARTV-TV-OPEN-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
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
            pressure=call(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level']).stdout.strip()
            if pressure!='1':
                boot=call([adb,'-s',SERIAL,'shell','getprop','sys.boot_completed'],4).stdout.strip()
                if boot!='1':raise Hold('HOST_PRESSURE_NO_NEW_EMULATOR')
                verify_target(adb)
            adb,started=attach_or_start(root,run)
            result.update(started_existing_avd=started,apk_sha256=installed_identity(adb,receipt))
            result['stage']='OPEN_APP';open_activity(adb)
            window=request_window_details()
            result.update(ok=window.get('window_observed') is True,foreground=True,window=window['status'],window_observed=window.get('window_observed') is True,stage='APP_OPEN')
            if not result['ok']:result['error']='TV_WINDOW_'+window['status']
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