"""Focused local observations, protected cleanup and shipping identity checks; no owner Mac access."""
import importlib.util, json, pathlib, tempfile, unittest
from unittest.mock import patch
root=pathlib.Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('host_check',pathlib.Path(__file__).with_name('host_check.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Checks(unittest.TestCase):
 def test_process_projection_drops_path_not_executables(self):
  r=m.processes('123 231.5 1048576 /Applications/Private App.app/Contents/MacOS/Worker\n8 0.0 1000 /usr/bin/node\ninvalid')
  self.assertEqual(r[0]['process'],'Worker');self.assertEqual(r[0]['rss_mib'],1024);self.assertNotIn('Private App',json.dumps(r));self.assertEqual(len(r),2)
 def test_nonmac_never_claims_host_measurements(self):
  with patch.object(m.platform,'system',return_value='Linux'),patch.object(m,'run') as call:
   d=m.capture();self.assertEqual(d['status'],'MAC_OBSERVATION_NOT_AVAILABLE_ON_THIS_HOST');call.assert_not_called()
 def test_private_save_and_symlink_preservation(self):
  with tempfile.TemporaryDirectory() as td:
   root=pathlib.Path(td);p=root/'report.json';m.save(p,{'ok':True});self.assertEqual(p.stat().st_mode&0o777,0o600)
   target=root/'keep';target.write_text('preserve');q=root/'link';q.symlink_to(target)
   with self.assertRaises(RuntimeError):m.save(q,{'bad':True})
   self.assertEqual(target.read_text(),'preserve')
 def test_source_network_outside_lock_and_preview_restored(self):
  java=root/'android-tv/app/src/main/java/in/ghartv/nova'
  s=(java/'ChannelRepository.java').read_text();start=s.index('public List<Channel> refreshJio');part=s[start:];self.assertLess(part.index('api.fetchChannels()'),part.index('synchronized (this)'))
  self.assertIn('PreviewGate.defaultEnabled',(java/'PlaybackComfort.java').read_text())
  h=(java/'HeroPreviewController.java').read_text();self.assertIn('gate.arm()',h);self.assertIn('PreviewGate.FIRST_FRAME_BUDGET_MS',h)
  self.assertIn('TrackedPlayback',(java/'JioApiClient.java').read_text());self.assertIn('playbackCalls.remove(call)',(java/'JioApiClient.java').read_text())
 def test_release_transitions_and_complete_bundle(self):
  s=(root/'tools/package_owner_review.py').read_text();self.assertIn("'tools/tv_local.py'",s);self.assertIn("'tools/performance/host_check.py'",s)
  starter=(root/'tools/run_owner_bundle.command.in').read_text();self.assertIn('99fedd034de8816e4c153f65f25eefbbe6a90150f33d2d3d3216e74deda351bb',starter)
  launcher=(root/'tools/owner_review.command.in').read_text();self.assertIn('PRIOR_REVIEW_PRESERVED_UNTIL_OWNER_ACCEPTANCE',launcher);self.assertIn("performance_snapshot('before');review()",launcher)
  self.assertIn('GHARTV_RC9_REVIEW.zip',s)
if __name__=='__main__':unittest.main()
