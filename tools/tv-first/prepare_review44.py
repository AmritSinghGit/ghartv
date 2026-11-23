#!/usr/bin/env python3
"""Same-branch Review44 UI fixes. No stream extraction or provider security changes."""
from pathlib import Path
import hashlib
R=Path(__file__).resolve().parents[2]; J=R/'android-tv/app/src/main/java/in/ghartv/nova'
EXPECTED={
'FlixMomoActivity.java':('3c1dd5bab2c6fcaa98ba3a6fb50a871f06f587d3080a278b4aaf1f73ac8ca82b','9a7844fb6e1574de367481de14da3725d2823b86beae205647a17e9884dd3024'),
'FilmDetailView.java':('a2f2040046b738621d4b7e499ed86542c501556a51d4917a6772ee497f16d9f1','8e881f00bb6416fdeae99a3af18ba6bce49a6b179bec1faf149e4bd108830cd1'),
'FilmHomeView.java':('80f6ff32a5e18047ea2024897aa732f83a73cd589dda5bf316dffeca13ff2760','cefd7e729e6dabf4598a9fcabd182ecbe7568bca75c78a51cf2859e1a3c21a5e'),
'FilmNativeControls.java':('2f0c081e4488af3add2aba33d7e939f6ce392acba72352e8e456d732136978d6','41678d1240ac45c223f4ea8d2f89e3c020c35ae6e0a62acc48fb00f9ce225ce5'),
'RemoteWebCursor.java':('e81f2136680219c257b30ac1d3ba2901cea9c99c52bdefa9f3587550f867215c','33d20174f9680d3ed179b595254617dd4ae38eb90553ca822ba224ca7b46a333')}
def digest(b):return hashlib.sha256(b).hexdigest()
if all(digest((J/n).read_bytes())==after for n,(before,after) in EXPECTED.items()):raise SystemExit(0)
for n,(before,after) in EXPECTED.items():assert digest((J/n).read_bytes())==before,n+' changed; preserve newer source'
def mod(name,changes):
 p=J/name;s=p.read_text()
 for old,new in changes:
  assert old in s,(name,old[:80]);s=s.replace(old,new,1)
 assert digest(s.encode())==EXPECTED[name][1],name+' final hash differs'
 p.write_text(s)
mod('FilmHomeView.java',[
 ('private final TextView status;', 'private final TextView status;\n    private final android.widget.ProgressBar progress;\n    private boolean loadingData;\n    private final Runnable loadingDeadline=()->{if(loadingData)unavailable("The provider is taking longer to supply suggestions.");};'),
 ('@Override protected void onSizeChanged', '@Override protected void onAttachedToWindow(){super.onAttachedToWindow();schedulePosters();}\n    @Override protected void onVisibilityChanged(View changed,int visible){super.onVisibilityChanged(changed,visible);if(visible==VISIBLE)schedulePosters();}\n    @Override protected void onDetachedFromWindow(){removeCallbacks(loadingDeadline);super.onDetachedFromWindow();}\n    @Override protected void onSizeChanged'),
 ('void focusRefresh(){if(!actions.isEmpty())focusVisible(actions.get(0));}', 'void focusRefresh(){for(View a:actions)if(a.isEnabled()){focusVisible(a);return;}}'),
 ('focusVisible(target);return true;', 'if(!target.isEnabled()){if(key==KeyEvent.KEYCODE_DPAD_DOWN&&!cards.isEmpty())target=cards.get(0);else {focusRefresh();return true;}}\n        focusVisible(target);return true;'),
 ('content.addView(actionRow);\n        status=', 'content.addView(actionRow);\n        progress=new android.widget.ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(GONE);progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);content.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));\n        status='),
 ('grid=new LinearLayout(a);grid.setOrientation(LinearLayout.VERTICAL);content.addView(grid);', 'grid=new LinearLayout(a);grid.setOrientation(LinearLayout.VERTICAL);content.addView(grid);\n        // Child rows can be added after this outer panel was already laid out.\n        // A scroll must not be necessary to trigger the first image requests.\n        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->schedulePosters());'),
 ('void loading(){status.setText(cardCount==0?"Loading suggestions from the provider…":"Refreshing provider suggestions…");}', 'void loading(){loadingData=true;progress.setVisibility(VISIBLE);if(!actions.isEmpty()){actions.get(0).setEnabled(false);actions.get(1).setEnabled(false);}status.setText(cardCount==0?"Loading FlixMomo suggestions… Please wait; you can still search or leave.":"Refreshing FlixMomo suggestions… Your existing cards remain available.");removeCallbacks(loadingDeadline);postDelayed(loadingDeadline,15000);}\n    private void ready(){loadingData=false;removeCallbacks(loadingDeadline);progress.setVisibility(GONE);if(!actions.isEmpty()){actions.get(0).setEnabled(true);actions.get(1).setEnabled(true);}}\n    boolean isLoadingData(){return loadingData;}'),
 ('void unavailable(String reason){status.setText', 'void unavailable(String reason){ready();status.setText'),
 ('if(allowed.isEmpty()){unavailable("The current page has not supplied any readable poster suggestions yet.");return;}', 'if(allowed.isEmpty()){if(loadingData)return;unavailable("The current page has not supplied any readable poster suggestions yet.");return;}\n        ready();'),
 ('wireFocus();schedulePosters();\n        status.setText', 'wireFocus();schedulePosters();\n        // Start the first row without waiting for measured child geometry.\n        // All remaining rows still use bounded viewport scheduling.\n        if(isAttachedToWindow()&&isShown())for(int i=0;i<Math.min(columns,posterTasks.size());i++){PosterTask task=posterTasks.get(i);if(!task.loaded){task.loaded=true;loader.load(task.view,task.url);}}\n        status.setText')])
