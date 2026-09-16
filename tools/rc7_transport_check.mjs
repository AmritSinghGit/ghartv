import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
process.env.GHARTV_DISABLE_KEYCHAIN='1';
process.env.GHARTV_WEB_HOST='127.0.0.1';
process.env.GHARTV_WEB_PORT='8790';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const src=await readFile(path.join(root,'web-player/server.mjs'),'utf8');
const segment=src.slice(src.indexOf('function stringMap('),src.indexOf('function playbackHeaders(')).replace('export function','function');
let failPrimary=true,failAll=false,slow=false,calls=0;
const code=`let catalogueCache={expiresAt:0,fetchedAt:0,channels:[]};let catalogueFlight=null;let catalogueOutcome={};const MOBILE_USER_AGENT='fixture';const JIO={dictionary:'dict',channels14:'a',channels31:'b',logoBase:'https://example.invalid/'};const now=()=>Date.now();${segment};globalThis.test={fetchCatalogue,outcome:()=>catalogueOutcome,expire:()=>{catalogueCache.expiresAt=0;},age:()=>{catalogueCache.fetchedAt=Date.now()-73*60*60*1000;catalogueCache.expiresAt=0;}};`;
const box=vm.createContext({Date,Map,Set,Promise,Error,Number,String,Boolean,console,upstreamJson:async(url)=>{
 calls++;if(slow)await new Promise(r=>setTimeout(r,10));
 if(failAll || (url==='a'&&failPrimary))throw Object.assign(new Error('Gateway Timeout Nginx'),{status:504});
 if(url==='dict')return {payload:{channelCategoryMapping:{7:'News'},languageIdMapping:{12:'Punjabi'}}};
 return {payload:{result:[{channel_id:url==='a'?1:2,channel_name:'Fixture '+url,channel_order:url==='a'?2:1,channelCategoryId:7,channelLanguageId:12}]}};
}});
vm.runInContext(code,box);
const first=await box.test.fetchCatalogue();assert.equal(first.length,1);assert.equal(first[0].language,'Punjabi');assert.equal(box.test.outcome().status,'partial_source');assert.equal(calls,3);
failPrimary=false;calls=0;box.test.expire();slow=true;
const all=await Promise.all(Array.from({length:20},()=>box.test.fetchCatalogue()));assert.equal(calls,3);assert.equal(all[0].length,2);assert.equal(all[0][0].id,'2');assert.equal(box.test.outcome().status,'fresh');
failAll=true;box.test.expire();const stale=await box.test.fetchCatalogue();assert.equal(stale.length,2);assert.equal(box.test.outcome().status,'stale');
box.test.age();await assert.rejects(()=>box.test.fetchCatalogue(),e=>e.status===503&&!e.message.includes('Nginx'));
console.log('GUIDE_PRIMARY_FAILURE_FALLBACK_COALESCING_STALE_AGE_AND_ERROR=PASS');
const {createAppServer}=await import('../web-player/server.mjs');
const server=createAppServer();await new Promise((r,j)=>{server.once('error',j);server.listen(8790,'127.0.0.1',r);});
try{
 const request=async(headers)=>fetch('http://127.0.0.1:8790/api/playback',{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify({channelId:'1'})});
 assert.equal((await request({Origin:'http://127.0.0.1:8790','Sec-Fetch-Site':'same-origin'})).status,401);
 assert.equal((await request({Origin:'http://ghartv-api-8790.localhost:43918','Sec-Fetch-Site':'same-origin'})).status,401);
 assert.equal((await request({Origin:'https://attacker.invalid','X-Forwarded-Host':'ghartv-api-8790.localhost:43918','Sec-Fetch-Site':'same-origin'})).status,403);
 assert.equal((await request({Origin:'http://arbitrary.localhost:43918','Sec-Fetch-Site':'same-origin'})).status,403);
 assert.equal((await request({Origin:'http://ghartv-api-8790.localhost:43919','Sec-Fetch-Site':'same-origin'})).status,403);
 assert.equal((await request({Origin:'http://ghartv-api-8790.localhost:43918','Sec-Fetch-Site':'cross-site'})).status,403);
 assert.equal((await fetch('http://127.0.0.1:8790/owner-api/v1/admin/summary')).status,401);
 console.log('REAL_HTTP_EXACT_LOCAL_FABRIC_ORIGIN_ACCEPTED_AUTH_REMAINS_REQUIRED_OTHER_ORIGINS_REJECTED=PASS');
}finally{await new Promise(r=>server.close(r));}
