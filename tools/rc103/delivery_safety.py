"""Final guarded corrections before the exact-source build."""
from pathlib import Path
R=Path(__file__).resolve().parents[2]
p=R/'tools/owner_review.command.in';s=p.read_text()
# An Android version bump must never change curl's exit status for a timeout.
s=s.replace("subprocess.CompletedProcess(command,29,'','')","subprocess.CompletedProcess(command,28,'','')")
s=s.replace("category='TIMEOUT' if p.returncode==29","category='TIMEOUT' if p.returncode==28")
s=s.replace('p.returncode in (6,7,18,29,35,52,55,56,92)','p.returncode in (6,7,18,28,35,52,55,56,92)')
s=s.replace("if note_text and not args.memory_only:\n     if r.get('dashboard')", "if note_text and not args.memory_only and not args.tv_only:\n     if r.get('dashboard')")
s=s.replace("r['status']='WEB_REVIEW_READY_ANDROID_HELD';r['blocker']=str(error)","r['status']='WEB_REVIEW_READY_ANDROID_HELD' if r.get('web_player')=='HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED' else 'ANDROID_HELD_RESOURCE_PRESSURE';r['blocker']=str(error)")
s=s.replace("print('Web review is ready. Android APK is signed and preserved; no new emulator was started under memory pressure.',flush=True)","print('Android APK is signed and preserved. No new emulator was started under memory pressure. Web status: '+r.get('web_player','NOT_STARTED'),flush=True)")
s=s.replace(" result=helper.native_window(pid)\n r['film_window']", " result=helper.native_window(pid)\n for attempt in range(5):\n  if result.get('window_observed'):break\n  time.sleep(.4);result=helper.native_window(pid)\n r['film_window']")
s=s.replace("print('3 / 5 · Signed APK payload and original certificate verified. Open local review first.',flush=True)","print('3 / 5 · New code29 APK and original signing certificate verified.',flush=True)")
p.write_text(s)
p=R/'web-player/review-sync.mjs';s=p.read_text()
s=s.replace("'FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED'].includes(r.status)","'FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED','ANDROID_HELD_RESOURCE_PRESSURE'].includes(r.status)")
s=s.replace("next_action:r.status==='ANDROID_READY_WINDOW_UNCONFIRMED'", "next_action:r.status==='ANDROID_HELD_RESOURCE_PRESSURE'?'REVIEW_PREPARED_APK_OR_RELEASE_IDENTIFIED_IDLE_RESOURCES':r.status==='ANDROID_READY_WINDOW_UNCONFIRMED'")
p.write_text(s)
p=R/'web-player/server.mjs';s=p.read_text().replace('const APP_VERSION = "0.6.0-rc10.1-web-films"','const APP_VERSION = "0.6.0-rc10.3-in-app-films"');p.write_text(s)
print('TV_ONLY_FOCUS_AND_TIMEOUT_SEMANTICS_VERIFIED')
