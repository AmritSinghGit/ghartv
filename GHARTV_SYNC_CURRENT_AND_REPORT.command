#!/bin/bash
# RC9 exact review and approval workflow. Restore existing configured signing; never generate a key or prompt for passwords.
set -u
umask 077
printf '\033[38;5;51m\nGharTV · CYAN REVIEW 14 R2 · 0.6.0 RC9 · automatic focus-owned preview restored + TV-first regression checks · artifact review · development checkout preserved\033[0m\n'
if ! command -v python3 >/dev/null 2>&1; then echo 'Python 3 is required; no TV or source changed.'; exit 1; fi
CLOSE_MARKER="${TMPDIR:-/tmp}/ghartv-review-close-$$"
export GHARTV_CLOSE_MARKER="$CLOSE_MARKER"
python3 - "$0" "$@" <<'PY'
from __future__ import annotations
import argparse, datetime as dt, fcntl, hashlib, io, json, os, re, shlex, shutil, socket, stat, subprocess, sys, tempfile, time, zipfile, signal, urllib.request, urllib.error
from pathlib import Path
REPO='AmritSinghGit/ghartv'; SOURCE='c20e3ef5dc8ff5dbee52c338930a5c4ac30157e8'
PROD='59c130abc1283e66607315db916973553164064d'; TAG='v0.6.0-rc9'; PACKAGE='in.ghartv.nova'
BACKEND_VERSION='0.6.0-rc4-owner-convergence'
VERSION='0.6.0-rc9-tv-first'; UNSIGNED='c095270d1da6fcf10382f8739fab03175a739d34636255f9748760096876b7a0'
PROD_HASH='8cff8f85403da5924865fddc687dbff11089d7c498f9863e483a7d8123c1ce13'
MANIFEST_SHA='091bff7a9f51dbe279df35764435c1e3febb08b425f8a52da1d17f70e27a8f72'; AVD='GharTV_Nova_Manual_google_tv_API36'; ASSET='GharTV-review-current.apk'
HOME=Path.home(); SELF=Path(sys.argv[1]).resolve(); STATE=HOME/'Library/Application Support/GharTV/owner-review'; CURRENT=STATE/'current'
PROJECT=Path(os.environ.get('GHARTV_PROJECT',str(HOME/'Downloads/GharTV_Nova_v0.4.2'))).expanduser()
RUNTIME=STATE/'runtime-current';COLLECTOR='https://ghartv-telemetry.ghartv-47d9a0.workers.dev'; manifest={}
EMBEDDED_MANIFEST='{\n  "schema": "ghartv.review-manifest.v2",\n  "source_sha": "c20e3ef5dc8ff5dbee52c338930a5c4ac30157e8",\n  "branch": "codex/ghartv-remove-auto-preview",\n  "pr": 1,\n  "version_name": "0.6.0-rc9-tv-first",\n  "version_code": 26,\n  "unsigned_sha256": "c095270d1da6fcf10382f8739fab03175a739d34636255f9748760096876b7a0",\n  "companion_sha256": "7de040fd57c1308b24fd822c0b31c4c797768064c3d36aad2fdc744b349db2c9",\n  "production_unchanged": true,\n  "owner_signed_apk_sha256": null,\n  "owner_mac_run": "NOT_EXECUTED",\n  "ai_super_resolution": "NOT_IMPLEMENTED"\n}\n'
IST=dt.timezone(dt.timedelta(hours=5,minutes=30))
RUN_ID='GHARTV-CYAN-14R2-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+str(os.getpid()); RUN=STATE/'runs'/RUN_ID
parser=argparse.ArgumentParser();parser.add_argument('--memory-only',action='store_true');parser.add_argument('--signed-apk',type=Path);parser.add_argument('--noninteractive',action='store_true');parser.add_argument('--skip-backend-deploy',action='store_true');parser.add_argument('--bundled-review',action='store_true');args=parser.parse_args(sys.argv[2:])
r=dict(review_slot='GREEN_LOCAL_REVIEW',public_slot='BLUE_HOUSEHOLD_RELEASE',development='SOURCE_ONLY_CHECKOUT_READ_ONLY',time_ist=dt.datetime.now(IST).isoformat(),time_utc=dt.datetime.now(dt.timezone.utc).isoformat(),checkout='READ_ONLY_NOT_INSPECTED',backend='NOT_CHECKED',web_player='NOT_STARTED',run_id=RUN_ID,lane_id='ghartv',repository=REPO,operon_session=os.environ.get('OPERON_SESSION_ID','UNBOUND'),
 production_source=PROD,review_source=SOURCE,version=VERSION,version_code=26,unsigned_apk_sha256=UNSIGNED,signed_apk_sha256='NOT_VERIFIED',
 delivery_sha='NOT_READ',local_sha='NOT_READ',production_feed='NOT_CHECKED',obsidian='NOT_WRITTEN',memory_bridge='NOT_RUN',
 review_release='NOT_CHECKED',emulator='UNCHANGED',physical_tv='NOT_VERIFIED',owner_decision='REVIEW_PENDING',
 dashboard='NOT_OPENED',signing_mode='NOT_ATTEMPTED',signing_error_code='NONE',signer_exit_code='NOT_RUN',
 phase='STARTING',review_screen='NOT_VERIFIED',receipt_sync='NOT_ATTEMPTED',cleanup_count=0,cleanup_bytes=0,status='STARTING')
class Stop(RuntimeError):pass
def safe(path):
 if any(p.is_symlink() for p in (path,*path.parents)):raise Stop('Symlinked path preserved; use the canonical directory')
def mkdir(path):safe(path);path.mkdir(parents=True,exist_ok=True);path.chmod(0o700)
def digest(path):
 h=hashlib.sha256()
 with open(path,'rb') as f:
  for b in iter(lambda:f.read(131072),b''):h.update(b)
 return h.hexdigest()
def write(path,text):
 safe(path);temp=path.with_name(path.name+'.new-'+str(os.getpid()))
 with open(temp,'x',encoding='utf-8') as f:f.write(text)
 temp.chmod(0o600);os.replace(temp,path)
def call(argv,timeout=45,check=True,env=None):
 p=subprocess.run(list(map(str,argv)),capture_output=True,text=True,timeout=timeout,env=env,stdin=subprocess.DEVNULL)
 if check and p.returncode:raise Stop(Path(str(argv[0])).name+' failed (exit '+str(p.returncode)+'); no force/recovery action taken')
 return p


