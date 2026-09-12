#!/bin/bash
# Same RC2. Obsidian first; optional native, owner-interactive signing and emulator review.
set -u
umask 077
printf '\033[38;5;51m\nGharTV · CYAN REVIEW 4 · Obsidian + runnable RC2\033[0m\n'
if ! command -v python3 >/dev/null 2>&1; then echo 'Python 3 is required; no TV or source changed.'; exit 1; fi
CLOSE_MARKER="${TMPDIR:-/tmp}/ghartv-review-close-$$"
export GHARTV_CLOSE_MARKER="$CLOSE_MARKER"
python3 - "$0" "$@" <<'PY'
from __future__ import annotations
import argparse, datetime as dt, fcntl, hashlib, io, json, os, re, shutil, socket, subprocess, sys, tempfile, time, zipfile
from pathlib import Path
REPO='AmritSinghGit/ghartv'; SOURCE='b4d0304441b7d00833e4d475c16e43e1ef92b3f3'
PROD='b46b2cd607c309d364d531b5fd9da618cd007f6c'; TAG='v0.5.5-rc2'; PACKAGE='in.ghartv.nova'
VERSION='0.5.5-rc2-movies-picture'; UNSIGNED='32efc94eaa635b3da1d1895570b857e9f5d2dea5b5bed1151818c48d03a7cd21'
PROD_HASH='6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199'
NOTE_HASH='b6381bbb631540bca756b16f7ed6185cb5c992c34a2826037008f6db67ef5158'; AVD='GharTV_Nova_Manual_google_tv_API36'; ASSET='GharTV-review-current.apk'
HOME=Path.home(); SELF=Path(sys.argv[1]).resolve(); STATE=HOME/'Library/Application Support/GharTV/owner-review'; CURRENT=STATE/'current'
PROJECT=Path(os.environ.get('GHARTV_PROJECT',str(HOME/'Downloads/GharTV_Nova_v0.4.2'))).expanduser()
RUN_ID='GHARTV-CYAN-4-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid()); RUN=STATE/'runs'/RUN_ID
parser=argparse.ArgumentParser();parser.add_argument('--memory-only',action='store_true');parser.add_argument('--signed-apk',type=Path);parser.add_argument('--noninteractive',action='store_true');args=parser.parse_args(sys.argv[2:])
r=dict(run_id=RUN_ID,lane_id='ghartv',repository=REPO,operon_session=os.environ.get('OPERON_SESSION_ID','UNBOUND'),
 production_source=PROD,review_source=SOURCE,version=VERSION,version_code=16,unsigned_apk_sha256=UNSIGNED,signed_apk_sha256='NOT_VERIFIED',
 delivery_sha='NOT_READ',local_sha='NOT_READ',production_feed='NOT_CHECKED',obsidian='NOT_WRITTEN',memory_bridge='NOT_RUN',
 review_release='NOT_CHECKED',emulator='UNCHANGED',physical_tv='NOT_VERIFIED',owner_decision='REVIEW_PENDING',
 dashboard='NOT_OPENED',cleanup_count=0,cleanup_bytes=0,status='STARTING')
class Stop(RuntimeError):pass
def safe(path):
 if any(p.is_symlink() for p in (path,*path.parents)):raise Stop('Symlinked path preserved; use the canonical directory')
def mkdir(path):safe(path);path.mkdir(parents=True,exist_ok=True);path.chmod(0o700)
def digest(path):
 h=hashlib.sha256()
 with open(path,'rb') as f:
  for b in iter(lambda:f.read(131072),b''):h.update(b)
 return h.hexdigest()
def write(path,text):
 safe(path);temp=path.with_name(path.name+'.new-'+str(os.getpid()))
 with open(temp,'x',encoding='utf-8') as f:f.write(text)
 temp.chmod(0o600);os.replace(temp,path)
def call(argv,timeout=45,check=True,env=None):
 p=subprocess.run(list(map(str,argv)),capture_output=True,text=True,timeout=timeout,env=env)
 if check and p.returncode:raise Stop(Path(str(argv[0])).name+' failed (exit '+str(p.returncode)+'); no force/recovery action taken')
 return p

