package in.ghartv.nova;

/** Focus on original poster links; preserves images, metadata, page and access controls. */
final class FilmPosterNavigation {
    private static final String JS = """
        (()=>{
          const allowed=['flixmomo.app','flixmomo.st','www.flixmomo.st','flixmomo.bet','www.flixmomo.bet'];
          const here=new URL(location.href),action=__ACTION__;
          const permitted=u=>u.protocol==='https:'&&allowed.includes(u.hostname)&&!u.username&&!u.password&&!u.port;
          const pack=o=>JSON.stringify(o);
          if(!permitted(here))return pack({state:'ORIGIN_REJECTED'});
          const heading=(document.title+' '+Array.from(document.querySelectorAll('h1,h2')).slice(0,4).map(x=>x.textContent).join(' '));
          if(here.pathname==='/dummy'||/just a moment|verify (that )?you are human|checking your browser|security verification|access denied/i.test(heading))return pack({state:'VERIFICATION_REQUIRED'});
          if(here.pathname.replace(/\\/+$/,'')!=='/search')return pack({state:'UNSUPPORTED'});
          const active=document.activeElement;
          const edit=active&&(active.isContentEditable||/^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName));
          if(action!=='scan'&&edit)return pack({state:'EDITING'});
          const clean=s=>String(s||'').replace(/[\\x00-\\x1f]/g,' ').replace(/\\s+/g,' ').trim();
          const label=a=>{
            const h=a.querySelector('h1,h2,h3,h4,[class*="title"]'),image=a.querySelector('img');
            let text=clean(h?.textContent||a.getAttribute('title')||a.getAttribute('aria-label')||image?.alt||a.innerText);
            if(!text.includes(' ')&&/[-_]/.test(text))text=text.replace(/\\.(jpg|jpeg|png|webp|avif)$/i,'').replace(/[-_]+/g,' ').replace(/\\b[a-z]/g,c=>c.toUpperCase());
            return text.slice(0,180);
          };
          function bounds(a){
            const outer=a.getBoundingClientRect(),image=a.querySelector('img,picture');
            if(!image)return outer;
            const art=image.getBoundingClientRect();
            // Inline anchors can be one text-line tall around a full-height poster.
            // Use the original image bounds, without changing its layout or element.
            return outer.width>=20&&outer.height>=20?outer:art;
          }
          function candidates(){
            const seen=new Set(),out=[];
            for(const a of Array.from(document.querySelectorAll('a[href]')).slice(0,900)){
              if(out.length>=120)break;
              const image=a.querySelector('img,picture');if(!image)continue;
              const rect=bounds(a),style=getComputedStyle(a),imageStyle=getComputedStyle(image);
              if(rect.width<20||rect.height<20||style.display==='none'||style.visibility==='hidden'||imageStyle.display==='none'||imageStyle.visibility==='hidden')continue;
              let url;try{url=new URL(a.getAttribute('href'),here);}catch(e){continue;}
              if(!permitted(url)||!/^\\/(movie|tv|show|watch|title)\\/[a-z0-9]/i.test(url.pathname)||url.search||url.hash||seen.has(url.href))continue;
              seen.add(url.href);out.push({node:a,url:url.href,label:label(a),rect});
            }return out;
          }
          const list=candidates();if(!list.length)return pack({state:'UNSUPPORTED',count:0});
          if(!document.getElementById('ghartv-poster-focus-v1')){
            const style=document.createElement('style');style.id='ghartv-poster-focus-v1';
            style.textContent='[data-ghartv-poster-focus="true"],[data-ghartv-poster-focus="true"] img{outline:4px solid #8cf4d5!important;outline-offset:4px!important;scroll-margin:30px 16px!important;}';
            (document.head||document.documentElement).appendChild(style);
          }
          for(const p of list)if(!p.node.hasAttribute('tabindex'))p.node.setAttribute('tabindex','0');
          if(action==='scan')return pack({state:'READY',count:list.length});
          let index=list.findIndex(p=>p.node===active||p.node.contains(active));
          if(index<0)index=list.findIndex(p=>p.node.getAttribute('data-ghartv-poster-focus')==='true');
          function focus(i){
            for(const e of document.querySelectorAll('[data-ghartv-poster-focus="true"]'))e.removeAttribute('data-ghartv-poster-focus');
            const p=list[i];p.node.setAttribute('data-ghartv-poster-focus','true');p.node.focus({preventScroll:true});
            (p.node.querySelector('img')||p.node).scrollIntoView({block:'nearest',inline:'nearest',behavior:'auto'});
            return pack({state:'FOCUSED',index:i,count:list.length,label:p.label});
          }
          if(index<0)return focus(0);if(action==='focus')return focus(index);
          if(action==='activate'){
            const p=list[index],u=new URL(p.node.getAttribute('href'),here);
            if(!p.node.isConnected||!permitted(u)||u.href!==p.url)return pack({state:'STALE'});
            return pack({state:'ACTIVATE',url:p.url,label:p.label});
          }
          const r=bounds(list[index].node),cx=r.left+r.width/2,cy=r.top+r.height/2;let best=-1,score=Infinity;
          for(let i=0;i<list.length;i++){
            if(i===index)continue;const b=bounds(list[i].node),dx=b.left+b.width/2-cx,dy=b.top+b.height/2-cy;
            const horizontal=action==='left'||action==='right',forward=action==='left'?-dx:action==='right'?dx:action==='up'?-dy:dy;
            const cross=horizontal?Math.abs(dy):Math.abs(dx);if(forward<=3)continue;
            const sameAxis=horizontal?cross<Math.max(r.height,b.height)*.45:cross<Math.max(r.width,b.width)*.75;
            const candidate=forward+cross*3+(sameAxis?0:100000);if(candidate<score){best=i;score=candidate;}
          }
          if(best<0)return pack({state:action==='up'?'TOOLBAR':'EDGE',index,count:list.length,label:list[index].label});
          return focus(best);
        })()
        """;
    static String script(String action){
        switch(action){case "scan":case "focus":case "left":case "right":case "up":case "down":case "activate":break;default:throw new IllegalArgumentException("Unknown poster action");}
        return JS.replace("__ACTION__","'"+action+"'");
    }
}
