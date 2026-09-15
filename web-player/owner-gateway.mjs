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
export async function ownerRoute(req,res,url){
  if(url.pathname==='/owner.html'&&req.method==='GET'){
    let html=await readFile(join(ROOT,'../docs/owner.html'),'utf8');
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
  if(!allowed.has(path)||!['GET','POST'].includes(req.method)){reply(res,404,{error:'not_found'});return true;}
  if(!authorized(req)){reply(res,401,{error:'local_owner_session_required'});return true;}
  const origin=req.headers.origin;
  if(req.method==='POST'&&origin!==`http://${req.headers.host}`){reply(res,403,{error:'origin_rejected'});return true;}
  const secret=await token();if(!secret){reply(res,503,{error:'existing_private_collector_config_unavailable',config_status:configStatus});return true;}
  const target=new URL(path+url.search,COLLECTOR),controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000);
  try{
    let body;if(req.method==='POST'){const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>16384){reply(res,413,{error:'too_large'});return true;}chunks.push(chunk);}body=Buffer.concat(chunks);}
    const upstream=await fetch(target,{method:req.method,headers:{Authorization:'Bearer '+secret,...(body?{'Content-Type':'application/json'}:{})},body,redirect:'error',signal:controller.signal});
    const text=await upstream.text();if(text.length>8*1024*1024)throw new Error('too_large');
    res.writeHead(upstream.status,{'Content-Type':'application/json','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'});res.end(text);
  }catch{reply(res,502,{error:'collector_unavailable_or_timed_out'});}finally{clearTimeout(timer);}return true;
}
