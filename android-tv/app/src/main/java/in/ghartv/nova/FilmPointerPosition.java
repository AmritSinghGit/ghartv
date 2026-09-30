package in.ghartv.nova;

/** Geometry for user-controlled pointing only. Never reads media URLs or iframe contents. */
final class FilmPointerPosition {
    static String script(){return """
        (()=>{const out=(v)=>JSON.stringify(v);const w=innerWidth,h=innerHeight;
          const region=Array.from(document.querySelectorAll('video,iframe')).map(e=>({e,r:e.getBoundingClientRect(),s:getComputedStyle(e)}))
            .filter(x=>x.s.display!=='none'&&x.s.visibility!=='hidden'&&x.r.width>=120&&x.r.height>=90&&x.r.bottom>0&&x.r.top<h&&x.r.right>0&&x.r.left<w);
          if(region.length!==1)return out({state:'MANUAL',count:region.length});
          const r=region[0].r;const left=Math.max(0,r.left),right=Math.min(w,r.right),top=Math.max(0,r.top),bottom=Math.min(h,r.bottom);
          if(right<=left||bottom<=top)return out({state:'MANUAL'});
          return out({state:'POSITION_ONLY',x:(left+right)/(2*w),y:(top+bottom)/(2*h)});
        })()
        """;}
    private FilmPointerPosition(){}
}
