"""In-app implementation, not the rejected external-tab endpoint. Cloud build only."""
from pathlib import Path
import re
R=Path(__file__).resolve().parents[2]
def edit(name,fn):
 p=R/name;p.write_text(fn(p.read_text()))
def once(s,a,b):
 if s.count(a)!=1:raise RuntimeError('ANCHOR_CHANGED: '+a[:100])
 return s.replace(a,b)
# Native component launches only through the user's verified review command.
# No HTTP request can execute it, and no browser-provided command is accepted.
def native(s):
 if 'DispatchQueue.global(qos: .userInitiated).async' in s:
  a=s.index('        DispatchQueue.global(qos: .userInitiated).async');b=s.index('\n    }\n    func target',a)
  s=s[:a]+'''        let args = CommandLine.arguments
        if args.count == 3, args[1] == "--query", let url = target("search", args[2]) {
            query.stringValue = args[2]; open(url)
        } else { open(URL(string: "https://flixmomo.app/")!) }
'''+s[b:]
 if '    func handle(' in s:
  a=s.index('    func handle(');b=s.index('    func open(',a);s=s[:a]+s[b:]
 s=s.replace('"pageError":lastNavigationFailed,"playbackVerified":false','"pageError":lastNavigationFailed,"providerPath":webView.url?.path == "/search" ? "search" : "other","playbackVerified":false')
 return s
edit('tools/films/GharTVFilmView.swift',native)
def android(s):
 a=s.index('    private void search(){');b=s.index('    private void exitFullScreen()',a)
 s=s[:a]+'''    private void search(){
        String text=query.getText().toString().trim();
        if(text.length()<2 || text.length()>120){status.setText("Enter 2–120 characters.");return;}
        if(browser==null){status.setText("Android System WebView is unavailable.");return;}
        // Provider-owned search and player remain inside this GharTV activity.
        // No DOM injection, stream extraction or authentication changes.
        browser.loadUrl(HOME+"search?q="+Uri.encode(text));
        browser.requestFocus();
    }
'''+s[b:]
 s=s.replace('"Search movies and series on FlixMomo"','"Search FlixMomo inside GharTV"')
 s=s.replace('if(pageReady)status.setText("Provider page loaded · not a playback verification · device network, not Tor");','''if("/dummy".equals(Uri.parse(u).getPath())){pageReady=false;status.setText("FlixMomo declined this embedded session. No protection was changed.");}
                else if(pageReady)status.setText("FlixMomo in GharTV · select a result and use its player · direct connection");''')
 return s
