"""Exact-reviewed-byte mode for the existing release_control owner workflow.

Uses the same release-control state and owner-run lock. This module does not
start a service, replace a runtime, build, sign, install, or control a TV.
"""
from __future__ import annotations
import base64, datetime as dt, fcntl, hashlib, json, os, re, subprocess, sys, tempfile
from pathlib import Path
from release_control import (HOME, STATE, RELEASE, REPO, PACKAGE, CERT, Hold,
                             safe, read_json, write_json, digest, command, sdk_tools, safe_blue)

def github(path, method='GET', body=None):
    if not path.startswith('repos/'+REPO+'/'): raise Hold('GITHUB_REPOSITORY_REJECTED')
    args=['gh','api','--hostname','github.com','--method',method,path]
    if body is not None: args+=['--input','-']
    return json.loads(command(args,input_data=json.dumps(body) if body is not None else None))

def live_blue():
    obj=github('repos/'+REPO+'/contents/update/latest.json?ref=main')
    if obj.get('encoding')!='base64' or not re.fullmatch('[a-f0-9]{40}',obj.get('sha','')):
        raise Hold('PUBLIC_CONTENTS_INVALID')
    feed=json.loads(base64.b64decode(obj['content']));safe_blue(feed)
    return obj['sha'],feed

# Exact bytes approved in the owner chat on 2026-09-26. This is NOT a new app build.
FAMILY = {
    'source': 'b5bc564dc83db4b157c2325c9df80ffa0b80d934',
    'code': 38,
    'version': '0.6.0-rc11.1-focus-filter-review',
    'signed': '439df956cb8c1a064291fb10db5b7566aaee26772213f7f6aeb5a7a7174aa7e5',
    'unsigned': '48109b8de7920ce5270e554a78eec4e48ad61596f603bae0c3aa9b40e4e6c127',
    'source_zip': 'a6bc05c22b72de4d95737f9e6dadfe11ab22be09fadfad9597916158b016f5c1',
    'verifier': '9444cac76dfba1b7ffea1b3ac93cb562d03ce8d9d95e4081ab43e418b64deb48',
    'run': 'GHARTV-CYAN-38-20260926T160406Z-94907',
    'tag': 'v0.6.0-rc11.1-focus-filter-review',
    'asset': 'GharTV-code38-household-update.apk',
}

def family_approval():
    """Read current owner authority; never revive an old approval after a hold."""
    obj = github('repos/'+REPO+'/contents/RELEASE_HOLD.json?ref=main')
    if obj.get('encoding') != 'base64': raise Hold('OWNER_APPROVAL_NOT_READABLE')
    policy = json.loads(base64.b64decode(obj['content']))
    selected = policy.get('approved_candidate', {})
    expected = {'version_code': FAMILY['code'], 'version_name': FAMILY['version'],
                'source': FAMILY['source'], 'signed_apk_sha256': FAMILY['signed'],
                'unsigned_apk_sha256': FAMILY['unsigned'], 'certificate_sha256': CERT,
                'owner_run': FAMILY['run'], 'approval_scope': 'ANDROID_UPDATE_AND_DOWNLOAD_FEED_ONLY'}
    if policy.get('owner_decision') != 'EXACT_CODE38_HOUSEHOLD_UPDATE_APPROVED_OTHER_ROLLOUT_HELD':
        raise Hold('OWNER_RELEASE_HOLD_OR_APPROVAL_CHANGED')
    if any(selected.get(k) != v for k,v in expected.items()):
        raise Hold('APPROVAL_DOES_NOT_MATCH_EXACT_REVIEWED38')
    return obj['sha']

