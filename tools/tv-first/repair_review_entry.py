"""Build-time repair of the existing entry, not an owner-Mac installer."""
from pathlib import Path
p=Path(__file__).resolve().parents[2]/'GHARTV_OPEN_REVIEW.command'
s=p.read_text()
old="    var app=Application(names[n]);if(!app.running())continue;running.push(names[n]);"
new="    var app;try{app=Application(names[n]);if(!app.running())continue;}catch(uninstalled){continue;}running.push(names[n]);"
if old in s:
    assert s.count(old)==1;s=s.replace(old,new)
else:assert new in s
s=s.replace("PATH='/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',GH_PROMPT_DISABLED", "PATH=os.environ.get('PATH','')+':/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',GH_PROMPT_DISABLED")
# Application('missing browser') can display a choose-application dialog rather
# than throwing promptly. Determine installed paths before resolving app names.
s=s.replace("var targets=JSON.parse(argv[0]), names=['Safari','Brave Browser','Google Chrome'];", "var targets=JSON.parse(argv[0]), names=JSON.parse(argv[1] || '[\"Safari\"]');")
s=s.replace("  for(var n=0;n<names.length;n++){", "  for(var n=0;n<names.length;n++){\n    console.error('GHARTV_TAB_STAGE_INSPECT_'+names[n]);")
s=s.replace("  var chosen=running.indexOf('Safari')", "  console.error('GHARTV_TAB_STAGE_INVENTORY_COMPLETE');\n  var chosen=running.indexOf('Safari')")
s=s.replace("    if(!app.running()){app.launch();delay(.5);}","    console.error('GHARTV_TAB_STAGE_CREATE_'+target.id);\n    if(!app.running()){app.launch();delay(1);}")
s=s.replace("  return JSON.stringify({status:'BROWSER_TABS_RECONCILED',results:result});", "  console.error('GHARTV_TAB_STAGE_DONE');\n  return JSON.stringify({status:'BROWSER_TABS_RECONCILED',results:result});")
s=s.replace("    p=call(['/usr/bin/osascript','-l','JavaScript',script,json.dumps(targets)],75,False)","""    names=['Safari']
    for name in ('Brave Browser','Google Chrome'):
        if any((base/(name+'.app')).is_dir() for base in (Path('/Applications'),HOME/'Applications')):names.append(name)
    try:p=call(['/usr/bin/osascript','-l','JavaScript',script,json.dumps(targets),json.dumps(names)],40,False)
    except Hold:
        return {'status':'BROWSER_AUTOMATION_TIMED_OUT_NO_FALLBACK_TABS','results':[]}
    stages=[line for line in p.stderr.splitlines() if line.startswith('GHARTV_TAB_STAGE_')]
    atomic(RUN/'browser-stages.json',{'stages':stages,'exit_code':p.returncode})""")
a=s.index('def apk_identity(');b=s.index('\ndef prepare_signed(',a)
s=s[:a]+'''def apk_identity(p,tools,env,expected_code=None):
    archive=artifact('GHARTV_CODE33_SOURCE.zip',ZIP_SHA)
    with zipfile.ZipFile(archive) as z:verifier_data=z.read('tools/GharTVApkVerifier.java')
    verifier=RUN/'GharTVApkVerifier.java'
    if verifier.is_file():
        safe(verifier)
        if verifier.read_bytes()!=verifier_data:raise Hold('EXISTING_APK_VERIFIER_CHANGED')
    else:atomic(verifier,verifier_data)
    java=Path(env['JAVA_HOME'])/'bin/java'
    checked=json.loads(call([java,'-Xmx256m','-cp',tools/'lib/apksigner.jar',verifier,p],60,env=env).stdout)
    if checked.get('ok') is not True or checked.get('certificate_sha256')!=[CERT]:raise Hold('ORIGINAL_CERTIFICATE_MISMATCH_NO_INSTALL')
    badging=call([tools/'aapt','dump','badging',p],30,env=env).stdout
    row=next((line for line in badging.splitlines() if line.startswith('package:')), '')
    match=re.search(r"name='([^']+)'\\s+versionCode='(\\d+)'",row)
    if not match or match[1]!=PACKAGE:raise Hold('APK_PACKAGE_MISMATCH')
    code=int(match[2])
    if expected_code is not None and code!=expected_code:raise Hold('APK_VERSION_MISMATCH')
    return code

'''+s[b:]
compile(s.split("<<'PY'\n",1)[1].rsplit('\nPY\n',1)[0],str(p),'exec')
p.write_text(s)
print('EXISTING_APKSIG_VERIFIER_AND_INSTALLED_BROWSER_HANDLING_INTEGRATED')
