"""Reuse exact owner sign/open flow; only APK/source identity changes."""
from pathlib import Path
import hashlib,json,re,zipfile
root=Path('/tmp/ghartv-candidate')
assert re.search(r'OK \(160 tests\)',(root/'proof/android-tests.txt').read_text())
s=Path(__file__).with_name('package_review44.py').read_text()
s=s.replace('44','47').replace('0.6.0-rc12.5-loading-mouse-review','0.6.0-rc13.1-cinema-experience-review')
s=s.replace("'actual_android_tests_passed':122","'actual_android_tests_passed':160").replace("'retained_review43_tests':110","'retained_review46_tests':142").replace("'new_review47_tests':12","'new_review47_tests':18")
exec(compile(s,'package_review47_generated','exec'),{'__name__':'__main__'})
v=json.loads((root/'VALIDATION.json').read_text());v.update(owner46_browser_movie_playback_confirmed=True,owner47_acceptance=False,embedded_player_retained=True,native_experiment_not_exposed=True,true_fullscreen_without_fixed_chrome=True,fullscreen_exit_focus_recovery=True,mouse_click_implementation_preserved=True,visit_cache_max_titles=12,visit_cache_ttl_minutes=10,video_prefetch=False,hidden_webview_prefetch=False,extra_video_player_or_controller=False,artwork_preload_first_two_rows=True,actual_network_speedup_measured=False,actual_live_provider_fullscreen_button=False)
(root/'VALIDATION.json').write_text(json.dumps(v,indent=2)+'\n')
(root/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(root.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
print(json.dumps(v))
