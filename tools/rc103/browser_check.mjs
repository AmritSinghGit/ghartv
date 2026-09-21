/** Native navigation wiring tests. Remote responses are fixtures, not provider results. */
import {chromium} from '../../web-player/browser-tools/node_modules/playwright-core/index.mjs';
import {createAppServer} from '../../web-player/server.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const out=process.argv[2];await mkdir(out,{recursive:true});
const server=createAppServer();await new Promise(r=>server.listen(8790,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:'/usr/bin/google-chrome',headless:true});
const records=[];
try{
 const context=await browser.newContext({viewport:{width:1440,height:1000}});
 const destinations=[];
 await context.route('**/*',route=>{
  const u=new URL(route.request().url());
  if(u.hostname==='127.0.0.1')return route.continue();
  if(u.origin==='https://flixmomo.app'){destinations.push(u.href);return route.fulfill({status:200,contentType:'text/html',body:'<!doctype html><title>Navigation test fixture</title><p>Not a provider result.</p>'});}
  return route.abort();
 });
 const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:8790/',{waitUntil:'networkidle'});
 await page.click('#accountButton');assert.equal(await page.locator('#loginDialog').evaluate(x=>x.open),true);await page.click('#closeLogin');
 assert.equal(await page.locator('a[href*="owner.html"]').count(),0);
 await page.screenshot({path:out+'/viewer-desktop.png',fullPage:true});
 await page.goto('http://127.0.0.1:8790/flixmomo.html',{waitUntil:'networkidle'});
 assert.equal(destinations.length,0,'No provider request on load');
 const q='ਜੱਟ & Juliet #2';await page.fill('#query',q);
 const searched=context.waitForEvent('page');await page.click('#submit');const result=await searched;
 await result.waitForURL('https://flixmomo.app/search?**');assert.equal(new URL(result.url()).searchParams.get('q'),q);assert.equal(await result.evaluate(()=>window.opener===null),true);await result.close();
 const browse=context.waitForEvent('page');await page.click('#browse');const home=await browse;await home.waitForURL('https://flixmomo.app/');await home.close();
 records.push({nativeSearchTarget:'PASS',nativeBrowseTarget:'PASS',unicodeEncoding:'PASS',newTabNoOpener:'PASS',providerResponse:'LOCAL_TEST_FIXTURE'});
 await page.selectOption('#route','tor-browser');
 assert.equal(await page.locator('#search').getAttribute('action'),'/api/films/native-only');
 assert.equal(await page.locator('#browse').getAttribute('href'),'#tor-browser');
 const before=destinations.length,tabCount=context.pages().length;
 await page.route('**/api/films/search',route=>route.fulfill({status:409,contentType:'application/json',body:JSON.stringify({error:'TOR_BROWSER_NOT_FOUND'})}));
 await page.click('#submit');await page.waitForFunction(()=>document.getElementById('notice').textContent.includes('not found'));
 assert.equal(destinations.length,before);assert.equal(context.pages().length,tabCount);assert.equal(await page.inputValue('#query'),q);
 records.push({missingTorNoDirectFallback:'PASS',queryPreserved:'PASS'});
 await page.selectOption('#route','direct');await page.fill('#query','');
 for(const width of [1440,390]){
  await page.setViewportSize({width,height:900});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:out+'/films-'+width+'.png',fullPage:true});
 }
 assert.deepEqual(errors,[]);await context.close();
 const plain=await browser.newContext({javaScriptEnabled:false});
 await plain.route('**/*',route=>{
  const u=new URL(route.request().url());
  if(u.hostname==='127.0.0.1')return route.continue();
  if(u.origin==='https://flixmomo.app')return route.fulfill({contentType:'text/html',body:'<title>No-script navigation fixture</title>'});
  return route.abort();
 });
 const nojs=await plain.newPage();await nojs.goto('http://127.0.0.1:8790/flixmomo.html');
 assert.equal(await nojs.locator('option[value="tor-browser"]').isDisabled(),true);
 await nojs.fill('#query','Dune');const wait=plain.waitForEvent('page');await nojs.click('#submit');const tab=await wait;await tab.waitForURL('https://flixmomo.app/search?q=Dune');await plain.close();
 records.push({nativeFormWithoutJavaScript:'PASS',torDisabledWithoutScript:'PASS'});
 await writeFile(out+'/CHROMIUM_VALIDATION.json',JSON.stringify({status:'PASS',records,scriptErrors:0,mobileOverflow:false,externalProviderRequests:0,liveSearchResults:false,livePlayback:false},null,2));
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
