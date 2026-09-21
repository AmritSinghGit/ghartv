import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {selectPlayback} from '../playback-capabilities.mjs';
import {createAppServer} from '../server.mjs';
import {safeReceipt} from '../review-sync.mjs';
const payload={result:'https://media.example/authorized.m3u8',mpd:{result:'https://media.example/authorized.mpd',key:'https://media.example/license'}};
test('Safari receives the provider-returned HLS alternative instead of Widevine DASH',()=>{
 assert.deepEqual(selectPlayback(payload,{nativeHls:true,preferNativeHls:true,widevine:false}),{protocol:'hls',url:payload.result,license:'',alternativeProvided:true});
});
test('DASH remains available for a Widevine-capable browser',()=>{
 const p=selectPlayback(payload,{nativeHls:false,widevine:true});assert.equal(p.protocol,'dash');assert.equal(p.license,payload.mpd.key);
});
test('No compatible authorized alternative produces a specific DRM error, not a fake stream',()=>{
 assert.throws(()=>selectPlayback({mpd:payload.mpd},{nativeHls:true,widevine:false}),e=>e.status===422&&e.code==='browser_drm_unavailable');
 assert.throws(()=>selectPlayback({}),e=>e.code==='stream_unavailable');
});
test('Single HLS option is retained and no new URL is invented',()=>{
 const p=selectPlayback({result:payload.result});assert.equal(p.url,payload.result);assert.equal(p.protocol,'hls');
});
test('native browser selector and gesture fallback do not rely on Brave',async()=>{
 const context={globalThis:{},setTimeout,clearTimeout};vm.runInNewContext(await readFile(new URL('../public/playback-engine.js',import.meta.url),'utf8'),context);
 const P=context.globalThis.GharTVPlayback,video={canPlayType:()=> 'maybe'},hls={isSupported:()=>true};
 assert.equal(P.engine(video,hls,{vendor:'Apple Computer, Inc.',userAgent:'Safari'},{}),'native');
 assert.equal(P.engine(video,hls,{vendor:'Google Inc.',userAgent:'Chrome'},{}),'hlsjs');
 assert.equal(P.engine({canPlayType:()=>''},null,{vendor:'',userAgent:''},{}),'unsupported');
 let gesture=0,error=0;await P.play({play:async()=>{throw Object.assign(new Error(),{name:'NotAllowedError'});}},()=>gesture++,()=>error++);
 assert.equal(gesture,1);assert.equal(error,0);
});
test('viewer does not serve analytics, collector config, support exports or owner bootstrap',async()=>{
 const server=createAppServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const base='http://127.0.0.1:'+server.address().port;
 try{
  for(const path of ['/owner.html','/owner','/owner-api/config-status','/owner-api/v1/admin/summary','/owner-api/v1/admin/export','/owner-api/support/capture','/owner-api/review/status','/provider-access.html','/release-control.html']){
   const r=await fetch(base+path);assert.equal(r.status,404,path);assert.doesNotMatch(await r.text(),/owner-bootstrap|GHARTV_TELEMETRY_ADMIN_TOKEN/);
  }
  const index=await fetch(base+'/');const html=await index.text();assert.doesNotMatch(html,/Owner console|owner\.html|owner-api/);
  assert.match(index.headers.get('content-security-policy'),/worker-src 'self' blob:/);
  const health=await (await fetch(base+'/api/health')).json();assert.equal(health.owner_reader,false);assert.equal(health.analytics,'NOT_SERVED_BY_VIEWER');
  const films=await (await fetch(base+'/flixmomo.html')).text();assert.match(films,/Inside GharTV/);assert.doesNotMatch(films,/\/owner.html|Owner analytics/);
  assert.equal((await fetch(base+'/api/films/status')).status,401);
  assert.equal((await fetch(base+'/api/films/status',{headers:{Origin:'https://untrusted.invalid'}})).status,403);
  assert.equal((await fetch(base+'/flixmomo.html',{headers:{'x-operon-preview':'fake'}})).status,403);
 }finally{server.closeAllConnections();await new Promise(r=>server.close(r));}
});
test('safe receipt includes the blocker and exact web source without private paths or raw text',()=>{
 const receipt=safeReceipt({run_id:'GHARTV-CYAN-16-20260921T010000Z-1234',review_source:'a'.repeat(40),web_source:'b'.repeat(40),version:'0.6.0-rc10.1-web-films',version_code:28,status:'WEB_REVIEW_READY_ANDROID_HELD',phase:'RESOURCE_PREFLIGHT',blocker:'HOST_PRESSURE_WARNING_NO_NEW_EMULATOR: /Users/private/account@example.com',prepared_signed_apk:'PERSISTED_AND_HASH_VERIFIED_BEFORE_EMULATOR_SELECTION',signed_apk_sha256:'c'.repeat(64),web_player:'HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED',analytics_routes:'DISABLED_IN_VIEWER'});
 assert.equal(receipt.blocker_code,'HOST_PRESSURE_WARNING_NO_NEW_EMULATOR');assert.equal(receipt.signed_apk_prepared,true);assert.equal(receipt.web_ready,true);assert.equal(receipt.phase,'RESOURCE_PREFLIGHT');assert.equal(receipt.web_source,'b'.repeat(40));assert.doesNotMatch(JSON.stringify(receipt),/Users|private|account@example/);
});
