"""Repair the existing RC7 review, preserve evidence, remove only verified redundant kits.
No APK install, key read, production write, new service, checkout mutation or system DNS edit.
"""
from __future__ import annotations
import datetime as dt,fcntl,hashlib,importlib.util,json,os,re,shutil,signal,stat,subprocess,sys,time,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).absolute().parent
HOME=Path.home();STATE=HOME/'Library/Application Support/GharTV/owner-review'
CURRENT=STATE/'current';RUNTIME=STATE/'runtime-current';WEBSTATE=HOME/'Library/Application Support/GharTV/web-player'
BASE='211c2624f5a553b041c1fc8d2194625f8d6b435a';VERSION='0.6.0-rc7-network-diagnostics';SERIAL='emulator-5580'
PKG='in.ghartv.nova';AVD='GharTV_Nova_Manual_google_tv_API36'
ID='GHARTV-CYAN-11-RECOVERY-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
RUN=STATE/'runs'/ID
class Hold(Exception):pass

def safe(path):
    p=Path(path).absolute()
    for q in (p,*p.parents):
        if q.is_symlink():raise Hold('SYMLINK_PRESERVED')
    return p

def private_dir(path):
    safe(path).mkdir(parents=True,exist_ok=True,mode=0o700)

def owned_file(path,limit=150*1024*1024):
    p=safe(path);s=p.stat()
    if not stat.S_ISREG(s.st_mode) or s.st_uid!=os.getuid() or s.st_size>limit:raise Hold('FILE_OWNERSHIP_OR_SIZE_REJECTED')
    return p

def digest(path):
    h=hashlib.sha256()
    with safe(path).open('rb') as f:
        for chunk in iter(lambda:f.read(131072),b''):h.update(chunk)
    return h.hexdigest()

def write(path,data):
    p=safe(path);private_dir(p.parent)
    if not isinstance(data,(str,bytes)):data=json.dumps(data,indent=2)+'\n'
    raw=data.encode() if isinstance(data,str) else data
    temp=p.with_name(p.name+'.tmp-'+str(os.getpid()))
    safe(temp)
    with temp.open('xb') as f:os.chmod(temp,0o600);f.write(raw);f.flush();os.fsync(f.fileno())
    os.replace(temp,p)

def read(path,limit=256000):return json.loads(owned_file(path,limit).read_text())
def call(args,timeout=15):
    return subprocess.run([str(x) for x in args],capture_output=True,text=True,timeout=timeout,stdin=subprocess.DEVNULL)
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*a,**kw):return None
OPENER=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
def http(path,timeout=5,headers=None):
    req=urllib.request.Request('http://127.0.0.1:8790'+path,headers=headers or {})
    with OPENER.open(req,timeout=timeout) as response:return response.read(250000)
def health():return json.loads(http('/api/health'))
def private_owner_headers():
    page=http('/owner.html').decode();m=re.search(r'id="owner-bootstrap" type="application/json">(.*?)</script>',page,re.S)
    if not m:raise Hold('OWNER_NONCE_NOT_FOUND')
    nonce=json.loads(m[1]).get('token','')
    if not re.fullmatch('[a-f0-9]{64}',nonce):raise Hold('OWNER_NONCE_INVALID')
    return {'Authorization':'Bearer '+nonce,'Origin':'http://127.0.0.1:8790'}

def checkpoint(r):
    write(RUN/'receipt.json',r)
    text='GHARTV_EXISTING_RC7_RECOVERY_HANDOFF\n'+'\n'.join(k.upper()+'='+(json.dumps(v) if isinstance(v,(dict,list)) else str(v)) for k,v in r.items())+'\n'
    write(RUN/'handoff.txt',text)
    return text

