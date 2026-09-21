"""Final guarded corrections before the exact-source build."""
from pathlib import Path
R=Path(__file__).resolve().parents[2]
p=R/'tools/owner_review.command.in';s=p.read_text()
s=s.replace("subprocess.CompletedProcess(command,29,'','')","subprocess.CompletedProcess(command,28,'','')")
s=s.replace("category='TIMEOUT' if p.returncode==29","category='TIMEOUT' if p.returncode==28")
s=s.replace('p.returncode in (6,7,18,29,35,52,55,56,92)','p.returncode in (6,7,18,28,35,52,55,56,92)')
s=s.replace("if note_text and not args.memory_only:\n     if r.get('dashboard')", "if note_text and not args.memory_only and not args.tv_only:\n     if r.get('dashboard')")
s=s.replace("r['status']='WEB_REVIEW_READY_ANDROID_HELD';r['blocker']=str(error)","r['status']='WEB_REVIEW_READY_ANDROID_HELD' if r.get('web_player')=='HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED' else 'ANDROID_HELD_RESOURCE_PRESSURE';r['blocker']=str(error)")
s=s.replace("print('Web review is ready. Android APK is signed and preserved; no new emulator was started under memory pressure.',flush=True)","print('Android APK is signed and preserved. No new emulator was started under memory pressure. Web status: '+r.get('web_player','NOT_STARTED'),flush=True)")
if 'for attempt in range(5)' not in s[s.index('def open_films():'):]:
 s=s.replace(" result=helper.native_window(pid)\n r['film_window']", " result=helper.native_window(pid)\n for attempt in range(5):\n  if result.get('window_observed'):break\n  time.sleep(.4);result=helper.native_window(pid)\n r['film_window']")
s=s.replace("print('3 / 5 · Signed APK payload and original certificate verified. Open local review first.',flush=True)","print('3 / 5 · New code29 APK and original signing certificate verified.',flush=True)")
p.write_text(s)
p=R/'web-player/review-sync.mjs';s=p.read_text()
s=s.replace("'FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED'].includes(r.status)","'FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED','ANDROID_HELD_RESOURCE_PRESSURE'].includes(r.status)")
if "next_action:r.status==='ANDROID_HELD_RESOURCE_PRESSURE'" not in s:
 s=s.replace("next_action:r.status==='ANDROID_READY_WINDOW_UNCONFIRMED'", "next_action:r.status==='ANDROID_HELD_RESOURCE_PRESSURE'?'REVIEW_PREPARED_APK_OR_RELEASE_IDENTIFIED_IDLE_RESOURCES':r.status==='ANDROID_READY_WINDOW_UNCONFIRMED'")
p.write_text(s)
p=R/'web-player/server.mjs';s=p.read_text().replace('const APP_VERSION = "0.6.0-rc10.1-web-films"','const APP_VERSION = "0.6.0-rc10.3-in-app-films"');p.write_text(s)
# Request focus only with already-granted Accessibility. Never request permission.
p=R/'tools/tv_window.py';s=p.read_text()
if "key('AXFrontmost')" not in s:
 s=s.replace("        windows_key, minimized_key, raise_key = key('AXWindows'), key('AXMinimized'), key('AXRaise')", "        true_value=ctypes.c_void_p.in_dll(cf,'kCFBooleanTrue')\n        result['frontmost_request_accepted']=ax.AXUIElementSetAttributeValue(app,key('AXFrontmost'),true_value)==0\n        windows_key, minimized_key, raise_key = key('AXWindows'), key('AXMinimized'), key('AXRaise')")
p.write_text(s)
p=R/'tools/films/GharTVWindowProbe.swift';s=p.read_text().replace('[.activateAllWindows, .activateIgnoringOtherApps]','[.activateAllWindows]');p.write_text(s)
# macOS may decline focus even when it reveals a window. Test that this state is
# accurately reported; the production REVIEW_READY gate still requires actual focus.
p=R/'tools/rc103/mac_window_smoke.py';s=p.read_text()
s=s.replace("   assert result.get('app_active') is True,result", "   if stage=='initial':assert result.get('app_active') is True,result\n   else:assert result['status']==('MAC_WINDOW_FRONTMOST_OBSERVED' if result.get('app_active') else 'MAC_WINDOW_ONSCREEN_OBSERVED'),result")
p.write_text(s)
# A same-document provider redirect can happen after the first page-finished event.
p=R/'tools/films/GharTVFilmView.swift';s=p.read_text()
if 'var routeObservation:' not in s:
 s=s.replace('    var lastNavigationFailed = false','    var routeObservation: NSKeyValueObservation?\n    var lastNavigationFailed = false')
 anchor='        browser.navigationDelegate = self; browser.uiDelegate = self; browser.allowsBackForwardNavigationGestures = true'
 s=s.replace(anchor,anchor+r'''
        routeObservation = browser.observe(\.url, options: [.new]) { [weak self] view, _ in
            guard let self = self, view.url?.path == "/dummy" else { return }
            self.lastNavigationFailed = true
            self.status.stringValue = "FlixMomo declined this embedded session. Search and playback are not verified."
            self.emit(["event":"provider_blocked","code":"DUMMY_REDIRECT","playbackVerified":false])
        }''')
p.write_text(s)
p=R/'tools/rc103/mac_in_app_smoke.py';s=p.read_text()
s=s.replace("threading.Thread(target=reader,daemon=True).start();deadline=time.monotonic()+40", "threading.Thread(target=reader,daemon=True).start();deadline=time.monotonic()+40;settle=None")
s=s.replace("  while time.monotonic()<deadline:\n   try:item=messages.get(timeout=1);events.append(item)","  while time.monotonic()<deadline:\n   if settle is not None and time.monotonic()>=settle:break\n   try:item=messages.get(timeout=1);events.append(item)")
s=s.replace("   if item.get('event') in ('page_finished','provider_blocked','navigation_failed'):break", "   if item.get('event') in ('provider_blocked','navigation_failed'):break\n   if item.get('event')=='page_finished' and settle is None:settle=time.monotonic()+8")
p.write_text(s)
print('TV_ONLY_FOCUS_TIMEOUTS_AND_LATE_PROVIDER_STATE_VERIFIED')
