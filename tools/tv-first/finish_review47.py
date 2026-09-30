from pathlib import Path
import os,subprocess

def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
paths=[]
def change(p,before,after):
 p=Path(p);s=p.read_text();assert s.count(before)==1,(str(p),before[:60]);p.write_text(s.replace(before,after,1));paths.append(str(p))
p='android-tv/app/src/main/java/in/ghartv/nova/FlixMomoActivity.java'
if 'String[] labels={"Resume","Sources","Mouse","Exit fullscreen"}' in Path(p).read_text():
 change(p,'String[] labels={"Resume","Sources","Mouse","Exit fullscreen"}','String[] labels={"Resume","Sources","Exit fullscreen"}')
 change(p,'if(item==0||item==2){fullMenu.setVisibility','if(item==0){fullMenu.setVisibility')
 change(p,'if(browser!=null){browser.clearCache(true);browser.clearHistory();}com.bumptech.glide.Glide.get(this).clearMemory();homePanel.discardSuggestions();','if(browser!=null){browser.stopLoading();browser.loadUrl("about:blank");browser.clearCache(true);browser.clearHistory();browser.clearFormData();}nativeControls.failure();com.bumptech.glide.Glide.get(this).clearMemory();homePanel.discardSuggestions();')
 change(p,'            if(cursor.handle(event))return true;\n            return super.dispatchKeyEvent(event);','''            if(cursor.handle(event)){
                if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER))controlTrace.emit(this,"mouse","MOUSE_CLICK_SENT");
                return true;
            }
            return super.dispatchKeyEvent(event);''')
 t='android-tv/app/src/androidTest/java/in/ghartv/nova/Review47ViewingTest.java'
 change(t,'    @Test public void trueFullscreenHidesAllBrowseCreditAndNotices()', '''    void proof(String name)throws Exception{
        android.graphics.Bitmap shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if(shot==null)throw new AssertionError("No test screenshot");
        java.io.File target=new java.io.File(h.activity.getExternalFilesDir(null),name);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(target)){if(!shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new AssertionError("Screenshot failed");}finally{shot.recycle();}
    }
    @Test public void trueFullscreenHidesAllBrowseCreditAndNotices()''')
 change(t,'assertTrue("A trusted click must enter actual provider fullscreen",yes[0]);','assertTrue("A trusted click must enter actual provider fullscreen",yes[0]);SystemClock.sleep(300);proof("review47-owned-fullscreen.png");h.press(KeyEvent.KEYCODE_MENU);proof("review47-fullscreen-menu.png");h.press(KeyEvent.KEYCODE_BACK);proof("review47-toolbar-return.png");')
 r='REVIEW47.md';change(r,'Resume, Sources, Mouse and Exit fullscreen','Resume, Sources and Exit fullscreen')
 subprocess.run(['python','tools/tv-first/check_contract.py'],check=True)
 if os.environ.get('GHARTV_PREPARE_DRY')!='1':
  assert git('ls-remote','origin','refs/heads/codex/ghartv-remove-auto-preview').split()[0]==git('rev-parse','HEAD'),'Concurrent source preserved'
  assert set(git('diff','--name-only').splitlines())==set(paths)
  subprocess.run(['git','add','--',*sorted(set(paths))],check=True)
  subprocess.run(['git','config','user.name','GharTV source preparation'],check=True)
  subprocess.run(['git','config','user.email','41898282+github-actions[bot]@users.noreply.github.com'],check=True)
  subprocess.run(['git','commit','-m','fix(review47): deduplicate fullscreen controls and preserve private clear-data pause'],check=True)
  subprocess.run(['git','push','origin','HEAD:refs/heads/codex/ghartv-remove-auto-preview'],check=True)
if os.environ.get('GITHUB_OUTPUT'):
 with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write('source_sha='+git('rev-parse','HEAD')+'\n')
