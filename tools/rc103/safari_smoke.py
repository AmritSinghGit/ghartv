"""Native Safari local UI/test-media checks. No request is sent to the movie provider."""
from pathlib import Path
import base64,functools,http.server,json,subprocess,sys,threading,time,urllib.error,urllib.request
OUT=Path(sys.argv[1]);FIXTURE=Path(sys.argv[2]);OUT.mkdir(parents=True,exist_ok=True)
BASE='http://127.0.0.1:8790';DRIVER='http://127.0.0.1:9515'
record={'browser':'Safari','native_safari':True,'status':'STARTING','live_provider_playback':False,'live_provider_search':False,'owner_mac':False}
class Handler(http.server.SimpleHTTPRequestHandler):
 extensions_map={**http.server.SimpleHTTPRequestHandler.extensions_map,'.m3u8':'application/vnd.apple.mpegurl','.ts':'video/mp2t'}
 def log_message(self,*args):pass
httpd=http.server.ThreadingHTTPServer(('127.0.0.1',8801),functools.partial(Handler,directory=str(FIXTURE)))
threading.Thread(target=httpd.serve_forever,daemon=True).start();proc=None;sid=None

def command(method,path,data=None):
 request=urllib.request.Request(DRIVER+path,data=json.dumps(data).encode() if data is not None else None,method=method,headers={'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(request,timeout=35) as response:value=json.load(response)
 except urllib.error.HTTPError as error:raise RuntimeError(error.read().decode()[:1000]) from None
 result=value.get('value',value)
 if isinstance(result,dict) and result.get('error'):raise RuntimeError(str(result)[:1000])
 return result

def action(method,path,data=None):return command(method,'/session/'+sid+path,data)
def script(text):return action('POST','/execute/sync',{'script':text,'args':[]})
def wait_for(test,seconds=18):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  if test():return
  time.sleep(.25)
 raise AssertionError('Condition not met during '+record.get('stage','test'))
def click(selector):
 element=action('POST','/element',{'using':'css selector','value':selector})
 ref=element.get('element-6066-11e4-a52e-4f735466cecf') or element.get('ELEMENT')
 action('POST','/element/'+ref+'/click',{})
def shot(name):(OUT/name).write_bytes(base64.b64decode(action('GET','/screenshot')))
try:
 record['stage']='ENABLE_NATIVE_DRIVER'
 enabled=subprocess.run(['sudo','-n','/usr/bin/safaridriver','--enable'],capture_output=True,text=True,timeout=20)
 if enabled.returncode:raise RuntimeError('SAFARI_AUTOMATION_ENABLE_FAILED')
 proc=subprocess.Popen(['/usr/bin/safaridriver','--port','9515'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 for _ in range(30):
  try:command('GET','/status');break
  except Exception:time.sleep(.2)
 session=command('POST','/session',{'capabilities':{'alwaysMatch':{'browserName':'safari'}}});sid=session['sessionId']
 record['browser_version']=session.get('capabilities',{}).get('browserVersion')
 action('POST','/timeouts',{'implicit':0,'pageLoad':25000,'script':15000})
 record['stage']='VIEWER_UI';action('POST','/url',{'url':BASE+'/'})
 wait_for(lambda:script("return typeof window.GharTVPlayback==='object' && document.getElementById('accountButton')!==null"))
 assert script("return !document.querySelector('a[href*=\"owner.html\"]')")
 click('#accountButton');assert script("return document.getElementById('loginDialog').open")
 shot('safari-viewer.png');click('#closeLogin');record['viewer_login_ui']='PASS_NO_CREDENTIALS_SENT'
 record['stage']='IN_APP_INFORMATION_PAGE';action('POST','/url',{'url':BASE+'/flixmomo.html'})
 wait_for(lambda:script("return document.body.innerText.includes('Inside GharTV')"))
 assert script("return !document.querySelector('form') && !document.querySelector('a[target=\"_blank\"]')")
 shot('safari-films.png');record['in_app_route']='PASS_NO_EXTERNAL_TAB_OR_NATIVE_EXECUTION'
 record['stage']='NATIVE_HLS_TEST_MEDIA';action('POST','/url',{'url':'http://127.0.0.1:8801/index.html'})
 wait_for(lambda:script('return !!window.GharTVPlayback'));click('#play')
 wait_for(lambda:script("return document.getElementById('video').currentTime>0.35"),25)
 video=script("const v=document.getElementById('video');return {engine:window.testEngine,time:v.currentTime,width:v.videoWidth,height:v.videoHeight,readyState:v.readyState,error:v.error&&v.error.code};")
 assert video['engine']=='native' and video['width']==640 and not video['error'],video
 record['hls_test_media']=video;record['status']='PASS';record['stage']='COMPLETED';shot('safari-hls-test-media.png')
except Exception as error:record.update(status='FAIL',error=type(error).__name__+': '+str(error)[:1000])
finally:
 if sid:
  try:command('DELETE','/session/'+sid)
  except Exception:pass
 if proc:
  proc.terminate()
  try:proc.wait(timeout=5)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
 httpd.shutdown();httpd.server_close()
 (OUT/'SAFARI_VALIDATION.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
if record['status']!='PASS':sys.exit(1)
