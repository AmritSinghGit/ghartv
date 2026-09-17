"""Bounded local Mac/app observations. No process kills, network changes or credentials."""
import argparse, datetime, json, os, pathlib, platform, re, shutil, subprocess, time, tempfile

def run(argv, timeout=4):
    try:
        p=subprocess.run(argv,capture_output=True,text=True,timeout=timeout,check=False)
        return p.stdout.strip() if p.returncode==0 else None
    except (OSError,subprocess.TimeoutExpired): return None

def processes(text):
    rows=[]
    for line in (text or '').splitlines():
        pieces=line.strip().split(None,3)
        if len(pieces)!=4:continue
        try:rows.append({'pid':int(pieces[0]),'cpu_pct':float(pieces[1]),'rss_mib':round(int(pieces[2])/1024,1),'process':pathlib.Path(pieces[3]).name[:100]})
        except ValueError:continue
    return sorted(rows,key=lambda r:r['cpu_pct'],reverse=True)

def capture():
    d={'schema':'ghartv.host-performance.v1','at_ist':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=5,minutes=30))).isoformat(),'host_os':platform.system(),'no_processes_stopped':True,'no_system_settings_changed':True}
    if platform.system()!='Darwin':return {**d,'status':'MAC_OBSERVATION_NOT_AVAILABLE_ON_THIS_HOST'}
    pressure=run(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'])
    d['memory_pressure']= {'1':'NORMAL','2':'WARNING','4':'CRITICAL'}.get(pressure,'NOT_REPORTED')
    for name,key in [('physical_memory_bytes','hw.memsize'),('logical_cpus','hw.logicalcpu')]:
        val=run(['/usr/sbin/sysctl','-n',key]);d[name]=int(val) if val and val.isdigit() else None
    d['swap']=run(['/usr/sbin/sysctl','-n','vm.swapusage']);d['vm_stat_before']=run(['/usr/bin/vm_stat'])
    d['samples']=[]
    for _ in range(3):
        rows=processes(run(['/bin/ps','-axo','pid=,pcpu=,rss=,comm=']))
        d['samples'].append({'top_cpu':rows[:12],'top_memory':sorted(rows,key=lambda x:x['rss_mib'],reverse=True)[:12],
            'emulators':[x for x in rows if 'qemu' in x['process'].lower() or x['process'].lower()=='emulator']})
        time.sleep(1)
    d['vm_stat_after']=run(['/usr/bin/vm_stat']);d['thermal_report']=run(['/usr/bin/pmset','-g','therm'])
    disk=shutil.disk_usage(pathlib.Path.home());d['disk_free_gib']=round(disk.free/1024**3,2)
    avd=pathlib.Path.home()/'.android/avd/GharTV_Nova_Manual_google_tv_API36.avd/config.ini'
    allowed={'hw.cpu.arch','hw.cpu.ncore','hw.ramSize','hw.gpu.enabled','hw.gpu.mode','image.sysdir.1'}
    if avd.is_file() and not avd.is_symlink():
        d['avd_configuration']={k:v for line in avd.read_text().splitlines() if '=' in line for k,v in [line.split('=',1)] if k in allowed}
    adb=pathlib.Path.home()/'Library/Android/sdk/platform-tools/adb'
    if adb.is_file() and 'GharTV_Nova_Manual_google_tv_API36' in (run([str(adb),'-s','emulator-5580','emu','avd','name']) or ''):
        prefix=[str(adb),'-s','emulator-5580','shell']
        package=run(prefix+['dumpsys','package','in.ghartv.nova']) or ''
        m=re.search(r'versionCode=(\d+)',package);d['installed_version_code']=int(m[1]) if m else None
        text=run(prefix+['cat','/sdcard/Android/data/in.ghartv.nova/files/performance.json'])
        try:d['app_measurements']=json.loads(text) if text else None
        except (ValueError,TypeError):d['app_measurements']=None
        stats=run(prefix+['dumpsys','gfxinfo','in.ghartv.nova'],6) or ''
        d['android_frame_summary']=[line.strip() for line in stats.splitlines() if re.search(r'Total frames|Janky frames|percentile|Missed Vsync|Slow UI',line)][:18]
        mem=run(prefix+['dumpsys','meminfo','in.ghartv.nova'],5) or ''
        d['app_memory_summary']=[line.strip() for line in mem.splitlines() if re.search(r'TOTAL PSS|TOTAL RSS|Native Heap|Dalvik Heap',line)][:6]
    d['status']='MEASURED_LOCALLY'
    d['interpretation']='HOST_MEMORY_PRESSURE_PRESENT' if d['memory_pressure'] in ('WARNING','CRITICAL') else 'MEASUREMENT_ONLY_NO_SINGLE_CAUSE_PROVEN'
    d['notice']='Process CPU can exceed 100% across cores and is ps-reported, not a whole-system instantaneous utilization. Normal memory pressure does not rule out CPU/GPU/provider delays. Missing app measures are not zero. No automatic cloud upload.'
    return d

def save(path,data):
    if any(p.is_symlink() for p in (path,*path.parents)):raise RuntimeError('SYMLINK_OUTPUT_PRESERVED')
    path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    fd,tmp=tempfile.mkstemp(prefix='.performance-',dir=path.parent)
    with os.fdopen(fd,'w') as f:f.write(json.dumps(data,indent=2)+'\n');f.flush();os.fsync(f.fileno())
    os.chmod(tmp,0o600);os.replace(tmp,path)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=pathlib.Path,required=True);args=parser.parse_args();data=capture();save(args.output,data);print(json.dumps({'status':data['status'],'memory_pressure':data.get('memory_pressure','NOT_MEASURED'),'file_saved':True}))
