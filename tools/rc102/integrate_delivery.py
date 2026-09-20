"""Guarded integration into the existing launcher. No owner resources are changed here."""
from pathlib import Path
R=Path(__file__).resolve().parents[2]
def once(s,a,b):
 if s.count(a)!=1:raise ValueError('ANCHOR_CHANGED: '+a[:100])
 return s.replace(a,b)
p=R/'tools/owner_review.command.in';s=p.read_text()
s=once(s,"REPO='AmritSinghGit/ghartv'; SOURCE='@SOURCE_SHA@'","REPO='AmritSinghGit/ghartv'; SOURCE='@SOURCE_SHA@'; WEB_SOURCE='@WEB_SOURCE_SHA@'")
s=s.replace("TAG='v0.6.0-rc10.1'","TAG='v0.6.0-rc10.2-web-security'").replace('CYAN REVIEW 15','CYAN REVIEW 16').replace('CYAN-15-','CYAN-16-').replace('GHARTV_CYAN_REVIEW_15_HANDOFF','GHARTV_CYAN_REVIEW_16_HANDOFF').replace('CYAN15-WEB-FILMS','CYAN16-VIEWER-SECURITY')
s=s.replace('0.6.0 RC10 · web first · independent FlixMomo · original TV contract retained · artifact review · development checkout preserved','RC10.2 browser review · same signed Android code28 · private analytics removed')
s=once(s,'args=parser.parse_args(sys.argv[2:])',"parser.add_argument('--copy-handoff',action='store_true');args=parser.parse_args(sys.argv[2:])")
s=once(s,'review_source=SOURCE,version=VERSION',"review_source=SOURCE,web_source=WEB_SOURCE,delivery_revision='RC10.2-WEB-SECURITY',analytics_routes='DISABLED_IN_VIEWER',version=VERSION")
a=s.index('def reconcile():');b=s.index('\ndef sync_checkout',a);part=s[a:b]
part=part.replace("if manifest.get('source_sha')!=SOURCE","if manifest.get('web_source_sha')!=WEB_SOURCE or manifest.get('source_sha')!=SOURCE").replace(".get('source')==SOURCE",".get('source')==WEB_SOURCE").replace("{'source':SOURCE,'owner':","{'source':WEB_SOURCE,'application_source':SOURCE,'owner':").replace("r['delivery_sha']=SOURCE","r['delivery_sha']=WEB_SOURCE")
s=s[:a]+part+s[b:]
a=s.index('def stop_owned_web_if_needed():');b=s.index('\ndef inspect_collector',a);part=s[a:b]
part=part.replace("health.get('commit')==SOURCE","health.get('commit')==WEB_SOURCE")
part=once(part," try:\n  with urllib.request.urlopen('http://127.0.0.1:8790/owner.html'"," if int(health.get('active_previews',0))>0:raise Stop('ACTIVE_TEMPORARY_VIEWER_PRESERVED_REVOKE_BEFORE_REPLACEMENT')\n try:\n  with urllib.request.urlopen('http://127.0.0.1:8790/owner.html'")
s=s[:a]+part+s[b:]
a=s.index('def open_dashboard():');b=s.index('\ndef capture_support_once',a);part=s[a:b]
part=part.replace(' inspect_collector()'," r['collector_config']='NOT_READ_BY_VIEWER';r['collector_auth']='NOT_CHECKED'").replace("health.get('commit')!=SOURCE","health.get('commit')!=WEB_SOURCE").replace("health.get('commit')==SOURCE","health.get('commit')==WEB_SOURCE").replace('GHARTV_WEB_SHA=SOURCE','GHARTV_WEB_SHA=WEB_SOURCE').replace("r['owner_url']='http://127.0.0.1:8790/owner.html'","r['owner_url']='REMOVED_USE_OPERON_ANALYTICS'").replace("call(['open',r['owner_url']],check=False);",'').replace("r['dashboard']='LOCAL_OWNER_READER_OPEN_REQUESTED'","r['dashboard']='VIEWER_OPEN_REQUESTED_ANALYTICS_REMOVED'")
s=s[:a]+part+s[b:]
s=s.replace("if r.get('dashboard')!='LOCAL_OWNER_READER_OPEN_REQUESTED'","if r.get('dashboard')!='VIEWER_OPEN_REQUESTED_ANALYTICS_REMOVED'")
s=s.replace("if info.get('target_commitish')!=SOURCE or","if info.get('target_commitish')!=WEB_SOURCE or")
s=once(s,'   resource_preflight()',"""   try:resource_preflight()
   except Stop as error:
    if not str(error).startswith('HOST_PRESSURE_'):raise
    r['status']='WEB_REVIEW_READY_ANDROID_HELD';r['blocker']=str(error)
    r['next_action']='REVIEW_WEB_OR_PREPARED_APK_WITHOUT_NEW_EMULATOR'
    call(['open','-R',r['prepared_signed_apk_path']],check=False)
    print('Web review is ready. Android APK is signed and preserved; no new emulator was started under memory pressure.',flush=True)
    return""")
