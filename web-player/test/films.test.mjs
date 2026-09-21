import test from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import {providerTarget,openInTorBrowser} from '../native-provider.mjs';
import {makeFilmRoute,filmHTML} from '../film-search.mjs';
const origin='http://127.0.0.1:8790';
function req({method='GET',body,headers={}}={}){const r=Readable.from(body?[Buffer.from(JSON.stringify(body))]:[]);r.method=method;r.headers={host:'127.0.0.1:8790',origin,'content-type':'application/json',...headers};return r;}
function response(){return {status:0,headers:{},body:'',writeHead(s,h){this.status=s;this.headers=h;},end(b){this.body=b;}};}
async function call(path,options,authorized=()=>true,settings={find:async()=>null}){const r=response(),route=makeFilmRoute(settings);assert.equal(await route(req(options),r,new URL(path,origin),authorized,'a'.repeat(64)),true);return r;}
test('Browse is exactly the provider root, never a synthetic movie route',()=>{assert.equal(providerTarget('browse'),'https://flixmomo.app/');});
test('Search uses the provider own verified q route and encodes punctuation and Punjabi',()=>{
 for(const query of ['Dune','ਜੱਟ ਐਂਡ ਜੂਲੀਅਟ','A&B #1 / test?','https://private.invalid/']){const u=new URL(providerTarget('search',query));assert.equal(u.origin,'https://flixmomo.app');assert.equal(u.pathname,'/search');assert.equal(u.searchParams.get('q'),query);assert.equal(u.hash,'');}
});
test('Invalid actions, control input and unbounded queries are rejected',()=>{for(const [a,q] of [['open','x'],['search','x'],['search','x'.repeat(121)],['search','a\r\nb'],['search',{}]])assert.throws(()=>providerTarget(a,q));});
test('Native Tor request uses fixed open executable, arguments and verified provider URL only',async()=>{
 let got;const r=await openInTorBrowser('search','Dune & me',{find:async()=>'/Applications/Tor Browser.app',run:async(...args)=>{got=args;return {};}});
 assert.equal(got[0],'/usr/bin/open');assert.deepEqual(got[1],['-a','/Applications/Tor Browser.app','--','https://flixmomo.app/search?q=Dune+%26+me']);assert.equal(got[2].shell,undefined);assert.equal(r.torVerified,false);assert.equal(r.automaticSearch,false);
});
test('Missing Tor Browser never opens a direct fallback',async()=>{let calls=0;await assert.rejects(openInTorBrowser('browse','',{find:async()=>null,run:async()=>{calls++;}}),/TOR_BROWSER_NOT_FOUND/);assert.equal(calls,0);});
test('Native form remains usable with no browser driver or JavaScript',()=>{const h=filmHTML('a'.repeat(64));assert.match(h,/action="https:\/\/flixmomo.app\/search" method="get" target="_blank"/);assert.match(h,/name="q"/);assert.match(h,/value="tor-browser" disabled/);assert.doesNotMatch(h,/playwright|webdriver|iframe|owner.html|localStorage|setInterval|normalizeResults/);});
test('Film page excludes previews and has tightly scoped native form CSP',async()=>{assert.equal((await call('/flixmomo.html',{headers:{'x-operon-preview':'1'}})).status,404);const r=await call('/flixmomo.html',{});assert.equal(r.status,200);assert.match(r.headers['Content-Security-Policy'],/form-action https:\/\/flixmomo.app/);assert.match(r.headers['Content-Security-Policy'],/frame-ancestors 'none'/);});
test('API requires local token, same origin and JSON for native opening',async()=>{
 assert.equal((await call('/api/films/status',{},()=>false)).status,401);
 assert.equal((await call('/api/films/search',{method:'POST',body:{query:'Dune',route:'tor-browser'},headers:{origin:'https://evil.invalid'}})).status,403);
 assert.equal((await call('/api/films/search',{method:'POST',body:{query:'Dune'},headers:{'content-type':'text/plain'}})).status,415);
});
test('Legacy automated endpoints are retired rather than a false search result',async()=>{
 const r=await call('/api/films/search',{method:'POST',body:{query:'Dune',route:'direct'}});assert.equal(r.status,409);assert.equal(JSON.parse(r.body).targetUrl,'https://flixmomo.app/search?q=Dune');assert.equal((await call('/api/films/open',{})).status,410);
});
test('Local status does not contact the provider, collect data or advertise automated search',async()=>{const r=await call('/api/films/status',{});const v=JSON.parse(r.body);assert.equal(v.automaticSearch,false);assert.equal(v.automaticProviderRequests,false);assert.equal(v.browserRequired,false);assert.equal(v.torBrowserAvailable,false);});
test('Oversized requests are bounded and unknown routes never launch',async()=>{assert.equal((await call('/api/films/search',{method:'POST',body:{query:'x'.repeat(3000)}})).status,413);const r=await call('/api/films/search',{method:'POST',body:{query:'Dune',route:'auto'}});assert.equal(JSON.parse(r.body).error,'EXPLICIT_ROUTE_REQUIRED');});
test('Tor route selection removes direct form and browse destinations before submit',()=>{const h=filmHTML('a'.repeat(64));assert.match(h,/tor\?'\/api\/films\/native-only'/);assert.match(h,/tor\?'#tor-browser'/);assert.match(h,/event.preventDefault\(\);void openTor\('search'\)/);});
