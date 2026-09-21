/** No HTTP-triggered native execution and no external-tab substitute. */
import {filmHTML} from './film-page.mjs';
export {filmHTML};
export function makeFilmRoute(){return async function(req,res,url,authorized,nonce){
 if(url.pathname!=='/flixmomo.html'&&!url.pathname.startsWith('/api/films/'))return false;
 const reply=(n,v)=>{res.writeHead(n,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(v));};
 if(req.headers['x-operon-preview']||req.ghartvViewer?.preview){reply(404,{error:'PRIVATE_LOCAL_ROUTE'});return true;}
 if(url.pathname==='/flixmomo.html'&&req.method==='GET'){
  res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Frame-Options':'DENY','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"});res.end(filmHTML());return true;
 }
 if(!authorized(req)){reply(401,{error:'LOCAL_SESSION_REQUIRED'});return true;}
 reply(410,{mode:'NATIVE_IN_GHARTV_VIEW',automaticBrowser:false,playbackVerified:false});return true;
};}
export const filmRoute=makeFilmRoute();
