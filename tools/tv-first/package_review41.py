"""CI-only packaging of the existing owner opener. No owner runtime actions here."""
import ast,base64,hashlib,io,json,os,subprocess,sys,tempfile,types,zipfile
from pathlib import Path
root=Path('/tmp/ghartv-candidate');source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
apk=root/'GharTV-code41-review-unsigned.apk';digest=lambda b:hashlib.sha256(b).hexdigest();apk_hash=digest(apk.read_bytes());archive=root/'GHARTV_CODE41_SOURCE.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for raw in subprocess.check_output(['git','ls-files','-z']).split(b'\0'):
        if not raw:continue
        name=raw.decode()
        if Path(name).suffix.lower() not in ('.woff','.woff2','.ttf','.otf'):z.writestr(name,subprocess.check_output(['git','show','HEAD:'+name]))
zip_hash=digest(archive.read_bytes());buf=io.BytesIO()
with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as z:z.writestr(apk.name,apk.read_bytes());z.writestr(archive.name,archive.read_bytes())
bundle=buf.getvalue();old=Path('GHARTV_OPEN_REVIEW.command').read_text();start=old.index("SOURCE='39363ee");end=old.index('SOURCE_ASSET=',start)
header=("SOURCE="+repr(source)+"\nCODE=41\nVERSION='0.6.0-rc12.2-connection-diagnostics-review'\nAPK_SHA="+repr(apk_hash)+"\nZIP_SHA="+repr(zip_hash)+"\nCERT='40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c'\nTAG='OWNER_REVIEW41_BUNDLED_NOT_PUBLIC_RELEASE'\nif sys.argv[1:] not in ([], ['--candidate','41']):\n    print('Compiled Review41 only. Use --candidate 41. Household publication is not part of review.')\n    raise SystemExit(2)\n")
entry=(old[:start]+header+old[end:]).replace('import ast, datetime','import base64, ast, datetime',1)
marker='def artifact(name,sha,tag=TAG):\n'
block='''    if name in (APK_ASSET,SOURCE_ASSET) and tag==TAG:
        expected=APK_SHA if name==APK_ASSET else ZIP_SHA
        if sha!=expected:raise Hold('BUNDLED_ARTIFACT_IDENTITY_MISMATCH')
        mkdir(CACHE);dest=safe(CACHE/sha)
        if dest.exists():
            if not dest.is_file() or digest(dest)!=sha:raise Hold('CACHE_BYTES_CHANGED_PRESERVED')
            return dest
        raw=base64.b64decode(BUNDLE_B64,validate=True)
        if hashlib.sha256(raw).hexdigest()!=BUNDLE_SHA:raise Hold('BUNDLED_REVIEW_CHECKSUM_FAILED')
        with zipfile.ZipFile(io.BytesIO(raw)) as z:data=z.read(name)
        if hashlib.sha256(data).hexdigest()!=sha:raise Hold('BUNDLED_FILE_CHECKSUM_FAILED')
        atomic(dest,data);return dest
'''
assert entry.count(marker)==1
entry=entry.replace(marker,'BUNDLE_SHA='+repr(digest(bundle))+'\nBUNDLE_B64='+repr(base64.b64encode(bundle).decode())+'\n\n'+marker+block)
def rawcode(t):return t.split("<<'PY'\n",1)[1].rsplit('\nPY\n',1)[0]
def funcs(t):return {n.name:ast.dump(n,include_attributes=False) for n in ast.parse(rawcode(t)).body if isinstance(n,ast.FunctionDef)}
a,b=funcs(old),funcs(entry);assert a.keys()==b.keys();assert all(a[k]==b[k] for k in a if k!='artifact')
command=root/'GHARTV_OPEN_REVIEW.command';command.write_text(entry);subprocess.run(['bash','-n',command],check=True)
for args in ([],['--candidate','41']):
    sys.argv=['entry']+args;m=types.ModuleType('entry_check');exec(compile(rawcode(entry),'entry','exec'),m.__dict__);assert m.CODE==41 and m.SOURCE==source
    with tempfile.TemporaryDirectory() as temp:
        m.CACHE=Path(temp).resolve();assert m.digest(m.artifact(m.APK_ASSET,m.APK_SHA))==apk_hash;assert m.digest(m.artifact(m.SOURCE_ASSET,m.ZIP_SHA))==zip_hash
for args in (['--candidate','40'],['--candidate','38'],['--publish']):
    sys.argv=['entry']+args
    try:exec(compile(rawcode(entry),'entry','exec'),{'__name__':'not_main'});raise AssertionError('Old/publication mode accepted')
    except SystemExit as e:assert e.code==2
manifest={'schema':'ghartv.owner-review41.v1','source':source,'version_code':41,'version':'0.6.0-rc12.2-connection-diagnostics-review','apk':apk.name,'apk_bytes':apk.stat().st_size,'apk_sha256':apk_hash,'source_archive_sha256':zip_hash,'entry_sha256':digest(command.read_bytes()),'entry_bytes':command.stat().st_size,'precompiled':True,'original_local_signing_required':True,'public_release':'HELD','public_feed_changed':False}
(root/'CANDIDATE.json').write_text(json.dumps(manifest,indent=2)+'\n')
validation={'schema':'ghartv.review41-validation.v1','source':source,'actual_android_tests_passed':88,'retained_review40_tests':70,'tls_and_telemetry_tests':18,'all_failed_certificates_cancelled':True,'subresource_scope_preserves_document':True,'complete_collector_ack_required':True,'opt_out_preserved':True,'test_content':'OWNED_LOCAL_PAGES_VIDEO_AND_INTERCEPTED_COLLECTOR_RESPONSES','production_events_injected':False,'authenticated_collector_export_read':False,'owner_mac_or_tv_run':False,'live_provider_playback':False,'analytics_runtime_deployed':False,'same_original_signer_and_window_functions':True,'public38_unchanged':True,'workflow_run':os.environ['GITHUB_RUN_ID']}
(root/'VALIDATION.json').write_text(json.dumps(validation,indent=2)+'\n')
(root/'REVIEW41.md').write_bytes(Path('REVIEW41.md').read_bytes())
(root/'SHA256SUMS').write_text(''.join(digest(p.read_bytes())+'  '+p.name+'\n' for p in sorted(root.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
print(json.dumps(manifest));print(json.dumps(validation))
