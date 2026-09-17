// Focused browser checks; synthetic schedules and local receipts, never owner/provider credentials.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp,mkdir,writeFile,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE||'/tmp/ghartv-browser/node_modules/playwright/index.mjs');
const home=await mkdtemp(join(tmpdir(),'ghartv-rc8-browser-')),origin='http://127.0.0.1:8874',sha='a'.repeat(40);
const receipt={run_id:'GHARTV-CYAN-8-20260915T140000Z-100',review_source:sha,version:'0.6.0-rc8-smooth-performance',version_code:24,unsigned_apk_sha256:'b'.repeat(64),signed_apk_sha256:'c'.repeat(64),production_source:'d'.repeat(40),status:'REVIEW_READY',emulator:'RC8_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND',obsidian:'WRITTEN_AND_READBACK_VERIFIED'};
const state=join(home,'Library/Application Support/GharTV/owner-review');await mkdir(join(state,'current'),{recursive:true});await mkdir(join(home,'Documents/Amrit Executive Memory'),{recursive:true});await writeFile(join(state,'current/receipt.json'),JSON.stringify(receipt),{mode:0o600});
let browser;const server=spawn(process.execPath,['web-player/server.mjs'],{env:{...process.env,HOME:home,GHARTV_WEB_HOST:'127.0.0.1',GHARTV_WEB_PORT:'8874',GHARTV_DISABLE_KEYCHAIN:'1',GHARTV_WEB_SHA:sha},stdio:'ignore'});
try{
 let ready=false;for(let i=0;i<50;i++){try{if((await fetch(origin+'/api/health')).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,100));}assert.ok(ready);
 const raw=await(await fetch(origin+'/owner.html')).text();const boot=JSON.parse(raw.match(/id="owner-bootstrap" type="application\/json">(.*?)<\/script>/s)[1]);
 assert.equal((await fetch(origin+'/owner-api/review/status')).status,401);
 assert.equal((await fetch(origin+'/owner-api/release/status')).status,401);
 assert.equal((await fetch(origin+'/owner-api/release/publish',{method:'POST',headers:{Authorization:'Bearer '+boot.token,Origin:'https://other.invalid'},body:'{}'})).status,403);
 assert.equal((await fetch(origin+'/owner-api/review/sync',{method:'POST',headers:{Authorization:'Bearer '+boot.token,Origin:'https://other.invalid'},body:'{}'})).status,403);
 browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH,args:['--no-sandbox']}: {})});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const now=Math.floor(Date.now()/1000)*1000;
 const channels=[{id:'7',number:7,name:'Morning Gurbani',language:'Punjabi',category:'Devotional'}, {id:'33',number:33,name:'News at Home',language:'Hindi',category:'News'}, {id:'81',number:81,name:'Evening Ardas',language:'Punjabi',category:'Devotional'}, {id:'94',number:94,name:'Punjab Today',language:'Punjabi',category:'News'}, {id:'105',number:105,name:'Cinema Classics',language:'Punjabi',category:'Movies'}, {id:'115',number:115,name:'Golden Melodies',language:'Hindi',category:'Music'}, {id:'211',number:211,name:'Learn Together',language:'English',category:'Educational'}, {id:'214',number:214,name:'The Sports Room',language:'English',category:'Sports'}, {id:'290',number:290,name:'Family Stories',language:'Punjabi',category:'Entertainment'}].map(c=>({...c,logoUrl:origin+'/fixture.svg'}));
 for(let i=0;i<220;i++)channels.push({id:String(1000+i),number:1000+i,name:'Extra '+i,language:'English',category:'Educational',logoUrl:origin+'/fixture.svg'});
 const requested=[];let summaryReads=0,detailReads=0,devicesReads=0,failSample=false;
 await page.addInitScript(()=>{HTMLMediaElement.prototype.play=function(){return Promise.resolve();};});
 await page.route('**/fixture.svg',r=>r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect rx="8" width="100" height="100" fill="#123441"/><text x="20" y="59" fill="#abecda" font-family="sans-serif" font-size="30">TV</text></svg>'}));
 await page.route('**/vendor/hls.min.js',r=>r.fulfill({contentType:'application/javascript',body:`window.Hls=class{static isSupported(){return true}static Events={MEDIA_ATTACHED:'attach',MANIFEST_PARSED:'manifest',ERROR:'error'};static ErrorTypes={NETWORK_ERROR:'network'};attachMedia(){}on(k,f){if(k==='attach')setTimeout(f,0);if(k==='manifest')setTimeout(f,1)}loadSource(){}destroy(){}};`}));
 await page.route('**/api/auth/status',r=>r.fulfill({json:{connected:true,mobile:'fixture account'}}));
 await page.route('**/api/channels',r=>r.fulfill({json:{channels}}));
 await page.route('**/api/playback',async r=>{requested.push(JSON.parse(r.request().postData()).channelId);await r.fulfill({json:{protocol:'hls',url:'/fixture.m3u8'}});});
 await page.route('**/api/epg?**',async r=>{const id=new URL(r.request().url()).searchParams.get('channel_id');await r.fulfill({json:{programs:[{showname:'Up next '+id,startEpoch:(now+600000)/1000,endEpoch:(now+1200000)/1000},{showname:'Live programme '+id,startEpoch:now-60000,endEpoch:now+600000}]}});});
 await page.goto(origin+'/');await page.locator('.channel-card').first().waitFor();assert.equal(await page.locator('.channel-card').count(),96);await page.locator('#moreChannels').click();assert.equal(await page.locator('.channel-card').count(),192);assert.equal(await page.locator('.topbar #search').count(),1);
 await mkdir('/tmp/ghartv-check-evidence',{recursive:true});await page.screenshot({path:'/tmp/ghartv-check-evidence/rc8-browser-guide-fixture.png',fullPage:true});
 await page.keyboard.press('/');assert.equal(await page.locator('#search').evaluate(e=>e===document.activeElement),true);
 await page.locator('#search').fill('Morning');await page.keyboard.press('ArrowDown');assert.equal(await page.locator('.channel-card').first().evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('Enter');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 7'));
 assert.equal(await page.locator('#nextChannel').evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('PageDown');await page.waitForTimeout(100);assert.equal(requested.at(-1),'7'); // one-title search stays scoped
 await page.keyboard.press('Escape');await page.keyboard.press('/');await page.locator('#search').fill('');
 await page.locator('#punjabiQuick').click();await page.getByRole('button',{name:'Devotional',exact:true}).click();assert.equal(await page.locator('.channel-card').count(),2);
 await page.locator('.channel-card').first().focus();await page.keyboard.press('ArrowRight');assert.equal(await page.locator('.channel-card').nth(1).evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('Enter');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 81'));
 await page.keyboard.press('PageDown');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 7'));
 await page.keyboard.press('PageUp');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 81'));
 assert.ok(requested.every(x=>['7','81'].includes(x)));assert.match(await page.locator('#playerScope').innerText(),/Devotional.*2 channels/);
 await page.keyboard.press('f');await page.waitForFunction(()=>!!document.fullscreenElement && document.querySelector('#fullscreenButton').getAttribute('aria-label')==='Exit fullscreen');
 await page.keyboard.press('f');await page.waitForFunction(()=>!document.fullscreenElement && document.querySelector('#fullscreenButton').getAttribute('aria-label')==='Enter fullscreen');
 await page.screenshot({path:'/tmp/ghartv-check-evidence/rc8-browser-player-fixture.png',fullPage:true});
 await page.keyboard.press('Escape');assert.equal(await page.locator('.channel-card').nth(1).evaluate(e=>e===document.activeElement),true);
 await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:'/tmp/ghartv-check-evidence/rc8-mobile-fixture.png',fullPage:true});
 // Owner controls keep manual D1 reads. Initial receipt/config requests are LOCAL only.
 await page.setViewportSize({width:1440,height:1000});
 await page.route('https://raw.githubusercontent.com/**',r=>r.fulfill({json:{versionCode:17,versionName:'0.5.4-rc8',sourceCommit:'d'.repeat(40)}}));
 await page.route('**/owner-api/v1/admin/**',r=>{
  const path=new URL(r.request().url()).pathname;
  if(path.endsWith('/summary')){summaryReads++;return r.fulfill({json:{ok:true,totals:{events:1240,installations:8},events:[],failures:[],versions:[]}});}
  if(path.endsWith('/export')){detailReads++;if(failSample)return r.fulfill({status:502,json:{error:'COLLECTOR_DNS_FAILED'}});return r.fulfill({json:{ok:true,events:[]}});}
  if(path.endsWith('/devices')){devicesReads++;return r.fulfill({json:{ok:true,devices:[]}});}
  return r.fulfill({json:{ok:true,commands:[]}});
 });
 let publicationRequests=0;
 const desk={ok:true,green:{state:'INSTALLED_REVIEW_READY',run_id:receipt.run_id,source:sha,version:receipt.version,version_code:24,sha256:receipt.signed_apk_sha256},blue:{state:'LAST_VERIFIED_PUBLIC',versionCode:17,versionName:'0.5.4-rc8',sourceCommit:'d'.repeat(40),verified_at:new Date().toISOString()},promotion:{state:'NOT_REQUESTED'}};
 await page.route('**/owner-api/release/**',r=>{if(r.request().method()==='POST'&&new URL(r.request().url()).pathname.endsWith('/publish')){publicationRequests++;const b=JSON.parse(r.request().postData());assert.equal(b.sha256,receipt.signed_apk_sha256);assert.equal(b.confirmation,'PUBLISH EXACT CODE 24');return r.fulfill({json:{...desk,publication:{state:'PUBLIC_UPDATE_VERIFIED'}}});}return r.fulfill({json:desk});});
 let hostCaptures=0;
 await page.route('**/owner-api/performance/**',r=>{if(r.request().method()==='POST')hostCaptures++;return r.fulfill({json:{ok:true,report:{status:'FIXTURE_NOT_MAC',memory_pressure:'NORMAL',disk_free_gib:123,at_ist:'fixture',app_measurements:{samples:[]}}}});});
 await page.goto(origin+'/owner.html');await page.waitForFunction(()=>document.querySelector('#reviewVersion').textContent.includes('code 24'));
 await page.waitForTimeout(120);assert.equal(hostCaptures,0);await page.locator('#measureHost').click();await page.waitForFunction(()=>document.querySelector('#hostPressure').textContent==='NORMAL');assert.equal(hostCaptures,1);assert.equal(summaryReads,0);assert.equal(detailReads,0);assert.equal(devicesReads,0);
 await page.getByRole('button',{name:'Playback health',exact:true}).click();await page.getByRole('button',{name:'Overview',exact:true}).click();assert.equal(summaryReads,0);
 await page.getByRole('button',{name:'Refresh reports',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#events').textContent==='1240');assert.equal(summaryReads,1);await page.waitForFunction(()=>document.querySelector('#status').getAttribute('aria-busy')==='false');assert.equal(detailReads,1);
 await page.screenshot({path:'/tmp/ghartv-check-evidence/rc8-owner-overview-fixture.png',fullPage:true});
 failSample=true;await page.locator('#refresh').click();await page.waitForFunction(()=>document.querySelector('#status').getAttribute('aria-busy')==='false');assert.match(await page.locator('#status').innerText(),/Partial/);assert.match(await page.locator('#freshness').innerText(),/COLLECTOR_DNS_FAILED/);assert.equal(await page.locator('#events').innerText(),'1240');assert.equal(await page.locator('#refresh').isEnabled(),true);failSample=false;
 const priorReads=summaryReads;await page.waitForTimeout(200);assert.equal(summaryReads,priorReads); // no polling reintroduced
 
 await page.getByRole('button',{name:'Televisions & messages',exact:true}).click();await page.screenshot({path:'/tmp/ghartv-check-evidence/rc8-owner-tvs-fixture.png',fullPage:true});
 await page.getByRole('button',{name:'Review & feedback',exact:true}).click();await page.locator('#feedback').fill('Fixture private feedback — never sent to GitHub.');await page.locator('#saveFeedback').click();await page.waitForFunction(()=>document.querySelector('#privateFeedbackState').textContent.includes('Saved for'));

 assert.equal(await page.locator('#approvePublish').isDisabled(),true);
 for(const id of ['reviewedTV','reviewedWeb','reviewedOwner'])await page.locator('#'+id).check();
 assert.equal(await page.locator('#approvePublish').isEnabled(),true);
 page.once('dialog',d=>d.dismiss());await page.locator('#approvePublish').click();assert.equal(publicationRequests,0);
 page.once('dialog',d=>d.accept());await page.locator('#approvePublish').click();await page.waitForFunction(()=>document.querySelector('#publishStatus').textContent.includes('readback verified'));assert.equal(publicationRequests,1);
 await page.screenshot({path:'/tmp/ghartv-check-evidence/rc8-release-desk-fixture.png',fullPage:true});
 const {readdir}=await import('node:fs/promises');const saved=await readdir(join(state,'feedback'));assert.equal(saved.length,1);assert.match(await readFile(join(state,'feedback',saved[0]),'utf8'),/never sent to GitHub/);
 // Reference lookup is distinct from an empty/failed report fetch.
 await page.getByRole('button',{name:'Playback health',exact:true}).click();
 let references=0;await page.route('**/owner-api/support/reference?**',r=>{references++;return r.fulfill({json:{ok:true,matches:[],sample_count:5000,cap_reached:true}})});
 await page.locator('#diagnosticRef').fill('GH-A1B2C3D4');await page.locator('#findDiagnostic').click();
 await page.waitForFunction(()=>document.querySelector('#diagnosticStatus').textContent.includes('does NOT mean no error'));
 assert.equal(references,1);
 await page.unroute('**/owner-api/support/reference?**');await page.route('**/owner-api/support/reference?**',r=>r.fulfill({status:502,json:{error:'COLLECTOR_DNS_FAILED'}}));await page.locator('#findDiagnostic').click();await page.waitForFunction(()=>document.querySelector('#diagnosticStatus').textContent.includes('not a no-match'));
 // Punjabi is independent of category, not merely a text-search workaround.
 await page.screenshot({path:'/tmp/ghartv-check-evidence/rc8-owner-diagnostics-fixture.png',fullPage:true});
 await page.locator('#lock').click();assert.equal(await page.locator('#feedback').inputValue(),'');assert.equal(await page.locator('#measureHost').isDisabled(),true);assert.equal(await page.locator('#hostMeasureResult').innerText(),'');assert.equal(await page.locator('#safeReceiptPreview').innerText(),'');
 await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));assert.deepEqual(errors,[]);

 await page.goto(origin+'/');await page.locator('.channel-card').first().waitFor();
 await page.getByRole('button',{name:'Playback & comfort',exact:true}).click();
 await page.locator('#stillMinutes').fill('1');await page.locator('#saveComfort').click();await page.waitForFunction(()=>!document.querySelector('#comfortSettings').open && JSON.parse(localStorage.getItem('ghartv_comfort_v1')||'{}').minutes===1);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('ghartv_comfort_v1')).minutes),1);
 await page.locator('.channel-card').first().click();await page.locator('#playerDialog').waitFor();
 await page.clock.install();await page.evaluate(()=>document.dispatchEvent(new KeyboardEvent('keydown',{key:'Shift'})));
 await page.clock.fastForward(61000);assert.equal(await page.locator('#idleTitle').innerText(),'Still watching?');
 await page.clock.fastForward(31000);assert.equal(await page.locator('#idleTitle').innerText(),'Your TV is resting');
 await page.locator('#restGuide').click();assert.equal(await page.locator('#playerDialog').evaluate(e=>e.open),false);
 await page.getByRole('button',{name:'Playback & comfort',exact:true}).click();await page.locator('#stillEnabled').uncheck();await page.locator('#saveComfort').click();await page.waitForFunction(()=>!document.querySelector('#comfortSettings').open && JSON.parse(localStorage.getItem('ghartv_comfort_v1')||'{}').enabled===false);
 await page.locator('.channel-card').first().click();await page.evaluate(()=>document.dispatchEvent(new KeyboardEvent('keydown',{key:'Shift'})));
 await page.clock.fastForward(120000);assert.equal(await page.locator('#idlePrompt').evaluate(e=>e.open),false);
 assert.deepEqual(errors,[]);
 console.log('RC8_BROWSER_MANUAL_REPORTS_PUNJABI_KEYBOARD_PAGED_GUIDE_INACTIVITY_AND_LOCAL_HOST_OBSERVATION=PASS');

}finally{if(browser)await browser.close();server.kill('SIGTERM');await new Promise(r=>setTimeout(r,100));await rm(home,{recursive:true,force:true});}