def family_local_bytes():
    """Use the already-signed cache and historical successful run, without ADB."""
    receipt = read_json(STATE/'runs'/FAMILY['run']/'receipt.json')
    expected = {'run_id': FAMILY['run'], 'review_source': FAMILY['source'],
                'version_code': FAMILY['code'], 'version': FAMILY['version'],
                'signed_apk_sha256': FAMILY['signed'], 'status': 'REVIEW_READY'}
    if any(receipt.get(k) != v for k,v in expected.items()):
        raise Hold('EXACT_REVIEWED38_LOCAL_RECEIPT_NOT_FOUND')
    apk = STATE/'verified-review-apks'/FAMILY['source']/(FAMILY['signed']+'.apk')
    safe(apk)
    if not apk.is_file() or apk.stat().st_uid != os.getuid() or apk.stat().st_size > 32*1024*1024:
        raise Hold('APPROVED_SIGNED38_FILE_MISSING_OR_UNTRUSTED')
    if digest(apk) != FAMILY['signed']: raise Hold('APPROVED_SIGNED38_BYTES_DIFFER_NO_PUBLICATION')
    return apk

def family_verify_apk(apk,work):
    """Verify only. Never load a keystore, re-sign, build, install, or open a window."""
    import zipfile
    source_zip = STATE/'artifact-cache'/FAMILY['source_zip']
    if not source_zip.is_file() or digest(source_zip) != FAMILY['source_zip']:
        raise Hold('EXISTING_VERIFIED_SOURCE_CACHE_REQUIRED')
    with zipfile.ZipFile(source_zip) as archive:
        info = archive.getinfo('tools/GharTVApkVerifier.java')
        if info.file_size > 16384: raise Hold('VERIFIER_SOURCE_BOUNDS')
        raw = archive.read(info)
    if hashlib.sha256(raw).hexdigest() != FAMILY['verifier']:
        raise Hold('ORIGINAL_VERIFIER_SOURCE_DIFFERS')
    verifier = work/'GharTVApkVerifier.java';verifier.write_bytes(raw)
    _,signer,java = sdk_tools()
    result = json.loads(command([java,'-Xmx256m','-cp',signer.parent/'lib/apksigner.jar',verifier,apk],60))
    if result.get('ok') is not True or result.get('certificate_sha256') != [CERT]:
        raise Hold('ORIGINAL_RELEASE_CERTIFICATE_NOT_VERIFIED')
    row = next((line for line in command([signer.parent/'aapt','dump','badging',apk],30).splitlines()
                if line.startswith('package:')), '')
    fields = dict(re.findall(r"(\w+)='([^']*)'",row))
    if (fields.get('name'),fields.get('versionCode'),fields.get('versionName')) != (PACKAGE,str(FAMILY['code']),FAMILY['version']):
        raise Hold('APPROVED38_PACKAGE_OR_VERSION_MISMATCH')

def family_optional(path):
    """Only a genuine 404 means absent; do not turn auth/network failure into create."""
    env = {k:v for k,v in os.environ.items() if not k.startswith('GHARTV_SIGNING_')}
    env.update(GH_PROMPT_DISABLED='1',GH_PAGER='cat')
    try:
        p = subprocess.run(['gh','api','--hostname','github.com',path],stdin=subprocess.DEVNULL,
                           capture_output=True,text=True,timeout=25,env=env)
    except subprocess.TimeoutExpired: raise Hold('GITHUB_READ_TIMEOUT_NO_CREATE') from None
    if p.returncode:
        if '(HTTP 404)' in p.stderr: return None
        raise Hold('GITHUB_READ_FAILED_NO_CREATE')
    if len(p.stdout)>3*1024*1024: raise Hold('GITHUB_RESPONSE_TOO_LARGE')
    return json.loads(p.stdout)

def family_check_tag(required=False):
    obj = family_optional('repos/'+REPO+'/git/ref/tags/'+FAMILY['tag'])
    if obj is None:
        if required: raise Hold('RELEASE_TAG_NOT_VERIFIED')
        return
    pointer = obj.get('object',{})
    for _ in range(3):
        if pointer.get('type') != 'tag': break
        sha = pointer.get('sha','')
        if not re.fullmatch('[a-f0-9]{40}',sha): raise Hold('RELEASE_TAG_INVALID')
        pointer = github('repos/'+REPO+'/git/tags/'+sha).get('object',{})
    if pointer.get('type') != 'commit' or pointer.get('sha') != FAMILY['source']:
        raise Hold('EXISTING_RELEASE_TAG_POINTS_ELSEWHERE_PRESERVED')