def preserve_inputs(prior):
    old=safe(STATE/'runs'/prior['run_id'])
    if not re.fullmatch(r'GHARTV-CYAN-[A-Za-z0-9-]+',prior['run_id']) or old.parent!=STATE/'runs':raise Hold('RUN_PATH_INVALID')
    names=['handoff.txt','receipt.json','NETWORK_CHECK.json','network-last.json','SUPPORT_SIGNALS.json','GHARTV_SUPPORT.zip']
    original=read(old/'receipt.json')
    if original.get('review_source')!=BASE or original.get('run_id')!=prior['run_id']:raise Hold('EVIDENCE_IDENTITY_CHANGED')
    archived=RUN/'preserved-inputs.zip'; records={}
    with zipfile.ZipFile(archived,'w',zipfile.ZIP_DEFLATED) as z:
        os.chmod(archived,0o600)
        for name in names:
            p=old/name
            if p.exists():owned_file(p,4*1024*1024);records[name]=digest(p);z.write(p,name)
    with zipfile.ZipFile(archived) as z:
        if z.testzip() is not None:raise Hold('EVIDENCE_ARCHIVE_INVALID')
        for name,h in records.items():
            if hashlib.sha256(z.read(name)).hexdigest()!=h:raise Hold('EVIDENCE_ARCHIVE_MISMATCH')
    write(RUN/'preserved-inputs.sha256.json',records)
    return old,records

def existing_sdk():
    sdk=HOME/'Library/Android/sdk';safe(sdk)
    for p in [sdk/'platform-tools/adb',sdk/'emulator/emulator']:
        if not p.is_file():raise Hold('EXISTING_ANDROID_TOOLS_NOT_FOUND')
    return sdk

def verify_apk(sdk,expected):
    adb=sdk/'platform-tools/adb'
    name=call([adb,'-s',SERIAL,'emu','avd','name']).stdout.splitlines()
    if not name or name[0].strip()!=AVD:raise Hold('CANONICAL_AVD_NOT_RUNNING')
    codes=re.findall(r'versionCode=(\d+)',call([adb,'-s',SERIAL,'shell','dumpsys','package',PKG]).stdout)
    if not codes or codes[0]!='23':raise Hold('INSTALLED_VERSION_CHANGED_NO_REPAIR')
    paths=call([adb,'-s',SERIAL,'shell','pm','path',PKG]).stdout.splitlines()
    if len(paths)!=1 or not paths[0].startswith('package:/data/app/') or not paths[0].endswith('/base.apk'):raise Hold('APK_LAYOUT_CHANGED')
    path=paths[0][8:].strip()
    if re.search(r'[^A-Za-z0-9_./=+~\-]',path):raise Hold('APK_PATH_REJECTED')
    # Read the hash on the device; no install, no key and no data mutation.
    result=call([adb,'-s',SERIAL,'shell','sha256sum',path],12)
    got=result.stdout.split()[0] if result.stdout.split() else ''
    if got!=expected:raise Hold('INSTALLED_BYTES_CHANGED')
    return got