def download(url,path):
 safe(path)
 call(['curl','--proto','=https','--proto-redir','=https','-fLsS','--connect-timeout','15','--max-time','120','--max-filesize','52428800',url,'-o',path],130)
def get(url):
 with tempfile.TemporaryDirectory(prefix='.public-read-',dir=CURRENT) as t:
  p=Path(t)/'response';download(url,p)
  if p.stat().st_size>2*1024*1024:raise Stop('Public metadata exceeds bound')
  return json.loads(p.read_text())
def git(*a):return call(['git','-C',PROJECT,*a]).stdout.strip()
def receipt():return 'GHARTV_CYAN_REVIEW_4_HANDOFF\n'+'\n'.join(k.upper()+'='+str(v) for k,v in r.items())+'\n'
def persist():
 write(RUN/'handoff.txt',receipt());write(RUN/'receipt.json',json.dumps(r,indent=2)+'\n');write(CURRENT/'handoff.txt',receipt())
def note_sync(note_text,transport=True):
 vault=HOME/'Documents/Amrit Executive Memory'
 if not vault.is_dir():r['obsidian']='EXISTING_VAULT_NOT_FOUND';return
 dest=vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv';mkdir(dest)
 note=dest/'GharTV - Current Progress.md';safe(note)
 marker='<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->'
 if note.exists() and marker not in note.read_text():note=dest/(RUN_ID+'-progress.md')
 body=note_text+'\n\n## Local run evidence\n\n```text\n'+receipt()+'```\n'
 write(note,body)
 if note.read_bytes()!=body.encode():raise Stop('Obsidian readback differs')
 r['obsidian']='WRITTEN_AND_READBACK_VERIFIED';r['obsidian_note']=str(note)
 bridge=HOME/'bin/amrit-context';bridge_path=str(bridge) if bridge.is_file() and os.access(bridge,os.X_OK) else shutil.which('amrit-context')
 if transport and bridge_path:
  try:
   a=call([bridge_path,'handoff','--file',note],60,False);b=call([bridge_path,'sync-once'],60,False)
   r['memory_bridge']='HANDOFF_EXIT_'+str(a.returncode)+'_SYNC_EXIT_'+str(b.returncode)+'_REPLICA_READBACK_UNVERIFIED'
  except Exception as e:r['memory_bridge']='FAILED_'+type(e).__name__
 elif not bridge_path:r['memory_bridge']='LOCAL_COMMAND_NOT_FOUND'
 write(dest/(RUN_ID+'-receipt.txt'),receipt())
def reconcile():
 meta=get(f'https://api.github.com/repos/{REPO}/branches/main');head=meta['commit']['sha']
 if not re.fullmatch('[a-f0-9]{40}',head):raise Stop('Invalid remote head')
 with tempfile.TemporaryDirectory(prefix='.control-',dir=CURRENT) as t:
  p=Path(t)/'runner';download(f'https://raw.githubusercontent.com/{REPO}/{head}/GHARTV_SYNC_CURRENT_AND_REPORT.command',p)
  if digest(p)!=digest(SELF):raise Stop('This runner has been superseded; no checkout changed')
  n=Path(t)/'note';download(f'https://raw.githubusercontent.com/{REPO}/{head}/GHARTV_LANE_PROGRESS.md',n)
  if digest(n)!=NOTE_HASH:raise Stop('Lane note digest mismatch')
  text=n.read_text()
 r['delivery_sha']=head
 return text

