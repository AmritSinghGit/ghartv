"""RC10.3.1: native .app lifecycle and exact provider migration observed on Sept21.
No UA, browser-detection, security-header, CAPTCHA or DRM changes.
"""
from pathlib import Path
import re
R=Path(__file__).resolve().parents[2]
def edit(name,fn):
 p=R/name;s=p.read_text();p.write_text(fn(s))
def once(s,a,b):
 if s.count(a)!=1:raise RuntimeError('ANCHOR_CHANGED: '+a[:100])
 return s.replace(a,b)
old_exe='web-player/native/GharTVFilmView'
new_exe='web-player/native/GharTVFilms.app/Contents/MacOS/GharTVFilmView'

def swift(s):
 if 'let providerHosts:' in s:return s
 s=once(s,'    var window: NSWindow!', '    let providerHosts: Set<String> = ["flixmomo.app", "flixmomo.st", "www.flixmomo.st"]\n    var window: NSWindow!')
 s=once(s,'        if action == "browse" { return URL(string: "https://flixmomo.app/") }', '''        let current = browser?.url
        let origin = current?.scheme == "https" && providerHosts.contains(current?.host ?? "") ? "https://" + current!.host! : "https://flixmomo.app"
        if action == "browse" { return URL(string: origin + "/") }''')
 s=s.replace('var url = URLComponents(string: "https://flixmomo.app/search")!', 'var url = URLComponents(string: origin + "/search")!')
 s=s.replace('@objc func browse() { open(URL(string: "https://flixmomo.app/")!) }','@objc func browse() { if let url = target("browse", "") { open(url) } }')
 s=s.replace('(!top || host == "flixmomo.app")','(!top || providerHosts.contains(host))')
 s=s.replace('"providerPage":webView.url?.host == "flixmomo.app"','"providerPage":providerHosts.contains(webView.url?.host ?? "")')
 s=s.replace('url.host == "flixmomo.app", url.user == nil','providerHosts.contains(url.host ?? ""), url.user == nil')
 return s
edit('tools/films/GharTVFilmView.swift',swift)
def android(s):
 if 'private static boolean providerHost' not in s:
  s=s.replace('    static boolean allowedTop(Uri u){','''    private static boolean providerHost(String host){
        return "flixmomo.app".equals(host) || "flixmomo.st".equals(host) || "www.flixmomo.st".equals(host);
    }
    static boolean allowedTop(Uri u){''').replace('"flixmomo.app".equals(u.getHost())','providerHost(u.getHost())')
  s=s.replace('browser.loadUrl(HOME+"search?q="+Uri.encode(text));','''Uri current=Uri.parse(browser.getUrl()==null?HOME:browser.getUrl());
        String origin=allowedTop(current)?"https://"+current.getHost():"https://flixmomo.app";
        browser.loadUrl(origin+"/search?q="+Uri.encode(text));''')
 return s
edit('android-tv/app/src/main/java/in/ghartv/nova/FlixMomoActivity.java',android)
edit('android-tv/app/build.gradle.kts',lambda s:s.replace('versionCode = 29','versionCode = 30').replace('0.6.0-rc10.3-in-app-films','0.6.0-rc10.3.1-in-app-films'))

def launcher(s):
 s=s.replace(old_exe,new_exe).replace("'LANG':'en_US.UTF-8'},start_new_session=True)","'LANG':'en_US.UTF-8','HOME':str(HOME)},start_new_session=True)")
 s=s.replace("TAG='v0.6.0-rc10.3-in-app'","TAG='v0.6.0-rc10.3.1-in-app'").replace('0.6.0-rc10.3-in-app-films','0.6.0-rc10.3.1-in-app-films').replace('RC10.3-IN-APP','RC10.3.1-IN-APP').replace('New code29 APK','New code30 APK')
 # Version constants only; curl timeout and other unrelated numeric statuses stay unchanged.
 for a,b in [("version_code=29","version_code=30"),("'version_code':29","'version_code':30"),("version_code')!=29","version_code')!=30"),("versionCode='29'","versionCode='30'"),('int(codes[0])>29','int(codes[0])>30'),('int(codes[0])==29','int(codes[0])==30'),('int(codes[0])<29','int(codes[0])<30'),('Different code-29 APK','Different code-30 APK'),("versionCode',0))>=29","versionCode',0))>=30")]:s=s.replace(a,b)
 # A prior native window is never replaced or duplicated during an update.
 s=s.replace("if cmd==str(RUNTIME/'"+new_exe+"'):raise Stop", "if cmd in (str(RUNTIME/'"+new_exe+"'),str(RUNTIME/'"+old_exe+"')):raise Stop")
 return s