def web_repair(manifest,r):
    marker=read(RUNTIME/'.ghartv-managed.json')
    if marker.get('source')!=BASE or marker.get('owner')!='ghartv-review-companion-v1':raise Hold('RUNTIME_MARKER_CHANGED')
    for name,expected in manifest['baseline'].items():
        p=owned_file(RUNTIME/name)
        permitted={expected,manifest['payload'].get(name,expected)}
        if digest(p) not in permitted:raise Hold('RUNTIME_FILE_CHANGED_PRESERVED')
    for name,expected in manifest['payload'].items():
        if digest(ROOT/'payload'/name)!=expected:raise Hold('REPAIR_PAYLOAD_MISMATCH')
    initial=health()
    if initial.get('service')!='ghartv-web-player' or initial.get('commit')!=BASE:raise Hold('WEB_RUNTIME_IDENTITY_CHANGED')
    preview=json.loads(http('/owner-api/fabric/status',headers=private_owner_headers()))
    if preview.get('active'):raise Hold('ACTIVE_INTERNET_SHARE_PRESERVED')
    pidtext=owned_file(WEBSTATE/'server.pid',32).read_text().strip()
    if not re.fullmatch('[1-9][0-9]{0,8}',pidtext):raise Hold('WEB_PID_INVALID')
    cmd=call(['/bin/ps','-p',pidtext,'-o','command=']).stdout.strip()
    uid=call(['/bin/ps','-p',pidtext,'-o','uid=']).stdout.strip()
    listener=set(call(['/usr/sbin/lsof','-nP','-iTCP:8790','-sTCP:LISTEN','-t']).stdout.split())
    if not cmd.endswith(str(RUNTIME/'web-player/server.mjs')) or uid!=str(os.getuid()) or listener!={pidtext}:raise Hold('WEB_PROCESS_OWNERSHIP_UNVERIFIED')
    node=shutil.which('node')
    if not node:raise Hold('NODE_NOT_FOUND')
    backup=RUN/'web-before';private_dir(backup)
    for name in manifest['payload']:write(backup/name,owned_file(RUNTIME/name).read_bytes())
    env={k:v for k,v in os.environ.items() if not k.startswith('GHARTV_SIGNING_')}
    env.update(GHARTV_WEB_HOST='127.0.0.1',GHARTV_WEB_PORT='8790',GHARTV_WEB_SHA=BASE,
               GHARTV_WEB_OVERLAY=manifest['payload']['web-player/server.mjs'],TZ='Asia/Kolkata')
    def start(environment):
        private_dir(WEBSTATE)
        with owned_or_new_log(WEBSTATE/'server.log') as log:
            process=subprocess.Popen([node,str(RUNTIME/'web-player/server.mjs')],stdin=subprocess.DEVNULL,stdout=log,stderr=log,env=environment,start_new_session=True)
        write(WEBSTATE/'server.pid',str(process.pid)+'\n');return process
    os.kill(int(pidtext),signal.SIGTERM)
    for _ in range(50):
        if not call(['/usr/sbin/lsof','-nP','-iTCP:8790','-sTCP:LISTEN','-t']).stdout.strip():break
        time.sleep(.1)
    else:raise Hold('WEB_STOP_NOT_CONFIRMED_NO_FORCED_KILL')
    process=None
    try:
        for name in manifest['payload']:write(RUNTIME/name,(ROOT/'payload'/name).read_bytes())
        process=start(env)
        for _ in range(40):
            time.sleep(.2)
            if process.poll() is not None:break
            try:
                h=health()
                if h.get('commit')==BASE and h.get('web_overlay')==env['GHARTV_WEB_OVERLAY']:break
            except Exception:continue
        else:raise Hold('WEB_START_TIMED_OUT')
        h=health()
        if h.get('commit')!=BASE or h.get('web_overlay')!=env['GHARTV_WEB_OVERLAY']:raise Hold('WEB_REPAIR_HEALTH_MISMATCH')
        for name,hsh in manifest['payload'].items():
            if digest(RUNTIME/name)!=hsh:raise Hold('WEB_REPAIR_BYTES_CHANGED')
        write(RUNTIME/'.ghartv-repair.json',{'kind':'RC7_RECOVERY_R1','apk_source':BASE,'web_source':manifest['web_source'],'delivery_source':manifest['delivery_source'],'payload':manifest['payload'],'promotion':'HELD'})
        r.update(web_repair='TRANSPORT_FILES_AND_HEALTH_VERIFIED',web_source=manifest['web_source'],repair_source=manifest['delivery_source'],web_overlay_sha256=env['GHARTV_WEB_OVERLAY'])
    except Exception:
        # Roll back only our own child and exact modified files. Never kill another listener.
        if process is not None and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=8)
            except subprocess.TimeoutExpired:raise Hold('REPAIR_CHILD_WILL_NOT_STOP_NO_ROLLBACK_OVER_LIVE_PROCESS')
        if call(['/usr/sbin/lsof','-nP','-iTCP:8790','-sTCP:LISTEN','-t']).stdout.strip():raise Hold('PORT_CHANGED_ROLLBACK_FILES_PRESERVED')
        for name in manifest['payload']:write(RUNTIME/name,(backup/name).read_bytes())
        oldenv=dict(env);oldenv['GHARTV_WEB_OVERLAY']=initial.get('web_overlay','not_verified');start(oldenv)
        raise

def owned_or_new_log(path):
    safe(path)
    if path.exists():owned_file(path,300*1024*1024)
    handle=path.open('a');os.chmod(path,0o600);return handle

