"""Observe then conditionally restart only the existing GharTV AVD. No host DNS edits.
Reads only the app's fixed GHNET_V1 tag, never whole-system logcat or credentials.
"""
from __future__ import annotations
import datetime,ipaddress,json,os,re,socket,subprocess,time
from pathlib import Path
AVD='GharTV_Nova_Manual_google_tv_API36'; PACKAGE='in.ghartv.nova'
HOSTS={'jio_playback':'jiotvapi.media.jio.com','jio_guide':'jiotvapi.cdn.jio.com','updates':'api.github.com','collector':'ghartv-telemetry.ghartv-47d9a0.workers.dev'}
def call(args,timeout=10):return subprocess.run([str(x) for x in args],capture_output=True,text=True,timeout=timeout,stdin=subprocess.DEVNULL)
def probe(adb,serial,ident):
 call([adb,'-s',serial,'shell','am','force-stop',PACKAGE])
 p=call([adb,'-s',serial,'shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--es','ghartv_network_probe',ident],25)
 if p.returncode or 'Status: ok' not in p.stdout:return {'status':'PROBE_LAUNCH_FAILED'}
 deadline=time.monotonic()+20
 while time.monotonic()<deadline:
  time.sleep(1)
  p=call([adb,'-s',serial,'logcat','-d','-t','250','-s','GharTVNetwork:I','*:S'],8)
  for line in p.stdout.splitlines():
   if 'GHNET_V1 ' not in line:continue
   try:data=json.loads(line.split('GHNET_V1 ',1)[1])
   except ValueError:continue
   if data.get('probe_id')==ident and data.get('schema')=='ghartv.network-probe.v1':return data
 return {'status':'PROBE_RESULT_NOT_OBSERVED'}
def system_dns():
 # Use only resolvers already configured on this Mac, and only if a direct DNS query succeeds.
 try:raw=call(['/usr/sbin/scutil','--dns'],6).stdout
 except Exception:return []
 servers=[]
 for value in re.findall(r'nameserver\[[0-9]+\]\s*:\s*([^\s]+)',raw):
  try:ip=ipaddress.ip_address(value)
  except ValueError:continue
  if ip.version!=4 or ip.is_loopback or ip.is_unspecified or ip.is_link_local or ip.is_multicast or value in servers:continue
  try:p=call(['/usr/bin/dig','@'+value,HOSTS['jio_playback'],'A','+short','+time=2','+tries=1'],4)
  except Exception:continue
  if p.returncode==0 and any(re.fullmatch(r'(?:[0-9]{1,3}\.){3}[0-9]{1,3}',line) for line in p.stdout.splitlines()):servers.append(value)
  if len(servers)==2:break
 return servers
