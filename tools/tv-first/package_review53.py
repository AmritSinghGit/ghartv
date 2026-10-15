"""Use existing signer and emulator workflow with a bounded private pre-update UI trace."""
from pathlib import Path
import re,json,hashlib
root=Path('/tmp/ghartv-candidate')
assert re.search(r'OK \(299 tests\)',(root/'proof/android-tests.txt').read_text())
s=Path(__file__).with_name('package_review44.py').read_text()
s=s.replace('44','53').replace('0.6.0-rc12.5-loading-mouse-review','0.6.0-rc14.4-network-recovery-review')
s=s.replace("'actual_android_tests_passed':122","'actual_android_tests_passed':299").replace("'retained_review43_tests':110","'retained_review52_tests':278").replace("'new_review53_tests':12","'new_review53_tests':21")
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
entry=entry.replace('def main():\\n', 'def main():\\n    global SERIAL\\n', 1)
anchor_sdk="                sdk=transport.sdk();tools,env=signing_tools(sdk);unsigned=artifact(APK_ASSET,APK_SHA)"
entry=entry.replace(anchor_sdk, "                sdk=transport.sdk();SERIAL=transport.existing_serial(sdk/'platform-tools/adb');transport.SERIAL=SERIAL;R['resolved_emulator_serial']=SERIAL;tools,env=signing_tools(sdk);unsigned=artifact(APK_ASSET,APK_SHA)")
entry=entry.replace("clock_helper=source_module(z,'tools/tv-first/review_clock.py')", "clock_helper=source_module(z,'tools/tv-first/review_clock.py');clock_helper.SERIAL=SERIAL")
entry=entry.replace("capture_helper=source_module(z,'tools/tv-first/collect_review_interactions.py')", "capture_helper=source_module(z,'tools/tv-first/collect_review_interactions.py');capture_helper.SERIAL=SERIAL")
network_anchor="    atomic(RUN/'NETWORK_PROBE.json',json.dumps(network,indent=2).encode())"
network_recovery="""
    recovery_helper=source_module(z,'tools/emulator_network_repair.py')
    recovered=recovery_helper.review_recovery(sdk,SERIAL,RUN,R["run_id"],network,memory_normal,lambda:transport.verify_target(adb))
    R['network_recovery_state']=recovered['state']
    atomic(RUN/'NETWORK_RECOVERY_REVIEW.json',json.dumps(recovered,indent=2).encode())
    print('Existing emulator network: '+recovered['state'],flush=True)
    if recovered['state'] not in ('NO_SHARED_DNS_FAILURE','DNS_AND_JIO_HTTPS_RECOVERED','JIO_HTTPS_REACHABLE'):
        raise Hold('ANDROID_NETWORK_'+recovered['state'])
"""
assert entry.count(network_anchor)==1
entry=entry.replace(network_anchor,network_anchor+network_recovery)
entry=entry.replace("good=R.get('mac_window_observed') is True and", "good=R.get('android_result') in ('REUSED_NORMAL_NOVA_CODE53','OPENED_NORMAL_NOVA_CODE53') and R.get('mac_window_observed') is True and")
'''
assert s.count(anchor)==1;s=s.replace(anchor,injection+anchor)
s=s.replace("('artifact','android_review')","('artifact','android_review','main')")
exec(compile(s,'package_review53_expanded','exec'),{'__name__':'__main__'})
p=root/'VALIDATION.json';v=json.loads(p.read_text());v.update(pointer_idle_ms=5000,immediate_dpad_tap=True,paired_back_menu_keys=True,stale_fallback_guard=True,pending_play_coalesced=True,private_local_ui_trace=True,prior_owner_action_sequence_retrieved=False,published49_unchanged=True,native_provider_route='NOT_ENABLED_NO_DOCUMENTED_AUTHORIZED_STREAM_VERIFIED')
v.update(production_pointer_first=True,parsed_details_not_used_for_movie_availability=True,remote_pointer_default=True,search_all_query_words=True,speech_service_fallback=True,microphone_only_explicit_permission=True,qr_remote_deferred=True,prior_harnesses_explicit_legacy_mode=True,viewer_version_label='Review 53')
v.update(provider_default='https://flixmomo.bet/',registered_origin_persistence=True,catalogue_mode_resumed_after_pointer=True,visible_catalogue_dependency_errors=True,automatic_unregistered_domains=False,live_provider_catalogue_verified=False,real_preview_controller_tests_enabled=True,provider_pointer_loading_recovery=True,shared_dns_recovery_uses_existing_guard=True)
p.write_text(json.dumps(v,indent=2)+'\n')
(root/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(root.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
