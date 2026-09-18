"""Focused promotion guards with private temporary fixtures. No external API, real release or key."""
import base64, importlib.util, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('release',Path(__file__).with_name('release_control.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
SRC='a'*40; H='b'*64
R={'run_id':'GHARTV-CYAN-10-20260916T000000Z-10','review_source':SRC,'signed_apk_sha256':H,'unsigned_apk_sha256':'c'*64}
BODY={'run_id':R['run_id'],'source':SRC,'sha256':H,'reviewed':{'tv':True,'web':True,'owner':True},'confirmation':'PUBLISH EXACT CODE 26','scope':'ANDROID_UPDATE_AND_DOWNLOAD_FEED_ONLY'}
BLUE={'versionCode':17,'versionName':'0.5.4-rc8','sourceCommit':'d'*40,'sha256':'e'*64,'apkUrl':'https://github.com/AmritSinghGit/ghartv/releases/download/v0.5.4-rc8/old.apk'}
class Guards(unittest.TestCase):
 def test_all_approval_fields_required(self):
  m.validate_approval(BODY,R)
  for key in BODY:
   bad=dict(BODY);bad.pop(key)
   with self.assertRaises(m.Hold):m.validate_approval(bad,R)
 def test_foreign_source_or_unsigned_not_approval(self):
  for key,value in [('sha256','c'*64),('source','d'*40),('reviewed',{'tv':True,'web':False,'owner':True})]:
   with self.assertRaises(m.Hold):m.validate_approval({**BODY,key:value},R)
 def test_feed_url_and_ids_constrained(self):
  m.safe_blue(BLUE)
  for key,value in [('apkUrl','https://evil.invalid/file.apk'),('sourceCommit','unknown'),('sha256','none')]:
   with self.assertRaises(m.Hold):m.safe_blue({**BLUE,key:value})
 def test_no_write_before_approval(self):
  with patch.object(m,'identity',return_value=R),patch.object(m,'verify_green') as check,patch.object(m,'github') as gh:
   with self.assertRaises(m.Hold):m.publish(SRC,{})
   check.assert_not_called();gh.assert_not_called()
 def run_flow(self,drift=False,newer=False,conflict=False):
  calls=[];feed=dict(BLUE);feed['versionCode']=27 if newer else 24;blob='f'*40;reads=0;uploaded=False
  def github(path,method='GET',body=None):
   nonlocal feed,blob,reads,uploaded
   calls.append((method,path,body))
   if 'contents/update/latest.json' in path:
    if method=='PUT':
     self.assertEqual(body['sha'],blob);self.assertEqual(body['branch'],'main');feed=json.loads(base64.b64decode(body['content']));blob='1'*40
     return {'commit':{'sha':'2'*40}}
    reads+=1
    if drift and reads==2:blob='3'*40
    return {'sha':blob,'encoding':'base64','content':base64.b64encode(json.dumps(feed).encode()).decode()}
   assets=[{'name':'GharTV-review-unsigned.apk','digest':'sha256:'+R['unsigned_apk_sha256']}]
   if uploaded or conflict:assets.append({'name':m.ASSET,'digest':'sha256:'+('0'*64 if conflict else H)})
   return {'target_commitish':SRC,'draft':False,'assets':assets}
  def command(args,timeout=25,input_data=None):
   nonlocal uploaded
   if args[:3]==['gh','release','upload']:uploaded=True
   if args[:3]==['gh','release','download']:(Path(args[-1])/m.ASSET).write_bytes(b'fixture')
   return '{}'
  with tempfile.TemporaryDirectory() as td:
   root=Path(td);current=root/'current';current.mkdir();(current/m.ASSET).write_bytes(b'fixture');release=root/'release';release.mkdir()
   with patch.object(m,'CURRENT',current),patch.object(m,'RELEASE',release),patch.object(m,'HOME',root),patch.object(m,'identity',return_value=R),patch.object(m,'verify_green',return_value=R),patch.object(m,'digest',return_value=H),patch.object(m,'github',side_effect=github),patch.object(m,'command',side_effect=command):
    if drift or newer or conflict:
     with self.assertRaises(m.Hold):m.publish(SRC,BODY)
     self.assertFalse(any(x[0]=='PUT' for x in calls))
    else:
     with patch.object(m,'snapshot',return_value={'ok':True}):result=m.publish(SRC,BODY)
     self.assertEqual(result['publication']['state'],'PUBLIC_UPDATE_VERIFIED');self.assertEqual(feed['sha256'],H);self.assertEqual(feed['sourceCommit'],SRC);self.assertEqual(feed['versionCode'],26)
 def test_exact_publish_and_readback(self):self.run_flow()
 def test_advanced_public_preserved(self):self.run_flow(newer=True)
 def test_racing_feed_preserved(self):self.run_flow(drift=True)
 def test_conflicting_signed_asset_preserved(self):self.run_flow(conflict=True)
if __name__=='__main__':unittest.main()
