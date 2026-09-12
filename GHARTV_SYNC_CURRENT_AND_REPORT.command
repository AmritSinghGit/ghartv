#!/bin/bash
# Canonical GharTV Cyan Review 2. Production is read-only in this launcher.
set -u
umask 077
printf '\033[38;5;51m\nGharTV · CYAN REVIEW 2 · Live dashboard + voice / picture candidate\n\033[0m'
if ! command -v python3 >/dev/null; then echo 'BLOCKED: python3 missing. Nothing changed.'; exit 1; fi
CLOSE_MARKER="${TMPDIR:-/tmp}/ghartv-close-$$"
export GHARTV_CLOSE_MARKER="$CLOSE_MARKER"
python3 - "$0" "$@" <<'PY'
from __future__ import annotations
import argparse, datetime as dt, hashlib, html, json, os, re, shlex, shutil, subprocess, sys, tempfile, time, urllib.request, urllib.error
from pathlib import Path
if sys.version_info < (3,9): raise SystemExit('Python 3.9 or newer is required. Nothing changed.')
HOME=Path.home(); SELF=Path(sys.argv[1]).resolve()
REPO='AmritSinghGit/ghartv'; REVIEW='bf3c9ddc0d4538c98e16590f5d420fafffba5952'
PRODUCTION='b46b2cd607c309d364d531b5fd9da618cd007f6c'
PROD_HASH='6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199'
VERSION='0.5.5-rc1-voice-quality'; TAG='v0.5.5-rc1'; PACKAGE='in.ghartv.nova'
COLLECTOR='https://ghartv-telemetry.ghartv-47d9a0.workers.dev'
PROD_URL=f'https://github.com/{REPO}/releases/download/v0.5.4-rc5/GharTV-Jio-Live-v0.5.4-rc5.apk'
PROJECT=Path(os.environ.get('GHARTV_PROJECT',str(HOME/'Downloads/GharTV_Nova_v0.4.2'))).expanduser()
STATE=HOME/'Library/Application Support/GharTV/owner-review'; CURRENT=STATE/'current'
RUN_ID='GHARTV-CYAN-2-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
RUN=STATE/'runs'/RUN_ID
parser=argparse.ArgumentParser(); parser.add_argument('--dashboard-only',action='store_true'); parser.add_argument('--noninteractive',action='store_true'); args=parser.parse_args(sys.argv[2:])
for p in (STATE,CURRENT,RUN):
    if p.is_symlink(): raise SystemExit('Refusing a symlinked private output directory')
    p.mkdir(parents=True,exist_ok=True); p.chmod(0o700)
r=dict(run_id=RUN_ID,lane='ghartv',repository=REPO,review_source=REVIEW,production_source=PRODUCTION,
       review_version=VERSION,production_version='0.5.4-rc5-family-photo',production_apk_sha256=PROD_HASH,
       production_feed='NOT_CHECKED',silent_install='NOT_IMPLEMENTED',physical_tv='NOT_VERIFIED',
       delivery_sha='NOT_READ',local_sha='NOT_READ',review_apk_sha256='NOT_BUILT',review_release='NOT_PUBLISHED',
       dashboard='NOT_OPENED',collector_auth='NOT_CHECKED',emulator='UNCHANGED',obsidian='NOT_ATTEMPTED',
       memory_bridge='NOT_ATTEMPTED',cleanup_removed=0,cleanup_bytes=0,status='STARTING',warnings=[])
class Stop(RuntimeError): pass
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*a,**k): return None
opener=urllib.request.build_opener(NoRedirect())
def digest(p):
    h=hashlib.sha256()
    with open(p,'rb') as f:
        for b in iter(lambda:f.read(131072),b''): h.update(b)
    return h.hexdigest()
def write(p,s):
    if p.is_symlink(): raise Stop('Refusing symlinked output')
    tmp=p.with_name(p.name+'.new-'+str(os.getpid()))
    with open(tmp,'x',encoding='utf-8') as f:f.write(s)
    tmp.chmod(0o600); os.replace(tmp,p)
def cmd(argv,timeout=40,required=True,cwd=None,env=None):
    p=subprocess.run(list(map(str,argv)),capture_output=True,text=True,timeout=timeout,cwd=cwd,env=env)
    if required and p.returncode: raise Stop(Path(str(argv[0])).name+' failed (exit '+str(p.returncode)+')')
    return p
