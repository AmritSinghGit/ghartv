import importlib.util
import subprocess
import time
import unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('review_clock',Path(__file__).with_name('review_clock.py'));clock=importlib.util.module_from_spec(spec);spec.loader.exec_module(clock)

class Fake:
    def __init__(self,offset=0,physical=False,denied=False):self.epoch=time.time()+offset;self.physical=physical;self.denied=denied;self.calls=[]
    def __call__(self,args,timeout=10,check=False):
        self.calls.append([str(x) for x in args]);out='';code=0;err=''
        if args[-3:]==['emu','avd','name']:out=clock.AVD+'\nOK\n'
        elif args[-2:]==['getprop','ro.kernel.qemu']:out='0' if self.physical else '1'
        elif args[-2:]==['date','+%s']:out=str(int(self.epoch))+'\n'
        elif 'set-time' in args:
            if self.denied:code=1;err='Permission denied'
            else:self.epoch=int(args[-1])/1000
        return subprocess.CompletedProcess(args,code,out,err)
    def writes(self):return [a for a in self.calls if 'set-time' in a]
class ClockCases(unittest.TestCase):
    def run_clock(self,f,**kw):return clock.reconcile('/sdk/adb',kw.get('serial',clock.SERIAL),kw.get('avd',clock.AVD),f,kw.get('reference',time.time))
    def test_aligned_clock_not_mutated(self):
        f=Fake();r=self.run_clock(f,reference=lambda:(_ for _ in ()).throw(AssertionError('should not probe')));self.assertEqual('CLOCK_ALIGNED',r['state']);self.assertFalse(f.writes())
    def test_future_clock_corrected_and_read_back(self):
        f=Fake(11*3600);r=self.run_clock(f);self.assertTrue(r['changed']);self.assertEqual(1,len(f.writes()));self.assertLess(abs(r['offset_after_seconds']),5)
    def test_past_clock_corrected(self):
        f=Fake(-13*3600);self.assertTrue(self.run_clock(f)['changed'])
    def test_other_serial_never_called(self):
        f=Fake(3600);self.assertEqual('TARGET_REJECTED',self.run_clock(f,serial='physical-device')['state']);self.assertEqual([],f.calls)
    def test_other_avd_never_called(self):
        f=Fake(3600);self.assertEqual('TARGET_REJECTED',self.run_clock(f,avd='Another_AVD')['state']);self.assertEqual([],f.calls)
    def test_physical_properties_refused(self):
        f=Fake(3600,physical=True);self.assertEqual('TARGET_REJECTED',self.run_clock(f)['state']);self.assertFalse(f.writes())
    def test_refusal_does_not_escalate(self):
        f=Fake(3600,denied=True);r=self.run_clock(f);self.assertEqual('CLOCK_SET_NOT_PERMITTED',r['state']);self.assertFalse(r['changed']);self.assertFalse(any('root' in a for a in f.calls))
    def test_uncorroborated_host_refused(self):
        f=Fake(3600);r=self.run_clock(f,reference=lambda:time.time()+3000);self.assertEqual('CLOCK_REFERENCE_UNVERIFIED_NO_CHANGE',r['state']);self.assertFalse(f.writes())
    def test_missing_https_reference_no_change(self):
        f=Fake(3600);r=self.run_clock(f,reference=lambda:(_ for _ in ()).throw(OSError('TLS')));self.assertFalse(r['changed']);self.assertFalse(f.writes())
    def test_large_unknown_date_is_not_rewritten(self):
        f=Fake(30*86400);self.assertEqual('CLOCK_REQUIRES_MANUAL_REVIEW',self.run_clock(f)['state']);self.assertFalse(f.writes())
    def test_probe_id_rejected_before_logs(self):
        f=Fake();self.assertEqual('PROBE_ID_REJECTED',clock.capture_probe('/sdk/adb',f,'../private')['state']);self.assertEqual([],f.calls)
    def test_source_keeps_security_and_device_boundaries(self):
        s=Path(clock.__file__).read_text();self.assertNotIn('CERT_NONE',s);self.assertNotIn('unverified_context',s);self.assertNotIn("'root'",s);self.assertNotIn('settings put',s);self.assertIn('NoRedirect',s)
if __name__=='__main__':unittest.main()
