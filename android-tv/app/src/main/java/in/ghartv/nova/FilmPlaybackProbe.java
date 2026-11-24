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
    /**
     * Only the one verified, playing top-document HTML5 video may occupy the TV.
     * Cross-origin iframes and unknown/protected players never get a false success.
     * The page's original inline styles are retained for TV Back.
     */
    static String fullscreenVerified(){return fullscreen(false);}
    /** A direct viewer gesture may enlarge one visible provider player even
     * before playback proof; automatic fullscreen still requires real media. */
    static String fullscreenRequested(){return fullscreen(true);}
    private static String fullscreen(boolean viewerRequested){
        return "(()=>{const viewerRequested="+viewerRequested+";"+"""
        try{
          if(window.__ghartv_verified_full76)return 'IMMERSIVE_READY';
          const visible=e=>{
            const b=e.getBoundingClientRect(),css=getComputedStyle(e);
            return b.width>=160&&b.height>=90&&css.visibility!=='hidden'&&css.display!=='none';
          };
          const videos=Array.from(document.querySelectorAll('video')).slice(0,24).filter(visible);
          const frames=Array.from(document.querySelectorAll('iframe')).slice(0,32).filter(visible);
          if(videos.length+frames.length!==1)return 'NO_SINGLE_PLAYER';
          const v=videos.length===1?videos[0]:null;
          const frame=frames.length===1?frames[0]:null;
          if(v&&v.mediaKeys)return 'PROTECTED_CONTENT';
          const report=frame&&window.__ghartvPhoneFrame;
          const framePlaying=!!(report&&report.source===frame.contentWindow&&
            Date.now()-report.at<2500&&report.state?.state==='playing');
          if(!viewerRequested){
            if(v&&(v.paused||v.ended||v.readyState<2||
                !Number.isFinite(v.currentTime)||v.currentTime<=0))return 'NOT_PLAYING';
            if(frame&&!framePlaying)return 'NOT_PLAYING';
          }
          const element=v||frame;
          const old={element,style:element.getAttribute('style'),
            body:document.body.style.overflow,html:document.documentElement.style.overflow};
          window.__ghartv_verified_full76=old;
          document.body.style.overflow='hidden';
          document.documentElement.style.overflow='hidden';
          element.style.cssText+=';position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483646!important;object-fit:contain!important;background:black!important;';
          return 'IMMERSIVE_READY';
        }catch(e){return 'IMMERSIVE_UNAVAILABLE';}})()
        """;
    }
    static String restoreFullscreen(){
        return """
        (()=>{try{
          const old=window.__ghartv_verified_full76;
          if(!old)return 'NOT_ACTIVE';
          const target=old.element||old.video;
          if(target?.isConnected){
            if(old.style===null)target.removeAttribute('style');
            else target.setAttribute('style',old.style);
          }
          document.body.style.overflow=old.body;
          document.documentElement.style.overflow=old.html;
          delete window.__ghartv_verified_full76;
          return 'RESTORED';
        }catch(e){return 'RESTORE_UNAVAILABLE';}})()
        """;
    }
    static String reveal(){
        return "(()=>{"+FilmPageSnapshot.CORE+"""
        if(window.__ghartv_verified_full76||document.fullscreenElement)return JSON.stringify({state:'PLAYER_ALREADY_FULLSCREEN'});
        const items=Array.from(document.querySelectorAll('video,iframe')).filter(e=>visible(e)&&e.getBoundingClientRect().width>=160&&e.getBoundingClientRect().height>=90);
        if(items.length!==1)return JSON.stringify({state:'MANUAL_VIEW'});
        items[0].scrollIntoView({block:'center',behavior:'instant'});
        return JSON.stringify({state:'PLAYER_IN_VIEW'});
        })()
        """;
    }
}