def family_check_release(release,allow_draft=True):
    if release.get('tag_name') != FAMILY['tag'] or release.get('target_commitish') != FAMILY['source']:
        raise Hold('EXISTING_RELEASE_TARGET_CHANGED_PRESERVED')
    if not allow_draft and release.get('draft'): raise Hold('RELEASE_NOT_PUBLISHED')
    if not isinstance(release.get('id'),int): raise Hold('RELEASE_ID_INVALID')
    if len(release.get('assets',[])) >= 100: raise Hold('RELEASE_ASSET_LIST_REQUIRES_REVIEW')

def family_asset(release,required=False):
    found = [a for a in release.get('assets',[]) if a.get('name') == FAMILY['asset']]
    if not found:
        if required: raise Hold('SIGNED_ASSET_NOT_VERIFIED')
        return None
    if len(found) != 1 or found[0].get('digest') != 'sha256:'+FAMILY['signed'] or found[0].get('state') != 'uploaded':
        raise Hold('DIFFERENT_OR_INCOMPLETE_RELEASE_ASSET_PRESERVED')
    expected = 'https://github.com/'+REPO+'/releases/download/'+FAMILY['tag']+'/'+FAMILY['asset']
    if found[0].get('browser_download_url') != expected: raise Hold('SIGNED_ASSET_URL_MISMATCH')
    return found[0]

def family_public_download(url,dest):
    # Unauthenticated readback: prove that Dad, not just the authenticated owner, can download it.
    allowed = 'https://github.com/'+REPO+'/releases/download/'+FAMILY['tag']+'/'+FAMILY['asset']
    if url != allowed: raise Hold('PUBLIC_DOWNLOAD_TARGET_REJECTED')
    command(['/usr/bin/curl','--proto','=https','--proto-redir','=https','-fLsS',
             '--connect-timeout','15','--max-time','150','--max-filesize','33554432',url,'-o',dest],165)
    if digest(dest) != FAMILY['signed']: raise Hold('PUBLIC_SIGNED_APK_BYTES_DIFFER')

def family_feed_relation(feed):
    same = all(feed.get(k) == v for k,v in {
        'versionCode':FAMILY['code'],'versionName':FAMILY['version'],'sha256':FAMILY['signed'],
        'sourceCommit':FAMILY['source'],'apkUrl':'https://github.com/'+REPO+'/releases/download/'+FAMILY['tag']+'/'+FAMILY['asset']
    }.items())
    if not same and feed['versionCode'] >= FAMILY['code']: raise Hold('PUBLIC_FEED_ALREADY_ADVANCED_NO_OVERWRITE')
    return same

def family_finish(feed,commit,already=False):
    state = {'schema':'ghartv.exact-promotion.v2','state':'PUBLIC_UPDATE_FEED_VERIFIED',
             'source':FAMILY['source'],'sha256':FAMILY['signed'],'code':FAMILY['code'],
             'version':FAMILY['version'],'commit':commit,'at':dt.datetime.now(dt.timezone.utc).isoformat(),
             'already_published':already,'apk_url':feed['apkUrl'],'new_build':False,'new_signing':False,
             'physical_tv_installation':'NOT_VERIFIED','notification':'NEXT_APP_UPDATE_CHECK_NOT_SILENT_PUSH',
             'broad_marketing':'HELD','public_raw_feed':'PROPAGATION_NOT_CHECKED'}
    # The API is authoritative; do not call the cached raw endpoint current without readback.
    try:
        url='https://raw.githubusercontent.com/'+REPO+'/main/update/latest.json'
        raw=json.loads(command(['/usr/bin/curl','--proto','=https','--proto-redir','=https','-fLsS','--connect-timeout','10','--max-time','20',url],25))
        if all(raw.get(k)==feed.get(k) for k in ('versionCode','sha256','sourceCommit','apkUrl')):
            state['public_raw_feed']='VERIFIED'
        else:state['public_raw_feed']='PROPAGATION_PENDING'
    except Exception:state['public_raw_feed']='PROPAGATION_PENDING'
    previous=None
    try:previous=read_json(RELEASE/'publication.json')
    except (OSError,ValueError,Hold):pass
    already_noted=bool(previous and previous.get('sha256')==FAMILY['signed'] and previous.get('github_notice')=='POSTED')
    write_json(RELEASE/'publication.json',state)
    vault=HOME/'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
    try:
        safe(vault)
        if vault.is_dir():
            note=vault/('GharTV - Publication code38.json');write_json(note,state)
            state['obsidian']='WRITTEN_AND_READBACK_VERIFIED' if read_json(note)==state else 'READBACK_FAILED'
        else:state['obsidian']='EXISTING_VAULT_NOT_FOUND'
    except (OSError,ValueError,Hold):state['obsidian']='PENDING'
    state['github_notice']='POSTED' if already_noted else 'PENDING'
    if not already_noted:
        try:
            comment=('Exact owner-reviewed GharTV code38 has been published to the existing household update feed. '
                     'Source `'+FAMILY['source']+'`, signed APK SHA256 `'+FAMILY['signed']+'`. '
                     'Public APK bytes and feed readback verified; no rebuild, re-signing, emulator action, data clear or public marketing. '
                     'Dad still needs to accept the Android update; his installation is not verified. '
                     'Raw feed propagation: '+state['public_raw_feed']+'.')
            command(['gh','pr','comment','1','--repo','github.com/'+REPO,'--body',comment],25)
            state['github_notice']='POSTED'
        except Hold:pass
    write_json(RELEASE/'publication.json',state)
    print('Published exact Review38. Dad can use Check for GharTV update. No TV installation was performed here.',file=sys.stderr)
    return {'ok':True,'publication':state}

