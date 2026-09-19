"""One-shot private runtime audit; optional exact old-IdentiFlow app suspension.
Not a daemon, not a replacement Operon console. No deletes, prune, build or deploy.
"""
from __future__ import annotations
import argparse, datetime as dt, fcntl, hashlib, html, json, os, re, shlex, shutil
import subprocess, sys, tempfile, time, zipfile
from pathlib import Path

REVISION='operon-resource-transition-20260919-r1'
HOME=Path.home()
GHSTATE=HOME/'Library/Application Support/GharTV/owner-review'
BASE=HOME/'.local/state/operon-terminal-runs/ghartv'
CONTEXT='desktop-linux'
KEEP='identiflow_dev_41fb0d3fed'
OLD={'identiflow_dev_4e7ee30787','identiflow_dev_99fae6a23d'}
ROLES={'frontend','backend'}
class Hold(RuntimeError): pass

def now():return dt.datetime.now(dt.timezone.utc).isoformat()
def sha(v):return hashlib.sha256(v if isinstance(v,bytes) else json.dumps(v,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def safe(p):
    p=Path(p).absolute()
    for q in (p,*p.parents):
        if q.is_symlink():raise Hold('SYMLINK_PRESERVED')
    if p.exists() and p.stat().st_uid!=os.getuid():raise Hold('OTHER_OWNER_PRESERVED')
    return p

def save(p,data,mode=0o600):
    p=safe(p);safe(p.parent).mkdir(parents=True,exist_ok=True,mode=0o700)
    b=data if isinstance(data,bytes) else data.encode() if isinstance(data,str) else (json.dumps(data,indent=2)+'\n').encode()
    fd,t=tempfile.mkstemp(prefix='.'+p.name+'.',dir=p.parent)
    try:
        with os.fdopen(fd,'wb') as f:os.fchmod(f.fileno(),mode);f.write(b);f.flush();os.fsync(f.fileno())
        os.replace(t,p)
    finally:
        if os.path.exists(t):os.unlink(t)

def run(args,timeout=20):
    try:
        p=subprocess.run([str(a) for a in args],stdin=subprocess.DEVNULL,capture_output=True,text=True,
            timeout=timeout,env={**os.environ,'LC_ALL':'C','GIT_TERMINAL_PROMPT':'0'})
        return {'code':p.returncode,'out':p.stdout[:3000000],'error':None if not p.returncode else 'COMMAND_FAILED'}
    except subprocess.TimeoutExpired:return {'code':None,'out':'','error':'TIMEOUT_REQUEST_OUTCOME_MAY_BE_PENDING'}
    except OSError:return {'code':None,'out':'','error':'COMMAND_UNAVAILABLE'}

def require(args,timeout=20):
    r=run(args,timeout)
    if r['code']!=0:raise Hold(r['error'])
    return r['out']

def docker(*a,timeout=20):return require(['docker','--context',CONTEXT,*a],timeout)
def engine():
    ctx=json.loads(require(['docker','context','inspect',CONTEXT]))[0]
    host=ctx.get('Endpoints',{}).get('docker',{}).get('Host','')
    if not host.startswith('unix://'):raise Hold('NONLOCAL_DOCKER_CONTEXT_REFUSED')
    p=Path(host[7:]).resolve()
    if not p.is_relative_to(HOME.resolve()):raise Hold('DOCKER_SOCKET_NOT_UNDER_THIS_HOME')
    info=json.loads(docker('info','--format','{"id":{{json .ID}},"os":{{json .OSType}},"system":{{json .OperatingSystem}}}'))
    if info['os']!='linux' or 'Docker Desktop' not in info['system'] or not info['id']:raise Hold('NOT_VERIFIED_DOCKER_DESKTOP')
    return {'context':CONTEXT,'endpoint':host,'engine_id':info['id']}

# Deliberately excludes Config.Env, commands, complete labels and logs.
FMT='''{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Image}},"created":{{json .Created}},"running":{{json .State.Running}},"started":{{json .State.StartedAt}},"oom":{{json .State.OOMKilled}},"project":{{json (index .Config.Labels "com.docker.compose.project")}},"service":{{json (index .Config.Labels "com.docker.compose.service")}},"workdir":{{json (index .Config.Labels "com.docker.compose.project.working_dir")}},"config_files":{{json (index .Config.Labels "com.docker.compose.project.config_files")}},"config_hash":{{json (index .Config.Labels "com.docker.compose.config-hash")}},"depends_on":{{json (index .Config.Labels "com.docker.compose.depends_on")}},"memory_limit":{{json .HostConfig.Memory}},"nano_cpus":{{json .HostConfig.NanoCpus}},"restart":{{json .HostConfig.RestartPolicy}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}'''
def inspect(ids):
    if not ids:return []
    if len(ids)>400 or any(not re.fullmatch('[a-f0-9]{64}',i) for i in ids):raise Hold('INVALID_OR_EXCESSIVE_CONTAINER_IDS')
    text=docker('inspect','--type','container','--format',FMT,*ids)
    rows=[]
    for line in text.splitlines():
        r=json.loads(line);r['name']=r['name'].lstrip('/')
        # Only names/IDs of networks, no IP addresses, aliases or endpoints in report.
        r['networks']={k:v.get('NetworkID') for k,v in (r['networks'] or {}).items()}
        r['mounts']=[{k:m.get(k) for k in ('Type','Name','Source','Destination','RW')} for m in r['mounts']]
        rows.append(r)
    if set(r['id'] for r in rows)!=set(ids):raise Hold('INCOMPLETE_CONTAINER_INSPECTION')
    return rows

def census():
    identity=engine();ids=docker('ps','-aq','--no-trunc').split()
    rows=inspect(ids);stats=run(['docker','--context',CONTEXT,'stats','--no-stream','--format','{{json .}}'],30)
    measurements=[]
    if stats['code']==0:
        for line in stats['out'].splitlines():
            try:measurements.append(json.loads(line))
            except ValueError:pass
    return {'at':now(),'engine':identity,'containers':rows,'stats':measurements,
        'stats_status':'OBSERVED' if stats['code']==0 else stats['error'],
        'disk_usage':run(['docker','--context',CONTEXT,'system','df'],30),
        'compose_projects':run(['docker','--context',CONTEXT,'compose','ls','--all','--format','json'],20)}

def app_identity(row):
    return {k:row.get(k) for k in ('id','name','created','image','project','service','config_hash','mounts','networks','restart')}
def eligible(row):return row.get('project') in OLD and row.get('service') in ROLES

def proposal(snapshot):
    rows=snapshot['containers']
    keep={r.get('service') for r in rows if r.get('project')==KEEP and r['running']}
    if not {'frontend','backend','postgres'}.issubset(keep):return [],'CURRENT_IDENTIFLOW_STACK_NOT_VERIFIED_RUNNING'
    candidates=[r for r in rows if eligible(r) and r['running'] and r.get('config_hash')]
    # Stop only complete old frontend/backend pairs; never databases, workers or Supabase.
    valid=[]
    for project in sorted(OLD):
        pair=[r for r in candidates if r['project']==project]
        if len(pair)!=2 or {r['service'] for r in pair}!=ROLES:continue
        if any(r['running'] and r.get('project')==project and r.get('service') not in ROLES|{'postgres'} for r in rows):continue
        nets=set().union(*(set(r['networks']) for r in pair))
        foreign=[r for r in rows if r['running'] and r.get('project')!=project and nets.intersection(r['networks'])]
        if foreign:continue
        valid+=sorted(pair,key=lambda r:r['service']!='frontend')
    return valid,'OWNER_IDLE_AND_DEPENDENCY_CONFIRMATION_REQUIRED' if valid else 'NO_VERIFIED_OLD_APP_PAIRS'

def suspend(snapshot,targets,folder,confirmation):
    if confirmation!='STOP OLD IDENTIFLOW APPS':raise Hold('NO_EXPLICIT_STOP_CONFIRMATION')
    # Not inferred from age, CPU=0 or tmp suffix; user affirms tasks/dependencies finished.
    if engine()!=snapshot['engine']:raise Hold('DOCKER_ENGINE_CHANGED')
    current=census();available,reason=proposal(current)
    if {r['id'] for r in targets}-{r['id'] for r in available}:raise Hold('LIVE_PROPOSAL_CHANGED')
    fresh={r['id']:r for r in current['containers']}
    for old in targets:
        r=fresh.get(old['id'])
        if not r or app_identity(r)!=app_identity(old) or r['started']!=old['started']:raise Hold('CONTAINER_CHANGED')
    receipt={'schema':'operon.reversible-app-suspension.v1','engine':snapshot['engine'],'at':now(),
        'owner_attestation':'Listed old app tasks and external dependants are idle; core/data services remain protected',
        'targets':[app_identity(r) for r in targets],'actions':[],'volumes_removed':0,'files_removed':0}
    save(folder/'RESTORE.json',receipt)
    for old in targets:
        if engine()!=snapshot['engine']:raise Hold('DOCKER_ENGINE_CHANGED_DURING_ACTION')
        observed=inspect([old['id']])[0]
        if app_identity(observed)!=app_identity(old) or (observed['running'] and observed['started']!=old['started']):
            raise Hold('TARGET_CHANGED_REMAINING_ACTIONS_HELD')
        if not observed['running']:continue
        action={'id':old['id'],'name':old['name'],'before_started':old['started'],'status':'STOP_REQUESTED'}
        receipt['actions'].append(action);save(folder/'RESTORE.json',receipt)
        # -1 means no forced SIGKILL deadline. Our CLI wait is bounded; pending is explicit.
        response=run(['docker','--context',CONTEXT,'stop','--timeout','-1',old['id']],45)
        latest=inspect([old['id']])[0]
        action['status']='STOPPED_VERIFIED' if not latest['running'] else 'STILL_RUNNING_OR_STOP_PENDING'
        action['cli_result']=response['code'];save(folder/'RESTORE.json',receipt)
        if latest['running']:break
    return receipt

def restore(path):
    p=safe(path);receipt=json.loads(p.read_text())
    if receipt.get('schema')!='operon.reversible-app-suspension.v1':raise Hold('INVALID_RESTORE_RECORD')
    if engine()!=receipt['engine']:raise Hold('DIFFERENT_DOCKER_ENGINE_NO_START')
    allowed=[r for r in receipt['targets'] if eligible(r)]
    stopped={a['id'] for a in receipt['actions'] if a['status'] in ('STOPPED_VERIFIED','STOP_REQUESTED','STILL_RUNNING_OR_STOP_PENDING')}
    changes=[]
    for expected in sorted(allowed,key=lambda r:r['service']!='backend'):
        if expected['id'] not in stopped:continue
        live=inspect([expected['id']])[0]
        if app_identity(live)!=expected:raise Hold('RESTORE_TARGET_CHANGED_NO_RECREATE')
        if not live['running']:
            docker('start',expected['id'],timeout=30)
            if not inspect([expected['id']])[0]['running']:raise Hold('RESTORE_NOT_RUNNING')
            changes.append(expected['name'])
    save(p.parent/'RESTORED.json',{'at':now(),'started':changes,'no_recreation':True})
    print('Restored exact saved app containers:',', '.join(changes) or 'already running')

def host():
    p=run(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],3)['out'].strip()
    return {'at':now(),'memory_pressure':{'1':'NORMAL','2':'WARNING','4':'CRITICAL'}.get(p,'UNKNOWN'),
        'swap':run(['/usr/sbin/sysctl','-n','vm.swapusage'],3)['out'].strip(),
        'process_snapshot':run(['/bin/ps','-axo','pid=,pcpu=,rss=,comm='],5)['out'],
        'notice':'RSS is not unique physical footprint; Docker CLI and VM memory accounting differ.'}

def git_audit(rows):
    paths={HOME/'projects/vcnow-operations',HOME/'projects/operon',HOME/'Downloads/GharTV_Nova_v0.4.2'}
    for r in rows:
        v=r.get('workdir')
        if v and str(v).startswith(str(HOME)+'/'):paths.add(Path(v))
        for v in (r.get('config_files') or '').split(','):
            if v.startswith(str(HOME)+'/'):paths.add(Path(v).parent)
    roots={}
    for p in sorted(paths):
        if not p.is_dir():continue
        x=run(['git','-C',p,'rev-parse','--show-toplevel'],5)
        if x['code']==0:roots[x['out'].strip()]=True
    results=[]
    for root in list(roots)[:40]:
        def g(*args):return run(['git','-C',root,*args],8)
        head=g('rev-parse','HEAD')['out'].strip();status=g('status','--porcelain=v1','--untracked-files=normal')
        ignored=g('ls-files','--others','--ignored','--exclude-standard','--directory')
        refs=g('for-each-ref','--format=%(refname:short) %(objectname)','refs/heads/')['out'].splitlines()
        origin=g('remote','get-url','origin')['out'].strip()
        m=re.fullmatch(r'(?:https://github\.com/|git@github\.com:)([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?',origin)
        item={'worktree':root,'head':head,'status_scan':status['error'] or 'OK','changed_or_untracked_entries':len(status['out'].splitlines()),
            'stash_count':len(g('stash','list','--format=%H')['out'].splitlines()),'ignored_scan':ignored['error'] or 'OK','ignored_entries_present':bool(ignored['out']),
            'preserved':True,'github_repository':m[1] if m else None,'github_refs_checked':False}
        # Contacts only an exact GitHub origin; does not fetch, push, prune, stash or change refs.
        if m:
            remote=g('ls-remote','--heads','origin')
            if remote['code']==0:
                rr={p[1].removeprefix('refs/heads/'):p[0] for line in remote['out'].splitlines() if len(p:=line.split())==2}
                local={p[0]:p[1] for line in refs if len(p:=line.split())==2}
                item['github_refs_checked']=True
                item['branches_not_equal_remote']=[name for name,s in local.items() if rr.get(name)!=s]
                item['head_exactly_on_remote_branch']=head in rr.values()
            else:item['remote_status']='UNAVAILABLE_NOT_PROOF_OF_UNPUSHED_WORK'
        results.append(item)
    return {'scope':'Only discovered Compose directories and named canonical roots; not a complete scan of all Mac source',
        'not_pushed_automatically':True,'worktrees':results,'private_files_uploaded':False}

def tty(prompt):
    try:
        with open('/dev/tty','r+') as f:f.write(prompt);f.flush();return f.readline().strip()
    except OSError:return ''

def mirror(folder,summary):
    vault=HOME/'Documents/Amrit Executive Memory'
    if not safe(vault).is_dir():return 'EXISTING_VAULT_NOT_FOUND'
    d=vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv';safe(d).mkdir(parents=True,exist_ok=True)
    package=Path(__file__).parent
    # Only write new handoff files; never overwrite installation receipt or existing progress note.
    for name in ('GHARTV_SUCCESSOR_HANDOFF.md','OPERON_RESOURCE_DIRECTIVE.md','GITHUB_AUDIT.md'):
        p=package/name
        if not p.is_file():continue
        dest=d/name
        if dest.exists() and dest.read_bytes()!=p.read_bytes():dest=d/(folder.name+'-'+name)
        save(dest,p.read_bytes())
        if dest.read_bytes()!=p.read_bytes():raise Hold('OBSIDIAN_READBACK_FAILED')
    note='# GharTV successor / resource observation\n\n```json\n'+json.dumps(summary,indent=2)+'\n```\nPrivate full inventory remains in the local run, not public GitHub.\n'
    save(d/(folder.name+'.md'),note)
    return 'FILES_WRITTEN_AND_READBACK_VERIFIED' if (d/(folder.name+'.md')).read_text()==note else 'NOT_VERIFIED'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--restore',type=Path);parser.add_argument('--no-prompts',action='store_true');args=parser.parse_args()
    if sys.platform!='darwin':print('MAC_ONLY_NO_ACTION');return 2
    if args.restore:restore(args.restore);return 0
    os.umask(0o077)
    rid='GHARTV-TRANSITION-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
    folder=safe(BASE/rid);folder.mkdir(parents=True,exist_ok=False,mode=0o700)
    summary={'schema':'operon.ghartv-successor-resource.v1','run_id':rid,'revision':REVISION,
        'concurrent_lanes_allowed':True,'files_deleted':0,'containers_deleted':0,'volumes_deleted':0,
        'containers_stopped':0,'apk_installed':False,'production_changed':False,'writer_lease':'NOT_ACQUIRED_BY_THIS_AUDIT',
        'github_sync':'SOURCE_PUBLISHED_EXECUTION_PRIVATE_NOT_AUTOMATICALLY_UPLOADED','mirror':'NOT_WRITTEN'}
    report={'before_host':host(),'errors':[]};targets=[]
    try:
        with safe(BASE/'resource-transition.lock').open('a') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            snapshot=census();save(folder/'DOCKER_BEFORE.json',snapshot)
            report['git']=git_audit(snapshot['containers']);save(folder/'GIT_AUDIT_PRIVATE.json',report['git'])
            targets,reason=proposal(snapshot);save(folder/'STOP_PROPOSAL.json',{'reason':reason,'targets':targets,'never_stop':'all DBs, current IdentiFlow, Supabase, JalNirnay, PI, VCNow and other lanes'})
            summary.update(containers_observed=len(snapshot['containers']),containers_running=sum(r['running'] for r in snapshot['containers']),proposed_old_app_stops=len(targets))
            print('\nFresh local Docker inventory saved. No containers stopped yet.')
            for r in targets:print('  Optional old app:',r['name'],r['id'][:12])
            confirmation=''
            if targets and not args.no_prompts:
                confirmation=tty('\nOnly the listed OLD IdentiFlow frontend/backend containers can be stopped.\nTheir databases and current project stay running. Confirm any Codex task using these old apps has finished and no other workflow needs them.\nType STOP OLD IDENTIFLOW APPS to suspend them reversibly, or press Enter for audit only: ')
            if confirmation=='STOP OLD IDENTIFLOW APPS':
                act=suspend(snapshot,targets,folder,confirmation)
                summary['containers_stopped']=sum(a['status']=='STOPPED_VERIFIED' for a in act['actions'])
                summary['restore_record']=str(folder/'RESTORE.json')
                # Stable copy permits exact-ID resume after the temporary package is removed.
                save(folder/'resource_transition.py',Path(__file__).read_bytes())
                wrapper='#!/bin/bash\nset -euo pipefail\nexport PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"\nexec python3 '+shlex.quote(str(folder/'resource_transition.py'))+' --restore '+shlex.quote(str(folder/'RESTORE.json'))+'\n'
                save(folder/'RESTORE_STOPPED_APPS.command',wrapper,0o700)
            summary['stop_decision']='EXPLICIT_OLD_APP_SUSPENSION' if confirmation=='STOP OLD IDENTIFLOW APPS' else 'AUDIT_ONLY_NOTHING_STOPPED'
            report['after_docker']=census();save(folder/'DOCKER_AFTER.json',report['after_docker'])
    except Exception as e:
        report['errors'].append(str(e) if isinstance(e,Hold) else type(e).__name__)
        summary['resource_status']='ATTENTION_REQUIRED_PARTIAL_EVIDENCE_PRESERVED'
    report['after_host']=host();save(folder/'HOST_PRIVATE.json',report)
    summary.update(memory_pressure_before=report['before_host']['memory_pressure'],memory_pressure_after=report['after_host']['memory_pressure'],
        resource_status=summary.get('resource_status','AUDIT_COMPLETE_NOT_A_PERFORMANCE_BENCHMARK'))
    # Recover actual action counts even after a mid-action failure.
    if (folder/'RESTORE.json').exists():
        act=json.loads((folder/'RESTORE.json').read_text());summary['containers_stopped']=sum(a['status']=='STOPPED_VERIFIED' for a in act['actions'])
        if not (folder/'RESTORE_STOPPED_APPS.command').exists():
            save(folder/'resource_transition.py',Path(__file__).read_bytes())
            save(folder/'RESTORE_STOPPED_APPS.command','#!/bin/bash\nexport PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"\nexec python3 '+shlex.quote(str(folder/'resource_transition.py'))+' --restore '+shlex.quote(str(folder/'RESTORE.json'))+'\n',0o700)
    try:summary['mirror']=mirror(folder,summary)
    except Exception as e:summary['mirror']='FAILED_'+type(e).__name__
    summary['evidence']=str(folder);save(folder/'HANDOFF.json',summary)
    text='GHARTV_SUCCESSOR_RESOURCE_HANDOFF\n'+json.dumps(summary,indent=2)+'\n';save(folder/'HANDOFF.txt',text)
    htmltext='<!doctype html><meta charset="utf-8"><title>Operon runtime handoff</title><style>body{font:16px system-ui;max-width:1000px;margin:32px auto;padding:20px}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style><h1>Concurrent lanes, bounded runtimes</h1><p>No database/volume, image, branch or file was deleted. Current source and private data remain local.</p><h2>Actual outcome</h2><pre>'+html.escape(text)+'</pre><h2>Docker observations</h2><pre>'+html.escape(json.dumps(report.get('after_docker',{}).get('stats',[]),indent=2))+'</pre>'
    save(folder/'REPORT.html',htmltext)
    archive=folder/'RESOURCE_AUDIT_PRIVATE.zip'
    members=[p for p in folder.iterdir() if p.is_file() and p.suffix in ('.json','.txt','.html')]
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in members:z.write(p,p.name)
    os.chmod(archive,0o600)
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        for p in members:assert sha(z.read(p.name))==sha(p.read_bytes())
    print(text);print('Private report:',archive)
    run(['/usr/bin/open',folder/'REPORT.html']);run(['/usr/bin/open','-R',archive])
    if not args.no_prompts:
        tty('\nPress Enter to copy the safe handoff (private inventory is not copied): ')
        subprocess.run(['/usr/bin/pbcopy'],input=text,text=True,timeout=3,check=False)
        tty('Press Enter to finish: ')
    return 1 if report['errors'] else 0

if __name__=='__main__':sys.exit(main())
