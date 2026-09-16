"""Existing GharTV owner console: exact-binary verification and explicitly approved promotion.

No key reading, build, Git checkout mutation, cloud runtime deployment, or unattended update.
Invoked only by the loopback, nonce- and Origin-protected owner route.
"""
from __future__ import annotations
import base64, datetime as dt, fcntl, hashlib, json, os, re, subprocess, sys, tempfile, urllib.request
from pathlib import Path
HOME=Path.home(); STATE=HOME/'Library/Application Support/GharTV/owner-review'
CURRENT=STATE/'current'; RUNTIME=STATE/'runtime-current'; RELEASE=STATE/'release-control'
REPO='AmritSinghGit/ghartv'; PACKAGE='in.ghartv.nova'; AVD='GharTV_Nova_Manual_google_tv_API36'
TAG='v0.6.0-rc7'; CODE=23; VERSION='0.6.0-rc7-network-diagnostics'; ASSET='GharTV-review-current.apk'
CERT='40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c'
class Hold(RuntimeError): pass
def safe(path):
    for p in (path,*path.parents):
        if p.is_symlink(): raise Hold('SYMLINKED_LOCAL_STATE_PRESERVED')
def read_json(path,limit=256000):
    safe(path); s=path.stat()
    if not path.is_file() or s.st_uid!=os.getuid() or s.st_size>limit: raise Hold('LOCAL_FILE_NOT_TRUSTED')
    return json.loads(path.read_text())
def write_json(path,data):
    safe(path); path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    temp=path.with_name(path.name+'.tmp-'+str(os.getpid()))
    with open(temp,'x') as f: json.dump(data,f,indent=2);f.write('\n')
    temp.chmod(0o600);os.replace(temp,path)
def digest(path):
    safe(path);h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(131072),b''):h.update(b)
    return h.hexdigest()
def command(args,timeout=25,input_data=None):
    env={k:v for k,v in os.environ.items() if not k.startswith('GHARTV_SIGNING_') and k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')}
    env.update(GH_PROMPT_DISABLED='1',GH_PAGER='cat')
    try:
        p=subprocess.run([str(x) for x in args],input=input_data,text=True,capture_output=True,timeout=timeout,env=env)
    except subprocess.TimeoutExpired: raise Hold('OPERATION_TIMED_OUT_READBACK_REQUIRED') from None
    except FileNotFoundError: raise Hold('REQUIRED_LOCAL_TOOL_NOT_FOUND') from None
    if p.returncode: raise Hold('OPERATION_FAILED_'+Path(str(args[0])).name.upper().replace('.','_')+'_EXIT_'+str(p.returncode))
    if len(p.stdout)>3*1024*1024: raise Hold('RESPONSE_EXCEEDED_BOUND')
    return p.stdout.strip()
def github(path,method='GET',body=None):
    if not path.startswith('repos/'+REPO+'/'):raise Hold('GITHUB_REPOSITORY_REJECTED')
    args=['gh','api','--method',method,path]
    if body is not None:args+=['--input','-']
    return json.loads(command(args,input_data=json.dumps(body) if body is not None else None))
def identity(expected):
    if not re.fullmatch('[a-f0-9]{40}',expected or ''):raise Hold('RUNTIME_SOURCE_NOT_VERIFIED')
    marker=read_json(RUNTIME/'.ghartv-managed.json');r=read_json(CURRENT/'receipt.json');a=read_json(CURRENT/'review-artifact.json')
    if marker.get('source')!=expected or r.get('review_source')!=expected or a.get('source')!=expected:raise Hold('CANDIDATE_IDENTITY_CHANGED_REFRESH_FIRST')
    if r.get('version_code')!=CODE or r.get('version')!=VERSION:raise Hold('WRONG_REVIEW_VERSION')
    if r.get('status')!='REVIEW_READY' or r.get('emulator')!='RC7_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND':raise Hold('GREEN_INSTALLATION_NOT_READY')
    if not re.fullmatch('[a-f0-9]{64}',r.get('signed_apk_sha256','')) or a.get('sha256')!=r['signed_apk_sha256']:raise Hold('SIGNED_IDENTITY_NOT_VERIFIED')
    return r
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None
def local_health():
    with urllib.request.build_opener(NoRedirect()).open('http://127.0.0.1:8790/api/health',timeout=4) as response:
        return json.loads(response.read(64000))
def safe_blue(value):
    if not isinstance(value,dict) or not isinstance(value.get('versionCode'),int):raise Hold('PUBLIC_MANIFEST_INVALID')
    if not re.fullmatch('[a-f0-9]{40}',value.get('sourceCommit','')) or not re.fullmatch('[a-f0-9]{64}',value.get('sha256','')):raise Hold('PUBLIC_IDENTITY_INVALID')
    if not re.fullmatch(r'https://github\.com/AmritSinghGit/ghartv/releases/download/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.apk',value.get('apkUrl','')):raise Hold('PUBLIC_DOWNLOAD_ORIGIN_REJECTED')
    return {k:value.get(k) for k in ('versionCode','versionName','sourceCommit','sha256','apkUrl','publishedAt')}
def live_blue():
    result=github('repos/'+REPO+'/contents/update/latest.json?ref=main')
    if result.get('encoding')!='base64' or not re.fullmatch('[a-f0-9]{40}',result.get('sha','')):raise Hold('PUBLIC_CONTENTS_INVALID')
    raw=base64.b64decode(result['content']);value=json.loads(raw);safe_blue(value)
    return result['sha'],value

def snapshot(expected):
    result={'ok':True,'schema':'ghartv.release-desk.v1','source':expected,'green':{'state':'NOT_READY'},'blue':{'state':'NOT_CHECKED'},'promotion':{'state':'NOT_REQUESTED'},'development':'SOURCE_ONLY_NOT_AN_INSTALLED_RELEASE'}
    try:
        r=identity(expected)
        result['green']={'state':'INSTALLED_REVIEW_READY','run_id':r['run_id'],'source':expected,'version':r['version'],'version_code':r['version_code'],'sha256':r['signed_apk_sha256'],'web_url':'http://127.0.0.1:8790/','owner_url':'http://127.0.0.1:8790/owner.html'}
    except (Hold,OSError,ValueError) as e:result['green']['reason']=str(e) if isinstance(e,Hold) else 'LOCAL_RECEIPT_NOT_READY'
    try:
        cached=read_json(RELEASE/'public-observation.json')
        result['blue']={'state':'LAST_VERIFIED_PUBLIC','verified_at':cached['verified_at'],**safe_blue(cached['manifest'])}
    except (Hold,OSError,ValueError,KeyError):pass
    try:
        p=read_json(RELEASE/'publication.json')
        if p.get('source')==expected:result['promotion']={k:p.get(k) for k in ('state','source','sha256','code','at','commit','error','release_url') if k in p}
    except (Hold,OSError,ValueError):pass
    return result

def sdk_tools():
    sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(HOME/'Library/Android/sdk')))
    choices=sorted(sdk.glob('build-tools/*/apksigner'),key=lambda p:tuple(map(int,re.findall(r'\d+',p.parent.name))))
    if not choices:raise Hold('EXISTING_ANDROID_TOOLS_NOT_FOUND')
    signer=choices[-1];java=Path('/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java')
    if not java.is_file():java=Path(command(['/usr/libexec/java_home','-v','17']))/'bin/java'
    return sdk/'platform-tools/adb',signer,java

