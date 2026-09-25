"""Cloud-only guarded integration. Commit the resulting native files before compilation.
Do not run this one-shot recipe against an owner checkout or reuse it after integration.
"""
from pathlib import Path
import importlib.util
R=Path(__file__).resolve().parents[2]
def edit(name,fn):
 p=R/name;old=p.read_text();new=fn(old)
 if old==new:raise RuntimeError('NO_CHANGE: '+name)
 p.write_text(new)
def once(s,old,new):
 if s.count(old)!=1:raise RuntimeError('ANCHOR_CHANGED: '+old[:100])
 return s.replace(old,new)
edit('android-tv/app/build.gradle.kts',lambda s:once(once(s,'versionCode = 26','versionCode = 27'),'0.6.0-rc9-tv-first','0.6.0-rc10-web-films'))
edit('android-tv/app/src/main/java/in/ghartv/nova/MainActivity.java',lambda s:once(s,'header.addView(movies, headerButtonParams());','''header.addView(movies, headerButtonParams());
        Button films = actionButton("FlixMomo");
        films.setOnClickListener(view -> startActivity(new Intent(this, FlixMomoActivity.class)));
        LinearLayout.LayoutParams filmParams = new LinearLayout.LayoutParams(TvUi.dp(this, 94), TvUi.dp(this, 40));
        filmParams.leftMargin = TvUi.dp(this, 6);
        header.addView(films, filmParams);'''))
edit('android-tv/app/src/main/java/in/ghartv/nova/LoginActivity.java',lambda s:once(s,'story.addView(hints, hintsParams);','''story.addView(hints, hintsParams);
        Button films = TvUi.button(this, "FlixMomo · search without Jio login", false);
        films.setOnClickListener(view -> startActivity(new Intent(this, FlixMomoActivity.class)));
        LinearLayout.LayoutParams filmsParams = new LinearLayout.LayoutParams(-1, TvUi.dp(this, 40));
        filmsParams.topMargin = TvUi.dp(this, 10);
        story.addView(films, filmsParams);'''))
edit('web-player/owner-gateway.mjs',lambda s:once(once(s,"import {performanceRoute}","import {filmRoute} from './film-search.mjs';\nimport {performanceRoute}"),"if(await providerRoute(req,res,url,authorized))return true;","if(await filmRoute(req,res,url,authorized,nonce))return true;\n  if(await providerRoute(req,res,url,authorized))return true;").replace('<p><a href="./provider-access.html">','<p><a href="./flixmomo.html">FlixMomo · separate search</a> · <a href="./provider-access.html">'))
edit('web-player/public/index.html',lambda s:once(s,'<div class="top-actions">','<div class="top-actions"><a class="owner-link" href="./flixmomo.html">FlixMomo ↗</a>'))
edit('web-player/server.mjs',lambda s:s.replace('0.6.0-rc7-network-diagnostics','0.6.0-rc10-web-films').replace('RC6-WEB-FABRIC-R1','RC10-WEB-FIRST-FILMS').replace("if(!['127.0.0.1:'+PORT,'localhost:'+PORT,'[::1]:'+PORT].includes(req.headers.host||''))","const boundPort=req.socket.localPort||PORT;\n    if(!['127.0.0.1:'+boundPort,'localhost:'+boundPort,'[::1]:'+boundPort].includes(req.headers.host||''))"))
for name in ('web-player/package.json','web-player/package-lock.json'):
 edit(name,lambda s:s.replace('0.6.0-rc8-smooth-performance','0.6.0-rc10-web-films'))