s=once(s,'def bridge_once():',r'''def light_handoff():
 keep=('run_id','review_source','web_source','version','version_code','status','phase','web_player','emulator','signed_apk_sha256','prepared_signed_apk','blocker','next_action','obsidian','receipt_sync','receipt_url')
 data={key:r[key] for key in keep if key in r}
 path=CURRENT/'light-handoff.md'
 write(path,'# GharTV current run\n\nRead the live GitHub PR1 managed receipt before resuming. This local snapshot is not production approval.\n\n```json\n'+json.dumps(data,indent=2)+'\n```\n')
 return path

def bridge_once():''')
s=s.replace("[path,'handoff','--file',r['obsidian_note']]","[path,'handoff','--file',str(light_handoff())]").replace('process.wait(timeout=10)','process.wait(timeout=25)').replace('   mirror_receipt()','   mirror_receipt()\n   light_handoff()')
a=s.index(" print('\\n'+receipt())");b=s.index("sys.exit(1 if r['status']=='ACTION_REQUIRED' else 0)",a)
s=s[:a]+r''' print('\nGharTV review result: '+r['status'])
 print('Web: '+r.get('web_url','NOT_STARTED'))
 print('Android: '+r.get('emulator','UNCHANGED'))
 if r.get('prepared_signed_apk_path'):print('Signed APK: '+r['prepared_signed_apk_path'])
 if r.get('blocker'):print('Android / startup note: '+str(r['blocker']))
 print('Continuity: '+r.get('receipt_sync','NOT_CONFIRMED')+' · Obsidian: '+r.get('obsidian','NOT_WRITTEN'))
 if r.get('receipt_url'):print('Receipt: '+r['receipt_url'])
 if r.get('receipt_sync')=='GITHUB_READBACK_VERIFIED':print('No handoff paste is needed. The next GharTV chat can read the mirrored receipt.')
 else:print('Remote receipt sync is pending; local receipt is saved in '+str(CURRENT/'light-handoff.md'))
 if args.copy_handoff:subprocess.run(['pbcopy'],input=receipt(),text=True,check=False)
'''+s[b:]
p.write_text(s)
p=R/'web-player/review-sync.mjs';s=p.read_text()
s=once(s,"result:r.status==='REVIEW_READY'?'REVIEW_READY':r.status==='ACTION_REQUIRED'?'ACTION_REQUIRED':'OTHER_LOCAL_STATE',",r"""result:['REVIEW_READY','ACTION_REQUIRED','WEB_REVIEW_READY_ANDROID_HELD','WEB_REVIEW_READY_ANDROID_NOT_TOUCHED','SIGNED_UPDATE_PREPARED_REVIEW_PENDING'].includes(r.status)?r.status:'OTHER_LOCAL_STATE',
    phase:['RESOURCE_PREFLIGHT','VERIFY_APK_CERTIFICATE','EMULATOR_SELECTION','REVIEW_OPEN','SIGNED_UPDATE_READY','CHECKOUT_OBSERVATION_ONLY','CONTINUITY','STARTING'].includes(r.phase)?r.phase:'OTHER_PHASE',
    blocker_code:typeof r.blocker==='string'&&/^HOST_PRESSURE_(WARNING|CRITICAL|NOT_REPORTED)_NO_NEW_EMULATOR:/.test(r.blocker)?r.blocker.split(':',1)[0]:r.blocker?'OTHER_BLOCKER_SEE_PRIVATE_RECEIPT':null,
    web_source:/^[a-f0-9]{40}$/.test(r.web_source||'')?r.web_source:null,
    web_ready:r.web_player==='HEALTH_AND_SOURCE_VERIFIED_PROVIDER_PLAYBACK_UNVERIFIED',
    signed_apk_prepared:r.prepared_signed_apk==='PERSISTED_AND_HASH_VERIFIED_BEFORE_EMULATOR_SELECTION',
    viewer_analytics:r.analytics_routes==='DISABLED_IN_VIEWER'?'DISABLED_IN_VIEWER':'NOT_CHECKED',
    next_action:r.status==='WEB_REVIEW_READY_ANDROID_HELD'?'REVIEW_WEB_OR_SIGNED_APK_NO_NEW_EMULATOR':'READ_CURRENT_PHASE_AND_OWNER_FEEDBACK',""")
