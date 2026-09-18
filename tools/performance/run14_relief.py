"""One-shot RC9/run14 evidence capture and OPTIONAL exact-Nova resource relief.
No installer, rebuild, VM startup, disk cleanup, global ADB restart or cloud upload.
"""
from __future__ import annotations
import datetime as dt
import fcntl
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import zipfile

AVD='GharTV_Nova_Manual_google_tv_API36'
SERIAL='emulator-5580'
PACKAGE='in.ghartv.nova'
SOURCE='c20e3ef5dc8ff5dbee52c338930a5c4ac30157e8'
OWNER_RUN='GHARTV-CYAN-14-20260918T154238Z-97081'
HOME=Path.home()
STATE=HOME/'Library/Application Support/GharTV/owner-review'
SDK=HOME/'Library/Android/sdk'
REVISION='run14-relief-1.0.0'

class Hold(RuntimeError): pass

def safe(path, max_bytes=None):
    p=Path(path).absolute()
    for q in (p,*p.parents):
        if q.is_symlink(): raise Hold('SYMLINK_PRESERVED')
    if p.exists():
        st=p.stat()
        if st.st_uid!=os.getuid(): raise Hold('OTHER_OWNER_FILE_PRESERVED')
        if max_bytes is not None and (not p.is_file() or st.st_size>max_bytes):
            raise Hold('UNEXPECTED_FILE_PRESERVED')
    return p

def write(path, data):
    p=safe(path);safe(p.parent).mkdir(parents=True,exist_ok=True,mode=0o700)
    raw=data if isinstance(data,bytes) else data.encode() if isinstance(data,str) else (json.dumps(data,indent=2)+'\n').encode()
    fd,t=tempfile.mkstemp(prefix='.'+p.name,dir=p.parent)
    try:
        with os.fdopen(fd,'wb') as f:
            os.fchmod(f.fileno(),0o600);f.write(raw);f.flush();os.fsync(f.fileno())
        os.replace(t,p)
    finally:
        if os.path.exists(t):os.unlink(t)

def call(args, timeout=4):
    try:
        p=subprocess.run(list(map(str,args)),stdin=subprocess.DEVNULL,capture_output=True,text=True,
                         timeout=timeout,check=False,env={**os.environ,'LC_ALL':'C'})
        return {'exit':p.returncode,'out':p.stdout,'error':'COMMAND_FAILED' if p.returncode else None}
    except subprocess.TimeoutExpired:return {'exit':None,'out':'','error':'COMMAND_TIMED_OUT'}
    except OSError:return {'exit':None,'out':'','error':'COMMAND_UNAVAILABLE'}

def command_text(args,timeout=4):
    r=call(args,timeout);return r['out'].strip() if r['exit']==0 else None

def table_rows(text):
    rows=[]
    for line in (text or '').splitlines():
        p=line.strip().split(None,3)
        try:
            if len(p)==4:rows.append({'pid':int(p[0]),'cpu_pct':float(p[1]),'rss_mib':round(int(p[2])/1024,1),'process':Path(p[3]).name[:100]})
        except ValueError:pass
    return rows

