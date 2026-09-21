"""Window-only recovery for the existing GharTV code30. No APK install or rebuild.
Reuses the verified existing transport; if the emulator GUI cannot be shown, opens
one scrcpy window for that same Android display. No recording or clipboard sync.
"""
from __future__ import annotations
import datetime as dt
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path,PurePosixPath
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

SOURCE='ae4c84578e26e707d1a11c5ff078f6395cf36dde'
SIGNED='723d84553477fd5dc390c1001bfcd213919ffd434a4152905c6a243634e164ad'
TRANSPORT_SHA='ebb2f9f4edfc5d63f343ff3caadd2bc7259506fa098aed26c046f2849e599427'
SCRCPY_SHA='20fd47c9014dd5e0fa77091f3cb7adbda8445a360c4584aeaa0150b5b3988ff3'
SCRCPY_URL='https://github.com/Genymobile/scrcpy/releases/download/v4.1/scrcpy-macos-aarch64-v4.1.tar.gz'
SERIAL='emulator-5580';AVD='GharTV_Nova_Manual_google_tv_API36'
HOME=Path.home();STATE=HOME/'Library/Application Support/GharTV/owner-review'
HERE=Path(__file__).resolve().parent
class Hold(RuntimeError):pass

def digest(p):
 h=hashlib.sha256()
 with Path(p).open('rb') as stream:
  for part in iter(lambda:stream.read(1024*1024),b''):h.update(part)
 return h.hexdigest()

def safe(p):
 p=Path(p).absolute()
 for q in (p,*p.parents):
  if q.is_symlink():raise Hold('LINKED_PATH_PRESERVED')
 return p

def write(p,value):
 p=safe(p);p.parent.mkdir(parents=True,mode=0o700,exist_ok=True)
 raw=value.encode() if isinstance(value,str) else (json.dumps(value,indent=2)+'\n').encode()
 temp=p.with_name(p.name+'.tmp-'+str(os.getpid()))
 with temp.open('xb') as stream:os.chmod(temp,0o600);stream.write(raw);stream.flush();os.fsync(stream.fileno())
 os.replace(temp,p)

def read(p):
 p=safe(p);info=p.stat()
 if not p.is_file() or info.st_uid!=os.getuid() or info.st_size>256000:raise Hold('UNTRUSTED_LOCAL_RECEIPT')
 return json.loads(p.read_text())

def env():
 return {'HOME':str(HOME),'PATH':'/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin','LANG':'en_US.UTF-8','GH_PROMPT_DISABLED':'1'}

def call(args,timeout=8):
 return subprocess.run([str(x) for x in args],stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=timeout,env=env())

def validate_receipt(r):
 if r.get('review_source')!=SOURCE or r.get('version_code')!=30 or r.get('signed_apk_sha256')!=SIGNED:
  raise Hold('CURRENT_CANDIDATE_CHANGED_PRESERVED')

def checked_bundle():
 m=json.loads((HERE/'PACKAGE.json').read_text())
 for name,h in m['files'].items():
  if '/' in name or '..' in name or not re.fullmatch('[a-f0-9]{64}',h):raise Hold('RECOVERY_MANIFEST_INVALID')
  p=safe(HERE/name)
  if not p.is_file() or digest(p)!=h:raise Hold('RECOVERY_CHECKSUM_FAILED')
 (HERE/'DisplayProbe').chmod(0o700)
 return m

def probe(pid):
 result=call([HERE/'DisplayProbe',str(pid)],5)
 if result.returncode:raise Hold('DISPLAY_QUERY_FAILED')
 data=json.loads(result.stdout)
 if not isinstance(data.get('window_observed'),bool):raise Hold('DISPLAY_RESPONSE_INVALID')
 return data

def mirror_args(binary):
 return [str(binary),'--serial='+SERIAL,'--window-title=GharTV TV review - code30',
         '--window-width=1120','--window-height=630','--window-x=80','--window-y=80',
         '--video-codec=h264','--max-size=1280','--max-fps=30','--video-bit-rate=4M',
         '--keyboard=sdk','--no-clipboard-autosync','--no-power-on']