# Only immutable, known public artifacts can enter the local distribution cache.
ARTIFACTS={
 'review-manifest.json': MANIFEST_SHA,
 'GharTV-review-companion.zip':'7de040fd57c1308b24fd822c0b31c4c797768064c3d36aad2fdc744b349db2c9',
 'GharTV-review-unsigned.apk':UNSIGNED,
 'GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk':PROD_HASH,
}
NETWORK_RECOVERY='CYAN14-TV-FIRST-PREVIEW'
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
  transient=p.returncode in (6,7,18,28,35,52,55,56,92) or (p.returncode==23 and http in (408,429,500,502,503,504))
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
 body='# GharTV RC9 — TV-first automatic preview restoration\n\n'
 body+='Same Android source `'+SOURCE+'`, code26. Bounded previews, smooth focus, local timing measurements, comfort settings and manual reporting.\n\n'
 body+='Last verified owner selection opened Nova code24 on5580; the manual Preview 12s/off-default change was explicitly rejected. Automatic focus-owned muted preview is restored in code26. Code25 remains recovery-only.\n\n'
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
 if int(m.get('versionCode',0))>=26:raise Stop('Production has caught up or advanced; review identity needs reconciliation')


def publish_review_after_local_success():
 if r.get('emulator')!='RC9_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND':return
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

def git(*a):return call(['git','-C',PROJECT,*a]).stdout.strip()
def receipt():return 'GHARTV_CYAN_REVIEW_14_HANDOFF\n'+'\n'.join(k.upper()+'='+str(v) for k,v in r.items())+'\n'
def persist():
 r['evidence']=str(RUN)
 write(RUN/'handoff.txt',receipt());write(RUN/'receipt.json',json.dumps(r,indent=2)+'\n');write(CURRENT/'handoff.txt',receipt());write(CURRENT/'receipt.json',json.dumps(r,indent=2)+'\n')
def note_sync(note_text,transport=True):
 vault=HOME/'Documents/Amrit Executive Memory'
 if not vault.is_dir():r['obsidian']='EXISTING_VAULT_NOT_FOUND';return
 dest=vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv';mkdir(dest)
 note=dest/'GharTV - Current Progress.md';safe(note)
 if note.exists() and '<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->' not in note.read_text():note=dest/(RUN_ID+'-progress.md')
 body=note_text+'\n\n## Actual local receipt\n\n```text\n'+receipt()+'```\n'
 write(note,body)
 if note.read_bytes()!=body.encode():raise Stop('Obsidian readback differs')
 r['obsidian']='WRITTEN_AND_READBACK_VERIFIED';r['obsidian_note']=str(note)
 write(dest/(RUN_ID+'-receipt.txt'),receipt())

def bridge_once():
 bridge=HOME/'bin/amrit-context';path=str(bridge) if bridge.is_file() and os.access(bridge,os.X_OK) else shutil.which('amrit-context')
 if not path or not r.get('obsidian_note'):r['memory_bridge']='PENDING_LOCAL_BRIDGE_OR_NOTE_UNAVAILABLE';return
 results=[]
 for command in ([path,'handoff','--file',r['obsidian_note']],[path,'sync-once']):
  process=subprocess.Popen(command,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,stdin=subprocess.DEVNULL,start_new_session=True)
  try:results.append('EXIT_'+str(process.wait(timeout=10)))
  except subprocess.TimeoutExpired:
   os.killpg(process.pid,signal.SIGTERM)
   try:process.wait(timeout=2)
   except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
   results.append('TIMEOUT_PENDING');break
 r['memory_bridge']='_'.join(results)+'_REPLICA_UNVERIFIED'

def mirror_receipt():
 # Child only receives a path to the shipped, verified local module, not the receipt/secrets on argv.
 # The module projects an explicit safe field list and performs bounded GitHub readback.
 if not shutil.which('node') or not (RUNTIME/'web-player/review-sync.mjs').is_file():
  r['receipt_sync']='PENDING_LOCAL_RUNTIME_UNAVAILABLE';return
 try:
  out=call(['node',RUNTIME/'web-player/review-sync.mjs','--publish'],22,False)
  if out.returncode!=0:r['receipt_sync']='PENDING_SYNC_PROCESS_ERROR';return
  result=json.loads(out.stdout)
  value=result.get('status','PENDING_UNCONFIRMED')
  r['receipt_sync']=value if re.fullmatch('[A-Z_]{1,100}',value) else 'PENDING_UNCONFIRMED'
  url=result.get('url','')
  if re.fullmatch(r'https://github\.com/AmritSinghGit/ghartv/pull/1#issuecomment-\d+',url):r['receipt_url']=url
 except Exception:r['receipt_sync']='PENDING_NETWORK_OR_CLI_UNAVAILABLE'

def reconcile():
 global manifest
 with tempfile.TemporaryDirectory(prefix='.release-read-',dir=CURRENT) as t:
  t=Path(t);meta=t/'manifest.json'
  r['phase']='ARTIFACT_MANIFEST'
  download(f'https://github.com/{REPO}/releases/download/{TAG}/review-manifest.json',meta)
  if digest(meta)!=MANIFEST_SHA:raise Stop('RELEASE_MANIFEST_CHECKSUM_MISMATCH')
  manifest=json.loads(meta.read_text())
  if manifest.get('source_sha')!=SOURCE or manifest.get('unsigned_sha256')!=UNSIGNED or manifest.get('version_code')!=26:raise Stop('RELEASE_IDENTITY_MISMATCH')
  r['phase']='ARTIFACT_COMPANION'
  bundle=t/'companion.zip';download(f'https://github.com/{REPO}/releases/download/{TAG}/GharTV-review-companion.zip',bundle)
  if digest(bundle)!=manifest['companion_sha256']:raise Stop('COMPANION_CHECKSUM_MISMATCH')
  safe(RUNTIME)
  marker=RUNTIME/'.ghartv-managed.json'
  if RUNTIME.exists() and (not marker.is_file() or marker.is_symlink()):raise Stop('Unknown runtime directory preserved')
  if RUNTIME.exists() and json.loads(marker.read_text()).get('source')==SOURCE:
   # Check every packaged byte before executing a reused managed runtime.
   with zipfile.ZipFile(bundle) as z:
    for item in z.infolist():
     if item.is_dir():continue
     p=RUNTIME/item.filename;safe(p)
     if not p.is_file() or p.read_bytes()!=z.read(item):raise Stop('Managed runtime was edited; preserved for reconciliation')
  else:
   stage=CURRENT/('.companion-stage-'+RUN_ID);mkdir(stage)
   with zipfile.ZipFile(bundle) as z:
    entries=z.infolist()
    if len(entries)>8000 or sum(i.file_size for i in entries)>150*1024*1024:raise Stop('Bundle exceeds bound')
    for item in entries:
     p=Path(item.filename)
     if p.is_absolute() or '..' in p.parts or (item.external_attr>>16)&0o170000==0o120000:raise Stop('Unsafe bundle member')
     if item.is_dir():continue
     dst=stage/p;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(z.read(item));dst.chmod(0o600)
   write(stage/'.ghartv-managed.json',json.dumps({'source':SOURCE,'owner':'ghartv-review-companion-v1'})+'\n')
   # Stable runtime path may be active. Stop only its proved owned server before replacement.
   if RUNTIME.exists():stop_owned_web_if_needed()
   if RUNTIME.exists():
    prior=STATE/('runtime-preserved-'+RUN_ID);RUNTIME.rename(prior);r['prior_runtime_preserved']=str(prior)
   stage.rename(RUNTIME)
  write(CURRENT/'review-manifest.json',json.dumps(manifest,indent=2)+'\n')
  r['delivery_sha']=SOURCE;r['artifact_manifest_sha256']=MANIFEST_SHA
  r['tv_experience_contract_sha256']=digest(RUNTIME/'TV_EXPERIENCE_CONTRACT.json')
  return (RUNTIME/'GHARTV_LANE_PROGRESS.md').read_text()

