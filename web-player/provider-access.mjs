/** On-demand provider-page inspection. NOT a video relay or stream extractor. */
import https from 'node:https';
import {lookup} from 'node:dns/promises';
import {isIP, BlockList} from 'node:net';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

export const REVISION = 'provider-access-1.0.0';
export const LIMITS = Object.freeze({timeoutMs:8000, browserMs:18000, maxBytes:131072, redirects:3});
export const PROVIDERS = Object.freeze({
  flixmomo: Object.freeze({id:'flixmomo', host:'flixmomo.app', name:'FlixMomo',
    nativePlayback:'NOT_INTEGRATED', embedPermission:'NOT_VERIFIED', browserPlayback:'NOT_VERIFIED'})
});
const blocked4 = new BlockList();
for (const [ip,n] of [['0.0.0.0',8],['10.0.0.0',8],['100.64.0.0',10],['127.0.0.0',8],
 ['169.254.0.0',16],['172.16.0.0',12],['192.0.0.0',24],['192.0.2.0',24],['192.88.99.0',24],
 ['192.168.0.0',16],['198.18.0.0',15],['198.51.100.0',24],['203.0.113.0',24],
 ['224.0.0.0',4],['240.0.0.0',4]]) blocked4.addSubnet(ip,n,'ipv4');
const global6 = new BlockList();global6.addSubnet('2000::',3,'ipv6');
const blocked6 = new BlockList();
for(const [ip,n] of [['2001::',23],['2001:db8::',32],['2002::',16],['3fff::',20]])blocked6.addSubnet(ip,n,'ipv6');
export function publicAddress(ip){
 const f=isIP(ip);return f===4?!blocked4.check(ip,'ipv4'):f===6&&global6.check(ip,'ipv6')&&!blocked6.check(ip,'ipv6');
}
export function providerURL(input){
 if(typeof input!=='string'||input.length>2048||/[\x00-\x20\\]/.test(input))throw Error('URL_REJECTED');
 let u;try{u=new URL(input);}catch{throw Error('URL_REJECTED');}
 if(u.protocol!=='https:'||u.username||u.password||u.port||u.hash)throw Error('HTTPS_PROVIDER_URL_REQUIRED');
 const provider=Object.values(PROVIDERS).find(p=>p.host===u.hostname);
 if(!provider)throw Error('PROVIDER_NOT_REGISTERED');
 // Query may contain a signed token on some providers. Nothing from the URL is logged or exported.
 return {url:u,provider};
}
export async function resolvePinned(host,resolve=lookup){
 let values,timer;
 try {values=await Promise.race([resolve(host,{all:true,verbatim:true}),new Promise((_,reject)=>{
  timer=setTimeout(()=>reject(Error('DNS_TIMEOUT')),3000);
 })]);} catch(e){throw Error(e.message==='DNS_TIMEOUT'?'DNS_TIMEOUT':'DNS_UNAVAILABLE');}
 finally{clearTimeout(timer);}
 if(!Array.isArray(values)||!values.length)throw Error('DNS_UNAVAILABLE');
 if(values.some(x=>!publicAddress(x.address)))throw Error('NON_PUBLIC_ADDRESS_REJECTED');
 return values.find(x=>x.family===4)||values[0];
}

