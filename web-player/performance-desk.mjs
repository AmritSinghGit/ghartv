import {readFile} from 'node:fs/promises';
import {homedir} from 'node:os';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
let busy=false;
const state=join(homedir(),'Library/Application Support/GharTV/owner-review/performance/latest.json');
export async function performanceRoute(req,res,url,authorized){
 if(!url.pathname.startsWith('/owner-api/performance/'))return false;
 const send=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(data));};
 if(req.headers['x-operon-preview']||req.ghartvViewer?.preview){send(404,{error:'PRIVATE_ROUTE'});return true;}
 if(!authorized(req)){send(401,{error:'LOCAL_OWNER_REQUIRED'});return true;}
 if(req.method==='GET'&&url.pathname==='/owner-api/performance/latest'){
  try{send(200,{ok:true,report:JSON.parse(await readFile(state,'utf8'))});}catch{send(200,{ok:false,status:'NOT_MEASURED'});}return true;
 }
 if(req.method!=='POST'||url.pathname!=='/owner-api/performance/capture'){send(405,{error:'METHOD_NOT_ALLOWED'});return true;}
 if(req.headers.origin!==`http://${req.headers.host}`){send(403,{error:'ORIGIN_REJECTED'});return true;}
 if(busy){send(409,{error:'CHECK_RUNNING'});return true;}busy=true;
 try{
  const script=join(dirname(fileURLToPath(import.meta.url)),'../tools/performance/host_check.py');
  await new Promise((resolve,reject)=>execFile('python3',[script,'--output',state],{timeout:45000,maxBuffer:65536},e=>e?reject(e):resolve()));
  send(200,{ok:true,report:JSON.parse(await readFile(state,'utf8'))});
 }catch{send(500,{error:'PERFORMANCE_CAPTURE_NOT_COMPLETED'});}finally{busy=false;}return true;
}