def verify_green(expected,open_tv=False):
    r=identity(expected);apk=CURRENT/ASSET
    if digest(apk)!=r['signed_apk_sha256']:raise Hold('SIGNED_APK_BYTES_CHANGED')
    health=local_health()
    if health.get('commit')!=expected or health.get('version')!=VERSION:raise Hold('WEB_CANDIDATE_DIFFERS')
    adb,signer,java=sdk_tools();matches=[]
    for line in command([adb,'devices']).splitlines():
        p=line.split()
        if len(p)==2 and p[1]=='device' and p[0].startswith('emulator-'):
            if command([adb,'-s',p[0],'emu','avd','name']).splitlines()[0].strip()==AVD:matches.append(p[0])
    if len(matches)!=1:raise Hold('ONE_EXISTING_GHARTV_EMULATOR_REQUIRED')
    serial=matches[0];paths=command([adb,'-s',serial,'shell','pm','path',PACKAGE]).splitlines()
    if len(paths)!=1 or not paths[0].startswith('package:/'):raise Hold('INSTALLED_APK_PATH_NOT_VERIFIED')
    with tempfile.TemporaryDirectory(prefix='.verify-green-',dir=RELEASE) as td:
        installed=Path(td)/'installed.apk';command([adb,'-s',serial,'pull',paths[0][8:],installed],40)
        if digest(installed)!=r['signed_apk_sha256']:raise Hold('EMULATOR_RUNNING_DIFFERENT_APK')
    verified=json.loads(command([java,'-cp',signer.parent/'lib/apksigner.jar',RUNTIME/'tools/GharTVApkVerifier.java',apk],45))
    if verified.get('ok') is not True or verified.get('certificate_sha256')!=[CERT]:raise Hold('ORIGINAL_RELEASE_CERTIFICATE_NOT_VERIFIED')
    badging=command([signer.parent/'aapt','dump','badging',apk])
    if not all(x in badging for x in ("name='"+PACKAGE+"'","versionCode='23'","versionName='"+VERSION+"'")):raise Hold('APK_PACKAGE_VERSION_MISMATCH')
    if open_tv:
        command([adb,'-s',serial,'shell','input','keyevent','KEYCODE_WAKEUP'])
        launched=command([adb,'-s',serial,'shell','am','start','-W','-n',PACKAGE+'/.MainActivity'])
        if 'Status: ok' not in launched:raise Hold('TV_OPEN_NOT_CONFIRMED')
    return r

