import unittest,importlib.util,subprocess
from pathlib import Path
spec=importlib.util.spec_from_file_location('tv_local',Path(__file__).parents[1]/'tv_local.py');tv=importlib.util.module_from_spec(spec);spec.loader.exec_module(tv)
class Identity(unittest.TestCase):
 def setUp(self):self.call,self.procs=tv.call,tv.avd_processes;self.rows={'emulator-5554':('device','1',tv.AVD,'')};tv.call=self.fake;tv.avd_processes=lambda:[]
 def tearDown(self):tv.call=self.call;tv.avd_processes=self.procs
 def fake(self,args,timeout=8):
  a=list(map(str,args));out='';code=0
  if a[-1]=='devices':out='List of devices attached\n'+''.join(f'{k}\t{v[0]}\n' for k,v in self.rows.items())
  elif '-s' in a:
   row=self.rows.get(a[a.index('-s')+1]);
   if row is None:return subprocess.CompletedProcess(a,1,'','missing')
   if a[-1]=='ro.kernel.qemu':out=row[1]
   elif a[-1]=='ro.boot.qemu.avd_name':out=row[2]
   elif a[-3:]==['emu','avd','name']:out=row[3]
  return subprocess.CompletedProcess(a,code,out,'')
 def test_empty_console_uses_exact_boot_avd(self):self.assertEqual('emulator-5554',tv.existing_serial('/sdk/adb'))
 def test_unresponsive_identity_never_selects_old_port(self):self.rows['emulator-5554']=('device','1','','');self.assertRaises(tv.Hold,tv.existing_serial,'/sdk/adb')
 def test_wrong_boot_identity_not_overridden_by_console(self):self.rows['emulator-5554']=('device','1','Different',tv.AVD+'\nOK');self.assertRaises(tv.Hold,tv.existing_serial,'/sdk/adb')
 def test_physical_properties_refused(self):self.rows['emulator-5554']=('device','0',tv.AVD,'');self.assertRaises(tv.Hold,tv.existing_serial,'/sdk/adb')
 def test_duplicate_matching_emulators_refused(self):self.rows['emulator-5556']=self.rows['emulator-5554'];self.assertRaises(tv.Hold,tv.existing_serial,'/sdk/adb')
 def test_offline_running_transport_refused(self):self.rows['emulator-5554']=('offline','1',tv.AVD,'');self.assertRaises(tv.Hold,tv.existing_serial,'/sdk/adb')
 def test_absent_transport_preserves_existing_start_path(self):self.rows={};self.assertEqual(tv.SERIAL,tv.existing_serial('/sdk/adb'))
 def test_legacy_name_only_with_qemu_is_accepted(self):self.rows['emulator-5554']=('device','1','',tv.AVD+'\nOK\n');self.assertEqual('emulator-5554',tv.existing_serial('/sdk/adb'))
if __name__=='__main__':unittest.main()