def sync_checkout():
 if not (PROJECT/'.git').exists():raise Stop('Canonical checkout missing; no duplicate clone created')
 if git('remote','get-url','origin').removesuffix('.git') not in ('https://github.com/'+REPO,'git@github.com:'+REPO,'ssh://git@github.com/'+REPO):raise Stop('Unexpected Git origin')
 r['local_sha']=git('rev-parse','HEAD')
 if git('symbolic-ref','--short','HEAD')!='main' or git('status','--porcelain','--untracked-files=normal'):raise Stop('Non-main or dirty checkout preserved; no reset/stash/blind push')
 git('fetch','--no-tags','origin','main');head=git('rev-parse','origin/main')
 if head!=r['delivery_sha']:raise Stop('Remote changed during sync; rerun after reconciliation')
 git('merge-base','--is-ancestor',r['local_sha'],head);git('merge-base','--is-ancestor',SOURCE,head)
 if git('diff','--name-only',SOURCE,head,'--','android-tv'):raise Stop('A different Android candidate is present')
 git('merge','--ff-only',head);r['local_sha']=git('rev-parse','HEAD')

def payload(path):
 out={};seen=set()
 with zipfile.ZipFile(path) as z:
  items=z.infolist()
  if len(items)>5000 or sum(x.file_size for x in items)>100*1024*1024:raise Stop('APK payload exceeds bound')
  for x in items:
   if x.filename in seen:raise Stop('Duplicate APK member')
   seen.add(x.filename)
   if x.is_dir() or re.fullmatch(r'META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))',x.filename,re.I):continue
   out[x.filename]=hashlib.sha256(z.read(x)).hexdigest()
 return out

