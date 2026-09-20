// Transport remains owned by Operon Local Fabric. No tunnel/server is started here.
const previews = new Map();
const idPattern=/^shr_[a-z0-9]{6,80}$/;
const allowedGets=new Set(['/','/index.html','/styles.css','/player.css','/app.js','/playback-engine.js','/viewer-context.js','/shaka-player.compiled.js','/vendor/hls.min.js','/api/auth/status','/api/channels','/api/epg','/api/health']);
const allowedPosts=new Set(['/api/auth/otp/send','/api/auth/otp/verify','/api/auth/logout','/api/playback']);
function reply(res,status,body){if(body.error&&!body.message)body.message=body.error==='preview_not_armed_or_expired'?'This private preview is unavailable, expired or revoked. Ask the owner for a new link.':body.error==='preview_otp_limit'?'The temporary preview sign-in limit has been reached. Ask the owner to start a new test.':'This action is not available in the temporary viewer.';res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(body));}
export function revokePreview(id){const p=previews.get(id);if(p){clearTimeout(p.timer);for(const res of p.responses)res.destroy();previews.delete(id);}return Boolean(p);}
export async function fabricControl(req,res,url,authorized){
  if(!url.pathname.startsWith('/owner-api/fabric/'))return false;
  if(req.headers['x-operon-preview']||!authorized(req)){reply(res,401,{error:'local_owner_required'});return true;}
  if(url.pathname==='/owner-api/fabric/status'&&req.method==='GET'){
    reply(res,200,{ok:true,capability:'ghartv.fabric-preview.v1',isolated_sessions:true,owner_routes_exposed:false,active:[...previews.values()].filter(p=>p.expiresAt>Date.now()).map(({id,expiresAt})=>({id,expires_at:new Date(expiresAt).toISOString()}))});return true;
  }
  if(req.method!=='POST'||req.headers.origin!==`http://${req.headers.host}`){reply(res,403,{error:'origin_or_method_rejected'});return true;}
  let body;try{const chunks=[];let n=0;for await(const c of req){n+=c.length;if(n>2048)throw Error();chunks.push(c);}body=JSON.parse(Buffer.concat(chunks).toString());}catch{reply(res,400,{error:'invalid_body'});return true;}
  if(!idPattern.test(body.id||'')){reply(res,400,{error:'invalid_share_id'});return true;}
  if(url.pathname==='/owner-api/fabric/revoke'){revokePreview(body.id);reply(res,200,{ok:true,revoked:body.id});return true;}
  if(url.pathname!=='/owner-api/fabric/arm'){reply(res,404,{error:'not_found'});return true;}
  let u;try{u=new URL(body.origin);}catch{reply(res,400,{error:'invalid_origin'});return true;}
  const duration=Date.parse(body.expires_at)-Date.now();
  if(u.origin!==body.origin||u.protocol!=='https:'||!/^[-a-z0-9]+\.trycloudflare\.com$/.test(u.hostname)||u.port||!Number.isFinite(duration)||duration<=0||duration>15*60*1000+3000){reply(res,400,{error:'invalid_scope_or_expiry'});return true;}
  if(previews.size&& !previews.has(body.id)){reply(res,409,{error:'one_preview_at_a_time'});return true;}
  revokePreview(body.id);
  const p={id:body.id,origin:u.origin,prefix:'/s/'+body.id,expiresAt:Date.parse(body.expires_at),responses:new Set(),count:0,rateAt:Date.now(),otpCount:0};
  p.timer=setTimeout(()=>revokePreview(body.id),duration);p.timer.unref();previews.set(body.id,p);
  reply(res,200,{ok:true,id:p.id,isolated_sessions:true,owner_routes_exposed:false,expires_at:new Date(p.expiresAt).toISOString()});return true;
}
export function previewContext(req,res,url){
  const id=req.headers['x-operon-preview'];if(id===undefined)return {preview:false,handled:false};
  const p=typeof id==='string'&&idPattern.test(id)?previews.get(id):null;
  if(!p||p.expiresAt<=Date.now()){reply(res,403,{error:'preview_not_armed_or_expired'});return {handled:true};}
  const method=req.method==='HEAD'?'GET':req.method;
  const media=/^\/api\/stream\/[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_./%-]*)?$/.test(url.pathname);
  const license=/^\/api\/license\/[A-Za-z0-9_-]+$/.test(url.pathname);
  if(!((method==='GET'&&(allowedGets.has(url.pathname)||media))||(method==='POST'&&(allowedPosts.has(url.pathname)||license)))){reply(res,404,{error:'viewer_route_only'});return {handled:true};}
  if(req.method==='POST'&&req.headers.origin!==p.origin){reply(res,403,{error:'preview_origin_rejected'});return {handled:true};}
  if(req.headers.origin&&req.headers.origin!==p.origin){reply(res,403,{error:'preview_origin_rejected'});return {handled:true};}
  if(Date.now()-p.rateAt>1000){p.rateAt=Date.now();p.count=0;}
  if(++p.count>80){reply(res,429,{error:'preview_rate_limit'});return {handled:true};}
  if(url.pathname==='/api/auth/otp/send'&& ++p.otpCount>3){reply(res,429,{error:'preview_otp_limit'});return {handled:true};}
  p.responses.add(res);res.once('close',()=>p.responses.delete(res));
  return {preview:true,handled:false,id:p.id,prefix:p.prefix,origin:p.origin,expiresAt:p.expiresAt};
}
export function isPreviewActive(id){const p=previews.get(id);return !!p&&p.expiresAt>Date.now();}

export function activePreviewCount(){return [...previews.values()].filter(p=>p.expiresAt>Date.now()).length;}
