package in.ghartv.nova;

/** Descriptive title copy only. No media URLs or account data. */
final class FilmDetailSnapshot {
    static final String JS="""

function detail(){
  if(!/^\\/(movie|tv|show|title)\\/[^/]+/i.test(here.pathname)||/\\/watch(?:\\/|$)/i.test(here.pathname))return null;
  const root=document.querySelector('main,[role="main"]')||document.body;if(!root)return null;
  const excluded=e=>!!e.closest('nav,header,footer,[role="navigation"],[aria-hidden="true"],.recommendations,.related,.similar');
  const label=e=>clean(e.getAttribute('aria-label')||e.innerText||e.textContent||e.value).replace(/^[\\s\\u25b6\\u25ba]+/,'').trim();
  const controls=Array.from(root.querySelectorAll('a[href],button,[role="button"],input[type="button"]')).filter(visible).slice(0,300);
  const watchControl=controls.find(e=>!excluded(e)&&/^(watch now|play( now)?|start watching|resume watching|watch movie)$/i.test(label(e)));
  const wr=watchControl?.getBoundingClientRect();
  const headings=Array.from(root.querySelectorAll('h1,h2,h3,[role="heading"],[itemprop="name"]')).filter(e=>visible(e)&&!excluded(e)&&clean(e.textContent).length>=2&&clean(e.textContent).length<=180&&!/^(flixmomo|related.*|similar.*|recommended.*|suggested.*|search results.*|trending.*|watch (now|online))$/i.test(clean(e.textContent))).slice(0,35);
  const ranked=headings.map((e,i)=>{const r=e.getBoundingClientRect();return {e,score:wr?Math.abs(r.bottom-wr.top)+Math.abs(r.left-wr.left)*.25+(r.top>wr.bottom?2000:0):(e.tagName==='H1'?0:100)+i};}).sort((a,b)=>a.score-b.score);
  const h=ranked[0]?.e,hr=h?.getBoundingClientRect();
  let structured=null,budget=0;const objects=[];
  function visit(v,depth){if(!v||depth>5||++budget>100)return;if(Array.isArray(v)){v.slice(0,25).forEach(x=>visit(x,depth+1));return;}if(typeof v!=='object')return;
    const types=Array.isArray(v['@type'])?v['@type']:[v['@type']];if(types.some(t=>['Movie','TVSeries','TVEpisode'].includes(t)))objects.push(v);if(v['@graph'])visit(v['@graph'],depth+1);if(v.mainEntity)visit(v.mainEntity,depth+1);}
  for(const e of Array.from(document.querySelectorAll('script[type="application/ld+json"]')).slice(0,12))if(e.textContent.length<=60000)try{visit(JSON.parse(e.textContent),0);}catch(ignore){}
  const sameRoute=v=>{if(!v)return true;try{const u=new URL(v,here);return permitted(u)&&u.pathname.replace(/\\/+$/,'')===here.pathname.replace(/\\/+$/,'');}catch(ignore){return false;}};
  const name=clean(h?.textContent);
  structured=objects.find(x=>sameRoute(x.url)&&clean(x.name).toLowerCase()===name.toLowerCase())||(!h&&objects.length===1&&sameRoute(objects[0].url)?objects[0]:null);
  const title=clean(name||structured?.name||'').slice(0,180);if(!title||/search results|verify|just a moment/i.test(title))return null;
  const text=v=>clean(typeof v==='string'?v:typeof v==='number'?String(v):'').replace(/<[^>]*>/g,'');
  const many=v=>Array.isArray(v)?v.map(text).filter(Boolean).join(', '):text(v);
  // SEO text is not a title synopsis. Prefer the actual visible title copy even
  // when JSON-LD repeats a generic site description with the correct title name.
  const boilerplate=v=>/watch\\s+(?:full\\s+)?(?:movies|films|tv shows)|no registration required|free (?:movies|streaming) (?:online|on)|(?:watch|stream).{0,45}(?:for free|free hd).{0,70}flixmomo|we use cookies|sign in to|your favou?rite movies/i.test(v);
  const meaningful=v=>v.length>=40&&v.length<=4000&&!boilerplate(v);
  const boundary=Array.from(root.querySelectorAll('h1,h2,h3,h4')).filter(e=>e!==h&&visible(e)&&/^(related|similar|recommended|suggested|you may also)/i.test(clean(e.textContent))).map(e=>e.getBoundingClientRect().top).filter(y=>!hr||y>hr.bottom);
  const bottom=boundary.length?Math.min(...boundary):Infinity;
  const inTitle=e=>{if(!visible(e)||excluded(e))return false;const r=e.getBoundingClientRect();return (!hr||r.bottom>=hr.top-60)&&r.top<bottom;};
  let scope=h?.parentElement||root;
  for(let i=0;i<5&&scope!==root&&watchControl&&!scope.contains(watchControl)&&scope.parentElement;i++)scope=scope.parentElement;
  // Descriptions and sidebars are often DIVs, not paragraphs. Read leaf copy,
  // never a containing block that also includes recommendations or controls.
  const paragraphs=Array.from(root.querySelectorAll('[itemprop="description"],.overview,.synopsis,[data-synopsis],p,div')).slice(0,900).filter(e=>{
    if(!inTitle(e)||e.closest('aside'))return false;const t=text(e.innerText||e.textContent);if(!meaningful(t))return false;
    if(e.querySelector('h1,h2,h3,h4,button,a[href],img,video,iframe,select,input'))return false;
    return !Array.from(e.children).some(c=>c.matches('p,div,[itemprop="description"],.overview,.synopsis,[data-synopsis]')&&meaningful(text(c.innerText||c.textContent)));
  });
  const scored=paragraphs.map(e=>{const r=e.getBoundingClientRect();return {e,score:(e.matches('[itemprop="description"],.overview,.synopsis,[data-synopsis]')?-10000:0)+(wr&&r.bottom<wr.top?2500:0)+Math.abs(r.top-(wr?.bottom||hr?.bottom||0))+Math.abs(r.left-(wr?.left||hr?.left||0))*.5};}).sort((a,b)=>a.score-b.score);
  const observed=scored[0]?text(scored[0].e.innerText||scored[0].e.textContent):'';
  const jsonDescription=text(structured?.description);
  const synopsis=(observed||(meaningful(jsonDescription)?jsonDescription:'')).slice(0,2400);
  const facts={};const put=(k,v)=>{v=text(v);if(v&&v.length<=180)facts[k]=v;};
  put('Type',many(structured?.['@type']));put('Released',structured?.datePublished);put('Duration',structured?.duration);put('Genres',many(structured?.genre));put('Language',many(structured?.inLanguage));
  if(structured?.aggregateRating){const x=structured.aggregateRating;put('Provider rating',text(x.ratingValue)+(x.bestRating?' / '+text(x.bestRating):'')+(x.ratingCount?' ('+text(x.ratingCount)+' ratings)':''));}
  const keys=new Map([['release date','Released'],['released','Released'],['duration','Duration'],['language','Language'],['languages','Language'],['genres','Genres'],['genre','Genres'],['status','Status']]);
  for(const n of Array.from(root.querySelectorAll('dt,strong,b,span,p,div')).slice(0,1000)){
    if(!inTitle(n))continue;const raw=clean(n.textContent);if(raw.length>30)continue;const k=keys.get(raw.replace(/:$/,'').toLowerCase());if(!k)continue;
    let value='';const next=n.nextElementSibling;
    if(n.tagName==='DT'&&next?.tagName==='DD')value=clean(next.textContent);
    else {const parent=clean(n.parentElement?.textContent);if(parent.startsWith(raw)&&parent.length<=raw.length+182)value=parent.slice(raw.length).trim();if(!value&&next&&visible(next))value=clean(next.textContent);}
    if(!keys.has(value.replace(/:$/,'').toLowerCase())&&!boilerplate(value))put(k,value);
  }
  const subtitles=Array.from(root.querySelectorAll('p,span,div')).slice(0,900).filter(inTitle).map(e=>({e,t:clean(e.innerText||e.textContent)})).filter(x=>/^(Movie|TV Series|TV|Series|Episode)\\b/i.test(x.t)&&x.t.length<100).sort((a,b)=>Math.abs(a.e.getBoundingClientRect().top-(hr?.bottom||0))-Math.abs(b.e.getBoundingClientRect().top-(hr?.bottom||0)));
  const subtitle=subtitles[0]?.t;
  if(subtitle){if(!facts.Type)put('Type',(subtitle.match(/^(TV Series|Movie|TV|Series|Episode)/i)||[])[0]);if(!facts.Duration)put('Duration',(subtitle.match(/\\b\\d+\\s*(?:hr|hour|h)\\b(?:\\s*\\d+\\s*(?:min|minutes|m)\\b)?|\\b\\d+\\s*(?:min|minutes)\\b/i)||[])[0]);}
  let image='';const imageUrl=v=>{try{if(typeof v!=='string'||!v.trim())return '';const u=new URL(v,here);return u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&(permitted(u)||u.hostname==='image.tmdb.org')?u.href:'';}catch(ignore){return '';}};
  const ri=structured?.image,original=typeof ri==='string'?ri:Array.isArray(ri)?ri[0]:ri?.url;
  const photos=Array.from(root.querySelectorAll('img')).filter(e=>inTitle(e)&&e.getBoundingClientRect().width>60&&e.getBoundingClientRect().height>80).sort((a,b)=>{const score=e=>(clean(e.alt).toLowerCase()===title.toLowerCase()?-5000:0)+Math.abs(e.getBoundingClientRect().top-(hr?.top||0));return score(a)-score(b);});
  for(const photo of photos){image=imageUrl(photo.getAttribute('data-src'))||imageUrl(photo.currentSrc)||imageUrl(photo.getAttribute('src'));if(image)break;}
  image=image||imageUrl(original);
  if(!image)for(const e of Array.from(root.querySelectorAll('div,figure')).slice(0,300)){if(!inTitle(e))continue;const r=e.getBoundingClientRect();if(r.width<60||r.height<100||r.width/r.height>.85||Math.abs(r.top-(hr?.top||0))>400)continue;const m=getComputedStyle(e).backgroundImage.match(/^url\\(["']?(.*?)["']?\\)$/);if(m&&(image=imageUrl(m[1])))break;}
  return {title,synopsis,facts,image,watch:!!watchControl,watchlist:controls.some(e=>/^(add to |remove from )(watchlist|watch list|playlist)$/i.test(label(e))),url:here.href,provider:'FlixMomo',provenance:observed?'provider-visible':structured?'provider-structured':'provider-visible',playbackVerified:false};
}
        """;
}