edit('android-tv/app/src/main/java/in/ghartv/nova/FlixMomoActivity.java',android)
edit('android-tv/app/build.gradle.kts',lambda s:s.replace('versionCode = 28','versionCode = 29').replace('0.6.0-rc10.1-web-films','0.6.0-rc10.3-in-app-films'))
page='''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>FlixMomo in GharTV</title><style>:root{color-scheme:dark}body{margin:0;background:#08111b;color:#edf6f2;font:17px/1.65 system-ui}main{max-width:850px;margin:auto;padding:45px 24px}a{color:#9ae7cd}h1{font-size:clamp(34px,6vw,60px);line-height:1.08;letter-spacing:-2px}p{color:#b3c4cd}section{border:1px solid #304551;border-radius:16px;padding:25px;margin:28px 0;background:#11232c}code{overflow-wrap:anywhere;font-size:13px}small{color:#a8bec2}</style></head><body><main><a href="/">← Live television</a><h1>FlixMomo.<br>Inside GharTV.</h1><p>Search, choose a title and use the provider’s player in the GharTV film window—not an automated Brave session or an external browser tab.</p><section><h2>On this Mac</h2><p>The current GharTV review opens a native <strong>GharTV · FlixMomo</strong> window. Use its Search field, Browse and Back controls. Complete any provider sign-in or verification there.</p><p>To open just that window with the same downloaded launcher:</p><code>/bin/bash "$HOME/Downloads/GHARTV_REVIEW_RC10_3.command" --films-only</code></section><section><h2>On Android TV</h2><p>Open <strong>FlixMomo</strong> from GharTV’s guide or sign-in screen. Search results and the provider’s player stay inside the app. Press Back to return to television.</p></section><small>The native view uses a direct connection, not Tor. FlixMomo’s pages, attribution and provider controls remain intact. Provider availability and playback are not guaranteed. A browser-only deployment needs provider-supported embedding or an integration API; no security headers are removed.</small></main></body></html>'''
(R/'web-player/film-page.mjs').write_text('export function filmHTML(){return '+repr(page)+';}\n')
(R/'web-player/film-search.mjs').write_text('''/** No HTTP-triggered native execution and no external-tab substitute. */
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
''')
def launcher(s):
 s=s.replace("TAG='v0.6.0-rc10.3-native-films'","TAG='v0.6.0-rc10.3-in-app'").replace('0.6.0-rc10.1-web-films','0.6.0-rc10.3-in-app-films')
 s=re.sub(r'\b28\b','29',s).replace('code28','code29').replace('same signed Android','compiled Android')
 s=s.replace('RC10.3-NATIVE-FILMS','RC10.3-IN-APP').replace('RC10.3 native browser review','RC10.3 in-app films and visible TV review')
 s=once(s,'args=parser.parse_args(sys.argv[2:])',"parser.add_argument('--films-only',action='store_true');parser.add_argument('--tv-only',action='store_true');args=parser.parse_args(sys.argv[2:])")
 s=once(s,'def capture_support_once():','''def open_films():
 executable=RUNTIME/'web-player/native/GharTVFilmView';metadata=RUNTIME/'web-player/native/manifest.json'
 for p in (executable,metadata):safe(p)
 if not executable.is_file() or not metadata.is_file():raise Stop('NATIVE_FILM_COMPONENT_MISSING')
 identity=json.loads(metadata.read_text())
 if identity.get('source')!=WEB_SOURCE or digest(executable)!=identity.get('sha256'):raise Stop('NATIVE_FILM_COMPONENT_CHECKSUM_FAILED')
 if call(['/usr/bin/uname','-m'],5,False).stdout.strip()!='arm64':raise Stop('NATIVE_FILM_REVIEW_REQUIRES_APPLE_SILICON')
 executable.chmod(0o700)
 state=STATE/'native-films';mkdir(state);pidfile=state/'current.pid';safe(pidfile)
 pid=None
 if pidfile.is_file():
  text=pidfile.read_text().strip()
  if text.isdigit():
   observed=call(['/bin/ps','-p',text,'-o','uid=,command='],5,False).stdout.strip().split(None,1)
   if len(observed)==2 and observed[0]==str(os.getuid()) and observed[1]==str(executable):pid=int(text)
 if pid is None:
  with open(state/(RUN_ID+'.log'),'x') as log:
   process=subprocess.Popen([str(executable)],stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.DEVNULL,env={'PATH':'/usr/bin:/bin','LANG':'en_US.UTF-8'},start_new_session=True)
  pid=process.pid;write(pidfile,str(pid)+'\\n');time.sleep(1)
 import importlib.util
 spec=importlib.util.spec_from_file_location('ghartv_film_window',RUNTIME/'tools/tv_window.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
 result=helper.native_window(pid)
 r['film_window']=result['status'];r['film_playback']='NOT_VERIFIED'
 print('GharTV film window: '+r['film_window'],flush=True)
 if not result.get('window_observed'):raise Stop('NATIVE_FILM_WINDOW_NOT_CONFIRMED')

def capture_support_once():''')
 s=once(s,"  open_dashboard()\n  if args.web_only:","  if not args.tv_only:open_dashboard()\n  if args.films_only:\n   open_films();r['status']='FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED'\n  elif args.web_only:")
 s=once(s,"   performance_snapshot('before');review()", "   if not args.tv_only:\n    try:open_films()\n    except Stop as e:r['film_window']=str(e);print('Film component: '+str(e),flush=True)\n   performance_snapshot('before');review()")
 s=once(s," if health.get('commit')==WEB_SOURCE:return", " if health.get('commit')==WEB_SOURCE:return\n native_pid=STATE/'native-films/current.pid'\n if native_pid.is_file() and native_pid.read_text().strip().isdigit():\n  cmd=call(['/bin/ps','-p',native_pid.read_text().strip(),'-o','command='],5,False).stdout.strip()\n  if cmd==str(RUNTIME/'web-player/native/GharTVFilmView'):raise Stop('CLOSE_GHARTV_FILM_WINDOW_BEFORE_UPDATING')")
 return s
edit('tools/owner_review.command.in',launcher)
edit('tools/tv-first/check_contract.py',lambda s:re.sub(r'\b28\b','29',s).replace('CODE28','CODE29'))
edit('web-player/review-sync.mjs',lambda s:s.replace("'ANDROID_READY_WINDOW_UNCONFIRMED'].includes(r.status)","'ANDROID_READY_WINDOW_UNCONFIRMED','FILM_WINDOW_OPEN_ANDROID_NOT_TOUCHED'].includes(r.status)"))
p=R/'tools/rc103/package.py';s=p.read_text()
s=s.replace("APP_SOURCE='9457654eafe86a08c402c6829c6cae3312c3e196'","APP_SOURCE=None").replace("APK_HASH='cc2d95be434f6b3a8de8986f0a17813507c98471d2f39d0230108f3f8b47d810'","APK_HASH=None")
s=s.replace("TAG='v0.6.0-rc10.3-native-films'","TAG='v0.6.0-rc10.3-in-app'")
s=s.replace("assert re.fullmatch('[a-f0-9]{40}',source)","assert re.fullmatch('[a-f0-9]{40}',source)\nAPP_SOURCE=source\ncompiled_apk=Path(sys.argv[4]);APK_HASH=hashlib.sha256(compiled_apk.read_bytes()).hexdigest()")
s=s.replace("apk=z.read(root+'assets/GharTV-review-unsigned.apk')","apk=compiled_apk.read_bytes()")
s=s.replace("'version_code':28","'version_code':29").replace('0.6.0-rc10.1-web-films','0.6.0-rc10.3-in-app-films').replace('RC10.3-NATIVE-FILMS','RC10.3-IN-APP').replace("'android_rebuilt':False","'android_rebuilt':True")
s=s.replace("'film-browser-worker.mjs'}","'film-browser-worker.mjs','native-provider.mjs'}")
p.write_text(s)
print('RC103_IN_APP_CODE29_INTEGRATED')