def sync_checkout():
 # Binary/source provenance comes from the immutable release. This is NOT a source sync.
 if not (PROJECT/'.git').exists():r['checkout']='NOT_FOUND_ARTIFACT_REVIEW_CONTINUES';return
 try:
  origin=git('remote','get-url','origin').removesuffix('.git')
  if origin not in ('https://github.com/'+REPO,'git@github.com:'+REPO):r['checkout']='OTHER_REPOSITORY_PRESERVED';return
  r['local_sha']=git('rev-parse','HEAD')
  r['local_branch']=call(['git','-C',PROJECT,'symbolic-ref','--short','HEAD'],check=False).stdout.strip() or 'DETACHED'
  status=call(['git','-C',PROJECT,'status','--porcelain','--untracked-files=normal'],check=False).stdout
  r['local_changed_entries']=len(status.splitlines());r['checkout']='PRESERVED_READ_ONLY_ARTIFACT_REVIEW'
 except Exception:r['checkout']='INSPECTION_UNAVAILABLE_PRESERVED'


def payload(path):
 out={};seen=set()
 with zipfile.ZipFile(path) as z:
  items=z.infolist()
  if len(items)>5000 or sum(x.file_size for x in items)>100*1024*1024:raise Stop('APK payload exceeds bound')
  for x in items:
   if x.filename in seen:raise Stop('Duplicate APK member')
   seen.add(x.filename)
   if x.is_dir() or re.fullmatch(r'META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))',x.filename,re.I):continue
   out[x.filename]=hashlib.sha256(z.read(x)).hexdigest()
 return out

SIGN_KEYS=('GHARTV_SIGNING_STORE','GHARTV_SIGNING_STORE_PASSWORD','GHARTV_SIGNING_KEY_ALIAS','GHARTV_SIGNING_KEY_PASSWORD')
def signing_config():
 # Exact previously deployed configuration, not arbitrary secret discovery or shell sourcing.
 root=HOME/'Library/Application Support/GharTV/signing';config=root/'signing.env';expected=root/'ghartv-release.jks'
 for path in (config,expected):
  safe(path)
  if not path.is_file():raise Stop('SIGNING_CONFIG_MISSING: existing signing.env and ghartv-release.jks are required; no replacement key created')
 if config.stat().st_uid!=os.getuid() or config.stat().st_mode & 0o077:
  raise Stop('SIGNING_CONFIG_PERMISSIONS: existing signing.env must be owned by this user and private (mode600)')
 if expected.stat().st_uid!=os.getuid():raise Stop('SIGNING_KEY_OWNER_MISMATCH: existing keystore is not owned by this user')
 fd=os.open(config,os.O_RDONLY|os.O_NOFOLLOW)
 with os.fdopen(fd,'r',encoding='utf-8') as f:
  content=f.read(16385)
 if len(content)>16384:raise Stop('SIGNING_CONFIG_INVALID: configuration exceeds the expected bound')
 values={}
 for line in content.splitlines():
  line=line.strip()
  if line.startswith('export '):line=line[7:].lstrip()
  key,sep,value=line.partition('=')
  if not sep or key.strip() not in SIGN_KEYS:continue
  key=key.strip()
  if key in values:raise Stop('SIGNING_CONFIG_INVALID: duplicate signing field')
  try:parts=shlex.split(value,comments=True,posix=True)
  except ValueError:raise Stop('SIGNING_CONFIG_INVALID: malformed quoted field') from None
  if len(parts)!=1 or not parts[0] or any(c in parts[0] for c in ('\x00','\n','\r')):
   raise Stop('SIGNING_CONFIG_INVALID: a signing field is empty or unsupported')
  values[key]=parts[0]
 if any(k not in values for k in SIGN_KEYS):raise Stop('SIGNING_CONFIG_INCOMPLETE: the existing configuration lacks a required field')
 location=values['GHARTV_SIGNING_STORE']
 for prefix in ('${HOME}/','$HOME/','~/'):
  if location.startswith(prefix):location=str(HOME)+'/'+location[len(prefix):];break
 if Path(location)!=expected:raise Stop('SIGNING_KEY_PATH_MISMATCH: configuration does not identify the canonical existing key')
 return values

def signing_category(message):
 text=message.lower()
 if 'keystore was tampered' in text or 'keystore password was incorrect' in text or 'password verification failed' in text:
  return 'KEYSTORE_PASSWORD_REJECTED'
 if 'cannot recover key' in text or 'unrecoverablekeyexception' in text or 'given final block not properly padded' in text:
  return 'PRIVATE_KEY_PASSWORD_REJECTED'
 if 'does not contain key' in text or 'does not contain an entry' in text or ('alias' in text and 'not found' in text):
  return 'KEY_ALIAS_NOT_FOUND'
 if 'invalid keystore format' in text or 'unrecognized keystore' in text:return 'KEYSTORE_FORMAT_REJECTED'
 if 'unsupportedclassversion' in text or 'could not find or load' in text:return 'JAVA_OR_SIGNER_RUNTIME_ERROR'
 return 'SIGNER_FAILED_UNCLASSIFIED'

def configured_sign(signer,align,unsigned,candidate,environment):
 values=signing_config();private_env=dict(environment)
 for k in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS',*SIGN_KEYS):private_env.pop(k,None)
 private_env['JAVA_TOOL_OPTIONS']='-Xmx256m'
 private_env['_GHARTV_LOCAL_STORE_PASS']=values['GHARTV_SIGNING_STORE_PASSWORD']
 private_env['_GHARTV_LOCAL_KEY_PASS']=values['GHARTV_SIGNING_KEY_PASSWORD']
 aligned=candidate.with_name('aligned.apk')
 try:
  call([align,'-f','4',unsigned,aligned],60,env=environment)
  argv=[str(signer),'sign','--ks',values['GHARTV_SIGNING_STORE'].replace('${HOME}',str(HOME)).replace('$HOME',str(HOME)),
   '--ks-key-alias',values['GHARTV_SIGNING_KEY_ALIAS'],'--ks-pass','env:_GHARTV_LOCAL_STORE_PASS',
   '--key-pass','env:_GHARTV_LOCAL_KEY_PASS','--v1-signing-enabled','true','--v2-signing-enabled','true',
   '--v3-signing-enabled','true','--v4-signing-enabled','false','--out',str(candidate),str(aligned)]
  argv[argv.index('--ks')+1]=str(HOME/'Library/Application Support/GharTV/signing/ghartv-release.jks')
  r['signing_mode']='EXISTING_LOCAL_CONFIG_NO_PROMPT';r['phase']='SIGNING'
  result=call(argv,60,False,private_env);r['signer_exit_code']=result.returncode
  if result.returncode:
   r['signing_error_code']=signing_category(result.stderr+'\n'+result.stdout)
   # Allowlisted diagnostic codes only: do not persist raw signer output, paths, passwords or command arguments.
   write(RUN/'signing-diagnostic.json',json.dumps({'stage':'signing','exit_code':result.returncode,'category':r['signing_error_code'],'raw_output_retained':False},indent=2)+'\n')
   raise Stop(r['signing_error_code']+': existing local signing configuration failed; no password guessing, no key replacement, emulator untouched')
  r['signing_error_code']='NONE'
 finally:
  private_env.clear();values.clear()


