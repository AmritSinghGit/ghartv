import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {Readable} from 'node:stream';
import {makeFilmRoute,filmHTML} from '../film-search.mjs';
const origin='http://127.0.0.1:8790';
function request(options={}){const r=Readable.from([]);r.method=options.method||'GET';r.headers={host:'127.0.0.1:8790',origin,...options.headers};return r;}
async function call(path,options={},auth=()=>true){const result={status:0,headers:{},body:'',writeHead(n,h){this.status=n;this.headers=h;},end(b){this.body=b;}};await makeFilmRoute()(request(options),result,new URL(path,origin),auth,'a'.repeat(64));return result;}
const source=name=>readFile(new URL(name,import.meta.url),'utf8');
test('Film page describes the native view, not another automated or external-tab result grid',()=>{const h=filmHTML();assert.match(h,/Inside GharTV/);assert.match(h,/--films-only/);assert.doesNotMatch(h,/<form|target="_blank"|href="https:\/\/flixmomo|Search driver ready|\/api\/films\/search/);});
test('Native component cannot be launched through an HTTP route',async()=>{const r=await call('/api/films/search',{method:'POST'});assert.equal(r.status,410);assert.equal(JSON.parse(r.body).automaticBrowser,false);const s=await source('../film-search.mjs');assert.doesNotMatch(s,/child_process|spawn\(|execFile|openInTorBrowser/);});
test('Film page excludes temporary previews and keeps form-action disabled',async()=>{assert.equal((await call('/flixmomo.html',{headers:{'x-operon-preview':'1'}})).status,404);const r=await call('/flixmomo.html');assert.equal(r.status,200);assert.match(r.headers['Content-Security-Policy'],/form-action 'none'/);});
test('API status remains inaccessible without a local session',async()=>{assert.equal((await call('/api/films/status',{},()=>false)).status,401);});
test('Android searches inside the existing WebView via encoded provider route, not injected DOM',async()=>{const s=await source('../../android-tv/app/src/main/java/in/ghartv/nova/FlixMomoActivity.java');assert.match(s,/browser.loadUrl\(origin\+"\/search\?q="\+Uri.encode\(text\)\)/);assert.doesNotMatch(s,/evaluateJavascript|addJavascriptInterface|setUserAgentString/);assert.match(s,/onShowCustomView/);assert.match(s,/h.cancel\(\)/);});
test('Mac native view preserves provider origin and does not change automation detection',async()=>{const s=await source('../../tools/films/GharTVFilmView.swift');assert.match(s,/WKWebView/);assert.match(s,/URLQueryItem\(name: "q", value: text\)/);assert.match(s,/websiteDataStore = .nonPersistent\(\)/);assert.doesNotMatch(s,/readLine\(|customUserAgent|navigator\.webdriver|evaluateJavaScript|NSWorkspace.*open|disable-web-security/);assert.match(s,/provider_blocked/);});
test('Review command checks native component hash and has a TV-only mode',async()=>{const s=await source('../../tools/owner_review.command.in');assert.match(s,/NATIVE_FILM_COMPONENT_CHECKSUM_FAILED/);assert.match(s,/--tv-only/);assert.match(s,/--films-only/);assert.match(s,/mac_window_observed/);assert.match(s,/version_code=30/);});
test('Private native window and Android readiness remain separate in safe receipt',async()=>{const s=await source('../review-sync.mjs');assert.match(s,/mac_window_observed:r.mac_window_observed===true/);assert.match(s,/ANDROID_READY_WINDOW_UNCONFIRMED/);});
