"""Read only this app's allowlisted technical transition tag on the existing AVD.
No screenshots, whole-system logs, request URLs, collector credentials or uploads.
"""
import json,re
SERIAL='emulator-5580'
AVD='GharTV_Nova_Manual_google_tv_API36'
EVENTS=set('PLAY_REQUEST FULLSCREEN_REQUEST FULLSCREEN_ENTER FULLSCREEN_EXIT FULLSCREEN_MENU FULLSCREEN_RESUME POINTER_ENTER POINTER_EXIT POINTER_WAKE POINTER_FADE POINTER_CLICK USE_PAGE TOOLBAR_OPEN BACK MENU ACTION_PENDING NAVIGATING TARGET_CONTROL TARGET_VIDEO TARGET_FRAME TARGET_OBSCURED TARGET_CHANGED VISUAL_TIMEOUT NOT_FOUND MEDIA_NOT_READY AMBIGUOUS_MEDIA CLICK_SENT_NOT_PLAYBACK_PROOF ALREADY_PLAYING'.split())
LAYERS=set('FULLSCREEN_MENU FULLSCREEN POINTER DETAIL DISCOVER TOOLBAR CONTROLS PAGE'.split())
def parse(text):
    rows=[]
    for line in text.splitlines():
        if not line.startswith('GHUX_V1 ') or len(line)>1200:continue
        try:
            row=json.loads(line[8:])
            if row.get('schema')!='ghartv.ui-transition.v1' or row.get('event') not in EVENTS or row.get('layer') not in LAYERS:continue
            if any(type(row.get(k)) is not int or not 0<=row[k]<=1000000000 for k in ('seq','version_code','elapsed_ms')):continue
            if any(type(row.get(k)) is not bool for k in ('pointer_enabled','pointer_visible')):continue
            # Do not copy unknown fields even from a matching log tag.
            rows.append({k:row[k] for k in ('schema','version_code','seq','elapsed_ms','event','layer','pointer_enabled','pointer_visible')})
        except (ValueError,TypeError):continue
    return rows[-128:]
def capture(adb,call):
    def run(args,seconds=6):return call([adb,'-s',SERIAL]+args,seconds,False)
    try:
        identity=run(['emu','avd','name'])
        names=[s.strip() for s in identity.stdout.splitlines() if s.strip() not in ('','OK')]
        if identity.returncode or names!=[AVD]:return {'state':'EXISTING_AVD_NOT_CONFIRMED','events':[]}
        pid=run(['shell','pidof','in.ghartv.nova']).stdout.strip()
        if not pid.isdigit():return {'state':'APP_NOT_RUNNING_NO_PRIOR_ACTION_TRACE','events':[]}
        log=run(['logcat','-d','-v','raw','--pid='+pid,'-t','512','-s','GharTVReview:I','*:S'])
        if log.returncode:return {'state':'APP_TRACE_READ_FAILED','events':[]}
        rows=parse(log.stdout)
        return {'schema':'ghartv.owner-ui-capture.v1','state':'APP_TRACE_CAPTURED' if rows else 'NO_COMPATIBLE_UI_TRACE_IN_BUFFER',
                'events':rows,'bounded_sample':True,'raw_log_saved':False,'uploaded':False,
                'note':'Review47 does not have this trace; absence is not absence of actions.'}
    except Exception:return {'state':'APP_TRACE_UNAVAILABLE','events':[]}