def _inspect_and_repair(sdk,serial,run_dir,run_id):
 sdk=Path(sdk);run_dir=Path(run_dir);adb=sdk/'platform-tools/adb';result={'schema':'ghartv.network-recovery.v1','restart':'NOT_NEEDED','physical_tv':'NOT_TOUCHED','playback':'NOT_VERIFIED'}
 if serial!='emulator-5580':return {**result,'status':'NONCANONICAL_EMULATOR_PRESERVED'}
 name=call([adb,'-s',serial,'emu','avd','name']).stdout.splitlines()
 if not name or name[0].strip()!=AVD:return {**result,'status':'AVD_IDENTITY_MISMATCH_PRESERVED'}
 before=probe(adb,serial,'before_'+run_id[-55:]);result['before']=before
 # DNS lookup bounded by subprocess timeout, no raw addresses printed or saved.
 host={}
 for label,name in HOSTS.items():
  try:
   p=call(['/usr/bin/python3','-c','import socket,sys;socket.getaddrinfo(sys.argv[1],443);print("RESOLVED")',name],6)
   host[label]='RESOLVED' if p.returncode==0 and p.stdout.strip()=='RESOLVED' else 'DNS_UNAVAILABLE'
  except Exception:host[label]='DNS_CHECK_TIMED_OUT'
 result['host_dns']=host
 failed=[x.get('service') for x in before.get('checks',[]) if x.get('dns')=='DNS_UNAVAILABLE']
 if len(failed)>=2 and 'jio_playback' in failed and all(host.get(x)=='RESOLVED' for x in failed):
  resolvers=system_dns()
  if not resolvers:result['restart']='DEFERRED_NO_WORKING_SYSTEM_RESOLVER';result['status']='EMULATOR_DNS_FAILURE_HOST_RESOLVES'
  else:
   binary=sdk/'emulator/emulator'
   if not binary.is_file():return {**result,'status':'EXISTING_EMULATOR_BINARY_MISSING'}
   again=call([adb,'-s',serial,'emu','avd','name']).stdout.splitlines()
   if not again or again[0].strip()!=AVD:return {**result,'status':'AVD_CHANGED_PRESERVED'}
   result['restart']='COLD_BOOT_SAME_AVD_USING_VERIFIED_SYSTEM_DNS';result['dns_source']='CURRENT_MAC_CONFIG_NO_NEW_PUBLIC_RESOLVER'
   p=call([adb,'-s',serial,'emu','kill'],15)
   if p.returncode: return {**result,'status':'GRACEFUL_STOP_NOT_CONFIRMED'}
   deadline=time.monotonic()+25
   while time.monotonic()<deadline:
    busy=False
    for port in (5580,5581):
     with socket.socket() as s:
      try:s.bind(('127.0.0.1',port))
      except OSError:busy=True
    if not busy:break
    time.sleep(1)
   else:return {**result,'status':'PORTS_STILL_BUSY_NO_DUPLICATE_START'}
   with open(run_dir/'emulator-cold-boot.log','w') as log:
    os.chmod(run_dir/'emulator-cold-boot.log',0o600)
    process=subprocess.Popen([str(binary),'-avd',AVD,'-port','5580','-no-snapshot-load','-no-snapshot-save','-dns-server',','.join(resolvers)],stdout=log,stderr=log,stdin=subprocess.DEVNULL,start_new_session=True)
   deadline=time.monotonic()+180
   while time.monotonic()<deadline:
    if process.poll() is not None:return {**result,'status':'COLD_BOOT_PROCESS_EXITED'}
    if call([adb,'-s',serial,'shell','getprop','sys.boot_completed'],5).stdout.strip()=='1':break
    time.sleep(2)
   else:return {**result,'status':'COLD_BOOT_TIMEOUT'}
   result['after']=probe(adb,serial,'after_'+run_id[-55:])
   jio=next((x for x in result['after'].get('checks',[]) if x.get('service')=='jio_playback'),{})
   result['status']='DNS_AND_JIO_HTTPS_RECOVERED' if jio.get('dns')=='RESOLVED' and jio.get('https')=='REACHABLE' else 'NETWORK_FAILURE_REMAINS'
 else:
  jio=next((x for x in before.get('checks',[]) if x.get('service')=='jio_playback'),{})
  result['status']='JIO_HTTPS_REACHABLE' if jio.get('https')=='REACHABLE' else 'NETWORK_NOT_CONFIRMED_NO_BLIND_RESTART'
 # Re-observe foreground after the probe/restart, not just before it.
 out=call([adb,'-s',serial,'shell','dumpsys','activity','activities'],8).stdout
 result['foreground_after_check']=any(PACKAGE+'/' in x and ('topResumedActivity' in x or 'mResumedActivity' in x) for x in out.splitlines())
 p=run_dir/'NETWORK_CHECK.json';p.write_text(json.dumps(result,indent=2)+'\n');p.chmod(0o600)
 return result

def inspect_and_repair(sdk,serial,run_dir,run_id):
 try:result=_inspect_and_repair(sdk,serial,run_dir,run_id)
 except Exception as error:result={'schema':'ghartv.network-recovery.v1','status':'OBSERVATION_FAILED_'+type(error).__name__,'restart':'NOT_CONFIRMED','physical_tv':'NOT_TOUCHED','playback':'NOT_VERIFIED'}
 if serial=='emulator-5580':
  try:
   out=call([Path(sdk)/'platform-tools/adb','-s',serial,'shell','dumpsys','activity','activities'],8).stdout
   result['foreground_after_check']=any(PACKAGE+'/' in x and ('topResumedActivity' in x or 'mResumedActivity' in x) for x in out.splitlines())
  except Exception:result['foreground_after_check']=False
 p=Path(run_dir)/'NETWORK_CHECK.json';p.write_text(json.dumps(result,indent=2)+'\n');p.chmod(0o600)
 return result
