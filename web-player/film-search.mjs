/** FlixMomo uses native provider pages; automated result scraping is not offered. */
import {providerTarget,findTorBrowser,openInTorBrowser} from './native-provider.mjs';
import {filmHTML} from './film-page.mjs';
export {filmHTML};
export function makeFilmRoute({find=findTorBrowser,open=openInTorBrowser}={}){
 return async function filmRoute(req,res,url,authorized,nonce){
  if(url.pathname!=='/flixmomo.html'&&!url.pathname.startsWith('/api/films/'))return false;
  const reply=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(data));};
  if(req.headers['x-operon-preview']||req.ghartvViewer?.preview){reply(404,{error:'PRIVATE_LOCAL_ROUTE'});return true;}
  if(url.pathname==='/flixmomo.html'&&req.method==='GET'){
   if(!/^[a-f0-9]{64}$/.test(nonce)){reply(500,{error:'LOCAL_SESSION_UNAVAILABLE'});return true;}
   res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Frame-Options':'DENY','Content-Security-Policy':`default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}'; connect-src 'self'; form-action https://flixmomo.app; base-uri 'none'; frame-ancestors 'none'`});res.end(filmHTML(nonce));return true;
  }
  if(!authorized(req)){reply(401,{error:'LOCAL_SESSION_REQUIRED'});return true;}
  if(url.pathname==='/api/films/status'&&req.method==='GET'){
   reply(200,{mode:'native-provider-pages',automaticSearch:false,browserRequired:false,torBrowserAvailable:!!await find(),torVerified:false,playbackVerified:false,automaticProviderRequests:false});return true;
  }
  if(!['/api/films/search','/api/films/browse'].includes(url.pathname)) {reply(410,{error:'AUTOMATED_BROWSER_RETIRED_USE_NATIVE_LINK'});return true;}
  if(req.method!=='POST'){reply(405,{error:'METHOD_NOT_ALLOWED'});return true;}
  if(req.headers.origin!==`http://${req.headers.host}`){reply(403,{error:'ORIGIN_REJECTED'});return true;}
  if(!String(req.headers['content-type']||'').startsWith('application/json')){reply(415,{error:'JSON_REQUIRED'});return true;}
  try{
   let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>2048){reply(413,{error:'BODY_TOO_LARGE'});return true;}}
   const body=JSON.parse(raw||'{}'),action=url.pathname.split('/').pop();
   const target=providerTarget(action,body.query);
   if(body.route==='direct'){reply(409,{error:'USE_NATIVE_BROWSER_LINK',targetUrl:target});return true;}
   if(body.route!=='tor-browser')throw Error('EXPLICIT_ROUTE_REQUIRED');
   reply(200,await open(action,body.query,{find}));
  }catch(e){const error=/^[A-Z_0-9]{3,100}$/.test(e.message)?e.message:'BROWSER_REQUEST_FAILED';reply(error==='TOR_BROWSER_NOT_FOUND'?409:400,{ok:false,error});}
  return true;
 };
}
export const filmRoute=makeFilmRoute();
