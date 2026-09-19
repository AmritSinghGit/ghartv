"""Repackage the verified RC9 APK/companion unchanged, correcting only delivery.
This builder runs before delivery, never on the owner's Mac.
"""
from pathlib import Path
import hashlib, json, re, sys, zipfile
BASE_SHA='8f85126879629717569eee799a3f5e8c11d3e8b69f19d76a5a9dde4f58eec914'
LAUNCHER_SHA='b15cec2a35ad6b3b1fc3f01750bb028f4f1cdb5d53f3db8705adb63b0363a309'

def h(data):return hashlib.sha256(data).hexdigest()
def change(text,old,new):
    if text.count(old)!=1:raise ValueError('EXPECTED_EXACTLY_ONE_PATCH_LOCATION: '+old[:80])
    return text.replace(old,new)

NEW_FUNCTIONS=r'''
def resource_preflight():
 r['phase']='RESOURCE_PREFLIGHT'
 if sys.platform!='darwin':raise Stop('MAC_ONLY_NO_ACTION')
 measured=call(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],5,False).stdout.strip()
 r['host_pressure_at_launch']={'1':'NORMAL','2':'WARNING','4':'CRITICAL'}.get(measured,'NOT_REPORTED')
 # Do not start another heavyweight VM under pressure. Reusing an already booted
 # exact Nova is allowed; this is not a declaration of good rendering performance.
 if measured!='1':
  adb=HOME/'Library/Android/sdk/platform-tools/adb'
  boot=call([adb,'-s','emulator-5580','shell','getprop','sys.boot_completed'],4,False).stdout.strip() if adb.is_file() else ''
  name=call([adb,'-s','emulator-5580','emu','avd','name'],4,False).stdout.splitlines() if boot=='1' else []
  if not (boot=='1' and name and name[0].strip()==AVD):
   raise Stop('HOST_PRESSURE_'+r['host_pressure_at_launch']+'_NO_NEW_EMULATOR: inspect the saved resource report and stop only identified idle workloads first')
  r['resource_admission']='REUSE_ALREADY_BOOTED_NOVA_ONLY'
 else:r['resource_admission']='HOST_PRESSURE_NORMAL'

def preserve_verified_signed(candidate,digest_value):
 cache=STATE/'verified-review-apks'/SOURCE;mkdir(cache)
 dest=cache/(digest_value+'.apk');safe(dest)
 if dest.exists():
  if digest(dest)!=digest_value:raise Stop('PREPARED_SIGNED_CACHE_CONFLICT_PRESERVED')
 else:
  temp=cache/('.candidate-'+str(os.getpid()))
  with open(temp,'xb') as out,open(candidate,'rb') as inp:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
  temp.chmod(0o600)
  if digest(temp)!=digest_value:raise Stop('PREPARED_SIGNED_CACHE_READBACK_FAILED')
  os.replace(temp,dest)
 write(CURRENT/'prepared-review-artifact.json',json.dumps({'source':SOURCE,'sha256':digest_value,'version_code':26,'installed':False,'certificate_verified':True},indent=2)+'\n')
 r['prepared_signed_apk']='PERSISTED_AND_HASH_VERIFIED_BEFORE_EMULATOR_SELECTION'
 r['prepared_signed_apk_path']=str(dest)
 # Preserve technical identity now, even if boot later times out.
 persist()

def attach_with_boot_evidence(transport,sdk):
 started=False
 try:return transport.attach_or_start(sdk,RUN)
 except transport.Hold as e:
  reason=str(e)
  if reason!='EMULATOR_BOOT_TIMEOUT_PROCESS_PRESERVED':raise Stop('EMULATOR_SELECTION_'+reason) from None
 # Original helper already waited 150 s. Continue observing the SAME VM for
 # another bounded 150 s: do not restart, change renderer, kill or create an AVD.
 adb=sdk/'platform-tools/adb';deadline=time.monotonic()+150;tick=0
 r['boot_wait']='EXISTING_VM_ADDITIONAL_150_SECONDS_NO_RESTART'
 while time.monotonic()<deadline:
  if tick%5==0:print('Waiting for the existing Nova VM to finish booting; no second emulator is being started…',flush=True)
  tick+=1
  try:
   name=call([adb,'-s','emulator-5580','emu','avd','name'],4,False).stdout.splitlines()
   if name and name[0].strip() not in ('',AVD):raise Stop('CANONICAL_AVD_CHANGED_DURING_BOOT_PRESERVED')
   if name and name[0].strip()==AVD and call([adb,'-s','emulator-5580','shell','getprop','sys.boot_completed'],4,False).stdout.strip()=='1':
    r['boot_wait']='EXISTING_VM_BOOT_VERIFIED_DURING_EXTENDED_WAIT'
    return adb,any(x.get('stage')=='STARTED_EXISTING_AVD' for x in json.loads((RUN/'PORT_CHECK.json').read_text()).get('observations',[]))
  except subprocess.TimeoutExpired:pass
  time.sleep(2)
 try:
  port=json.loads((RUN/'PORT_CHECK.json').read_text());port['r2_boot_stage']='BOOT_TIMEOUT_AFTER_EXTENDED_OBSERVATION_PROCESS_PRESERVED';write(RUN/'PORT_CHECK.json',json.dumps(port,indent=2)+'\n')
 except (OSError,ValueError):pass
 raise Stop('EMULATOR_BOOT_TIMEOUT_AFTER_EXTENDED_OBSERVATION_PROCESS_PRESERVED')

'''