def verify(expected,open_tv=False):
    verify_green(expected,open_tv)
    blue_error=None
    try:
        _,blue=live_blue();write_json(RELEASE/'public-observation.json',{'verified_at':dt.datetime.now(dt.timezone.utc).isoformat(),'manifest':blue})
    except Exception as e:blue_error=str(e) if isinstance(e,Hold) else 'PUBLIC_CHECK_UNAVAILABLE'
    out=snapshot(expected);out['green']['verified_now']=True;out['green']['tv_open_requested']=open_tv
    if blue_error:out['blue']['check_error']=blue_error
    return out

def validate_approval(body,r):
    if body.get('run_id')!=r['run_id'] or body.get('source')!=r['review_source'] or body.get('sha256')!=r['signed_apk_sha256']:raise Hold('APPROVAL_DOES_NOT_MATCH_CURRENT_GREEN')
    if body.get('confirmation')!='PUBLISH EXACT CODE 23':raise Hold('EXPLICIT_PRODUCTION_APPROVAL_REQUIRED')
    if body.get('reviewed')!={'tv':True,'web':True,'owner':True}:raise Hold('THREE_REVIEW_CONFIRMATIONS_REQUIRED')
    if body.get('scope')!='ANDROID_UPDATE_AND_DOWNLOAD_FEED_ONLY':raise Hold('PUBLICATION_SCOPE_NOT_CONFIRMED')

def publish(expected,body):
    # Verify the owner-selected exact candidate BEFORE any external write.
    r=identity(expected);validate_approval(body,r);verify_green(expected)
    apk=CURRENT/ASSET;h=r['signed_apk_sha256'];blob,blue=live_blue()
    if blue['versionCode']==CODE and blue['sourceCommit']==expected and blue['sha256']==h:
        return finish_publication(expected,h,None,blue,already=True)
    if blue['versionCode']>=CODE:raise Hold('PUBLIC_RELEASE_ALREADY_ADVANCED_NO_OVERWRITE')
    release=github('repos/'+REPO+'/releases/tags/'+TAG)
    if release.get('target_commitish')!=expected or release.get('draft'):raise Hold('RELEASE_TARGET_CHANGED')
    unsigned=[a for a in release.get('assets',[]) if a.get('name')=='GharTV-review-unsigned.apk']
    if len(unsigned)!=1 or unsigned[0].get('digest')!='sha256:'+r['unsigned_apk_sha256']:raise Hold('CLOUD_UNSIGNED_ARTIFACT_CHANGED')
    at=dt.datetime.now(dt.timezone.utc).isoformat()
    decision={'schema':'ghartv.exact-promotion.v1','state':'APPROVED_PREPARING','source':expected,'sha256':h,'code':CODE,'at':at,'run_id':r['run_id'],'scope':body['scope'],'owner_reviewed':body['reviewed'],'previous_public':safe_blue(blue)}
    write_json(RELEASE/'publication.json',decision);write_json(RELEASE/('approval-'+r['run_id']+'.json'),decision)
    found=[a for a in release.get('assets',[]) if a.get('name')==ASSET]
    if found and (len(found)!=1 or found[0].get('digest')!='sha256:'+h):raise Hold('DIFFERENT_SIGNED_RELEASE_ASSET_PRESERVED')
    if not found:command(['gh','release','upload',TAG,apk,'--repo',REPO],65)
    confirmed=github('repos/'+REPO+'/releases/tags/'+TAG)
    found=[a for a in confirmed.get('assets',[]) if a.get('name')==ASSET and a.get('digest')=='sha256:'+h]
    if len(found)!=1:raise Hold('SIGNED_RELEASE_UPLOAD_NOT_VERIFIED')
    with tempfile.TemporaryDirectory(prefix='.published-readback-',dir=RELEASE) as td:
        command(['gh','release','download',TAG,'--repo',REPO,'--pattern',ASSET,'--dir',td],65)
        if digest(Path(td)/ASSET)!=h:raise Hold('PUBLISHED_APK_READBACK_DIFFERENT')
    # Refetch immediately before the atomic one-file update. Do not merge or reset a checkout.
    latest_blob,latest=live_blue()
    if latest_blob!=blob:raise Hold('PUBLIC_FEED_CHANGED_DURING_PREPARATION_NO_OVERWRITE')
    if identity(expected)['signed_apk_sha256']!=h or digest(apk)!=h:raise Hold('GREEN_CHANGED_BEFORE_PUBLICATION')
    feed={'versionCode':CODE,'versionName':VERSION,'apkUrl':f'https://github.com/{REPO}/releases/download/{TAG}/{ASSET}',
          'sha256':h,'sourceCommit':expected,'artifactTag':TAG,'channel':'production','publishedAt':at,
          'notes':'Owner-reviewed exact code23: network diagnostics, manual reports, Punjabi selection and release desk. Update in place; do not uninstall.',
          'ownerDecision':'EXPLICIT_LOCAL_THREE_SURFACE_APPROVAL','distributionMode':'promote-exact-reviewed-signed-apk-no-rebuild',
          'mandatoryUpdateSupported':False,'silentInstallSupported':False,'previousVersionCode':blue['versionCode'],'previousArtifactSha256':blue['sha256']}
    encoded=base64.b64encode((json.dumps(feed,indent=2)+'\n').encode()).decode()
    changed=github('repos/'+REPO+'/contents/update/latest.json','PUT',{'message':'release: promote owner-approved exact code23 APK; no rebuild','branch':'main','sha':blob,'content':encoded})
    commit=changed.get('commit',{}).get('sha')
    _,observed=live_blue()
    if any(observed.get(k)!=feed[k] for k in ('sourceCommit','sha256','versionCode','apkUrl')):raise Hold('PUBLICATION_READBACK_PENDING_CHECK_FEED')
    write_json(RELEASE/'public-observation.json',{'verified_at':at,'manifest':observed})
    return finish_publication(expected,h,commit,observed)

