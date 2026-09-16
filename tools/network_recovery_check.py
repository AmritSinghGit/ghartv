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
  before={'schema':'ghartv.network-probe.v1','checks':[{'service':s,'dns':dns,'https':'REACHABLE' if dns=='RESOLVED' else 'NOT_CHECKED'} for s in m.HOSTS]}
  with tempfile.TemporaryDirectory() as d,patch.object(m,'call',fake),patch.object(m,'probe',return_value=before),patch.object(m,'system_dns',return_value=[]),patch.object(m.subprocess,'Popen',side_effect=AssertionError('no new process expected')):
   result=m.inspect_and_repair('/test/sdk',serial,Path(d),'TEST-CHECK-123456');self.assertEqual(json.loads((Path(d)/'NETWORK_CHECK.json').read_text()),result)
  return result,calls
 def test_healthy_no_restart(self):
  r,c=self.simulate();self.assertEqual(r['status'],'JIO_HTTPS_REACHABLE');self.assertFalse(any('emu kill' in v for v in c))
 def test_shared_failure_no_restart(self):
  r,c=self.simulate('DNS_UNAVAILABLE',False);self.assertEqual(r['restart'],'NOT_NEEDED');self.assertFalse(any('emu kill' in v for v in c))
 def test_mismatch_without_verified_resolver_preserved(self):
  r,c=self.simulate('DNS_UNAVAILABLE');self.assertEqual(r['restart'],'DEFERRED_NO_WORKING_SYSTEM_RESOLVER');self.assertFalse(any('emu kill' in v for v in c))
 def test_other_device_no_actions(self):
  r,c=self.simulate(serial='physical-tv');self.assertEqual(c,[]);self.assertEqual(r['status'],'NONCANONICAL_EMULATOR_PRESERVED')
 def test_wrong_avd_preserved(self):
  r,c=self.simulate(name='Other_AVD');self.assertEqual(r['status'],'AVD_IDENTITY_MISMATCH_PRESERVED');self.assertFalse(any('emu kill' in v for v in c))
 def test_guarded_boot_arguments(self):
  s=Path(m.__file__).read_text();self.assertNotIn('-wipe-data',s);self.assertNotIn('8.8.8.8',s);self.assertIn("'-no-snapshot-load','-no-snapshot-save','-dns-server'",s);self.assertIn('system_dns()',s)
if __name__=='__main__':unittest.main()
