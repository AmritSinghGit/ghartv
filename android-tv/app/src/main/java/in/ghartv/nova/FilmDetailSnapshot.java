package in.ghartv.nova;

/** Reads only current top-document descriptive metadata. No iframe/media URLs or credentials. */
final class FilmDetailSnapshot {
    static final String JS="""
      function detail(){
        if(!/^\\/(movie|tv|show|title)\\/[^/]+/i.test(here.pathname)||/\\/watch(?:\\/|$)/i.test(here.pathname))return null;
        let structured=null, budget=0;const metadataCandidates=[];
        function visit(v,depth){if(!v||depth>5||++budget>100)return;if(Array.isArray(v)){v.slice(0,25).forEach(x=>visit(x,depth+1));return;}
          if(typeof v!=='object')return;const types=Array.isArray(v['@type'])?v['@type']:[v['@type']];
          if(types.some(t=>['Movie','TVSeries','TVEpisode'].includes(t)))metadataCandidates.push(v);
          if(v['@graph'])visit(v['@graph'],depth+1);if(v.mainEntity)visit(v.mainEntity,depth+1);
        }
        for(const node of Array.from(document.querySelectorAll('script[type="application/ld+json"]')).slice(0,12)){
          if(node.textContent.length>60000)continue;try{visit(JSON.parse(node.textContent),0);}catch(e){}
        }
        const root=document.querySelector('main,[role="main"]')||document;
        const h=Array.from(root.querySelectorAll('h1')).find(visible);
        structured=metadataCandidates.find(x=>clean(x.name).toLowerCase()===clean(h?.textContent).toLowerCase())||(!h&&metadataCandidates.length===1?metadataCandidates[0]:null);
        const title=clean(h?.textContent||structured?.name||'').slice(0,180);
        if(!title||/search results|verify|just a moment/i.test(title))return null;
        const scope=h?.closest('article,section')||h?.parentElement||root;
        const text=v=>clean(typeof v==='string'?v:typeof v==='number'?String(v):'').replace(/<[^>]*>/g,'');
        const first=sel=>{const n=Array.from(root.querySelectorAll(sel)).find(visible);return n?clean(n.textContent):'';};
        let synopsis=text(structured?.description)||first('[itemprop="description"],.overview,.synopsis,[data-synopsis]');
        if(!synopsis){const p=Array.from(scope.querySelectorAll('p')).find(e=>visible(e)&&clean(e.textContent).length>70&&!/cookie|privacy|sign in|log in|watchlist|advertis/i.test(e.textContent));synopsis=p?clean(p.textContent):'';}
        const facts={};
        const put=(k,v)=>{v=text(v);if(v&&v.length<=180)facts[k]=v;};
        put('Type',structured?.['@type']);put('Released',structured?.datePublished);put('Duration',structured?.duration);
        const many=v=>Array.isArray(v)?v.map(text).filter(Boolean).join(', '):text(v);
        put('Genres',many(structured?.genre));put('Language',many(structured?.inLanguage));
        if(structured?.aggregateRating){const x=structured.aggregateRating;put('Provider rating',text(x.ratingValue)+(x.bestRating?' / '+text(x.bestRating):'')+(x.ratingCount?' ('+text(x.ratingCount)+' ratings)':''));}
        const keys=new Map([['release date','Released'],['released','Released'],['duration','Duration'],['language','Language'],['languages','Language'],['genres','Genres'],['genre','Genres'],['status','Status']]);
        for(const label of Array.from(root.querySelectorAll('dt,strong,b')).slice(0,180)){
          if(!visible(label))continue;const k=keys.get(clean(label.textContent).replace(/:$/,'').toLowerCase());if(!k||facts[k])continue;
          let value=label.tagName==='DT'?label.nextElementSibling?.textContent:label.parentElement?.textContent;
          value=clean(value).replace(new RegExp('^'+clean(label.textContent).replace(/[.*+?^${}()|[\\]\\\\]/g,'\\\\$&')),'').trim();put(k,value);
        }
        let image='';const raw=structured?.image;const original=typeof raw==='string'?raw:Array.isArray(raw)?raw[0]:raw?.url;
        const img=Array.from(scope.querySelectorAll('img')).find(e=>visible(e)&&e.width>60&&e.height>80);
        const source=original||img?.currentSrc||img?.getAttribute('src')||'';
        if(source)try{const u=new URL(source,here);if(u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&(permitted(u)||u.hostname==='image.tmdb.org'))image=u.href;}catch(e){}
        const controls=Array.from(root.querySelectorAll('a[href],button,[role="button"],input[type="button"]')).filter(visible).slice(0,300);
        const label=e=>clean(e.innerText||e.getAttribute('aria-label')||e.value);
        const watch=controls.some(e=>/^(watch now|play( now)?|start watching|resume watching|watch movie)$/i.test(label(e)));
        const watchlist=controls.some(e=>/^(add to |remove from )(watchlist|watch list|playlist)$/i.test(label(e)));
        return {title,synopsis:synopsis.slice(0,2400),facts,image,watch,watchlist,url:here.href,provider:'FlixMomo',provenance:structured?'provider-structured-and-visible':'provider-visible',playbackVerified:false};
      }
      """;
}
