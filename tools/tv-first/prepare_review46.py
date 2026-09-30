#!/usr/bin/env python3
"""CI source preparation on the one existing branch; no owner-machine operation."""
from pathlib import Path
import os, subprocess

def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
def edit(name,before,after):
    p=Path(name);s=p.read_text();assert s.count(before)==1,(name,before[:80]);p.write_text(s.replace(before,after,1));return name

root='android-tv/app/src/main/java/in/ghartv/nova/'
paths=[]
if 'versionCode = 45' in Path('android-tv/app/build.gradle.kts').read_text():
    for name,sha in {
      'android-tv/app/build.gradle.kts':'a0bbf35c4e04b75d1a00d2be59b46967d92d8460',
      'android-tv/app/src/main/AndroidManifest.xml':'c7afd4397c6ce3d5871b37f61190a0b1f7e918f7',
      root+'FlixMomoActivity.java':'21be4e741bba4b2286b9e8ebfda1b598871f1e05',
      root+'RemoteWebCursor.java':'5e0d08617bd111e0a45e079a0bf23d6d22710a5f',
    }.items():assert git('hash-object',name)==sha,name+' baseline changed'
    paths.append(edit('android-tv/app/build.gradle.kts','versionCode = 45\n        versionName = "0.6.0-rc12.6-play-control-evidence-review"','versionCode = 46\n        versionName = "0.6.0-rc13-direct-native-pointer-review"'))
    paths.append(edit('tools/tv-first/check_contract.py',"'versionCode = 45' in gradle and '0.6.0-rc12.6-play-control-evidence-review' in gradle","'versionCode = 46' in gradle and '0.6.0-rc13-direct-native-pointer-review' in gradle"))
    paths.append(edit('android-tv/app/src/main/AndroidManifest.xml','        <activity android:name=".FlixMomoActivity"','        <activity android:name=".NativeFilmPlayerActivity" android:exported="false" android:screenOrientation="landscape" />\n        <activity android:name=".FlixMomoActivity"'))
    cursor=root+'RemoteWebCursor.java'
    edit(cursor,'    private int clickCode=-1;','    private long userSequence;\n    public long placementToken(){return userSequence;}\n    public boolean aimFraction(float x,float y,long token){if(!enabled||token!=userSequence||!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)return false;state.position(x*getWidth(),y*getHeight());invalidate();return true;}\n    private int clickCode=-1;')
    edit(cursor,'        final int key=event.getKeyCode(),dir=direction(key);','        final int key=event.getKeyCode(),dir=direction(key);\n        if(event.getAction()==KeyEvent.ACTION_DOWN)userSequence++;')
    paths.append(cursor)
    native=root+'NativeFilmPlayerActivity.java'
    edit(native,'    private Button pause;','    private Button pause;\n    private AlertDialog speedDialog;')
    edit(native,'new AlertDialog.Builder(this).setTitle("Native playback speed")','speedDialog=new AlertDialog.Builder(this).setTitle("Native playback speed")')
    paths.append(native)
    films=root+'FlixMomoActivity.java'
    edit(films,'        navigation.addView(connectionButton);navigation.addView(diagnosticsButton);chrome.addView(navigation);','''        navigation.addView(connectionButton);navigation.addView(diagnosticsButton);
        Button nativeButton=TvUi.button(this,"Native player",false);
        nativeButton.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("GharTV native player")
            .setMessage("Native playback is available for an explicit supported media link offered by a registered provider. An embedded webpage or pointer click does not supply that link. The current FlixMomo iframe has not been verified for native playback. Engine check plays only an owned test clip; it is not a movie result.")
            .setPositiveButton("Engine check (test clip)",(d,w)->startActivity(NativeFilmPlayerActivity.checkIntent(this)))
            .setNegativeButton("Return",null).show());
        navigation.addView(nativeButton);chrome.addView(navigation);''')
    edit(films,'                if(!request.isForMainFrame()||allowedTop(request.getUrl()))return false;', '''                if(request.isForMainFrame()&&request.hasGesture()&&NativeFilmPlayerActivity.supported(request.getUrl())){
                    // Handle only an explicit user-selected direct media navigation.
                    // No reading of iframe internals or browser media requests.
                    nativeControls.pause();
                    startActivity(new Intent(FlixMomoActivity.this,NativeFilmPlayerActivity.class).putExtra(NativeFilmPlayerActivity.EXTRA_MEDIA,request.getUrl().toString()));
                    return true;
                }
                if(!request.isForMainFrame()||allowedTop(request.getUrl()))return false;''')
    edit(films,'        playMessage("Mouse: arrows move · OK clicks · hold an arrow at an edge to scroll · Back/Menu returns to controls");','''        playMessage("Pointer: arrows move · OK clicks · Back/Menu returns. This operates the embedded player, not the native engine.");
        aimPointerAtMedia();''')
    edit(films,'    private void leaveMouse(boolean controls){', '''    private void aimPointerAtMedia(){
        if(browser==null||custom!=null||!mouseActive)return;
        final WebView page=browser;final String url=page.getUrl();final long token=cursor.placementToken();
        page.postOnAnimation(()->{
            if(!mouseActive||page!=browser)return;
            page.evaluateJavascript(FilmPointerPosition.script(),raw->{
                if(!mouseActive||page!=browser||!java.util.Objects.equals(url,page.getUrl()))return;
                try{Object parsed=new org.json.JSONTokener(raw).nextValue();if(!(parsed instanceof String))return;
                    org.json.JSONObject point=new org.json.JSONObject((String)parsed);
                    if("POSITION_ONLY".equals(point.optString("state")))cursor.aimFraction((float)point.optDouble("x"),(float)point.optDouble("y"),token);
                }catch(Exception ignored){}
            });
        });
    }
    private void leaveMouse(boolean controls){''')
    # User remains in control: a failed automatic tap opens a pointer, never a blind click.
    edit(films,'                controlTrace.emit(FlixMomoActivity.this,action,result);','''                controlTrace.emit(FlixMomoActivity.this,action,result);
                if("TARGET_OBSCURED".equals(result)||"TARGET_CHANGED".equals(result)){
                    handler.post(()->{if(!isFinishing()&&!homeRequested&&!detailRequested&&!mouseActive)enterMouse();});
                }''')
    paths.append(films)
    subprocess.run(['python','tools/tv-first/check_contract.py'],check=True)
    changed=set(git('diff','--name-only').splitlines())
    assert changed==set(paths),(changed,set(paths))
    assert git('ls-remote','origin','refs/heads/codex/ghartv-remove-auto-preview').split()[0]==os.environ['GITHUB_SHA'],'Concurrent work preserved'
    subprocess.run(['git','add','--',*sorted(set(paths))],check=True)
    subprocess.run(['git','config','user.name','GharTV source preparation'],check=True)
    subprocess.run(['git','config','user.email','41898282+github-actions[bot]@users.noreply.github.com'],check=True)
    subprocess.run(['git','commit','-m','feat(ghartv): add direct-link native Media3 player and user-controlled media pointer; no stream extraction'],check=True)
    subprocess.run(['git','push','origin','HEAD:refs/heads/codex/ghartv-remove-auto-preview'],check=True)
with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write('source_sha='+git('rev-parse','HEAD')+'\n')