def host_snapshot():
    pressure=command_text(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'])
    d={'at_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'pressure_raw':pressure,
       'memory_pressure':{'1':'NORMAL','2':'WARNING','4':'CRITICAL'}.get(pressure,'NOT_REPORTED'),
       'swap':command_text(['/usr/sbin/sysctl','-n','vm.swapusage']),
       'physical_memory_bytes':command_text(['/usr/sbin/sysctl','-n','hw.memsize']),
       'vm_stat':command_text(['/usr/bin/vm_stat']),
       'disk_free_gib':round(shutil.disk_usage(HOME).free/1024**3,2)}
    rows=table_rows(command_text(['/bin/ps','-axo','pid=,pcpu=,rss=,comm=']))
    d['top_memory']=sorted(rows,key=lambda r:r['rss_mib'],reverse=True)[:12]
    d['top_cpu']=sorted(rows,key=lambda r:r['cpu_pct'],reverse=True)[:12]
    d['emulator_processes']=[r for r in rows if 'qemu' in r['process'].lower() or r['process'].lower()=='emulator']
    d['notice']='RSS is resident memory, not unique physical footprint. ps CPU is a sampled estimate; >100% can span cores. No single-cause diagnosis.'
    return d

def parse_processes(text,sdk=SDK):
    """Accept only owner QEMU executable inside THIS SDK, exact AVD and port."""
    rows=[]
    for line in text.splitlines():
        p=line.strip().split(None,2)
        if len(p)!=3 or not p[0].isdigit() or p[1]!=str(os.getuid()):continue
        try:a=shlex.split(p[2])
        except ValueError:continue
        if not a:continue
        executable=Path(a[0])
        try:relative=executable.relative_to(sdk/'emulator/qemu')
        except ValueError:continue
        if '..' in relative.parts or len(relative.parts)!=2 or not relative.name.startswith('qemu-system-'):continue
        def value(flag):
            return a[a.index(flag)+1] if a.count(flag)==1 and a.index(flag)+1<len(a) else None
        if value('-avd')!=AVD and not (a.count('@'+AVD)==1 and '-avd' not in a):continue
        if value('-port')!='5580':continue
        rows.append({'pid':int(p[0]),'command':p[2],'exe':str(executable)})
    return rows

def nova_processes():
    r=call(['/bin/ps','-axo','pid=,uid=,command='])
    if r['exit']!=0:raise Hold('PROCESS_INVENTORY_UNAVAILABLE_NO_STOP')
    return parse_processes(r['out'])

def one_process(pid):
    r=call(['/bin/ps','-p',str(pid),'-o','pid=,uid=,command='])
    if r['exit']==0:return parse_processes(r['out'])
    if r['exit']==1 and not r['out'].strip():return []
    raise Hold('PID_RECHECK_UNAVAILABLE_STOP_OUTCOME_UNKNOWN')

def stop_exact_nova(outcome):
    rows=nova_processes()
    if not rows:return 'NO_EXACT_NOVA_PROCESS_IDENTIFIED_NOTHING_STOPPED'
    if len(rows)!=1:return 'AMBIGUOUS_NOVA_PROCESSES_PRESERVED'
    row=rows[0];pid=row['pid']
    initial=command_text(['/bin/ps','-p',str(pid),'-o','lstart='])
    if not initial:return 'PROCESS_START_IDENTITY_UNAVAILABLE_NO_STOP'
    if one_process(pid)!=[row] or command_text(['/bin/ps','-p',str(pid),'-o','lstart='])!=initial:
        return 'PROCESS_CHANGED_NO_STOP'
    # Signal only this owner-owned, exact QEMU PID. No group signals or force kill.
    try:os.kill(pid,signal.SIGTERM)
    except ProcessLookupError:return 'NOVA_ALREADY_EXITED'
    except PermissionError:return 'NOVA_STOP_PERMISSION_DENIED'
    outcome['termination_request']='SIGTERM_TO_EXACT_NOVA_PID';outcome['nova_pid']=pid
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        current=one_process(pid)
        if current!=[row]:return 'NOVA_STOPPED_AND_LEFT_OFF'
        started=command_text(['/bin/ps','-p',str(pid),'-o','lstart='])
        if started is None:return 'STOP_REQUEST_SENT_OUTCOME_UNVERIFIED'
        if started!=initial:return 'ORIGINAL_NOVA_EXITED_REUSED_PID_PRESERVED'
        time.sleep(.5)
    return 'NOVA_DID_NOT_EXIT_NO_FORCE_KILL'

def saved_evidence(folder, report):
    source=safe(STATE/'runs'/OWNER_RUN)
    report['saved_files']={}
    for name in ('PORT_CHECK.json','PERFORMANCE_BEFORE.json','PERFORMANCE_AFTER.json','receipt.json'):
        p=source/name
        try:
            raw=safe(p,2*1024*1024).read_bytes();data=json.loads(raw)
            if not isinstance(data,dict):raise ValueError('JSON_OBJECT_REQUIRED')
            write(folder/('run14-'+name),raw)
            report['saved_files'][name]='COPIED_AND_HASH_VERIFIED' if hashlib.sha256((folder/('run14-'+name)).read_bytes()).digest()==hashlib.sha256(raw).digest() else 'NOT_VERIFIED'
            if name=='PORT_CHECK.json':report['run14_port_check']=data
            if name=='receipt.json':report['run14_phase']=data.get('phase');report['run14_blocker']=data.get('blocker')
        except FileNotFoundError:report['saved_files'][name]='NOT_PRESENT'
        except (Hold,OSError,ValueError):report['saved_files'][name]='NOT_READ_PRESERVED'
    p=source/'emulator-start.private.log'
    try:
        safe(p);st=p.stat()
        if p.is_file() and st.st_uid==os.getuid():
            with p.open('rb') as f:f.seek(max(0,st.st_size-131072));raw=f.read(131072)
            write(folder/'run14-emulator-start.private.log',raw)
            report['emulator_log']='LAST_128_KIB_SAVED_PRIVATELY'
    except OSError:report['emulator_log']='NOT_AVAILABLE'

def android_observation():
    adb=SDK/'platform-tools/adb';d={'serial':SERIAL,'device_state':'NOT_READ','installed_version_code':None,'boot_completed':None}
    if not adb.is_file():return d
    d['device_state']=command_text([adb,'-s',SERIAL,'get-state'],3)
    name=command_text([adb,'-s',SERIAL,'emu','avd','name'],4)
    d['avd_verified']=bool(name and name.splitlines()[0].strip()==AVD)
    if not d['avd_verified']:return d
    d['boot_completed']=command_text([adb,'-s',SERIAL,'shell','getprop','sys.boot_completed'],4)
    package=command_text([adb,'-s',SERIAL,'shell','dumpsys','package',PACKAGE],5) or ''
    m=re.search(r'versionCode=(\d+)',package);d['installed_version_code']=int(m[1]) if m else None
    return d

def report_html(report):
    esc=lambda x:html.escape(str(x))
    parts=['<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'">',
           '<title>GharTV Mac / boot observation</title><style>body{font:16px system-ui;max-width:960px;margin:32px auto;padding:0 20px;background:#0b1821;color:#e8f1f5}h1{font-size:30px}section{padding:18px;border:1px solid #46616d;border-radius:12px;margin:20px 0}table{border-collapse:collapse;width:100%}td,th{text-align:left;border-bottom:1px solid #46616d;padding:8px}pre{white-space:pre-wrap;overflow-wrap:anywhere}.muted{color:#b0c4ce}</style>',
           '<h1>Mac load and Nova startup</h1><p>Private local observations. No application files, system settings, other lanes or public release changed.</p>',
           '<section><b>Nova:</b> '+esc(report.get('relief','NOT_RUN'))+'<p>Memory pressure before: '+esc(report.get('before',{}).get('memory_pressure'))+' · after: '+esc(report.get('after',{}).get('memory_pressure'))+'</p><p>No automatic restart. This report is not a successful RC9 install or playback test.</p></section>']
    for field,title in [('top_memory','Largest resident-memory processes after observation'),('top_cpu','Highest ps-reported CPU after observation')]:
        parts.append('<section><h2>'+title+'</h2><table><tr><th>Process</th><th>PID</th><th>RSS MiB</th><th>CPU %</th></tr>')
        for r in report.get('after',report.get('before',{})).get(field,[]):
            parts.append('<tr>'+''.join('<td>'+esc(r[k])+'</td>' for k in ('process','pid','rss_mib','cpu_pct'))+'</tr>')
        parts.append('</table><p class="muted">RSS is not unique physical footprint. Save work and quit only applications you recognise and no longer need. Other lanes were not stopped.</p></section>')
    parts.append('<section><h2>Recorded startup state</h2><pre>'+esc(json.dumps({'run14_port_check':report.get('run14_port_check'),'android_now':report.get('android')},indent=2))+'</pre></section>')
    return ''.join(parts)

def main():
    if sys.platform!='darwin':print('MAC_ONLY_NO_ACTION');return 1
    mode=sys.argv[1] if len(sys.argv)>1 else 'inspect'
    if mode not in ('inspect','relieve'):print('Use inspect or relieve');return 1
    os.umask(0o077)
    run_id='GHARTV-RUN14-RELIEF-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid())
    folder=safe(STATE/'runs'/run_id);folder.mkdir(parents=True,exist_ok=False,mode=0o700)
    report={'schema':'ghartv.run14-relief.v1','revision':REVISION,'run_id':run_id,'related_owner_run':OWNER_RUN,'source':SOURCE,'mode':mode,'public_release_changed':False,'apk_installed':False,'files_deleted':0,'bytes_deleted':0,'other_processes_stopped':False,'emulator_restarted':False,'raw_logs_uploaded':False}
    print('\033[36mGharTV · preserve evidence and relieve only the exact Nova VM\033[0m',flush=True)
    try:
        with safe(STATE/'owner-run.lock').open('a') as lock:
            try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:raise Hold('ANOTHER_GHARTV_ACTION_RUNNING_NOTHING_STOPPED') from None
            print('1 / 3 · Reading saved run14 evidence and measuring current Mac load.',flush=True)
            saved_evidence(folder,report);report['before']=host_snapshot();report['android']=android_observation()
            write(folder/'OBSERVATION_BEFORE.json',report)
            print('2 / 3 · Requesting exit of only verified Nova5580; no force-kill or automatic restart.' if mode=='relieve' else '2 / 3 · Inspect only; no process stopped.',flush=True)
            if mode=='relieve':
                receipt=json.loads(safe(STATE/'current/receipt.json',256000).read_text())
                if receipt.get('run_id')!=OWNER_RUN or receipt.get('review_source')!=SOURCE:
                    raise Hold('CURRENT_REVIEW_CHANGED_CAPTURE_ONLY_NO_STOP')
                report['relief']=stop_exact_nova(report)
            else:report['relief']='INSPECT_ONLY_NO_STOP'
            time.sleep(2);report['after']=host_snapshot()
    except Exception as e:
        report['error']=str(e) if isinstance(e,Hold) else type(e).__name__
        report.setdefault('relief','ACTION_REQUIRED_NO_UNVERIFIED_PROCESS_STOP')
    print('3 / 3 · Saving private report, startup details and actual outcome.',flush=True)
    write(folder/'REPORT.json',report);write(folder/'REPORT.html',report_html(report))
    summary={k:report[k] for k in ('schema','revision','run_id','related_owner_run','source','mode','relief','apk_installed','public_release_changed','files_deleted','bytes_deleted','other_processes_stopped','emulator_restarted')}
    summary.update(memory_pressure_before=report.get('before',{}).get('memory_pressure','NOT_MEASURED'),memory_pressure_after=report.get('after',{}).get('memory_pressure','NOT_MEASURED'),port_stage=report.get('run14_port_check',{}).get('stage','NOT_AVAILABLE'),installed_code_observed=report.get('android',{}).get('installed_version_code'),result='EVIDENCE_SAVED_NOT_INSTALL_OR_PLAYBACK_PROOF',private_report=str(folder/'GHARTV_RUN14_DIAGNOSTICS_PRIVATE.zip'))
    if 'error' in report:summary['error']=report['error']
    note=HOME/'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
    try:
        if safe(note).is_dir():
            text='# GharTV run14 Mac/boot observation\n\n```json\n'+json.dumps(summary,indent=2)+'\n```\nExisting TV experience contract and public release are unchanged. No claim of RC9 installation.\n'
            write(note/(run_id+'.md'),text)
            summary['obsidian']='RUN_NOTE_READBACK_VERIFIED' if (note/(run_id+'.md')).read_text()==text else 'NOT_VERIFIED'
    except (OSError,Hold):summary['obsidian']='NOT_WRITTEN_EXISTING_NOTE_PRESERVED'
    handoff='GHARTV_RUN14_RELIEF_HANDOFF\n'+json.dumps(summary,indent=2)+'\n'
    write(folder/'handoff.txt',handoff);write(STATE/'current/run14-relief.json',summary)
    archive=folder/'GHARTV_RUN14_DIAGNOSTICS_PRIVATE.zip'
    members=[p for p in folder.iterdir() if p.is_file() and p!=archive]
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in members:z.write(p,p.name)
    os.chmod(archive,0o600)
    with zipfile.ZipFile(archive) as z:
        if z.testzip() is not None:raise Hold('ARCHIVE_VERIFICATION_FAILED')
        for p in members:
            if hashlib.sha256(z.read(p.name)).digest()!=hashlib.sha256(p.read_bytes()).digest():raise Hold('ARCHIVE_READBACK_FAILED')
    print(handoff,flush=True)
    print('Largest processes by resident memory (private local observation):',flush=True)
    for r in report.get('after',report.get('before',{})).get('top_memory',[])[:8]:
        print(f"{r['process']:<34} RSS {r['rss_mib']:>8.1f} MiB   CPU {r['cpu_pct']:>6.1f}%",flush=True)
    call(['/usr/bin/open',folder/'REPORT.html']);call(['/usr/bin/open','-a','Activity Monitor']);call(['/usr/bin/open','-R',archive])
    try:
        with open('/dev/tty','r+') as tty:
            tty.write('\nPress Enter to copy the handoff (not private logs): ');tty.flush();tty.readline()
            subprocess.run(['/usr/bin/pbcopy'],input=handoff,text=True,timeout=3,check=False)
            tty.write('Press Enter to finish; Nova is not restarted: ');tty.flush();tty.readline()
    except (EOFError,OSError,subprocess.TimeoutExpired):pass
    return 0 if 'error' not in report else 1

if __name__=='__main__':sys.exit(main())
