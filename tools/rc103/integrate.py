"""Guarded RC10.3 integration, run once in the cloud build checkout."""
from pathlib import Path
R=Path(__file__).resolve().parents[2]
def change(path,fn):
 p=R/path;s=p.read_text();p.write_text(fn(s))
def once(s,a,b):
 if s.count(a)!=1:raise RuntimeError('ANCHOR_CHANGED: '+a[:100])
 return s.replace(a,b)

def launcher(s):
 s=s.replace("TAG='v0.6.0-rc10.2-web-security'","TAG='v0.6.0-rc10.3-native-films'")
 s=s.replace('CYAN REVIEW 16','CYAN REVIEW 17').replace('CYAN-16-','CYAN-17-').replace('GHARTV_CYAN_REVIEW_16_HANDOFF','GHARTV_CYAN_REVIEW_17_HANDOFF').replace('CYAN16-VIEWER-SECURITY','CYAN17-NATIVE-FILMS')
 s=s.replace('RC10.2-WEB-SECURITY','RC10.3-NATIVE-FILMS').replace('RC10.2 browser review','RC10.3 native browser review')
 s=once(s,"   call([adb,'-s',serial,'shell','am','force-stop',PACKAGE])","   call([adb,'-s',serial,'shell','wm','dismiss-keyguard'],check=False)")
 s=once(s,"   r['emulator']='RC10_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND';r['phase']='REVIEW_OPEN';r['status']='REVIEW_READY'", """   r['emulator']='RC10_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND';r['phase']='REVIEW_OPEN'
   window=transport.request_window_details()
   r['mac_window']=window['status'];r['mac_window_observed']=window.get('window_observed') is True
   r['mac_window_frontmost']=window.get('app_active') is True
   write(RUN/'TV_WINDOW.json',json.dumps(window,indent=2)+'\\n')
   r['status']='REVIEW_READY' if r['mac_window_observed'] and r['mac_window_frontmost'] else 'ANDROID_READY_WINDOW_UNCONFIRMED'
   if r['status']!='REVIEW_READY':
    r['blocker']='TV_WINDOW_'+window['status']
    r['next_action']='SHOW_EXISTING_NOVA_WINDOW_NO_SECOND_VM'
   print('Mac TV window: '+r['mac_window'],flush=True)""")
 s=s.replace("   if obsolete.is_file() and not obsolete.is_symlink() and digest(obsolete)==UNSIGNED:","   if r['status']=='REVIEW_READY' and obsolete.is_file() and not obsolete.is_symlink() and digest(obsolete)==UNSIGNED:")
 s=once(s,"'obsidian','receipt_sync','receipt_url')","'obsidian','receipt_sync','receipt_url','mac_window','mac_window_observed','mac_window_frontmost')")
 s=once(s," print('Android: '+r.get('emulator','UNCHANGED'))"," print('Android: '+r.get('emulator','UNCHANGED'))\n print('TV window: '+r.get('mac_window','NOT_CHECKED'))")
 return s
change('tools/owner_review.command.in',launcher)
change('tools/run_owner_bundle.command.in',lambda s:once(s,"known={'7a7bf90", "known={'cf0c620c908eba1af060a91b64c239ec2da2a97e05b768a034738368b83eef8b','7a7bf90").replace('RC10.2','RC10.3').replace("'delivery_revision':'RC10.1-STARTUP-R2'","'delivery_revision':'RC10.3-NATIVE-FILMS'"))
change('web-player/server.mjs',lambda s:once(s,'RC10.2-VIEWER-SECURITY-NATIVE-HLS','RC10.3-NATIVE-FILMS-TV-WINDOW'))

def transport(s):
 a=s.index('def request_window():');b=s.index('\ndef host_dns',a)
 s=s[:a]+'''def request_window_details():
    import importlib.util
    path=Path(__file__).with_name('tv_window.py')
    spec=importlib.util.spec_from_file_location('ghartv_tv_window',path)
    helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    return helper.request_window_details(avd_processes())

def request_window():
    return request_window_details()['status']
'''+s[b:]
 s=once(s,"            result.update(ok=True,foreground=True,window=request_window(),stage='APP_OPEN')", """            window=request_window_details()
            result.update(ok=window.get('window_observed') is True,foreground=True,window=window['status'],window_observed=window.get('window_observed') is True,stage='APP_OPEN')
            if not result['ok']:result['error']='TV_WINDOW_'+window['status']""")
 s=once(s,"            adb,started=attach_or_start(root,run)","""            pressure=call(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level']).stdout.strip()
            if pressure!='1':
                boot=call([adb,'-s',SERIAL,'shell','getprop','sys.boot_completed'],4).stdout.strip()
                if boot!='1':raise Hold('HOST_PRESSURE_NO_NEW_EMULATOR')
                verify_target(adb)
            adb,started=attach_or_start(root,run)""")
 return s
change('tools/tv_local.py',transport)

def mirror(s):
 s=s.replace("'SIGNED_UPDATE_PREPARED_REVIEW_PENDING'].includes(r.status)","'SIGNED_UPDATE_PREPARED_REVIEW_PENDING','ANDROID_READY_WINDOW_UNCONFIRMED'].includes(r.status)")
 s=once(s,"    web_source:/^[a-f0-9]{40}$/", """    mac_window:['MAC_WINDOW_FRONTMOST_OBSERVED','MAC_WINDOW_ONSCREEN_OBSERVED','MAC_WINDOW_NOT_ONSCREEN','MAC_WINDOW_UNCONFIRMED','MAC_WINDOW_QUERY_UNAVAILABLE','MAC_TARGET_CHANGED_PRESERVED','MAC_TARGET_UNCONFIRMED','MAC_TARGET_NOT_UNIQUE','HEADLESS_EMULATOR_PRESERVED','ANDROID_STUDIO_EMBEDDED_WINDOW'].includes(r.mac_window)?r.mac_window:'NOT_CHECKED',
    mac_window_observed:r.mac_window_observed===true,
    mac_window_frontmost:r.mac_window_frontmost===true,
    web_source:/^[a-f0-9]{40}$/""")
 s=s.replace(":r.blocker?'OTHER_BLOCKER_SEE_PRIVATE_RECEIPT':null,",":typeof r.blocker==='string'&&r.blocker.startsWith('TV_WINDOW_')?'TV_WINDOW_NOT_CONFIRMED':r.blocker?'OTHER_BLOCKER_SEE_PRIVATE_RECEIPT':null,")
 s=s.replace("next_action:r.status==='WEB_REVIEW_READY_ANDROID_HELD'?", "next_action:r.status==='ANDROID_READY_WINDOW_UNCONFIRMED'?'SHOW_EXISTING_NOVA_WINDOW_NO_SECOND_VM':r.status==='WEB_REVIEW_READY_ANDROID_HELD'?")
 return s
change('web-player/review-sync.mjs',mirror)
change('tools/tv-first/check_contract.py',lambda s:s.replace('GHARTV_CYAN_REVIEW_16_HANDOFF','GHARTV_CYAN_REVIEW_17_HANDOFF').replace('GHARTV_CYAN_REVIEW_15_HANDOFF','GHARTV_CYAN_REVIEW_16_HANDOFF').replace('REVIEW16_LABEL','REVIEW17_LABEL'))
change('web-player/test/browser-security.test.mjs',lambda s:s.replace('Open FlixMomo in this browser','Search FlixMomo'))
print('RC103_NATIVE_SEARCH_AND_TV_WINDOW_INTEGRATED')
