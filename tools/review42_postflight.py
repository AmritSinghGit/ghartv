#!/usr/bin/env python3
"""Resume the SAME signed GharTV42 through its existing opener and audit collection.

No APK rebuild, signing-key access, new controller, service, emulator, analytics
instance, telemetry opt-in, warehouse write, raw-data export or household release.
The Analytics service is observed, not silently overwritten/restarted.
"""
from __future__ import annotations
import ast
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shlex
import ssl
import subprocess
import sys
import time
import types
import urllib.error
import urllib.parse
import urllib.request
import zipfile

REVISION = 'review42-postflight-r1'
SOURCE = 'fab21208c75c43e918ea6efbd130831720cac9ce'
ZIP_SHA = '670933773a79f528a2bc5c90e0576a03aaba03de0ec7d4201eb7b232c6d8ac35'
APK_SHA = 'bf02954bcd7e2a4268c04ae866fd7cf2b0cd6b34c6af5254e4d6ca0ffbe7015f'
SIGNED = '88d58bf6407084b92bea4c5156d1e7b2d4811d1370d0d433cf9504550e2ff867'
COLLECTOR = 'https://ghartv-telemetry.ghartv-47d9a0.workers.dev'
HOME = Path.home()
STATE = HOME/'Library/Application Support/GharTV/owner-review'
CONFIG = HOME/'Library/Application Support/GharTV/telemetry/collector.env'
TEMPLATE_SHA = '4fbf2e39f184d0a784e84b3fadd5dcabd3c4a9b136514b9ea331784038e6aba5'

class PostflightError(RuntimeError):
    pass


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def owned(path, limit, private=False):
    path = Path(path).absolute()
    if any(p.is_symlink() for p in (path, *path.parents)):
        raise PostflightError('SYMLINKED_PATH_PRESERVED')
    stat = path.stat()
    if not path.is_file() or stat.st_uid != os.getuid() or stat.st_size > limit:
        raise PostflightError('UNTRUSTED_OR_OVERSIZED_LOCAL_FILE')
    if private and stat.st_mode & 0o077:
        raise PostflightError('PRIVATE_CONFIG_PERMISSIONS_REQUIRE_REVIEW')
    return path.read_bytes()


def adb_step(args):
    a = list(map(str,args))[1:]
    if 'install' in a: return 'INSTALL_IN_PLACE'
    if 'pull' in a: return 'READ_INSTALLED_APK'
    if 'sha256sum' in a: return 'VERIFY_INSTALLED_BYTES'
    if 'path' in a and 'pm' in a: return 'READ_PACKAGE_PATH'
    if 'avd' in a and 'name' in a: return 'VERIFY_AVD_IDENTITY'
    if 'devices' in a: return 'LIST_TRANSPORTS'
    if 'set-time' in a: return 'SET_EMULATOR_CLOCK'
    if 'date' in a: return 'READ_EMULATOR_CLOCK'
    if 'start' in a and 'am' in a: return 'OPEN_MAIN_ACTIVITY'
    if 'getprop' in a: return 'READ_BOOT_PROPERTY'
    if 'logcat' in a: return 'READ_APP_NETWORK_PROBE'
    if 'keyevent' in a: return 'WAKE_TV'
    return 'ADB_OPERATION'


def adb_reason(stdout, stderr):
    message = (stdout or '')+'\n'+(stderr or '')
    match = re.search(r'\b(INSTALL_(?:FAILED|PARSE_FAILED)_[A-Z0-9_]+)\b', message)
    if match: return match[1]
    lower = message.lower()
    for needle, reason in (
        ('device offline','DEVICE_OFFLINE'), ('device unauthorized','DEVICE_UNAUTHORIZED'),
        ('no devices/emulators','DEVICE_NOT_FOUND'), ('device not found','DEVICE_NOT_FOUND'),
        ("not found", 'DEVICE_OR_PATH_NOT_FOUND'), ('insufficient storage','INSUFFICIENT_STORAGE'),
        ('no space left','INSUFFICIENT_STORAGE'), ('permission denied','PERMISSION_DENIED'),
        ('read-only file system','READ_ONLY_FILESYSTEM'), ('failed to read','TRANSPORT_READ_FAILED'),
        ('connection reset','TRANSPORT_DISCONNECTED'), ('closed','TRANSPORT_CLOSED')):
        if needle in lower: return reason
    return 'NONZERO_EXIT_PRIVATE_DETAIL_SAVED'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*a,**kw): return None