def git(*a): return cmd(['git','-C',PROJECT,*a]).stdout.strip()
def get(url,token=''):
    headers={'User-Agent':'GharTV-owner-review/2','Cache-Control':'no-cache'}
    if token: headers['Authorization']='Bearer '+token
    with opener.open(urllib.request.Request(url,headers=headers),timeout=20) as response:
        b=response.read(8*1024*1024+1)
    if len(b)>8*1024*1024: raise Stop('Response exceeds report bound')
    return json.loads(b)
def envfile(path,keys):
    out={}
    if path.is_file() and not path.is_symlink():
        for line in path.read_text().splitlines():
            line=line.strip().removeprefix('export '); k,sep,v=line.partition('=')
            if sep and k in keys:
                values=shlex.split(v,comments=True)
                if len(values)==1: out[k]=values[0]
    return out

def sync():
    print('1 / 4 · Reconcile exact source and existing checkout')
    if not (PROJECT/'.git').exists(): raise Stop('Canonical checkout missing; no duplicate clone created')
    origin=git('remote','get-url','origin').removesuffix('.git')
    if origin not in ('https://github.com/'+REPO,'git@github.com:'+REPO,'ssh://git@github.com/'+REPO): raise Stop('Unexpected origin; preserved')
    r['local_sha']=git('rev-parse','HEAD')
    if git('symbolic-ref','--short','HEAD')!='main': raise Stop('Checkout not on main; preserved')
    if git('status','--porcelain','--untracked-files=normal'): raise Stop('Local changes require reconciliation; no reset, stash or blind push')
    git('fetch','--no-tags','origin','main'); remote=git('rev-parse','origin/main')
    git('merge-base','--is-ancestor',r['local_sha'],remote)
    git('merge-base','--is-ancestor',REVIEW,remote)
    if git('diff','--name-only',REVIEW,remote,'--','android-tv'): raise Stop('A newer app candidate exists; no obsolete install attempted')
    source=cmd(['git','-C',PROJECT,'show',remote+':GHARTV_SYNC_CURRENT_AND_REPORT.command']).stdout.encode()
    if hashlib.sha256(source).hexdigest()!=digest(SELF): raise Stop('Launcher superseded; use the canonical current handoff')
    git('merge','--ff-only',remote); r['local_sha']=git('rev-parse','HEAD'); r['delivery_sha']=remote
    r['checkout']='FAST_FORWARD_VERIFIED'

def production():
    m=get(f'https://raw.githubusercontent.com/{REPO}/main/update/latest.json?check='+str(time.time_ns()))
    expected={'versionCode':14,'sourceCommit':PRODUCTION,'sha256':PROD_HASH,'apkUrl':PROD_URL}
    if any(m.get(k)!=v for k,v in expected.items()): raise Stop('Production feed differs from this handoff; preserved, not overwritten')
    r['production_feed']='RC5_AVAILABLE_FOR_TV_CHECK'; r['production_check_at']=dt.datetime.now(dt.timezone.utc).isoformat()

def dashboard():
    print('2 / 4 · Open live owner dashboard from the existing collector')
    template=PROJECT/'docs/owner.html'
    if not template.is_file(): raise Stop('Committed dashboard template missing')
    token=''
    try:
        config=envfile(HOME/'Library/Application Support/GharTV/telemetry/collector.env',{'GHARTV_TELEMETRY_ENDPOINT','GHARTV_TELEMETRY_ADMIN_TOKEN'})
        if config.get('GHARTV_TELEMETRY_ENDPOINT',COLLECTOR).rstrip('/')!=COLLECTOR: raise Stop('Collector origin mismatch; token not sent')
        token=config.get('GHARTV_TELEMETRY_ADMIN_TOKEN','')
        if token:
            data=get(COLLECTOR+'/v1/admin/summary?days=7',token)
            if data.get('ok') is not True: raise Stop('Collector read did not succeed')
            r['collector_auth']='AUTHENTICATED_READ_SUCCEEDED'
        else: r['collector_auth']='LOCAL_ADMIN_TOKEN_MISSING'
    except Exception as e:
        r['collector_auth']='UNAVAILABLE_'+type(e).__name__; token=''
    boot={'token':token,'run_id':RUN_ID,'review_source':REVIEW,'control_sha':r['delivery_sha'],'review_apk_sha256':r['review_apk_sha256']}
    payload=json.dumps(boot).replace('<','\u003c').replace('>','\u003e').replace('&','\u0026')
    page=template.read_text().replace('<!--OWNER_BOOTSTRAP-->','<script id="owner-bootstrap" type="application/json">'+payload+'</script>')
    target=CURRENT/'owner.html'; write(target,page); r['dashboard_path']=str(target)
    cmd(['open',target]); r['dashboard']='OPENED_AUTO_REFRESH_15_SECONDS'

