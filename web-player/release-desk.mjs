/** Existing owner-console routes only. No extra HTTP listener or release daemon. */
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';
const ROOT=dirname(fileURLToPath(import.meta.url));let busy=false;
export function invokeRelease(action,body={}){
 if(action==='publish')return Promise.resolve({ok:false,error:'RC7_PLAYBACK_REVIEW_REJECTED_PUBLICATION_HELD'});
 const source=process.env.GHARTV_WEB_SHA||'';
 if(!/^[a-f0-9]{40}$/.test(source))return Promise.resolve({ok:false,error:'RUNNING_SOURCE_UNVERIFIED'});
 if(process.env.GHARTV_DISABLE_KEYCHAIN==='1')return Promise.resolve({ok:false,error:'TEST_MODE_RELEASE_ACTION_DISABLED'});
 return new Promise(resolve=>{
  const p=spawn('python3',[join(ROOT,'../tools/release_control.py'),action,source],{stdio:['pipe','pipe','ignore'],env:{...process.env,GH_PROMPT_DISABLED:'1'}});let out='',done=false;
  const finish=x=>{if(done)return;done=true;clearTimeout(timer);resolve(x);};
  const timer=setTimeout(()=>{p.kill('SIGTERM');setTimeout(()=>p.kill('SIGKILL'),400).unref();finish({ok:false,error:'RELEASE_ACTION_TIMED_OUT_CHECK_PUBLIC_FEED_BEFORE_RETRY'});},action==='publish'?240000:action==='open-tv'?210000:90000);
  p.stdout.on('data',chunk=>{out+=chunk;if(out.length>256000){p.kill();finish({ok:false,error:'RELEASE_RESPONSE_BOUND'});}});
  p.on('error',()=>finish({ok:false,error:'LOCAL_PYTHON_UNAVAILABLE'}));
  p.on('close',()=>{try{finish(JSON.parse(out));}catch{finish({ok:false,error:'RELEASE_RESPONSE_INVALID'});}});
  p.stdin.on('error',()=>{});p.stdin.end(action==='publish'?JSON.stringify(body):'');
 });
}
export async function releaseRoute(req,res,url,authorized){
 if(!url.pathname.startsWith('/owner-api/release/'))return false;
 const send=(code,data)=>{res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(data));};
 if(!authorized(req)){send(401,{ok:false,error:'LOCAL_OWNER_SESSION_REQUIRED'});return true;}
 const action=url.pathname.split('/').pop();
 if(action==='status'&&req.method==='GET'){send(200,await invokeRelease('status'));return true;}
 if(!['verify','open-tv','publish'].includes(action)||req.method!=='POST'){send(405,{ok:false,error:'METHOD_NOT_ALLOWED'});return true;}
 if(req.headers.origin!==`http://${req.headers.host}`){send(403,{ok:false,error:'ORIGIN_REJECTED'});return true;}
 if(busy){send(409,{ok:false,error:'RELEASE_ACTION_ALREADY_RUNNING'});return true;}
 try{
  let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>8192){send(413,{ok:false,error:'REQUEST_TOO_LARGE'});return true;}}
  const body=JSON.parse(raw||'{}');busy=true;
  send(200,await invokeRelease(action,body));
 }catch{send(400,{ok:false,error:'RELEASE_ACTION_FAILED'});}finally{busy=false;}
 return true;
}
