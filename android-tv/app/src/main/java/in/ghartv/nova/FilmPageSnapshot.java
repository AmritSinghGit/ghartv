package in.ghartv.nova;

import org.json.JSONObject;

/** Read a bounded, provider-owned top document, never cookies, scripts, video URLs
 * or cross-origin frames. Action code only clicks a revalidated visible control
 * which the viewer explicitly selected. No detection or access controls change. */
final class FilmPageSnapshot {
    static final String CORE = """
        const hosts=['flixmomo.app','flixmomo.st','www.flixmomo.st'];
        function permitted(u){return u.protocol==='https:'&&hosts.includes(u.hostname)&&!u.username&&!u.password&&!u.port;}
        const here=new URL(location.href);
        if(!permitted(here))return JSON.stringify({state:'ORIGIN_REJECTED'});
        const clean=t=>String(t||'').replace(/[\\x00-\\x1f]/g,' ').replace(/\\s+/g,' ').trim();
        const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};
        const heading=clean(document.title+' '+Array.from(document.querySelectorAll('h1,h2')).slice(0,4).map(x=>x.textContent).join(' '));
        if(here.pathname==='/dummy'||/just a moment|verify (that )?you are human|checking your browser|security verification|access denied/i.test(heading))return JSON.stringify({state:'PROVIDER_VERIFICATION_REQUIRED'});
        function players(){
          const choices=[],seen=new Set();
          Array.from(document.querySelectorAll('button,[role="button"],input[type="button"]')).slice(0,600).forEach(e=>{
            if(!visible(e)||e.disabled||e.getAttribute('aria-disabled')==='true')return;
            const label=clean(e.innerText||e.value||e.getAttribute('aria-label'));
            if(!/^(player|server|source)\\s*#?\\s*\\d{1,2}$/i.test(label)||seen.has(label.toLowerCase()))return;
            seen.add(label.toLowerCase());choices.push({kind:'button',label,element:e,selected:e.getAttribute('aria-pressed')==='true'||e.getAttribute('aria-selected')==='true'});
          });
          Array.from(document.querySelectorAll('select')).slice(0,20).forEach((select,si)=>{
            if(!visible(select)||select.disabled)return;
            Array.from(select.options).slice(0,20).forEach((option,oi)=>{
              const label=clean(option.textContent);
              if(option.disabled||!/^(player|server|source)\\s*#?\\s*\\d{1,2}$/i.test(label)||seen.has(label.toLowerCase()))return;
              seen.add(label.toLowerCase());choices.push({kind:'select',label,element:select,option:oi,selected:option.selected});
            });
          });
          return choices.slice(0,12);
        }
        """;
    static String read(){return "(()=>{"+CORE+"""
        const results=[],seen=new Set();
        Array.from(document.querySelectorAll('a[href]')).slice(0,900).forEach(a=>{
          if(results.length>=60||!visible(a))return;
          let u;try{u=new URL(a.getAttribute('href'),here);}catch(e){return;}
          if(!permitted(u)||!/^\\/(movie|tv|show|watch|title)\\/[a-z0-9]/i.test(u.pathname)||seen.has(u.href))return;
          const img=a.querySelector('img'),h=a.querySelector('h1,h2,h3,h4');
          const label=clean(img?.alt||h?.textContent||a.getAttribute('aria-label')||a.innerText);
          if(label.length<2||label.length>180)return;
          seen.add(u.href);results.push({title:label,url:u.href});
        });
        const offered=players().map((p,i)=>({index:i,label:p.label,selected:p.selected}));
        const media=Array.from(document.querySelectorAll('video')).filter(visible);
        // Error only, never a playback-success or cross-origin iframe inference.
        const mediaError=media.length===1&&media[0].error?media[0].error.code:0;
        return JSON.stringify({state:'SNAPSHOT',url:here.href,search:here.pathname==='/search',results,players:offered,mediaError,mediaObservable:media.length===1});
        """+"})()";}
    static String select(String pageUrl,int index,String label){
        if(index<0||index>=12)throw new IllegalArgumentException("Invalid player index");
        return "(()=>{"+CORE+"if(location.href!=="+JSONObject.quote(pageUrl)+")return JSON.stringify({state:'STALE_PAGE'});"+
            "const list=players(),p=list["+index+"];if(!p||p.label!=="+JSONObject.quote(label)+")return JSON.stringify({state:'PLAYER_CHANGED'});"+
            "if(p.kind==='select'){p.element.selectedIndex=p.option;p.element.dispatchEvent(new Event('change',{bubbles:true}));}else{p.element.click();}"+
            "return JSON.stringify({state:'SELECTION_REQUESTED',label:p.label});})()";
    }
}