s=s.replace("performance_report_saved:r.performance_after==='MEASURED_LOCALLY'","performance_report_saved:r.performance_before==='MEASURED_LOCALLY'||r.performance_after==='MEASURED_LOCALLY'");p.write_text(s)
p=R/'tools/run_owner_bundle.command.in';s=p.read_text()
s=s.replace('GharTV RC10.1 startup repair R2. Same APK, same companion, no public rollout.','GharTV RC10.2 browser delivery. Same Android APK, new web companion, no public rollout.').replace('GharTV Nova RC10.1 · startup repair R2','GharTV Nova RC10.2 · browser and privacy review')
a=s.index(" if '--noninteractive' not in sys.argv[2:]:");b=s.index('\n sys.exit(1)',a)
s=s[:a]+" if '--copy-handoff' in sys.argv[2:]:subprocess.run(['pbcopy'],input=text,text=True,check=False)"+s[b:];p.write_text(s)
p=R/'tools/tv-first/check_contract.py';s=p.read_text().replace('GHARTV_CYAN_REVIEW_15_HANDOFF','GHARTV_CYAN_REVIEW_16_HANDOFF').replace('GHARTV_CYAN_REVIEW_14_HANDOFF','GHARTV_CYAN_REVIEW_15_HANDOFF').replace('REVIEW15_LABEL','REVIEW16_LABEL');p.write_text(s)
p=R/'AGENTS.md';s=p.read_text();p.write_text('''# First read for every GharTV continuation

Fetch https://api.github.com/repos/AmritSinghGit/ghartv/issues/comments/5687119492 and verify the GHARTV_SAFE_RECEIPT_V1 marker, exact run and hashes. Read actual PR1 head, release manifest and production feed separately. Do not ask Amrit to paste a handoff that connected GitHub can read. Use existing amrit-memory bootstrap when available; a bridge timeout is not a verified replica. Never claim automatic knowledge of every arbitrary future chat.

The September19 owner run signed code28 successfully. Android did not install because memory was WARNING at RESOURCE_PREFLIGHT. The public viewer homepage is approved: preserve it. Analytics belongs to the existing Operon Analytics GharTV tenant/PR61, not the viewer. New web source and unchanged Android source must remain distinct. No duplicate database, signing key, worktree, emulator or runtime controller.

'''+s)
print('RC102_CONTINUITY_AND_SEPARATE_IDENTITIES_INTEGRATED')
