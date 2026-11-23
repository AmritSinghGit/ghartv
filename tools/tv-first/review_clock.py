"""Clock preflight for the already-selected GharTV emulator, never a physical TV.

No root, trust-store changes, DNS changes, new emulator or host clock writes.
The existing opener calls this after its normal AVD identity check.
"""
import datetime as dt
import email.utils
import json
import time
import urllib.request

SERIAL = 'emulator-5580'
AVD = 'GharTV_Nova_Manual_google_tv_API36'
REFERENCE_URLS = ('https://api.github.com/repos/AmritSinghGit/ghartv',
                  'https://ghartv-telemetry.ghartv-47d9a0.workers.dev/health')

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def reference_time():
    """Require two HTTPS Date headers to agree with each other AND the Mac."""
    refs=[]
    opener=urllib.request.build_opener(NoRedirect())
    for url in REFERENCE_URLS:
        started=time.monotonic()
        request=urllib.request.Request(url+'?ghartv_clock='+str(time.time_ns()),
            headers={'Cache-Control':'no-cache','User-Agent':'GharTV-owner-clock-preflight/42'})
        with opener.open(request,timeout=7) as response:
            if response.status!=200:raise ValueError('REFERENCE_UNAVAILABLE')
            if int(response.headers.get('Age','0'))>30:raise ValueError('REFERENCE_STALE')
            value=email.utils.parsedate_to_datetime(response.headers['Date']).timestamp()
            if time.monotonic()-started>10:raise ValueError('REFERENCE_TOO_SLOW')
            refs.append((value,time.monotonic()))
    now=time.monotonic()
    epochs=[v+now-m for v,m in refs]
    midpoint=sum(epochs)/2
    if max(epochs)-min(epochs)>60 or abs(midpoint-time.time())>120:
        raise ValueError('MAC_CLOCK_NOT_CORROBORATED')
    return midpoint

def reconcile(adb, serial, avd, call, reference=reference_time):
    result={'schema':'ghartv.emulator-clock.v1','state':'NOT_CHECKED','changed':False,
            'physical_tv_touched':False,'certificate_checks_changed':False}
    if serial!=SERIAL or avd!=AVD:
        result['state']='TARGET_REJECTED';return result
    def shell(*args):return call([adb,'-s',serial,'shell',*args],10,False)
    try:
        name=call([adb,'-s',serial,'emu','avd','name'],8,False)
        names=[s.strip() for s in name.stdout.splitlines() if s.strip() not in ('','OK')]
        if name.returncode!=0 or names!=[AVD] or shell('getprop','ro.kernel.qemu').stdout.strip()!='1':
            result['state']='TARGET_REJECTED';return result
        read=shell('date','+%s')
        if read.returncode!=0 or not read.stdout.strip().isdigit():
            result['state']='TV_CLOCK_UNREADABLE';return result
        before=int(read.stdout.strip());host=time.time();skew=round(before-host)
        result.update(offset_before_seconds=skew,tv_epoch_before=before,host_epoch_observed=int(host))
        if abs(skew)<=120:
            result['state']='CLOCK_ALIGNED';return result
        # Do not auto-rewrite a wildly different unknown date. Seven days covers
        # resume/snapshot drift; anything larger needs explicit date review.
        if abs(skew)>7*86400:
            result['state']='CLOCK_REQUIRES_MANUAL_REVIEW';return result
        try:target=reference()
        except Exception:
            result['state']='CLOCK_REFERENCE_UNVERIFIED_NO_CHANGE';return result
        if abs(target-time.time())>120:
            result['state']='CLOCK_REFERENCE_UNVERIFIED_NO_CHANGE';return result
        result['reference']='TWO_HTTPS_DATES_AND_MAC_AGREE'
        # Android's documented shell service, no adb root or permission changes.
        write=shell('cmd','alarm','set-time',str(round(target*1000)))
        result['set_time_exit']=write.returncode
        if write.returncode!=0 or any(x in (write.stdout+write.stderr).lower() for x in ('exception','denied','not permitted','unable','failed','unknown command')):
            result['state']='CLOCK_SET_NOT_PERMITTED';return result
        checked=shell('date','+%s')
        if checked.returncode!=0 or not checked.stdout.strip().isdigit():
            result['state']='CLOCK_READBACK_REQUIRED';return result
        result['offset_after_seconds']=round(int(checked.stdout.strip())-time.time())
        result['changed']=abs(result['offset_after_seconds'])<=10
        result['state']='EMULATOR_CLOCK_CORRECTED' if result['changed'] else 'CLOCK_READBACK_FAILED'
        return result
    except Exception:
        result['state']='CLOCK_CHECK_FAILED';return result

def capture_probe(adb, call, probe_id):
    """Read only this app's fixed-schema diagnostic tag; no system-wide log export."""
    if not probe_id.startswith('REVIEW42_') or not probe_id.replace('_','').isalnum():
        return {'state':'PROBE_ID_REJECTED'}
    pid=call([adb,'-s',SERIAL,'shell','pidof','in.ghartv.nova'],8,False).stdout.strip()
    if not pid.isdigit():return {'state':'APP_PID_NOT_CONFIRMED'}
    for _ in range(7):
        log=call([adb,'-s',SERIAL,'logcat','-d','-v','raw','--pid='+pid,'-t','60','-s','GharTVNetwork:I','*:S'],8,False)
        for line in reversed(log.stdout.splitlines()):
            if not line.startswith('GHNET_V1 ') or len(line)>16000:continue
            try:
                row=json.loads(line[9:])
                if row.get('probe_id')==probe_id and row.get('schema')=='ghartv.network-probe.v1':
                    return row
            except (ValueError,TypeError):pass
        time.sleep(2)
    return {'state':'APP_NETWORK_PROBE_NOT_RECEIVED'}