def patched_launcher(b):
    if h(b)!=LAUNCHER_SHA:raise ValueError('ORIGINAL_LAUNCHER_HASH_MISMATCH')
    s=b.decode();s=change(s,'def review():',NEW_FUNCTIONS+'def review():')
    s=change(s,"prior=CURRENT/ASSET;meta=CURRENT/'review-artifact.json';reuse=False", """prior=CURRENT/ASSET;meta=CURRENT/'review-artifact.json';reuse=False
    prepared=CURRENT/'prepared-review-artifact.json'
    if prepared.is_file() and not prepared.is_symlink():
     try:
      pm=json.loads(prepared.read_text());ph=pm.get('sha256','')
      if pm.get('source')==SOURCE and re.fullmatch('[a-f0-9]{64}',ph):
       candidate_cache=STATE/'verified-review-apks'/SOURCE/(ph+'.apk');safe(candidate_cache)
       if candidate_cache.is_file() and digest(candidate_cache)==ph:prior=candidate_cache;meta=prepared
     except (ValueError,OSError):pass""")
    s=change(s,"h=digest(c);r['signed_apk_sha256']=h", "h=digest(c);r['signed_apk_sha256']=h\n   preserve_verified_signed(c,h)")
    s=change(s,'adb,started=transport.attach_or_start(sdk,RUN)','adb,started=attach_with_boot_evidence(transport,sdk)')
    s=change(s,'r[\'network_recovery\']=NETWORK_RECOVERY\n recovery_note();persist()',"r['network_recovery']=NETWORK_RECOVERY\n r['delivery_revision']='RC9-CONTINUATION-R2'\n r['control_launcher_sha256']=digest(SELF)\n resource_preflight()\n recovery_note();persist()")
    s=change(s,"else 'CONTINUITY_REQUIRES_ATTENTION';cleanup()", "else 'CONTINUITY_REQUIRES_ATTENTION'")
    s=change(s,"performance_snapshot('before');review()","performance_snapshot('before');review()\n  if r['status']=='REVIEW_READY':cleanup()")
    s=change(s,"RUN_ID='GHARTV-CYAN-14-'","RUN_ID='GHARTV-CYAN-14R2-'")
    s=s.replace('CYAN REVIEW 14 ·','CYAN REVIEW 14 R2 ·')
    python=s.split("<<'PY'\n",1)[1].rsplit('\nPY\n',1)[0]
    compile(python,'RC9-launcher','exec')
    return s.encode()

