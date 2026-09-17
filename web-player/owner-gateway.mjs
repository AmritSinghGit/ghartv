import {performanceRoute} from './performance-desk.mjs';
import {providerRoute} from './provider-access.mjs';
import {releaseRoute} from './release-desk.mjs';
import {captureSupport,readSupport,validReference,projectEvent} from './support-report.mjs';
import {fabricControl} from './fabric-preview.mjs';
import {latestReview,publishReceipt,saveFeedback} from './review-sync.mjs';
import {readFile} from 'node:fs/promises';
import {homedir} from 'node:os';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes,timingSafeEqual} from 'node:crypto';
const ROOT=dirname(fileURLToPath(import.meta.url));
const COLLECTOR='https://ghartv-telemetry.ghartv-47d9a0.workers.dev';
const nonce=randomBytes(32).toString('hex');
const allowed=new Set(['/v1/admin/summary','/v1/admin/export','/v1/admin/devices','/v1/admin/devices/claim','/v1/admin/broadcast','/v1/admin/commands','/v1/admin/devices/revoke']);
let configPromise;let configStatus='NOT_READ';let configReadAt=0;
async function token(){
  if(process.env.GHARTV_DISABLE_KEYCHAIN==='1'){configStatus='DISABLED_IN_TEST';return '';}
  if(!configPromise||Date.now()-configReadAt>30000){configReadAt=Date.now();configPromise=(async()=>{
    const {lstat}=await import('node:fs/promises');
    const path=join(homedir(),'Library/Application Support/GharTV/telemetry/collector.env'),s=await lstat(path);
    if(!s.isFile()||s.isSymbolicLink()||s.uid!==process.getuid()||(s.mode&0o077)||s.size>16384)throw new Error('PRIVATE_COLLECTOR_CONFIG_REQUIRED');
    const content=await readFile(path,'utf8');const found={};
    // Published configuration uses one literal shell-quoted assignment per line; no evaluation.
    for(const line of content.split(/\r?\n/)){
      const m=line.match(/^\s*(?:export\s+)?(GHARTV_TELEMETRY_ADMIN_TOKEN|GHARTV_TELEMETRY_ENDPOINT)=(.*)\s*$/);if(!m)continue;
      let v=m[2].trim();if((v.startsWith("'")&&v.endsWith("'"))||(v.startsWith('"')&&v.endsWith('"')))v=v.slice(1,-1);
      if(/[\r\n`$]/.test(v))throw new Error('UNSUPPORTED_CONFIG_FORMAT');found[m[1]]=v;
    }
    if((found.GHARTV_TELEMETRY_ENDPOINT||COLLECTOR).replace(/\/$/,'')!==COLLECTOR)throw new Error('COLLECTOR_ORIGIN_MISMATCH');
    configStatus=found.GHARTV_TELEMETRY_ADMIN_TOKEN?'PRESENT':'ADMIN_TOKEN_FIELD_MISSING';return found.GHARTV_TELEMETRY_ADMIN_TOKEN||'';
  })().catch(e=>{configStatus=e.code==='ENOENT'?'FILE_NOT_FOUND':e.message==='PRIVATE_COLLECTOR_CONFIG_REQUIRED'?'PRIVATE_PERMISSIONS_REQUIRED':e.message==='COLLECTOR_ORIGIN_MISMATCH'?'COLLECTOR_ORIGIN_MISMATCH':'CONFIG_UNREADABLE';return '';});}
  return configPromise;
}
function reply(res,status,body){const data=JSON.stringify(body);res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(data);}
function authorized(req){const supplied=Buffer.from((req.headers.authorization||'').replace(/^Bearer /,'')),wanted=Buffer.from(nonce);return supplied.length===wanted.length&&timingSafeEqual(supplied,wanted);}
async function collectorRead(path) {
  const secret=await token();
  if(!secret)throw Object.assign(new Error('Collector configuration unavailable'),{code:configStatus});
  if(!/^\/v1\/admin\/(summary|export)\?days=(1|7|30)(?:&limit=5000)?$/.test(path))throw Error('READ_PATH_REJECTED');
  const response=await fetch(COLLECTOR+path,{headers:{Authorization:'Bearer '+secret},redirect:'error',signal:AbortSignal.timeout(30000)});
  if(!response.ok)throw Object.assign(Error('COLLECTOR_HTTP_'+response.status),{code:'COLLECTOR_HTTP_'+response.status});
  const text=await response.text();if(text.length>8*1024*1024)throw Error('REPORT_TOO_LARGE');
  const data=JSON.parse(text);if(data.ok!==true)throw Error('INVALID_COLLECTOR_RESPONSE');return data;
}
function readFailure(e){const c=e?.cause?.code||e?.code||e?.name||'';return /ENOTFOUND|EAI_AGAIN/.test(c)?'COLLECTOR_DNS_FAILED':/Timeout|Abort|TIMEOUT/.test(c)?'COLLECTOR_TIMED_OUT':/CERT|TLS|SSL/.test(c)?'COLLECTOR_TLS_FAILED':'COLLECTOR_CONNECTION_FAILED';}
export async function ownerRoute(req,res,url){
  if(req.headers['x-operon-preview']){reply(res,404,{error:'owner_routes_private'});return true;}
  if(await providerRoute(req,res,url,authorized))return true;
  if(url.pathname==='/provider-access.html'&&req.method==='GET'){
    const scriptNonce=randomBytes(20).toString('hex');
    const html=(await readFile(join(ROOT,'../docs/provider-access.html'),'utf8'))
      .replaceAll('__SCRIPT_NONCE__',scriptNonce).replace('__OWNER_BOOT__',JSON.stringify({token:nonce}).replace(/</g,'\\u003c'));
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer',
      'Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-"+scriptNonce+"'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"});
    res.end(html);return true;
  }
  if(await performanceRoute(req,res,url,authorized))return true;
  if(await releaseRoute(req,res,url,authorized))return true;
  if(await fabricControl(req,res,url,authorized))return true;
  if(url.pathname==='/owner.html'&&req.method==='GET'){
    let html=await readFile(join(ROOT,'../docs/owner.html'),'utf8');
    html=html.replace('<nav', '<p><a href="./provider-access.html">Provider compatibility · check a source</a></p><nav');
    const origin=`http://${req.headers.host}`;
    const boot={token:nonce,run_id:'OWNER_LOCAL_READER',review_source:process.env.GHARTV_WEB_SHA||'not_verified',control_sha:process.env.GHARTV_WEB_SHA||'not_verified'};
    html=html.replace("const ENDPOINT='https://ghartv-telemetry.ghartv-47d9a0.workers.dev';",`const ENDPOINT=${JSON.stringify(origin+'/owner-api')};`)
      .replace('<!--OWNER_BOOTSTRAP-->','<script id="owner-bootstrap" type="application/json">'+JSON.stringify(boot).replace(/</g,'\\u003c')+'</script>')
      .replace('The Mac launcher opens a private copy with the existing local credential.','This local reader uses your existing saved collector configuration automatically. You do not need to find or paste its admin token here.');
    html=html.replace(/<section id="access">[\s\S]*?<\/section>/,'<section id="access"><div class="connection-row"><span class="status-dot" aria-hidden="true"></span><div><h2>Private connection</h2><p id="configStatus">Checking saved configuration…</p></div></div><details><summary>Where is my collector credential?</summary><p class="muted">Saved on this Mac in ~/Library/Application Support/GharTV/telemetry/collector.env. It stays on the local server; this page never displays it. Report requests verify live authorization.</p></details><input id="token" type="password" hidden><button id="connect" hidden>Connect</button></section>');
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8' ,'Cache-Control':'no-store','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer'});res.end(html);return true;
  }
  if(!url.pathname.startsWith('/owner-api/'))return false;
  const path=url.pathname.slice('/owner-api'.length);
  if(['/review/status','/review/sync','/review/feedback'].includes(path)){
    if(!authorized(req)){reply(res,401,{error:'local_owner_session_required'});return true;}
    if(path==='/review/status'&&req.method==='GET'){
      try{reply(res,200,await latestReview());}catch{reply(res,200,{ok:false,status:'NO_LOCAL_RECEIPT_YET'});}return true;
    }
    if(req.method!=='POST'){reply(res,405,{error:'method_not_allowed'});return true;}
    if(req.headers.origin!==`http://${req.headers.host}`){reply(res,403,{error:'origin_rejected'});return true;}
    try{
      const chunks=[];let size=0;for await(const c of req){size+=c.length;if(size>32768){reply(res,413,{error:'too_large'});return true;}chunks.push(c);}
      const body=JSON.parse(Buffer.concat(chunks).toString('utf8')||'{}');
      if(path==='/review/sync'){reply(res,200,await publishReceipt());return true;}
      reply(res,200,await saveFeedback(body));
    }catch{reply(res,400,{error:'review_action_failed_or_candidate_changed'});}return true;
  }
  if(path==='/config-status'&&req.method==='GET'){if(!authorized(req)){reply(res,401,{error:'local_owner_session_required'});return true;}await token();reply(res,200,{ok:true,config_status:configStatus,token_present:configStatus==='PRESENT',location:'~/Library/Application Support/GharTV/telemetry/collector.env',field:'GHARTV_TELEMETRY_ADMIN_TOKEN',token_returned:false});return true;}
  if(path.startsWith('/support/')){
    if(!authorized(req)){reply(res,401,{error:'local_owner_session_required'});return true;}
    if(req.method==='POST'&&req.headers.origin!==`http://${req.headers.host}`){reply(res,403,{error:'origin_rejected'});return true;}
    try{
      if(path==='/support/capture'&&req.method==='POST'){
        let raw='';for await(const c of req){raw+=c;if(raw.length>2048){reply(res,413,{error:'too_large'});return true;}}
        const body=JSON.parse(raw||'{}');reply(res,200,await captureSupport(collectorRead,Number(body.days||7),String(body.reference||'').trim().toUpperCase()));return true;
      }
      if(path==='/support/reference'&&req.method==='GET'){
        const ref=String(url.searchParams.get('ref')||'').toUpperCase();if(!validReference(ref)){reply(res,400,{error:'Use a GH-XXXXXXXX reference'});return true;}
        const data=await collectorRead('/v1/admin/export?days=30&limit=5000');
        reply(res,200,{ok:true,reference:ref,matches:(data.events||[]).filter(e=>String(e.reference).toUpperCase()===ref).slice(0,50).map(projectEvent),sample_count:data.events.length,cap_reached:data.events.length>=5000,window_days:30,checked_at:new Date().toISOString(),scope:'latest5000_received_events_not_all_history'});return true;
      }
      if(path.startsWith('/support/download/')&&req.method==='GET'){reply(res,200,await readSupport(path.split('/').pop()));return true;}
      reply(res,404,{error:'not_found'});
    }catch(e){reply(res,502,{error:e.code&&/^[A-Z_0-9]{1,80}$/.test(e.code)?e.code:readFailure(e)});}return true;
  }
  if(!allowed.has(path)||!['GET','POST'].includes(req.method)){reply(res,404,{error:'not_found'});return true;}
  if(!authorized(req)){reply(res,401,{error:'local_owner_session_required'});return true;}
  const origin=req.headers.origin;
  if(req.method==='POST'&&origin!==`http://${req.headers.host}`){reply(res,403,{error:'origin_rejected'});return true;}
  const secret=await token();if(!secret){reply(res,503,{error:'existing_private_collector_config_unavailable',config_status:configStatus});return true;}
  const target=new URL(path+url.search,COLLECTOR),controller=new AbortController(),timer=setTimeout(()=>controller.abort(),30000);
  try{
    let body;if(req.method==='POST'){const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>16384){reply(res,413,{error:'too_large'});return true;}chunks.push(chunk);}body=Buffer.concat(chunks);}
    const upstream=await fetch(target,{method:req.method,headers:{Authorization:'Bearer '+secret,...(body?{'Content-Type':'application/json'}:{})},body,redirect:'error',signal:controller.signal});
    const text=await upstream.text();if(text.length>8*1024*1024)throw new Error('too_large');
    res.writeHead(upstream.status,{'Content-Type':'application/json','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'});res.end(text);
  }catch(e){reply(res,502,{error:readFailure(e)});}finally{clearTimeout(timer);}return true;
}
