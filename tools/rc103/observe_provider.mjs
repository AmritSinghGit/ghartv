/** Bounded public-page observation only. Never solves challenges or alters detection. */
import {mkdir,writeFile} from 'node:fs/promises';
import {chromium} from '../../web-player/browser-tools/node_modules/playwright-core/index.mjs';
import {launchOptions} from '../../web-player/film-browser-worker.mjs';
const out=process.argv[2];await mkdir(out,{recursive:true});
const report={provider:'https://flixmomo.app/',privateCredentials:false,playbackTest:false,sessions:[]};
for(const variant of ['ordinary','current-arguments']){
 let browser;
 const row={variant,navigations:[],failures:[],scriptReferences:[],observations:[]};
 try{
  browser=await chromium.launch(variant==='ordinary'?{executablePath:'/usr/bin/google-chrome',headless:false,chromiumSandbox:true}:launchOptions({executable:'/usr/bin/google-chrome',route:'direct',headless:false,env:process.env}));
  const context=await browser.newContext({acceptDownloads:false,permissions:[]});
  const page=await context.newPage();
  page.on('framenavigated',f=>{if(f===page.mainFrame())try{const u=new URL(f.url());row.navigations.push({host:u.hostname,path:u.pathname});}catch{}});
  page.on('requestfailed',r=>{if(row.failures.length<20){const u=new URL(r.url());row.failures.push({host:u.hostname,path:u.pathname.slice(0,150),error:r.failure()?.errorText});}});
  page.on('pageerror',e=>{row.pageError=String(e.message).slice(0,250);});
  page.on('response',async response=>{
   if(response.request().resourceType()!=='script')return;
   try{
    const u=new URL(response.url());if(u.origin!=='https://flixmomo.app')return;
    row.scriptReferences.push({path:u.pathname,status:response.status()});
    if(!response.ok())return;const text=await response.text();if(text.length>4000000)return;
    const hits=[...text.matchAll(/.{0,180}(?:\/dummy|action:\s*['"]search|\/search\?|searchParams\.get\().{0,220}/g)].slice(0,12).map(m=>m[0]);
    if(hits.length){row.scriptClues??=[];row.scriptClues.push({path:u.pathname,hits});}
   }catch{}
  });
  const response=await page.goto('https://flixmomo.app/',{waitUntil:'domcontentloaded',timeout:20000});row.http=response?.status();
  await page.waitForTimeout(4500);
  row.title=await page.title();
  row.pageText=(await page.locator('body').innerText({timeout:2000})).slice(0,5000);
  row.inputs=await page.locator('input').evaluateAll(xs=>xs.map(x=>({type:x.type,name:x.name,placeholder:x.placeholder,formAction:x.form?.action,formMethod:x.form?.method})).slice(0,12));
  row.publicLinks=await page.locator('a[href]').evaluateAll(xs=>xs.map(x=>({text:x.textContent?.trim().slice(0,70),href:x.getAttribute('href')})).filter(x=>/movie|tv|search|mirror/i.test(x.href||'')).slice(0,20));
  if(/just a moment|verify.*human|captcha|checking.*browser|security verification/i.test(row.title+' '+row.pageText))row.outcome='PROVIDER_VERIFICATION_REQUIRED_NO_BYPASS';
  else if(new URL(page.url()).pathname==='/dummy')row.outcome='DUMMY_REDIRECT_OBSERVED';
  else{
   const input=page.locator('input[type="search"],input[placeholder*="search" i]').first();
   if(await input.isVisible().catch(()=>false)){
    await input.fill('Dune');await input.press('Enter');await page.waitForTimeout(3000);
    row.searchPath=new URL(page.url()).pathname;
    row.searchParameters=[...new URL(page.url()).searchParams.keys()];
    row.searchText=(await page.locator('body').innerText({timeout:1500})).slice(0,5000);
    row.searchLinks=await page.locator('a[href]').evaluateAll(xs=>xs.map(x=>({text:x.querySelector('img')?.alt||x.textContent?.trim().slice(0,120),href:x.getAttribute('href')})).filter(x=>/movie|tv|search/i.test(x.href||'')).slice(0,30));
    row.outcome='SEARCH_INTERACTION_OBSERVED_NOT_PLAYBACK';
   }else row.outcome='SEARCH_INPUT_UNAVAILABLE';
  }
  await page.screenshot({path:out+'/'+variant+'.png',timeout:3000});
 }catch(e){row.outcome='OBSERVATION_FAILED';row.error=String(e.message).slice(0,500);}
 finally{if(browser)await browser.close();report.sessions.push(row);}
}
await writeFile(out+'/PROVIDER_OBSERVATION.json',JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
