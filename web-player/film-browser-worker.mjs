/** Optional, on-demand Brave/Chromium provider session. No parent credentials or profile reuse. */
import {pathToFileURL} from 'node:url';
import {resolvePinned} from './provider-access.mjs';
export const PROVIDER='https://flixmomo.app';
export function filmURL(input){
 const u=new URL(input,PROVIDER);
 if(u.origin!==PROVIDER||u.username||u.password||u.hash||u.href.length>2048||!/^\/(movie|tv)\/\d+(?:\/[^/?#]*)*(?:\/)?$/.test(u.pathname))throw Error('FILM_URL_REJECTED');
 return u.href;
}
export function torProxy(raw='socks5://127.0.0.1:9050'){
 const u=new URL(raw);
 if(u.protocol!=='socks5:'||u.hostname!=='127.0.0.1'||!['9050','9150'].includes(u.port)||u.username||u.password||u.pathname||u.search||u.hash)throw Error('LOCAL_TOR_PROXY_REQUIRED');
 return u.origin.replace('null',`socks5://127.0.0.1:${u.port}`);
}
export function launchOptions({executable,route,proxy,headless=true,env={}}){
 if(!['direct','tor'].includes(route))throw Error('ROUTE_REQUIRED');
 const args=['--disable-quic','--force-webrtc-ip-handling-policy=disable_non_proxied_udp','--disable-background-networking','--disable-sync','--no-first-run'];
 if(route==='tor')args.push('--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1');
 return {executablePath:executable,headless,chromiumSandbox:true,timeout:20000,args,env,...(route==='tor'?{proxy:{server:torProxy(proxy)}}:{})};
}
export function normalizeResults(rows){
 const seen=new Set(),out=[];
 for(const row of rows||[]){try{
  const url=filmURL(row.url),u=new URL(url),id=u.pathname.match(/^\/(movie|tv)\/(\d+)/).slice(1).join(':');
  if(seen.has(id))continue;const title=String(row.title||'').replace(/\s+/g,' ').trim().slice(0,160);
  if(!title)continue;seen.add(id);out.push({id,title,url,type:id.startsWith('movie:')?'Movie':'Series'});
 }catch{}if(out.length>=40)break;}
 return out;
}
const matches=(h,allowed)=>allowed.some(a=>h===a||h.endsWith('.'+a));
const searchHosts=['flixmomo.app','themoviedb.org','tmdb.org','challenges.cloudflare.com'];
async function run(input){
 if(!['search','open','browse'].includes(input.action)||!['direct','tor'].includes(input.route))throw Error('ACTION_REJECTED');
 const {chromium}=await import('./browser-tools/node_modules/playwright-core/index.mjs');
 const browser=await chromium.launch(launchOptions({executable:process.env.GHARTV_FILM_EXECUTABLE,route:input.route,proxy:process.env.GHARTV_TOR_PROXY,headless:input.action==='search',env:process.env}));
 let stopping=false;
 const close=async()=>{if(stopping)return;stopping=true;await browser.close().catch(()=>{});};
 process.on('SIGTERM',()=>close().finally(()=>process.exit(0)));
 const context=await browser.newContext({acceptDownloads:false,serviceWorkers:'block',permissions:[],locale:'en-IN'});
 const page=await context.newPage();context.on('page',p=>{if(p!==page)p.close().catch(()=>{});});
 let torVerified=false,checkingTor=input.route==='tor';
 await context.route('**/*',async r=>{
  try{
   const u=new URL(r.request().url());
   if(u.protocol==='data:'||u.protocol==='blob:')return r.continue();
   if(u.protocol!=='https:'||u.username||u.password||u.port)return r.abort();
   if(checkingTor && u.hostname!=='check.torproject.org')return r.abort();
   if(r.request().isNavigationRequest()&&r.request().frame()===page.mainFrame()&&!['flixmomo.app','check.torproject.org'].includes(u.hostname))return r.abort();
   if(input.action==='search'&&!checkingTor&&!matches(u.hostname,searchHosts))return r.abort();
   if(['localhost'].includes(u.hostname)||/\.local$/.test(u.hostname)||/^\[|^[\d.]+$/.test(u.hostname))return r.abort();
   if(input.route==='direct')await resolvePinned(u.hostname);
   if(input.action==='search'&&['media','font','image'].includes(r.request().resourceType()))return r.abort();
   return r.continue();
  }catch{return r.abort();}
 });
 try{
  if(checkingTor){
   const response=await page.goto('https://check.torproject.org/api/ip',{waitUntil:'domcontentloaded',timeout:18000});
   if(!response?.ok())throw Error('TOR_CHECK_FAILED');
   const body=await page.locator('body').innerText();let data;try{data=JSON.parse(body);}catch{throw Error('TOR_CHECK_FAILED');}
   if(data.IsTor!==true)throw Error('TOR_NOT_VERIFIED_NO_DIRECT_FALLBACK');
   torVerified=true;checkingTor=false;
  }
  if(input.action!=='search'){
   await page.goto(input.action==='browse'?PROVIDER+'/':filmURL(input.url),{waitUntil:'domcontentloaded',timeout:20000});
   process.stdout.write(JSON.stringify({ok:true,status:'BROWSER_OPENED_PLAYBACK_UNVERIFIED',route:input.route,torVerified,playbackVerified:false})+'\n');
   const timer=setTimeout(()=>close(),2*60*60*1000);
   await new Promise(resolve=>browser.on('disconnected',resolve));clearTimeout(timer);return;
  }
  const query=String(input.query||'').trim();if(query.length<2||query.length>120)throw Error('QUERY_LENGTH_REJECTED');
  await page.goto(PROVIDER+'/',{waitUntil:'domcontentloaded',timeout:20000});
  const field=page.locator('input[type="search"], input[placeholder*="search" i]').first();
  if(!await field.isVisible().catch(()=>false)){
   const link=page.getByRole('link',{name:/^search$/i}).first(),button=page.getByRole('button',{name:/search/i}).first();
   if(await link.isVisible().catch(()=>false))await link.click();else if(await button.isVisible().catch(()=>false))await button.click();
  }
  try { await field.waitFor({state:'visible',timeout:12000}); }
  catch(error) {
   const title=await page.title().catch(()=>'');
   const text=await page.locator('body').innerText({timeout:1000}).catch(()=>'');
   if(/just a moment|security verification|verify.*human|checking.*browser|captcha/i.test(title+' '+text))throw Error('PROVIDER_VERIFICATION_REQUIRED_USE_BROWSER');
   throw Error('PROVIDER_SEARCH_CONTROL_UNAVAILABLE');
  }
  let responseSeen=false;
  const observed=query.toLowerCase();
  page.on('response',r=>{try{const u=new URL(r.url());if(r.ok()&&['fetch','xhr'].includes(r.request().resourceType())&&[...u.searchParams.values()].some(x=>x.toLowerCase()===observed)&&/search/i.test(u.pathname))responseSeen=true;}catch{}});
  await field.fill(query);await field.press('Enter');
  await page.waitForFunction(q=>{const u=new URL(location.href);return /search/i.test(u.pathname)&&[...u.searchParams.values()].some(v=>v.toLowerCase()===q.toLowerCase());},query,{timeout:6000}).catch(()=>{});
  for(let n=0;n<12;n++){
   if(responseSeen || await page.locator('main').innerText().then(t=>t.toLowerCase().includes(query.toLowerCase())&&/results|no .*found|nothing found/i.test(t)).catch(()=>false))break;
   await page.waitForTimeout(300);
  }
  const searchURL=new URL(page.url());
  const routeConfirmed=/search/i.test(searchURL.pathname)&&[...searchURL.searchParams.values()].some(x=>x.toLowerCase()===observed);
  if(!routeConfirmed&&!responseSeen)throw Error('PROVIDER_SEARCH_NOT_CONFIRMED');
  await page.waitForTimeout(800);
  const rows=await page.locator('main a[href], [role="main"] a[href]').evaluateAll(nodes=>nodes.map(a=>({url:a.href,title:a.querySelector('img')?.alt||a.querySelector('h2,h3,h4')?.textContent||a.textContent})));
  const results=normalizeResults(rows);
  process.stdout.write(JSON.stringify({ok:true,status:results.length?'SEARCH_RESULTS':'NO_RENDERED_RESULTS',results,route:input.route,torVerified,provider:'flixmomo',playbackVerified:false,checkedAt:new Date().toISOString()})+'\n');
 }finally{await close();}
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href){
 let raw='';for await(const chunk of process.stdin){raw+=chunk;if(raw.length>4096)process.exit(2);}
 try{await run(JSON.parse(raw));}catch(error){
  const message=String(error.message||'');const code=/^[A-Z_0-9]{3,100}$/.test(message)?message:/playwright|module/i.test(message)?'BROWSER_DRIVER_UNAVAILABLE':/timeout/i.test(message)?'PROVIDER_TIMED_OUT':/sandbox/i.test(message)?'BROWSER_SANDBOX_UNAVAILABLE':/executable/i.test(message)?'BROWSER_UNAVAILABLE':'PROVIDER_BROWSER_FAILED';
  process.stdout.write(JSON.stringify({ok:false,error:code,playbackVerified:false})+'\n');process.exitCode=1;
 }
}