function pinnedGet(url,address,signal){
 return new Promise((resolve,reject)=>{
  const req=https.request(url,{method:'GET',agent:false,signal,servername:url.hostname,
   lookup:(_host,options,cb)=>options?.all?cb(null,[address]):cb(null,address.address,address.family),
   headers:{Accept:'text/html,application/xhtml+xml;q=0.9,*/*;q=0.1','Accept-Encoding':'identity',
    'User-Agent':'GharTV-Provider-Check/1.0',Range:`bytes=0-${LIMITS.maxBytes-1}`}},res=>{
   let chunks=[],size=0,done=false;
   const finish=(truncated=false)=>{if(done)return;done=true;resolve({status:res.statusCode,
    headers:res.headers,body:Buffer.concat(chunks).toString('utf8'),truncated});};
   if(res.statusCode>=300&&res.statusCode<400){finish();res.destroy();return;}
   res.on('data',chunk=>{const remaining=LIMITS.maxBytes-size;chunks.push(chunk.subarray(0,remaining));size+=Math.min(remaining,chunk.length);
    if(size>=LIMITS.maxBytes){finish(true);res.destroy();}});
   res.on('end',()=>finish());res.on('error',e=>{if(!done)reject(e);});
  });req.on('error',reject);req.end();
 });
}
export function failureCode(error){
 const c=String(error?.code||error?.cause?.code||error?.message||error?.name||'');
 if(/NON_PUBLIC|PROVIDER_NOT|HTTPS_PROVIDER|URL_REJECTED|REDIRECT_/.test(c))return c;
 if(c==='DNS_TIMEOUT')return 'DNS_TIMEOUT';
 if(/ENOTFOUND|EAI_AGAIN|DNS_UNAVAILABLE/.test(c))return 'DNS_UNAVAILABLE';
 if(/ABORT|TIMEOUT|TIMED_?OUT/i.test(c))return 'REQUEST_TIMED_OUT';
 if(/CERT|TLS|SSL/.test(c))return 'TLS_VERIFICATION_FAILED';
 if(/BROWSER|SANDBOX/.test(c)&&/^[A-Z_0-9]+$/.test(c))return c;
 return 'CONNECTION_FAILED';
}
function framePolicy(headers){
 const x=String(headers['x-frame-options']||'').toLowerCase();
 if(x.includes('deny'))return 'DENIED';if(x.includes('sameorigin'))return 'SAME_ORIGIN_ONLY';
 if(/frame-ancestors/i.test(String(headers['content-security-policy']||'')))return 'PROVIDER_CSP_REQUIRES_REVIEW';
 return 'UNSPECIFIED_NOT_PERMISSION';
}
export async function inspectPage(input,{resolve=lookup,request=pinnedGet,timeoutMs=LIMITS.timeoutMs}={}){
 const {url:initial,provider}=providerURL(input);let url=initial;const began=Date.now();
 const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),timeoutMs);
 const base={schema:'ghartv.provider-inspection.v1',revision:REVISION,provider:provider.id,
  host:provider.host,checkedAt:new Date().toISOString(),nativePlayback:'NOT_INTEGRATED',
  playbackVerified:false,drm:'NOT_TESTED',route:'DIRECT_HTTPS',credentialsUsed:false};
 try {
  for(let n=0;n<=LIMITS.redirects;n++){
   if(controller.signal.aborted)throw Error('REQUEST_TIMED_OUT');
   const pin=await resolvePinned(url.hostname,resolve);
   const r=await Promise.race([request(url,pin,controller.signal),new Promise((_,reject)=>{
    if(controller.signal.aborted)reject(Error('REQUEST_TIMED_OUT'));
    else controller.signal.addEventListener('abort',()=>reject(Error('REQUEST_TIMED_OUT')),{once:true});
   })]);
   if(r.status>=300&&r.status<400){
    if(!r.headers.location||n===LIMITS.redirects)throw Error('REDIRECT_LIMIT');
    const next=new URL(r.headers.location,url);
    if(next.origin!==initial.origin)throw Error('REDIRECT_ORIGIN_NOT_APPROVED');
    url=providerURL(next.href).url;continue;
   }
   const type=String(r.headers['content-type']||'').split(';')[0].toLowerCase();
   const html=['text/html','application/xhtml+xml'].includes(type);
   const sample=html?r.body:'';
   return {...base,status:r.status>=200&&r.status<300?(html?'HTML_PAGE_NOT_VIDEO':'CONTENT_NOT_INTEGRATED'):
    r.status===401?'PROVIDER_LOGIN_REQUIRED':r.status===403?'PROVIDER_DENIED':r.status===429?'PROVIDER_RATE_LIMITED':'PROVIDER_HTTP_ERROR',
    httpStatus:r.status,contentType:type,elapsedMs:Date.now()-began,redirects:n,
    framePolicy:framePolicy(r.headers),sampleTruncated:!!r.truncated,
    markupOnly:{videoElements:(sample.match(/<video\b/gi)||[]).length,
     embeddedFrames:(sample.match(/<iframe\b/gi)||[]).length,scripts:(sample.match(/<script\b/gi)||[]).length},
    note:'Page delivery is not successful playback. No media URL, cookie, page text or licence key is returned.'};
  }
 }catch(e){return {...base,status:failureCode(e),elapsedMs:Date.now()-began};}
 finally{clearTimeout(timer);controller.abort();}
}
export async function inspectInBrowser(input){
 const {url,provider}=providerURL(input);
 if(!process.env.GHARTV_PROVIDER_BROWSER_EXECUTABLE)return Promise.resolve({provider:provider.id,status:'BROWSER_NOT_CONFIGURED',playbackVerified:false});
 const profile=await mkdtemp(join(tmpdir(),'ghartv-provider-check-'));
 // The child cannot read provider tokens from the parent environment. No shell or user profile reuse.
 return new Promise(resolve=>{
  const env={PATH:process.env.PATH||'/usr/bin:/bin',TMPDIR:process.env.TMPDIR||'/tmp',LANG:'en_US.UTF-8',
   GHARTV_PROVIDER_BROWSER_EXECUTABLE:process.env.GHARTV_PROVIDER_BROWSER_EXECUTABLE,
   HOME:profile,GHARTV_PROVIDER_PROFILE:profile};
  const child=spawn(process.execPath,[fileURLToPath(new URL('./provider-browser-worker.mjs',import.meta.url))],
   {stdio:['pipe','pipe','ignore'],env,detached:process.platform!=='win32'});
  let output='',done=false;
  const stop=()=>{try{if(process.platform!=='win32')process.kill(-child.pid,'SIGKILL');else child.kill('SIGKILL');}catch{}};
  const finish=result=>{if(done)return;done=true;clearTimeout(timer);stop();resolve(result);};
  const timer=setTimeout(()=>finish({provider:provider.id,status:'BROWSER_TIMED_OUT',playbackVerified:false}),LIMITS.browserMs);
  child.stdout.on('data',data=>{output+=data;if(output.length>16384)finish({status:'BROWSER_RESPONSE_TOO_LARGE',playbackVerified:false});});
  child.on('error',()=>finish({status:'BROWSER_UNAVAILABLE',playbackVerified:false}));
  child.on('close',()=>{try{const r=JSON.parse(output);finish({...r,playbackVerified:false});}catch{finish({status:'BROWSER_CHECK_FAILED',playbackVerified:false});}});
  child.stdin.on('error',()=>{});child.stdin.end(JSON.stringify({url:url.href}));
 }).finally(()=>rm(profile,{recursive:true,force:true}));
}
export function makeProviderRoute({inspect=inspectPage,browser=inspectInBrowser}={}){
 let busy=false,last=0;
 return async function providerRoute(req,res,url,authorized){
  if(!url.pathname.startsWith('/owner-api/providers/'))return false;
  const send=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'});res.end(JSON.stringify(data));};
  if(req.headers['x-operon-preview']||req.ghartvViewer?.preview){send(404,{error:'PRIVATE_OWNER_ROUTE'});return true;}
  if(!authorized(req)){send(401,{error:'LOCAL_OWNER_SESSION_REQUIRED'});return true;}
  if(url.pathname==='/owner-api/providers/status'&&req.method==='GET'){
   send(200,{revision:REVISION,providers:Object.values(PROVIDERS),busy,browserConfigured:!!process.env.GHARTV_PROVIDER_BROWSER_EXECUTABLE,
    automaticRequests:false,torRouting:false,nativePlayback:false});return true;
  }
  if(req.method!=='POST'||url.pathname!=='/owner-api/providers/inspect'){send(405,{error:'METHOD_NOT_ALLOWED'});return true;}
  if(req.headers.origin!==`http://${req.headers.host}`){send(403,{error:'ORIGIN_REJECTED'});return true;}
  if(!String(req.headers['content-type']||'').startsWith('application/json')){send(415,{error:'JSON_REQUIRED'});return true;}
  if(busy||Date.now()-last<1000){send(429,{error:'CHECK_ALREADY_RUNNING_OR_RECENT'});return true;}
  busy=true;let raw='';
  try{
   for await(const c of req){raw+=c;if(Buffer.byteLength(raw)>4096){send(413,{error:'REQUEST_TOO_LARGE'});return true;}}
   const body=JSON.parse(raw);providerURL(body.url);
   if(!['https','browser'].includes(body.mode))throw Error('MODE_REJECTED');
   const data=await (body.mode==='browser'?browser(body.url):inspect(body.url));send(200,data);
  }catch(e){send(400,{error:/^(URL_REJECTED|HTTPS_PROVIDER_URL_REQUIRED|PROVIDER_NOT_REGISTERED|MODE_REJECTED)$/.test(e.message)?e.message:'INVALID_REQUEST'});}
  finally{busy=false;last=Date.now();}
  return true;
 };
}
export const providerRoute=makeProviderRoute();
