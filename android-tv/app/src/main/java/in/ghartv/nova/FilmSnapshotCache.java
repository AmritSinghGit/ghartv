package in.ghartv.nova;

/** Cache descriptive DOM scans while refreshing playback state on every read.
 * Stores no media addresses, account state or network data. Never polls by itself.
 */
final class FilmSnapshotCache {
    static String wrap(String fullRead) {
        return """
        (()=>{
          const now=performance.now(),key='__ghartv_descriptive_cache_v43';
          let cache=window[key];
          if(!cache||cache.document!==document){
            cache={document,url:'',dirty:true,value:null,at:0,fullScans:0,hits:0};
            const observer=new MutationObserver(()=>{cache.dirty=true;});
            observer.observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,
              attributeFilter:['src','data-src','srcset','href','class','style','hidden','disabled','aria-hidden','aria-disabled','aria-selected','aria-pressed','data-state']});
            addEventListener('resize',()=>{cache.dirty=true;},{passive:true});
            document.addEventListener('input',()=>{cache.dirty=true;},true);
            document.addEventListener('change',()=>{cache.dirty=true;},true);
            window[key]=cache;
          }
          const needs=cache.dirty||cache.url!==location.href||!cache.value||now-cache.at>15000;
          let data;
          if(needs){
            data=JSON.parse(__FULL_READ__);
            if(data.state!=='SNAPSHOT')return JSON.stringify(data);
            cache.value=data;cache.url=location.href;cache.at=now;cache.dirty=false;cache.fullScans++;
          }else{data={...cache.value};cache.hits++;}
          // A cached title must never become cached playback success. Re-read the
          // media clock, pause/error and visible frame count on every invocation.
          const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';};
          const videos=Array.from(document.querySelectorAll('video')).slice(0,32).filter(visible);
          const frames=Array.from(document.querySelectorAll('iframe')).slice(0,64).filter(e=>visible(e)&&e.getBoundingClientRect().width>=160&&e.getBoundingClientRect().height>=90);
          const v=videos.length===1?videos[0]:null;
          data.mediaError=v?.error?.code||0;data.mediaObservable=!!v;data.mediaTime=v?.currentTime||0;
          data.mediaPlaying=!!v&&!v.paused&&!v.ended&&v.readyState>=2;data.mediaCandidates=videos.length+frames.length;data.frameCount=frames.length;
          data.descriptiveCacheHit=!needs;data.descriptiveFullScans=cache.fullScans;data.descriptiveCacheHits=cache.hits;
          data.snapshotReadMs=Math.round((performance.now()-now)*100)/100;
          return JSON.stringify(data);
        })()
        """.replace("__FULL_READ__",fullRead);
    }
}