def finish_publication(source,h,commit,feed,already=False):
    state={'state':'PUBLIC_UPDATE_VERIFIED','source':source,'sha256':h,'code':CODE,'at':dt.datetime.now(dt.timezone.utc).isoformat(),'commit':commit,'release_url':f'https://github.com/{REPO}/releases/tag/{TAG}','already_published':already,'physical_tv':'NOT_VERIFIED','web_hosting':'NOT_DEPLOYED_BY_THIS_ACTION','notification':'NEXT_SUCCESSFUL_APP_UPDATE_CHECK_NOT_INSTANT_PUSH'}
    # Latest alias is separate from the authoritative update feed. A failure does not undo a verified feed.
    try:command(['gh','release','edit',TAG,'--repo',REPO,'--prerelease=false','--latest'],20);state['latest_alias']='UPDATED'
    except Hold:state['latest_alias']='PENDING_FEED_ALREADY_VERIFIED'
    write_json(RELEASE/'publication.json',state)
    try:
        vault=HOME/'Documents/Amrit Executive Memory';safe(vault)
        if vault.is_dir():write_json(vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'/('promotion-code23-'+source[:12]+'.json'),state)
    except (Hold,OSError):state['obsidian']='PUBLICATION_NOTE_PENDING'
    try:
        # Same PR, only release identity. No local paths, owner data or support records.
        comment='Owner approved and published exact GharTV code23. Source `'+source+'`, signed APK SHA256 `'+h+'`. No rebuild. Public update feed readback verified. Physical TV install and hosted browser deployment are not established by this publication.'
        command(['gh','pr','comment','1','--repo',REPO,'--body',comment],15)
    except Hold:state['cross_lane_notice']='PENDING';write_json(RELEASE/'publication.json',state)
    return {**snapshot(source),'publication':state}

def main():
    action=sys.argv[1] if len(sys.argv)>1 else '';source=sys.argv[2] if len(sys.argv)>2 else ''
    if action not in ('status','verify','open-tv','publish'):raise Hold('UNKNOWN_RELEASE_ACTION')
    safe(RELEASE);RELEASE.mkdir(parents=True,exist_ok=True,mode=0o700)
    if action=='status':return snapshot(source)
    lockpath=STATE/'owner-run.lock';safe(lockpath)
    with lockpath.open('a') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:raise Hold('REVIEW_PREPARATION_STILL_RUNNING_WAIT_FOR_HANDOFF') from None
        if action in ('verify','open-tv'):return verify(source,action=='open-tv')
        raw=sys.stdin.read(8193)
        if len(raw)>8192:raise Hold('APPROVAL_TOO_LARGE')
        return publish(source,json.loads(raw))
if __name__=='__main__':
    try:print(json.dumps(main()))
    except Exception as e:
        code=str(e) if isinstance(e,Hold) else 'LOCAL_RELEASE_CHECK_FAILED_'+type(e).__name__
        if not re.fullmatch('[A-Z0-9_]+',code):code='LOCAL_RELEASE_CHECK_FAILED'
        print(json.dumps({'ok':False,'error':code,'production_success_not_assumed':True}));sys.exit(1)
