"""Finish the existing Review47 source in CI; never an owner installer.

The previous160-case run caught an omitted fullscreen action and lost Menu arrows.
Repair those production paths, retain all existing assertions, and commit only the
three explicitly checked source files on the same branch. Repeated runs are reads.
"""
from pathlib import Path
import os
import subprocess

BRANCH='codex/ghartv-remove-auto-preview'
def git(*args):
    return subprocess.check_output(['git',*args],text=True).strip()
def substitute(text,before,after):
    assert text.count(before)==1,before[:100]
    return text.replace(before,after,1)

focus=Path('android-tv/app/src/main/java/in/ghartv/nova/FilmPageFocus.java')
activity=Path('android-tv/app/src/main/java/in/ghartv/nova/FlixMomoActivity.java')
controls=Path('android-tv/app/src/main/java/in/ghartv/nova/FilmNativeControls.java')
if 'case "fullscreen":' not in focus.read_text():
    expected={str(focus):'26b575da17e7a8c33fb67ee37c0e731ceef1680e',
              str(activity):'e7a0857acc9d8809748cba14eb73e832178dca02',
              str(controls):'93248eefd6463e2b521370ef23c29ab27b977879'}
    for name,sha in expected.items():
        assert git('hash-object',name)==sha,name+' changed; preserve and reconcile'
    a=focus.read_text()
    a=substitute(a,'switch(action) {case "media":case "commit":',
                   'switch(action) {case "fullscreen":case "media":case "commit":')
    a=substitute(a,"const focusedMediaAction=['media','activate'].includes(action)",
                   "const focusedMediaAction=['media','activate','fullscreen'].includes(action)")
    # Fullscreen is an explicit viewer action, not keyboard input into a form.
    a=substitute(a,"!['watch','watchlist','scan','focus','commit'].includes(action)",
                   "!['watch','watchlist','scan','focus','commit','fullscreen'].includes(action)")
    b=activity.read_text()
    before='            if(fullMenu!=null&&fullMenu.getVisibility()==View.VISIBLE)return super.dispatchKeyEvent(event);'
    after='''            if(fullMenu!=null&&fullMenu.getVisibility()==View.VISIBLE){
                // Fullscreen's custom WebView surface must not receive menu arrows.
                // Resolve them here instead of depending on ViewRoot fallback focus.
                if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                    if(event.getAction()==KeyEvent.ACTION_DOWN){
                        LinearLayout row=(LinearLayout)fullMenu.getChildAt(1);
                        int at=0;for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i).hasFocus()){at=i;break;}
                        if(key==KeyEvent.KEYCODE_DPAD_LEFT)at=Math.max(0,at-1);
                        else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)at=Math.min(row.getChildCount()-1,at+1);
                        row.getChildAt(at).requestFocus();
                    }
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }'''
    b=substitute(b,before,after)
    c=controls.read_text()
    c=substitute(c,'        if("media".equals(action)){hideAll();source.requestFocus();}',
                   '        if("media".equals(action)||"fullscreen".equals(action)){hideAll();source.requestFocus();}')
    # Validate all edits in memory before mutating the checked CI checkout.
    focus.write_text(a);activity.write_text(b);controls.write_text(c)
    subprocess.run(['python','tools/tv-first/check_contract.py'],check=True)
    paths=sorted(expected)
    assert set(git('diff','--name-only').splitlines())==set(paths),'Unexpected source delta'
    assert not git('ls-files','--others','--exclude-standard'),'Unexpected untracked source'
    if os.environ.get('GHARTV_PREPARE_DRY')!='1':
        assert git('ls-remote','origin','refs/heads/'+BRANCH).split()[0]==git('rev-parse','HEAD'),'Concurrent work preserved'
        subprocess.run(['git','add','--',*paths],check=True)
        subprocess.run(['git','config','user.name','GharTV source preparation'],check=True)
        subprocess.run(['git','config','user.email','41898282+github-actions[bot]@users.noreply.github.com'],check=True)
        subprocess.run(['git','commit','-m','fix(review47): route actual fullscreen click and keep remote focus within fullscreen menu'],check=True)
        subprocess.run(['git','push','origin','HEAD:refs/heads/'+BRANCH],check=True)
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'],'a') as out:
        out.write('source_sha='+git('rev-parse','HEAD')+'\n')