def publish_reviewed38():
    """Explicit approved-byte publication on the owner Mac; no runtime/start/sign actions."""
    if sys.platform != 'darwin': raise Hold('RUN_ON_OWNER_MAC_WITH_EXISTING_SIGNED38')
    if not STATE.is_dir(): raise Hold('EXISTING_GHARTV_STATE_REQUIRED')
    safe(RELEASE);RELEASE.mkdir(parents=True,exist_ok=True,mode=0o700)
    lockpath=STATE/'owner-run.lock';safe(lockpath)
    with lockpath.open('a') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:raise Hold('EXISTING_GHARTV_ACTION_RUNNING_NO_DUPLICATE') from None
        print('1/4 Verifying the approved signed38 already on this Mac; no signing or installation.',file=sys.stderr)
        apk=family_local_bytes();approval=family_approval();blob,previous=live_blue()
        same=family_feed_relation(previous)
        with tempfile.TemporaryDirectory(prefix='.publish-reviewed38-',dir=RELEASE) as temp:
            work=Path(temp);family_verify_apk(apk,work)
            family_check_tag()
            path='repos/'+REPO+'/releases/tags/'+FAMILY['tag']
            release=family_optional(path)
            if same:
                if release is None: raise Hold('FEED_EXISTS_BUT_RELEASE_NOT_FOUND')
                family_check_release(release,False);asset=family_asset(release,True);family_check_tag(True)
                family_public_download(asset['browser_download_url'],work/'public-readback.apk')
                return family_finish(previous,None,True)
            print('2/4 Uploading those exact bytes to the existing repository release.',file=sys.stderr)
            notes=('Household review update: exact owner-reviewed GharTV code38.\n\n'
                   'Channel filters, guide ordering, Discover focus and shared search. '
                   'FlixMomo supplies third-party film suggestions and its player; JioTV supplies authenticated live-TV access. '
                   'GharTV is independent and does not own their content. Some provider controls still require additional interaction.\n\n'
                   'Update the existing app without uninstalling. This is an experimental household build, not a broad commercial launch. '
                   'No rebuild or new signing identity.\n\nSource: '+FAMILY['source']+'\nSigned SHA256: '+FAMILY['signed']+'\n')
            if release is None:
                if family_approval()!=approval:raise Hold('OWNER_APPROVAL_CHANGED_BEFORE_RELEASE_CREATE')
                release=github('repos/'+REPO+'/releases','POST',{'tag_name':FAMILY['tag'],
                    'target_commitish':FAMILY['source'],'name':'GharTV code38 - household review update',
                    'body':notes,'draft':True,'prerelease':True,'make_latest':'false'})
            family_check_release(release)
            upload=work/FAMILY['asset'];upload.write_bytes(apk.read_bytes())
            if digest(upload)!=FAMILY['signed']:raise Hold('APPROVED_APK_CHANGED_DURING_COPY')
            if family_asset(release) is None:
                if not release.get('draft'):raise Hold('PUBLISHED_RELEASE_MISSING_APPROVED_ASSET_PRESERVED')
                command(['gh','release','upload',FAMILY['tag'],upload,'--repo','github.com/'+REPO],150)
            release=github(path);family_check_release(release);asset=family_asset(release,True)
            if family_approval()!=approval:raise Hold('OWNER_APPROVAL_CHANGED_BEFORE_RELEASE_PUBLISH')
            latest_blob,latest=live_blue()
            if latest_blob!=blob:raise Hold('PUBLIC_FEED_CHANGED_DURING_UPLOAD_NO_OVERWRITE')
            if release.get('draft'):
                release=github('repos/'+REPO+'/releases/'+str(release['id']),'PATCH',
                               {'draft':False,'prerelease':True,'make_latest':'false'})
            family_check_release(release,False);family_check_tag(True)
            print('3/4 Downloading the public APK without credentials and checking its checksum.',file=sys.stderr)
            family_public_download(asset['browser_download_url'],work/'public-readback.apk')
            # Public assets can safely precede advertisement. Never advertise nonexistent/wrong bytes.
            if family_approval()!=approval:raise Hold('OWNER_APPROVAL_CHANGED_BEFORE_FEED_UPDATE')
            current_blob,current=live_blue()
            if current_blob!=blob:raise Hold('PUBLIC_FEED_CHANGED_DURING_PREPARATION_NO_OVERWRITE')
            if digest(apk)!=FAMILY['signed']:raise Hold('APPROVED_SIGNED_CACHE_CHANGED')
            feed={'versionCode':FAMILY['code'],'versionName':FAMILY['version'],'apkUrl':asset['browser_download_url'],
                  'sha256':FAMILY['signed'],'sourceCommit':FAMILY['source'],'artifactTag':FAMILY['tag'],
                  'channel':'production','publishedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
                  'ownerDecision':'APPROVED_EXACT_CODE38_HOUSEHOLD_REVIEW','ownerRun':FAMILY['run'],
                  'distributionMode':'exact-reviewed-signed-apk-no-rebuild',
                  'notes':'Household review update: channel filters, guide ordering, Discover navigation and shared search. Film suggestions and playback use FlixMomo; some provider controls remain under review. Update in place; do not uninstall.',
                  'mandatoryUpdateSupported':False,'silentInstallSupported':False,
                  'previousVersionCode':previous['versionCode'],'previousArtifactSha256':previous['sha256']}
            print('4/4 Advancing the shared household update feed, then reading it back.',file=sys.stderr)
            change=github('repos/'+REPO+'/contents/update/latest.json','PUT',{
                'branch':'main','sha':blob,'message':'release: advertise exact owner-approved signed38 household update; no rebuild',
                'content':base64.b64encode((json.dumps(feed,indent=2)+'\n').encode()).decode()})
            _,readback=live_blue()
            if readback!=feed:raise Hold('PUBLICATION_READBACK_PENDING_NO_SUCCESS_CLAIM')
            write_json(RELEASE/'public-observation.json',{'verified_at':feed['publishedAt'],'manifest':readback})
            return family_finish(readback,change.get('commit',{}).get('sha'))


if __name__=='__main__':
    try:
        if sys.argv[1:]!=['publish-reviewed38']: raise Hold('USE_PUBLISH_REVIEWED38_ONLY')
        print(json.dumps(publish_reviewed38(),indent=2))
    except Exception as error:
        code=str(error) if isinstance(error,Hold) else 'RELEASE_CHECK_FAILED_'+type(error).__name__.upper()
        if not re.fullmatch('[A-Z0-9_]+',code):code='RELEASE_CHECK_FAILED'
        print(json.dumps({'ok':False,'error':code,'feed_success_not_assumed':True,
                          'next_step':'Read the current release/feed before retrying. No key or app reinstall is needed.'}))
        raise SystemExit(1)
