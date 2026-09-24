"""Build-time repair of the existing entry, not an owner-Mac installer."""
from pathlib import Path
p=Path(__file__).resolve().parents[2]/'GHARTV_OPEN_REVIEW.command'
s=p.read_text()
old="    var app=Application(names[n]);if(!app.running())continue;running.push(names[n]);"
new="    var app;try{app=Application(names[n]);if(!app.running())continue;}catch(uninstalled){continue;}running.push(names[n]);"
if old in s:
    assert s.count(old)==1;s=s.replace(old,new)
else:assert new in s
# Preserve the user's current Node/NVM installation instead of restricting PATH.
s=s.replace("PATH='/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',GH_PROMPT_DISABLED", "PATH=os.environ.get('PATH','')+':/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',GH_PROMPT_DISABLED")
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
print('EXISTING_APKSIG_VERIFIER_AND_OPTIONAL_BROWSER_HANDLING_INTEGRATED')