def get_json(url, token='', timeout=25, headers=None):
    p = urllib.parse.urlsplit(url)
    if token:
        if (p.scheme,p.netloc,p.path)!=(
                'https','ghartv-telemetry.ghartv-47d9a0.workers.dev','/v1/admin/export'):
            raise PostflightError('TOKEN_DESTINATION_REJECTED')
    elif not (url==COLLECTOR+'/health' or
            (p.scheme=='http' and p.hostname in ('127.0.0.1','localhost') and p.port
             and p.path in ('/api/v1/tenants/ghartv/health','/api/v1/tenants/ghartv/analytics'))):
        raise PostflightError('AUDIT_DESTINATION_REJECTED')
    if p.username or p.password or p.fragment:
        raise PostflightError('AUDIT_DESTINATION_REJECTED')
    h={'Accept':'application/json','Cache-Control':'no-cache','User-Agent':'GharTV42-postflight/1'}
    h.update(headers or {})
    if token: h['Authorization']='Bearer '+token
    req=urllib.request.Request(url,headers=h)
    # No ambient proxies or authenticated redirects. Normal certificate checks.
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    with opener.open(req,timeout=timeout) as r:
        raw=r.read(16*1024*1024+1)
        if len(raw)>16*1024*1024:raise PostflightError('EXPORT_TOO_LARGE')
        return json.loads(raw)


def parse_time(v):
    if v is None or isinstance(v,bool):return None
    try:
        if isinstance(v,(int,float)) or str(v).strip().isdigit():
            v=float(v);return dt.datetime.fromtimestamp(v/1000 if v>10_000_000_000 else v,dt.timezone.utc)
        d=dt.datetime.fromisoformat(str(v).replace('Z','+00:00'))
        return d.replace(tzinfo=dt.timezone.utc) if d.tzinfo is None else d
    except (ValueError,TypeError,OverflowError,OSError):return None


def export_counts(data):
    if not isinstance(data,dict) or data.get('ok') is not True or not isinstance(data.get('events'),list):
        raise PostflightError('COLLECTOR_SCHEMA_NOT_RECOGNIZED')
    rows=data['events']
    if len(rows)>5000:raise PostflightError('EXPORT_BOUND_EXCEEDED')
    valid=legacy=delivery=recent=0
    now=dt.datetime.now(dt.timezone.utc)
    for row in rows:
        if not isinstance(row,dict):continue
        timestamp=next((d for key in ('received_at','client_ts','event_timestamp','timestamp','created_at')
                        if (d:=parse_time(row.get(key))) is not None),None)
        if timestamp:
            valid+=1
            if now-dt.timedelta(days=30)<=timestamp<=now+dt.timedelta(minutes=2):recent+=1
        if any(parse_time(row.get(k)) is not None for k in ('event_timestamp','timestamp','created_at')):legacy+=1
        if row.get('event_name',row.get('name'))=='diagnostic_delivery_check':delivery+=1
    return {'state':'COLLECTOR_HAS_EVENTS' if rows else 'COLLECTOR_RETURNED_NO_EVENTS',
            'export_rows':len(rows),'parseable_rows':valid,'legacy_parseable_rows':legacy,
            'recent_parseable_rows':recent,'manual_delivery_checks':delivery,
            'possibly_truncated':len(rows)>=5000,'raw_rows_saved':False,
            'no_events_scope':'this authorized 30-day bounded export, not all app use'}


