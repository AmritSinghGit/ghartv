"""Focused safety checks; no real Docker, Mac services, keys or provider requests."""
import ast, contextlib, copy, hashlib, importlib.util, io, json, pathlib, tempfile, types, unittest
from unittest.mock import patch
ROOT=pathlib.Path(__file__).parent

def load(name,file):
 s=importlib.util.spec_from_file_location(name,ROOT/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
r=load('resources','resource_transition.py');b=load('builder','build_rc9_r2.py')
def row(i,project,role):
 return {'id':str(i).zfill(64),'name':project+'-'+role+'-1','project':project,'service':role,'image':'sha256:'+str(i).zfill(64),
   'created':'creation','started':'start','config_hash':'abc','running':True,'mounts':[],'restart':{'Name':'unless-stopped'},'networks':{project:'net'+project}}
def snapshot():
 rows=[row(i+1,r.KEEP,s) for i,s in enumerate(['frontend','backend','postgres'])]
 i=10
 for p in sorted(r.OLD):
  for s in ['frontend','backend','postgres']:rows.append(row(i,p,s));i+=1
 rows.append(row(70,'unresolved-supabase','api'))
 return {'engine':{'context':'desktop-linux','endpoint':'unix://local','engine_id':'E1'},'containers':rows}
class Checks(unittest.TestCase):
 def test_old_apps_only(self):
  s=snapshot();targets,reason=r.proposal(s);self.assertEqual(len(targets),4)
  self.assertEqual({t['service'] for t in targets},r.ROLES);self.assertNotIn(r.KEEP,{t['project'] for t in targets})
 def test_current_missing_holds(self):
  s=snapshot();s['containers']=s['containers'][1:];self.assertEqual(r.proposal(s)[0],[])
 def test_cross_project_dependency_holds(self):
  s=snapshot();p=sorted(r.OLD)[0];s['containers'][-1]['networks'][p]='net'+p
  self.assertEqual(len(r.proposal(s)[0]),2)
 def test_no_guessing_from_names(self):
  s=snapshot()
  for t in s['containers']:
   if t['project'] in r.OLD:t['project']=None
  self.assertEqual(r.proposal(s)[0],[])
 def test_missing_config_fingerprint_holds(self):
  s=snapshot()
  for t in s['containers']:
   if t['project'] in r.OLD:t['config_hash']=None
  self.assertEqual(r.proposal(s)[0],[])
 def test_stop_requires_confirmation(self):
  with self.assertRaisesRegex(r.Hold,'CONFIRMATION'):r.suspend(snapshot(),[],pathlib.Path('/unused'),'')
 def test_changed_engine_blocks(self):
  with patch.object(r,'engine',return_value={'engine_id':'E2'}),self.assertRaisesRegex(r.Hold,'ENGINE_CHANGED'):
   r.suspend(snapshot(),[],pathlib.Path('/unused'),'STOP OLD IDENTIFLOW APPS')
 def test_exact_stop_no_force_timeout(self):
  s=snapshot();targets=r.proposal(s)[0];live={t['id']:copy.deepcopy(t) for t in s['containers']};calls=[]
  def invoke(args,timeout=20):
   calls.append(args);self.assertEqual(args[-3:-1],['--timeout','-1']);live[args[-1]]['running']=False;return {'code':0,'out':''}
  with tempfile.TemporaryDirectory() as d,patch.object(r,'engine',return_value=s['engine']),patch.object(r,'census',return_value=s),patch.object(r,'inspect',side_effect=lambda ids:[copy.deepcopy(live[i]) for i in ids]),patch.object(r,'run',side_effect=invoke):
   out=r.suspend(s,targets,pathlib.Path(d),'STOP OLD IDENTIFLOW APPS')
   self.assertEqual(len(calls),4);self.assertTrue(all(a['status']=='STOPPED_VERIFIED' for a in out['actions']))
   self.assertTrue(all(t['running'] for t in live.values() if t['service']=='postgres'))
 def test_changed_image_blocks_all(self):
  s=snapshot();targets=r.proposal(s)[0];fresh=copy.deepcopy(s);fresh['containers'][3]['image']='different'
  with tempfile.TemporaryDirectory() as d,patch.object(r,'engine',return_value=s['engine']),patch.object(r,'census',return_value=fresh),patch.object(r,'run') as cmd:
   with self.assertRaisesRegex(r.Hold,'CONTAINER_CHANGED'):r.suspend(s,targets,pathlib.Path(d),'STOP OLD IDENTIFLOW APPS')
   cmd.assert_not_called()
 def test_pending_stop_not_success_or_escalation(self):
  s=snapshot();targets=r.proposal(s)[0]
  with tempfile.TemporaryDirectory() as d,patch.object(r,'engine',return_value=s['engine']),patch.object(r,'census',return_value=s),patch.object(r,'inspect',return_value=[targets[0]]),patch.object(r,'run',return_value={'code':None,'error':'TIMEOUT'}) as cmd:
   out=r.suspend(s,targets,pathlib.Path(d),'STOP OLD IDENTIFLOW APPS');self.assertEqual(cmd.call_count,1);self.assertEqual(out['actions'][0]['status'],'STILL_RUNNING_OR_STOP_PENDING')
 def test_private_inspection_excludes_environment(self):
  self.assertNotIn('.Config.Env',r.FMT);self.assertNotIn('.Config.Cmd',r.FMT);self.assertNotIn('json .Config.Labels',r.FMT)
 def test_nonmac_no_action(self):
  with patch.object(r.sys,'platform','linux'),patch.object(r.sys,'argv',['script']),patch.object(r,'census') as cmd,contextlib.redirect_stdout(io.StringIO()):
   self.assertEqual(r.main(),2);cmd.assert_not_called()
 def test_other_old_project_worker_holds_pair(self):
  s=snapshot();s['containers'].append(row(90,sorted(r.OLD)[0],'worker'))
  self.assertEqual(len(r.proposal(s)[0]),2)
 def test_restore_only_exact_saved_stopped_apps(self):
  s=snapshot();targets=r.proposal(s)[0];live={t['id']:copy.deepcopy(t) for t in targets};calls=[]
  for v in live.values():v['running']=False
  record={'schema':'operon.reversible-app-suspension.v1','engine':s['engine'],'targets':[r.app_identity(t) for t in targets],
   'actions':[{'id':t['id'],'status':'STOPPED_VERIFIED'} for t in targets]}
  def start(*a,**kw):
   calls.append(a);self.assertEqual(a[0],'start');live[a[1]]['running']=True;return a[1]
  with tempfile.TemporaryDirectory() as d,patch.object(r,'engine',return_value=s['engine']),patch.object(r,'inspect',side_effect=lambda ids:[live[i] for i in ids]),patch.object(r,'docker',side_effect=start),contextlib.redirect_stdout(io.StringIO()):
   path=pathlib.Path(d)/'RESTORE.json';path.write_text(json.dumps(record));r.restore(path)
   self.assertEqual(len(calls),4);self.assertTrue(all(v['running'] for v in live.values()))
 def test_changed_restore_image_preserved(self):
  s=snapshot();t=r.proposal(s)[0][0];record={'schema':'operon.reversible-app-suspension.v1','engine':s['engine'],
   'targets':[r.app_identity(t)],'actions':[{'id':t['id'],'status':'STOPPED_VERIFIED'}]};live=copy.deepcopy(t);live['image']='other';live['running']=False
  with tempfile.TemporaryDirectory() as d,patch.object(r,'engine',return_value=s['engine']),patch.object(r,'inspect',return_value=[live]),patch.object(r,'docker') as start:
   path=pathlib.Path(d)/'RESTORE.json';path.write_text(json.dumps(record))
   with self.assertRaisesRegex(r.Hold,'RESTORE_TARGET_CHANGED'):r.restore(path)
   start.assert_not_called()
 def test_mirror_preserves_different_existing_note(self):
  with tempfile.TemporaryDirectory() as d,patch.object(r,'HOME',pathlib.Path(d)):
   folder=pathlib.Path(d)/'run';folder.mkdir();vault=pathlib.Path(d)/'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv';vault.mkdir(parents=True)
   old=vault/'GHARTV_SUCCESSOR_HANDOFF.md';old.write_text('owner newer note')
   self.assertEqual(r.mirror(folder,{'fixture':True}),'FILES_WRITTEN_AND_READBACK_VERIFIED')
   self.assertEqual(old.read_text(),'owner newer note');self.assertTrue((vault/'run-GHARTV_SUCCESSOR_HANDOFF.md').is_file())
 def namespace(self,folder):
  class Stop(RuntimeError):pass
  ns={'Path':pathlib.Path,'STATE':pathlib.Path(folder),'CURRENT':pathlib.Path(folder)/'current','SOURCE':'a'*40,'RUN':pathlib.Path(folder)/'run','r':{},'os':__import__('os'),'re':__import__('re'),'json':json,'shutil':__import__('shutil'),'Stop':Stop,'persist':lambda:None,'print':lambda *a,**k:None,'subprocess':__import__('subprocess')}
  ns['CURRENT'].mkdir();ns['RUN'].mkdir();ns['mkdir']=lambda p:p.mkdir(parents=True,exist_ok=True)
  ns['safe']=lambda p:None;ns['digest']=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();ns['write']=lambda p,s:p.write_text(s)
  exec(b.NEW_FUNCTIONS,ns);return ns
 def test_signed_artifact_survives_and_parses(self):
  with tempfile.TemporaryDirectory() as d:
   n=self.namespace(d);p=pathlib.Path(d)/'signed';p.write_bytes(b'fixture');h=n['digest'](p)
   n['preserve_verified_signed'](p,h);p.unlink()
   meta=json.loads((n['CURRENT']/'prepared-review-artifact.json').read_text());self.assertFalse(meta['installed']);self.assertEqual(meta['sha256'],h)
   self.assertEqual(pathlib.Path(n['r']['prepared_signed_apk_path']).read_bytes(),b'fixture')
 def test_specific_hold_preserved_no_extra_wait(self):
  with tempfile.TemporaryDirectory() as d:
   n=self.namespace(d)
   class H(Exception):pass
   class Transport:
    Hold=H
    def attach_or_start(self,*a):raise H('OTHER_LISTENER_PRESERVED')
   with self.assertRaisesRegex(n['Stop'],'OTHER_LISTENER_PRESERVED'):n['attach_with_boot_evidence'](Transport(),pathlib.Path(d))
 def test_extended_boot_observes_without_restart(self):
  with tempfile.TemporaryDirectory() as d:
   n=self.namespace(d);n['time']=types.SimpleNamespace(monotonic=lambda:10,sleep=lambda x:None);n['AVD']='EXACT'
   n['write'](n['RUN']/'PORT_CHECK.json',json.dumps({'observations':[{'stage':'STARTED_EXISTING_AVD'}]}))
   n['call']=lambda args,*a:types.SimpleNamespace(stdout='EXACT\nOK' if args[-1]=='name' else '1')
   class H(Exception):pass
   class Transport:
    Hold=H
    calls=0
    def attach_or_start(self,*a):self.calls+=1;raise H('EMULATOR_BOOT_TIMEOUT_PROCESS_PRESERVED')
   t=Transport();_,started=n['attach_with_boot_evidence'](t,pathlib.Path(d));self.assertTrue(started);self.assertEqual(t.calls,1)
 def test_corrupt_original_refused(self):
  with self.assertRaises(ValueError):b.patched_launcher(b'not the verified launcher')
if __name__=='__main__':unittest.main()
