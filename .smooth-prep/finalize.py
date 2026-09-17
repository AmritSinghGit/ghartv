from pathlib import Path
import json,base64,lzma,hashlib,subprocess
raw=base64.b64decode(''.join(Path('.smooth-prep/part'+str(i)).read_text() for i in range(3)),validate=True)
assert hashlib.sha256(raw).hexdigest()=='93bbf55349556ca169bf609e55999905f5c3103a5274d20a02b7ffc427475d5f'
data=json.loads(lzma.decompress(raw));assert len(data['targets'])==33
names=list(data['targets']);assert set(names)==set(data['baseline'])==set(data['edits'])
for name,expected in data['baseline'].items():
    p=Path(name);assert not p.is_absolute() and '..' not in p.parts
    assert name.startswith(('android-tv/','web-player/','tools/','docs/')) or name in ['CURRENT_HANDOFF.md','GHARTV_LANE_PROGRESS.md','OWNER_REVIEW_REQUIREMENTS.json','REVIEW_060_RC8.md','VALIDATE_SOURCE.command']
    if expected is None:assert not p.exists(),name
    else:assert hashlib.sha256(p.read_bytes()).hexdigest()==expected,name
for name,edits in data['edits'].items():
    p=Path(name);lines=p.read_text().splitlines(keepends=True) if p.exists() else []
    for first,last,text in reversed(edits):
        assert 0<=first<=last<=len(lines);lines[first:last]=[text]
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(''.join(lines))
    assert hashlib.sha256(p.read_bytes()).hexdigest()==data['targets'][name],name
p=Path('web-player/owner-gateway.mjs')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='673217e9659289a04248afaaa21b668c70cfaa2a'
s=p.read_text();needle='  if(await releaseRoute(req,res,url,authorized))return true;';assert s.count(needle)==1
s="import {performanceRoute} from './performance-desk.mjs';\n"+s.replace(needle,'  if(await performanceRoute(req,res,url,authorized))return true;\n'+needle)
assert 'import {providerRoute}' in s;p.write_text(s);names.append(str(p))
p=Path('android-tv/app/src/main/java/in/ghartv/nova/PlaybackComfort.java');s=p.read_text()
old='private final Runnable check=()->{';assert s.count(old)==1
s=s.replace(old,'private final Runnable check=this::prompt;\n    private void prompt(){')
old='h.postDelayed(expire,30000L);\n    };';assert s.count(old)==1
p.write_text(s.replace(old,'h.postDelayed(expire,30000L);\n    }'))
p=Path('docs/owner.html');s=p.read_text();a=s.index("<script>\n(()=>{\n const el=id=>document.getElementById(id),b=el('measureHost')");b=s.index('</script>',a)+len('</script>')
block=Path('.smooth-prep/owner-measure.html').read_text().replace('finally{if(epoch===generation)b.disabled=!local()||!auth;}','finally{b.disabled=!local()||!auth;}')
s=s[:a]+block+s[b:];p.write_text(s)
p=Path('web-player/public/app.js');s=p.read_text()
a=s.index('comfortDialog.addEventListener("close",');b=s.index('\nconst idlePrompt=',a)
s=s[:a]+'''// Save in the submit action, not the asynchronously dispatched dialog close event.
comfortDialog.querySelector("form").addEventListener("submit",event=>{
 event.preventDefault();
 if(event.submitter?.value!=="save"){comfortDialog.close("cancel");return;}
 if(!event.currentTarget.reportValidity())return;
 const n=Number($("stillMinutes").value);if(!Number.isInteger(n)||n<1||n>240)return;
 comfortConfig={enabled:$("stillEnabled").checked,minutes:n,guide:$("stillGuide").checked};
 try{localStorage.setItem("ghartv_comfort_v1",JSON.stringify(comfortConfig));}catch{}
 comfortDialog.close("save");activity();
});'''+s[b:];p.write_text(s)
p=Path('tools/rc8_browser_check.mjs');s=p.read_text()
needle="await page.locator('#lock').click();assert.equal(await page.locator('#feedback').inputValue(),'');";assert s.count(needle)==1
s=s.replace(needle,needle+"assert.equal(await page.locator('#measureHost').isDisabled(),true);assert.equal(await page.locator('#hostMeasureResult').innerText(),'');")
needle="await page.locator('#stillMinutes').fill('1');await page.locator('#saveComfort').click();";assert s.count(needle)==1
s=s.replace(needle,needle+"await page.waitForFunction(()=>!document.querySelector('#comfortSettings').open && JSON.parse(localStorage.getItem('ghartv_comfort_v1')||'{}').minutes===1);")
needle="await page.locator('#stillEnabled').uncheck();await page.locator('#saveComfort').click();";assert s.count(needle)==1
s=s.replace(needle,needle+"await page.waitForFunction(()=>!document.querySelector('#comfortSettings').open && JSON.parse(localStorage.getItem('ghartv_comfort_v1')||'{}').enabled===false);")
p.write_text(s)
p=Path('GHARTV_LANE_PROGRESS.md');s=p.read_text();marker='<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->'
if marker not in s:p.write_text(marker+'\n\n'+s)
Path('/tmp/smooth-targets.json').write_text(json.dumps(names))
print('EXACT_PERFORMANCE_SOURCE=34_FILES; PROVIDER_SOURCE_PRESERVED; CALLBACK_OWNER_AUTH_AND_SYNCHRONOUS_SETTINGS_SAVE_FIXED; NO_PUBLIC_FEED_CHANGE')
