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
assert 'versionCode = 38' in gradle and '0.6.0-rc11.1-focus-filter-review' in gradle
films=(J/'FlixMomoActivity.java').read_text();cursor=(J/'RemoteWebCursor.java').read_text();controls=(J/'FilmNativeControls.java').read_text()
assert 'cursor.handle(event)' in films and 'onRenderProcessGone' in films and 'handler.cancel()' in films
assert 'onPageFinished' in films and 'if(mainFrameError)' in films
assert 'postOnAnimation(frame)' in cursor and 'ACTION_CANCEL' in cursor and 'removeCallbacks(frame)' in cursor
assert 'evaluateJavascript' not in films and 'addJavascriptInterface' not in films
assert 'renderResults' not in controls and 'resultsView' not in controls
assert 'posterKey(event)' in films and 'BuildConfig.VERSION_CODE' in films
assert (J/'FilmPosterNavigation.java').is_file()
assert 'RecognizerIntent.ACTION_RECOGNIZE_SPEECH' in (J/'UnifiedSearch.java').read_text()
launcher=(R/'tools/owner_review.command.in').read_text()
assert "version_code=30" in launcher and "manifest.get('version_code')!=30" in launcher
assert 'transport.attach_or_start(sdk,RUN)' in launcher
assert 'TV_EXPERIENCE_CONTRACT.json' in (R/'tools/package_owner_review.py').read_text()
assert 'java.util.Objects.equals(selectedChannel.id,candidate.id)' in m
assert 'channel.number+":"+safe(channel.id)' not in c
print('CODE38_ORIGINAL_POSTERS_NO_TEXT_TILE_SURFACE_AND_EXISTING_TV_BEHAVIOR=PASS')

manifest=(R/'android-tv/app/src/main/AndroidManifest.xml').read_text()
assert not (J/'MovieHubActivity.java').exists()
assert 'MovieHubActivity' not in manifest and 'Punjabi +' not in m
assert 'UnifiedSearch.voice(this)' in m and 'UnifiedSearch.open(this,q)' in m
assert 'UnifiedSearch.dialog(this)' in player and 'UnifiedSearch.voice(this)' in player
assert 'FilmHomeView' in films and 'showLiveResults(text)' in films
assert 'FilmPageFocus.script' in controls and 'pageKey(event)' in films
assert 'FilmHomeView' in (J/'FilmHomeView.java').read_text()
assert 'Tor is not enabled' in (J/'ReviewNotice.java').read_text()
assert json.loads((R/'RELEASE_HOLD.json').read_text())['public_feed_write'] is False
print('CODE38_HOME_SHARED_SEARCH_DETAIL_FOCUS_PUNJABI_PLUS_REMOVAL_RELEASE_HOLD=PASS')

assert 'searchQuery="";selectedChannel=null;startAtFirst=true;' in m
assert 'chipAdapter = new ChipAdapter(this::selectGuideCategory)' in m
assert 'actionButton("Voice")' not in m
assert 'FOCUS_BLOCK_DESCENDANTS' in (J/'FilmHomeView.java').read_text()
assert 'homePanel.handleRemote(event,getCurrentFocus())' in films