mod('FilmNativeControls.java',[
 ('default boolean pageIsVisible(){return true;}', 'default void mouse(){}\n        default boolean pageIsVisible(){return true;}'),
 ('add(row,"Use page",()->{hideAll();host.usePage();});add(row,"Hide",this::hideTray);', 'add(row,"Mouse",()->{hideAll();host.mouse();});add(row,"Hide",this::hideTray);'),
 ('private void showTray(boolean focus)', 'void refreshState(){probeCount=0;finished();}\n    private void showTray(boolean focus)')])
mod('RemoteWebCursor.java',[
 ('public boolean enabled(){return enabled;}', 'public boolean enabled(){return enabled;}\n    public void center(){cancel();state.position(getWidth()/2f,getHeight()/2f);invalidate();}')])
mod('FlixMomoActivity.java',[
 ('private boolean mainFrameError,pageReady,loading;', 'private boolean mainFrameError,pageReady,loading,mouseActive;'),
 ('public void searchToolbar(){toolbar();query.requestFocus();}', 'public void searchToolbar(){toolbar();query.requestFocus();}\n            public void mouse(){enterMouse();}'),
 ('private void showHome(boolean force){', 'private void enterMouse(){\n        mouseActive=true;playUntil=0;nativeControls.pause();nativeControls.hideAll();usePage();\n        cursor.scrollMode(false);cursor.enable(true);cursor.center();cursor.enter();modeButton.setText("Cursor: on");\n        playMessage("Mouse: arrows move · OK clicks · hold an arrow at an edge to scroll · Back/Menu returns to controls");\n    }\n    private void leaveMouse(boolean controls){mouseActive=false;cursor.enable(false);modeButton.setText("Cursor: off");nativeControls.resume();if(controls)nativeControls.menu();}\n    private void showHome(boolean force){if(mouseActive)leaveMouse(false);'),
 ('private void openDetail(String url){', 'private void openDetail(String url){\n        if(mouseActive)leaveMouse(false);'),
 ('// Retained cards are immediately reviewable. Do not reread a title as search suggestions.\n        if(!nativeSearch.isEmpty()){query.setText(nativeSearch);navigate(providerOrigin()+"/search?q="+Uri.encode(nativeSearch),false);}else navigate(providerOrigin()+"/",false);', '// Already-observed cards remain usable on Back, without another provider-page load.\n        // Selecting a card or explicitly refreshing still navigates to that current source.\n        if(homePanel.cardCount()==0){homePanel.loading();navigate(providerOrigin()+(!nativeSearch.isEmpty()?"/search?q="+Uri.encode(nativeSearch):"/"),false);}'),
 ('private void search(){playUntil=0;', 'private void search(){if(mouseActive)leaveMouse(false);playUntil=0;'),
 ('@Override public boolean dispatchKeyEvent(KeyEvent event){', '@Override public boolean dispatchKeyEvent(KeyEvent event){\n        if(mouseActive){\n            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU||event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)leaveMouse(true);return true;}\n            if(cursor.handle(event))return true;\n        }'),
 ('@Override public void onBackPressed(){if(detailRequested)', '@Override public void onBackPressed(){if(mouseActive){leaveMouse(true);return;}if(detailRequested)'),
 ('@Override protected void onResume(){super.onResume();if(nativeControls!=null)nativeControls.resume();', '@Override protected void onResume(){super.onResume();if(nativeControls!=null&&!mouseActive)nativeControls.resume();'),
 ('@Override public void onPageStarted(WebView view,String url,Bitmap icon){', '@Override public void onPageCommitVisible(WebView view,String url){if(!mainFrameError&&allowedTop(Uri.parse(url))&&!mouseActive)nativeControls.finished();}\n            @Override public void onPageStarted(WebView view,String url,Bitmap icon){'),
 ('status.setText("Original page loaded · artwork stays here · Menu shows GharTV controls");nativeControls.finished();', 'status.setText(homeRequested?"Loading FlixMomo suggestions…":detailRequested?"Loading title information…":"Original page loaded · Menu shows GharTV controls");if(!mouseActive)nativeControls.finished();'),
 ('public void provider(){homeRequested=false;homePanel.setVisibility(View.GONE);usePage();}', 'public void provider(){homeRequested=false;homePanel.setVisibility(View.GONE);String destination=providerOrigin()+(nativeSearch.isEmpty()?"/":"/search?q="+Uri.encode(nativeSearch));navigate(destination,true);}')])
