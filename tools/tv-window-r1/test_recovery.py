import importlib.util,io,json,os,tarfile,tempfile,unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
P=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('recover',P/'open_tv.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Tests(unittest.TestCase):
 def test_current_candidate(self):m.validate_receipt({'review_source':m.SOURCE,'version_code':30,'signed_apk_sha256':m.SIGNED})
 def test_other_source_preserved(self):
  for key in ('review_source','version_code','signed_apk_sha256'):
   r={'review_source':m.SOURCE,'version_code':30,'signed_apk_sha256':m.SIGNED};r[key]='wrong'
   with self.assertRaises(m.Hold):m.validate_receipt(r)
 def test_precise_mirror_transport(self):
  args=m.mirror_args('/private/tool/scrcpy');self.assertIn('--serial=emulator-5580',args);self.assertIn('--max-size=1280',args);self.assertIn('--max-fps=30',args);self.assertIn('--no-clipboard-autosync',args)
  self.assertFalse(any(x.startswith(('--record','--new-display','--tcpip','--turn-screen-off','--power-off-on-close','--stay-awake','--start-app')) for x in args))
 def archive(self,name,kind=tarfile.REGTYPE):
  b=io.BytesIO()
  with tarfile.open(fileobj=b,mode='w') as z:
   info=tarfile.TarInfo(name);info.type=kind;info.size=0;z.addfile(info)
  b.seek(0);return tarfile.open(fileobj=b,mode='r')
 def test_tar_regular(self):
  with self.archive('root/scrcpy') as z:self.assertEqual(list(m.archive_files(z)),['root/scrcpy'])
 def test_tar_paths(self):
  for name in ('/absolute','root/../../escape','root\\escape'):
   with self.archive(name) as z:
    with self.assertRaises(m.Hold):m.archive_files(z)
 def test_tar_links(self):
  for kind in (tarfile.SYMTYPE,tarfile.LNKTYPE,tarfile.CHRTYPE):
   with self.archive('root/a',kind) as z:
    with self.assertRaises(m.Hold):m.archive_files(z)
 def test_linked_local_path(self):
  with tempfile.TemporaryDirectory() as name:
   p=Path(name).resolve();(p/'real').mkdir();(p/'alias').symlink_to(p/'real')
   with self.assertRaises(m.Hold):m.safe(p/'alias'/'new')
 def test_reused_pid_preserved(self):
  with patch.object(m,'call',return_value=SimpleNamespace(stdout=str(os.getuid())+' /other/app',returncode=0)):
   with self.assertRaises(m.Hold):m.process_matches({'pid':123,'process_started':'time'},Path('/own/scrcpy'))
 def test_closed_display_not_running(self):
  with patch.object(m,'call',return_value=SimpleNamespace(stdout='',returncode=1)):
   self.assertFalse(m.process_matches({'pid':123},Path('/own/scrcpy')))
 def test_no_install_no_device_restart(self):
  s=(P/'open_tv.py').read_text();self.assertNotIn("'install'",s);self.assertNotIn('kill-server',s);self.assertNotIn('os.kill',s);self.assertNotIn("'uninstall'",s);self.assertNotIn("'force-stop'",s);self.assertNotIn('screenrecord',s)
 def test_probe_not_gated_on_application_registration(self):
  s=(P/'DisplayProbe.swift').read_text();self.assertIn('let registered = app != nil',s);self.assertIn('let all = CGWindowListCopyWindowInfo',s);self.assertNotIn('let target = NSRunningApplication(processIdentifier: pid) {',s)
if __name__=='__main__':unittest.main()