def eligible_tree(directory,expected):
    """Return only a byte-exact kit tree. Unknown, changed or symlink files veto deletion."""
    try:
        safe(directory)
        if not directory.is_dir() or directory.stat().st_uid!=os.getuid():return False
        actual={}
        for parent,dirs,names in os.walk(directory,followlinks=False):
            for d in dirs:
                p=Path(parent)/d;safe(p)
                if d in ('.git','.hg','.svn') or p.stat().st_uid!=os.getuid():return False
            for name in names:
                p=owned_file(Path(parent)/name);rel=str(p.relative_to(directory))
                if rel not in expected:return False
                actual[rel]=digest(p)
                if actual[rel]!=expected[rel]:return False
        return actual==expected
    except (OSError,Hold):return False

def cleanup_known(manifest,old,records):
    removed=[];skipped=[]
    def remove_file(p,h):
        try:
            if digest(owned_file(p))!=h:skipped.append(str(p));return
            size=p.stat().st_size;p.unlink();removed.append({'path':str(p),'bytes':size,'sha256':h})
        except (OSError,Hold):skipped.append(str(p))
    for parent in (HOME/'Downloads',HOME/'Desktop'):
        if not parent.is_dir() or parent.is_symlink():continue
        entries=list(parent.iterdir())
        if len(entries)>10000:skipped.append(str(parent));continue
        trusted_trees={}
        for item in manifest['obsolete_kits']:
            prefix=Path(item['name']).stem+'/'
            for candidate in entries:
                if not re.fullmatch(re.escape(Path(item['name']).stem)+r'(?: \([0-9]+\)| [0-9]+)?\.zip',candidate.name):continue
                try:
                    if digest(owned_file(candidate))!=item['sha256']:continue
                    with zipfile.ZipFile(candidate) as z:
                        infos=[x for x in z.infolist() if not x.is_dir()]
                        if len(infos)>8000 or sum(x.file_size for x in infos)>100*1024*1024:continue
                        if not all(x.filename.startswith(prefix) and '..' not in Path(x.filename).parts for x in infos):continue
                        trusted_trees[item['name']]={x.filename[len(prefix):]:hashlib.sha256(z.read(x)).hexdigest() for x in infos}
                        break
                except (OSError,Hold,zipfile.BadZipFile):continue
        for p in entries:
            for item in manifest['obsolete_kits']:
                # Includes known duplicate names, never general GHARTV* wildcards.
                stem=Path(item['name']).stem
                if re.fullmatch(re.escape(stem)+r'(?: \([0-9]+\)| [0-9]+)?\.zip',p.name):remove_file(p,item['sha256'])
                elif p.name==item.get('directory') and item['name'] in trusted_trees and eligible_tree(p,trusted_trees[item['name']]):
                    size=sum(q.stat().st_size for q in p.rglob('*') if q.is_file())
                    shutil.rmtree(p);removed.append({'path':str(p),'bytes':size,'verified_kit':item['sha256']})
            # Only loose evidence duplicates with a verified canonical original + archive.
            if p.name in records and (old/p.name).is_file() and digest(old/p.name)==records[p.name]:remove_file(p,records[p.name])
    return removed,skipped

