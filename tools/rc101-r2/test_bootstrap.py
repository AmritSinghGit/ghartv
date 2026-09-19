"""Execute the real embedded bootstrap in isolated homes, on Linux and macOS."""
import hashlib,json,os,subprocess,sys,tempfile,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[2]
TEMPLATE=(R/'tools/run_owner_bundle.command.in').read_text()
sha=lambda b:hashlib.sha256(b).hexdigest()
class BootstrapTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory(prefix='ghartv-bootstrap-tests-')
  self.base=Path(self.temp.name).resolve();self.home=self.base/'home';self.home.mkdir()
  self.root=self.base/'bundle';self.root.mkdir();(self.root/'assets').mkdir()
  self.core=b'#!/bin/bash\nprintf "fixture only\\n"\n';self.asset=b'public bytes only'
  (self.root/'GHARTV_SYNC_CURRENT_AND_REPORT.command').write_bytes(self.core)
  (self.root/'assets/sample.bin').write_bytes(self.asset)
  self.text=TEMPLATE.replace('@COMMAND_SHA@',sha(self.core)).replace('@ARTIFACT_HASHES_REPR@',repr({'sample.bin':sha(self.asset)}))
  self.seed=self.text.split("<<'SEED'\n",1)[1].split('\nSEED\n',1)[0]
  self.managed=self.home/'.local/share/ghartv-launcher/current';self.cache=self.home/'Library/Application Support/GharTV/owner-review/artifact-cache'
 def tearDown(self):self.temp.cleanup()
 def run_seed(self,root=None):
  env={'PATH':os.environ['PATH'],'HOME':str(self.home),'LANG':'en_US.UTF-8'}
  return subprocess.run([sys.executable,'-c',self.seed,str(root or self.root),'--noninteractive'],text=True,capture_output=True,env=env,timeout=15)
 def target(self):return self.managed/'GHARTV_SYNC_CURRENT_AND_REPORT.command'
 def test_fresh_install(self):
  p=self.run_seed();self.assertEqual(p.returncode,0,p.stdout+p.stderr);self.assertEqual(self.target().read_bytes(),self.core)
  self.assertEqual(self.target().stat().st_mode&0o777,0o700)
 def test_repeat_install(self):
  self.assertEqual(self.run_seed().returncode,0);self.assertEqual(self.run_seed().returncode,0)
  self.assertEqual((self.cache/sha(self.asset)).read_bytes(),self.asset)
 def test_corrupt_input_is_rejected_before_outputs(self):
  (self.root/'assets/sample.bin').write_bytes(b'corrupt');p=self.run_seed()
  self.assertNotEqual(p.returncode,0);self.assertIn('BUNDLE_CHECKSUM_MISMATCH',p.stdout);self.assertFalse(self.target().exists())
 def test_input_symlink_is_preserved(self):
  link=self.base/'package-link';link.symlink_to(self.root,target_is_directory=True);p=self.run_seed(link)
  self.assertNotEqual(p.returncode,0);self.assertIn(str(link),p.stdout);self.assertTrue(link.is_symlink());self.assertFalse(self.target().exists())
 def test_asset_symlink_is_preserved(self):
  p=self.root/'assets/sample.bin';p.unlink();outside=self.base/'outside';outside.write_bytes(self.asset);p.symlink_to(outside)
  r=self.run_seed();self.assertIn('UNEXPECTED_SYMLINK_PRESERVED',r.stdout);self.assertEqual(outside.read_bytes(),self.asset)
 def test_managed_symlink_is_preserved(self):
  self.managed.parent.mkdir(parents=True);outside=self.base/'outside';outside.mkdir();self.managed.symlink_to(outside,target_is_directory=True)
  p=self.run_seed();self.assertNotEqual(p.returncode,0);self.assertIn(str(self.managed),p.stdout);self.assertEqual(list(outside.iterdir()),[])
 def test_unknown_launcher_is_preserved(self):
  self.managed.mkdir(parents=True);self.target().write_bytes(b'custom source')
  p=self.run_seed();self.assertIn('NEWER_OR_EDITED_LAUNCHER_PRESERVED',p.stdout);self.assertEqual(self.target().read_bytes(),b'custom source')
 def test_cache_conflict_is_preserved(self):
  self.cache.mkdir(parents=True);p=self.cache/sha(self.asset);p.write_bytes(b'different')
  result=self.run_seed();self.assertNotEqual(result.returncode,0);self.assertEqual(p.read_bytes(),b'different');self.assertFalse(self.target().exists())
 def test_symlink_target_is_preserved(self):
  self.managed.mkdir(parents=True);outside=self.base/'other';outside.write_bytes(b'keep');self.target().symlink_to(outside)
  p=self.run_seed();self.assertIn('UNEXPECTED_SYMLINK_PRESERVED',p.stdout);self.assertEqual(outside.read_bytes(),b'keep')
 def test_failure_receipt_exists(self):
  (self.root/'assets/sample.bin').write_bytes(b'bad');p=self.run_seed();self.assertIn('GHARTV_STARTUP_HANDOFF',p.stdout)
  entries=list((self.home/'Library/Application Support/GharTV/owner-review/runs').glob('*/bootstrap.json'));self.assertEqual(len(entries),1)
  data=json.loads(entries[0].read_text());self.assertEqual(data['status'],'ACTION_REQUIRED');self.assertFalse(data['apk_installed']);self.assertFalse(data['production_changed'])
 def test_bootstrap_does_not_start_browser_or_android(self):
  p=self.run_seed();self.assertEqual(p.returncode,0);self.assertNotIn("['adb'",self.seed);self.assertNotIn('Popen',self.seed)
 def test_default_is_full_review_not_prepare(self):
  last=self.text.split('SEED\n')[-1];self.assertIn('--bundled-review "$@"',last);self.assertNotIn('--prepare-update',last)
 @unittest.skipUnless(sys.platform=='darwin','Real Apple alias test executes only on macOS')
 def test_real_var_alias_matches_user_failure_path(self):
  physical=self.root.resolve();self.assertTrue(str(physical).startswith('/private/var/'))
  logical=Path(str(physical).replace('/private/var/','/var/',1))
  self.assertTrue(Path('/var').is_symlink());self.assertTrue(any(p.is_symlink() for p in (logical,*logical.parents)))
  p=self.run_seed(logical);self.assertEqual(p.returncode,0,p.stdout+p.stderr);self.assertEqual(self.target().read_bytes(),self.core)
 @unittest.skipUnless(sys.platform=='darwin','Real Apple alias test executes only on macOS')
 def test_real_tmp_alias(self):
  with tempfile.TemporaryDirectory(prefix='ghartv-tmp-alias-',dir='/tmp') as d:
   import shutil
   root=Path(d)/'bundle';shutil.copytree(self.root,root)
   p=self.run_seed(root);self.assertEqual(p.returncode,0,p.stdout+p.stderr)
if __name__=='__main__':unittest.main(verbosity=2)