def collection_audit(target=None, request=get_json):
    result={'schema':'ghartv.collection-postflight.v1','observation':'READ_ONLY',
            'consent_changed':False,'test_events_sent':False,'service_restarted':False,
            'analytics_repair_deployed':False}
    # Public health is separate from authorized export and app delivery.
    try:
        health=request(COLLECTOR+'/health',timeout=10)
        result['collector_health']='RESPONDED' if isinstance(health,dict) else 'UNRECOGNIZED'
    except Exception as e:result['collector_health']='CHECK_FAILED_'+type(e).__name__.upper()
    token=''
    try:
        values={}
        if CONFIG.exists():
            for line in owned(CONFIG,16384,True).decode().splitlines():
                line=line.strip()
                if line.startswith('export '):line=line[7:].strip()
                if not line or line.startswith('#') or '=' not in line:continue
                k,v=line.split('=',1)
                if k.strip() in ('GHARTV_TELEMETRY_ENDPOINT','GHARTV_TELEMETRY_ADMIN_TOKEN'):
                    words=shlex.split(v,comments=True);values[k.strip()]=words[0] if len(words)==1 else ''
        endpoint=os.environ.get('GHARTV_TELEMETRY_ENDPOINT') or values.get('GHARTV_TELEMETRY_ENDPOINT','')
        token=os.environ.get('GHARTV_TELEMETRY_ADMIN_TOKEN') or values.get('GHARTV_TELEMETRY_ADMIN_TOKEN','')
        if not endpoint or not token:
            result['state']='COLLECTOR_CONFIG_MISSING_NO_USAGE_CONCLUSION'
        elif endpoint.rstrip('/')!=COLLECTOR:
            result['state']='COLLECTOR_CONFIG_ORIGIN_MISMATCH_TOKEN_NOT_SENT'
        else:
            result['collector_config_verified']=True
            data=request(COLLECTOR+'/v1/admin/export?days=30&limit=5000',token=token,timeout=35)
            result.update(export_counts(data));del data
    except urllib.error.HTTPError as e:
        result['state']='COLLECTOR_AUTH_REJECTED' if e.code in (401,403) else 'COLLECTOR_HTTP_'+str(e.code)
        result['http_status']=e.code
    except Exception as e:
        result['state']=str(e) if isinstance(e,PostflightError) else 'COLLECTOR_READ_FAILED_'+type(e).__name__.upper()
    finally:token=''
    if target:
        p=urllib.parse.urlsplit(target)
        if p.scheme=='http' and p.hostname in ('localhost','127.0.0.1') and p.port and not p.username and not p.password:
            origin=p.scheme+'://'+p.netloc
            try:
                # Same owner role header used by this local owner report; no cookies/tokens copied.
                health=request(origin+'/api/v1/tenants/ghartv/health',timeout=5,
                               headers={'X-Operon-Role':'owner'})
                if health.get('tenant')!='ghartv':raise PostflightError('WRONG_TENANT_RESPONSE')
                result['report_configured']=health.get('configured') is True
                result['report_adapter_revision']=health.get('adapter_revision','LEGACY_NO_REVISION')
                result['report_repair_active']=health.get('adapter_revision')=='ghartv-collector-schema-20260928'
            except urllib.error.HTTPError as e:result['report_health']='HTTP_'+str(e.code)
            except Exception as e:result['report_health']='UNVERIFIED_'+type(e).__name__.upper()
    if result.get('export_rows',0)>0 and result.get('legacy_parseable_rows')==0 and not result.get('report_repair_active'):
        result['finding']='COLLECTOR_HAS_DATA_LEGACY_REPORT_DROPS_TIMESTAMPS'
    elif result.get('report_repair_active') is False:
        result['finding']='REPORT_REPAIR_NOT_ACTIVE_COLLECTION_STATE_REPORTED_SEPARATELY'
    return result