spec=importlib.util.spec_from_file_location('r2',R/'tools/transition-20260919/build_rc9_r2.py');r2=importlib.util.module_from_spec(spec);spec.loader.exec_module(r2)
def launcher(s):
 s=once(s,'def review():',r2.NEW_FUNCTIONS+'def review():')
 s=once(s,"prior=CURRENT/ASSET;meta=CURRENT/'review-artifact.json';reuse=False", """prior=CURRENT/ASSET;meta=CURRENT/'review-artifact.json';reuse=False
    prepared=CURRENT/'prepared-review-artifact.json'
    if prepared.is_file() and not prepared.is_symlink():
     try:
      pm=json.loads(prepared.read_text());ph=pm.get('sha256','')
      if pm.get('source')==SOURCE and re.fullmatch('[a-f0-9]{64}',ph):
       candidate_cache=STATE/'verified-review-apks'/SOURCE/(ph+'.apk');safe(candidate_cache)
       if candidate_cache.is_file() and digest(candidate_cache)==ph:prior=candidate_cache;meta=prepared
     except (ValueError,OSError):pass""")
 s=once(s,"h=digest(c);r['signed_apk_sha256']=h", """h=digest(c);r['signed_apk_sha256']=h
   preserve_verified_signed(c,h)
   if args.prepare_update:
    r['status']='SIGNED_UPDATE_PREPARED_REVIEW_PENDING';r['phase']='SIGNED_UPDATE_READY';return
   resource_preflight()""")
 s=once(s,'adb,started=transport.attach_or_start(sdk,RUN)','adb,started=attach_with_boot_evidence(transport,sdk)')
 s=once(s,"args=parser.parse_args(sys.argv[2:])","parser.add_argument('--web-only',action='store_true');parser.add_argument('--prepare-update',action='store_true');args=parser.parse_args(sys.argv[2:])")
 s=once(s,"else 'CONTINUITY_REQUIRES_ATTENTION';cleanup()","else 'CONTINUITY_REQUIRES_ATTENTION'")
 s=once(s,"performance_snapshot('before');review()","""open_dashboard()
  if args.web_only:
   if r.get('web_player')!='HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED':raise Stop('WEB_NOT_READY')
   r['status']='WEB_REVIEW_READY_ANDROID_NOT_TOUCHED'
  else:
   performance_snapshot('before');review()
   if r['status']=='REVIEW_READY':cleanup()""")
 s=once(s,"if note_text:\n     open_dashboard();performance_snapshot('after');capture_support_once()","if note_text and not args.memory_only:\n     if r.get('dashboard')!='LOCAL_OWNER_READER_OPEN_REQUESTED':open_dashboard()\n     r['collector_capture']='NOT_REQUESTED_MANUAL_REPORTING_ONLY'")
 s=s.replace('0.6.0-rc9-tv-first','0.6.0-rc10-web-films').replace('v0.6.0-rc9','v0.6.0-rc10')
 s=s.replace('CYAN REVIEW 14','CYAN REVIEW 15').replace('CYAN-14-','CYAN-15-').replace('CYAN14-TV-FIRST-PREVIEW','CYAN15-WEB-FILMS').replace('GHARTV_CYAN_REVIEW_14_HANDOFF','GHARTV_CYAN_REVIEW_15_HANDOFF')
 s=s.replace('RC9','RC10').replace('code26','code27').replace('code-26','code-27')
 for a,b in [('version_code=26','version_code=27'),("'version_code':26","'version_code':27"),("version_code')!=26","version_code')!=27"),("versionCode='26'","versionCode='27'"),('int(codes[0])>26','int(codes[0])>27'),('int(codes[0])==26','int(codes[0])==27'),('int(codes[0])<26','int(codes[0])<27'),("versionCode',0))>=26","versionCode',0))>=27")]:s=s.replace(a,b)
 s=once(s,"r['owner_url']='http://127.0.0.1:8790/owner.html'","r['owner_url']='http://127.0.0.1:8790/owner.html';r['films_url']='http://127.0.0.1:8790/flixmomo.html'")
 return s.replace('automatic focus-owned preview restored + TV-first regression checks','web first · independent FlixMomo · original TV contract retained')
edit('tools/owner_review.command.in',launcher)
def starter(s):
 s=once(s,'umask 077\n','umask 077\nif [ "$(uname -s)" != Darwin ]; then echo MAC_ONLY_NO_ACTION; exit 2; fi\n')
 return once(s,"known={'","known={'a6bfd744ba8740430bb98bda5cc961edfa945820909164a28aaf11e7187cb392','b15cec2a35ad6b3b1fc3f01750bb028f4f1cdb5d53f3db8705adb63b0363a309','")
edit('tools/run_owner_bundle.command.in',starter)
def package(s):
 s=s.replace('REVIEW_060_RC9','REVIEW_060_RC10').replace('GHARTV_RC9_REVIEW','GHARTV_RC10_REVIEW').replace('0.6.0-rc9-tv-first','0.6.0-rc10-web-films').replace("'version_code':26","'version_code':27")
 s=once(s,"if str(p) not in ('web-player/node_modules/hls.js/dist/hls.min.js','web-player/node_modules/hls.js/LICENSE'):continue","if str(p) not in ('web-player/node_modules/hls.js/dist/hls.min.js','web-player/node_modules/hls.js/LICENSE') and p.parts[:4] != ('web-player','browser-tools','node_modules','playwright-core'):continue")
 s=once(s,"z.write(command,prefix+command.name);z.write(starter,prefix+starter.name)","""z.write(command,prefix+command.name);z.write(starter,prefix+starter.name)
 for n,flag in [('RUN_GHARTV_WEB.command','--web-only'),('PREPARE_GHARTV_UPDATE.command','--prepare-update')]:
  z.writestr(prefix+n,'#!/bin/bash\\nset -euo pipefail\\nHERE="$(cd "$(dirname "$0")" && pwd)"\\nexec /bin/bash "$HERE/RUN_GHARTV_REVIEW.command" '+flag+' "$@"\\n')""")
 return s
edit('tools/package_owner_review.py',package)
edit('tools/tv-first/check_contract.py',lambda s:s.replace('26','27').replace('REVIEW_14','REVIEW_15').replace('REVIEW14','REVIEW15').replace('REVIEW_13_HANDOFF','REVIEW_14_HANDOFF'))
edit('web-player/test/server.test.mjs',lambda s:once(s,'assert.doesNotMatch(source, /localStorage|sessionStorage|document\\.cookie/);','''assert.doesNotMatch(source, /sessionStorage|document\\.cookie/);
  const stored=[...source.matchAll(/localStorage\\.(?:setItem|getItem)\\("([^"]+)"/g)].map(m=>m[1]);
  assert.deepEqual([...new Set(stored)], ["ghartv_comfort_v1"]);
  assert.doesNotMatch(source, /localStorage\\.(?:setItem|getItem)\\([^"\\s]/);'''))
if (R/'tools/rc10/site.html').is_file():
 (R/'docs/index.html').write_bytes((R/'tools/rc10/site.html').read_bytes())
 (R/'docs/downloads.html').write_text('<!doctype html><html lang="en"><head><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=./index.html#download"><title>GharTV downloads</title></head><body><a href="./index.html#download">Household APK and RC10 review downloads</a></body></html>\n')
print('RC10_SOURCE_INTEGRATED: Android code27, same app and TV contract, web-first original-signer delivery')
