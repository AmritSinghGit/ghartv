/** Separate FlixMomo surface in the existing private web service; no database or daemon. */
import {connect} from 'node:net';
import {spawn} from 'node:child_process';
import {access,mkdtemp,rm} from 'node:fs/promises';
import {constants} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {filmURL,torProxy} from './film-browser-worker.mjs';
export async function findBrowser(){
 const candidates=[process.env.GHARTV_PROVIDER_BROWSER_EXECUTABLE,'/Applications/Brave Browser.app/Contents/MacOS/Brave Browser','/Applications/Chromium.app/Contents/MacOS/Chromium','/Applications/Google Chrome.app/Contents/MacOS/Google Chrome','/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge','/usr/bin/brave-browser','/usr/bin/chromium','/usr/bin/chromium-browser','/usr/bin/google-chrome','/usr/bin/microsoft-edge'];
 for(const p of candidates.filter(Boolean))try{if(!p.startsWith('/')||/[\r\n]/.test(p))continue;await access(p,constants.X_OK);return p;}catch{}
 return null;
}
export async function detectTor(){
 if(process.env.GHARTV_TOR_PROXY)return torProxy(process.env.GHARTV_TOR_PROXY);
 for(const port of [9050,9150]){
  const open=await new Promise(resolve=>{const socket=connect({host:'127.0.0.1',port});let done=false;
   const end=value=>{if(done)return;done=true;socket.destroy();resolve(value);};
   socket.setTimeout(250,()=>end(false));socket.once('connect',()=>end(true));socket.once('error',()=>end(false));});
  if(open)return 'socks5://127.0.0.1:'+port;
 }
 throw Error('TOR_SOCKS_NOT_RUNNING_START_TOR_BROWSER');
}
export function makeFilmRoute({discover=findBrowser,start=spawn}={}){
 let active=null,busy=false;const results=new Map();
 async function stop(){
  const task=active;if(!task)return false;active=null;task.child.kill('SIGTERM');
  await new Promise(resolve=>{const timer=setTimeout(()=>{try{if(process.platform!=='win32')process.kill(-task.child.pid,'SIGKILL');else task.child.kill('SIGKILL');}catch{}resolve();},2000);task.child.once('close',()=>{clearTimeout(timer);resolve();});});
  await rm(task.profile,{recursive:true,force:true});return true;
 }
 async function execute(input){
  if(active||busy)throw Error('BROWSER_BUSY_STOP_CURRENT_SESSION');
  busy=true; // BROWSER_ADMISSION_RESERVED before asynchronous discovery
  let profile;
  try {
  const executable=await discover();if(!executable)throw Error('OPTIONAL_BROWSER_MISSING_USE_DIRECT_LINK');
  const proxy=input.route==='tor'?await detectTor():'socks5://127.0.0.1:9050';
  profile=await mkdtemp(join(tmpdir(),'ghartv-film-'));
  const env={PATH:process.env.PATH||'/usr/bin:/bin',HOME:profile,TMPDIR:profile,LANG:'en_US.UTF-8',GHARTV_FILM_EXECUTABLE:executable,GHARTV_TOR_PROXY:proxy};
  if(process.env.DISPLAY)env.DISPLAY=process.env.DISPLAY;
  const child=start(process.execPath,[fileURLToPath(new URL('./film-browser-worker.mjs',import.meta.url))],{stdio:['pipe','pipe','ignore'],env,detached:process.platform!=='win32'});
  const task={child,profile};active=task;
  return await new Promise(resolve=>{
   let buffer='',done=false;
   const finish=async value=>{if(done)return;done=true;clearTimeout(timer);if(input.action==='search'||!value.ok){await stop();}busy=false;resolve(value);};
   const timer=setTimeout(()=>finish({ok:false,error:'PROVIDER_TIMED_OUT',playbackVerified:false}),55000);
   child.stdout.on('data',chunk=>{buffer+=chunk;if(buffer.length>65536)return void finish({ok:false,error:'RESPONSE_TOO_LARGE'});const line=buffer.indexOf('\n');if(line>=0)try{finish(JSON.parse(buffer.slice(0,line)));}catch{finish({ok:false,error:'INVALID_BROWSER_RESPONSE'});}});
   child.once('error',()=>finish({ok:false,error:'BROWSER_START_FAILED'}));
   child.once('close',()=>{if(active===task)active=null;rm(profile,{recursive:true,force:true}).catch(()=>{});if(!done)finish({ok:false,error:'BROWSER_EXITED_BEFORE_RESULT'});});
   child.stdin.on('error',()=>{});child.stdin.end(JSON.stringify(input));
  });
  } catch(error) { busy=false; if(profile)await rm(profile,{recursive:true,force:true}); throw error; }
 }
 return async function filmRoute(req,res,url,authorized,nonce){
  if(url.pathname!=='/flixmomo.html'&&!url.pathname.startsWith('/api/films/'))return false;
  const reply=(s,data)=>{res.writeHead(s,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(data));};
  if(req.headers['x-operon-preview']||req.ghartvViewer?.preview){reply(404,{error:'PRIVATE_OWNER_ROUTE'});return true;}
  if(url.pathname==='/flixmomo.html'&&req.method==='GET'){
   res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Frame-Options':'DENY','Content-Security-Policy':`default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}'; connect-src 'self'; frame-src https://flixmomo.app; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`});res.end(filmHTML(nonce));return true;
  }
  if(!authorized(req)){reply(401,{error:'LOCAL_OWNER_SESSION_REQUIRED'});return true;}
  if(url.pathname==='/api/films/status'&&req.method==='GET'){
   let driver=false;try{await access(fileURLToPath(new URL('./browser-tools/node_modules/playwright-core/index.mjs',import.meta.url)));driver=true;}catch{}
   reply(200,{browserAvailable:!!await discover(),driverAvailable:driver,busy,playerOpen:!!active,tor:'NOT_CHECKED_UNTIL_REQUEST',routes:['direct','tor'],playbackVerified:false,automaticProviderRequests:false});return true;
  }
  if(req.method!=='POST'){reply(405,{error:'METHOD_NOT_ALLOWED'});return true;}
  if(req.headers.origin!==`http://${req.headers.host}`){reply(403,{error:'ORIGIN_REJECTED'});return true;}
  if(!String(req.headers['content-type']||'').startsWith('application/json')){reply(415,{error:'JSON_REQUIRED'});return true;}
  try{
   let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>2048){reply(413,{error:'BODY_TOO_LARGE'});return true;}}
   const body=JSON.parse(raw||'{}'),action=url.pathname.split('/').pop();
   if(action==='stop'){reply(200,{ok:true,stopped:await stop()});return true;}
   if(!['search','open','browse'].includes(action)||!['direct','tor'].includes(body.route))throw Error('ACTION_OR_ROUTE_REJECTED');
   let input;
   if(action==='search'){const query=String(body.query||'').trim();if(query.length<2||query.length>120||/[\x00-\x1f]/.test(query))throw Error('QUERY_LENGTH_REJECTED');input={action,query,route:body.route};}
   else if(action==='browse'){input={action,route:body.route};}
   else{const item=results.get(String(body.id));if(!item)throw Error('SEARCH_RESULT_EXPIRED_SEARCH_AGAIN');input={action,route:body.route,url:filmURL(item.url)};}
   const value=await execute(input);
   if(action==='search'&&value.ok){results.clear();for(const item of (value.results||[]).slice(0,40))results.set(item.id,item);}
   reply(value.ok?200:502,value);
  }catch(e){const error=/^[A-Z_0-9]{3,100}$/.test(e.message)?e.message:'REQUEST_FAILED';reply(error.includes('BUSY')?409:400,{ok:false,error});}
  return true;
 };
}
export const filmRoute=makeFilmRoute();
export function filmHTML(nonce){return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>FlixMomo · GharTV</title><style>
:root{color-scheme:dark;--ink:#f0f6ff;--dim:#a5b6cc;--line:#253448;--mint:#77f3cc}*{box-sizing:border-box}body{margin:0;background:radial-gradient(ellipse at 95% 0,#253658 0,transparent 42%),#080e19;color:var(--ink);font:16px system-ui,sans-serif}a{color:var(--mint)}header,main{max-width:1240px;margin:auto;padding:26px}header{display:flex;gap:24px;align-items:center;border-bottom:1px solid var(--line)}header b{font-size:23px;margin-right:auto;letter-spacing:-1px}.mark{display:inline-grid;place-items:center;border:1px solid var(--mint);border-radius:10px;width:34px;height:34px;color:var(--mint);margin-right:10px}h1{font-size:clamp(36px,5vw,68px);line-height:1.06;letter-spacing:-3px;margin:14px 0}p{line-height:1.6;color:var(--dim);max-width:800px}.eyebrow{color:var(--mint);letter-spacing:3px;font-size:12px}.search{margin:30px 0;padding:20px;border:1px solid var(--line);border-radius:18px;background:#111c2bc9;display:flex;flex-wrap:wrap;gap:12px}input,select,button{font:inherit;border-radius:10px;border:1px solid #445268;padding:13px;background:#121e30;color:var(--ink)}input{flex:1;min-width:220px}button{cursor:pointer}button.primary{color:#081910;background:var(--mint);border:0;font-weight:750}button:disabled{opacity:.45;cursor:wait}:focus-visible{outline:3px solid var(--mint);outline-offset:4px}.note{padding:15px 18px;border-left:3px solid var(--mint);background:#10242b;border-radius:0 10px 10px 0}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:18px;margin:26px 0}.card{border:1px solid var(--line);border-radius:16px;padding:22px;background:linear-gradient(145deg,#1e2b40,#101722)}.card h2{font-size:21px;line-height:1.35;min-height:58px}.card small{color:var(--mint)}.card button{margin:8px 6px 0 0;font-size:14px}.card p{font-size:13px}.empty{padding:40px;border:1px dashed var(--line);border-radius:16px;color:var(--dim)}details{margin:24px 0;padding:18px;border:1px solid var(--line);border-radius:14px}summary{cursor:pointer}#player{display:none;border:1px solid var(--line);border-radius:18px;padding:16px}iframe{width:100%;height:70vh;border:0;background:black;border-radius:12px}footer{color:var(--dim);font-size:13px;border-top:1px solid var(--line);padding:24px 0;margin-top:35px}.status{font-size:13px;color:var(--mint)}@media(max-width:600px){header,main{padding:18px}header{flex-wrap:wrap;gap:12px}header a{font-size:13px}h1{letter-spacing:-1.5px}}
</style></head><body><header><b><span class="mark">G</span>GharTV</b><a href="/">Live television</a></header><main><span class="eyebrow">SEPARATE PROVIDER · YOUR CHOICE OF ROUTE</span><h1>A little more<br>movie night.</h1><p>Open FlixMomo in the browser you already use. Brave is not required. Optional automatic search and isolated Tor browsing use a compatible browser installed on this computer.</p><p><a id="directProvider" href="https://flixmomo.app/" target="_blank" rel="noopener noreferrer">Open FlixMomo in this browser ↗</a><span id="directRouteNote"> · Direct connection</span></p><div class="status" id="status" role="status">Checking local browser availability…</div><form class="search" id="search"><input id="query" placeholder="A film, series or something Punjabi…" aria-label="Movie or series" minlength="2" maxlength="120" required><select id="route" aria-label="Network route"><option value="direct">Direct HTTPS</option><option value="tor">Tor · local SOCKS</option></select><button class="primary" id="submit">Search FlixMomo</button><button type="button" id="browse">Browse FlixMomo</button><button type="button" id="stop">Close GharTV browser</button></form><div class="note" id="notice">No provider request runs until you search. A returned title or loaded page is not proof that its video is available.</div><div id="results" class="grid"><div class="empty">Your search results will appear here. No sample titles, counters or viewing activity are fabricated.</div></div><section id="player"><button id="closePlayer">Close embedded player</button><p>Direct device connection. Provider framing, login and playback restrictions are respected. Embedded playback cannot use the server's Tor route.</p><iframe id="frame" title="FlixMomo provider player" sandbox="allow-scripts allow-same-origin allow-forms allow-presentation" allow="fullscreen; encrypted-media; picture-in-picture" referrerpolicy="no-referrer" allowfullscreen></iframe></section><details><summary>Browser routing and privacy</summary><p>The server detects installed Brave, Chromium or Chrome. It starts one isolated session only when requested, does not use your existing browser profile, and closes its own player after two hours or when you press Close. Other browsers and project runtimes are not stopped.</p><p>Open Tor Browser first, or run your existing Tor service. GharTV detects local SOCKS ports 9050 and 9150; an optional GHARTV_TOR_PROXY setting can select one explicitly. The browser checks the route with the Tor Project before accessing FlixMomo. Failed verification stops the request; there is no direct fallback. This is not the full anti-fingerprinting protection of Tor Browser.</p><p>Search terms go to the selected provider. No Jio login, collector credential or watch history is forwarded. Search results exist only in server memory. The Android FlixMomo screen uses the TV's ordinary network; desktop Tor mode does not route Android or Jio traffic.</p></details><footer>GharTV Nova RC10.2 · Existing web service, independent provider · Use content you are entitled to access. No DRM, account or provider restrictions are removed.</footer></main><script nonce="${nonce}">
const token=${JSON.stringify(nonce)},$=id=>document.getElementById(id);let last=[];
async function api(path,body){const response=await fetch('/api/films/'+path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});const value=await response.json();if(!response.ok||value.ok===false)throw Error(value.error||'Request failed');return value;}
function message(text){$('notice').textContent=text==='PROVIDER_VERIFICATION_REQUIRED_USE_BROWSER'?'FlixMomo requires browser verification. Choose Browse FlixMomo, complete the check yourself, and use its search. No verification bypass is attempted.':text;}
async function localStatus(){try{const s=await api('status');$('status').textContent=(s.browserAvailable?'Browser detected':'Direct browser link is ready; optional automatic search needs Chrome, Edge, Chromium or Brave')+' · '+(s.driverAvailable?'Search driver ready':'Search driver missing')+' · '+(s.playerOpen?'GharTV browser open':'No GharTV browser running');}catch(e){$('status').textContent=e.message;}}
function render(){const root=$('results');root.replaceChildren();if(!last.length){const p=document.createElement('p');p.className='empty';p.textContent='No rendered results were returned. Browse the provider directly or try another query.';root.append(p);return;}for(const item of last){const card=document.createElement('article');card.className='card';const small=document.createElement('small');small.textContent=item.type+' / FLIXMOMO';const title=document.createElement('h2');title.textContent=item.title;const embed=document.createElement('button');embed.textContent='Open here · direct';embed.disabled=$('route').value==='tor';embed.onclick=()=>{$('frame').src=item.url;$('player').style.display='block';$('player').scrollIntoView({behavior:'smooth'});message('Provider frame requested. Playback remains unverified. Use Browser player if the provider blocks framing.');};const open=document.createElement('button');open.textContent='Browser player';open.onclick=async()=>{open.disabled=true;try{const v=await api('open',{id:item.id,route:$('route').value});message(v.status+(v.torVerified?' · Tor verified before page access':' · Direct route'));}catch(e){message(e.message);}finally{open.disabled=false;localStatus();}};const note=document.createElement('p');note.textContent='Provider-controlled playback; availability not verified.';card.append(small,title,embed,open,note);root.append(card);}}
$('search').onsubmit=async event=>{event.preventDefault();$('submit').disabled=true;message('Searching the provider in an isolated browser…');try{const v=await api('search',{query:$('query').value,route:$('route').value});last=v.results||[];render();message(v.status+' · '+(v.torVerified?'Tor verified before provider access':'Direct HTTPS')+' · playback not verified');}catch(e){last=[];render();message(e.message);}finally{$('submit').disabled=false;localStatus();}};
$('route').onchange=()=>{$('directProvider').hidden=$('route').value==='tor';$('directRouteNote').textContent=$('route').value==='tor'?'Tor selected: direct-browser link disabled. Choose Direct HTTPS to use your current browser.':' · Direct connection';$('frame').removeAttribute('src');$('player').style.display='none';render();message($('route').value==='tor'?'Tor selected. Embedded direct playback is disabled. A verified Tor browser session is required.':'Direct HTTPS selected explicitly.');};
$('browse').onclick=async()=>{ $('browse').disabled=true;try{const v=await api('browse',{route:$('route').value});message('Provider browser opened. Complete any verification yourself, then use its search and player. '+(v.torVerified?'Tor route checked.':'Direct route.'));}catch(e){message(e.message);}finally{$('browse').disabled=false;localStatus();}};
$('stop').onclick=async()=>{try{await api('stop',{});message('GharTV-owned browser closed. Other browser sessions are unchanged.');}catch(e){message(e.message);}localStatus();};$('closePlayer').onclick=()=>{$('frame').removeAttribute('src');$('player').style.display='none';};localStatus();
</script></body></html>`;}