def build(original:Path,directory:Path,docs:Path):
    data=original.read_bytes()
    if h(data)!=BASE_SHA:raise ValueError('RC9_ORIGINAL_BUNDLE_MISMATCH')
    with zipfile.ZipFile(original) as z:
        assert z.testzip() is None
        files={n.split('/',1)[1]:z.read(n) for n in z.namelist() if not n.endswith('/')}
    for line in files['SHA256SUMS'].decode().splitlines():
        digest,name=line.split('  ',1);assert h(files[name])==digest
    protected={n:h(b) for n,b in files.items() if n.startswith('assets/')}
    files['GHARTV_SYNC_CURRENT_AND_REPORT.command']=patched_launcher(files['GHARTV_SYNC_CURRENT_AND_REPORT.command'])
    revised=h(files['GHARTV_SYNC_CURRENT_AND_REPORT.command'])
    starter=files['RUN_GHARTV_REVIEW.command'].decode()
    starter=change(starter,"command_hash='"+LAUNCHER_SHA+"'","command_hash='"+revised+"'")
    starter=change(starter,"known={'", "known={'"+LAUNCHER_SHA+"','")
    starter=change(starter,'umask 077\n','umask 077\nif [ "$(uname -s)" != Darwin ]; then echo MAC_ONLY_NO_ACTION; exit 2; fi\n')
    files['RUN_GHARTV_REVIEW.command']=starter.encode()
    for name in ('resource_transition.py','GHARTV_SUCCESSOR_HANDOFF.md','OPERON_RESOURCE_DIRECTIVE.md','GITHUB_AUDIT.md'):
        files[name]=(docs/name).read_bytes()
    files['RUN_HANDOFF_AND_RESOURCE_REVIEW.command']=b'#!/bin/bash\nset -euo pipefail\numask 077\nexport PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"\ncd "$(dirname "$0")"\nshasum -a 256 -c SHA256SUMS\nexec python3 ./resource_transition.py "$@"\n'
    validation={'schema':'ghartv.rc9-continuation-r2.v1','application_source':'c20e3ef5dc8ff5dbee52c338930a5c4ac30157e8',
      'version_code':26,'application_apk_changed':False,'companion_changed':False,'protected_asset_hashes':protected,
      'original_bundle_sha256':BASE_SHA,'original_launcher_sha256':LAUNCHER_SHA,'revised_launcher_sha256':revised,
      'mac_execution':False,'live_playback_verified':False,'production_changed':False,
      'changes':['resource admission before new VM','verified signed artifact persistence before boot','preserve precise transport Hold reason','extra bounded same-VM boot observation after original150s timeout','cleanup only after verified install','successor plus resource handoff; optional exact-ID old IdentiFlow app suspension']}
    files['DELIVERY_R2.json']=(json.dumps(validation,indent=2)+'\n').encode()
    files.pop('SHA256SUMS',None)
    files['SHA256SUMS']=''.join(h(b)+'  '+n+'\n' for n,b in sorted(files.items())).encode()
    directory.mkdir(parents=True,exist_ok=True);dest=directory/'GHARTV_RC9_CONTINUATION_R2.zip'
    with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
        for n,b in sorted(files.items()):
            info=zipfile.ZipInfo('GHARTV_RC9_CONTINUATION_R2/'+n,(2026,9,19,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100600<<16
            z.writestr(info,b)
    with zipfile.ZipFile(dest) as z:
        assert z.testzip() is None
        for line in z.read('GHARTV_RC9_CONTINUATION_R2/SHA256SUMS').decode().splitlines():
            digest,name=line.split('  ',1);assert h(z.read('GHARTV_RC9_CONTINUATION_R2/'+name))==digest
        for n,digest in protected.items():assert h(z.read('GHARTV_RC9_CONTINUATION_R2/'+n))==digest
    report={**validation,'file':dest.name,'sha256':h(dest.read_bytes()),'size':dest.stat().st_size}
    (directory/'DELIVERY.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
    return files,report
if __name__=='__main__':build(Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]))