def resource_preflight():
 r['phase']='RESOURCE_PREFLIGHT'
 if sys.platform!='darwin':raise Stop('MAC_ONLY_NO_ACTION')
 measured=call(['/usr/sbin/sysctl','-n','kern.memorystatus_vm_pressure_level'],5,False).stdout.strip()
 r['host_pressure_at_launch']={'1':'NORMAL','2':'WARNING','4':'CRITICAL'}.get(measured,'NOT_REPORTED')
 # Do not start another heavyweight VM under pressure. Reusing an already booted
 # exact Nova is allowed; this is not a declaration of good rendering performance.
 if measured!='1':
  adb=HOME/'Library/Android/sdk/platform-tools/adb'
  boot=call([adb,'-s','emulator-5580','shell','getprop','sys.boot_completed'],4,False).stdout.strip() if adb.is_file() else ''
  name=call([adb,'-s','emulator-5580','emu','avd','name'],4,False).stdout.splitlines() if boot=='1' else []
  if not (boot=='1' and name and name[0].strip()==AVD):
   raise Stop('HOST_PRESSURE_'+r['host_pressure_at_launch']+'_NO_NEW_EMULATOR: inspect the saved resource report and stop only identified idle workloads first')
  r['resource_admission']='REUSE_ALREADY_BOOTED_NOVA_ONLY'
 else:r['resource_admission']='HOST_PRESSURE_NORMAL'

def preserve_verified_signed(candidate,digest_value):
 cache=STATE/'verified-review-apks'/SOURCE;mkdir(cache)
 dest=cache/(digest_value+'.apk');safe(dest)
 if dest.exists():
  if digest(dest)!=digest_value:raise Stop('PREPARED_SIGNED_CACHE_CONFLICT_PRESERVED')
 else:
  temp=cache/('.candidate-'+str(os.getpid()))
  with open(temp,'xb') as out,open(candidate,'rb') as inp:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
  temp.chmod(0o600)
  if digest(temp)!=digest_value:raise Stop('PREPARED_SIGNED_CACHE_READBACK_FAILED')
  os.replace(temp,dest)
 write(CURRENT/'prepared-review-artifact.json',json.dumps({'source':SOURCE,'sha256':digest_value,'version_code':26,'installed':False,'certificate_verified':True},indent=2)+'\n')
 r['prepared_signed_apk']='PERSISTED_AND_HASH_VERIFIED_BEFORE_EMULATOR_SELECTION'
 r['prepared_signed_apk_path']=str(dest)
 # Preserve technical identity now, even if boot later times out.
 persist()

def attach_with_boot_evidence(transport,sdk):
 started=False
 try:return transport.attach_or_start(sdk,RUN)
 except transport.Hold as e:
  reason=str(e)
  if reason!='EMULATOR_BOOT_TIMEOUT_PROCESS_PRESERVED':raise Stop('EMULATOR_SELECTION_'+reason) from None
 # Original helper already waited 150 s. Continue observing the SAME VM for
 # another bounded 150 s: do not restart, change renderer, kill or create an AVD.
 adb=sdk/'platform-tools/adb';deadline=time.monotonic()+150;tick=0
 r['boot_wait']='EXISTING_VM_ADDITIONAL_150_SECONDS_NO_RESTART'
 while time.monotonic()<deadline:
  if tick%5==0:print('Waiting for the existing Nova VM to finish booting; no second emulator is being started…',flush=True)
  tick+=1
  try:
   name=call([adb,'-s','emulator-5580','emu','avd','name'],4,False).stdout.splitlines()
   if name and name[0].strip() not in ('',AVD):raise Stop('CANONICAL_AVD_CHANGED_DURING_BOOT_PRESERVED')
   if name and name[0].strip()==AVD and call([adb,'-s','emulator-5580','shell','getprop','sys.boot_completed'],4,False).stdout.strip()=='1':
    r['boot_wait']='EXISTING_VM_BOOT_VERIFIED_DURING_EXTENDED_WAIT'
    return adb,any(x.get('stage')=='STARTED_EXISTING_AVD' for x in json.loads((RUN/'PORT_CHECK.json').read_text()).get('observations',[]))
  except subprocess.TimeoutExpired:pass
  time.sleep(2)
 try:
  port=json.loads((RUN/'PORT_CHECK.json').read_text());port['r2_boot_stage']='BOOT_TIMEOUT_AFTER_EXTENDED_OBSERVATION_PROCESS_PRESERVED';write(RUN/'PORT_CHECK.json',json.dumps(port,indent=2)+'\n')
 except (OSError,ValueError):pass
 raise Stop('EMULATOR_BOOT_TIMEOUT_AFTER_EXTENDED_OBSERVATION_PROCESS_PRESERVED')

