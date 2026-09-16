"""Observe then conditionally restart only the existing GharTV AVD. No host DNS edits.
Reads only the app's fixed GHNET_V1 tag, never whole-system logcat or credentials.
"""
from __future__ import annotations
import datetime,ipaddress,json,os,re,socket,subprocess,time,sys
from concurrent.futures import ThreadPoolExecutor
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
def dns_observation(adb,serial,name):
 """A numeric PING header proves resolution even when ICMP replies are blocked."""
 try:
  p=call([adb,'-s',serial,'shell','ping','-c','1','-W','1',name],5)
  text=(p.stdout or '')+' '+(p.stderr or '')
 except subprocess.TimeoutExpired as e:
  text=e.stdout or ''
  if isinstance(text,bytes):text=text.decode('utf-8','replace')
  return 'RESOLVED' if re.search(r'PING[^\n]*\([0-9a-fA-F:.]+\)',text) else 'DNS_CHECK_TIMED_OUT'
 except Exception:return 'DNS_OBSERVATION_UNAVAILABLE'
 if re.search(r'PING[^\n]*\([0-9a-fA-F:.]+\)',text):return 'RESOLVED'
 if re.search(r'unknown host|bad address|name or service not known|temporary failure in name resolution',text,re.I):return 'DNS_UNAVAILABLE'
 return 'DNS_OBSERVATION_UNAVAILABLE'

def recovery_decision(before,host,rounds):
 if len(rounds)!=2:return 'INSUFFICIENT_DNS_EVIDENCE_NO_RESTART'
 failures={'DNS_UNAVAILABLE','DNS_CHECK_TIMED_OUT'}
 failed=[k for k in HOSTS if all(row.get(k) in failures for row in rounds)]
 if len(failed)<2 or 'jio_playback' not in failed:return 'DNS_DIVERGENCE_NOT_CONFIRMED_NO_RESTART'
 if not all(host.get(k)=='RESOLVED' for k in failed):return 'HOST_NETWORK_ALSO_FAILING_NO_RESTART'
 timedout=any(row.get(k)=='DNS_CHECK_TIMED_OUT' for row in rounds for k in failed)
 if timedout and before.get('validated') is not False:return 'AMBIGUOUS_TIMEOUT_NO_RESTART'
 return 'HOST_EMULATOR_DNS_DIVERGENCE_CONFIRMED'

def clock_observation(adb,serial):
 try:
  start=time.time();p=call([adb,'-s',serial,'shell','date','+%s'],4);end=time.time()
  epoch=int(p.stdout.strip());delta=round(epoch-(start+end)/2)
  return {'status':'SKEW_DETECTED' if abs(delta)>300 else 'WITHIN_FIVE_MINUTES',
          'emulator_minus_mac_seconds':delta,'measurement_seconds':round(end-start,2),'clock_changed':False}
 except Exception:return {'status':'NOT_MEASURED','clock_changed':False}

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
  if p.returncode==0 and any(re.fullmatch(r'(?:[0-9]{1,3}\.){3}[0-9]{1,3}',line) for line in p.stdout.splitlines()):
   try:
    other=call(['/usr/bin/dig','@'+value,HOSTS['updates'],'A','+short','+time=2','+tries=1'],4)
    if other.returncode==0 and any(re.fullmatch(r'(?:[0-9]{1,3}\.){3}[0-9]{1,3}',line) for line in other.stdout.splitlines()):servers.append(value)
   except Exception:pass
  if len(servers)==2:break
 return servers
def _inspect_and_repair(sdk,serial,run_dir,run_id):
 sdk=Path(sdk);run_dir=Path(run_dir);adb=sdk/'platform-tools/adb';result={'schema':'ghartv.network-recovery.v1','restart':'NOT_ATTEMPTED','physical_tv':'NOT_TOUCHED','playback':'NOT_VERIFIED'}
 if serial!='emulator-5580':return {**result,'status':'NONCANONICAL_EMULATOR_PRESERVED'}
 name=call([adb,'-s',serial,'emu','avd','name']).stdout.splitlines()
 if not name or name[0].strip()!=AVD:return {**result,'status':'AVD_IDENTITY_MISMATCH_PRESERVED'}
 before=probe(adb,serial,'before_'+run_id[-55:]);result['before']=before
 # DNS lookup bounded by subprocess timeout, no raw addresses printed or saved.
 host={}
 for label,name in HOSTS.items():
  try:
   p=call([sys.executable,'-c','import socket,sys;socket.getaddrinfo(sys.argv[1],443);print("RESOLVED")',name],6)
   host[label]='RESOLVED' if p.returncode==0 and p.stdout.strip()=='RESOLVED' else 'DNS_UNAVAILABLE'
  except Exception:host[label]='DNS_CHECK_TIMED_OUT'
 result['host_dns']=host
 # The RC7 app's CHECK_TIMED_OUT covers the whole probe, not proven DNS.
 # Corroborate with two rounds of independent DNS observations before restart.
 suspects=[x.get('service') for x in before.get('checks',[])
           if x.get('dns') in ('DNS_UNAVAILABLE','CHECK_TIMED_OUT')]
 rounds=[]
 if 'jio_playback' in suspects and len(set(suspects))>=2:
  for _ in range(2):
   with ThreadPoolExecutor(max_workers=4) as pool:
    rounds.append(dict(zip(HOSTS,pool.map(lambda name:dns_observation(adb,serial,name),HOSTS.values()))))
 result['independent_dns']=rounds
 decision=recovery_decision(before,host,rounds);result['decision']=decision
 result['clock_before']=clock_observation(adb,serial)
 if decision=='HOST_EMULATOR_DNS_DIVERGENCE_CONFIRMED':
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
   result['after']=probe(adb,serial,'after_'+run_id[-55:]);result['clock_after']=clock_observation(adb,serial)
   jio=next((x for x in result['after'].get('checks',[]) if x.get('service')=='jio_playback'),{})
   result['status']='DNS_AND_JIO_HTTPS_RECOVERED' if jio.get('dns')=='RESOLVED' and jio.get('https')=='REACHABLE' else 'NETWORK_FAILURE_REMAINS'
 else:
  jio=next((x for x in before.get('checks',[]) if x.get('service')=='jio_playback'),{})
  result['status']='JIO_HTTPS_REACHABLE' if jio.get('https')=='REACHABLE' else decision
  if result['status']=='JIO_HTTPS_REACHABLE':result['restart']='NOT_NEEDED_CONFIRMED_JIO_HTTPS'
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