def cleanup():
    known={'GHARTV_RC5_EXACT_STABLE_R1.command':'3d3a6e0a69b0ba4ce105913d92801af4bec6cb895da37f91a5d9383ef5149901',
      'GHARTV_V054_RC5R1_VALIDATION_REPAIR_AND_CONTINUE.command':'3926275b708788ce3765b7fbb8ccb3fb72b31226fe7ac6fa720b83b5e8ac9916',
      'GHARTV_V054_RC5_CI_REPAIR_PHOTO_AUTOPREVIEW_CONTINUE.command':'669cdd4ced27efaa67baf3b5643b4f0094f37822f3f83fb4159da360362c49f8',
      'GHARTV_SYNC_CURRENT_AND_REPORT.command':'24b3af4f37fac5d85538c990b87e8507442ccad75339b99377fd47bb00916d72'}
    if not (HOME/'Downloads').is_dir(): return
    for p in (HOME/'Downloads').iterdir():
        for name,sha in known.items():
            stem,ext=name.rsplit('.',1)
            if re.fullmatch(re.escape(stem)+r'(?: \(\d+\))?\.'+ext,p.name) and p.is_file() and not p.is_symlink() and p.resolve()!=SELF:
                if digest(p)==sha:
                    n=p.stat().st_size;p.unlink();r['cleanup_removed']+=1;r['cleanup_bytes']+=n

def download(url,p):
    cmd(['curl','--proto','=https','--proto-redir','=https','-fLsS','--connect-timeout','15','--max-time','120',url,'-o',p],130)

