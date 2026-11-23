#!/usr/bin/env python3
"""Exact existing-lane UX delta. CI commits expanded source only after verification."""
from pathlib import Path
import os,subprocess

def git(*a): return subprocess.check_output(['git',*a],text=True).strip()
paths=[]
def edit(name,before,after):
    p=Path(name);s=p.read_text();assert s.count(before)==1,(name,before[:100]);p.write_text(s.replace(before,after,1));paths.append(name)
J='android-tv/app/src/main/java/in/ghartv/nova/'
if 'versionCode = 46' in Path('android-tv/app/build.gradle.kts').read_text():
    edit('android-tv/app/build.gradle.kts','versionCode = 46\n        versionName = "0.6.0-rc13-direct-native-pointer-review"','versionCode = 47\n        versionName = "0.6.0-rc13.1-cinema-experience-review"')
    edit('tools/tv-first/check_contract.py',"'versionCode = 46' in gradle and '0.6.0-rc13-direct-native-pointer-review' in gradle","'versionCode = 47' in gradle and '0.6.0-rc13.1-cinema-experience-review' in gradle")
    p=J+'FlixMomoActivity.java'
    edit(p,'    private LinearLayout chrome;','''    private LinearLayout chrome,browseRoot,fullMenu;
    private FrameLayout screen,fullHost;
    private TextView credit;
    private android.app.AlertDialog optionsDialog;
    private final FilmVisitCache visitCache=new FilmVisitCache();
    private boolean closingFullscreen;
    // Retain the experimental engine in source/tests, not normal viewing in47.
    private static final boolean NATIVE_PLAYER_EXPERIMENT=false;''')
    edit(p,'        LinearLayout root=new LinearLayout(this);root.setOrientation','        screen=new FrameLayout(this);\n        LinearLayout root=new LinearLayout(this);browseRoot=root;root.setOrientation')
    edit(p,'"GharTV Discover / FlixMomo · Review "','"GharTV Films · Review "')
    begin='        LinearLayout navigation=new LinearLayout(this);'
    end='        root.addView(chrome);'
    s=Path(p).read_text();fragment=s[s.index(begin):s.index(end)]
    edit(p,fragment,'''        pageButton=TvUi.button(this,"Options",false);row.addView(pageButton);
        // Detached compatibility references are not duplicate on-screen controls.
        modeButton=new Button(this);scrollButton=new Button(this);retryButton=new Button(this);
        help=TvUi.label(this,"",12,TvUi.MUTED,false);
        status=TvUi.label(this,"",12,TvUi.MUTED,false);
        pageButton.setOnClickListener(v->showOptions());
''')
    edit(p,'        TextView credit=TvUi.label(this,FilmProviderPolicy.CREDIT','        credit=TvUi.label(this,FilmProviderPolicy.CREDIT')
    edit(p,'stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);','''stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        fullHost=new FrameLayout(this);fullHost.setBackgroundColor(android.graphics.Color.BLACK);fullHost.setVisibility(View.GONE);screen.addView(fullHost,new FrameLayout.LayoutParams(-1,-1));setContentView(screen);''')
    edit(p,'            public void searchToolbar(){toolbar();query.requestFocus();}','            public void searchToolbar(){toolbar();pageButton.requestFocus();}\n            public void fullscreen(){nativeControls.pageAction("fullscreen");}')
    edit(p,'public boolean pageIsVisible(){return !homeRequested&&!detailRequested;}','public boolean pageIsVisible(){return !homeRequested&&!detailRequested&&custom==null;}')
    edit(p,'public void pageObserved(org.json.JSONObject data){if(homeRequested','public void pageObserved(org.json.JSONObject data){visitCache.put(data.optJSONObject("detail"));if(homeRequested')
    edit(p,'                if("TARGET_OBSCURED".equals(result)||"TARGET_CHANGED".equals(result)){','                if(("fullscreen".equals(action)&&!"CLICK_SENT_NOT_PLAYBACK_PROOF".equals(result)&&!result.startsWith("TARGET_"))||"TARGET_OBSCURED".equals(result)||"TARGET_CHANGED".equals(result)){')
    edit(p,'                com.bumptech.glide.Glide.get(FlixMomoActivity.this).clearMemory();','                visitCache.clear();com.bumptech.glide.Glide.get(FlixMomoActivity.this).clearMemory();')
    edit(p,'        pageButton.setOnClickListener(v->usePage());','        pageButton.setOnClickListener(v->showOptions());')
    edit(p,'if(request.isForMainFrame()&&request.hasGesture()&&NativeFilmPlayerActivity.supported(request.getUrl()))','if(NATIVE_PLAYER_EXPERIMENT&&request.isForMainFrame()&&request.hasGesture()&&NativeFilmPlayerActivity.supported(request.getUrl()))')
    edit(p,'@Override public void onShowCustomView(View view,CustomViewCallback callback){if(custom!=null){callback.onCustomViewHidden();return;}cursor.cancel();custom=view;customCallback=callback;chrome.setVisibility(View.GONE);browser.setVisibility(View.GONE);stage.addView(view,new FrameLayout.LayoutParams(-1,-1));cursor.target(view);cursor.enter();view.requestFocus();}','@Override public void onShowCustomView(View view,CustomViewCallback callback){enterFullScreen(view,callback);}')
    edit(p,'        playMessage("Pointer: arrows move · OK clicks · Back/Menu returns. This operates the embedded player, not the native engine.");','        playMessage("Mouse: arrows move · OK clicks · Menu returns to controls");')
    edit(p,'detailPanel.begin(url,card);navigate(url,false);','detailPanel.begin(url,card);detailPanel.preview(visitCache.get(url));navigate(url,false);')
    edit(p,'    private void playMessage(String text){playNotice.setText(text);playNotice.setVisibility(View.VISIBLE);}','    private void playMessage(String text){playNotice.setText(text);playNotice.setVisibility(custom==null&&homeRequested?View.VISIBLE:View.GONE);}')
    edit(p,'    private void toolbar(){chrome.setVisibility(View.VISIBLE);cursor.leave();if(custom!=null)exitFullScreen();pageButton.requestFocus();}','''    private void toolbar(){
        if(custom!=null){exitFullScreen();return;}
        if(mouseActive)leaveMouse(false);nativeControls.hideAll();cursor.enable(false);
        chrome.setVisibility(View.VISIBLE);credit.setVisibility(View.VISIBLE);playNotice.setVisibility(View.GONE);
        pageButton.requestFocus();
    }
    private void showOptions(){
        if(custom!=null){showFullscreenMenu();return;}
        if(mouseActive)leaveMouse(false);nativeControls.hideAll();cursor.enable(false);chrome.setVisibility(View.VISIBLE);
        String[] items={"Return to video / website","Connection details","Diagnostics","Privacy & attribution"};
        optionsDialog=new android.app.AlertDialog.Builder(this).setTitle("GharTV · FlixMomo")
            .setMessage(null).setItems(items,(d,n)->{
                if(n==0)usePage();else if(n==1)showFilmConnection();else if(n==2)DiagnosticsDialog.show(this);else ReviewNotice.show(this,()->{visitCache.clear();if(browser!=null){browser.clearCache(true);browser.clearHistory();}com.bumptech.glide.Glide.get(this).clearMemory();homePanel.discardSuggestions();homeRequested=true;detailRequested=false;detailPanel.setVisibility(View.GONE);homePanel.setVisibility(View.VISIBLE);chrome.setVisibility(View.VISIBLE);homePanel.unavailable("Local film data cleared. Choose Refresh to load again.");});
            }).setNegativeButton("Close",null).create();
        optionsDialog.setOnDismissListener(d->{if(custom==null&&chrome.getVisibility()==View.VISIBLE)pageButton.requestFocus();});optionsDialog.show();
    }
    private void moveCursor(FrameLayout parent){
        cursor.cancel();android.view.ViewParent old=cursor.getParent();if(old instanceof android.view.ViewGroup)((android.view.ViewGroup)old).removeView(cursor);
        parent.addView(cursor,new FrameLayout.LayoutParams(-1,-1));
    }
    void enterFullScreen(View view,WebChromeClient.CustomViewCallback callback){
        if(custom!=null||isFinishing()){callback.onCustomViewHidden();return;}
        nativeControls.pause();nativeControls.hideAll();handler.removeCallbacks(playDeadline);playUntil=0;
        custom=view;customCallback=callback;browseRoot.setVisibility(View.GONE);browser.setVisibility(View.GONE);
        fullHost.setVisibility(View.VISIBLE);fullHost.addView(view,new FrameLayout.LayoutParams(-1,-1));
        moveCursor(fullHost);cursor.target(view);mouseActive=true;cursor.enable(true);cursor.center();cursor.enter();
        fullMenu=new LinearLayout(this);fullMenu.setOrientation(LinearLayout.VERTICAL);fullMenu.setPadding(16,10,16,12);fullMenu.setBackgroundColor(0xee071e29);
        fullMenu.addView(TvUi.label(this,"GharTV · Playback by FlixMomo",14,TvUi.MINT,true));
        LinearLayout buttons=new LinearLayout(this);fullMenu.addView(buttons);
        String[] labels={"Resume","Sources","Mouse","Exit fullscreen"};
        for(int i=0;i<labels.length;i++){final int item=i;Button b=TvUi.button(this,labels[i],i==0);b.setId(View.generateViewId());buttons.addView(b,new LinearLayout.LayoutParams(0,TvUi.dp(this,48),1));b.setOnClickListener(v->{
            if(item==0||item==2){fullMenu.setVisibility(View.GONE);mouseActive=true;cursor.enable(true);cursor.target(custom);cursor.enter();custom.requestFocus();}
            else if(item==1){exitFullScreen();usePage();nativeControls.openSources();}
            else exitFullScreen();
        });}
        for(int i=0;i<buttons.getChildCount();i++){View b=buttons.getChildAt(i);b.setNextFocusLeftId(buttons.getChildAt(Math.max(0,i-1)).getId());b.setNextFocusRightId(buttons.getChildAt(Math.min(buttons.getChildCount()-1,i+1)).getId());b.setNextFocusUpId(b.getId());b.setNextFocusDownId(b.getId());}
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM);fullHost.addView(fullMenu,fp);fullMenu.setVisibility(View.GONE);
        view.requestFocus();TvUi.immersive(this);
    }
    private void showFullscreenMenu(){
        if(custom==null)return;mouseActive=false;cursor.enable(false);fullMenu.setVisibility(View.VISIBLE);fullMenu.bringToFront();((LinearLayout)fullMenu.getChildAt(1)).getChildAt(0).requestFocus();
    }
''')
    edit(p,'    @Override public boolean dispatchKeyEvent(KeyEvent event){\n        if(mouseActive){','''    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(custom!=null){
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)exitFullScreen();return true;}
            if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)showFullscreenMenu();return true;}
            if(fullMenu!=null&&fullMenu.getVisibility()==View.VISIBLE)return super.dispatchKeyEvent(event);
            if(cursor.handle(event))return true;
            return super.dispatchKeyEvent(event);
        }
        // Native toolbar focus is authoritative. Mouse/page navigation must not consume its keys.
        if(chrome.getVisibility()==View.VISIBLE&&chrome.hasFocus()){
            if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)usePage();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)showOptions();return true;}
            if(!homeRequested)return super.dispatchKeyEvent(event);
        }
        if(mouseActive){''')
    old='    private void exitFullScreen(){if(custom==null)return;cursor.cancel();stage.removeView(custom);custom=null;chrome.setVisibility(View.VISIBLE);if(browser!=null){browser.setVisibility(View.VISIBLE);cursor.target(browser);}WebChromeClient.CustomViewCallback callback=customCallback;customCallback=null;if(callback!=null)callback.onCustomViewHidden();}'
    edit(p,old,'''    private void exitFullScreen(){
        if(custom==null||closingFullscreen)return;closingFullscreen=true;
        View old=custom;WebChromeClient.CustomViewCallback callback=customCallback;custom=null;customCallback=null;
        cursor.cancel();fullHost.removeView(old);if(fullMenu!=null)fullHost.removeView(fullMenu);fullMenu=null;
        moveCursor(stage);fullHost.setVisibility(View.GONE);browseRoot.setVisibility(View.VISIBLE);
        mouseActive=false;cursor.enable(false);modeButton.setText("Cursor: off");
        if(browser!=null){browser.setVisibility(View.VISIBLE);cursor.target(browser);}
        if(callback!=null)callback.onCustomViewHidden();closingFullscreen=false;
        if(!isFinishing()&&!isDestroyed()){nativeControls.resume();toolbar();}
    }''')
    edit(p,'@Override public void onBackPressed(){if(mouseActive)','@Override public void onBackPressed(){if(custom!=null){exitFullScreen();return;}if(mouseActive)')
    edit(p,'    private void usePage(){detailRequested=false;','    private void usePage(){credit.setVisibility(View.GONE);networkNotice.setVisibility(View.GONE);playNotice.setVisibility(View.GONE);detailRequested=false;')
    edit(p,'    private void showHome(boolean force){if(mouseActive)','    private void showHome(boolean force){credit.setVisibility(View.VISIBLE);if(mouseActive)')
    edit(p,'@Override protected void onResume(){super.onResume();EngagementTracker.start(this,"discover");if(nativeControls!=null&&!mouseActive)','@Override protected void onResume(){super.onResume();EngagementTracker.start(this,"discover");if(nativeControls!=null&&!mouseActive&&custom==null)')
    p=J+'FilmNativeControls.java'
    edit(p,'        default void mouse(){}','        default void mouse(){} default void fullscreen(){}')
    edit(p,'playButton=add(row,"Play",this::playDefault);add(row,"Players",this::showPlayers);add(row,"Watchlist",()->host.pageAction("watchlist"));add(row,"Not playing",()->next(true));','playButton=add(row,"Play",this::playDefault);add(row,"Sources",this::showPlayers);add(row,"Fullscreen",host::fullscreen);')
    edit(p,'add(row,"Search",()->{hideAll();host.searchToolbar();});add(row,"Mouse",()->{hideAll();host.mouse();});','add(row,"Mouse",()->{hideAll();host.mouse();});add(row,"More",()->{hideAll();host.searchToolbar();});')
    edit(p,'    private void showPlayers(){','    void openSources(){showPlayers();}\n    private void showPlayers(){')
    edit(p,'        add(choices,autoNext?', '        if(players.length()>1)add(choices,"Try next source",()->next(true));\n        add(choices,autoNext?')
    p=J+'FilmPageFocus.java'
    edit(p,"        if(action==='media'){",'''        if(action==='fullscreen'){
          const named=candidates.filter(e=>/^(full ?screen|enter full ?screen|expand player)$/i.test(buttonText(e)));
          return named.length===1?focus(candidates.indexOf(named[0]),true):pack({state:named.length?'AMBIGUOUS_MEDIA':'NOT_FOUND'});
        }
        if(action==='media'){''')
    p=J+'FilmHomeView.java'
    edit(p,'Math.min(columns,posterTasks.size())','Math.min(columns*2,posterTasks.size())')
    edit(p,'The first row without waiting','The first two rows without waiting') if 'The first row without waiting' in Path(p).read_text() else None
    edit(p,'        content.addView(TvUi.label(a,"Suggestions by FlixMomo · GharTV Review "+BuildConfig.VERSION_CODE,13,TvUi.MINT,true));','')
    p=J+'FilmDetailView.java'
    edit(p,'    void followNavigation(String target){','''    void preview(JSONObject cached){
        if(cached==null)return;render(cached);waiting=true;watch.setEnabled(false);watchlist.setEnabled(false);progress.setVisibility(VISIBLE);
        subtitle.setText("FlixMomo · details from this visit, refreshing");status.setText("Checking the current Watch action…");
    }
    void followNavigation(String target){''')
    p=J+'RemoteWebCursor.java'
    edit(p,'    private final Runnable frame=()->tick();','    private final Runnable frame=()->tick();\n    private final Runnable idleHide=()->{if(!pressed&&!state.moving())setVisibility(INVISIBLE);};')
    edit(p,'public void enter(){if(enabled&&target!=null){setVisibility(VISIBLE);bringToFront();invalidate();}}','public void enter(){if(enabled&&target!=null){removeCallbacks(idleHide);setVisibility(VISIBLE);bringToFront();invalidate();postDelayed(idleHide,2500);}}')
    edit(p,'        state.stop();removeCallbacks(frame);scheduled=false;','        state.stop();removeCallbacks(frame);removeCallbacks(idleHide);scheduled=false;')
    edit(p,'        final int key=event.getKeyCode(),dir=direction(key);','        removeCallbacks(idleHide);postDelayed(idleHide,2500);\n        final int key=event.getKeyCode(),dir=direction(key);')
    subprocess.run(['python','tools/tv-first/check_contract.py'],check=True)
    if os.environ.get('GHARTV_PREPARE_DRY')!='1':
        assert git('ls-remote','origin','refs/heads/codex/ghartv-remove-auto-preview').split()[0]==os.environ['GITHUB_SHA'],'Concurrent work preserved'
        assert set(git('diff','--name-only').splitlines())==set(paths),'Unexpected staged source'
        subprocess.run(['git','add','--',*sorted(set(paths))],check=True)
        subprocess.run(['git','config','user.name','GharTV verified source preparation'],check=True)
        subprocess.run(['git','config','user.email','41898282+github-actions[bot]@users.noreply.github.com'],check=True)
        subprocess.run(['git','commit','-m','feat(ghartv): polished embedded cinema, recoverable controls and bounded visit preload'],check=True)
        subprocess.run(['git','push','origin','HEAD:refs/heads/codex/ghartv-remove-auto-preview'],check=True)
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'],'a') as out:out.write('source_sha='+git('rev-parse','HEAD')+'\n')
