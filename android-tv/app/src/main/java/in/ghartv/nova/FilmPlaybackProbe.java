package in.ghartv.nova;
import org.json.JSONObject;

/** Observes only top-document media; never inspects frame contents or returns stream URLs. */
final class FilmPlaybackProbe {
    static String sample(long token){
        return "(()=>{"+FilmPageSnapshot.CORE+"const token="+token+";"+"""
        const vs=Array.from(document.querySelectorAll('video')).slice(0,16).filter(visible);
        const fs=Array.from(document.querySelectorAll('iframe')).slice(0,32).filter(e=>visible(e)&&e.getBoundingClientRect().width>=160&&e.getBoundingClientRect().height>=90);
        const v=vs.length===1?vs[0]:null;
        let s=window.__ghartv_start49;
        if(!s||s.token!==token||s.video!==v){
          if(s?.pending&&s.video?.cancelVideoFrameCallback)try{s.video.cancelVideoFrameCallback(s.pending);}catch(e){}
          s={token,video:v,frame:false,pending:0};window.__ghartv_start49=s;
        }
        if(v&&!s.pending&&typeof v.requestVideoFrameCallback==='function'){
          s.pending=v.requestVideoFrameCallback(()=>{if(window.__ghartv_start49===s){s.frame=true;s.pending=0;}});
        }
        return JSON.stringify({state:'MEDIA_OBSERVATION',url:location.href,observable:!!v,frames:fs.length,
          frame:!!s.frame,frameApi:!!v&&typeof v.requestVideoFrameCallback==='function',playing:!!v&&!v.paused&&!v.ended&&v.readyState>=2,
          paused:!!v&&v.paused,position:v?.currentTime||0,error:v?.error?.code||0,protected:!!v?.mediaKeys});
        })()
        """;
    }
    static String release(String page, boolean automatic){
        return "(()=>{"+FilmPageSnapshot.CORE+"if(location.href!=="+JSONObject.quote(page)+")return JSON.stringify({state:'STALE_PAGE'});const automatic="+automatic+";"+"""
        const vs=Array.from(document.querySelectorAll('video,audio')).slice(0,24);
        const fs=Array.from(document.querySelectorAll('iframe')).slice(0,32).filter(e=>visible(e)&&e.getBoundingClientRect().width>=160&&e.getBoundingClientRect().height>=90);
        if(automatic&&(fs.length||vs.length!==1||vs[0].mediaKeys))return JSON.stringify({state:'MANUAL_REQUIRED'});
        const s=window.__ghartv_start49;if(s?.pending&&s.video?.cancelVideoFrameCallback)try{s.video.cancelVideoFrameCallback(s.pending);}catch(e){}
        delete window.__ghartv_start49;
        for(const v of vs){v.pause();v.removeAttribute('src');for(const source of v.querySelectorAll('source'))source.remove();v.load();}
        // Explicit Cancel/Next may detach the current visible frame; its contents are never read.
        if(!automatic)for(const f of fs){f.setAttribute('src','about:blank');f.removeAttribute('srcdoc');}
        return JSON.stringify({state:'RELEASED',paused:vs.every(v=>v.paused),framesDetached:automatic?0:fs.length});
        })()
        """;
    }
    static String reveal(){
        return "(()=>{"+FilmPageSnapshot.CORE+"""
        const items=Array.from(document.querySelectorAll('video,iframe')).filter(e=>visible(e)&&e.getBoundingClientRect().width>=160&&e.getBoundingClientRect().height>=90);
        if(items.length!==1)return JSON.stringify({state:'MANUAL_VIEW'});
        items[0].scrollIntoView({block:'center',behavior:'instant'});
        return JSON.stringify({state:'PLAYER_IN_VIEW'});
        })()
        """;
    }
}