def review_tv():
    print('3 / 4 · Build/sign the review candidate; production remains RC5')
    if not shutil.which('gh'): raise Stop('GitHub CLI missing; dashboard is available; review APK not built')
    cmd(['gh','auth','status','-h','github.com'])
    sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(HOME/'Library/Android/sdk')))
    java=Path('/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home')
    if not (java/'bin/java').is_file(): java=Path(cmd(['/usr/libexec/java_home','-v','17']).stdout.strip())
    buildtools=sorted(sdk.glob('build-tools/*/apksigner'),key=lambda p:[int(x) for x in re.findall(r'\d+',p.parent.name)])
    if not buildtools: raise Stop('Android build tools missing')
    signer=buildtools[-1]; aapt=signer.parent/'aapt'; adb=sdk/'platform-tools/adb'
    environment=dict(os.environ,JAVA_HOME=str(java),ANDROID_SDK_ROOT=str(sdk),ANDROID_HOME=str(sdk))
    environment['PATH']=str(java/'bin')+os.pathsep+environment.get('PATH','')
    environment['GRADLE_USER_HOME']=str(HOME/'Library/Caches/GharTV-Nova/gradle-user-home-java17')
    target=CURRENT/'GharTV-review-current.apk'; marker=CURRENT/'review-artifact.json'
    if target.is_symlink() or marker.is_symlink(): raise Stop('Refusing symlinked candidate files')
    reused=False
    if target.is_file() and marker.is_file():
        meta=json.loads(marker.read_text()); reused=meta.get('source')==REVIEW and meta.get('sha256')==digest(target)
    if not reused:
        keys={'GHARTV_SIGNING_STORE','GHARTV_SIGNING_STORE_PASSWORD','GHARTV_SIGNING_KEY_ALIAS','GHARTV_SIGNING_KEY_PASSWORD'}
        values=envfile(HOME/'Library/Application Support/GharTV/signing/signing.env',keys)
        if not all(values.get(k) for k in keys): raise Stop('Existing signing identity unavailable; no replacement key generated')
        values['GHARTV_SIGNING_STORE']=os.path.expanduser(os.path.expandvars(values['GHARTV_SIGNING_STORE']))
        environment.update(values)
        gradle=PROJECT/'android-tv/gradlew'
        if not os.access(gradle,os.X_OK): gradle=HOME/'Library/Caches/GharTV-Nova/gradle-8.11.1/bin/gradle'
        # Build output stays in this private run directory; secrets are never printed by this launcher.
        with open(RUN/'android-build.log','w') as log:
            p=subprocess.run([str(gradle),'--no-daemon','--max-workers=2',':app:assembleRelease'],cwd=PROJECT/'android-tv',env=environment,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
        if p.returncode: raise Stop('Android build failed; inspect private android-build.log. Existing emulator unchanged')
        built=PROJECT/'android-tv/app/build/outputs/apk/release/app-release.apk'
        if not built.is_file(): raise Stop('Signed APK not produced')
        staged=CURRENT/('.review-candidate-'+str(os.getpid())+'.apk')
        if staged.exists() or staged.is_symlink(): raise Stop('Staged candidate path already exists')
        shutil.copyfile(built,staged);staged.chmod(0o600)
    else: staged=target
    if git('rev-parse','HEAD')!=r['local_sha'] or git('status','--porcelain','--untracked-files=normal'):
        raise Stop('Checkout changed during build; no release/install attempted')
    badging=cmd([aapt,'dump','badging',staged],env=environment).stdout
    if not all(x in badging for x in ["name='"+PACKAGE+"'","versionCode='15'","versionName='"+VERSION+"'"]): raise Stop('Unexpected review package/version; not installed')
    def certificate(path):
        s=cmd([signer,'verify','--print-certs',path],env=environment).stdout
        values=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)',s)
        if not values: raise Stop('APK signer was not verified')
        return set(v.lower() for v in values)
    cert=certificate(staged)
    with tempfile.TemporaryDirectory(prefix='.verify-stable-',dir=CURRENT) as td:
        stable=Path(td)/'stable.apk';download(PROD_URL,stable)
        if digest(stable)!=PROD_HASH or certificate(stable)!=cert: raise Stop('Signer continuity failed; no install/uninstall attempted')
    apkhash=digest(staged)
    if staged!=target: os.replace(staged,target)
    r['review_apk_sha256']=apkhash
    write(marker,json.dumps({'source':REVIEW,'sha256':apkhash,'version':VERSION},indent=2)+'\n')
    checksum=CURRENT/'GharTV-review-current.apk.sha256';write(checksum,apkhash+'  '+target.name+'\n')
    probe=cmd(['gh','api',f'repos/{REPO}/releases/tags/{TAG}'],required=False)
    if probe.returncode==0:
        release=json.loads(probe.stdout)
        assets=[a for a in release.get('assets',[]) if a.get('name')==target.name]
        if release.get('target_commitish')!=REVIEW or not release.get('prerelease') or len(assets)!=1 or assets[0].get('digest')!='sha256:'+apkhash:
            raise Stop('Review release already exists with different metadata/bytes; preserved, not clobbered')
        r['review_release']='EXISTING_EXACT_APK_VERIFIED'
    elif '404' in probe.stderr:
        cmd(['gh','release','create',TAG,str(target),str(checksum),'-R',REPO,'--target',REVIEW,'--prerelease','--title','GharTV 0.5.5 RC1 — in-app voice and picture diagnostics','--notes','Owner review only. Exact source '+REVIEW+'. Production feed remains the approved RC5 code 14. No AI super-resolution or silent-install claim.'],120)
        r['review_release']='PRERELEASE_PUBLISHED_NOT_PRODUCTION'
    else: raise Stop('GitHub release could not be read; no blind release write attempted')
    published=json.loads(cmd(['gh','api',f'repos/{REPO}/releases/tags/{TAG}']).stdout)
    advertised=[a for a in published.get('assets',[]) if a.get('name')==target.name]
    if published.get('target_commitish')!=REVIEW or not published.get('prerelease') or len(advertised)!=1 or advertised[0].get('digest')!='sha256:'+apkhash:
        raise Stop('Published review asset did not verify; emulator unchanged')
    print('Review APK verified and pushed. Reusing only the named owner emulator.')
    serial='';devices=cmd([adb,'devices']).stdout.splitlines()
    for line in devices:
        parts=line.split()
        if len(parts)==2 and parts[1]=='device' and parts[0].startswith('emulator-'):
            name=cmd([adb,'-s',parts[0],'emu','avd','name'],required=False).stdout.splitlines()
            if name and name[0].strip()=='GharTV_Nova_Manual_google_tv_API36': serial=parts[0];break
    if not serial:
        if any(x.startswith('emulator-5580') for x in devices): raise Stop('Port 5580 occupied by another/unauthorised emulator; no duplicate started')
        emulator=sdk/'emulator/emulator'
        with open(RUN/'emulator.log','w') as log:
            subprocess.Popen([str(emulator),'-avd','GharTV_Nova_Manual_google_tv_API36','-port','5580','-no-snapshot-load'],stdout=log,stderr=log,start_new_session=True)
        serial='emulator-5580'; cmd([adb,'-s',serial,'wait-for-device'],180)
        for _ in range(90):
            if cmd([adb,'-s',serial,'shell','getprop','sys.boot_completed'],required=False).stdout.strip()=='1':break
            time.sleep(2)
        else: raise Stop('Existing named AVD did not boot in time')
    r['emulator_serial']=serial
    installed=cmd([adb,'-s',serial,'shell','dumpsys','package',PACKAGE]).stdout
    codes=re.findall(r'versionCode=(\d+)',installed)
    if codes and int(codes[0])>15: raise Stop('Emulator has a newer app; no downgrade attempted')
    if codes and int(codes[0])==15:
        paths=cmd([adb,'-s',serial,'shell','pm','path',PACKAGE]).stdout.splitlines()
        if len(paths)!=1 or not paths[0].startswith('package:'): raise Stop('Unexpected installed APK layout')
        with tempfile.TemporaryDirectory(prefix='.installed-',dir=CURRENT) as td:
            installed_apk=Path(td)/'installed.apk';cmd([adb,'-s',serial,'pull',paths[0][8:].strip(),installed_apk])
            if digest(installed_apk)!=apkhash: raise Stop('Different code-15 APK is installed; no overwrite without reconciliation')
    result=cmd([adb,'-s',serial,'install','-r',target],120).stdout
    if 'Success' not in result: raise Stop('Android did not confirm installation; no app data cleared')
    paths=cmd([adb,'-s',serial,'shell','pm','path',PACKAGE]).stdout.splitlines()
    if len(paths)!=1 or not paths[0].startswith('package:'): raise Stop('Post-install APK path unavailable')
    with tempfile.TemporaryDirectory(prefix='.verify-installed-',dir=CURRENT) as td:
        observed_apk=Path(td)/'observed.apk';cmd([adb,'-s',serial,'pull',paths[0][8:].strip(),observed_apk])
        if digest(observed_apk)!=apkhash: raise Stop('Post-install APK digest differs from the reviewed artifact')
    cmd([adb,'-s',serial,'shell','am','force-stop',PACKAGE])
    opened=cmd([adb,'-s',serial,'shell','am','start','-W','-n',PACKAGE+'/.SplashActivity','--es','ghartv_theme_preview','auto']).stdout
    if 'Error:' in opened: raise Stop('Review app did not open')
    r['emulator']='REVIEW_APK_INSTALLED_AND_OPENED';r['owner_decision']='REVIEW_PENDING'
    old=CURRENT/'GharTV-Jio-Live-current.apk'
    if old.is_file() and not old.is_symlink() and digest(old)==PROD_HASH:
        n=old.stat().st_size;old.unlink();r['cleanup_removed']+=1;r['cleanup_bytes']+=n