def review():
 print('\n2 / 5 · Verify exact cloud APK and reuse the existing local signing configuration.',flush=True)
 sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(HOME/'Library/Android/sdk')));tools=sorted(sdk.glob('build-tools/*/apksigner'),key=lambda p:tuple(map(int,re.findall(r'\d+',p.parent.name))))
 if not tools:raise Stop('Android apksigner not found in the existing SDK')
 signer=tools[-1];align=signer.parent/'zipalign';aapt=signer.parent/'aapt';adb=sdk/'platform-tools/adb'
 env=dict(os.environ)
 for key in (*SIGN_KEYS,'_GHARTV_LOCAL_STORE_PASS','_GHARTV_LOCAL_KEY_PASS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):env.pop(key,None)
 java=Path('/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home')
 if not (java/'bin/java').is_file():
  detected=call(['/usr/libexec/java_home','-v','17'],check=False).stdout.strip()
  if not detected:raise Stop('JDK17_NOT_FOUND: preserve existing toolchain; no new Java installed')
  java=Path(detected)
 env['JAVA_HOME']=str(java);env['PATH']=str(java/'bin')+':'+env.get('PATH','');env['JAVA_TOOL_OPTIONS']='-Xmx256m'
 def cert(path):
  r['phase']='VERIFY_APK_CERTIFICATE';r['certificate_probe_artifact']=path.name
  helper=RUNTIME/'tools/GharTVApkVerifier.java';jar=signer.parent/'lib/apksigner.jar'
  if not helper.is_file() or not jar.is_file():raise Stop('APK_VERIFIER_LIBRARY_MISSING: preserve key and app; installed Android tools required')
  checked=call([str(java/'bin/java'),'-cp',str(jar),str(helper),str(path)],60,False,env)
  r['certificate_probe_exit']=checked.returncode
  try:data=json.loads(checked.stdout.strip())
  except ValueError:raise Stop('APK_VERIFIER_OUTPUT_INVALID: native result unavailable; no install or key change') from None
  if checked.returncode or data.get('ok') is not True:raise Stop('APK_VERIFY_FAILED: '+str(data.get('error','UNKNOWN')))
  values=data.get('certificate_sha256',[])
  if not values or not all(re.fullmatch('[a-f0-9]{64}',v) for v in values):raise Stop('APK_VERIFIER_CERTIFICATE_INVALID')
  r['certificate_engine']='ANDROID_APKSIG_LIBRARY';return set(values)
 with tempfile.TemporaryDirectory(prefix='.rc4-review-',dir=CURRENT) as t:
   t=Path(t);u=t/'unsigned.apk';c=t/'candidate.apk';p=t/'production.apk'
   r['phase']='ARTIFACT_UNSIGNED_APK'
   download(f'https://github.com/{REPO}/releases/download/{TAG}/GharTV-review-unsigned.apk',u)
   r['phase']='ARTIFACT_SIGNER_REFERENCE'
   download(f'https://github.com/{REPO}/releases/download/v0.5.4-rc8/GharTV-Jio-Live-v0.5.4-rc8-pre-birthday-recovery.apk',p)
   if digest(u)!=UNSIGNED or digest(p)!=PROD_HASH:raise Stop('Release byte digest mismatch')
   r['review_release']='LOCAL_PREPARATION_GITHUB_PUBLICATION_PENDING'
   if args.signed_apk:
    safe(args.signed_apk.expanduser());shutil.copyfile(args.signed_apk.expanduser(),c);r['signing_mode']='OWNER_SUPPLIED_SIGNED_APK'
   else:
    prior=CURRENT/ASSET;meta=CURRENT/'review-artifact.json';reuse=False
    prepared=CURRENT/'prepared-review-artifact.json'
    if prepared.is_file() and not prepared.is_symlink():
     try:
      pm=json.loads(prepared.read_text());ph=pm.get('sha256','')
      if pm.get('source')==SOURCE and re.fullmatch('[a-f0-9]{64}',ph):
       candidate_cache=STATE/'verified-review-apks'/SOURCE/(ph+'.apk');safe(candidate_cache)
       if candidate_cache.is_file() and digest(candidate_cache)==ph:prior=candidate_cache;meta=prepared
     except (ValueError,OSError):pass
    if prior.is_file() and meta.is_file() and not prior.is_symlink() and not meta.is_symlink():
     try:
      m=json.loads(meta.read_text());reuse=m.get('source')==SOURCE and m.get('sha256')==digest(prior)
     except (ValueError,OSError):reuse=False
    if reuse:shutil.copyfile(prior,c);r['signing_mode']='REUSED_LOCAL_RC9_PENDING_VERIFICATION'
    else:configured_sign(signer,align,u,c,env)
   if payload(u)!=payload(c):raise Stop('SIGNED_PAYLOAD_DIFFERS_FROM_BUILT_APK: no install')
   candidateCert=cert(c);referenceCert=cert(p)
   if referenceCert!={'40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c'}:raise Stop('PUBLIC_REFERENCE_CERTIFICATE_CHANGED: no install')
   if candidateCert!=referenceCert:raise Stop('RELEASE_SIGNER_MISMATCH: saved key differs from the published signing reference; no uninstall or key replacement')
   r['signing_certificate_sha256']=','.join(sorted(candidateCert))
   r['phase']='SIGNED_PAYLOAD_AND_CERTIFICATE_VERIFIED'
   print('3 / 5 · Signed APK payload and original certificate verified. Open local review first.',flush=True)
   badging=call([aapt,'dump','badging',c],env=env).stdout
   if not all(v in badging for v in ["name='in.ghartv.nova'","versionCode='26'","versionName='0.6.0-rc9-tv-first'"]):raise Stop('Wrong package or Android version')
   # Development checkout is intentionally untouched; verified artifact bytes are authoritative.
   h=digest(c);r['signed_apk_sha256']=h
   preserve_verified_signed(c,h)
   r['review_release']='SIGNED_LOCAL_VERIFIED_GITHUB_PUBLICATION_PENDING';r['phase']='EMULATOR_SELECTION'
   print('4 / 5 · Update and open the same named emulator; no physical-TV change.',flush=True)
   target=CURRENT/ASSET;safe(target)
   # Use the canonical corrected transport selector, NOT a parallel plain-bind opener.
   import importlib.util
   spec=importlib.util.spec_from_file_location('ghartv_review_transport',RUNTIME/'tools/tv_local.py')
   transport=importlib.util.module_from_spec(spec);spec.loader.exec_module(transport)
   if (transport.AVD,transport.SERIAL,transport.PACKAGE)!=(AVD,'emulator-5580',PACKAGE):raise Stop('CANONICAL_TRANSPORT_TARGET_MISMATCH')
   adb,started=attach_with_boot_evidence(transport,sdk)
   serial=transport.SERIAL;r['emulator_serial']=serial;r['started_existing_avd']=started
   codes=re.findall(r'versionCode=(\d+)',call([adb,'-s',serial,'shell','dumpsys','package',PACKAGE]).stdout)
   if codes and int(codes[0])>26:raise Stop('Newer version installed; no downgrade')
   def pull_installed(destination):
    paths=call([adb,'-s',serial,'shell','pm','path',PACKAGE]).stdout.splitlines()
    if len(paths)!=1 or not paths[0].startswith('package:'):raise Stop('Unexpected installed package layout')
    call([adb,'-s',serial,'pull',paths[0][8:].strip(),destination],60)
   if codes:
    old=t/'installed.apk';pull_installed(old)
    if cert(old)!=cert(c):raise Stop('Installed signer differs; no uninstall or storage clearing')
    if int(codes[0])==26 and digest(old)!=h:raise Stop('Different code-26 APK installed; reconcile first')
   if not codes or int(codes[0])<26:
    result=call([adb,'-s',serial,'install','-r',c],120).stdout
    if 'Success' not in result:raise Stop('Android did not confirm install')
   installed=t/'installed-final.apk';pull_installed(installed)
   if digest(installed)!=h:raise Stop('Installed byte verification failed')
   r['emulator']='RC9_INSTALLED_BYTES_VERIFIED';r['phase']='OPENING_REVIEW'
   call([adb,'-s',serial,'shell','input','keyevent','KEYCODE_WAKEUP'],check=False)
   call([adb,'-s',serial,'shell','am','force-stop',PACKAGE])
   out=call([adb,'-s',serial,'shell','am','start','-W','-n',PACKAGE+'/.MainActivity']).stdout
   if 'Status: ok' not in out:raise Stop('RC9_INSTALLED_LAUNCH_NOT_CONFIRMED: no data clear or downgrade attempted')
   foreground=False
   for _ in range(8):
    time.sleep(1)
    pid=call([adb,'-s',serial,'shell','pidof',PACKAGE],check=False).stdout.strip()
    activities=call([adb,'-s',serial,'shell','dumpsys','activity','activities'],check=False).stdout
    lines=[line for line in activities.splitlines() if ('topResumedActivity' in line or 'mResumedActivity' in line) and PACKAGE+'/' in line]
    if pid and lines:
     match=re.search(r'in\.ghartv\.nova/(?:in\.ghartv\.nova\.)?\.?(MainActivity|LoginActivity|PlayerActivity|MovieHubActivity|SplashActivity)',lines[0])
     r['review_screen']=match.group(1) if match else 'GHARTV_FOREGROUND';foreground=True;break
   if not foreground:raise Stop('RC9_INSTALLED_FOREGROUND_NOT_CONFIRMED: launch did not settle in GharTV; installed identity retained in this receipt')
   c.chmod(0o600);os.replace(c,target)
   write(CURRENT/'review-artifact.json',json.dumps({'source':SOURCE,'sha256':h,'unsigned_sha256':UNSIGNED,'version':VERSION},indent=2)+'\n')
   r['emulator']='RC9_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND';r['phase']='REVIEW_OPEN';r['status']='REVIEW_READY'
   print('RC9 is installed and GharTV is the foreground Android activity: '+r['review_screen'],flush=True)
   r['network_check']='NOT_RERUN_PERFORMANCE_REVIEW_USE_EXPLICIT_CONNECTION_CHECK'
   r['emulator_network_restart']='NOT_ATTEMPTED'
   r['provider_playback']='OWNER_PLAYBACK_REVIEW_REQUIRED'

   obsolete=CURRENT/'GharTV-review-unsigned.apk'
   if obsolete.is_file() and not obsolete.is_symlink() and digest(obsolete)==UNSIGNED:
    r['cleanup_bytes']+=obsolete.stat().st_size;obsolete.unlink();r['cleanup_count']+=1

def performance_snapshot(label):
 try:
  helper=RUNTIME/'tools/performance/host_check.py'
  target=RUN/('PERFORMANCE_'+label.upper()+'.json')
  p=call([sys.executable,helper,'--output',target],timeout=45,check=False)
  if p.returncode or not target.is_file():r['performance_'+label]='NOT_CAPTURED';return
  data=json.loads(target.read_text());r['performance_'+label]=data.get('status','UNKNOWN')
  r['host_memory_pressure_'+label]=data.get('memory_pressure','NOT_REPORTED')
  if label=='after':
   mkdir(STATE/'performance');write(STATE/'performance/latest.json',target.read_text())
  r['performance_private_report']=str(target)
 except Exception as e:r['performance_'+label]='NOT_CAPTURED_'+type(e).__name__

def cleanup():
 allowed={'GHARTV_RC6_REVIEW.zip':'4ae2585a64eaa0a589e20c99a1fe2059204ea2fda2dc791be0407a1b87b23d8b','GHARTV_CYAN_REVIEW_5.zip':'41daac68572e83561d7ef7dd4c0b503d890a5e8cec97979b176fa61d289516df','GHARTV_CYAN_REVIEW_4.zip':'9693852663be42086f7b869e3374a37d9f010dc23fcebaefb19724bc8dbd90fe','GHARTV_SYNC_CURRENT_AND_REPORT.command':'e797636a8718a67273ef68ce240c9d5e3bbe5f1201ea96b4864c70e0248654b5','GHARTV_RC5_SOURCE_HANDOFF.zip':'3968480bf420c9e9b4c16eee83a89e171dddf78c7d16eb4bd77031f0b789547d','GHARTV_CYAN_REVIEW_2.zip':'7d8906d5ba84be44bb1546f4f6887e5259f734b61eedb0201c99b263901443b8','GHARTV_RC5_EXACT_STABLE_R1.command':'3d3a6e0a69b0ba4ce105913d92801af4bec6cb895da37f91a5d9383ef5149901'}
 d=HOME/'Downloads'
 if not d.is_dir() or d.is_symlink():return
 for p in d.iterdir():
  for name,h in allowed.items():
   stem,ext=name.rsplit('.',1)
   if re.fullmatch(re.escape(stem)+r'(?: \(\d+\))?\.'+re.escape(ext),p.name) and p.is_file() and not p.is_symlink() and p!=SELF and digest(p)==h:
    r['cleanup_bytes']+=p.stat().st_size;p.unlink();r['cleanup_count']+=1
def local_json(url):
 with urllib.request.urlopen(url,timeout=3) as response:return json.loads(response.read(32768))

def stop_owned_web_if_needed():
 state=HOME/'Library/Application Support/GharTV/web-player';pidfile=state/'server.pid'
 try:health=local_json('http://127.0.0.1:8790/api/health')
 except Exception:
  occupied=call(['lsof','-nP','-iTCP:8790','-sTCP:LISTEN','-t'],check=False).stdout.strip()
  if occupied:raise Stop('WEB_PORT_8790_OWNERSHIP_UNVERIFIED: preserved existing listener')
  return
 if health.get('service')!='ghartv-web-player':raise Stop('PORT_8790_OTHER_SERVICE_PRESERVED')
 # All old temporary shares from this owner run were revoked. A new active preview
 # must be stopped explicitly before replacing its source.
 try:
  with urllib.request.urlopen('http://127.0.0.1:8790/owner.html',timeout=3) as response:page=response.read(250000).decode()
  match=re.search(r'id="owner-bootstrap" type="application/json">(.*?)</script>',page,re.S)
  if match:
   n=json.loads(match[1]).get('token','')
   req=urllib.request.Request('http://127.0.0.1:8790/owner-api/fabric/status',headers={'Authorization':'Bearer '+n})
   with urllib.request.urlopen(req,timeout=3) as response:preview=json.loads(response.read(32768))
   if preview.get('active'):raise Stop('ACTIVE_TEMPORARY_VIEWER_PRESERVED_REVOKE_BEFORE_REPLACEMENT')
 except Stop:raise
 except urllib.error.HTTPError as e:
  if e.code not in (404,):raise Stop('PREVIEW_ACTIVITY_NOT_VERIFIED_PRESERVED')
 except Exception:raise Stop('PREVIEW_ACTIVITY_NOT_VERIFIED_PRESERVED')

 if health.get('commit')==SOURCE:return
 if not pidfile.is_file() or pidfile.is_symlink():raise Stop('EXISTING_WEB_PID_NOT_OWNED: no process killed')
 value=pidfile.read_text().strip()
 if not value.isdigit():raise Stop('EXISTING_WEB_PID_INVALID')
 pid=int(value);cmd=call(['ps','-p',str(pid),'-o','command='],check=False).stdout
 valid_paths=(str(PROJECT/'web-player/server.mjs'),str(RUNTIME/'web-player/server.mjs'))
 if not any(p in cmd for p in valid_paths):raise Stop('EXISTING_WEB_PROCESS_NOT_OWNED')
 listening=call(['lsof','-nP','-iTCP:8790','-sTCP:LISTEN','-t'],check=False).stdout.split()
 if str(pid) not in listening:raise Stop('EXISTING_WEB_PORT_PID_MISMATCH')
 os.kill(pid,signal.SIGTERM)
 for _ in range(30):
  if not call(['ps','-p',str(pid),'-o','pid='],check=False).stdout.strip():break
  time.sleep(.1)
 else:raise Stop('OWNED_WEB_PROCESS_DID_NOT_STOP: no forced kill')

def inspect_collector():
 config=HOME/'Library/Application Support/GharTV/telemetry/collector.env'
 r['collector_config']='NOT_FOUND';r['collector_auth']='NOT_CHECKED'
 if not config.is_file() or config.is_symlink():return
 r['collector_config']='PRESENT';return

def open_dashboard():
 inspect_collector()
 if not RUNTIME.is_dir():raise Stop('Companion runtime not prepared')
 if not shutil.which('node'):r['web_player']='NODE_NOT_AVAILABLE';return
 try:health=local_json('http://127.0.0.1:8790/api/health')
 except Exception:health={}
 if health.get('commit')!=SOURCE:
  stop_owned_web_if_needed();state=HOME/'Library/Application Support/GharTV/web-player';mkdir(state)
  env=dict(os.environ,GHARTV_WEB_HOST='127.0.0.1',GHARTV_WEB_PORT='8790',GHARTV_WEB_SHA=SOURCE,TZ='Asia/Kolkata')
  with open(state/'server.log','a') as log:
   process=subprocess.Popen([shutil.which('node'),str(RUNTIME/'web-player/server.mjs')],stdin=subprocess.DEVNULL,stdout=log,stderr=log,env=env,start_new_session=True)
  write(state/'server.pid',str(process.pid)+'\n')
  for _ in range(25):
   time.sleep(.2)
   try:health=local_json('http://127.0.0.1:8790/api/health')
   except Exception:continue
   if health.get('commit')==SOURCE:break
  if health.get('commit')!=SOURCE:raise Stop('WEB_RUNTIME_DID_NOT_START: private server.log retained')
 r['web_player']='HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED'
 r['web_url']='http://127.0.0.1:8790/';r['owner_url']='http://127.0.0.1:8790/owner.html'
 call(['open',r['owner_url']],check=False);call(['open',r['web_url']],check=False)
 r['dashboard']='LOCAL_OWNER_READER_OPEN_REQUESTED'
 if r.get('prior_runtime_preserved'):r['old_managed_runtime_cleanup']='PRIOR_REVIEW_PRESERVED_UNTIL_OWNER_ACCEPTANCE'

def capture_support_once():
 try:
  base='http://127.0.0.1:8790'
  with urllib.request.urlopen(base+'/owner.html',timeout=5) as response:html=response.read(250000).decode()
  m=re.search(r'id="owner-bootstrap" type="application/json">(.*?)</script>',html,re.S)
  if not m:raise Stop('LOCAL_NONCE_NOT_FOUND')
  nonce=json.loads(m[1]).get('token','')
  if not re.fullmatch('[a-f0-9]{64}',nonce):raise Stop('LOCAL_NONCE_INVALID')
  req=urllib.request.Request(base+'/owner-api/support/capture',data=json.dumps({'days':7,'reference':''}).encode(),headers={'Authorization':'Bearer '+nonce,'Origin':base,'Content-Type':'application/json'})
  class NoRedirect(urllib.request.HTTPRedirectHandler):
   def redirect_request(self,*a,**k):return None
  with urllib.request.build_opener(NoRedirect()).open(req,timeout=65) as response:data=json.loads(response.read(250000))
  if not data.get('ok'):
   r['collector_capture']='INCOMPLETE';r['collector_read_steps']=[{'part':x.get('part'),'status':x.get('status')} for x in data.get('steps',[])];return
  r['collector_auth']='VERIFIED_BY_SUPPORT_READ';r['collector_capture']='SUMMARY_AND_BOUNDED_EXPORT_SAVED_PRIVATELY'
  write(RUN/'SUPPORT_SIGNALS.json',json.dumps(data['report'],indent=2)+'\n')
  r['support_capture_id']=data['capture_id']
  # A private diagnostic attachment, NEVER included in the public technical mirror.
  support=RUN/'GHARTV_SUPPORT.zip'
  with zipfile.ZipFile(support,'w',zipfile.ZIP_DEFLATED) as z:
   for name in ('SUPPORT_SIGNALS.json','NETWORK_CHECK.json'):
    if (RUN/name).is_file():z.write(RUN/name,name)
  support.chmod(0o600);r['private_support_report']=str(support)
 except Exception as e:r['collector_capture']='NOT_CONFIRMED_'+type(e).__name__

def collector_check_and_deploy():
 # Updating the already-configured collector is separate from promoting the TV APK.
 cfg=HOME/'Library/Application Support/GharTV/telemetry/collector.env';safe(cfg)
 if not cfg.is_file():r['backend']='EXISTING_COLLECTOR_CONFIG_MISSING';return
 st=cfg.stat()
 if st.st_uid!=os.getuid() or st.st_mode&0o077 or st.st_size>16384:r['backend']='PRIVATE_CONFIG_REQUIRED';return
 values={}
 for line in cfg.read_text().splitlines():
  line=line.strip().removeprefix('export ');key,sep,val=line.partition('=')
  if key in ('GHARTV_TELEMETRY_ENDPOINT','GHARTV_TELEMETRY_D1_ID','GHARTV_TELEMETRY_D1_NAME','GHARTV_TELEMETRY_WORKER','GHARTV_TELEMETRY_ADMIN_TOKEN') and sep:
   parts=shlex.split(val,comments=True)
   if len(parts)==1:values[key]=parts[0]
 if values.get('GHARTV_TELEMETRY_ENDPOINT','').rstrip('/')!=COLLECTOR:r['backend']='CONFIGURED_COLLECTOR_DIFFERS_PRESERVED';return
 class NoRedirect(urllib.request.HTTPRedirectHandler):
  def redirect_request(self,*a,**k):return None
 opener=urllib.request.build_opener(NoRedirect())
 def read(path,private=False):
  req=urllib.request.Request(COLLECTOR+path,headers={'Authorization':'Bearer '+values['GHARTV_TELEMETRY_ADMIN_TOKEN']} if private else {})
  with opener.open(req,timeout=12) as response:return json.loads(response.read(2*1024*1024))
 try:health=read('/health')
 except Exception:health={}
 if health.get('revision')!=BACKEND_VERSION and not args.skip_backend_deploy:
  db=values.get('GHARTV_TELEMETRY_D1_ID','')
  if not re.fullmatch('[a-f0-9-]{36}',db) or values.get('GHARTV_TELEMETRY_D1_NAME')!='ghartv-telemetry' or values.get('GHARTV_TELEMETRY_WORKER')!='ghartv-telemetry':r['backend']='EXISTING_BACKEND_IDENTITY_NOT_VERIFIED';return
  if not shutil.which('npx'):r['backend']='WRANGLER_RUNTIME_UNAVAILABLE';return
  worker=RUNTIME/'telemetry/worker';template=(worker/'wrangler.toml.template').read_text();config=worker/'wrangler.toml'
  write(config,template.replace('__D1_DATABASE_ID__',db))
  env=dict(os.environ,CI='true',WRANGLER_SEND_METRICS='false')
  prefix=['npx','--yes','wrangler@4.119.0','--config',str(config)]
  info=json.loads(call(prefix+['d1','info','ghartv-telemetry','--json'],90,env=env).stdout)
  if isinstance(info,dict) and isinstance(info.get('result'),dict):info=info['result']
  if not isinstance(info,dict) or (info.get('uuid') or info.get('id') or info.get('database_id'))!=db:raise Stop('D1_ACCOUNT_OR_DATABASE_MISMATCH: no deployment')
  r['backend']='APPLYING_ADDITIVE_SCHEMA_TO_EXISTING_DB'
  call(prefix+['d1','execute','ghartv-telemetry','--remote','--file',str(worker/'schema.sql'),'--yes'],90,env=env)
  call(prefix+['deploy','--keep-vars'],120,env=env)
  health=read('/health')
 if health.get('revision')!=BACKEND_VERSION:r['backend']='MATCHING_BACKEND_NOT_DEPLOYED';return
 summary=read('/v1/admin/summary?days=1',True);devices=read('/v1/admin/devices',True);commands=read('/v1/admin/commands',True)
 if not all(x.get('ok') is True for x in (summary,devices,commands)):raise Stop('COLLECTOR_AUTHENTICATED_READ_FAILED')
 r['backend']='REVISION_AND_AUTHENTICATED_READS_VERIFIED';r['collector_events_24h']=summary.get('totals',{}).get('events','UNAVAILABLE');r['collector_read_at_ist']=dt.datetime.now(IST).isoformat()
 values.clear()


note_text='';lock_acquired=False
try:
 for p in (STATE,CURRENT,RUN):mkdir(p)
 lockpath=STATE/'owner-run.lock';safe(lockpath);lock=open(lockpath,'w');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);lock_acquired=True
 r['network_recovery']=NETWORK_RECOVERY
 r['delivery_revision']='RC9-CONTINUATION-R2'
 r['control_launcher_sha256']=digest(SELF)
 resource_preflight()
 recovery_note();persist()
 r['phase']='CONTINUITY';print('1 / 5 · Reconcile current handoff and update existing Obsidian note.',flush=True)
 note_text=reconcile()
 try:note_sync(note_text)
 except Exception as e:r['obsidian']='FAILED_'+type(e).__name__
 persist()
 r['phase']='CHECKOUT_OBSERVATION_ONLY';sync_checkout()
 observe_production()
 r['status']='MEMORY_UPDATED' if r['obsidian']=='WRITTEN_AND_READBACK_VERIFIED' else 'CONTINUITY_REQUIRES_ATTENTION'
 if not args.memory_only:
  performance_snapshot('before');review()
  if r['status']=='REVIEW_READY':cleanup()
