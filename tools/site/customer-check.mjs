import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const {chromium}=await import(pathToFileURL(process.env.GHARTV_PLAYWRIGHT_ENTRY).href);
const root=resolve('docs'),out=process.env.GHARTV_SITE_OUTPUT;await mkdir(out,{recursive:true});
const server=createServer(async(req,res)=>{try{const url=new URL(req.url,'http://localhost');const path=resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname));if(!path.startsWith(root+'/'))throw Error();const bytes=await readFile(path);res.writeHead(200,{'content-type':({'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.avif':'image/avif'})[extname(path)]||'application/octet-stream'});res.end(bytes);}catch{res.writeHead(404);res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome'}),records=[];
try{
 for(const width of [1440,390,320]){
  const context=await browser.newContext({viewport:{width,height:width===1440?1000:844}}),page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('console',msg=>{if(msg.type()==='error'&&/Content Security|Uncaught|Refused/.test(msg.text()))errors.push(msg.text());});
  await context.route('https://**/*',r=>r.abort());
  await page.goto('http://127.0.0.1:'+server.address().port,{waitUntil:'networkidle'});
  const text=await page.locator('body').innerText();assert.doesNotMatch(text,/\b(owner|candidate|emulator|signing|unsigned|checksum|code28|worktree)\b/i);
  assert.match(text,/This download is the family edition/);assert.match(text,/eligible Jio account/);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Horizontal overflow at '+width);
  assert.ok(await page.locator('img').evaluate(img=>img.complete&&img.naturalWidth>0),'Product image failed');
  assert.match(await page.locator('#download-apk').getAttribute('href'),/v0.6.0-rc8\/GharTV-review-current.apk$/);
  assert.deepEqual(errors,[]);await page.screenshot({path:out+'/public-'+width+'.png',fullPage:true});
  records.push({width,overflow:false,product_image_loaded:true,no_owner_jargon:true,download:'EXISTING_SIGNED_PRODUCTION_APK',external_requests:'BLOCKED_TEST',script_or_csp_failures:0});await context.close();
 }
 await writeFile(out+'/PUBLIC_SITE_VALIDATION.json',JSON.stringify(records,null,2));console.log(JSON.stringify(records));
}finally{await browser.close();await new Promise(r=>server.close(r));}
