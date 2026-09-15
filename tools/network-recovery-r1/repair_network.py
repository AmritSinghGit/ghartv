"""One delivery repair; Android and companion bytes are intentionally unchanged."""
from pathlib import Path
import hashlib, json, sys

ORIGINAL = 'f9d11248be07981968cdc0b0e5808cc05daa8d1faccca66cb80b2178129adab2'
M = {'schema':'ghartv.review-manifest.v2','source_sha':'d2f364982ce2972d6a6c75588f206ef098edd65b','branch':'codex/ghartv-remove-auto-preview','pr':1,'version_name':'0.6.0-rc4-owner-convergence','version_code':20,'unsigned_sha256':'a594bc6ddc5d5448c20ac2850c0a3aed432954381c9ce9e840bb8fb56ff8aa5f','companion_sha256':'40b77d93ba163674c34130c48f093482ae442da3d4e7787598ca6a3edfde2a3f','production_unchanged':True,'owner_signed_apk_sha256':None,'owner_mac_run':'NOT_EXECUTED','ai_super_resolution':'NOT_IMPLEMENTED'}
MANIFEST = json.dumps(M,indent=2)+'\n'
assert hashlib.sha256(MANIFEST.encode()).hexdigest() == '52ec393fb49268f368eb2ce44d209aa433ceac3f4ed18ad5eb135248ac7265d7'

