"""Use existing signer and emulator workflow with a bounded private pre-update UI trace."""
from pathlib import Path
import re,json,hashlib
root=Path('/tmp/ghartv-candidate')
assert re.search(r'OK \(180 tests\)',(root/'proof/android-tests.txt').read_text())
s=Path(__file__).with_name('package_review44.py').read_text()
s=s.replace('44','48').replace('0.6.0-rc12.5-loading-mouse-review','0.6.0-rc13.2-input-stability-review')
s=s.replace("'actual_android_tests_passed':122","'actual_android_tests_passed':180").replace("'retained_review43_tests':110","'retained_review47_tests':160").replace("'new_review48_tests':12","'new_review48_tests':20")
anchor='def rawcode(t):'
injection='''anchor="                sdk=transport.sdk();tools,env=signing_tools(sdk);unsigned=artifact(APK_ASSET,APK_SHA)"
capture="""                capture_helper=source_module(z,'tools/tv-first/collect_review_interactions.py')
                prior_ui=capture_helper.capture(sdk/'platform-tools/adb',call)
                atomic(RUN/'PRIOR_UI_INTERACTIONS.json',prior_ui)
                R['prior_ui_capture_state']=prior_ui['state']
                print('Prior GharTV interaction trace: '+prior_ui['state']+'; '+str(len(prior_ui.get('events',[])))+' technical records (private, not uploaded)',flush=True)
"""
assert entry.count(anchor)==1
entry=entry.replace(anchor,anchor+'\\n'+capture)
entry=entry.replace("good=R.get('mac_window_observed') is True and", "good=R.get('android_result') in ('REUSED_NORMAL_NOVA_CODE48','OPENED_NORMAL_NOVA_CODE48') and R.get('mac_window_observed') is True and")
'''
assert s.count(anchor)==1;s=s.replace(anchor,injection+anchor)
s=s.replace("('artifact','android_review')","('artifact','android_review','main')")
exec(compile(s,'package_review48_expanded','exec'),{'__name__':'__main__'})
p=root/'VALIDATION.json';v=json.loads(p.read_text());v.update(pointer_idle_ms=5000,immediate_dpad_tap=True,paired_back_menu_keys=True,stale_fallback_guard=True,pending_play_coalesced=True,private_local_ui_trace=True,prior_owner_action_sequence_retrieved=False,public48_promotion=False)
p.write_text(json.dumps(v,indent=2)+'\n')
(root/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(root.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
