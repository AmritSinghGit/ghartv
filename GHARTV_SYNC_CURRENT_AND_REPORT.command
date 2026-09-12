#!/bin/bash
# RC2 continuity review. No signing credentials, account tokens, installer or release writes.
set -u
umask 077
printf '\033[38;5;51m\nGharTV · CYAN REVIEW 3 · Source / release / continuity\033[0m\n'
if ! command -v python3 >/dev/null; then echo 'BLOCKED: Python 3 is required. No TV changed.'; exit 1; fi
python3 - "$0" "$@" <<'PY'
from pathlib import Path
import datetime as dt, hashlib, json, os, re, shutil, subprocess, sys, tempfile
HOME=Path.home();SELF=Path(sys.argv[1]).resolve();REPO='AmritSinghGit/ghartv'
SOURCE='b4d0304441b7d00833e4d475c16e43e1ef92b3f3'
PROD='b46b2cd607c309d364d531b5fd9da618cd007f6c'
UNSIGNED='32efc94eaa635b3da1d1895570b857e9f5d2dea5b5bed1151818c48d03a7cd21'
PROD_HASH='6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199'
PROJECT=Path(os.environ.get('GHARTV_PROJECT',str(HOME/'Downloads/GharTV_Nova_v0.4.2'))).expanduser()
STATE=HOME/'Library/Application Support/GharTV/owner-review';CURRENT=STATE/'current'
RUN_ID='GHARTV-CYAN-3-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
RUN=STATE/'runs'/RUN_ID
for directory in (STATE,CURRENT,RUN):
    if any(p.is_symlink() for p in (directory,*directory.parents)):raise SystemExit('Unsafe output ancestry; no change')
    directory.mkdir(parents=True,exist_ok=True);directory.chmod(0o700)
r=dict(run_id=RUN_ID,lane='ghartv',repository=REPO,operon_session=os.environ.get('OPERON_SESSION_ID','UNBOUND'),
    review_source=SOURCE,review_version='0.5.5-rc2-movies-picture',review_code=16,unsigned_sha256=UNSIGNED,
    production_source=PROD,production_code=14,production_feed='NOT_CHECKED',local_sha='NOT_READ',delivery_sha='NOT_READ',
    package='NOT_DOWNLOADED',signing='NOT_ATTEMPTED',signed_apk='NOT_VERIFIED',emulator='UNCHANGED',physical_tv='NOT_VERIFIED',
    obsidian='NOT_ATTEMPTED',memory_bridge='NOT_ATTEMPTED',cleanup_files=0,cleanup_bytes=0,status='STARTING')
class Stop(RuntimeError):pass
def execute(argv,timeout=45):
    p=subprocess.run(list(map(str,argv)),capture_output=True,text=True,timeout=timeout)
    if p.returncode:raise Stop(Path(str(argv[0])).name+' failed; existing work preserved')
    return p.stdout.strip()
