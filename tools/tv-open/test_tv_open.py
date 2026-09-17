import importlib.util,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch,Mock
ROOT=Path(__file__).resolve().parent
SRC=ROOT/'src' if (ROOT/'src').exists() else ROOT.parent.parent
spec=importlib.util.spec_from_file_location('tv_local',SRC/'tools/tv_local.py');tv=importlib.util.module_from_spec(spec);spec.loader.exec_module(tv)
sys.modules['tv_local']=tv
class Check(unittest.TestCase):
 def test_rejected_receipt_is_openable_without_reapproval(self):
  with tempfile.TemporaryDirectory() as d:
   state=Path(d)/'state';(state/'current').mkdir(parents=True)
   old={'review_source':'a'*40,'version':'0.6.0-rc7-network-diagnostics','version_code':23,'status':'ACTION_REQUIRED','owner_decision':'REJECTED_PLAYBACK_BLOCKED','signed_apk_sha256':'b'*64}
   (state/'current/receipt.json').write_text(json.dumps(old))
   with patch.object(tv.sys,'platform','darwin'),patch.object(tv,'STATE',state),patch.object(tv,'HOME',Path(d)),patch.object(tv,'sdk',return_value=Path(d)),patch.object(tv,'attach_or_start',return_value=(Path(d)/'adb',False)),patch.object(tv,'installed_identity',return_value='b'*64),patch.object(tv,'open_activity') as open_app,patch.object(tv,'request_window',return_value='MAC_ACTIVATION_ACCEPTED'):
    result=tv.main_action('open','a'*40)
   self.assertTrue(result['ok']);open_app.assert_called_once();self.assertEqual(result['owner_decision'],'REJECTED_PLAYBACK_BLOCKED');self.assertEqual(json.loads((state/'current/receipt.json').read_text()),old)
 def test_existing_emulator_never_started_twice(self):
  def cmd(args,timeout=8):
   a=list(map(str,args));out=''
   if a[-1]=='devices':out='List of devices attached\nemulator-5580\tdevice\n'
   if a[-3:]==['emu','avd','name']:out=tv.AVD+'\nOK'
   if a[-1]=='sys.boot_completed':out='1'
   return Mock(stdout=out,stderr='',returncode=0)
  with patch.object(tv,'call',side_effect=cmd),patch.object(tv.subprocess,'Popen') as p:
   _,started=tv.attach_or_start(Path('/fake'),Path('/tmp'))
  self.assertFalse(started);p.assert_not_called()
 def test_offline_never_duplicate(self):
  with patch.object(tv,'call',return_value=Mock(stdout='emulator-5580 offline\n',returncode=0)),patch.object(tv.subprocess,'Popen') as p:
   with self.assertRaisesRegex(tv.Hold,'OFFLINE'):tv.attach_or_start(Path('/fake'),Path('/tmp'))
   p.assert_not_called()
 def test_foreground_not_inferred_from_am_success(self):
  with patch.object(tv,'call',return_value=Mock(stdout='Status: ok',stderr='',returncode=0)),patch.object(tv.time,'sleep'):
   with self.assertRaisesRegex(tv.Hold,'NOT_FOREGROUND'):tv.open_activity('/fake/adb')
 def test_release_action_bypasses_readiness_gate(self):
  spec=importlib.util.spec_from_file_location('release_control',SRC/'tools/release_control.py');rc=importlib.util.module_from_spec(spec);spec.loader.exec_module(rc)
  with tempfile.TemporaryDirectory() as d,patch.object(rc,'RELEASE',Path(d)/'release'),patch.object(rc.sys,'argv',['release_control.py','open-tv','a'*40]),patch.object(rc,'identity',side_effect=AssertionError('gate called')),patch.object(tv,'main_action',return_value={'ok':True}) as act:
   self.assertTrue(rc.main()['ok']);act.assert_called_once_with('open','a'*40)
 def test_dns_resolved_not_ping_exit_code(self):
  with patch.object(tv,'call',return_value=Mock(stdout='PING fixture (192.0.2.4): 56 data bytes',stderr='',returncode=1)):
   self.assertEqual(tv.device_dns('/fake','fixture'),'RESOLVED')
if __name__=='__main__':unittest.main()
