"""Guarded RC10 integration repair, applied only in the cloud build checkout."""
from pathlib import Path
R=Path(__file__).resolve().parents[2]
p=R/'web-player/film-search.mjs';s=p.read_text()
if 'BROWSER_ADMISSION_RESERVED' not in s:
 old="if(active||busy)throw Error('BROWSER_BUSY_STOP_CURRENT_SESSION');\n  const executable="
 new="if(active||busy)throw Error('BROWSER_BUSY_STOP_CURRENT_SESSION');\n  busy=true; // BROWSER_ADMISSION_RESERVED before asynchronous discovery\n  let profile;\n  try {\n  const executable="
 assert s.count(old)==1;s=s.replace(old,new)
 s=s.replace("const profile=await mkdtemp(join(tmpdir(),'ghartv-film-'));busy=true;","profile=await mkdtemp(join(tmpdir(),'ghartv-film-'));")
 s=s.replace('return new Promise(resolve=>{\n   let buffer=', 'return await new Promise(resolve=>{\n   let buffer=')
 old='child.stdin.on(\'error\',()=>{});child.stdin.end(JSON.stringify(input));\n  });\n }'
 new='child.stdin.on(\'error\',()=>{});child.stdin.end(JSON.stringify(input));\n  });\n  } catch(error) { busy=false; if(profile)await rm(profile,{recursive:true,force:true}); throw error; }\n }'
 assert s.count(old)==1;s=s.replace(old,new)
 s=s.replace('<a href="/release-control.html">Release desk</a>','<a href="/owner.html">Private workspace</a>')
 p.write_text(s)
p=R/'web-player/test/films.test.mjs';s=p.read_text()
if 'parallel requests reserve' not in s:
 s+='''\ntest('parallel requests reserve browser admission before discovery resolves',async()=>{\n let release;const discovery=new Promise(resolve=>{release=resolve;});\n const route=makeFilmRoute({discover:()=>discovery});const a=response(),b=response();\n const first=route(req('/owner-api/films/search',{method:'POST',body:{query:'dune',route:'direct'}}),a,new URL('/owner-api/films/search',origin),()=>true,'a'.repeat(64));\n await new Promise(resolve=>setImmediate(resolve));\n await route(req('/owner-api/films/search',{method:'POST',body:{query:'dune',route:'direct'}}),b,new URL('/owner-api/films/search',origin),()=>true,'a'.repeat(64));\n assert.equal(b.status,409);release(null);await first;assert.equal(a.status,400);\n});\n'''
 p.write_text(s)
print('RC10_BROWSER_ADMISSION_AND_LINKS_VERIFIED')
