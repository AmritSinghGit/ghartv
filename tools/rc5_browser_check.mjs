// Focused real-browser check with synthetic channel/EPG/transport responses, never provider accounts.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdir,readFile} from 'node:fs/promises';
import {chromium} from '/tmp/ghartv-browser/node_modules/playwright/index.mjs';
const origin='http://127.0.0.1:8874';let browser;
const server=spawn(process.execPath,['web-player/server.mjs'],{env:{...process.env,GHARTV_WEB_HOST:'127.0.0.1',GHARTV_WEB_PORT:'8874',GHARTV_DISABLE_KEYCHAIN:'1',GHARTV_WEB_SHA:'fixture-rc5'},stdio:'ignore'});
try{
 for(let i=0;i<50;i++){try{if((await fetch(origin+'/api/health')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 const raw=await(await fetch(origin+'/owner.html')).text();
 const boot=JSON.parse(raw.match(/id="owner-bootstrap" type="application\/json">(.*?)<\/script>/s)[1]);
 assert.equal((await fetch(origin+'/owner-api/config-status')).status,401);
 const config=await(await fetch(origin+'/owner-api/config-status',{headers:{Authorization:'Bearer '+boot.token}})).json();
 assert.equal(config.token_returned,false);assert.equal(config.config_status,'DISABLED_IN_TEST');assert.equal(config.token_present,false);
 assert.ok(!raw.includes('type="password" autocomplete'));
 browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const now=Math.floor(Date.now()/1000)*1000;const channels=[
 {id:'7',number:7,name:'Devotional One',language:'Punjabi',category:'Devotional',logoUrl:origin+'/fixture.svg'},
 {id:'33',number:33,name:'News One',language:'Hindi',category:'News',logoUrl:origin+'/fixture.svg'},
 {id:'81',number:81,name:'Devotional Two',language:'Punjabi',category:'Devotional',logoUrl:origin+'/fixture.svg'}];
 const requested=[];
 await page.addInitScript(()=>{HTMLMediaElement.prototype.play=function(){return Promise.resolve();};});
 await page.route('**/fixture.svg',r=>r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#15263a"/><text x="15" y="55" fill="#81e8df">TV</text></svg>'}));
 await page.route('**/vendor/hls.min.js',r=>r.fulfill({contentType:'application/javascript',body:`window.Hls=class{static isSupported(){return true}static Events={MEDIA_ATTACHED:'attach',MANIFEST_PARSED:'manifest',ERROR:'error'};static ErrorTypes={NETWORK_ERROR:'network'};attachMedia(){}on(k,f){if(k==='attach')setTimeout(f,0);if(k==='manifest')setTimeout(f,1)}loadSource(){}destroy(){}};`}));
 await page.route('**/api/auth/status',r=>r.fulfill({json:{connected:true,mobile:'test household'}}));
 await page.route('**/api/channels',r=>r.fulfill({json:{channels}}));
 await page.route('**/api/playback',async r=>{requested.push(JSON.parse(r.request().postData()).channelId);await r.fulfill({json:{protocol:'hls',url:'/fixture.m3u8'}});});
 await page.route('**/api/epg?**',async r=>{
  const url=new URL(r.request().url()),id=url.searchParams.get('channel_id');
  await new Promise(ok=>setTimeout(ok,120));
  await r.fulfill({json:{programs:[{showname:'Next '+id,startEpoch:Math.floor((now+600000)/1000),endEpoch:Math.floor((now+1200000)/1000)},{showname:'Now '+id,startEpoch:now-60000,endEpoch:now+600000}]}});
 });
 await page.goto(origin+'/');await page.locator('.channel-card').first().waitFor();
 await page.getByRole('button',{name:'Devotional',exact:true}).click();assert.equal(await page.locator('.channel-card').count(),2);
 await mkdir('/tmp/ghartv-check-evidence',{recursive:true});
 await page.screenshot({path:'/tmp/ghartv-check-evidence/browser-guide-fixture.png',fullPage:true});
 await page.locator('.channel-card').first().click();await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Next 7'));
 assert.match(await page.locator('#playerScope').innerText(),/Devotional.*2 channels/);
 await page.getByRole('button',{name:'Next channel',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Next 81'));
 await page.getByRole('button',{name:'Next channel',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#playerTitle').textContent==='Devotional One');
 await page.getByRole('button',{name:'Previous channel',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Next 81'));
 assert.ok(requested.length>=4 && requested.every(id=>['7','81'].includes(id)));assert.ok(!requested.includes('33'));
 await page.screenshot({path:'/tmp/ghartv-check-evidence/browser-player-fixture.png',fullPage:true});
 await page.locator('#closePlayer').click();await page.getByRole('button',{name:'All',exact:true}).click();await page.locator('#search').fill('News One');await page.locator('.channel-card').first().click();
 await page.waitForFunction(()=>document.querySelector('#playerTitle').textContent==='News One');await page.getByRole('button',{name:'Next channel',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#playerNext').textContent.includes('Next 33'));assert.equal(requested.at(-1),'33');
 await page.locator('#closePlayer').click();await page.setViewportSize({width:390,height:844});await page.locator('#search').fill('');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
 await page.goto(origin+'/owner.html');await page.waitForFunction(()=>document.querySelector('#configStatus').textContent!=='Checking the existing collector configuration…');
 assert.equal(await page.locator('#token').isVisible(),false);assert.match(await page.locator('#access').innerText(),/collector.env/);
 assert.deepEqual(errors,[]);
 console.log('BROWSER_SCOPED_NEXT_PREVIOUS_SEARCH_NOW_NEXT_TOKEN_STATUS_STATIC_LAYOUT=PASS');
}finally{if(browser)await browser.close();server.kill('SIGTERM');}
