// Focused browser checks; synthetic schedules and local receipts, never owner/provider credentials.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp,mkdir,writeFile,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE||'/tmp/ghartv-browser/node_modules/playwright/index.mjs');
const home=await mkdtemp(join(tmpdir(),'ghartv-rc6-browser-')),origin='http://127.0.0.1:8874',sha='a'.repeat(40);
const receipt={run_id:'GHARTV-CYAN-8-20260915T140000Z-100',review_source:sha,version:'0.6.0-rc6-family-focus',version_code:22,unsigned_apk_sha256:'b'.repeat(64),signed_apk_sha256:'c'.repeat(64),production_source:'d'.repeat(40),status:'REVIEW_READY',emulator:'RC6_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND',obsidian:'WRITTEN_AND_READBACK_VERIFIED'};
const state=join(home,'Library/Application Support/GharTV/owner-review');await mkdir(join(state,'current'),{recursive:true});await mkdir(join(home,'Documents/Amrit Executive Memory'),{recursive:true});await writeFile(join(state,'current/receipt.json'),JSON.stringify(receipt),{mode:0o600});
let browser;const server=spawn(process.execPath,['web-player/server.mjs'],{env:{...process.env,HOME:home,GHARTV_WEB_HOST:'127.0.0.1',GHARTV_WEB_PORT:'8874',GHARTV_DISABLE_KEYCHAIN:'1',GHARTV_WEB_SHA:sha},stdio:'ignore'});
try{
 let ready=false;for(let i=0;i<50;i++){try{if((await fetch(origin+'/api/health')).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,100));}assert.ok(ready);
 const raw=await(await fetch(origin+'/owner.html')).text();const boot=JSON.parse(raw.match(/id="owner-bootstrap" type="application\/json">(.*?)<\/script>/s)[1]);
 assert.equal((await fetch(origin+'/owner-api/review/status')).status,401);
 assert.equal((await fetch(origin+'/owner-api/review/sync',{method:'POST',headers:{Authorization:'Bearer '+boot.token,Origin:'https://other.invalid'},body:'{}'})).status,403);
 browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH,args:['--no-sandbox']}: {})});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const now=Math.floor(Date.now()/1000)*1000;
 const channels=[{id:'7',number:7,name:'Morning Gurbani',language:'Punjabi',category:'Devotional'}, {id:'33',number:33,name:'News at Home',language:'Hindi',category:'News'}, {id:'81',number:81,name:'Evening Ardas',language:'Punjabi',category:'Devotional'}, {id:'94',number:94,name:'Punjab Today',language:'Punjabi',category:'News'}, {id:'105',number:105,name:'Cinema Classics',language:'Punjabi',category:'Movies'}, {id:'115',number:115,name:'Golden Melodies',language:'Hindi',category:'Music'}, {id:'211',number:211,name:'Learn Together',language:'English',category:'Educational'}, {id:'214',number:214,name:'The Sports Room',language:'English',category:'Sports'}, {id:'290',number:290,name:'Family Stories',language:'Punjabi',category:'Entertainment'}].map(c=>({...c,logoUrl:origin+'/fixture.svg'}));
 const requested=[];let summaryReads=0,detailReads=0,devicesReads=0;
 await page.addInitScript(()=>{HTMLMediaElement.prototype.play=function(){return Promise.resolve();};});
 await page.route('**/fixture.svg',r=>r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect rx="8" width="100" height="100" fill="#123441"/><text x="20" y="59" fill="#abecda" font-family="sans-serif" font-size="30">TV</text></svg>'}));
 await page.route('**/vendor/hls.min.js',r=>r.fulfill({contentType:'application/javascript',body:`window.Hls=class{static isSupported(){return true}static Events={MEDIA_ATTACHED:'attach',MANIFEST_PARSED:'manifest',ERROR:'error'};static ErrorTypes={NETWORK_ERROR:'network'};attachMedia(){}on(k,f){if(k==='attach')setTimeout(f,0);if(k==='manifest')setTimeout(f,1)}loadSource(){}destroy(){}};`}));
 await page.route('**/api/auth/status',r=>r.fulfill({json:{connected:true,mobile:'fixture account'}}));
 await page.route('**/api/channels',r=>r.fulfill({json:{channels}}));
 await page.route('**/api/playback',async r=>{requested.push(JSON.parse(r.request().postData()).channelId);await r.fulfill({json:{protocol:'hls',url:'/fixture.m3u8'}});});
 await page.route('**/api/epg?**',async r=>{const id=new URL(r.request().url()).searchParams.get('channel_id');await r.fulfill({json:{programs:[{showname:'Up next '+id,startEpoch:(now+600000)/1000,endEpoch:(now+1200000)/1000},{showname:'Live programme '+id,startEpoch:now-60000,endEpoch:now+600000}]}});});
 await page.goto(origin+'/');await page.locator('.channel-card').first().waitFor();assert.equal(await page.locator('.topbar #search').count(),1);
 await mkdir('/tmp/ghartv-check-evidence',{recursive:true});await page.screenshot({path:'/tmp/ghartv-check-evidence/rc6-browser-guide-fixture.png',fullPage:true});
 await page.keyboard.press('/');assert.equal(await page.locator('#search').evaluate(e=>e===document.activeElement),true);
 await page.locator('#search').fill('Morning');await page.keyboard.press('ArrowDown');assert.equal(await page.locator('.channel-card').first().evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('Enter');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 7'));
 assert.equal(await page.locator('#nextChannel').evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('PageDown');await page.waitForTimeout(100);assert.equal(requested.at(-1),'7'); // one-title search stays scoped
 await page.keyboard.press('Escape');await page.keyboard.press('/');await page.locator('#search').fill('');
 await page.getByRole('button',{name:'Devotional',exact:true}).click();assert.equal(await page.locator('.channel-card').count(),2);
 await page.locator('.channel-card').first().focus();await page.keyboard.press('ArrowRight');assert.equal(await page.locator('.channel-card').nth(1).evaluate(e=>e===document.activeElement),true);
 await page.keyboard.press('Enter');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 81'));
 await page.keyboard.press('PageDown');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 7'));
 await page.keyboard.press('PageUp');await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Up next 81'));
 assert.ok(requested.every(x=>['7','81'].includes(x)));assert.match(await page.locator('#playerScope').innerText(),/Devotional.*2 channels/);
 await page.keyboard.press('f');await page.waitForFunction(()=>!!document.fullscreenElement && document.querySelector('#fullscreenButton').getAttribute('aria-label')==='Exit fullscreen');
 await page.keyboard.press('f');await page.waitForFunction(()=>!document.fullscreenElement && document.querySelector('#fullscreenButton').getAttribute('aria-label')==='Enter fullscreen');
 await page.screenshot({path:'/tmp/ghartv-check-evidence/rc6-browser-player-fixture.png',fullPage:true});
 await page.keyboard.press('Escape');assert.equal(await page.locator('.channel-card').nth(1).evaluate(e=>e===document.activeElement),true);
 await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:'/tmp/ghartv-check-evidence/rc6-mobile-fixture.png',fullPage:true});
 // Owner controls keep manual D1 reads. Initial receipt/config requests are LOCAL only.
 await page.setViewportSize({width:1440,height:1000});
 await page.route('https://raw.githubusercontent.com/**',r=>r.fulfill({json:{versionCode:17,versionName:'0.5.4-rc8',sourceCommit:'d'.repeat(40)}}));
 await page.route('**/owner-api/v1/admin/**',r=>{
  const path=new URL(r.request().url()).pathname;
  if(path.endsWith('/summary')){summaryReads++;return r.fulfill({json:{ok:true,totals:{events:1240,installations:8},events:[],failures:[],versions:[]}});}
  if(path.endsWith('/export')){detailReads++;return r.fulfill({json:{ok:true,events:[]}});}
  if(path.endsWith('/devices')){devicesReads++;return r.fulfill({json:{ok:true,devices:[]}});}
  return r.fulfill({json:{ok:true,commands:[]}});
 });
 await page.goto(origin+'/owner.html');await page.waitForFunction(()=>document.querySelector('#reviewVersion').textContent.includes('code 22'));
 await page.waitForTimeout(120);assert.equal(summaryReads,0);assert.equal(detailReads,0);assert.equal(devicesReads,0);
 await page.getByRole('button',{name:'Playback health',exact:true}).click();await page.getByRole('button',{name:'Overview',exact:true}).click();assert.equal(summaryReads,0);
 await page.getByRole('button',{name:'Refresh summary',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#events').textContent==='1240');assert.equal(summaryReads,1);assert.equal(detailReads,0);
 await page.screenshot({path:'/tmp/ghartv-check-evidence/rc6-owner-overview-fixture.png',fullPage:true});
 await page.getByRole('button',{name:'Televisions & messages',exact:true}).click();await page.screenshot({path:'/tmp/ghartv-check-evidence/rc6-owner-tvs-fixture.png',fullPage:true});
 await page.getByRole('button',{name:'Review & feedback',exact:true}).click();await page.locator('#feedback').fill('Fixture private feedback — never sent to GitHub.');await page.locator('#saveFeedback').click();await page.waitForFunction(()=>document.querySelector('#privateFeedbackState').textContent.includes('Saved for'));
 const {readdir}=await import('node:fs/promises');const saved=await readdir(join(state,'feedback'));assert.equal(saved.length,1);assert.match(await readFile(join(state,'feedback',saved[0]),'utf8'),/never sent to GitHub/);
 await page.locator('#lock').click();assert.equal(await page.locator('#feedback').inputValue(),'');assert.equal(await page.locator('#safeReceiptPreview').innerText(),'');
 await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));assert.deepEqual(errors,[]);
 console.log('RC6_BROWSER_TOP_SEARCH_KEYBOARD_SCOPE_FULLSCREEN_FOCUS_MANUAL_REPORTS_PRIVATE_FEEDBACK=PASS');
}finally{if(browser)await browser.close();server.kill('SIGTERM');await new Promise(r=>setTimeout(r,100));await rm(home,{recursive:true,force:true});}