def handoff():
    values={'RUN_ID':RUN_ID,'LANE_ID':'ghartv','REPOSITORY':REPO,'STATUS':r['status'],'PRODUCTION_FEED':r['production_feed'],
      'PRODUCTION_SOURCE_SHA':PRODUCTION,'PRODUCTION_APK_SHA256':PROD_HASH,'PHYSICAL_TV_UPDATED':r['physical_tv'],
      'SILENT_INSTALL':r['silent_install'],'REVIEW_SOURCE_SHA':REVIEW,'DELIVERY_SHA':r['delivery_sha'],'LOCAL_SHA':r['local_sha'],
      'REVIEW_VERSION':VERSION,'REVIEW_APK_SHA256':r['review_apk_sha256'],'REVIEW_RELEASE':r['review_release'],
      'EMULATOR':r['emulator'],'DASHBOARD':r['dashboard'],'DASHBOARD_PATH':r.get('dashboard_path','NOT_CREATED'),
      'COLLECTOR_AUTH':r['collector_auth'],'OBSIDIAN':r['obsidian'],'MEMORY_BRIDGE':r['memory_bridge'],
      'CLEANUP_REMOVED':r['cleanup_removed'],'CLEANUP_BYTES':r['cleanup_bytes'],'EVIDENCE':str(RUN),
      'BLOCKER':r.get('blocker','NONE'),'OWNER_DECISION':'REVIEW_PENDING','NEXT_ACTION':'Confirm dad update separately. Review in-app Voice and player Picture. Copy this handoff with feedback.'}
    return 'GHARTV_CYAN_REVIEW_2_HANDOFF\n'+'\n'.join(k+'='+str(v) for k,v in values.items())+'\n'

