"""Bounded unit/regression checks. No owner paths, credentials, playback or deletion."""
import importlib.util,subprocess,tempfile,json,hashlib,os,sys
from pathlib import Path
from unittest.mock import patch
BASE=Path(__file__).absolute().parent

def load(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
NET=BASE/'emulator_network_repair.py'
if not NET.exists():NET=BASE.parent/'emulator_network_repair.py'
net=load('network',NET);runner=load('runner',BASE/'recover_and_clean.py')
K=list(net.HOSTS);host={k:'RESOLVED' for k in K};bad={k:'DNS_CHECK_TIMED_OUT' for k in K}
before={'validated':False,'checks':[{'service':k,'dns':'CHECK_TIMED_OUT','https':'NOT_CHECKED'} for k in K]}
assert net.recovery_decision(before,host,[bad,bad])=='HOST_EMULATOR_DNS_DIVERGENCE_CONFIRMED'
assert net.recovery_decision(before,host,[])=='INSUFFICIENT_DNS_EVIDENCE_NO_RESTART'
assert net.recovery_decision(before,host,[host,host])=='DNS_DIVERGENCE_NOT_CONFIRMED_NO_RESTART'
assert net.recovery_decision({'validated':True},host,[bad,bad])=='AMBIGUOUS_TIMEOUT_NO_RESTART'
assert net.recovery_decision(before,bad,[bad,bad])=='HOST_NETWORK_ALSO_FAILING_NO_RESTART'
one={**host,'jio_playback':'DNS_UNAVAILABLE'}
assert net.recovery_decision(before,host,[one,one])=='DNS_DIVERGENCE_NOT_CONFIRMED_NO_RESTART'
with patch.object(net,'call',return_value=subprocess.CompletedProcess([],1,'PING example.invalid (192.0.2.1): 56 data bytes\n100% packet loss','')):
 assert net.dns_observation('adb','emulator-5580','example.invalid')=='RESOLVED'
with patch.object(net,'call',side_effect=subprocess.TimeoutExpired([],5,output=b'PING example.invalid (192.0.2.1): 56 data bytes')):
 assert net.dns_observation('adb','emulator-5580','example.invalid')=='RESOLVED'
with patch.object(net,'call',side_effect=subprocess.TimeoutExpired([],5)):
 assert net.dns_observation('adb','emulator-5580','example.invalid')=='DNS_CHECK_TIMED_OUT'
with patch.object(net,'call',return_value=subprocess.CompletedProcess([],127,'','ping: not found')):
 assert net.dns_observation('adb','emulator-5580','example.invalid')=='DNS_OBSERVATION_UNAVAILABLE'
# Real fixture files, including unknown and symlink negatives. Only these are deleted.
with tempfile.TemporaryDirectory() as td:
 home=Path(td).resolve();runner.HOME=home
 for name in ('Downloads','Desktop','history'): (home/name).mkdir()
 old=home/'history';kit=home/'Downloads/OLD_REVIEW';kit.mkdir();(kit/'a.txt').write_text('exact')
 sha=runner.digest(kit/'a.txt');expected={'a.txt':sha}
 assert runner.eligible_tree(kit,expected)
 (kit/'unknown.txt').write_text('owner work');assert not runner.eligible_tree(kit,expected);(kit/'unknown.txt').unlink()
 (kit/'symlink').symlink_to(old);assert not runner.eligible_tree(kit,expected);(kit/'symlink').unlink()
 archive=home/'Downloads/OLD_REVIEW.zip';
 with __import__('zipfile').ZipFile(archive,'w') as z:z.writestr('OLD_REVIEW/a.txt','exact')
 evidence=old/'receipt.json';evidence.write_text('{"private":"retained"}')
 (home/'Downloads/receipt.json').write_bytes(evidence.read_bytes())
 (home/'Desktop/receipt.json').write_text('unrelated owner data')
 (home/'Downloads/CURRENT.zip').write_text('active release must stay')
 plan={'obsolete_kits':[{'name':'OLD_REVIEW.zip','sha256':runner.digest(archive),'directory':'OLD_REVIEW','files':expected}]}
 rows,skipped=runner.cleanup_known(plan,old,{'receipt.json':runner.digest(evidence)})
 assert len(rows)==3 and evidence.exists() and (home/'Desktop/receipt.json').read_text()=='unrelated owner data'
 assert (home/'Downloads/CURRENT.zip').exists() and not kit.exists() and not archive.exists()
 print('EXACT_KIT_CLEANUP_UNKNOWN_SYMLINK_CURRENT_AND_ORIGINAL_EVIDENCE_PROTECTION=PASS')
# AST constraints protect accidental expansion into APK replacement/system edits.
s=(BASE/'recover_and_clean.py').read_text();n=NET.read_text()
for forbidden in ('-wipe-data','pm clear','git reset','git clean','docker system prune'):
 assert forbidden not in s+n
assert "['install'" not in s and "'install','-r'" not in s
assert "'restart':'NOT_ATTEMPTED'" in n
print('TIMEOUT_DIVERGENCE_RESOLVED_WITH_BLOCKED_ICMP_AMBIGUOUS_AND_SHARED_FAILURE_GATES=PASS')
print('NO_OWNER_EXECUTION_NO_ACTUAL_EMULATOR_RECOVERY_NO_PROVIDER_PLAYBACK=CONFIRMED')
