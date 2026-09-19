import {chromium} from '../../web-player/browser-tools/node_modules/playwright-core/index.mjs';
import {createAppServer} from '../../web-player/server.mjs';
import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {join,normalize} from 'node:path';
import assert from 'node:assert/strict';
const out='/tmp/ghartv-ui';await mkdir(out,{recursive:true});
const web=createAppServer();await new Promise(r=>web.listen(8790,'127.0.0.1',r));
const site=createServer(async(req,res)=>{try{const path=normalize(new URL(req.url,'http://localhost').pathname);if(path.includes('..'))throw Error();const data=await readFile(join(process.cwd(),'docs',path==='/'?'index.html':path));res.writeHead(200,{'content-type':path.endsWith('.avif')?'image/avif':'text/html; charset=utf-8'});res.end(data);}catch{res.writeHead(404);res.end();}});await new Promise(r=>site.listen(8801,'127.0.0.1',r));
// This browser renders only local test surfaces; external requests are rejected.
const browser=await chromium.launch({headless:true,executablePath:process.env.GHARTV_TEST_BROWSER});
const records=[];
try{
 for(const [surface,url] of [['films','http://127.0.0.1:8790/flixmomo.html'],['web','http://127.0.0.1:8790/'],['owner','http://127.0.0.1:8790/owner.html'],['site','http://127.0.0.1:8801/']]){
  const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();const errors=[],requests=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>requests.push(r.url()));
  await context.route('https://**/*',r=>r.abort());
  const response=await page.goto(url,{waitUntil:'networkidle'});assert.equal(response.status(),200);assert.deepEqual(errors,[]);
  await page.screenshot({path:join(out,surface+'.png'),fullPage:true});
  if(surface==='films'){assert.ok(!requests.some(u=>u.includes('flixmomo.app')));await page.selectOption('#route','tor');assert.match(await page.locator('#notice').innerText(),/Tor selected/);}
  if(surface==='owner')assert.ok(!requests.some(u=>u.includes('/v1/admin/')),'Owner reports must remain manual');
  if(surface==='site'){await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'mobile overflow');await page.screenshot({path:join(out,'site-mobile.png'),fullPage:true});}
  records.push({surface,http:200,scriptErrors:errors.length,externalRequestsBlocked:true,livePlaybackVerified:false});await context.close();
 }
 await writeFile(join(out,'UI_VALIDATION.json'),JSON.stringify(records,null,2));console.log(JSON.stringify(records));
}finally{await browser.close();await new Promise(r=>web.close(r));await new Promise(r=>site.close(r));}
