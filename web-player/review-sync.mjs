/** Safe receipt mirror for the EXISTING GharTV PR. No private log or feedback upload. */
import {readFile,writeFile,mkdir,lstat,rename,unlink} from 'node:fs/promises';
import {homedir} from 'node:os';
import {join,dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash,randomBytes} from 'node:crypto';
import {spawn} from 'node:child_process';
const ROOT=join(homedir(),'Library/Application Support/GharTV/owner-review');
const SYNC=join(ROOT,'receipt-sync');
const REPO='AmritSinghGit/ghartv';const ISSUE=1;const MARKER='<!-- GHARTV_SAFE_RECEIPT_V1 -->';
function hash(value){return createHash('sha256').update(value).digest('hex');}
async function safe(path){for(let p=resolve(path);;p=dirname(p)){try{const s=await lstat(p);if(s.isSymbolicLink())throw new Error('SYMLINK_REFUSED');}catch(e){if(e.code!=='ENOENT')throw e;}if(p===dirname(p))break;}}
async function privateWrite(path,value){await safe(path);await mkdir(dirname(path),{recursive:true,mode:0o700});const temp=path+'.tmp-'+randomBytes(6).toString('hex');await writeFile(temp,value,{mode:0o600,flag:'wx'});await rename(temp,path);}
async function boundedJson(path){await safe(path);const s=await lstat(path);if(!s.isFile()||s.size>128000||s.uid!==process.getuid())throw new Error('RECEIPT_FILE_REFUSED');return JSON.parse(await readFile(path,'utf8'));}
export function safeReceipt(r){
  if(!/^GHARTV-CYAN-\d+(?:-[A-Z0-9-]+)?-\d{8}T\d{6}Z-\d+$/.test(r.run_id||''))throw new Error('RUN_ID_INVALID');
  if(!/^[a-f0-9]{40}$/.test(r.review_source||''))throw new Error('SOURCE_INVALID');
  if(!/^\d[0-9A-Za-z.-]{1,70}$/.test(r.version||'')||!Number.isSafeInteger(r.version_code))throw new Error('VERSION_INVALID');
  const pressure=v=>['NORMAL','WARNING','CRITICAL','NOT_REPORTED'].includes(v)?v:'NOT_MEASURED';
  const digest=v=>/^[a-f0-9]{64}$/.test(v||'')?v:null;
  return {schema:'ghartv.safe-owner-receipt.v1',repository:REPO,lane:'ghartv',run_id:r.run_id,
    review_source:r.review_source,version:r.version,version_code:r.version_code,
    unsigned_apk_sha256:digest(r.unsigned_apk_sha256),signed_apk_sha256:digest(r.signed_apk_sha256),
    production_source:/^[a-f0-9]{40}$/.test(r.production_source||'')?r.production_source:null,
    result:['REVIEW_READY','ACTION_REQUIRED','WEB_REVIEW_READY_ANDROID_HELD','WEB_REVIEW_READY_ANDROID_NOT_TOUCHED','SIGNED_UPDATE_PREPARED_REVIEW_PENDING','ANDROID_READY_WINDOW_UNCONFIRMED','FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED','ANDROID_HELD_RESOURCE_PRESSURE'].includes(r.status)?r.status:'OTHER_LOCAL_STATE',
    phase:['RESOURCE_PREFLIGHT','VERIFY_APK_CERTIFICATE','EMULATOR_SELECTION','REVIEW_OPEN','SIGNED_UPDATE_READY','CHECKOUT_OBSERVATION_ONLY','CONTINUITY','STARTING'].includes(r.phase)?r.phase:'OTHER_PHASE',
    blocker_code:typeof r.blocker==='string'&&/^HOST_PRESSURE_(WARNING|CRITICAL|NOT_REPORTED)_NO_NEW_EMULATOR:/.test(r.blocker)?r.blocker.split(':',1)[0]:typeof r.blocker==='string'&&r.blocker.startsWith('TV_WINDOW_')?'TV_WINDOW_NOT_CONFIRMED':r.blocker?'OTHER_BLOCKER_SEE_PRIVATE_RECEIPT':null,
    mac_window:['MAC_WINDOW_FRONTMOST_OBSERVED','MAC_WINDOW_ONSCREEN_OBSERVED','MAC_WINDOW_NOT_ONSCREEN','MAC_WINDOW_UNCONFIRMED','MAC_WINDOW_QUERY_UNAVAILABLE','MAC_TARGET_CHANGED_PRESERVED','MAC_TARGET_UNCONFIRMED','MAC_TARGET_NOT_UNIQUE','HEADLESS_EMULATOR_PRESERVED','ANDROID_STUDIO_EMBEDDED_WINDOW'].includes(r.mac_window)?r.mac_window:'NOT_CHECKED',
    mac_window_observed:r.mac_window_observed===true,
    mac_window_frontmost:r.mac_window_frontmost===true,
    web_source:/^[a-f0-9]{40}$/.test(r.web_source||'')?r.web_source:null,
    web_ready:r.web_player==='HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED',
    signed_apk_prepared:r.prepared_signed_apk==='PERSISTED_AND_HASH_VERIFIED_BEFORE_EMULATOR_SELECTION',
    viewer_analytics:r.analytics_routes==='DISABLED_IN_VIEWER'?'DISABLED_IN_VIEWER':'NOT_CHECKED',
    next_action:r.status==='ANDROID_HELD_RESOURCE_PRESSURE'?'REVIEW_PREPARED_APK_OR_RELEASE_IDENTIFIED_IDLE_RESOURCES':r.status==='ANDROID_READY_WINDOW_UNCONFIRMED'?'SHOW_EXISTING_NOVA_WINDOW_NO_SECOND_VM':r.status==='WEB_REVIEW_READY_ANDROID_HELD'?'REVIEW_WEB_OR_SIGNED_APK_NO_NEW_EMULATOR':'READ_CURRENT_PHASE_AND_OWNER_FEEDBACK',
    installed_foreground:/^RC\d+_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND$/.test(r.emulator||''),
    obsidian_readback:r.obsidian==='WRITTEN_AND_READBACK_VERIFIED',
    bridge:r.memory_bridge?.includes('TIMEOUT')?'PENDING_TIMEOUT':r.memory_bridge?.includes('EXIT_0_EXIT_0')?'COMMANDS_EXITED_ZERO_REPLICA_UNVERIFIED':'NOT_CONFIRMED',
    collector_config_present:r.collector_config==='PRESENT',collector_auth_verified:['SUCCESS','VERIFIED_BY_SUPPORT_READ'].includes(r.collector_auth),
    signed_asset_publication:r.review_release==='SIGNED_REVIEW_ASSET_VERIFIED'?'VERIFIED':'NOT_VERIFIED',
    host_memory_pressure_before:pressure(r.host_memory_pressure_before),host_memory_pressure_after:pressure(r.host_memory_pressure_after),performance_report_saved:r.performance_before==='MEASURED_LOCALLY'||r.performance_after==='MEASURED_LOCALLY',physical_tv:'NOT_VERIFIED',owner_decision:r.owner_decision==='REJECTED_PLAYBACK_BLOCKED'?'REJECTED_PLAYBACK_BLOCKED':'REVIEW_PENDING',cleanup_count:Number.isSafeInteger(r.cleanup_count)&&r.cleanup_count>=0?r.cleanup_count:0,cleanup_bytes:Number.isSafeInteger(r.cleanup_bytes)&&r.cleanup_bytes>=0?r.cleanup_bytes:0};
}
export async function latestReview(){
  let r;
  try{r=await boundedJson(join(ROOT,'current/receipt.json'));}
  catch(e){
    // Older launchers saved only current/handoff.txt. Read only that exact bounded local file.
    const path=join(ROOT,'current/handoff.txt');await safe(path);const s=await lstat(path);if(!s.isFile()||s.size>128000||s.uid!==process.getuid())throw e;
    r={};for(const line of (await readFile(path,'utf8')).split('\n')){const i=line.indexOf('=');if(i>0){const k=line.slice(0,i).toLowerCase();r[k]=line.slice(i+1);}}r.version_code=Number(r.version_code);
  }
  const receipt=safeReceipt(r);let transport={status:'NOT_SENT'};
  try{const saved=await boundedJson(join(SYNC,'state.json'));if(saved.run_id===receipt.run_id)transport={status:saved.status,url:validCommentUrl(saved.url)?saved.url:null,verified_at:saved.verified_at||null};}catch{}
  return {ok:true,receipt,transport,private_feedback_uploaded:false};
}
function validCommentUrl(url){return typeof url==='string'&&/^https:\/\/github\.com\/AmritSinghGit\/ghartv\/pull\/1#issuecomment-\d+$/.test(url);}
async function gh(args,input,deadline){
  const remaining=deadline-Date.now();if(remaining<500)throw new Error('SYNC_BUDGET_EXPIRED');
  return new Promise((resolve,reject)=>{
    const child=spawn('gh',['api',...args],{stdio:['pipe','pipe','ignore'],env:{...process.env,GH_PROMPT_DISABLED:'1',GH_PAGER:'cat'}});let raw='',done=false;
    const finish=(err,result)=>{if(done)return;done=true;clearTimeout(timer);err?reject(err):resolve(result);};
    const timer=setTimeout(()=>{child.kill('SIGTERM');setTimeout(()=>child.kill('SIGKILL'),300).unref();finish(new Error('SYNC_TIMEOUT'));},Math.min(7000,remaining));
    child.on('error',()=>finish(new Error('GITHUB_CLI_UNAVAILABLE')));
    child.stdout.on('data',chunk=>{raw+=chunk;if(raw.length>2*1024*1024){child.kill();finish(new Error('GITHUB_RESPONSE_BOUND'));}});
    child.on('close',code=>{if(code!==0)return finish(new Error('GITHUB_REQUEST_FAILED'));try{finish(null,JSON.parse(raw));}catch{finish(new Error('GITHUB_RESPONSE_INVALID'));}});
    child.stdin.on('error',()=>{});child.stdin.end(input?JSON.stringify(input):undefined);
  });
}
let active=false;
export async function publishReceipt(){
  if(active)return {ok:false,status:'SYNC_ALREADY_RUNNING'};
  active=true;let handle,packet;
  try{
    packet=(await latestReview()).receipt;const deadline=Date.now()+18000;
    await safe(SYNC);await mkdir(SYNC,{recursive:true,mode:0o700});const lock=join(SYNC,'sync.lock');
    try{const {open}=await import('node:fs/promises');handle=await open(lock,'wx',0o600);}
    catch(e){
      if(e.code!=='EEXIST')throw e;
      // Only reclaim a private lock whose recorded process no longer exists.
      await safe(lock);const info=await lstat(lock);const pidText=await readFile(lock,'utf8');
      if(!info.isFile()||info.uid!==process.getuid()||info.size>24||!/^[1-9]\d*$/.test(pidText))return{ok:false,status:'SYNC_LOCK_REQUIRES_ATTENTION'};
      let alive=true;try{process.kill(Number(pidText),0);}catch(error){if(error.code==='ESRCH')alive=false;}
      if(alive)return{ok:false,status:'SYNC_ALREADY_RUNNING'};
      await unlink(lock);const {open}=await import('node:fs/promises');handle=await open(lock,'wx',0o600);
    }
    await handle.writeFile(String(process.pid));
    const body=MARKER+'\n## GharTV latest local review receipt\n\n'+
      'Automatically copied from the owner launcher. These are owner-device observations, not server-side or physical-TV verification. No private feedback, paths, names, account IDs, tokens, raw logs or screenshots are included.\n\n'+
      '```json\n'+JSON.stringify(packet,null,2)+'\n```\n\nRECEIPT_SHA256='+hash(JSON.stringify(packet))+'\n';
    const bodyHash=hash(body),pending=join(SYNC,'pending.json');await privateWrite(pending,JSON.stringify({packet,body_hash:bodyHash})+'\n');
    if(process.env.GHARTV_DISABLE_KEYCHAIN==='1')return{ok:false,status:'TEST_MODE_NO_NETWORK'};
    let saved={};try{saved=await boundedJson(join(SYNC,'state.json'));}catch{}
    if(saved.run_id===packet.run_id&&saved.body_hash===bodyHash&&saved.status==='GITHUB_READBACK_VERIFIED'){await unlink(pending).catch(()=>{});return{ok:true,status:saved.status,url:saved.url};}
    let existing=null;
    if(Number.isSafeInteger(saved.comment_id)){
      const c=await gh([`repos/${REPO}/issues/comments/${saved.comment_id}`],null,deadline);
      if(c.issue_url===`https://api.github.com/repos/${REPO}/issues/${ISSUE}`&&c.user?.login==='AmritSinghGit'&&c.body?.startsWith(MARKER))existing=c;
      else throw new Error('MIRROR_IDENTITY_CONFLICT');
    }else{
      // No blind repeated create after an ambiguous POST: search bounded existing comments first.
      for(let page=1;page<=3;page++){
        const rows=await gh([`repos/${REPO}/issues/${ISSUE}/comments?per_page=100&page=${page}`],null,deadline);
        if(!Array.isArray(rows))throw new Error('MIRROR_LOOKUP_INVALID');
        existing=rows.find(c=>c.user?.login==='AmritSinghGit'&&c.body?.startsWith(MARKER));
        if(existing||rows.length<100)break;
        if(page===3)throw new Error('MIRROR_LOOKUP_BOUND_PENDING');
      }
    }
    // An older queued run must not replace a newer mirror.
    const other=existing?.body?.match(/"run_id": "(GHARTV-CYAN-[^"]+)"/)?.[1];
    const stamp=id=>id?.match(/(\d{8}T\d{6}Z)-\d+$/)?.[1]||'';
    if(other&&stamp(other)>stamp(packet.run_id))throw new Error('NEWER_MIRROR_PRESERVED');
    let comment=existing;
    if(existing?.body!==body){
      comment=await gh(existing?['--method','PATCH',`repos/${REPO}/issues/comments/${existing.id}`,'--input','-']:['--method','POST',`repos/${REPO}/issues/${ISSUE}/comments`,'--input','-'],{body},deadline);
    }
    if(!Number.isSafeInteger(comment?.id)||comment.issue_url!==`https://api.github.com/repos/${REPO}/issues/${ISSUE}`)throw new Error('MIRROR_WRITE_UNCONFIRMED');
    // Store concrete id before readback so retries never create another current mirror.
    const state={run_id:packet.run_id,comment_id:comment.id,body_hash:bodyHash,status:'READBACK_PENDING',url:`https://github.com/${REPO}/pull/${ISSUE}#issuecomment-${comment.id}`};
    await privateWrite(join(SYNC,'state.json'),JSON.stringify(state)+'\n');
    const check=await gh([`repos/${REPO}/issues/comments/${comment.id}`],null,deadline);
    if(check.body!==body||check.issue_url!==comment.issue_url)throw new Error('MIRROR_READBACK_DIFFERENT');
    state.status='GITHUB_READBACK_VERIFIED';state.verified_at=new Date().toISOString();
    await privateWrite(join(SYNC,'state.json'),JSON.stringify(state)+'\n');await unlink(pending).catch(()=>{});
    return {ok:true,status:state.status,url:state.url,run_id:packet.run_id};
  }catch(e){
    const permitted=new Set(['SYNC_BUDGET_EXPIRED','SYNC_TIMEOUT','GITHUB_CLI_UNAVAILABLE','GITHUB_RESPONSE_BOUND','GITHUB_REQUEST_FAILED','GITHUB_RESPONSE_INVALID','MIRROR_IDENTITY_CONFLICT','MIRROR_LOOKUP_INVALID','MIRROR_LOOKUP_BOUND_PENDING','NEWER_MIRROR_PRESERVED','MIRROR_WRITE_UNCONFIRMED','MIRROR_READBACK_DIFFERENT']);
    const status='PENDING_'+(permitted.has(e.message)?e.message:'LOCAL_RECEIPT_UNAVAILABLE');
    if(packet)try{let prior={};try{prior=await boundedJson(join(SYNC,'state.json'));}catch{}await privateWrite(join(SYNC,'state.json'),JSON.stringify({...prior,status,run_id:packet.run_id})+'\n');}catch{}
    return {ok:false,status,run_id:packet?.run_id};
  }finally{active=false;if(handle){await handle.close();await unlink(join(SYNC,'sync.lock')).catch(()=>{});}}
}
export async function saveFeedback(body){
  if(typeof body.text!=='string'||!body.text.trim()||body.text.length>8000)throw new Error('FEEDBACK_LENGTH');
  const r=(await latestReview()).receipt;if(body.run_id!==r.run_id)throw new Error('REVIEW_CHANGED_REFRESH_FIRST');
  const key=r.run_id+'-'+Date.now()+'-'+randomBytes(3).toString('hex');
  const note='# GharTV owner feedback\n\nRun: '+r.run_id+'\nReview: '+r.version+' / code '+r.version_code+'\nSource: '+r.review_source+'\n\n'+body.text.trim()+'\n';
  await privateWrite(join(ROOT,'feedback',key+'.md'),note);
  let obsidian=false;const vault=join(homedir(),'Documents/Amrit Executive Memory');
  try{const s=await lstat(vault);if(s.isDirectory()&&!s.isSymbolicLink()){await privateWrite(join(vault,'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv',key+'-feedback.md'),note);obsidian=true;}}catch{}
  return {ok:true,run_id:r.run_id,saved_locally:true,obsidian,uploaded:false};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const result=process.argv.includes('--publish')?await publishReceipt():await latestReview().catch(()=>({ok:false,status:'NO_LOCAL_RECEIPT'}));
  console.log(JSON.stringify(result));
}