NETWORK = r'''
# Only immutable, known public artifacts can enter the local distribution cache.
ARTIFACTS={
 'review-manifest.json': MANIFEST_SHA,
 'GharTV-review-companion.zip':'40b77d93ba163674c34130c48f093482ae442da3d4e7787598ca6a3edfde2a3f',
 'GharTV-review-unsigned.apk':UNSIGNED,
 'GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk':PROD_HASH,
}
NETWORK_RECOVERY='CYAN6-NETWORK-R1'
CACHE=STATE/'artifact-cache'


def network_record(label,url,attempt,p=None,outcome='NOT_RUN',elapsed=0):
 from urllib.parse import urlsplit
 original=urlsplit(url)
 # Never retain redirected URLs, proxy details, raw stderr or response headers.
 item={'artifact':label,'host':original.hostname,'attempt':attempt,'result':outcome,
       'elapsed_seconds':round(elapsed,3),'curl_exit':p.returncode if p else None}
 if p is not None:
  values=p.stdout.strip().split('|')
  for key,value in zip(('http_status','dns_seconds','tcp_seconds','tls_seconds','first_byte_seconds','total_seconds','bytes_received','redirects'),values):
   try:item[key]=float(value) if '.' in value else int(value)
   except ValueError:pass
 r['last_download']=item
 write(RUN/'network-last.json',json.dumps(item,indent=2)+'\n')


def download(url,path,attempts=2,max_seconds=90):
 from urllib.parse import urlsplit
 safe(path)
 parsed=urlsplit(url)
 if parsed.scheme!='https' or parsed.username or parsed.password:
  raise Stop('PUBLIC_DOWNLOAD_URL_INVALID')
 allowed=('github.com','raw.githubusercontent.com')
 if parsed.hostname not in allowed or not parsed.path.startswith(('/AmritSinghGit/ghartv/','/repos/AmritSinghGit/ghartv/')):
  raise Stop('PUBLIC_DOWNLOAD_ORIGIN_REFUSED')
 name=parsed.path.rsplit('/',1)[-1]
 expected=ARTIFACTS.get(name) if parsed.hostname=='github.com' and '/releases/download/' in parsed.path else None
 r['network_recovery']=NETWORK_RECOVERY;r['download_artifact']=name;r['download_host']=parsed.hostname
 if expected:
  mkdir(CACHE)
  cached=CACHE/expected
  for local in (SELF.parent/'assets'/name,cached):
   if not local.is_file() or local.is_symlink():continue
   safe(local)
   if digest(local)!=expected:
    r['invalid_cached_artifacts']=r.get('invalid_cached_artifacts',0)+1
    continue
   if local!=path:shutil.copyfile(local,path)
   network_record(name,url,0,outcome='VERIFIED_LOCAL_BYTES')
   return
  if name=='review-manifest.json':
   raw=EMBEDDED_MANIFEST.encode('utf-8')
   if hashlib.sha256(raw).hexdigest()!=expected:raise Stop('EMBEDDED_MANIFEST_MISMATCH')
   write(path,EMBEDDED_MANIFEST);write(cached,EMBEDDED_MANIFEST)
   network_record(name,url,0,outcome='VERIFIED_EMBEDDED_MANIFEST')
   return
 if args.bundled_review:
  network_record(name,url,0,outcome='BUNDLED_ARTIFACT_MISSING')
  raise Stop('BUNDLED_ARTIFACT_MISSING: '+name+'; use the complete recovery ZIP; no network fallback or checksum bypass')
 attempts=max(1,min(2,attempts));max_seconds=max(1,min(90,max_seconds))
 for attempt in range(1,attempts+1):
  temp=path.with_name(path.name+'.download-'+str(os.getpid()))
  safe(temp)
  if temp.exists():temp.unlink()
  print('Download '+name+' from '+str(parsed.hostname)+' · attempt '+str(attempt)+'/'+str(attempts),flush=True)
  command=['curl','--proto','=https','--proto-redir','=https','--max-redirs','5','-fLsS',
    '--connect-timeout',str(min(20,max_seconds)),'--max-time',str(max_seconds),'--max-filesize','52428800',
    '--write-out','%{http_code}|%{time_namelookup}|%{time_connect}|%{time_appconnect}|%{time_starttransfer}|%{time_total}|%{size_download}|%{num_redirects}']
  if attempt==2:command.extend(['--ipv4','--http1.1'])
  command.extend([url,'-o',str(temp)])
  started=time.monotonic()
  try:p=call(command,timeout=max_seconds+5,check=False)
  except subprocess.TimeoutExpired:
   p=subprocess.CompletedProcess(command,28,'','')
  elapsed=time.monotonic()-started
  if p.returncode==0:
   if not temp.is_file() or (expected and digest(temp)!=expected):
    if temp.exists():temp.unlink()
    network_record(name,url,attempt,p,'CHECKSUM_MISMATCH',elapsed)
    raise Stop('DOWNLOAD_CHECKSUM_MISMATCH: '+name+'; refused without retry or execution')
   temp.chmod(0o600);os.replace(temp,path)
   if expected:
    cached=CACHE/expected;safe(cached)
    # Do not replace a corrupt owner file silently. It was ignored above.
    if not cached.exists():shutil.copyfile(path,cached);cached.chmod(0o600)
   network_record(name,url,attempt,p,'DOWNLOADED_AND_VERIFIED' if expected else 'DOWNLOADED_LIVE_METADATA',elapsed)
   return
  if temp.exists():temp.unlink()
  category='TIMEOUT' if p.returncode==28 else 'DNS_FAILED' if p.returncode==6 else 'TRANSFER_FAILED'
  network_record(name,url,attempt,p,category,elapsed)
  http=r['last_download'].get('http_status',0)
  transient=p.returncode in (6,7,18,28,35,52,55,56,92) or (p.returncode==22 and http in (408,429,500,502,503,504))
  if not transient or attempt==attempts:break
  time.sleep(1)
 raise Stop('DOWNLOAD_'+category+': '+name+' from '+str(parsed.hostname)+'; curl exit '+str(p.returncode)+
            '; measured '+str(round(elapsed,1))+'s on last attempt; see network-last.json; no local source or credentials changed')


def get(url):
 with tempfile.TemporaryDirectory(prefix='.public-read-',dir=CURRENT) as t:
  p=Path(t)/'response';download(url,p,attempts=1,max_seconds=15)
  if p.stat().st_size>2*1024*1024:raise Stop('Public metadata exceeds bound')
  return json.loads(p.read_text())


def recovery_note():
 vault=HOME/'Documents/Amrit Executive Memory'
 if not vault.is_dir():r['obsidian']='EXISTING_VAULT_NOT_FOUND';return
 dest=vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv';mkdir(dest)
 note=dest/(RUN_ID+'-network-recovery.md')
 body='# GharTV RC4 — distribution recovery\n\n'
 body+='Same Android source `'+SOURCE+'`, code20. This is a launcher/network repair, not an APK rebuild.\n\n'
 body+='Previous owner run GHARTV-CYAN-6-20260915T111739Z-35925 timed out in continuity downloads before signing, app installation or Obsidian write. Its generic error does not identify the stalled endpoint or network stage.\n\n'
 body+='This run verifies local/bundled/cached immutable bytes first. Public release publication and live collector status are separate from local installation. Production is not changed. Other lanes must not infer a signed APK or running emulator from this note alone.\n\n```text\n'+receipt()+'```\n'
 write(note,body)
 if note.read_bytes()!=body.encode():raise Stop('RECOVERY_NOTE_READBACK_FAILED')
 r['obsidian']='RECOVERY_NOTE_WRITTEN_AND_READBACK_VERIFIED';r['obsidian_note']=str(note)


def observe_production():
 if args.bundled_review:
  r['production_feed']='NOT_FETCHED_BUNDLED_REVIEW_NO_PRODUCTION_WRITE';return
 try:m=get(f'https://raw.githubusercontent.com/{REPO}/main/update/latest.json')
 except Exception:
  r['production_feed']='UNAVAILABLE_LOCAL_REVIEW_ONLY_NO_PRODUCTION_WRITE';return
 r['production_feed']=str(m.get('versionName','UNKNOWN'))+' / code '+str(m.get('versionCode','UNKNOWN'))
 r['observed_production_source']=m.get('sourceCommit','UNKNOWN')
 if int(m.get('versionCode',0))>=20:raise Stop('Production has caught up or advanced; review identity needs reconciliation')


def publish_review_after_local_success():
 if r.get('emulator')!='RC4_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND':return
 if args.bundled_review:
  r['review_release']='LOCAL_SIGNED_VERIFIED_GITHUB_UPLOAD_DEFERRED_BUNDLED_REVIEW';return
 if not shutil.which('gh'):
  r['review_release']='LOCAL_SIGNED_VERIFIED_GITHUB_CLI_UNAVAILABLE_UPLOAD_PENDING';return
 target=CURRENT/ASSET
 try:
  info=json.loads(call(['gh','api',f'repos/{REPO}/releases/tags/{TAG}'],20).stdout)
  if info.get('target_commitish')!=SOURCE or not info.get('prerelease') or info.get('draft'):
   r['review_release']='PUBLISHED_RELEASE_IDENTITY_CHANGED_NO_UPLOAD';return
  published=[a for a in info.get('assets',[]) if a.get('name')==ASSET]
  if published:
   r['review_release']='SIGNED_REVIEW_ASSET_VERIFIED' if len(published)==1 and published[0].get('digest')=='sha256:'+r['signed_apk_sha256'] else 'DIFFERENT_SIGNED_REVIEW_ASSET_PRESERVED_NO_UPLOAD'
   return
  call(['gh','release','upload',TAG,target,'--repo',REPO],45)
  info=json.loads(call(['gh','api',f'repos/{REPO}/releases/tags/{TAG}'],20).stdout)
  found=[a for a in info.get('assets',[]) if a.get('name')==ASSET and a.get('digest')=='sha256:'+r['signed_apk_sha256']]
  r['review_release']='SIGNED_REVIEW_ASSET_VERIFIED' if len(found)==1 else 'UPLOAD_NOT_VERIFIED_LOCAL_REVIEW_PRESERVED'
 except Exception:r['review_release']='GITHUB_PUBLICATION_PENDING_LOCAL_REVIEW_PRESERVED'
'''

