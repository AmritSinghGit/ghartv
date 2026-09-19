"""RC10.1: permit the provider's normal verification, never solve or bypass it."""
from pathlib import Path
import re
R=Path(__file__).resolve().parents[2]
def change(name,fn):
 p=R/name;s=p.read_text();n=fn(s)
 if n!=s:p.write_text(n)
def one(s,a,b):
 assert s.count(a)==1,(a[:80],s.count(a));return s.replace(a,b)
def worker(s):
 if "'browse'" in s:return s
 s=one(s,"const searchHosts=['flixmomo.app','themoviedb.org','tmdb.org'];","const searchHosts=['flixmomo.app','themoviedb.org','tmdb.org','challenges.cloudflare.com'];")
 s=one(s,"['search','open'].includes(input.action)","['search','open','browse'].includes(input.action)")
 s=one(s,"if(input.action==='open'){","if(input.action!=='search'){")
 s=one(s,"await page.goto(filmURL(input.url),","await page.goto(input.action==='browse'?PROVIDER+'/':filmURL(input.url),")
 s=one(s,"await field.waitFor({state:'visible',timeout:8000});","""try { await field.waitFor({state:'visible',timeout:12000}); }
  catch(error) {
   const title=await page.title().catch(()=>'');
   const text=await page.locator('body').innerText({timeout:1000}).catch(()=>'');
   if(/just a moment|security verification|verify.*human|checking.*browser|captcha/i.test(title+' '+text))throw Error('PROVIDER_VERIFICATION_REQUIRED_USE_BROWSER');
   throw Error('PROVIDER_SEARCH_CONTROL_UNAVAILABLE');
  }""")
 return s
change('web-player/film-browser-worker.mjs',worker)
def service(s):
 if "'browse'" in s:return s
 s=one(s,"if(input.action!=='open'||!value.ok)","if(input.action==='search'||!value.ok)")
 s=one(s,"['search','open'].includes(action)","['search','open','browse'].includes(action)")
 s=one(s,"else{const item=results.get(String(body.id));","else if(action==='browse'){input={action,route:body.route};}\n   else{const item=results.get(String(body.id));")
 s=one(s,'<button type="button" id="stop">','<button type="button" id="browse">Browse FlixMomo</button><button type="button" id="stop">')
 s=one(s,"$('stop').onclick=async()=>{","""$('browse').onclick=async()=>{ $('browse').disabled=true;try{const v=await api('browse',{route:$('route').value});message('Provider browser opened. Complete any verification yourself, then use its search and player. '+(v.torVerified?'Tor route checked.':'Direct route.'));}catch(e){message(e.message);}finally{$('browse').disabled=false;localStatus();}};
$('stop').onclick=async()=>{""")
 s=one(s,"function message(text){$('notice').textContent=text;}","function message(text){$('notice').textContent=text==='PROVIDER_VERIFICATION_REQUIRED_USE_BROWSER'?'FlixMomo requires browser verification. Choose Browse FlixMomo, complete the check yourself, and use its search. No verification bypass is attempted.':text;}")
 return s.replace('GharTV Nova RC10 ·','GharTV Nova RC10.1 ·')
change('web-player/film-search.mjs',service)
for name in ['android-tv/app/build.gradle.kts','web-player/package.json','web-player/package-lock.json','web-player/server.mjs','tools/owner_review.command.in','tools/package_owner_review.py']:
 change(name,lambda s:s.replace('0.6.0-rc10-web-films','0.6.0-rc10.1-web-films'))
change('android-tv/app/build.gradle.kts',lambda s:s.replace('versionCode = 27','versionCode = 28'))
change('tools/owner_review.command.in',lambda s:re.sub(r'\b27\b','28',s).replace('code27','code28').replace("TAG='v0.6.0-rc10'","TAG='v0.6.0-rc10.1'"))
change('tools/package_owner_review.py',lambda s:re.sub(r'\b27\b','28',s).replace('GHARTV_RC10_REVIEW','GHARTV_RC10_1_REVIEW'))
change('tools/tv-first/check_contract.py',lambda s:re.sub(r'\b27\b','28',s).replace('CODE27','CODE28'))
change('tools/run_owner_bundle.command.in',lambda s:s.replace("known={'","known={'fdb079207be8a533d5865f5aa8d283729b1b7d633afd6d947f376b9a06207578','",1) if 'fdb079207be8a533d5865f5aa8d283729b1b7d633afd6d947f376b9a06207578' not in s else s)
p=R/'web-player/test/films.test.mjs';s=p.read_text()
if 'manual provider browse' not in s:
 s+='''\ntest('manual provider browse uses only the registered home and explicit route',async()=>{\n const r=await call('/owner-api/films/browse',{method:'POST',body:{route:'direct'}});\n assert.equal(JSON.parse(r.body).error,'INSTALL_BRAVE_OR_CHROMIUM_FIRST');\n assert.match(filmHTML('a'.repeat(64)),/Complete any verification yourself/);\n});\n''';p.write_text(s)
change('REVIEW_060_RC10.md',lambda s:s.replace('Nova RC10 —','Nova RC10.1 —').replace('versionCode27','versionCode28').replace('0.6.0-rc10-web-films','0.6.0-rc10.1-web-films').replace('code27','code28'))
p=R/'REVIEW_060_RC10.md';s=p.read_text()
if 'Normal provider verification' not in s:
 s+='''\n## Normal provider verification — RC10.1\nThe first RC10 live search encountered the provider's security verification page because the search allow-list blocked its required challenges.cloudflare.com script. RC10.1 permits that exact verification host while retaining HTTPS and network restrictions. It does not solve or bypass a CAPTCHA. A required verification now produces a specific status and an explicit Browse FlixMomo action opens the provider in the selected isolated browser route for the owner to complete checks manually. Read VALIDATION.json for the new probe's actual result; a successful build is not a provider playback pass.\n''';p.write_text(s)
for name in ['docs/index.html','tools/rc10/site.html']:
 change(name,lambda s:s.replace('v0.6.0-rc10/','v0.6.0-rc10.1/').replace('v0.6.0-rc10"','v0.6.0-rc10.1"').replace('GHARTV_RC10_REVIEW','GHARTV_RC10_1_REVIEW').replace('code27','code28'))
print('RC10_1_NORMAL_PROVIDER_VERIFICATION_AND_CODE28_INTEGRATED')
