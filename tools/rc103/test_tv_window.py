import importlib.util,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace
R=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('tv_window',R/'tools/tv_window.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
CMD='/Library/Android/emulator/qemu/darwin-aarch64/qemu-system-aarch64 -avd '+m.AVD+' -port 5580'
class WindowTests(unittest.TestCase):
 def test_exact_qemu_only(self):
  self.assertEqual(m.classify_processes([(123,CMD)])['pid'],123)
  for cmd in [CMD.replace(m.AVD,'Other'),'/usr/bin/echo '+CMD,CMD+' -avd Other']:
   self.assertEqual(m.classify_processes([(123,cmd)])['status'],'MAC_TARGET_NOT_UNIQUE')
 def test_multiple_targets_not_activated(self):self.assertEqual(m.classify_processes([(123,CMD),(124,CMD)])['status'],'MAC_TARGET_NOT_UNIQUE')
 def test_headless_preserved(self):self.assertEqual(m.classify_processes([(123,CMD+' -no-window')])['status'],'HEADLESS_EMULATOR_PRESERVED')
 def test_embedded_is_not_a_standalone_window(self):self.assertEqual(m.classify_processes([(123,CMD+' -qt-hide-window')])['status'],'ANDROID_STUDIO_EMBEDDED_WINDOW')
 def test_pid_revalidation_preserves_unrelated_process(self):
  r=m.request_window_details([(123,CMD)],run=lambda *a,**k:SimpleNamespace(returncode=0,stdout=str(os.getuid())+' /usr/bin/other'))
  self.assertEqual(r['status'],'MAC_TARGET_CHANGED_PRESERVED')
 def test_query_failure_never_reports_visible(self):
  with patch.object(m.sys,'platform','darwin'),patch.object(m,'restore_minimized_if_authorized',return_value={}),patch.object(m,'verified_probe',return_value='/test/probe'):
   r=m.native_window(123,run=lambda *a,**k:SimpleNamespace(returncode=1,stdout=''))
  self.assertFalse(r['window_observed']);self.assertEqual(r['status'],'MAC_WINDOW_QUERY_UNAVAILABLE')
 def test_native_result_fields_are_separate(self):
  with patch.object(m.sys,'platform','darwin'),patch.object(m,'restore_minimized_if_authorized',return_value={}),patch.object(m,'verified_probe',return_value='/test/probe'):
   r=m.native_window(123,run=lambda *a,**k:SimpleNamespace(returncode=0,stdout=json.dumps({'status':'MAC_WINDOW_ONSCREEN_OBSERVED','window_observed':True,'app_active':False})))
  self.assertTrue(r['window_observed']);self.assertFalse(r['app_active'])
 def test_launcher_calls_existing_window_helper_and_does_not_force_stop(self):
  s=(R/'tools/owner_review.command.in').read_text();self.assertIn('transport.request_window_details()',s);self.assertNotIn("'am','force-stop',PACKAGE",s);self.assertIn('ANDROID_READY_WINDOW_UNCONFIRMED',s)
if __name__=='__main__':unittest.main()