def patch(src:Path,dst:Path):
 raw=src.read_bytes()
 if hashlib.sha256(raw).hexdigest()!=ORIGINAL:raise SystemExit('Original owner launcher changed; no patch applied')
 s=raw.decode()
 def once(old,new):
  nonlocal s
  if s.count(old)!=1:raise SystemExit('Patch target ambiguous: '+old[:90])
  s=s.replace(old,new,1)
 s=s.replace('CYAN REVIEW 6 · 0.6.0 RC4','CYAN REVIEW 6 · NETWORK RECOVERY R1 · same 0.6.0 RC4',1)
 s=s.replace("RUN_ID='GHARTV-CYAN-6-'","RUN_ID='GHARTV-CYAN-6-NET-R1-'",1)
 s=s.replace("args=parser.parse_args(sys.argv[2:])","parser.add_argument('--bundled-review',action='store_true');args=parser.parse_args(sys.argv[2:])",1)
 once("IST=dt.timezone(dt.timedelta(hours=5,minutes=30))","EMBEDDED_MANIFEST="+repr(MANIFEST)+"\nIST=dt.timezone(dt.timedelta(hours=5,minutes=30))")
 start=s.index('def download(url,path):');end=s.index('def git(*a):',start)
 s=s[:start]+NETWORK+'\n'+s[end:]
 once("  download(f'https://github.com/{REPO}/releases/download/{TAG}/review-manifest.json',meta)","  r['phase']='ARTIFACT_MANIFEST'\n  download(f'https://github.com/{REPO}/releases/download/{TAG}/review-manifest.json',meta)")
 once("  bundle=t/'companion.zip';download", "  r['phase']='ARTIFACT_COMPANION'\n  bundle=t/'companion.zip';download")
 once(" if not shutil.which('gh'):raise Stop('GitHub CLI unavailable; Obsidian progress was already written')\n call(['gh','auth','status','-h','github.com'])\n",'')
 a=s.index(' def release():',s.index('def review():'));b=s.index(" with tempfile.TemporaryDirectory(prefix='.rc4-review-'",a)
 s=s[:a]+s[b:]
 once("   download(f'https://github.com/{REPO}/releases/download/{TAG}/GharTV-review-unsigned.apk',u)","   r['phase']='ARTIFACT_UNSIGNED_APK'\n   download(f'https://github.com/{REPO}/releases/download/{TAG}/GharTV-review-unsigned.apk',u)")
 once("   download(f'https://github.com/{REPO}/releases/download/v0.5.4-rc8/GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk',p)","   r['phase']='ARTIFACT_SIGNER_REFERENCE'\n   download(f'https://github.com/{REPO}/releases/download/v0.5.4-rc8/GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk',p)")
 a=s.index("   published=release();",s.index('def review():'));b=s.index('   elif args.signed_apk:',a)
 s=s[:a]+"   r['review_release']='LOCAL_PREPARATION_GITHUB_PUBLICATION_PENDING'\n   if args.signed_apk:"+s[b+len('   elif args.signed_apk:'):]
 a=s.index("   h=digest(c);r['signed_apk_sha256']=h;race=release()")
 b=s.index("   print('4 / 5",a)
 s=s[:a]+"   h=digest(c);r['signed_apk_sha256']=h\n   r['review_release']='SIGNED_LOCAL_VERIFIED_GITHUB_PUBLICATION_PENDING';r['phase']='EMULATOR_SELECTION'\n"+s[b:]
 s=s.replace('Signed APK payload and original certificate verified. Publish review only.','Signed APK payload and original certificate verified. Open local review first.',1)
 a=s.index(" m=get(f'https://raw.githubusercontent.com/{REPO}/main/update/latest.json')",s.index("note_text='';lock_acquired=False"))
 b=s.index(" r['status']='MEMORY_UPDATED'",a)
 s=s[:a]+" observe_production()\n"+s[b:]
 once(" r['phase']='CONTINUITY';print", " r['network_recovery']=NETWORK_RECOVERY\n recovery_note();persist()\n r['phase']='CONTINUITY';print")
 once("    if note_text and not args.memory_only:collector_check_and_deploy()", "    if args.bundled_review:r['backend']='NOT_CHECKED_BUNDLED_REVIEW_NO_DEPLOYMENT'\n    elif note_text and not args.memory_only:collector_check_and_deploy()")
 once("   print('5 / 5 · Save exact outcome", "   publish_review_after_local_success()\n   print('5 / 5 · Save exact outcome")
 once("   persist()\n   bridge_once()\n   persist()", "   else:recovery_note()\n   persist()\n   bridge_once()\n   if note_text:\n    try:note_sync(note_text)\n    except Exception:r['obsidian']='FINAL_NOTE_UPDATE_FAILED'\n   else:recovery_note()\n   persist()")
 compile(s.split("<<'PY'\n",1)[1].split('\nPY\n',1)[0],str(dst),'exec')
 dst.write_text(s);dst.chmod(0o700)
 return hashlib.sha256(s.encode()).hexdigest()

if __name__=='__main__':
 if len(sys.argv)!=3:raise SystemExit('usage: repair_network.py ORIGINAL.command OUTPUT.command')
 print(patch(Path(sys.argv[1]),Path(sys.argv[2])))
