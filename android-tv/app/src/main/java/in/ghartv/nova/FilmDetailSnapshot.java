package in.ghartv.nova;

/** Only descriptive data from this title's top document. Never player URLs or account data. */
final class FilmDetailSnapshot {
    static final String JS="""
      function detail(){
        if(!/^\\/(movie|tv|show|title)\\/[^/]+/i.test(here.pathname)||/\\/watch(?:\\/|$)/i.test(here.pathname))return null;
        const root=document.querySelector('main,[role="main"]')||document.body;
        if(!root)return null;
        const label=e=>clean(e.getAttribute('aria-label')||e.innerText||e.textContent||e.value).replace(/^[\\s\\u25b6\\u25ba]+/,'').trim();
        const controls=Array.from(root.querySelectorAll('a[href],button,[role="button"],input[type="button"]')).filter(visible).slice(0,300);
        const watchControl=controls.find(e=>/^(watch now|play( now)?|start watching|resume watching|watch movie)$/i.test(label(e)));
        const watchRect=watchControl?.getBoundingClientRect();
        const headings=Array.from(root.querySelectorAll('h1,h2,h3,[role="heading"],[itemprop="name"]')).filter(e=>visible(e)&&!e.closest('nav,header,footer,[role="navigation"]')&&clean(e.textContent).length>=2&&clean(e.textContent).length<=180&&!/^(flixmomo|related.*|similar.*|recommended.*|suggested.*|search results.*|trending.*|watch (now|online))$/i.test(clean(e.textContent))).slice(0,35);
        // React/MUI title pages often use h2/h3. An h1-only reader misses them entirely.
        const ranked=headings.map((e,i)=>{const r=e.getBoundingClientRect();return {e,score:watchRect?Math.abs(r.bottom-watchRect.top)+Math.abs(r.left-watchRect.left)*.25+(r.top>watchRect.bottom?2000:0):(e.tagName==='H1'?0:100)+i};}).sort((a,b)=>a.score-b.score);
        const h=ranked[0]?.e;
        let scope=h?.parentElement||root;
        // Include sibling overview/facts without treating the entire footer as title data.
        for(let i=0;i<4&&scope!==root&&scope?.parentElement&&(!scope.contains(watchControl)||!scope.querySelector('p,[itemprop="description"],.overview,.synopsis'));i++)scope=scope.parentElement;
        if(!scope)scope=root;
        let structured=null,budget=0;const data=[];
        function visit(v,depth){if(!v||depth>5||++budget>100)return;if(Array.isArray(v)){v.slice(0,25).forEach(x=>visit(x,depth+1));return;}if(typeof v!=='object')return;
          const types=Array.isArray(v['@type'])?v['@type']:[v['@type']];if(types.some(t=>['Movie','TVSeries','TVEpisode'].includes(t)))data.push(v);if(v['@graph'])visit(v['@graph'],depth+1);if(v.mainEntity)visit(v.mainEntity,depth+1);}
        for(const node of Array.from(document.querySelectorAll('script[type="application/ld+json"]')).slice(0,12)){if(node.textContent.length<=60000)try{visit(JSON.parse(node.textContent),0);}catch(e){}}
        const headingName=clean(h?.textContent);
        structured=data.find(x=>clean(x.name).toLowerCase()===headingName.toLowerCase())||(!h&&data.length===1?data[0]:null);
        const title=clean(headingName||structured?.name||'').slice(0,180);if(!title||/search results|verify|just a moment/i.test(title))return null;
        const text=v=>clean(typeof v==='string'?v:typeof v==='number'?String(v):'').replace(/<[^>]*>/g,'');
        const many=v=>Array.isArray(v)?v.map(text).filter(Boolean).join(', '):text(v);
        const paragraphs=Array.from(scope.querySelectorAll('[itemprop="description"],.overview,.synopsis,[data-synopsis],p')).filter(e=>visible(e)&&!e.closest('nav,header,footer,[role="navigation"]')&&clean(e.textContent).length>=40&&clean(e.textContent).length<=4000&&!/^(we use cookies|sign in|log in|this site does not|your favorite movies)/i.test(clean(e.textContent)));
        // Explicit overview beats layout order. No borrowing a recommended title's synopsis.
        const explicit=paragraphs.find(e=>e.matches('[itemprop="description"],.overview,.synopsis,[data-synopsis]'));
        const nearest=paragraphs.map(e=>({e,r:e.getBoundingClientRect()})).filter(x=>!watchRect||x.r.bottom>=watchRect.top-80).sort((a,b)=>Math.abs(a.r.top-(watchRect?.bottom||0))-Math.abs(b.r.top-(watchRect?.bottom||0)))[0]?.e;
        const synopsis=text(explicit?.textContent||structured?.description||nearest?.textContent||'').slice(0,2400);
        const facts={};const put=(k,v)=>{v=text(v);if(v&&v.length<=180)facts[k]=v;};
        put('Type',many(structured?.['@type']));put('Released',structured?.datePublished);put('Duration',structured?.duration);put('Genres',many(structured?.genre));put('Language',many(structured?.inLanguage));
        if(structured?.aggregateRating){const x=structured.aggregateRating;put('Provider rating',text(x.ratingValue)+(x.bestRating?' / '+text(x.bestRating):'')+(x.ratingCount?' ('+text(x.ratingCount)+' ratings)':''));}
        const keys=new Map([['release date','Released'],['released','Released'],['duration','Duration'],['language','Language'],['languages','Language'],['genres','Genres'],['genre','Genres'],['status','Status']]);
        for(const n of Array.from(root.querySelectorAll('dt,strong,b,span,p')).slice(0,400)){
          if(!visible(n)||n.closest('nav,header,footer'))continue;
          const raw=clean(n.textContent);if(raw.length>30)continue;const k=keys.get(raw.replace(/:$/,'').toLowerCase());if(!k||facts[k])continue;
          let value='';const next=n.nextElementSibling;
          if(n.tagName==='DT'&&next?.tagName==='DD')value=clean(next.textContent);
          else {const parent=clean(n.parentElement?.textContent);if(parent.startsWith(raw)&&parent.length<=raw.length+182)value=parent.slice(raw.length).trim();if(!value&&next&&visible(next))value=clean(next.textContent);}
          if(!keys.has(value.replace(/:$/,'').toLowerCase()))put(k,value);
        }
        // Plain visible subtitle, e.g. "Movie 1 hr 41 min", is not structured JSON.
        const subtitle=Array.from(scope.querySelectorAll('p,span')).filter(visible).map(e=>clean(e.textContent)).find(s=>/^(Movie|TV|TV Series|Series|Episode)\\b/i.test(s)&&s.length<100);
        if(subtitle){if(!facts.Type)put('Type',(subtitle.match(/^(TV Series|Movie|TV|Series|Episode)/i)||[])[0]);if(!facts.Duration)put('Duration',(subtitle.match(/\\b\\d+\\s*(?:hr|hour|h)\\b(?:\\s*\\d+\\s*(?:min|minutes|m)\\b)?|\\b\\d+\\s*(?:min|minutes)\\b/i)||[])[0]);}
        let image='';const rawImage=structured?.image;const original=typeof rawImage==='string'?rawImage:Array.isArray(rawImage)?rawImage[0]:rawImage?.url;
        const photos=Array.from(root.querySelectorAll('img')).filter(e=>visible(e)&&e.getBoundingClientRect().width>60&&e.getBoundingClientRect().height>80&&!e.closest('nav,header,footer'));
        const photo=photos.find(e=>clean(e.alt).toLowerCase()===title.toLowerCase())||photos.sort((a,b)=>Math.abs(a.getBoundingClientRect().top-(h?.getBoundingClientRect().top||0))-Math.abs(b.getBoundingClientRect().top-(h?.getBoundingClientRect().top||0)))[0];
        const source=original||photo?.currentSrc||photo?.getAttribute('src')||'';
        if(source)try{const u=new URL(source,here);if(u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&(permitted(u)||u.hostname==='image.tmdb.org'))image=u.href;}catch(e){}
        return {title,synopsis,facts,image,watch:!!watchControl,watchlist:controls.some(e=>/^(add to |remove from )(watchlist|watch list|playlist)$/i.test(label(e))),url:here.href,provider:'FlixMomo',provenance:structured?'provider-structured-and-visible':'provider-visible',playbackVerified:false};
      }
      """;
}