def review():
 if args.noninteractive:raise Stop('Review requires an owner at the terminal; use --memory-only for unattended note sync')
 if not shutil.which('gh'):raise Stop('GitHub CLI unavailable; Obsidian progress was already written')
 call(['gh','auth','status','-h','github.com'])
 sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(HOME/'Library/Android/sdk')));tools=sorted(sdk.glob('build-tools/*/apksigner'),key=lambda p:tuple(map(int,re.findall(r'\d+',p.parent.name))))
 if not tools:raise Stop('Android apksigner not found in the existing SDK')
 signer=tools[-1];align=signer.parent/'zipalign';aapt=signer.parent/'aapt';adb=sdk/'platform-tools/adb'
 env=dict(os.environ);java=call(['/usr/libexec/java_home','-v','17'],check=False).stdout.strip()
 if java:env['JAVA_HOME']=java;env['PATH']=java+'/bin:'+env.get('PATH','')
 def cert(path):
  out=call([signer,'verify','--print-certs',path],env=env).stdout
  values=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})',out)
  if not values:raise Stop('APK certificate unavailable')
  return set(v.lower() for v in values)
 def release():
  x=json.loads(call(['gh','api',f'repos/{REPO}/releases/tags/{TAG}']).stdout)
  if x.get('target_commitish')!=SOURCE or not x.get('prerelease') or x.get('draft'):raise Stop('Review release identity changed')
  assets=x.get('assets',[]);u=[v for v in assets if v['name']=='GharTV-review-unsigned.apk'];s=[v for v in assets if v['name']==ASSET]
  if len(u)!=1 or u[0].get('digest')!='sha256:'+UNSIGNED or len(s)>1:raise Stop('Review assets changed')
  return s
 with io.TextIOWrapper(io.FileIO('/dev/tty','r+'), encoding='utf-8', write_through=True) as tty:
  tty.write('\nThis reviews RC2 in the named emulator only. It may sign with your chosen EXISTING key and upload only the signed REVIEW APK. Production and physical TVs stay unchanged.\nType REVIEW RC2 to proceed, or press Enter to finish after Obsidian sync: ');tty.flush()
  if tty.readline().strip()!='REVIEW RC2':r['status']='MEMORY_UPDATED_REVIEW_DEFERRED' if r['obsidian']=='WRITTEN_AND_READBACK_VERIFIED' else 'REVIEW_DEFERRED_CONTINUITY_REQUIRES_ATTENTION';return
  with tempfile.TemporaryDirectory(prefix='.rc2-review-',dir=CURRENT) as t:
   t=Path(t);u=t/'unsigned.apk';c=t/'candidate.apk';p=t/'production.apk'
   download(f'https://github.com/{REPO}/releases/download/{TAG}/GharTV-review-unsigned.apk',u)
   download(f'https://github.com/{REPO}/releases/download/v0.5.4-rc5/GharTV-Jio-Live-v0.5.4-rc5.apk',p)
   if digest(u)!=UNSIGNED or digest(p)!=PROD_HASH:raise Stop('Release byte digest mismatch')
   published=release()
   if published:
    expected=published[0].get('digest','')
    if not re.fullmatch('sha256:[a-f0-9]{64}',expected):raise Stop('Signed asset digest unavailable')
    download(f'https://github.com/{REPO}/releases/download/{TAG}/{ASSET}',c)
    if digest(c)!=expected[7:]:raise Stop('Signed download digest mismatch')
   elif args.signed_apk:
    safe(args.signed_apk.expanduser());shutil.copyfile(args.signed_apk.expanduser(),c)
   else:
    keydir=HOME/'Library/Application Support/GharTV/signing';safe(keydir)
    keys=[k for k in keydir.iterdir() if k.is_file() and not k.is_symlink() and k.suffix.lower() in ('.jks','.keystore','.p12')] if keydir.is_dir() else []
    default=str(keys[0]) if len(keys)==1 else ''
    tty.write('\nChoose the existing GharTV release keystore. Do NOT create another key.\nKeystore path'+(' ['+default+']' if default else '')+': ');tty.flush()
    value=tty.readline().strip() or default
    if not value:raise Stop('No owner-selected keystore; signing not attempted')
    key=Path(value).expanduser();safe(key)
    if not key.is_file():raise Stop('Selected keystore does not exist')
    tty.write('Key alias (Enter lets apksigner use the only key): ');tty.flush();alias=tty.readline().strip()
    aligned=t/'aligned.apk';call([align,'-f','4',u,aligned],60,env=env)
    tty.write('Android apksigner will now ask locally for your keystore password. The helper does not read signing.env or capture/store your password.\n');tty.flush()
    argv=[str(signer),'sign','--ks',str(key),'--v4-signing-enabled','false','--out',str(c)]
    if alias:argv+=['--ks-key-alias',alias]
    argv.append(str(aligned))
    native=subprocess.run(argv,stdin=tty,stdout=tty,stderr=tty,env=env,timeout=600)
    if native.returncode:raise Stop('Native signing failed; previous emulator remains unchanged')
   if payload(u)!=payload(c) or cert(c)!=cert(p):raise Stop('Payload/signature continuity failed; no upload or installation')
   badging=call([aapt,'dump','badging',c],env=env).stdout
   if not all(v in badging for v in ["name='in.ghartv.nova'","versionCode='16'","versionName='0.5.5-rc2-movies-picture'"]):raise Stop('Wrong package or Android version')
   if git('rev-parse','HEAD')!=r['local_sha'] or git('status','--porcelain','--untracked-files=normal'):raise Stop('Checkout changed during review preparation')
   h=digest(c);r['signed_apk_sha256']=h;race=release()
   if race and race[0].get('digest')!='sha256:'+h:raise Stop('Another signed artifact exists; no overwrite')
   if not race:
    public=t/ASSET;shutil.copyfile(c,public);call(['gh','release','upload',TAG,public,'--repo',REPO],120)
   verified=release()
   if len(verified)!=1 or verified[0].get('digest')!='sha256:'+h:raise Stop('Signed review upload not verified')
   r['review_release']='SIGNED_REVIEW_ASSET_VERIFIED';target=CURRENT/ASSET;safe(target)
   devices=call([adb,'devices']).stdout.splitlines();matches=[]
   for line in devices:
    parts=line.split()
    if len(parts)==2 and parts[1]=='device' and parts[0].startswith('emulator-'):
     name=call([adb,'-s',parts[0],'emu','avd','name'],check=False).stdout.splitlines()
     if name and name[0].strip()==AVD:matches.append(parts[0])
   if len(matches)>1:raise Stop('Multiple matching AVDs; none selected automatically')
   if not matches:
    if not (HOME/'.android/avd'/f'{AVD}.ini').is_file():raise Stop('Existing AVD not found; no new AVD created')
    if any(v.startswith('emulator-5580') for v in devices):raise Stop('Existing port 5580 transport unavailable; no duplicate start')
    for port in (5580,5581):
     with socket.socket() as sock:
      try:sock.bind(('127.0.0.1',port))
      except OSError:raise Stop('AVD port occupied; no duplicate start')
    with open(RUN/'emulator.log','w') as log:subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',AVD,'-port','5580','-no-snapshot-load'],stdout=log,stderr=log,start_new_session=True)
    matches=['emulator-5580'];call([adb,'-s',matches[0],'wait-for-device'],180)
   serial=matches[0];r['emulator_serial']=serial
   for _ in range(90):
    if call([adb,'-s',serial,'shell','getprop','sys.boot_completed'],check=False).stdout.strip()=='1':break
    time.sleep(2)
   else:raise Stop('AVD boot incomplete; no installation')
   codes=re.findall(r'versionCode=(\d+)',call([adb,'-s',serial,'shell','dumpsys','package',PACKAGE]).stdout)
   if codes and int(codes[0])>16:raise Stop('Newer version installed; no downgrade')
   def pull_installed(destination):
    paths=call([adb,'-s',serial,'shell','pm','path',PACKAGE]).stdout.splitlines()
    if len(paths)!=1 or not paths[0].startswith('package:'):raise Stop('Unexpected installed package layout')
    call([adb,'-s',serial,'pull',paths[0][8:].strip(),destination],60)
   if codes:
    old=t/'installed.apk';pull_installed(old)
    if cert(old)!=cert(c):raise Stop('Installed signer differs; no uninstall or storage clearing')
    if int(codes[0])==16 and digest(old)!=h:raise Stop('Different code-16 APK installed; reconcile first')
   if not codes or int(codes[0])<16:
    result=call([adb,'-s',serial,'install','-r',c],120).stdout
    if 'Success' not in result:raise Stop('Android did not confirm install')
   installed=t/'installed-final.apk';pull_installed(installed)
   if digest(installed)!=h:raise Stop('Installed byte verification failed')
   call([adb,'-s',serial,'shell','am','force-stop',PACKAGE]);out=call([adb,'-s',serial,'shell','am','start','-W','-n',PACKAGE+'/.SplashActivity']).stdout
   if 'Status: ok' not in out:raise Stop('App launch not confirmed')
   c.chmod(0o600);os.replace(c,target)
   write(CURRENT/'review-artifact.json',json.dumps({'source':SOURCE,'sha256':h,'unsigned_sha256':UNSIGNED,'version':VERSION},indent=2)+'\n')
   r['emulator']='RC2_INSTALLED_BYTES_VERIFIED_AND_LAUNCHED';r['status']='REVIEW_READY'
   obsolete=CURRENT/'GharTV-review-unsigned.apk'
   if obsolete.is_file() and not obsolete.is_symlink() and digest(obsolete)==UNSIGNED:
    r['cleanup_bytes']+=obsolete.stat().st_size;obsolete.unlink();r['cleanup_count']+=1

