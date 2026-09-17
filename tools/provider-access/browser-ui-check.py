"""Trusted local owner-page UI test. Does not access any provider or play a film."""
import json, os, pathlib, subprocess, time, urllib.request
from playwright.sync_api import sync_playwright
root=pathlib.Path(__file__).resolve().parents[2]
env={**os.environ,'GHARTV_DISABLE_KEYCHAIN':'1','GHARTV_WEB_PORT':'18391'}
server=subprocess.Popen(['node',str(root/'web-player/server.mjs')],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
try:
    for _ in range(40):
        try:
            urllib.request.urlopen('http://127.0.0.1:18391/api/health',timeout=.3).close();break
        except Exception:time.sleep(.1)
    calls=[];errors=[]
    with sync_playwright() as p:
        # Only our trusted localhost fixture is opened. Production browser worker requires sandbox/non-root.
        b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
        page=b.new_page(viewport={'width':1280,'height':900});page.on('pageerror',lambda e:errors.append(str(e)))
        # Managed Chromium in this environment disallows localhost navigation.
        # Test UI against HTML fetched from the actual server, with an explicit in-page fetch fixture.
        html=urllib.request.urlopen('http://127.0.0.1:18391/provider-access.html').read().decode()
        page.evaluate("""() => {window.testCalls=[];window.fetch=async(url,options)=>{
            window.testCalls.push(JSON.parse(options.body));
            return new Response(JSON.stringify({status:'HTML_PAGE_NOT_VIDEO',playbackVerified:false,httpStatus:200}),{status:200,headers:{'Content-Type':'application/json'}});
        };}""")
        page.set_content(html,wait_until='domcontentloaded')
        assert not page.evaluate('window.testCalls')
        page.locator('#url').fill('https://flixmomo.app/movie/1/example/watch?p=1')
        page.get_by_role('button',name='Check HTTPS page',exact=True).click()
        page.wait_for_function("document.querySelector('#result').textContent.includes('HTML watch page')")
        calls=page.evaluate('window.testCalls');assert len(calls)==1 and calls[0]['mode']=='https'
        page.get_by_role('button',name='Check with isolated browser').click()
        page.wait_for_function("!document.querySelector('#browser').disabled")
        calls=page.evaluate('window.testCalls');assert len(calls)==2 and calls[1]['mode']=='browser'
        page.locator('#url').fill('https://evil.example/')
        assert page.locator('#original').is_hidden()
        page.get_by_role('button',name='Check HTTPS page',exact=True).click()
        assert len(page.evaluate('window.testCalls'))==2
        assert 'registered' in page.locator('#result').inner_text()
        page.locator('#url').fill('https://flixmomo.app/')
        shot=os.environ.get('GHARTV_PROVIDER_UI_SCREENSHOT')
        if shot:page.screenshot(path=shot,full_page=True)
        page.set_viewport_size({'width':390,'height':844})
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
        assert not errors,errors
        b.close()
    print(json.dumps({'ui':'PASS_RENDERED_HTML_WITH_FETCH_FIXTURE','provider_network_requests':0,'controlled_requests':len(calls),'automatic_requests':0,'mobile_overflow':False,'js_errors':errors}))
finally:
    server.terminate()
    try:server.wait(timeout=3)
    except subprocess.TimeoutExpired:server.kill();server.wait(timeout=3)