def archive_files(archive):
 files={};total=0
 for member in archive.getmembers():
  name=PurePosixPath(member.name)
  if name.is_absolute() or '..' in name.parts or '\\' in member.name:raise Hold('ARCHIVE_PATH_REJECTED')
  if member.isdir():continue
  if not member.isfile() or member.name in files:raise Hold('ARCHIVE_MEMBER_REJECTED')
  total+=member.size
  if total>200000000 or len(files)>1000:raise Hold('ARCHIVE_TOO_LARGE')
  files[member.name]=member
 return files

def prepare_scrcpy(root):
 root=safe(root);root.mkdir(parents=True,mode=0o700,exist_ok=True)
 archive=safe(root/'scrcpy-macos-aarch64-v4.1.tar.gz')
 if archive.exists() and digest(archive)!=SCRCPY_SHA:raise Hold('CACHED_DISPLAY_TOOL_CHANGED_PRESERVED')
 if not archive.exists():
  temp=root/('download-'+str(os.getpid())+'.tar.gz')
  result=call(['/usr/bin/curl','--proto','=https','--proto-redir','=https','-fL','--connect-timeout','15','--max-time','180','--retry','1',SCRCPY_URL,'-o',temp],205)
  if result.returncode:raise Hold('OFFICIAL_DISPLAY_DOWNLOAD_FAILED')
  if digest(temp)!=SCRCPY_SHA:raise Hold('OFFICIAL_DISPLAY_CHECKSUM_FAILED')
  os.replace(temp,archive)
 with tarfile.open(archive,'r:gz') as package:
  members=archive_files(package)
  for name,member in members.items():
   target=safe(root/name);data=package.extractfile(member).read();h=hashlib.sha256(data).hexdigest()
   if target.exists():
    if not target.is_file() or digest(target)!=h:raise Hold('DISPLAY_TOOL_FILE_CHANGED_PRESERVED')
   else:
    target.parent.mkdir(parents=True,mode=0o700,exist_ok=True)
    with target.open('xb') as stream:stream.write(data)
   target.chmod(0o700 if member.mode&0o111 else 0o600)
 binary=root/'scrcpy-macos-aarch64-v4.1/scrcpy'
 server=root/'scrcpy-macos-aarch64-v4.1/scrcpy-server'
 if not binary.is_file() or not server.is_file():raise Hold('OFFICIAL_DISPLAY_LAYOUT_UNEXPECTED')
 return binary,server

def process_matches(saved,binary):
 if not isinstance(saved.get('pid'),int) or saved['pid']<=0:return False
 result=call(['/bin/ps','-p',str(saved['pid']),'-o','uid=,command='])
 parts=result.stdout.strip().split(None,1)
 if not parts:return False
 if len(parts)!=2 or parts[0]!=str(os.getuid()) or not parts[1].startswith(str(binary)+' ') or '--serial='+SERIAL not in parts[1].split():
  raise Hold('DISPLAY_PID_CHANGED_PRESERVED')
 started=call(['/bin/ps','-p',str(saved['pid']),'-o','lstart=']).stdout.strip()
 if started!=saved.get('process_started'):raise Hold('DISPLAY_PID_REUSED_PRESERVED')
 return True