def cleanup():
 allowed={'GHARTV_RC2_SOURCE_HANDOFF.zip':'3968480bf420c9e9b4c16eee83a89e171dddf78c7d16eb4bd77031f0b789547d','GHARTV_CYAN_REVIEW_2.zip':'7d8906d5ba84be44bb1546f4f6887e5259f734b61eedb0201c99b263901443b8','GHARTV_RC5_EXACT_STABLE_R1.command':'3d3a6e0a69b0ba4ce105913d92801af4bec6cb895da37f91a5d9383ef5149901'}
 d=HOME/'Downloads'
 if not d.is_dir() or d.is_symlink():return
 for p in d.iterdir():
  for name,h in allowed.items():
   stem,ext=name.rsplit('.',1)
   if re.fullmatch(re.escape(stem)+r'(?: \(\d+\))?\.'+re.escape(ext),p.name) and p.is_file() and not p.is_symlink() and p!=SELF and digest(p)==h:
    r['cleanup_bytes']+=p.stat().st_size;p.unlink();r['cleanup_count']+=1
note_text='';lock_acquired=False
try:
 for p in (STATE,CURRENT,RUN):mkdir(p)
 lockpath=STATE/'owner-run.lock';safe(lockpath);lock=open(lockpath,'w');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);lock_acquired=True
 note_text=reconcile()
 try:note_sync(note_text)
 except Exception as e:r['obsidian']='FAILED_'+type(e).__name__
 persist()
 sync_checkout()
 m=get(f'https://raw.githubusercontent.com/{REPO}/main/update/latest.json')
 if m.get('versionCode')!=14 or m.get('sourceCommit')!=PROD or m.get('sha256')!=PROD_HASH:raise Stop('Production feed changed; not overwritten')
 r['production_feed']='RC5_CODE14_ADVERTISED';r['status']='MEMORY_UPDATED' if r['obsidian']=='WRITTEN_AND_READBACK_VERIFIED' else 'CONTINUITY_REQUIRES_ATTENTION';cleanup()
 if not args.memory_only:review()
 local=CURRENT/'owner.html'
 destination=str(local) if local.is_file() and not local.is_symlink() else 'https://amritsinghgit.github.io/ghartv/owner.html'
 opened=call(['open',destination],check=False);r['dashboard']='OPEN_REQUESTED_AUTH_NOT_VERIFIED' if opened.returncode==0 else 'OPEN_FAILED'