mod('FilmDetailView.java',[
 ('private final ImageView poster;private final ScrollView copy;', 'private final ImageView poster;private final ScrollView copy;private final ProgressBar progress;private boolean waiting;'),
 ('info.addView(subtitle);', 'info.addView(subtitle);progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(GONE);info.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));'),
 ('void begin(String target,JSONObject card){url=target;', 'void begin(String target,JSONObject card){waiting=true;progress.setVisibility(VISIBLE);url=target;'),
 ('facts.setText("");status.setText("Loading details. Original page is available at any time.");', 'facts.setText(card==null?"":card.optString("metadata"));status.setText("Loading this title from FlixMomo… Watch is enabled when its action is available.");'),
 ('String name=value.optString("title");if(name.length()<2||name.length()>180)return;', 'String name=value.optString("title");if(name.length()<2||name.length()>180)return;waiting=false;progress.setVisibility(GONE);'),
 ('void unavailable(String reason){status.setText', 'void unavailable(String reason){waiting=false;progress.setVisibility(GONE);status.setText')])
for name in ('android-tv/app/build.gradle.kts','tools/tv-first/check_contract.py'):
 p=R/name;p.write_text(p.read_text().replace('versionCode = 43','versionCode = 44').replace('0.6.0-rc12.4-page-performance-review','0.6.0-rc12.5-loading-mouse-review'))
p=R/'tools/tv-first/package_review43.py';s=p.read_text().replace('43','44').replace('0.6.0-rc12.4-page-performance-review','0.6.0-rc12.5-loading-mouse-review').replace('110','122').replace("'retained_review42_tests':100,'new_review44_tests':10","'retained_review43_tests':110,'new_review44_tests':12")
s=s.replace("'native_film_handoff_implemented':False","'native_film_handoff_implemented':False,'explicit_mouse_fallback':True,'first_row_loads_without_scroll':True,'honest_loading_state':True")
assert digest(s.encode())=='20dbc27b93277f8f0619b6bdba8129960c4a3eaed0e49398b555d61971fbf44a'
(R/'tools/tv-first/package_review44.py').write_text(s)