def start_mirror(binary,server,adb,run,r):
 current=STATE/'tv-window/current-display.json';saved={}
 if current.exists():saved=read(current)
 if saved and process_matches(saved,binary):
  pid=saved['pid'];log=safe(Path(saved['log']));child=None;r['display_reused']=True
 else:
  log=run/'display.private.log'
  child_env={**env(),'ADB':str(adb),'SCRCPY_SERVER_PATH':str(server)}
  with safe(log).open('xb') as stream:
   os.chmod(log,0o600)
   child=subprocess.Popen(mirror_args(binary),stdin=subprocess.DEVNULL,stdout=stream,stderr=stream,
                          env=child_env,cwd=binary.parent,start_new_session=True)
  pid=child.pid;time.sleep(.3)
  saved={'pid':pid,'log':str(log),'serial':SERIAL,'process_started':call(['/bin/ps','-p',str(pid),'-o','lstart=']).stdout.strip()}
  write(current,saved);r['display_reused']=False
 r['tv_window_transport']='SCRCPY_EXISTING_NOVA';deadline=time.monotonic()+25
 while time.monotonic()<deadline:
  if child and child.poll() is not None:raise Hold('DISPLAY_PROCESS_EXITED_SEE_PRIVATE_LOG')
  if not process_matches(saved,binary):raise Hold('DISPLAY_PROCESS_NOT_RUNNING')
  with log.open('rb') as stream:
   stream.seek(max(0,log.stat().st_size-64000));text=stream.read().decode('utf-8','replace')
  frame=re.search(r'Texture:\s*(\d+)x(\d+)',text)
  view=probe(pid);r['display_observation']=view
  if frame and view['window_observed']:
   r['display_frame_dimensions']=[int(frame[1]),int(frame[2])]
   return view
  time.sleep(.7)
 raise Hold('DISPLAY_FRAME_OR_WINDOW_NOT_CONFIRMED')

def save_continuity(r,run,old_bytes,publish):
 write(run/'WINDOW_RECOVERY.json',r)
 if not publish:return
 current=STATE/'current/receipt.json'
 if current.read_bytes()!=old_bytes:raise Hold('NEWER_LOCAL_RECEIPT_PRESERVED')
 write(run/'PREVIOUS_RECEIPT.json',old_bytes.decode())
 note='# GharTV TV window recovery\n\n'+json.dumps({k:r.get(k) for k in ('run_id','review_source','version_code','status','tv_window_transport','mac_window','blocker','apk_changed','emulator_restarted')},indent=2)+'\n\nNo movie playback or physical-TV verification. No production promotion.\n'
 folder=HOME/'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
 if folder.is_dir():
  target=folder/(r['run_id']+'.md');write(target,note)
  latest=folder/'GharTV - Current Progress.md'
  if latest.is_file() and latest.stat().st_size<240000:
   prior=safe(latest).read_text();write(latest,prior+'\n\n'+note)
  r['obsidian']='WRITTEN_AND_READBACK_VERIFIED' if target.read_text()==note else 'READBACK_FAILED'
 else:r['obsidian']='LOCAL_VAULT_NOT_FOUND'
 r['memory_bridge']='NOT_REQUESTED_WINDOW_RECOVERY_REPLICA_UNVERIFIED'
 write(current,r);write(STATE/'current/light-handoff.md',note);write(run/'WINDOW_RECOVERY.json',r)
 node=shutil.which('node',path=env()['PATH'])
 if node:
  try:
   result=call([node,HERE/'window-receipt-sync.mjs','--publish'],25);sync=json.loads(result.stdout)
   r['receipt_sync']=sync.get('status','PENDING');r['receipt_url']=sync.get('url')
  except (OSError,ValueError,subprocess.TimeoutExpired):r['receipt_sync']='PENDING_TRANSPORT'
 else:r['receipt_sync']='PENDING_NODE_UNAVAILABLE'
 write(current,r);write(run/'WINDOW_RECOVERY.json',r)

