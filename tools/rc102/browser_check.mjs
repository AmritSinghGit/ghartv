/** Chromium UI validation; external requests blocked. No provider credentials. */
import {chromium} from '../../web-player/browser-tools/node_modules/playwright-core/index.mjs';
import {createAppServer} from '../../web-player/server.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const out=process.argv[2];await mkdir(out,{recursive:true});
const server=createAppServer();await new Promise(r=>server.listen(8790,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:'/usr/bin/google-chrome',headless:true});const result=[];
try {
 for(const [name,path] of [['viewer','/'],['films','/flixmomo.html']]) {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>new URL(route.request().url()).hostname==='127.0.0.1'?route.continue():route.abort());
  assert.equal((await page.goto('http://127.0.0.1:8790'+path,{waitUntil:'networkidle'})).status(),200);
  assert.deepEqual(errors,[]);
  if(name==='viewer'){await page.click('#accountButton');assert.equal(await page.locator('#loginDialog').evaluate(d=>d.open),true);await page.click('#closeLogin');}
  if(name==='films'){assert.equal(await page.locator('#directProvider').getAttribute('href'),'https://flixmomo.app/');await page.selectOption('#route','tor');assert.equal(await page.locator('#directProvider').isVisible(),false);await page.selectOption('#route','direct');}
  await page.screenshot({path:out+'/'+name+'-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'mobile overflow: '+name);
  await page.screenshot({path:out+'/'+name+'-mobile.png',fullPage:true});
  result.push({surface:name,scriptErrors:errors.length,http:200,mobileOverflow:false,externalRequestsBlocked:true});await page.close();
 }
 await writeFile(out+'/CHROMIUM_VALIDATION.json',JSON.stringify({status:'PASS',browser:'Chromium',surfaces:result,liveProvider:false},null,2));
} finally {await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
