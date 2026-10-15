"""Focused mocked macOS decision checks; not a claim of native emulator recovery."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace
spec=importlib.util.spec_from_file_location('recovery',Path(__file__).with_name('emulator_network_repair.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class RecoveryTests(unittest.TestCase):
 def simulate(self,dns='RESOLVED',host_ok=True,serial='emulator-5580',name=None):
  calls=[]
  def fake(args,timeout=10):
   text=' '.join(map(str,args));calls.append(text)
   if 'avd name' in text:return SimpleNamespace(stdout=(name or m.AVD)+'\nOK\n',returncode=0)
   if 'getaddrinfo' in text:return SimpleNamespace(stdout='RESOLVED\n' if host_ok else '',returncode=0 if host_ok else 1)
   if 'dumpsys activity' in text:return SimpleNamespace(stdout='mResumedActivity: '+m.PACKAGE+'/.MainActivity',returncode=0)
   raise AssertionError('Unexpected process action '+text)
  before={'schema':'ghartv.network-probe.v1','status':'COMPLETE','validated':False,'checks':[{'service':s,'dns':dns,'https':'REACHABLE' if dns=='RESOLVED' else 'NOT_CHECKED'} for s in m.HOSTS]}
  with tempfile.TemporaryDirectory() as d,patch.object(m,'call',fake),patch.object(m,'probe',return_value=before),patch.object(m,'dns_observation',return_value=dns),patch.object(m,'system_dns',return_value=[]),patch.object(m.subprocess,'Popen',side_effect=AssertionError('no new process expected')):
   result=m.inspect_and_repair('/test/sdk',serial,Path(d),'TEST-CHECK-123456');self.assertEqual(json.loads((Path(d)/'NETWORK_CHECK.json').read_text()),result)
  return result,calls
 def test_healthy_no_restart(self):
  r,c=self.simulate();self.assertEqual(r['status'],'JIO_HTTPS_REACHABLE');self.assertFalse(any('emu kill' in v for v in c))
 def test_shared_failure_no_restart(self):
  r,c=self.simulate('DNS_UNAVAILABLE',False);self.assertEqual(r['restart'],'NOT_ATTEMPTED');self.assertFalse(any('emu kill' in v for v in c))
 def test_mismatch_without_verified_resolver_preserved(self):
  r,c=self.simulate('DNS_UNAVAILABLE');self.assertEqual(r['restart'],'DEFERRED_NO_WORKING_SYSTEM_RESOLVER');self.assertFalse(any('emu kill' in v for v in c))
 def test_other_device_no_actions(self):
  r,c=self.simulate(serial='physical-tv');self.assertEqual(c,[]);self.assertEqual(r['status'],'NONCANONICAL_EMULATOR_PRESERVED')
 def test_wrong_avd_preserved(self):
  r,c=self.simulate(name='Other_AVD');self.assertEqual(r['status'],'AVD_IDENTITY_MISMATCH_PRESERVED');self.assertFalse(any('emu kill' in v for v in c))
 def test_guarded_boot_arguments(self):
  s=Path(m.__file__).read_text();self.assertNotIn('-wipe-data',s);self.assertNotIn('8.8.8.8',s);self.assertIn("'-no-snapshot-load','-no-snapshot-save','-dns-server'",s);self.assertIn('system_dns()',s)
class ReviewRecoveryTests(unittest.TestCase):
 def shared(self):return {'status':'COMPLETE','validated':False,'checks':[{'service':'jio_playback','dns':'CHECK_TIMED_OUT'},{'service':'updates','dns':'DNS_UNAVAILABLE'}]}
 def test_healthy_review_never_restarts(self):
  before={'status':'COMPLETE','checks':[{'service':'jio_playback','dns':'RESOLVED','https':'REACHABLE'}]}
  with patch.object(m,'inspect_and_repair',side_effect=AssertionError('must not restart')):
   out=m.review_recovery('/sdk','emulator-5580','/run','id',before,lambda:False,lambda:self.fail('no mutation verification needed'))
  self.assertEqual(out['state'],'NO_SHARED_DNS_FAILURE');self.assertIs(out['network'],before)
 def test_incomplete_probe_never_becomes_success(self):
  with patch.object(m,'inspect_and_repair',side_effect=AssertionError('must not restart')):
   out=m.review_recovery('/sdk','emulator-5580','/run','id',{},lambda:True,lambda:self.fail('unverified probe'))
  self.assertEqual(out['state'],'PROBE_INCOMPLETE')
 def test_shared_failure_keeps_memory_guard(self):
  with patch.object(m,'inspect_and_repair',side_effect=AssertionError('must not restart')):
   out=m.review_recovery('/sdk','emulator-5580','/run','id',self.shared(),lambda:False,lambda:self.fail('memory held'))
  self.assertEqual(out['state'],'MEMORY_PRESSURE_REPAIR_HELD')
 def test_other_transport_cannot_be_restarted(self):
  with patch.object(m,'inspect_and_repair',side_effect=AssertionError('must not restart')):
   out=m.review_recovery('/sdk','physical-tv','/run','id',self.shared(),lambda:True,lambda:self.fail('wrong transport'))
  self.assertEqual(out['state'],'NONCANONICAL_EMULATOR_PRESERVED')
 def test_current_identity_checked_before_one_existing_recovery(self):
  sequence=[];after={'status':'COMPLETE','checks':[{'service':'jio_playback','dns':'RESOLVED','https':'REACHABLE'}]}
  def recover(*args):sequence.append('recover');return {'status':'DNS_AND_JIO_HTTPS_RECOVERED','restart':'COLD_BOOT_SAME_AVD_USING_VERIFIED_SYSTEM_DNS','after':after}
  with patch.object(m,'inspect_and_repair',side_effect=recover) as check:
   out=m.review_recovery('/sdk','emulator-5580','/run','id',self.shared(),lambda:True,lambda:sequence.append('verify'))
   check.assert_called_once_with('/sdk','emulator-5580','/run','id')
  self.assertEqual(sequence,['verify','recover']);self.assertIs(out['network'],after);self.assertEqual(out['state'],'DNS_AND_JIO_HTTPS_RECOVERED')
 def test_failure_remains_failure_no_retry_until_green(self):
  with patch.object(m,'inspect_and_repair',return_value={'status':'NETWORK_FAILURE_REMAINS'}) as check:
   out=m.review_recovery('/sdk','emulator-5580','/run','id',self.shared(),lambda:True,lambda:None);self.assertEqual(check.call_count,1)
  self.assertEqual(out['state'],'NETWORK_FAILURE_REMAINS')
 def test_identity_denial_cannot_reach_restart(self):
  def denied():raise RuntimeError('identity changed')
  with patch.object(m,'inspect_and_repair',side_effect=AssertionError('must not restart')):
   with self.assertRaisesRegex(RuntimeError,'identity changed'):m.review_recovery('/sdk','emulator-5580','/run','id',self.shared(),lambda:True,denied)
 def test_two_independent_dns_rounds_are_required(self):
  host={key:'RESOLVED' for key in m.HOSTS};round1={key:'DNS_UNAVAILABLE' for key in m.HOSTS}
  self.assertEqual(m.recovery_decision({'validated':False},host,[round1]),'INSUFFICIENT_DNS_EVIDENCE_NO_RESTART')
  self.assertEqual(m.recovery_decision({'validated':False},host,[round1,round1]),'HOST_EMULATOR_DNS_DIVERGENCE_CONFIRMED')
 def test_timeouts_without_unvalidated_network_are_not_dns_proof(self):
  host={key:'RESOLVED' for key in m.HOSTS};row={key:'DNS_CHECK_TIMED_OUT' for key in m.HOSTS}
  self.assertEqual(m.recovery_decision({'validated':True},host,[row,row]),'AMBIGUOUS_TIMEOUT_NO_RESTART')
if __name__=='__main__':unittest.main()
