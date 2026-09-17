/** Disposable read-only JS compatibility check. Does not extract or relay media. */
import {providerURL,resolvePinned} from './provider-access.mjs';
import {lstat} from 'node:fs/promises';
import {isAbsolute} from 'node:path';
let browser;
async function main(){
 if(process.getuid?.()===0)throw Error('SANDBOX_NON_ROOT_USER_REQUIRED');
 const exe=process.env.GHARTV_PROVIDER_BROWSER_EXECUTABLE||'';
 if(!isAbsolute(exe))throw Error('BROWSER_NOT_CONFIGURED');
 const stat=await lstat(exe);if(!stat.isFile()||!(stat.mode&0o111))throw Error('BROWSER_EXECUTABLE_INVALID');
 let raw='';for await(const chunk of process.stdin){raw+=chunk;if(raw.length>4096)throw Error('REQUEST_TOO_LARGE');}
 const {url,provider}=providerURL(JSON.parse(raw).url);const pin=await resolvePinned(url.hostname);
 let chromium;
 try {({chromium}=await import('playwright-core'));}
 catch{try{({chromium}=await import('./browser-tools/node_modules/playwright-core/index.mjs'));}
 catch{throw Error('BROWSER_DRIVER_NOT_INSTALLED');}}
 const began=Date.now();let denied=0,failed=0,mediaRequests=0,popups=0;
 const map=pin.family===6?`[${pin.address}]`:pin.address;
 // No user profile or logged-in browser attachment. Pin DNS while retaining TLS hostname validation.
 browser=await chromium.launch({executablePath:exe,headless:true,chromiumSandbox:true,timeout:6000,
  env:{PATH:process.env.PATH,HOME:process.env.HOME,TMPDIR:process.env.TMPDIR||'/tmp',LANG:'en_US.UTF-8'},
  args:[`--host-resolver-rules=MAP ${url.hostname} ${map}, MAP * ~NOTFOUND`,
   '--disable-quic','--force-webrtc-ip-handling-policy=disable_non_proxied_udp',
   '--autoplay-policy=user-gesture-required','--disable-background-networking']});
 const context=await browser.newContext({acceptDownloads:false,serviceWorkers:'block',permissions:[],
  ignoreHTTPSErrors:false,viewport:{width:1024,height:576}});
 await context.route('**/*',async route=>{
  const request=route.request();let u;
  try{u=providerURL(request.url()).url;}catch{denied++;await route.abort('blockedbyclient');return;}
  if(u.hostname!==provider.host||request.method()!=='GET'){denied++;await route.abort('blockedbyclient');return;}
  if(['media','image','font'].includes(request.resourceType())){
   if(request.resourceType()==='media')mediaRequests++;
   await route.abort('blockedbyclient');return;
  }
  await route.continue();
 });
 if(context.routeWebSocket)await context.routeWebSocket('**/*',socket=>{denied++;socket.close();});
 const page=await context.newPage();page.on('popup',p=>{popups++;void p.close();});
 page.on('dialog',d=>void d.dismiss());page.on('requestfailed',()=>failed++);
 const response=await page.goto(url.href,{waitUntil:'domcontentloaded',timeout:7000});
 await page.waitForTimeout(1000);
 const document=await page.evaluate(()=>({
  readyState:document.readyState,videoElements:document.querySelectorAll('video').length,
  embeddedFrames:document.querySelectorAll('iframe').length,mediaSourceSupported:typeof MediaSource!=='undefined',
  emeApiPresent:typeof navigator.requestMediaKeySystemAccess==='function'
 }));
 return {schema:'ghartv.provider-browser-observation.v1',provider:provider.id,host:provider.host,
  status:response?.ok()?(denied?'PAGE_OBSERVED_WITH_BLOCKED_DEPENDENCIES':'PAGE_OBSERVED'):'PROVIDER_HTTP_ERROR',
  httpStatus:response?.status()||null,elapsedMs:Date.now()-began,document,deniedRequests:denied,failedRequests:failed,
  mediaRequestsNotDownloaded:mediaRequests,closedPopups:popups,browserEngine:'CONFIGURED_CHROMIUM_FAMILY',
  isolatedContext:true,credentialsUsed:false,playbackVerified:false,torRouting:false,
  note:'DOM observation only; third-party origins and media requests are blocked. No media URL, page text, cookies or DRM licences returned.'};
}
try {console.log(JSON.stringify(await main()));}
catch(e){const c=String(e.code||e.message||'');console.log(JSON.stringify({
 status:/^[A-Z][A-Z_0-9]{1,80}$/.test(c)?c:/ERR_NAME_NOT_RESOLVED/.test(c)?'BROWSER_DNS_UNAVAILABLE':
 /ERR_CERT|SSL/.test(c)?'BROWSER_TLS_FAILED':/Timeout/.test(c)?'BROWSER_TIMED_OUT':'BROWSER_CHECK_FAILED',playbackVerified:false}));}
finally{await browser?.close().catch(()=>{});}
