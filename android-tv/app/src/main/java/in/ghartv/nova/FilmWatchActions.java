package in.ghartv.nova;

/** Observed movie/episode actions; never constructs an episode URL or bypasses a provider gate. */
final class FilmWatchActions {
    static final String JS="""

// One policy shared by title readiness and the actual user-requested Watch action.
function ghartvWatchLabel(e){return clean(e.getAttribute('aria-label')||e.innerText||e.textContent||e.value).replace(/^[\\s\\u25b6\\u25ba]+/,'').trim();}
function ghartvWatchText(t){
  if(typeof t!=='string'||t.length>96)return false;
  return /^(watch now|play( now)?|start watching|resume watching|watch movie)$/i.test(t)
    || /^(watch|play|resume|continue)\\s+(?:season\\s*\\d{1,3}\\s*[-:·]?\\s*)?(?:episode|ep\\.?)\\s*#?\\s*\\d{1,4}$/i.test(t)
    || /^(watch|play|resume|continue)\\s+s\\s*\\d{1,3}\\s*e\\s*\\d{1,4}$/i.test(t)
    || /^(watch|play|resume|continue)\\s+(?:first|next)\\s+episode$/i.test(t);
}
function ghartvWatchEnabled(e){
  if(!e||!e.isConnected||!visible(e)||e.disabled||e.getAttribute('aria-disabled')==='true')return false;
  if(!e.matches('a[href],button,[role="button"],input[type="button"],input[type="submit"]'))return false;
  if(e.closest('nav,header,footer,[role="navigation"],[aria-hidden="true"],.related,.recommendations,[data-related]'))return false;
  for(let p=e;p&&p.nodeType===1;p=p.parentElement){const s=getComputedStyle(p);if(s.display==='none'||s.visibility==='hidden'||Number(s.opacity)===0)return false;}
  if(e.tagName==='A'){try{const u=new URL(e.getAttribute('href'),here);if(u.protocol!=='https:'||!hosts.includes(u.hostname)||u.username||u.password||u.port)return false;}catch(ignore){return false;}}
  return ghartvWatchText(ghartvWatchLabel(e));
}
function ghartvWatchChoices(nodes){
  const eligible=Array.from(nodes).slice(0,700).filter(ghartvWatchEnabled);
  // A page may render several different WATCH buttons far below the selected
  // title (e.g. recommendations). Only auto-play the uniquely visible action
  // in the current viewport. Never choose between two on-screen alternatives.
  const inViewport=e=>{const r=e.getBoundingClientRect();return r.right>0&&r.bottom>0&&r.left<innerWidth&&r.top<innerHeight;};
  const inView=eligible.filter(inViewport);
  const visiblePool=inView.length?inView:eligible;
  const seen=new Set();return visiblePool.filter(e=>{
    if(visiblePool.some(p=>p!==e&&p.contains(e)&&ghartvWatchLabel(p)===ghartvWatchLabel(e)))return false;
    const id=e.tagName==='A'?new URL(e.getAttribute('href'),here).href+'\\n'+ghartvWatchLabel(e).toLowerCase():e;
    if(seen.has(id))return false;seen.add(id);return true;
  });
}

    """;
}
