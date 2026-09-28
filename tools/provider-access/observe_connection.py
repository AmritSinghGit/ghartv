"""Read-only fixed-host TLS/HTTP observation. No account, search, media or ingest requests."""
import concurrent.futures, datetime, json, ssl, urllib.request, urllib.error, sys
from pathlib import Path
TARGETS=[('flixmomo_app','https://flixmomo.app/'),('flixmomo_st','https://flixmomo.st/'),('flixmomo_bet','https://flixmomo.bet/'),('collector_health','https://ghartv-telemetry.ghartv-47d9a0.workers.dev/health')]
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def check(target):
    name,url=target;row={'service':name,'tls':'NOT_CONFIRMED','http':None}
    opener=urllib.request.build_opener(NoRedirect(),urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    req=urllib.request.Request(url,method='GET' if name=='collector_health' else 'HEAD',headers={'User-Agent':'GharTV-Connection-Observation/41'})
    try:
        try:res=opener.open(req,timeout=8)
        except urllib.error.HTTPError as error:res=error
        with res:
            row.update(tls='VERIFIED_BY_CLOUD_SYSTEM_TRUST',http=res.code,server_date=res.headers.get('Date'))
            if name=='collector_health' and res.code==200:
                data=json.loads(res.read(8192));row['health']={k:data.get(k) for k in ('ok','service','schema','retention_days')}
    except Exception as error:
        reason=getattr(error,'reason',error);row['error_type']=type(reason).__name__
        if isinstance(reason,ssl.SSLCertVerificationError):row.update(tls='CERTIFICATE_REJECTED',verify_code=reason.verify_code)
    return row

if __name__=='__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:rows=list(pool.map(check,TARGETS))
    out={'schema':'ghartv.connection-observation.v1','observed_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'location':'GITHUB_CLOUD_NOT_OWNER_TV','checks':rows,'admin_or_ingest_requests':False,'provider_playback':False,'redirects_followed':False}
    Path(sys.argv[1]).write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out))
