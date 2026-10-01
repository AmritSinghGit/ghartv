import json,importlib.util,unittest,subprocess
from pathlib import Path
spec=importlib.util.spec_from_file_location('trace',Path(__file__).with_name('collect_review_interactions.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class CaptureTests(unittest.TestCase):
 def row(self,**kw):return dict(schema='ghartv.ui-transition.v1',version_code=48,seq=1,elapsed_ms=10,event='BACK',layer='POINTER',pointer_enabled=True,pointer_visible=False,**kw)
 def test_unknown_fields_never_copied(self):
  rows=m.parse('GHUX_V1 '+json.dumps(self.row(url='https://private',title='x')));self.assertEqual(1,len(rows));self.assertNotIn('url',rows[0]);self.assertNotIn('title',rows[0])
 def test_invalid_events_not_copied(self):
  row=self.row();row['event']='my password';self.assertEqual([],m.parse('GHUX_V1 '+json.dumps(row)))
 def test_non_json_and_wrong_tag_ignored(self):self.assertEqual([],m.parse('OtherTAG secret\nGHUX_V1 corrupt'))
 def test_bounded_to128(self):self.assertEqual(128,len(m.parse('\n'.join('GHUX_V1 '+json.dumps(self.row()) for _ in range(180)))))
 def test_invalid_types_rejected(self):
  for k in ('seq','version_code','elapsed_ms'):
   r=self.row();r[k]=True;self.assertEqual([],m.parse('GHUX_V1 '+json.dumps(r)))
 def test_other_avd_not_read(self):
  calls=[]
  def call(a,t,c):calls.append(a);return subprocess.CompletedProcess(a,0,'Other_AVD\nOK','')
  self.assertEqual('EXISTING_AVD_NOT_CONFIRMED',m.capture('adb',call)['state']);self.assertEqual(1,len(calls))
 def test_missing_trace_is_not_no_activity_claim(self):
  def call(a,t,c):
   text=m.AVD+'\nOK' if 'emu' in a else '55' if 'pidof' in a else ''
   return subprocess.CompletedProcess(a,0,text,'')
  r=m.capture('adb',call);self.assertEqual('NO_COMPATIBLE_UI_TRACE_IN_BUFFER',r['state']);self.assertFalse(r['uploaded'])
 def test_read_failure_does_not_abort_review(self):
  def call(a,t,c):raise TimeoutError()
  self.assertEqual('APP_TRACE_UNAVAILABLE',m.capture('adb',call)['state'])
if __name__=='__main__':unittest.main()
