"""Real native-window test on a cloud Mac; no emulator, owner app or private content."""
from pathlib import Path
import importlib.util,json,subprocess,sys,tempfile,time
assert sys.platform=='darwin','MACOS_REQUIRED'
R=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('tv_window',R/'tools/tv_window.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
record={'schema':'ghartv.rc103-native-window-test.v1','status':'STARTING','real_mac_window':True,'actual_nova_emulator':False,'owner_mac':False,'observations':[]}
child=None
try:
 with tempfile.TemporaryDirectory(prefix='ghartv-window-test-') as name:
  root=Path(name);swift=root/'Window.swift';binary=root/'GharTVWindowTest'
  swift.write_text('''import AppKit
let app = NSApplication.shared
app.setActivationPolicy(.regular)
let window = NSWindow(contentRect: NSRect(x: 80, y: 80, width: 640, height: 360), styleMask: [.titled, .closable, .resizable], backing: .buffered, defer: false)
window.title = "GharTV isolated window test"
window.makeKeyAndOrderFront(nil)
app.activate(ignoringOtherApps: true)
app.run()
''')
  compiled=subprocess.run(['/usr/bin/swiftc',str(swift),'-o',str(binary)],capture_output=True,text=True,timeout=60)
  assert compiled.returncode==0,'APPKIT_FIXTURE_COMPILE_FAILED: '+compiled.stderr[-800:]
  child=subprocess.Popen([str(binary)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  time.sleep(1)
  for stage in ('initial','after_hide'):
   if stage=='after_hide':
    code='ObjC.import("AppKit");var app=$.NSRunningApplication.runningApplicationWithProcessIdentifier('+str(child.pid)+');app.hide;"HIDE_REQUESTED";'
    hidden=subprocess.run(['/usr/bin/osascript','-l','JavaScript','-e',code],capture_output=True,text=True,timeout=5)
    assert hidden.returncode==0,'FIXTURE_HIDE_REQUEST_FAILED'
    time.sleep(.3)
   result=m.native_window(child.pid)
   record['observations'].append({'stage':stage,**result})
   assert result.get('window_observed') is True,result
   if stage=='initial':assert result.get('app_active') is True,result
   else:assert result['status']==('MAC_WINDOW_FRONTMOST_OBSERVED' if result.get('app_active') else 'MAC_WINDOW_ONSCREEN_OBSERVED'),result
  record['status']='PASS'
except Exception as error:record.update(status='FAIL',error=type(error).__name__+': '+str(error)[:1000])
finally:
 if child:
  child.terminate()
  try:child.wait(timeout=5)
  except subprocess.TimeoutExpired:child.kill();child.wait()
 (out/'MAC_WINDOW_VALIDATION.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
if record['status']!='PASS':sys.exit(1)