def main():
 if sys.platform!='darwin' or os.uname().machine!='arm64':print('APPLE_SILICON_MAC_REQUIRED');return 2
 os.umask(0o077);checked_bundle()
 run_id='GHARTV-CYAN-17-WINDOW-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
 run=safe(STATE/'runs'/run_id);run.mkdir(parents=True,mode=0o700)
 r={'run_id':run_id,'status':'ACTION_REQUIRED','apk_changed':False,'emulator_restarted':False,'production_changed':False,'tv_window_transport':'NOT_OPENED','owner_decision':'REVIEW_PENDING'}
 publish=False;old_bytes=b''
 with safe(STATE/'owner-run.lock').open('a') as lock:
  try:
   try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
   except BlockingIOError:raise Hold('ANOTHER_OWNER_ACTION_RUNNING') from None
   prior=read(STATE/'current/receipt.json');validate_receipt(prior);old_bytes=(STATE/'current/receipt.json').read_bytes()
   r={**prior,**r,'parent_run_id':prior['run_id'],'phase':'STARTING','mac_window':'NOT_CHECKED','mac_window_observed':False,'mac_window_frontmost':False,'emulator':'UNCHANGED','blocker':None,'receipt_sync':'NOT_SENT','recovery_source':json.loads((HERE/'PACKAGE.json').read_text())['source']}
   r['owner_decision']=prior.get('owner_decision','REVIEW_PENDING')
   publish=True
   path=safe(STATE/'runtime-current/tools/tv_local.py')
   if digest(path)!=TRANSPORT_SHA:raise Hold('EXISTING_TRANSPORT_CHANGED_PRESERVED')
   spec=importlib.util.spec_from_file_location('ghartv_existing_transport',path);transport=importlib.util.module_from_spec(spec);spec.loader.exec_module(transport)
   sdk=transport.sdk();adb=sdk/'platform-tools/adb'
   print('Checking the existing Nova and the exact installed code30. No APK installation.',flush=True)
   boot=call([adb,'-s',SERIAL,'shell','getprop','sys.boot_completed'],5)
   if boot.returncode or boot.stdout.strip()!='1':
    pressure=call(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level']).stdout.strip()
    if pressure!='1':raise Hold('HOST_PRESSURE_NO_NEW_EMULATOR')
    adb,started=transport.attach_or_start(sdk,run);r['started_existing_avd']=started
   transport.verify_target(adb);transport.installed_identity(adb,prior);transport.open_activity(adb)
   r['emulator']='RC10_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND';r['phase']='REVIEW_OPEN'
   procs=transport.avd_processes();qemu=[pid for pid,command in procs if 'qemu-system-' in command]
   if len(qemu)==1:
    view=probe(qemu[0]);r['original_window_observation']=view
    if view['window_observed'] and view['app_active']:
     r['tv_window_transport']='EMULATOR_UI'
    else:view=None
   else:view=None
   if view is None:
    print('Opening a local view of this SAME Nova display; no second emulator, no recording.',flush=True)
    binary,server=prepare_scrcpy(STATE/'tv-window/scrcpy-4.1')
    view=start_mirror(binary,server,adb,run,r)
   r['mac_window']=view['status'];r['mac_window_observed']=view['window_observed'];r['mac_window_frontmost']=view['app_active']
   r['status']='REVIEW_READY' if view['window_observed'] and view['app_active'] else 'ANDROID_READY_WINDOW_UNCONFIRMED'
   if r['status']!='REVIEW_READY':r['blocker']='TV_WINDOW_VISIBLE_CLICK_TO_FOCUS'
  except Exception as error:
   r['status']='ACTION_REQUIRED';r['blocker']=str(error) if isinstance(error,Hold) else 'WINDOW_RECOVERY_'+type(error).__name__.upper()
  finally:
   try:save_continuity(r,run,old_bytes,publish)
   except Exception:r['continuity']='PENDING_LOCAL_STATE_PRESERVED';write(run/'WINDOW_RECOVERY.json',r)
 print('\nGharTV TV review: '+r['status'])
 print('Window: '+r.get('mac_window','NOT_CHECKED')+' / '+r['tv_window_transport'])
 if r.get('blocker'):print('Note: '+r['blocker'])
 if r.get('mac_window_observed'):
  print('Use arrow keys and Enter. Right-click or Option+B goes Back. Closing this view leaves Nova running.')
 print('APK changed: no. Existing emulator restarted: no. Household release changed: no.')
 print('Continuity: '+r.get('receipt_sync','PENDING')+'; evidence: '+str(run))
 return 0 if r.get('mac_window_observed') else 1

if __name__=='__main__':sys.exit(main())
