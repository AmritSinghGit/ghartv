import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {providerURL,publicAddress,inspectPage,makeProviderRoute,inspectInBrowser,failureCode} from '../../web-player/provider-access.mjs';
const target='https://flixmomo.app/movie/1/example/watch?p=1';let count=0;
for(const u of ['http://flixmomo.app/','https://flixmomo.app:8080/','https://user:pass@flixmomo.app/',
 'https://flixmomo.app.evil.test/','https://127.0.0.1/','https://[::1]/','file:///etc/passwd',
 'javascript:alert(1)','https://flixmomo.app/#token','https://flixmomo.app/\\evil']){assert.throws(()=>providerURL(u));count++;}
assert.equal(providerURL(target).provider.id,'flixmomo');count++;
assert.equal(failureCode(Error('DNS_TIMEOUT')),'DNS_TIMEOUT');count++;
for(const ip of ['127.0.0.1','10.0.0.1','192.168.1.1','169.254.169.254','0.0.0.0','100.64.1.1','198.18.0.1','192.0.2.1',
 '224.0.0.1','255.255.255.255','::1','::','fe80::1','fd00::1','::ffff:127.0.0.1','2001:db8::1','2002:7f00:1::','3fff::1']){assert.equal(publicAddress(ip),false,ip);count++;}
for(const ip of ['1.1.1.1','8.8.8.8','2606:4700:4700::1111']){assert.equal(publicAddress(ip),true,ip);count++;}
const resolve=async()=>[{address:'1.1.1.1',family:4}];let calls=0;
const ok={status:200,headers:{'content-type':'text/html','x-frame-options':'SAMEORIGIN'},body:'<script>secret</script><iframe></iframe><video></video>',truncated:false};
let r=await inspectPage(target,{resolve,request:async(u,pin)=>{calls++;assert.equal(pin.address,'1.1.1.1');return ok;}});
assert.equal(calls,1);assert.equal(r.status,'HTML_PAGE_NOT_VIDEO');assert.equal(r.framePolicy,'SAME_ORIGIN_ONLY');assert.equal(r.playbackVerified,false);assert.equal(r.markupOnly.videoElements,1);assert.ok(!JSON.stringify(r).includes('secret'));assert.ok(!JSON.stringify(r).includes('/movie/1'));count++;
r=await inspectPage(target,{resolve:async()=>[{address:'10.0.0.1',family:4}],request:async()=>{throw Error('must not fetch');}});assert.equal(r.status,'NON_PUBLIC_ADDRESS_REJECTED');count++;
r=await inspectPage(target,{resolve:async()=>{throw Object.assign(Error('secret hostname'),{code:'ENOTFOUND'});}});assert.equal(r.status,'DNS_UNAVAILABLE');assert.ok(!JSON.stringify(r).includes('secret'));count++;
r=await inspectPage(target,{resolve,request:async()=>({status:302,headers:{location:'https://attacker.example/'},body:''})});assert.equal(r.status,'REDIRECT_ORIGIN_NOT_APPROVED');count++;
r=await inspectPage(target,{resolve,request:async()=>({status:302,headers:{location:'/loop'},body:''})});assert.equal(r.status,'REDIRECT_LIMIT');count++;
r=await inspectPage(target,{resolve,request:async()=>new Promise(()=>{}),timeoutMs:15});assert.equal(r.status,'REQUEST_TIMED_OUT');count++;
for(const [http,status]of [[401,'PROVIDER_LOGIN_REQUIRED'],[403,'PROVIDER_DENIED'],[429,'PROVIDER_RATE_LIMITED'],[504,'PROVIDER_HTTP_ERROR']]){r=await inspectPage(target,{resolve,request:async()=>({...ok,status:http})});assert.equal(r.status,status);count++;}
delete process.env.GHARTV_PROVIDER_BROWSER_EXECUTABLE;r=await inspectInBrowser(target);assert.equal(r.status,'BROWSER_NOT_CONFIGURED');count++;
let inspections=0,release;
const route=makeProviderRoute({inspect:async()=>{inspections++;return await new Promise(done=>release=()=>done({status:'HTML_PAGE_NOT_VIDEO',playbackVerified:false}));}});
const server=createServer(async(req,res)=>{const u=new URL(req.url,'http://127.0.0.1');if(!await route(req,res,u,r=>r.headers.authorization==='Bearer test-only')){res.writeHead(404);res.end();}});await new Promise(done=>server.listen(0,'127.0.0.1',done));
const base='http://127.0.0.1:'+server.address().port;
try{
 let p=await fetch(base+'/owner-api/providers/status');assert.equal(p.status,401);count++;
 const headers={Authorization:'Bearer test-only',Origin:base,'Content-Type':'application/json'};
 p=await fetch(base+'/owner-api/providers/status',{headers});assert.equal(p.status,200);assert.equal(inspections,0);count++;
 p=await fetch(base+'/owner-api/providers/status',{headers:{...headers,'x-operon-preview':'yes'}});assert.equal(p.status,404);count++;
 p=await fetch(base+'/owner-api/providers/inspect',{method:'POST',headers:{...headers,Origin:'https://evil.test'},body:JSON.stringify({url:target,mode:'https'})});assert.equal(p.status,403);assert.equal(inspections,0);count++;
 const pending=fetch(base+'/owner-api/providers/inspect',{method:'POST',headers,body:JSON.stringify({url:target,mode:'https'})});
 while(!release)await new Promise(done=>setTimeout(done,5));
 p=await fetch(base+'/owner-api/providers/inspect',{method:'POST',headers,body:JSON.stringify({url:target,mode:'https'})});assert.equal(p.status,429);assert.equal(inspections,1);release();assert.equal((await pending).status,200);count++;
}finally{await new Promise(done=>server.close(done));}
// Check direct integration and private bootstrap on the actual existing server, with no provider reads.
process.env.GHARTV_DISABLE_KEYCHAIN='1';process.env.GHARTV_WEB_PORT='18390';
const {createAppServer}=await import('../../web-player/server.mjs');
const app=createAppServer();await new Promise(done=>app.listen(18390,'127.0.0.1',done));
try{
 const p=await fetch('http://127.0.0.1:18390/provider-access.html');assert.equal(p.status,200);const text=await p.text();
 assert.ok(!text.includes('__OWNER_BOOT__'));assert.ok(p.headers.get('content-security-policy').includes("frame-ancestors 'none'"));
 const scripts=[...text.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)];for(const s of scripts)new Function(s[1]);count++;
 const owner=await(await fetch('http://127.0.0.1:18390/owner.html')).text();assert.ok(owner.includes('Provider compatibility'));count++;
 const bad=await fetch('http://127.0.0.1:18390/owner-api/providers/inspect',{method:'POST',headers:{Origin:'http://127.0.0.1:18390','Content-Type':'application/json'},body:JSON.stringify({url:target,mode:'https'})});assert.equal(bad.status,401);count++;
}finally{await new Promise(done=>app.close(done));}
console.log(JSON.stringify({passed:count,actualHttpRouteTests:true,providerResponses:'controlled fixtures',liveFlixmomoPlayback:false,tor:false}));
