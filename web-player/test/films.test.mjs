import test from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import {filmURL,torProxy,launchOptions,normalizeResults} from '../film-browser-worker.mjs';
import {makeFilmRoute,filmHTML} from '../film-search.mjs';
const origin='http://127.0.0.1:8790';
function req(path,{method='GET',body,headers={}}={}){const r=Readable.from(body?[Buffer.from(JSON.stringify(body))]:[]);r.method=method;r.headers={host:'127.0.0.1:8790',origin,'content-type':'application/json',...headers};return r;}
function response(){return {status:0,headers:{},body:'',writeHead(s,h){this.status=s;this.headers=h;},end(b){this.body=b;}};}
async function call(path,options,authorized=()=>true,discover=async()=>null){const r=response(),route=makeFilmRoute({discover,start(){throw Error('UNEXPECTED_BROWSER_START');}});assert.equal(await route(req(path,options),r,new URL(path,origin),authorized,'a'.repeat(64)),true);return r;}
test('film URLs are restricted to the exact registered provider and catalogue paths',()=>{
 assert.equal(filmURL('https://flixmomo.app/movie/438631/dune/watch?p=1'),'https://flixmomo.app/movie/438631/dune/watch?p=1');
 for(const v of ['https://flixmomo.app.evil/movie/1/a','https://user:pass@flixmomo.app/movie/1/a','http://flixmomo.app/movie/1/a','https://127.0.0.1/movie/1/a','https://flixmomo.app/api/private'])assert.throws(()=>filmURL(v));
});
test('Tor accepts only local SOCKS on supported ports, no arbitrary proxy or credentials',()=>{
 assert.equal(torProxy(),'socks5://127.0.0.1:9050');assert.equal(torProxy('socks5://127.0.0.1:9150'),'socks5://127.0.0.1:9150');
 for(const v of ['http://127.0.0.1:9050','socks5://localhost:9050','socks5://example.org:9050','socks5://u:p@127.0.0.1:9050','socks5://127.0.0.1:9050/direct'])assert.throws(()=>torProxy(v));
});
test('Tor launch has no direct fallback, suppresses local DNS, keeps sandbox on',()=>{
 const o=launchOptions({executable:'/browser',route:'tor'});assert.equal(o.chromiumSandbox,true);assert.equal(o.proxy.server,'socks5://127.0.0.1:9050');assert.ok(o.args.some(a=>a.includes('MAP * ~NOTFOUND')));assert.ok(o.args.includes('--disable-quic'));assert.ok(!o.args.some(a=>a.includes('no-sandbox')||a.includes('direct://')));
 assert.equal(launchOptions({executable:'/browser',route:'direct'}).proxy,undefined);assert.throws(()=>launchOptions({route:'automatic'}));
});
test('results are real-provider bounded, normalized and deduplicated by stable ID',()=>{
 const r=normalizeResults([{url:'/movie/12/a',title:'  Film   A  '},{url:'/movie/12/a/watch',title:'Play'},{url:'https://evil.invalid/movie/4/a',title:'Bad'},...Array.from({length:90},(_,i)=>({url:'/tv/'+i+'/a',title:'Series '+i}))]);
 assert.equal(r.length,40);assert.deepEqual(r[0],{id:'movie:12',title:'Film A',url:'https://flixmomo.app/movie/12/a',type:'Movie'});
});
test('film page is private from Fabric previews, with frame and script CSP',async()=>{
 assert.equal((await call('/flixmomo.html',{headers:{'x-operon-preview':'1'}})).status,404);
 const ok=await call('/flixmomo.html',{});assert.equal(ok.status,200);assert.match(ok.headers['Content-Security-Policy'],/frame-ancestors 'none'/);assert.match(ok.body,/Tor selected/);
});
test('film APIs require owner session and reject cross-origin POST',async()=>{
 assert.equal((await call('/owner-api/films/status',{},()=>false)).status,401);
 assert.equal((await call('/owner-api/films/search',{method:'POST',body:{query:'dune',route:'tor'},headers:{origin:'https://evil.invalid'}})).status,403);
});
test('missing browser yields actionable failure rather than invented results',async()=>{
 const r=await call('/owner-api/films/search',{method:'POST',body:{query:'dune',route:'direct'}});assert.equal(r.status,400);assert.equal(JSON.parse(r.body).error,'INSTALL_BRAVE_OR_CHROMIUM_FIRST');
});
test('unrecognized routes and arbitrary open URLs are rejected',async()=>{
 const r=await call('/owner-api/films/open',{method:'POST',body:{id:'movie:1',url:'https://evil.invalid',route:'direct'}});assert.equal(JSON.parse(r.body).error,'SEARCH_RESULT_EXPIRED_SEARCH_AGAIN');
 assert.equal((await call('/owner-api/films/search',{method:'POST',body:{query:'dune',route:'auto'}})).status,400);
});
test('UI never claims server Tor for embedded direct playback and has no report polling',()=>{
 const html=filmHTML('a'.repeat(64));assert.match(html,/Embedded playback cannot use the server's Tor route/);assert.match(html,/embed.disabled=\$\('route'\).value==='tor'/);assert.doesNotMatch(html,/setInterval|localStorage|collector.env|admin\/summary/);
});

test('parallel requests reserve browser admission before discovery resolves',async()=>{
 let release;const discovery=new Promise(resolve=>{release=resolve;});
 const route=makeFilmRoute({discover:()=>discovery});const a=response(),b=response();
 const first=route(req('/owner-api/films/search',{method:'POST',body:{query:'dune',route:'direct'}}),a,new URL('/owner-api/films/search',origin),()=>true,'a'.repeat(64));
 await new Promise(resolve=>setImmediate(resolve));
 await route(req('/owner-api/films/search',{method:'POST',body:{query:'dune',route:'direct'}}),b,new URL('/owner-api/films/search',origin),()=>true,'a'.repeat(64));
 assert.equal(b.status,409);release(null);await first;assert.equal(a.status,400);
});

test('manual provider browse uses only the registered home and explicit route',async()=>{
 const r=await call('/owner-api/films/browse',{method:'POST',body:{route:'direct'}});
 assert.equal(JSON.parse(r.body).error,'INSTALL_BRAVE_OR_CHROMIUM_FIRST');
 assert.match(filmHTML('a'.repeat(64)),/Complete any verification yourself/);
});