def make_module(source_zip):
    with zipfile.ZipFile(source_zip) as z:
        raw=z.read('GHARTV_OPEN_REVIEW.command')
    if sha(raw)!=TEMPLATE_SHA:raise PostflightError('EXISTING_TEMPLATE_HASH_MISMATCH')
    text=raw.decode();start=text.index("SOURCE='39363ee");end=text.index('SOURCE_ASSET=',start)
    header="SOURCE="+repr(SOURCE)+"\nCODE=42\nVERSION='0.6.0-rc12.3-playback-focus-clock-review'\nAPK_SHA="+repr(APK_SHA)+"\nZIP_SHA="+repr(ZIP_SHA)+"\nCERT='40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c'\nTAG='REVIEW42_CACHED_ONLY'\n"
    text=text[:start]+header+text[end:]
    text=text.replace('1/4  Signing with your existing local GharTV key…','1/4  Reusing the already-verified signed42; no keystore access…')
    code=text.split("<<'PY'\n",1)[1].rsplit('\nPY\n',1)[0]
    m=types.ModuleType('ghartv_existing_review42');exec(compile(code,'GHARTV_OPEN_REVIEW.command','exec'),m.__dict__)
    m.R['launcher_revision']=REVISION
    return m


def install_hooks(m):
    original_artifact=m.artifact
    def cached(name,expected,tag=None):
        if name in (m.APK_ASSET,m.SOURCE_ASSET):
            path=m.CACHE/expected;raw=owned(path,32*1024*1024)
            if sha(raw)!=expected:raise m.Hold('CACHED42_BYTES_CHANGED_PRESERVED')
            return path
        return original_artifact(name,expected,tag) if tag is not None else original_artifact(name,expected)
    m.artifact=cached
    def existing_signed(unsigned,tools,env):
        p=m.STATE/'verified-review-apks'/SOURCE/(SIGNED+'.apk')
        raw=owned(p,32*1024*1024)
        if sha(raw)!=SIGNED:raise m.Hold('SIGNED42_CHANGED_NO_RESIGN')
        m.apk_identity(p,tools,env,42)
        if m.payload(p)!=m.payload(unsigned):raise m.Hold('SIGNED42_PAYLOAD_CHANGED')
        m.R['signing_mode']='REUSED_VERIFIED_SIGNED42_NO_KEY_READ'
        return p
    m.prepare_signed=existing_signed
    def observed_call(original,args,timeout=15,check=True,env=None,transport=False):
        a=list(map(str,args));is_adb=Path(a[0]).name=='adb'
        if is_adb:
            step=adb_step(a);m.R['android_operation']=step
            # Avoid the streaming transport, without downgrade/clear/uninstall switches.
            if step=='INSTALL_IN_PLACE' and '--no-streaming' not in a:
                a.insert(a.index('install')+1,'--no-streaming')
            print('  Android step: '+step,flush=True)
        try:
            p=original(a,timeout) if transport else original(a,timeout,False,env)
        except (subprocess.TimeoutExpired, m.Hold) as error:
            if is_adb:
                reason='TIMEOUT' if isinstance(error,subprocess.TimeoutExpired) or 'TIMEOUT' in str(error) else 'COMMAND_EXCEPTION'
                m.R['adb_failure']={'step':step,'reason':reason}
                m.atomic(m.RUN/'ADB_FAILURE_PRIVATE.json',m.R['adb_failure'])
                raise m.Hold('ADB_'+step+'_'+reason) from None
            raise
        if is_adb and p.returncode:
            reason=adb_reason(p.stdout,p.stderr)
            detail={'step':step,'reason':reason,'returncode':p.returncode,
                    'stdout':(p.stdout or '')[-8000:],'stderr':(p.stderr or '')[-8000:]}
            m.atomic(m.RUN/'ADB_FAILURE_PRIVATE.json',detail)
            m.R['adb_failure']={k:detail[k] for k in ('step','reason','returncode')}
            print('  '+step+': '+reason+' (exit '+str(p.returncode)+')',flush=True)
        if not transport and check and p.returncode:
            raise m.Hold(('ADB_'+step+'_'+reason) if is_adb else 'COMMAND_FAILED_'+Path(a[0]).name.upper())
        return p
    base_call=m.call
    m.call=lambda a,timeout=15,check=True,env=None:observed_call(base_call,a,timeout,check,env)
    source_module=m.source_module
    def module(z,name):
        result=source_module(z,name)
        if name=='tools/tv_local.py':
            fn=result.call
            result.call=lambda a,timeout=8:observed_call(fn,a,timeout,False,transport=True)
        return result
    m.source_module=module
    android=m.android_review
    def android_review(z,transport,sdk,signed,tools,env):
        # Preserve original identity/certificate/version/update checks; only installation
        # transport and per-operation diagnostics differ. Clock/probe follow readiness.
        outcome=android(z,transport,sdk,signed,tools,env)
        clock=m.source_module(z,'tools/tv-first/review_clock.py')
        report=clock.reconcile(sdk/'platform-tools/adb',m.SERIAL,m.AVD,m.call)
        m.R['emulator_clock']=report;m.atomic(m.RUN/'EMULATOR_CLOCK.json',report)
        print('TV clock: '+report['state'],flush=True)
        if report.get('changed'):
            m.call([sdk/'platform-tools/adb','-s',m.SERIAL,'shell','am','force-stop',m.PACKAGE],10)
            m.R['app_restarted_after_clock_correction']=True
        probe_id='REVIEW42_'+str(int(time.time()))
        r=m.call([sdk/'platform-tools/adb','-s',m.SERIAL,'shell','am','start','-W','-n',m.PACKAGE+'/.MainActivity','--es','ghartv_network_probe',probe_id],30)
        if 'Status: ok' not in r.stdout:raise m.Hold('PROBE_ACTIVITY_NOT_CONFIRMED')
        try:
            network=clock.capture_probe(sdk/'platform-tools/adb',m.call,probe_id)
            m.atomic(m.RUN/'NETWORK_PROBE.json',network)
            m.R['network_probe_status']=network.get('status',network.get('state','UNKNOWN'))
        except Exception as e:
            m.R['optional_network_preflight']='UNCONFIRMED_'+type(e).__name__.upper()
        return outcome
    m.android_review=android_review
    resolver=m.analytics_target
    def analytics_target():
        target=resolver()
        print('\nChecking actual collector export and current Analytics adapter…',flush=True)
        audit=collection_audit(target)
        m.R['analytics_collection_audit']=audit;m.atomic(m.RUN/'ANALYTICS_COLLECTION_CHECK.json',audit)
        print('Collector: '+audit['state'],flush=True)
        if 'export_rows' in audit:
            print('  Export rows: '+str(audit['export_rows'])+'; readable timestamps: '+str(audit['parseable_rows'])+
                  '; readable by old adapter: '+str(audit['legacy_parseable_rows']),flush=True)
        print('Report adapter: '+audit.get('report_adapter_revision',audit.get('report_health','NOT_CHECKED')),flush=True)
        if audit.get('finding'):print('Finding: '+audit['finding'],flush=True)
        print('No telemetry consent changed; no test events sent. Analytics service not restarted.',flush=True)
        return target
    m.analytics_target=analytics_target
    persist=m.persist
    def persist_with_audit(z=None):
        audit=m.R.get('analytics_collection_audit',{})
        if audit:
            m.R['collector_config']='PRESENT' if audit.get('collector_config_verified') else 'NOT_VERIFIED'
            verified=audit.get('state') in ('COLLECTOR_HAS_EVENTS','COLLECTOR_RETURNED_NO_EVENTS')
            m.R['collector_auth']='VERIFIED_BY_SUPPORT_READ' if verified else 'NOT_VERIFIED'
            m.R['analytics']=audit.get('state','NOT_CHECKED')+'; report='+audit.get('report_adapter_revision',audit.get('report_health','NOT_CHECKED'))
        persist(z)
    m.persist=persist_with_audit


def main():
    if sys.argv[1:]!=['--resume-and-check']:raise PostflightError('USE_RESUME_AND_CHECK_ONLY')
    if sys.platform!='darwin':raise PostflightError('RUN_ON_EXISTING_OWNER_MAC')
    source_zip=STATE/'artifact-cache'/ZIP_SHA
    if sha(owned(source_zip,32*1024*1024))!=ZIP_SHA:raise PostflightError('EXACT42_SOURCE_CACHE_REQUIRED')
    m=make_module(source_zip);install_hooks(m)
    print('GharTV42 launcher repair: SAME signed APK; detailed Android operations and read-only collection check.',flush=True)
    return m.main()

if __name__=='__main__':
    try:raise SystemExit(main())
    except PostflightError as e:print(str(e));raise SystemExit(2)
