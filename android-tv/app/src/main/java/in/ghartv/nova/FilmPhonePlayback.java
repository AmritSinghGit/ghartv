package in.ghartv.nova;

/**
 * Phone transport for the ONE directly observable HTML5 video in the current
 * top document. Never inspects cross-origin frames, provider URLs or DRM keys.
 * Commands are requests; only a later sample proves the player's actual state.
 */
final class FilmPhonePlayback {
    private FilmPhonePlayback() {}

    private static final String VIDEO = """
        const videos=Array.from(document.querySelectorAll('video')).slice(0,24).filter(v=>{
          const r=v.getBoundingClientRect(),s=getComputedStyle(v);
          return r.width>=160&&r.height>=90&&s.display!=='none'&&s.visibility!=='hidden';
        });
        const frames=Array.from(document.querySelectorAll('iframe')).slice(0,32).filter(f=>{
          const r=f.getBoundingClientRect(),s=getComputedStyle(f);
          return r.width>=160&&r.height>=90&&s.display!=='none'&&s.visibility!=='hidden';
        });
        const video=videos.length===1&&frames.length===0&&
          !videos[0].mediaKeys?videos[0]:null;
        const frame=frames.length===1&&videos.length===0?frames[0]:null;
        const remembered=typeof window!=='undefined'&&window.__ghartvPhoneFrame;
        const remote=frame&&remembered&&remembered.source===frame.contentWindow&&
          Date.now()-remembered.at<2500?remembered.state:null;
        """;

    static String sample() {
        return "(()=>{try{"+VIDEO+"""
            const v=video;
            if(!v&&remote)return JSON.stringify(remote);
            if(!v)return JSON.stringify({state:'unknown',observable:false,positionMs:0,
              durationMs:0,seekable:false,seekStartMs:0,seekEndMs:0});
            const finite=n=>Number.isFinite(n)&&n>=0;
            const time=finite(v.currentTime)?v.currentTime:0;
            const duration=finite(v.duration)?v.duration:0;
            let start=0,end=0,seekable=false;
            try{
              const ranges=v.seekable;
              if(ranges&&ranges.length){
                start=ranges.start(0);end=ranges.end(ranges.length-1);
                seekable=finite(start)&&finite(end)&&end>start;
              }
            }catch(e){seekable=false;}
            return JSON.stringify({state:v.ended?'paused':v.paused?'paused':
              v.readyState>=2?'playing':'starting',observable:true,
              positionMs:Math.round(time*1000),durationMs:Math.round(duration*1000),
              seekable,seekStartMs:seekable?Math.round(start*1000):0,
              seekEndMs:seekable?Math.round(end*1000):0});
          }catch(e){return JSON.stringify({state:'unknown',observable:false,positionMs:0,
            durationMs:0,seekable:false,seekStartMs:0,seekEndMs:0});}})()
          """;
    }

    static String command(String operation,long positionMs) {
        if(!("play".equals(operation)||"pause".equals(operation)||"seek".equals(operation)))
            throw new IllegalArgumentException("Unsupported playback command");
        if(positionMs<0||positionMs>86_400_000L)
            throw new IllegalArgumentException("Out-of-range playback position");
        return "(()=>{try{"+VIDEO+"const operation='"+operation+"';const requested="+positionMs+";"+"""
            if(!video){
              if(!remote)return JSON.stringify({result:'UNOBSERVABLE'});
              if(operation==='seek'&&!remote.seekable)
                return JSON.stringify({result:'NOT_SEEKABLE'});
              if(frame.contentWindow&&typeof frame.contentWindow.postMessage==='function'){
                frame.contentWindow.postMessage({tag:'ghartv-phone-media-v1',
                  kind:'command',operation,positionMs:requested},'*');
                return JSON.stringify({result:'REQUESTED'});
              }
              return JSON.stringify({result:'UNOBSERVABLE'});
            }
            if(operation==='play'){
              if(video.ended&&video.seekable.length)video.currentTime=video.seekable.start(0);
              const promise=video.play();if(promise&&promise.catch)promise.catch(()=>{});
              return JSON.stringify({result:'REQUESTED'});
            }
            if(operation==='pause'){
              video.pause();return JSON.stringify({result:'REQUESTED'});
            }
            const ranges=video.seekable;
            if(!ranges||!ranges.length)return JSON.stringify({result:'NOT_SEEKABLE'});
            const seconds=requested/1000;
            let nearest=null,distance=Infinity;
            for(let i=0;i<ranges.length;i++){
              const a=ranges.start(i),b=ranges.end(i);
              if(!Number.isFinite(a)||!Number.isFinite(b)||b<=a)continue;
              const target=Math.max(a,Math.min(b,seconds));
              const delta=Math.abs(target-seconds);
              if(delta<distance){nearest=target;distance=delta;}
            }
            if(nearest===null)return JSON.stringify({result:'NOT_SEEKABLE'});
            video.currentTime=nearest;
            return JSON.stringify({result:'REQUESTED'});
          }catch(e){return JSON.stringify({result:'UNAVAILABLE'});}})()
          """;
    }
}
