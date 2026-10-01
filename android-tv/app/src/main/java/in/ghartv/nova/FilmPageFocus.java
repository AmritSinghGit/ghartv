package in.ghartv.nova;

/** Bounded interaction with the provider document. No media or credential extraction. */
final class FilmPageFocus {
    private static final String JS = """
        (()=>{
        const hosts=__GHARTV_HOSTS__;
        const here=new URL(location.href), action=__ACTION__, pack=o=>JSON.stringify({...o,url:location.href});
        const viewport=()=>{const v=window.visualViewport;return {left:v?.offsetLeft||0,top:v?.offsetTop||0,width:v?.width||innerWidth,height:v?.height||innerHeight};};
        const buttonText=e=>clean(e.getAttribute('aria-label')||e.innerText||e.textContent||e.value).replace(/^[\\s\\u25b6\\u25ba]+/,'').trim();
        if(here.protocol!=='https:'||!hosts.includes(here.hostname)||here.username||here.password||here.port)return pack({state:'ORIGIN_REJECTED'});
        const clean=s=>String(s||'').replace(/[\\x00-\\x1f]/g,' ').replace(/\\s+/g,' ').trim();
        const heading=clean(document.title+' '+Array.from(document.querySelectorAll('h1,h2')).slice(0,3).map(e=>e.textContent).join(' '));
        if(here.pathname==='/dummy'||/just a moment|verify (that )?you are human|checking your browser|security verification|access denied/i.test(heading))return pack({state:'VERIFICATION_REQUIRED'});
        const active=document.activeElement,editing=active&&(active.isContentEditable||/^(INPUT|TEXTAREA|SELECT|IFRAME|VIDEO)$/.test(active.tagName));
        // A focused video is not a text field. Let the already-requested media
        // action reach its existing single-visible-target checks below. Inputs,
        // editable content and ordinary navigation keep the editing behavior.
        const focusedMediaAction=['media','activate','fullscreen'].includes(action)&&active&&!active.isContentEditable&&/^(VIDEO|IFRAME)$/.test(active.tagName);
        if(editing&&!focusedMediaAction&&!['watch','watchlist','scan','focus','commit','fullscreen'].includes(action))return pack({state:'EDITING'});
        const visible=e=>{let r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>1&&r.height>1&&s.display!=='none'&&s.visibility!=='hidden'&&!e.disabled&&e.getAttribute('aria-disabled')!=='true';};
        const dialogs=Array.from(document.querySelectorAll('dialog[open],[role="dialog"],[aria-modal="true"]')).filter(visible);
        const root=dialogs.length?dialogs[dialogs.length-1]:document;
        const label=e=>clean(e.getAttribute('aria-label')||e.getAttribute('title')||e.innerText||e.value||e.getAttribute('placeholder')||(/^(VIDEO|IFRAME)$/.test(e.tagName)?'Video player':'')).slice(0,180);
        function permittedLink(e){if(e.tagName!=='A')return true;try{const u=new URL(e.getAttribute('href'),here);return u.protocol==='https:'&&hosts.includes(u.hostname)&&!u.username&&!u.password&&!u.port;}catch{return false;}}
        const candidates=Array.from(root.querySelectorAll('a[href],button,summary,input:not([type="hidden"]),textarea,select,video,iframe,[role="button"],[role="tab"],[tabindex]'))
        .filter(e=>visible(e)&&permittedLink(e)&&!e.closest('[aria-hidden="true"]')&&(!/^(IFRAME|VIDEO)$/.test(e.tagName)||(e.getBoundingClientRect().width>=160&&e.getBoundingClientRect().height>=90))).filter((e,i,all)=>!all.some((p,j)=>j!==i&&p.contains(e)&&/^(A|BUTTON|SELECT|INPUT|TEXTAREA|SUMMARY)$/.test(p.tagName))).slice(0,180);
        const watch=e=>/^(watch now|play( now)?|start watching|resume watching|watch movie)$/i.test(buttonText(e));
        const watchlist=e=>/^(add to |remove from )?(watchlist|watch list|playlist)$/i.test(label(e));
        if(action==='scan')return pack({state:'READY',count:candidates.length,watch:candidates.some(watch),watchlist:candidates.some(watchlist)});
        if(action==='commit'){
          const pending=window.__ghartvTap39;window.__ghartvTap39=null;
          if(!pending||pending.url!==location.href||!pending.element.isConnected||!visible(pending.element)||!permittedLink(pending.element))return pack({state:'STALE'});
          const e=pending.element,r=e.getBoundingClientRect(),v=viewport(),x=Math.max(v.left+1,Math.min(v.left+v.width-1,r.left+r.width/2)),y=Math.max(v.top+1,Math.min(v.top+v.height-1,r.top+r.height/2));
          const hit=document.elementFromPoint(x,y);if(!(hit===e||e.contains(hit)))return pack({state:'OBSCURED'});
          return pack({state:'TAP',label:label(e),x:x-v.left,y:y-v.top,width:v.width,height:v.height,tag:e.tagName});
        }

        let i=candidates.findIndex(e=>e===active||e.contains(active));if(i<0)i=candidates.findIndex(e=>e.getAttribute('data-ghartv-page-focus')==='true');
        function focus(n,tap=false){
         if(n<0||!candidates[n])return pack({state:'NOT_FOUND'});
         const e=candidates[n];if(!e.isConnected||!visible(e)||!permittedLink(e))return pack({state:'STALE'});
         if(!document.getElementById('ghartv-page-focus-style')){const s=document.createElement('style');s.id='ghartv-page-focus-style';s.textContent='[data-ghartv-page-focus="true"]{outline:4px solid #72f3d0!important;outline-offset:4px!important;scroll-margin:24px!important}';(document.head||document.documentElement).appendChild(s);}
         for(const p of document.querySelectorAll('[data-ghartv-page-focus="true"]'))p.removeAttribute('data-ghartv-page-focus');
         e.setAttribute('data-ghartv-page-focus','true');if(!e.hasAttribute('tabindex'))e.setAttribute('tabindex','0');
         e.focus({preventScroll:true});const old=document.documentElement.style.scrollBehavior;document.documentElement.style.scrollBehavior='auto';e.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});document.documentElement.style.scrollBehavior=old;
         if(tap)window.__ghartvTap39={element:e,url:location.href};
         const r=e.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2;
         if(tap&&(x<0||x>innerWidth||y<0||y>innerHeight))return pack({state:'OUTSIDE_VIEWPORT'});
         return pack({state:tap?'TAP':'FOCUSED',label:label(e),x,y,width:innerWidth,height:innerHeight,tag:e.tagName});
        }
        if(action==='watch'){
          const link=candidates.find(e=>watch(e)&&e.tagName==='A'&&permittedLink(e));
          if(link){const u=new URL(link.getAttribute('href'),here);if(u.href!==here.href&&!u.hash)return pack({state:'NAVIGATE',target:u.href,label:buttonText(link)});}
          let n=candidates.findIndex(watch);if(n<0){const media=candidates.map((e,i)=>({e,i})).filter(x=>/^(VIDEO|IFRAME)$/.test(x.e.tagName));if(media.length===1)n=media[0].i;}return focus(n,true);}
        if(action==='fullscreen'){
          const named=candidates.filter(e=>/^(full ?screen|enter full ?screen|expand player)$/i.test(buttonText(e)));
          return named.length===1?focus(candidates.indexOf(named[0]),true):pack({state:named.length?'AMBIGUOUS_MEDIA':'NOT_FOUND'});
        }
        if(action==='media'){
          // A provider may put a real play button ABOVE its video/iframe.
          // Only a visible, semantic play control over the one media region is
          // eligible. We never click an arbitrary obscuring advert or new origin.
          const media=candidates.filter(e=>/^(VIDEO|IFRAME)$/.test(e.tagName));
          if(media.length!==1)return pack({state:media.length?'AMBIGUOUS_MEDIA':'MEDIA_NOT_READY'});
          const e=media[0];if(e.tagName==='VIDEO'&&!e.paused&&e.currentTime>0)return pack({state:'ALREADY_PLAYING'});
          const r=e.getBoundingClientRect();
          const isPlay=b=>/^(play|play video|start video|start playback|resume video|resume playback|watch now)$/i.test(buttonText(b));
          const buttons=Array.from(root.querySelectorAll('button,[role="button"],a[href],.vjs-big-play-button,.plyr__control--overlaid'))
            .filter(b=>b!==e&&visible(b)&&permittedLink(b)&&!b.closest('nav,header,footer,[aria-hidden="true"]')&&isPlay(b))
            .filter(b=>{const q=b.getBoundingClientRect(),x=q.left+q.width/2,y=q.top+q.height/2;
              return x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom&&q.width*q.height<=r.width*r.height;});
          const unique=buttons.filter((b,i)=>!buttons.some((p,j)=>j!==i&&p.contains(b)));
          if(unique.length>1)return pack({state:'AMBIGUOUS_MEDIA'});
          if(unique.length===1){const b=unique[0];if(!candidates.includes(b))candidates.push(b);return focus(candidates.indexOf(b),true);}
          return focus(candidates.indexOf(e),true);
        }
        if(action==='watchlist'){
         // Prefer the current title's mutation control to the generic header link.
         let n=candidates.findIndex(e=>/^(add to |remove from )(watchlist|watch list|playlist)$/i.test(label(e)));
         if(n<0)n=candidates.findIndex(e=>watchlist(e)&&!e.closest('nav,header,[role="navigation"]'));
         if(n<0)n=candidates.findIndex(watchlist);return focus(n,true);
        }
        if(!candidates.length)return pack({state:'NOT_FOUND'});
        if(i<0){let preferred=candidates.findIndex(watch);i=preferred>=0?preferred:0;if(action!=='activate')return focus(i);}
        if(action==='activate')return focus(i,true);
        if(action==='focus')return focus(i);
        const r=candidates[i].getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2;let best=-1,score=Infinity;
        for(let j=0;j<candidates.length;j++){if(i===j)continue;const b=candidates[j].getBoundingClientRect(),dx=b.left+b.width/2-cx,dy=b.top+b.height/2-cy;
         const horizontal=action==='left'||action==='right',forward=action==='left'?-dx:action==='right'?dx:action==='up'?-dy:dy,cross=horizontal?Math.abs(dy):Math.abs(dx);
         if(forward<3)continue;const same=horizontal?cross<Math.max(r.height,b.height)*.65:cross<Math.max(r.width,b.width)*.65;const s=forward+cross*3+(same?0:100000);if(s<score){best=j;score=s;}}
        if(best<0)return pack({state:'EDGE'});return focus(best);
        })()
        """;
    static String script(String action) {
        switch(action) {case "fullscreen":case "media":case "commit":case "scan":case "focus":case "left":case "right":case "up":case "down":case "activate":case "watch":case "watchlist":break;
            default:throw new IllegalArgumentException("Unsupported page action");}
        return JS.replace("__ACTION__", "'"+action+"'").replace("__GHARTV_HOSTS__",FilmProviderPolicy.hostsJavascript());
    }
}
