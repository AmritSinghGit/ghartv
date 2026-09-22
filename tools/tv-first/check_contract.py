from pathlib import Path
import json
R=Path(__file__).resolve().parents[2];J=R/'android-tv/app/src/main/java/in/ghartv/nova'
m=(J/'MainActivity.java').read_text();c=(J/'HeroPreviewController.java').read_text();p=(J/'PlaybackComfort.java').read_text()
tv=(J/'TvUi.java').read_text();player=(J/'PlayerActivity.java').read_text();family=(J/'FamilyTheme.java').read_text();api=(J/'JioApiClient.java').read_text()
assert 'Preview 12s' not in m and 'previewNow(' not in c
assert 'PreviewGate.defaultEnabled' in p and 'if(preview.isChecked()!=originalPreview)' in p
assert 'PreviewGate.FOCUS_DELAY_MS' in c and 'PreviewGate.FIRST_FRAME_BUDGET_MS' in c and 'PreviewGate.VISIBLE_PREVIEW_MS' in c
assert 'gate.firstFrame(token)' in c and 'Muted preview · OK to watch' in c
assert c.index('playerView.setVisibility(View.VISIBLE)')<c.index('private void prepare(')
assert 'findContainingViewHolder' in m and 'epoch!=focusEpoch' in m and 'renderGuide(firstGuide)' in m
assert 'PreviewSession' not in m
contract=json.loads((R/'TV_EXPERIENCE_CONTRACT.json').read_text());assert len(contract['rules'])==9
assert 'emulator-5580' in contract['canonical_serial']
print('TV_EXPERIENCE_CONTRACT=PASS_9_RULES_SOURCE_CHECKED')

assert 'pointerTarget(view, true)' in tv and 'TYPE_HAND' in tv and 'ACTION_HOVER_ENTER' in tv
assert 'playerView.setOnClickListener(view -> showGuide(true, nextButton))' in player
assert 'MediaStore.ACTION_PICK_IMAGES' in family and 'ACTION_GET_CONTENT' in family and 'takePersistableUriPermission' in family
assert 'takePlaybackHandoff' in api and 'PLAYBACK_HANDOFF_MS = 20_000L' in api
gradle=(R/'android-tv/app/build.gradle.kts').read_text()
assert 'versionCode = 32' in gradle and '0.6.0-rc10.5-tv-photo-picker' in gradle
print('CODE32_POINTER_PHOTO_PICKER_AND_FAST_OPEN=PASS')

launcher=(R/'tools/owner_review.command.in').read_text()
assert "version_code=30" in launcher and "manifest.get('version_code')!=30" in launcher
assert "int(codes[0])==30" in launcher and "int(codes[0])<30" in launcher
assert 'transport.attach_or_start(sdk,RUN)' in launcher and "sock.bind(('127.0.0.1',port))" not in launcher
assert 'TV_EXPERIENCE_CONTRACT.json' in (R/'tools/package_owner_review.py').read_text()
print('HISTORICAL_CODE30_OPENER_PRESERVED=PASS')

assert 'GHARTV_CYAN_REVIEW_17_HANDOFF' in launcher and 'GHARTV_CYAN_REVIEW_16_HANDOFF' not in launcher
assert 'java.util.Objects.equals(selectedChannel.id,candidate.id)' in m
assert 'channel.number+":"+safe(channel.id)' not in c
print('STABLE_CHANNEL_ID_AND_REVIEW17_LABEL=PASS')
