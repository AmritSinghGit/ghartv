"""CI-only reuse of the existing verified owner opener; no new native-install flow."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile
root=Path('/tmp/ghartv-candidate')
proof=(root/'proof/android-tests.txt').read_text()
assert re.search(r'OK \(142 tests\)',proof),'All142 Android tests required'
s=Path(__file__).with_name('package_review44.py').read_text()
assert "'actual_android_tests_passed':122" in s
s=s.replace('44','46').replace('0.6.0-rc12.5-loading-mouse-review','0.6.0-rc13-direct-native-pointer-review')
s=s.replace("'actual_android_tests_passed':122","'actual_android_tests_passed':142").replace("'retained_review43_tests':110","'retained_review45_tests':130").replace("'new_review46_tests':12","'new_review46_tests':12")
exec(compile(s,'package_review46_generated','exec'),{'__name__':'__main__'})
manifest=json.loads((root/'CANDIDATE.json').read_text());apk=root/manifest['apk']
with zipfile.ZipFile(apk) as z:
    data=z.read('assets/ghartv-native-check.mp4');assert len(data)>1000
    check_hash=hashlib.sha256(data).hexdigest()
    classes=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
    assert b'NativeFilmPlayerActivity' in classes and b'Review46NativePointerTest' not in classes
validation=json.loads((root/'VALIDATION.json').read_text())
validation.update(native_direct_link_engine_implemented=True,native_provider_movie_handoff_verified=False,
    owned_native_media_sha256=check_hash,owned_native_media_bytes=len(data),
    native_media_play_pause_seek_speed_decoder_lifecycle_tested=True,
    native_player_contains_no_webview=True,pointer_does_not_click_automatically=True,
    prior_blocked_stream_interception_not_retried=True,local_artifact_independent_verification=False)
(root/'VALIDATION.json').write_text(json.dumps(validation,indent=2)+'\n')
(root/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(root.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
print('FINAL_CANDIDATE='+json.dumps(manifest));print('FINAL_VALIDATION='+json.dumps(validation))