lockfile=STATE/'owner-run.lock'
lock=None
try:
    import fcntl
    if lockfile.is_symlink(): raise Stop('Symlinked lock file')
    lock=open(lockfile,'w');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except Exception:
    print('Another owner run is active, or its private lock is unavailable. No second run started.');sys.exit(1)
try:
    sync();production();cleanup()
    if not args.dashboard_only: review_tv()
    r['status']='REVIEW_READY' if r['emulator']=='REVIEW_APK_INSTALLED_AND_OPENED' else 'DASHBOARD_READY'
except Exception as e:
    r['status']='ACTION_REQUIRED';r['blocker']=str(e) if isinstance(e,Stop) else type(e).__name__
    print('ACTION REQUIRED:',r['blocker'])
finally:
    if r.get('checkout')=='FAST_FORWARD_VERIFIED':
        try: dashboard()
        except Exception as e:
            r['dashboard']='FAILED_'+type(e).__name__;r['warnings'].append('Dashboard: '+type(e).__name__)
            if r['status']=='DASHBOARD_READY': r['status']='ACTION_REQUIRED'
    staged_path=CURRENT/('.review-candidate-'+str(os.getpid())+'.apk')
    if staged_path.is_file() and not staged_path.is_symlink(): staged_path.unlink()
    print('4 / 4 · Save exact handoff; preserve source, credentials and unknown files')
    try:
        note_root=HOME/'Documents/Amrit Executive Memory'
        if note_root.is_dir():
            dest=note_root/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv';dest.mkdir(parents=True,exist_ok=True)
            note=dest/(RUN_ID+'.md');r['obsidian']='WRITTEN';write(note,'# GharTV Cyan Review 2\n\n```text\n'+handoff()+'```\n')
            if shutil.which('amrit-context'):
                a=cmd(['amrit-context','handoff','--file',note],60,False)
                r['memory_bridge']='HANDOFF_EXIT_'+str(a.returncode)+'_REPLICATION_NOT_VERIFIED'
            else:r['memory_bridge']='LOCAL_COMMAND_NOT_AVAILABLE'
            write(note,'# GharTV Cyan Review 2\n\n```text\n'+handoff()+'```\n')
        else:r['obsidian']='EXISTING_VAULT_NOT_FOUND_NOT_DUPLICATED'
    except Exception as e:r['obsidian']='FAILED_'+type(e).__name__
    try:
        write(RUN/'handoff.txt',handoff());write(RUN/'receipt.json',json.dumps(r,indent=2)+'\n');write(CURRENT/'handoff.txt',handoff())
        canonical_runs=HOME/'.local/state/operon-terminal-runs/ghartv'
        if not canonical_runs.is_symlink():
            canonical_runs.mkdir(parents=True,exist_ok=True);write(canonical_runs/(RUN_ID+'.txt'),handoff())
    except Exception as e: print('Receipt write failed:',type(e).__name__)
    print('\n'+handoff())
    if not args.noninteractive:
        try:
            with open('/dev/tty','r+') as tty:
                tty.write('\nPress Enter to copy this handoff: ');tty.flush();tty.readline()
                subprocess.run(['pbcopy'],input=handoff(),text=True,check=False)
                tty.write('Copied. Press Enter to finish this terminal; dashboard and emulator remain: ');tty.flush();tty.readline()
                marker=Path(os.environ['GHARTV_CLOSE_MARKER'])
                with open(marker,'x') as f: f.write('owner-confirmed-close\n')
                marker.chmod(0o600)
        except OSError:pass
sys.exit(0 if r['status'] in ('REVIEW_READY','DASHBOARD_READY') else 1)
PY
RESULT=$?
if [ -f "$CLOSE_MARKER" ] && [ ! -L "$CLOSE_MARKER" ] && [ "$0" = "$HOME/.local/share/ghartv-launcher/current/GHARTV_SYNC_CURRENT_AND_REPORT.command" ] && [ -t 1 ] && [ "${TERM_PROGRAM:-}" = Apple_Terminal ]; then
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