except Exception as e:
 r['status']='ACTION_REQUIRED';r['blocker']=str(e) if isinstance(e,Stop) else type(e).__name__
finally:
 try:
  if lock_acquired:
   persist()
   try:
    if note_text:
     open_dashboard();performance_snapshot('after');capture_support_once()
   except Exception as e:r['dashboard']='OPEN_FAILED_'+(str(e) if isinstance(e,Stop) else type(e).__name__)
   try:
    if args.bundled_review:r['backend']='NOT_CHECKED_BUNDLED_REVIEW_NO_DEPLOYMENT'
    elif note_text and not args.memory_only:collector_check_and_deploy()
   except Exception as e:r['backend']='ATTENTION_'+(str(e) if isinstance(e,Stop) else type(e).__name__)
   r['performance_basis']='LOCAL_SNAPSHOT_NOT_PROOF_OF_PLAYBACK_SPEEDUP'
   publish_review_after_local_success()
   print('5 / 5 · Save exact outcome and current memory; no secrets in handoff.',flush=True)
   if note_text:
    try:note_sync(note_text)
    except Exception as e:r['obsidian']='FAILED_'+type(e).__name__
   else:recovery_note()
   persist()
   bridge_once()
   persist()
   mirror_receipt()
   if note_text:
    try:note_sync(note_text)
    except Exception:r['obsidian']='FINAL_NOTE_UPDATE_FAILED'
   else:recovery_note()
   persist()
 except Exception as e:print('Continuity receipt issue: '+type(e).__name__)
 if lock_acquired:
  fcntl.flock(lock,fcntl.LOCK_UN);lock.close()
 print('\n'+receipt())
 if not args.noninteractive:
  try:
   with io.TextIOWrapper(io.FileIO('/dev/tty','r+'), encoding='utf-8', write_through=True) as tty:
    tty.write('\nPress Enter to copy this handoff: ');tty.flush();tty.readline()
    copied=subprocess.run(['pbcopy'],input=receipt(),text=True,check=False)
    tty.write(('Copied.' if copied.returncode==0 else 'Copy failed; handoff saved in current/handoff.txt.')+' Press Enter to finish this terminal: ');tty.flush();tty.readline()
    mark=Path(os.environ['GHARTV_CLOSE_MARKER'])
    with open(mark,'x') as f:f.write('owner-confirmed\n')
  except OSError:pass
sys.exit(1 if r['status']=='ACTION_REQUIRED' else 0)
PY
RESULT=$?
if [ -f "$CLOSE_MARKER" ] && [ ! -L "$CLOSE_MARKER" ] && [ "${TERM_PROGRAM:-}" = Apple_Terminal ] && [ "$0" = "$HOME/.local/share/ghartv-launcher/current/GHARTV_SYNC_CURRENT_AND_REPORT.command" ]; then
 TARGET_TTY="$(tty 2>/dev/null || true)"
 if [[ "$TARGET_TTY" == /dev/ttys* ]]; then
  (sleep 1; osascript - "$TARGET_TTY" <<'APPLESCRIPT'
on run argv
 tell application "Terminal"
  repeat with w in windows
   if (count tabs of w) is 1 then
    try
     if tty of selected tab of w is item 1 of argv then
      close w
      return
     end if
    end try
   end if
  end repeat
 end tell
end run
APPLESCRIPT
  ) </dev/null >/dev/null 2>&1 &
 fi
fi
if [ -f "$CLOSE_MARKER" ] && [ ! -L "$CLOSE_MARKER" ]; then rm -f "$CLOSE_MARKER"; fi
exit "$RESULT"
