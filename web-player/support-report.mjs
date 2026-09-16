// Local-only support collection. All requests are explicit and bounded; no provider/session data.
import {mkdir,writeFile,lstat,readFile} from 'node:fs/promises';
import {join,resolve} from 'node:path';
import {homedir} from 'node:os';
import {randomBytes} from 'node:crypto';
const root=()=>process.env.GHARTV_REVIEW_STATE||join(homedir(),'Library/Application Support/GharTV/owner-review');
const token=v=>typeof v==='string'&&/^[A-Za-z0-9_. -]{1,80}$/.test(v)?v:'UNREPORTED';
const count=v=>Number.isSafeInteger(v)&&v>=0?v:null;
const stamp=v=>v!=null&&Number.isFinite(new Date(v).getTime())?new Date(v).toISOString():null;
export function validReference(v){return /^GH-[A-F0-9]{8}$/.test(String(v||''));}
export function projectEvent(e){const a=e.attributes||{};return {reference:validReference(e.reference)?e.reference:null,received_at:stamp(e.received_at),client_at:stamp(e.client_ts),version:token(e.app_version),version_code:count(e.version_code),event:token(e.event_name),stage:token(a.stage),error_type:token(a.error_type),cause_type:token(a.cause_type),failure_kind:token(a.failure_kind),http_status:Number.isInteger(a.http_status)&&a.http_status>=100&&a.http_status<=599?a.http_status:null,failed_channel_id:/^[0-9]{1,8}$/.test(String(a.failed_channel_id||''))?String(a.failed_channel_id):null};}
export function supportProjection(summary,exported,reference=''){
 if(summary?.ok!==true || !Array.isArray(exported?.events)||exported.ok!==true)throw new Error('INVALID_REPORT');
 const rows=exported.events, errors=rows.filter(e=>['app_error','app_crash','playback_failure'].includes(e.event_name)),groups=new Map();
 for(const e of errors){const item=projectEvent(e);const key=JSON.stringify([item.version_code,item.stage,item.failure_kind,item.cause_type,item.http_status,item.failed_channel_id]);const group=groups.get(key)||{...item,reference:undefined,client_at:undefined,received_at:undefined,count:0};group.count++;groups.set(key,group);}
 return {schema:'ghartv.private-support-signals.v1',captured_at:new Date().toISOString(),display_timezone:'Asia/Kolkata',window_days:summary.window_days,summary_generated_at:stamp(summary.generated_at),totals:{events:count(summary.totals?.events),installations:count(summary.totals?.installations)},sample:{count:rows.length,limit:5000,cap_reached:rows.length>=5000,oldest_received:rows.length?stamp(Math.min(...rows.map(e=>Number(e.received_at)).filter(Number.isFinite))):null,not_complete_history:rows.length>=5000},versions:(summary.versions||[]).map(v=>({version:token(v.app_version),code:count(v.version_code),events:count(v.events),installations:count(v.installations)})),failure_groups:[...groups.values()].sort((a,b)=>b.count-a.count).slice(0,150),reference:reference||null,matching_errors:reference?errors.filter(e=>String(e.reference).toUpperCase()===reference).slice(0,50).map(projectEvent):[],coverage_notice:'Summary totals are not concurrent viewers. Error samples span reported versions, including old releases and emulators. An absent reference may be unsent, opted out, expired, or outside the export cap. No raw event attributes, model/installation IDs, names, paths, tokens, programme history or URLs are included in this shareable projection.'};
}
async function privateDir(dir){for(let p=resolve(dir);p!=='/';p=resolve(p,'..')){try{if((await lstat(p)).isSymbolicLink())throw Error('UNSAFE_SUPPORT_PATH');}catch(e){if(e.code!=='ENOENT')throw e;}}await mkdir(dir,{recursive:true,mode:0o700});}
export async function captureSupport(read,days=7,reference=''){
 if(![1,7,30].includes(Number(days))||reference&&!validReference(reference))throw Error('INVALID_SUPPORT_REQUEST');
 const results=await Promise.allSettled([read('/v1/admin/summary?days='+days),read('/v1/admin/export?days='+days+'&limit=5000')]);
 if(results.some(r=>r.status!=='fulfilled'))return {ok:false,error:'COLLECTOR_READ_INCOMPLETE',steps:results.map((r,i)=>({part:i?'sample':'summary',status:r.status==='fulfilled'?'SUCCESS':r.reason?.code||'FAILED'}))};
 const report=supportProjection(results[0].value,results[1].value,reference);const id='support-'+Date.now()+'-'+randomBytes(4).toString('hex');
 const dir=join(root(),'support',id);await privateDir(dir);
 // Raw evidence remains private and is never returned through the download endpoint.
 await writeFile(join(dir,'private-collector-export.json'),JSON.stringify(results[1].value),{mode:0o600,flag:'wx'});
 await writeFile(join(dir,'SUPPORT_SIGNALS.json'),JSON.stringify(report,null,2)+'\n',{mode:0o600,flag:'wx'});
 return {ok:true,capture_id:id,report};
}
export async function readSupport(id){if(!/^support-[0-9]{13}-[a-f0-9]{8}$/.test(id))throw Error('INVALID_SUPPORT_ID');const file=join(root(),'support',id,'SUPPORT_SIGNALS.json');await privateDir(join(root(),'support',id));const s=await lstat(file);if(!s.isFile()||s.isSymbolicLink()||s.size>200000)throw Error('UNSAFE_SUPPORT_FILE');return JSON.parse(await readFile(file,'utf8'));}