except Exception as e:
 r['status']='ACTION_REQUIRED';r['blocker']=str(e) if isinstance(e,Stop) else type(e).__name__
finally:
 try:
  if lock_acquired:
   if note_text:
    try:note_sync(note_text)
    except Exception as e:r['obsidian']='FAILED_'+type(e).__name__
   persist()
 except Exception as e:print('Continuity receipt issue: '+type(e).__name__)
 print('\n'+receipt())
 if not args.noninteractive:
  try:
   with io.TextIOWrapper(io.FileIO('/dev/tty','r+'), encoding='utf-8', write_through=True) as tty:
    tty.write('\nPress Enter to copy this handoff: ');tty.flush();tty.readline()
    copied=subprocess.run(['pbcopy'],input=receipt(),text=True,check=False)
    tty.write(('Copied.' if copied.returncode==0 else 'Copy failed; handoff saved in current/handoff.txt.')+' Press Enter to finish this terminal: ');tty.flush();tty.readline()
    mark=Path(os.environ['GHARTV_CLOSE_MARKER'])
    with open(mark,'x') as f:f.write('owner-confirmed\n')
  except OSError:pass
sys.exit(1 if r['status']=='ACTION_REQUIRED' else 0)
PY
RESULT=$?
if [ -f "$CLOSE_MARKER" ] && [ ! -L "$CLOSE_MARKER" ] && [ "${TERM_PROGRAM:-}" = Apple_Terminal ] && [ "$0" = "$HOME/.local/share/ghartv-launcher/current/GHARTV_SYNC_CURRENT_AND_REPORT.command" ]; then
 TARGET_TTY="$(tty 2>/dev/null || true)"
 if [[ "$TARGET_TTY" == /dev/ttys* ]]; then
  (sleep 1; osascript - "$TARGET_TTY" <<'APPLESCRIPT'
on run argv
 tell application "Terminal"
  repeat with w in windows
   if (count tabs of w) is 1 then
    try
     if tty of selected tab of w is item 1 of argv then
      close w
      return
     end if
    end try
   end if
  end repeat
 end tell
end run
APPLESCRIPT
  ) </dev/null >/dev/null 2>&1 &
 fi
fi
if [ -f "$CLOSE_MARKER" ] && [ ! -L "$CLOSE_MARKER" ]; then rm -f "$CLOSE_MARKER"; fi
exit "$RESULT"