def git(*args):return execute(['git','-C',PROJECT,*args])
def sha(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for b in iter(lambda:f.read(131072),b''):h.update(b)
    return h.hexdigest()
def write(path,text):
    if path.is_symlink():raise Stop('Symlinked output refused')
    temp=path.with_name(path.name+'.new-'+str(os.getpid()))
    with open(temp,'x',encoding='utf-8') as f:f.write(text)
    temp.chmod(0o600);os.replace(temp,path)
def download(url,path):
    execute(['curl','--proto','=https','--proto-redir','=https','-fLsS','--connect-timeout','15','--max-time','90',url,'-o',path],100)
def handoff():
    return 'GHARTV_CYAN_REVIEW_3_CONTINUITY\n'+'\n'.join(k.upper()+'='+str(v) for k,v in r.items())+'\nNEXT_ACTION=Review source/release; sign RC2 with the existing owner key before any installation. Production remains RC5.\n'
try:
    import fcntl
    lock_path=STATE/'owner-run.lock'
    if lock_path.is_symlink():raise Stop('Unsafe lock')
    lock=open(lock_path,'w');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except Exception:raise SystemExit('Another owner run is active or lock unavailable; no second run')
try:
    if not (PROJECT/'.git').exists():raise Stop('Canonical checkout missing; no clone created')
    if git('remote','get-url','origin').removesuffix('.git') not in ('https://github.com/'+REPO,'git@github.com:'+REPO,'ssh://git@github.com/'+REPO):raise Stop('Unexpected origin')
    r['local_sha']=git('rev-parse','HEAD')
    if git('symbolic-ref','--short','HEAD')!='main':raise Stop('Expected existing main; no branch switch')
    if git('status','--porcelain','--untracked-files=normal'):raise Stop('Local changes require reconciliation; no reset/stash/blind push')
    git('fetch','--no-tags','origin','main');remote=git('rev-parse','origin/main')
    git('merge-base','--is-ancestor',r['local_sha'],remote);git('merge-base','--is-ancestor',SOURCE,remote)
    if git('diff','--name-only',SOURCE,remote,'--','android-tv'):raise Stop('A different app candidate is on main; no obsolete review')
    raw=subprocess.check_output(['git','-C',str(PROJECT),'show',remote+':GHARTV_SYNC_CURRENT_AND_REPORT.command'])
    if hashlib.sha256(raw).hexdigest()!=sha(SELF):raise Stop('Launcher superseded; use current handoff')
    git('merge','--ff-only',remote);r['delivery_sha']=remote;r['local_sha']=git('rev-parse','HEAD')
    with tempfile.TemporaryDirectory(prefix='.rc2-read-',dir=CURRENT) as temp:
        temp=Path(temp);manifest=temp/'production.json'
        download('https://raw.githubusercontent.com/'+REPO+'/main/update/latest.json',manifest)
        m=json.loads(manifest.read_text())
        if m.get('versionCode')!=14 or m.get('sourceCommit')!=PROD or m.get('sha256')!=PROD_HASH:raise Stop('Production feed changed; not overwritten')
        r['production_feed']='RC5_CODE14_ADVERTISED'
        apk=temp/'unsigned.apk'
        download('https://github.com/'+REPO+'/releases/download/v0.5.5-rc2/GharTV-review-unsigned.apk',apk)
        if sha(apk)!=UNSIGNED:raise Stop('Unsigned release checksum mismatch')
        target=CURRENT/'GharTV-review-unsigned.apk'
        if target.is_symlink():raise Stop('Unsafe review output')
        apk.chmod(0o600);os.replace(apk,target);r['package']='UNSIGNED_VERIFIED_NOT_INSTALLABLE'
    # Only obsolete, byte-identical owner-delivery downloads. Never source or state.
    known={'GHARTV_RC5_EXACT_STABLE_R1.command':'3d3a6e0a69b0ba4ce105913d92801af4bec6cb895da37f91a5d9383ef5149901',
      'GHARTV_SYNC_CURRENT_AND_REPORT.command':'d881494707e32be2dc50e2f4187ff52beb684256d23afa530cda093c9ca2f054',
      'GHARTV_CYAN_REVIEW_2.zip':'7d8906d5ba84be44bb1546f4f6887e5259f734b61eedb0201c99b263901443b8'}
    downloads=HOME/'Downloads'
    if downloads.is_dir() and not downloads.is_symlink():
        for p in downloads.iterdir():
            for name,expected in known.items():
                stem,ext=name.rsplit('.',1)
                if re.fullmatch(re.escape(stem)+r'(?: \(\d+\))?\.'+re.escape(ext),p.name) and p.is_file() and not p.is_symlink() and p.resolve()!=SELF and sha(p)==expected:
                    n=p.stat().st_size;p.unlink();r['cleanup_files']+=1;r['cleanup_bytes']+=n
    execute(['open','https://amritsinghgit.github.io/ghartv/owner.html'])
    execute(['open','https://github.com/'+REPO+'/releases/tag/v0.5.5-rc2'])
    r['status']='SOURCE_REVIEW_READY_SIGNATURE_REQUIRED'
except Exception as e:
    r['status']='ACTION_REQUIRED';r['blocker']=str(e) if isinstance(e,Stop) else type(e).__name__
finally:
    try:
        vault=HOME/'Documents/Amrit Executive Memory'
        if vault.is_dir():
            dest=vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
            if any(p.is_symlink() for p in (dest,*dest.parents)):raise Stop('Unsafe vault ancestry')
            dest.mkdir(parents=True,exist_ok=True);note=dest/(RUN_ID+'.md')
            body='# GharTV RC2 — source and commercial direction\n\n```text\n'+handoff()+'```\n'
            if r['delivery_sha']!='NOT_READ':
                for name in ('CURRENT_HANDOFF.md','PRODUCT_COMMERCIALIZATION.md'):
                    f=PROJECT/name
                    if f.is_file() and not f.is_symlink():body+='\n\n---\n\n'+f.read_text()
            write(note,body)
            if hashlib.sha256(note.read_bytes()).hexdigest()!=hashlib.sha256(body.encode()).hexdigest():raise Stop('Obsidian readback mismatch')
            r['obsidian']='RUN_NOTE_WRITTEN_READBACK_VERIFIED'
            if shutil.which('amrit-context'):
                try:
                    a=subprocess.run(['amrit-context','handoff','--file',str(note)],capture_output=True,timeout=60)
                    b=subprocess.run(['amrit-context','sync-once'],capture_output=True,timeout=60)
                    r['memory_bridge']='HANDOFF_EXIT_'+str(a.returncode)+'_SYNC_EXIT_'+str(b.returncode)+'_REPLICATION_UNVERIFIED'
                except Exception as e:r['memory_bridge']='FAILED_'+type(e).__name__
            else:r['memory_bridge']='COMMAND_NOT_AVAILABLE'
            write(dest/(RUN_ID+'-receipt.txt'),handoff())
        else:r['obsidian']='EXISTING_VAULT_NOT_FOUND_NO_DUPLICATE_CREATED'
    except Exception as e:r['obsidian']='FAILED_'+type(e).__name__
    write(RUN/'handoff.txt',handoff());write(RUN/'receipt.json',json.dumps(r,indent=2)+'\n');write(CURRENT/'handoff.txt',handoff())
    print('\n'+handoff())
    print('No signing key or collector token was read. No APK was installed or promoted.')
    if '--noninteractive' not in sys.argv:
        try:
            with open('/dev/tty','r+') as tty:
                tty.write('\nPress Enter to copy the handoff: ');tty.flush();tty.readline()
                subprocess.run(['pbcopy'],input=handoff(),text=True,check=False)
                tty.write('Copied. Press Enter to finish; your running TV candidate remains unchanged: ');tty.flush();tty.readline()
        except OSError:pass
sys.exit(0 if r['status']=='SOURCE_REVIEW_READY_SIGNATURE_REQUIRED' else 1)
PY