def main():
    if sys.platform!='darwin':raise Hold('MAC_ONLY_NO_ACTION_TAKEN')
    print('\033[36mGharTV · RC7 recovery + bounded cleanup · same APK, no public rollout\033[0m',flush=True)
    manifest=read(ROOT/'repair-manifest.json',512000)
    for path in (STATE,CURRENT,RUN):private_dir(path)
    lockpath=safe(STATE/'owner-run.lock')
    with lockpath.open('a') as lock:
        os.chmod(lockpath,0o600);fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        prior=read(CURRENT/'receipt.json')
        if prior.get('review_source')!=BASE or prior.get('version_code')!=23:raise Hold('CURRENT_CANDIDATE_CHANGED_NO_ACTION')
        if not re.fullmatch('[a-f0-9]{64}',prior.get('signed_apk_sha256','')):raise Hold('SIGNED_IDENTITY_NOT_RECORDED')
        r=dict(prior);r.update(run_id=ID,previous_run_id=prior['run_id'],time_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
             time_ist=dt.datetime.now(dt.timezone(dt.timedelta(hours=5,minutes=30))).isoformat(),
             status='ACTION_REQUIRED',owner_decision='REJECTED_PLAYBACK_BLOCKED',phase='RECOVERY',
             cleanup_count=0,cleanup_bytes=0,production_feed='NOT_CHANGED',provider_playback='NOT_TESTED_BY_RECOVERY',
             emulator_network_restart='NOT_ATTEMPTED',receipt_sync='NOT_ATTEMPTED',obsidian='NOT_WRITTEN',memory_bridge='NOT_RUN',
             evidence=str(RUN),web_repair='NOT_ATTEMPTED')
        for key in ('private_support_report','prior_runtime_preserved','old_managed_runtime_cleanup','support_capture_id','collector_capture'):
            r.pop(key,None)
        try:
            old,records=preserve_inputs(prior);r['evidence_preservation']='EXACT_ARCHIVE_READBACK_VERIFIED'
            print('1 / 4  Original support evidence preserved. Current APK will not be replaced.',flush=True)
            try:web_repair(manifest,r)
            except Exception as e:r['web_repair']='HELD_'+(str(e) if isinstance(e,Hold) else type(e).__name__)
            checkpoint(r)
            print('2 / 4  Checking the same emulator; conditional cold boot only on fresh corroborated DNS failure.',flush=True)
            network={'status':'NOT_OBSERVED'}
            try:
                sdk=existing_sdk();verify_apk(sdk,prior['signed_apk_sha256'])
                spec=importlib.util.spec_from_file_location('ghartv_recovery',ROOT/'emulator_network_repair.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
                network=helper.inspect_and_repair(sdk,SERIAL,RUN,ID)
                r['network_check']=network.get('status');r['emulator_network_restart']=network.get('restart')
                verify_apk(sdk,prior['signed_apk_sha256']);r['emulator']='RC7_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND' if network.get('foreground_after_check') else 'RC7_BYTES_VERIFIED_FOREGROUND_UNCONFIRMED'
                r['installed_apk']='UNCHANGED_BYTE_VERIFIED'
            except Exception as e:
                r['network_check']='HELD_'+(str(e) if isinstance(e,Hold) else type(e).__name__)
                r['emulator']='UNCHANGED_CURRENT_FOREGROUND_UNVERIFIED'
            r['provider_playback']='OWNER_REVIEW_REQUIRED_NOT_INFERRED_FROM_PROBE'
            print('3 / 4  Removing only hash-verified obsolete installers and exact redundant copies.',flush=True)
            removed,skipped=cleanup_known(manifest,old,records)
            write(RUN/'cleanup.json',{'removed':removed,'preserved_unverified':skipped,'scope':'KNOWN_OLD_KITS_AND_VERIFIED_LOOSE_EVIDENCE_DUPLICATES','runtime_checkout_keys_settings_run_history':'PRESERVED'})
            r.update(cleanup_count=len(removed),cleanup_bytes=sum(x['bytes'] for x in removed),phase='RECOVERY_REVIEW',review_screen='MainActivity')
            if network.get('status') in ('JIO_HTTPS_REACHABLE','DNS_AND_JIO_HTTPS_RECOVERED') and r['web_repair']=='TRANSPORT_FILES_AND_HEALTH_VERIFIED':r['recovery']='READY_FOR_PLAYBACK_RETEST_NOT_APPROVED'
            else:r['recovery']='NETWORK_OR_WEB_ATTENTION_REQUIRED'
        except Exception as e:
            r['blocker']=str(e) if isinstance(e,Hold) else type(e).__name__
        finally:
            # Preserve installed baseline evidence. Never publish a successful playback claim.
            text=checkpoint(r);write(CURRENT/'receipt.json',r);write(CURRENT/'handoff.txt',text)
            try:
                vault=HOME/'Documents/Amrit Executive Memory'
                if not safe(vault).is_dir():raise Hold('VAULT_NOT_FOUND')
                dest=vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
                body='<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->\n# GharTV RC7 recovery and bounded cleanup\n\n'+text+'\nPlayback remains unapproved. Original run evidence, APK, keys, app data and checkout retained.\n'
                write(dest/(ID+'.md'),body)
                current=dest/'GharTV - Current Progress.md'
                if current.exists():
                    prior_note=owned_file(current,1024*1024).read_bytes()
                    write(RUN/'previous-progress-note.md',prior_note)
                    if b'<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->' not in prior_note:current=dest/(ID+'-progress.md')
                write(current,body)
                if current.read_text()!=body:raise Hold('NOTE_READBACK_MISMATCH')
                r['obsidian']='WRITTEN_AND_READBACK_VERIFIED';r['obsidian_note']=str(current)
            except Exception as e:r['obsidian']='NOT_CONFIRMED_'+type(e).__name__
            text=checkpoint(r);write(CURRENT/'receipt.json',r);write(CURRENT/'handoff.txt',text)
            bridge=HOME/'bin/amrit-context'
            if not bridge.is_file():
                found=shutil.which('amrit-context');bridge=Path(found) if found else None
            if bridge and r.get('obsidian_note'):
                try:
                    states=[]
                    for cmd in ([str(bridge),'handoff','--file',r['obsidian_note']],[str(bridge),'sync-once']):
                        proc=subprocess.Popen(cmd,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
                        try:states.append('EXIT_'+str(proc.wait(timeout=8)))
                        except subprocess.TimeoutExpired:
                            os.killpg(proc.pid,signal.SIGTERM)
                            try:proc.wait(timeout=2)
                            except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
                            states.append('TIMEOUT_PENDING');break
                    r['memory_bridge']='_'.join(states)+'_REPLICA_UNVERIFIED'
                except Exception:r['memory_bridge']='PENDING_LOCAL_TRANSPORT'
            text=checkpoint(r);write(CURRENT/'receipt.json',r);write(CURRENT/'handoff.txt',text)
            if r.get('web_repair')=='TRANSPORT_FILES_AND_HEALTH_VERIFIED':
                try:
                    sync=call(['node',RUNTIME/'web-player/review-sync.mjs','--publish'],23)
                    result=json.loads(sync.stdout);r['receipt_sync']=result.get('status','NOT_CONFIRMED')
                except Exception:r['receipt_sync']='PENDING_TRANSPORT'
            text=checkpoint(r);write(CURRENT/'receipt.json',r);write(CURRENT/'handoff.txt',text)
            if r.get('obsidian_note'):
                try:
                    write(Path(r['obsidian_note']),'<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->\n# GharTV RC7 recovery and bounded cleanup\n\n'+text+'\nPlayback remains unapproved. No credentials, app data, sources or historical run evidence were deleted.\n')
                    if text not in Path(r['obsidian_note']).read_text():raise Hold('FINAL_NOTE_READBACK_MISMATCH')
                except Exception:r['obsidian']='FINAL_NOTE_READBACK_FAILED';text=checkpoint(r);write(CURRENT/'receipt.json',r);write(CURRENT/'handoff.txt',text)
            print('4 / 4  Saved outcome. No public update, account reset, install or tunnel was performed.\n',flush=True)
            print(text)
            for url in ('http://127.0.0.1:8790/','http://127.0.0.1:8790/owner.html'):
                try:call(['open',url],5)
                except Exception:pass
            if sys.stdin.isatty():
                try:
                    input('Press Enter to copy this handoff: ')
                    subprocess.run(['pbcopy'],input=text,text=True,check=False)
                    input('Press Enter to finish: ')
                except EOFError:pass
        return 0 if r.get('recovery')=='READY_FOR_PLAYBACK_RETEST_NOT_APPROVED' else 1
if __name__=='__main__':
    try:sys.exit(main())
    except Exception as e:
        print('RECOVERY_NOT_STARTED: '+(str(e) if isinstance(e,Hold) else type(e).__name__));sys.exit(1)
