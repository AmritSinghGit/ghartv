import {readFile} from 'node:fs/promises';
import {homedir} from 'node:os';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes,timingSafeEqual} from 'node:crypto';
const ROOT=dirname(fileURLToPath(import.meta.url));
const COLLECTOR='https://ghartv-telemetry.ghartv-47d9a0.workers.dev';
const nonce=randomBytes(32).toString('hex');
const allowed=new Set(['/v1/admin/summary','/v1/admin/export','/v1/admin/devices','/v1/admin/devices/claim','/v1/admin/broadcast','/v1/admin/commands','/v1/admin/devices/revoke']);
let configPromise;
async function token(){
  if(process.env.GHARTV_DISABLE_KEYCHAIN==='1')return '';
  if(!configPromise)configPromise=(async()=>{
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
    return found.GHARTV_TELEMETRY_ADMIN_TOKEN||'';
  })().catch(()=> '');
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
      .replace('The Mac launcher opens a private copy with the existing local credential.','This local reader keeps the collector credential in the existing loopback server. The browser receives only a temporary local-session capability.');
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer'});res.end(html);return true;
  }
  if(!url.pathname.startsWith('/owner-api/'))return false;
  const path=url.pathname.slice('/owner-api'.length);
  if(!allowed.has(path)||!['GET','POST'].includes(req.method)){reply(res,404,{error:'not_found'});return true;}
  if(!authorized(req)){reply(res,401,{error:'local_owner_session_required'});return true;}
  const origin=req.headers.origin;
  if(req.method==='POST'&&origin!==`http://${req.headers.host}`){reply(res,403,{error:'origin_rejected'});return true;}
  const secret=await token();if(!secret){reply(res,503,{error:'existing_private_collector_config_unavailable'});return true;}
  const target=new URL(path+url.search,COLLECTOR),controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000);
  try{
    let body;if(req.method==='POST'){const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>16384){reply(res,413,{error:'too_large'});return true;}chunks.push(chunk);}body=Buffer.concat(chunks);}
    const upstream=await fetch(target,{method:req.method,headers:{Authorization:'Bearer '+secret,...(body?{'Content-Type':'application/json'}:{})},body,redirect:'error',signal:controller.signal});
    const text=await upstream.text();if(text.length>8*1024*1024)throw new Error('too_large');
    res.writeHead(upstream.status,{'Content-Type':'application/json','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'});res.end(text);
  }catch{reply(res,502,{error:'collector_unavailable_or_timed_out'});}finally{clearTimeout(timer);}return true;
}
