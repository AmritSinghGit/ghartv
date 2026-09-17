"""Apply only the launch-control fix to the already-installed RC7 recovery runtime."""
from __future__ import annotations
import importlib.util,json,os,shutil,subprocess,sys,datetime as dt,fcntl
from pathlib import Path
ROOT=Path(__file__).absolute().parent

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def main():
    if sys.platform!='darwin':raise RuntimeError('MAC_ONLY_NO_ACTION')
    r=load('ghartv_existing_repair',ROOT/'recover_and_clean.py')
    ident='GHARTV-CYAN-12-LAUNCH-FIX-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
    r.ID=ident;r.RUN=r.STATE/'runs'/ident;r.private_dir(r.RUN)
    manifest=r.read(ROOT/'repair-manifest.json',512000)
    helper=ROOT/'tv_local.py';target=r.RUNTIME/'tools/tv_local.py'
    if r.digest(helper)!=manifest['helper_sha256']:raise RuntimeError('HELPER_HASH_MISMATCH')
    outcome={'run_id':ident,'kind':'RC7_OPEN_CONTROL_FIX','apk':'UNCHANGED','production':'HELD',
             'owner_decision':'REJECTED_PLAYBACK_BLOCKED','web_repair':'NOT_ATTEMPTED','cleanup':'NOT_REQUESTED_NO_DELETION'}
    with r.safe(r.STATE/'owner-run.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        receipt=r.read(r.CURRENT/'receipt.json')
        marker=r.read(r.RUNTIME/'.ghartv-managed.json')
        if receipt.get('review_source')!=r.BASE or marker.get('source')!=r.BASE:raise RuntimeError('DIFFERENT_CANDIDATE_PRESERVED')
        for name,expected in manifest['baseline'].items():
            if r.digest(r.owned_file(r.RUNTIME/name)) not in {expected,manifest['payload'][name]}:raise RuntimeError('CHANGED_RUNTIME_PRESERVED')
        if target.exists() and r.digest(r.owned_file(target))!=manifest['helper_sha256']:raise RuntimeError('UNKNOWN_HELPER_PRESERVED')
        r.write(target,helper.read_bytes())
        try:r.web_repair(manifest,outcome)
        finally:r.write(r.RUN/'INSTALL_RESULT.json',outcome)
    # Persistent open shortcut points at the same managed runtime. No shell-profile hooks.
    launch=r.HOME/'.local/share/ghartv-launcher/current';r.private_dir(launch)
    scripts={
      'GHARTV_OPEN_TV.command':'open',
      'GHARTV_COLLECT_LOCAL_LOGS.command':'capture',
    }
    for filename,action in scripts.items():
        p=launch/filename
        text='#!/bin/bash\n# GHARTV_PERSISTENT_TV_OPEN_V1\nset -euo pipefail\numask 077\nexport PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"\nexec python3 "$HOME/Library/Application Support/GharTV/owner-review/runtime-current/tools/tv_local.py" '+action+'\n'
        if p.exists() and 'GHARTV_PERSISTENT_TV_OPEN_V1' not in r.owned_file(p).read_text():raise RuntimeError('EXISTING_SHORTCUT_PRESERVED')
        r.write(p,text);p.chmod(0o700)
    desktop=r.HOME/'Desktop/GharTV TV.command'
    text=(launch/'GHARTV_OPEN_TV.command').read_text()
    if desktop.parent.is_dir() and (not desktop.exists() or 'GHARTV_PERSISTENT_TV_OPEN_V1' in r.owned_file(desktop).read_text()):
        r.write(desktop,text);desktop.chmod(0o700);outcome['desktop_shortcut']='CREATED'
    # A standard AppleScript app opens that command in Terminal and is Spotlight/Dock accessible.
    apps=r.HOME/'Applications';r.private_dir(apps);app=apps/'GharTV TV.app'
    if not app.exists():
        applescript='on run\n do shell script "/usr/bin/open -a Terminal " & quoted form of (POSIX path of (path to home folder) & ".local/share/ghartv-launcher/current/GHARTV_OPEN_TV.command")\nend run\n'
        p=subprocess.run(['/usr/bin/osacompile','-o',str(app),'-'],input=applescript,text=True,capture_output=True,timeout=15)
        if p.returncode==0:r.write(app/'Contents/.ghartv-shortcut','GHARTV_PERSISTENT_TV_OPEN_V1\n');outcome['mac_app']='CREATED'
        else:outcome['mac_app']='NOT_CREATED_DESKTOP_COMMAND_AVAILABLE'
    else:outcome['mac_app']='EXISTING_PRESERVED'
    helper=load('ghartv_tv_local',target)
    print('\033[36mGharTV · Open-control fix · opening the existing TV and collecting local evidence\033[0m',flush=True)
    opened=helper.main_action('capture',r.BASE)
    outcome['tv']=opened;r.write(r.RUN/'INSTALL_RESULT.json',outcome)
    note=r.HOME/'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
    text='GHARTV_OPEN_CONTROL_HANDOFF\n'+json.dumps(outcome,indent=2)+'\n'
    r.write(r.RUN/'handoff.txt',text)
    if note.is_dir():r.write(note/(ident+'.md'),'# GharTV open-control correction\n\n'+text+'\nNo APK rebuild, production action or cleanup.\n')
    for url in ('http://127.0.0.1:8790/','http://127.0.0.1:8790/owner.html'):
        subprocess.run(['/usr/bin/open',url],check=False)
    if opened.get('private_report'):subprocess.run(['/usr/bin/open','-R',opened['private_report']],check=False)
    # Publish only a minimal technical outcome to the existing PR, never the private report.
    safe_notice={'run_id':ident,'tool_revision':'tv-open-1.0.0','apk_source':r.BASE,
                 'source':manifest['delivery_source'],'app_foreground':opened.get('foreground',False),
                 'private_log_capture':opened.get('capture','NOT_COMPLETED'),
                 'network_summary':opened.get('network_summary',{}),
                 'owner_decision':'REJECTED_PLAYBACK_BLOCKED','production_changed':False}
    gh=shutil.which('gh')
    if gh:
        try:
            # A single execution notice, not a replacement of the installed-APK receipt.
            message='GharTV open-control local result (no raw logs or paths):\n```json\n'+json.dumps(safe_notice,indent=2)+'\n```'
            p=subprocess.run([gh,'pr','comment','1','--repo','AmritSinghGit/ghartv','--body',message],
                  stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=12,env={**os.environ,'GH_PROMPT_DISABLED':'1'})
            outcome['github_notice']='POST_COMMAND_SUCCEEDED_READBACK_UNVERIFIED' if p.returncode==0 else 'PENDING'
        except Exception:outcome['github_notice']='PENDING'
    r.write(r.RUN/'INSTALL_RESULT.json',outcome)
    text='GHARTV_OPEN_CONTROL_HANDOFF\n'+json.dumps(outcome,indent=2)+'\n'
    r.write(r.RUN/'handoff.txt',text)
    if note.is_dir():r.write(note/(ident+'.md'),'# GharTV open-control correction\n\n'+text+'\nNo APK rebuild, production action or cleanup.\n')
    print(text,flush=True)
    if sys.stdin.isatty():
        try:
            input('Press Enter to copy this handoff: ');subprocess.run(['pbcopy'],input=text,text=True)
            input('Press Enter to finish (the TV stays open): ')
        except EOFError:pass
    return 0 if opened.get('ok') else 1
if __name__=='__main__':
    try:sys.exit(main())
    except Exception as e:
        print('OPEN_FIX_NOT_COMPLETED: '+(str(e) if isinstance(e,RuntimeError) else type(e).__name__))
        sys.exit(1)