edit('tools/owner_review.command.in',launcher)
edit('tools/run_owner_bundle.command.in',lambda s:s.replace("known={'cf0c620", "known={'fe8992e2e202aebf7e697db3839a3d053d1476b89f94d5b6471cdf0fc7468c9d','cf0c620") if 'fe8992e2e202aebf7e697db3839a3d053d1476b89f94d5b6471cdf0fc7468c9d' not in s else s)
edit('web-player/server.mjs',lambda s:s.replace('0.6.0-rc10.3-in-app-films','0.6.0-rc10.3.1-in-app-films').replace('RC10.3-NATIVE-FILMS-TV-WINDOW','RC10.3.1-IN-APP-NATIVE-BUNDLE'))
edit('web-player/film-page.mjs',lambda s:s.replace('GHARTV_REVIEW_RC10_3.command','GHARTV_REVIEW_RC10_3_1.command'))
edit('tools/tv-first/check_contract.py',lambda s:re.sub(r'\b29\b','30',s).replace('CODE29','CODE30'))
# Accept the equivalent encoded URL using the verified current provider origin.
edit('web-player/test/films.test.mjs',lambda s:s.replace(r'HOME\+"search\?q="',r'origin\+"\/search\?q="').replace('version_code=29','version_code=30'))

def package(s):
 return s.replace("TAG='v0.6.0-rc10.3-in-app'","TAG='v0.6.0-rc10.3.1-in-app'").replace('GHARTV_RC10_3_REVIEW','GHARTV_RC10_3_1_REVIEW').replace('GHARTV_REVIEW_RC10_3.command','GHARTV_REVIEW_RC10_3_1.command').replace("'version_code':29","'version_code':30").replace('0.6.0-rc10.3-in-app-films','0.6.0-rc10.3.1-in-app-films').replace('RC10.3-IN-APP','RC10.3.1-IN-APP')
edit('tools/rc103/package.py',package)

def smoke(s):
 s=s.replace('GHARTV_RC10_3_REVIEW','GHARTV_RC10_3_1_REVIEW').replace(old_exe,new_exe)
 s=s.replace("env={'PATH':'/usr/bin:/bin','LANG':'en_US.UTF-8'})", "env={'PATH':'/usr/bin:/bin','LANG':'en_US.UTF-8','HOME':str(home)})")
 s=s.replace("events=[];messages=queue.Queue()", "events=[];messages=queue.Queue()")
 # Require an actual provider navigation outcome instead of only a native window.
 s=s.replace("  provider='NOT_CONFIRMED'", "  assert any(x.get('event') in ('page_finished','provider_blocked','navigation_failed') for x in events),'NATIVE_PROVIDER_NAVIGATION_NOT_OBSERVED'\n  provider='NOT_CONFIRMED'")
 s=s.replace("settle=time.monotonic()+8", "settle=time.monotonic()+15")
 return s
edit('tools/rc103/mac_in_app_smoke.py',smoke)
edit('tools/rc103/REVIEW.md',lambda s:s.replace('RC10.3','RC10.3.1').replace('code29','code30').replace('GHARTV_REVIEW_RC10_3.command','GHARTV_REVIEW_RC10_3_1.command')+'\n## Native application lifecycle and provider migration\nThe native component is now an actual macOS application bundle with a bundle identifier and verified Info.plist, rather than a bare WebKit executable. A separate ordinary native observation returned live search results after following the migration explicitly advertised by flixmomo.app to flixmomo.st. Only those three exact provider hosts (app, st, www.st) are accepted; arbitrary external top-level navigation remains blocked. Search uses the current verified provider origin, preserving the provider pages and player. This is not a claim that every title plays or that the Android migration was tested on a physical TV.\n')
edit('GHARTV_LANE_PROGRESS.md',lambda s:s.replace('RC10.3','RC10.3.1').replace('code29','code30'))
print('RC1031_NATIVE_APPLICATION_BUNDLE_AND_PROVIDER_MIGRATION_INTEGRATED')
