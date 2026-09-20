"""One-time guarded integration into the existing cloud checkout."""
from pathlib import Path
R=Path(__file__).resolve().parents[2]
def once(s,a,b):
 if s.count(a)!=1:raise ValueError('ANCHOR_CHANGED: '+a[:80])
 return s.replace(a,b)
p=R/'web-player/server.mjs';s=p.read_text()
s=once(s,'import { ownerRoute } from "./owner-gateway.mjs";','import {filmRoute} from "./film-search.mjs";\nimport {selectPlayback} from "./playback-capabilities.mjs";')
s=once(s,'const WEB_REPAIR = "RC10-WEB-FIRST-FILMS";', 'const WEB_REPAIR = "RC10.2-VIEWER-SECURITY-NATIVE-HLS";\nconst filmToken = randomBytes(32).toString("hex");')
s=once(s,'async function authorizePlayback(session, channel, retry = true)', 'async function authorizePlayback(session, channel, retry = true, capabilities = {})')
s=s.replace('return authorizePlayback(session, channel, false);','return authorizePlayback(session, channel, false, capabilities);')
start=s.index('  const hls = value(payload.result);',s.index('async function authorizePlayback'));end=s.index('  const ticket = id();',start)
s=s[:start]+'''  const choice = selectPlayback(payload, capabilities);
  const protocol = choice.protocol, selected = choice.url, license = choice.license;
'''+s[end:]
s=once(s,'out[part.slice(0, at).trim()] = decodeURIComponent(part.slice(at + 1).trim());','try { out[part.slice(0, at).trim()] = decodeURIComponent(part.slice(at + 1).trim()); } catch { /* Ignore malformed cookies. */ }')
s=once(s,'const playback = await authorizePlayback(session, channel);','const playback = await authorizePlayback(session, channel, true, body.capabilities || {});')
s=s.replace("media-src 'self' blob:; connect-src 'self';", "media-src 'self' blob:; worker-src 'self' blob:; connect-src 'self';")
s=once(s,'owner_reader:!req.ghartvViewer.preview',"owner_reader:false,analytics:'NOT_SERVED_BY_VIEWER',active_previews:activePreviewCount()")
s=once(s,'import {previewContext,isPreviewActive}','import {previewContext,isPreviewActive,activePreviewCount}')
s=once(s,'      if(!req.ghartvViewer.preview && await ownerRoute(req,res,url))return;',r'''      // Do not import or serve private analytics, collector config or owner reports.
      if (url.pathname.startsWith('/owner-api') || /^(?:\/owner(?:\.html)?|\/provider-access\.html|\/release-control\.html|\/performance(?:-desk)?\.html|\/support(?:\.html)?)$/.test(url.pathname)) {
        apiError(res,404,'This page is not part of GharTV.','not_found'); return;
      }
      if (!req.ghartvViewer.preview && (url.pathname === '/flixmomo.html' || url.pathname.startsWith('/api/films/'))) {
        assertLocalOrigin(req);
        if(await filmRoute(req,res,url,r => r.headers.authorization === 'Bearer '+filmToken,filmToken)) return;
      }''')
s=s.replace('status === 401 ? "auth_required" : "provider_error"','error.code || (status === 401 ? "auth_required" : "provider_error")')
p.write_text(s)
p=R/'web-player/fabric-preview.mjs';s=p.read_text().replace("'/app.js','/viewer-context.js'","'/app.js','/playback-engine.js','/viewer-context.js'")
s+="\nexport function activePreviewCount(){return [...previews.values()].filter(p=>p.expiresAt>Date.now()).length;}\n";p.write_text(s)
print('VIEWER_ANALYTICS_REMOVED_AND_RENDITION_SELECTION_INTEGRATED')
