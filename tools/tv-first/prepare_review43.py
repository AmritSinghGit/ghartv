#!/usr/bin/env python3
"""Apply bounded Review43 performance changes on the existing source branch.
No native stream resolver, third-party media extraction or live-TV changes.
"""
from pathlib import Path
import hashlib

ROOT=Path(__file__).resolve().parents[2]
BASE={
 'android-tv/app/build.gradle.kts':'d31c69b296cd460a0039b25a3665822b71cc654085c4edcf5279ce1d609637a3',
 'android-tv/app/src/main/java/in/ghartv/nova/FilmPageSnapshot.java':'33b2fe35dc280fef7017006459fe2c223d6ba9863e4e29bf5126e3d05ae935e1',
 'android-tv/app/src/main/java/in/ghartv/nova/FilmHomeView.java':'1a79645f6956893182c52778c02674c37f8a6f7d64a85bc80378b156446279ed',
 'tools/tv-first/check_contract.py':'b8cb0434dd85f6ec9463066f5dc4798eb3d2c2e76f0008878a1c49e9503fb4a2',
 'tools/tv-first/package_review42.py':'0d66f174f3f62f735258a95321de0abc20df786214afba1cb7359bd0a670b6a6',
}

def once(text,old,new):
    assert text.count(old)==1,repr(old[:100])
    return text.replace(old,new)

def prepare():
    if 'versionCode = 43' in (ROOT/'android-tv/app/build.gradle.kts').read_text():return []
    for name,digest in BASE.items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name+' changed; preserved'
    planned={}
    name='android-tv/app/build.gradle.kts';s=(ROOT/name).read_text();s=once(s,'versionCode = 42','versionCode = 43');s=once(s,'0.6.0-rc12.3-playback-focus-clock-review','0.6.0-rc12.4-page-performance-review');planned[name]=s
    name='tools/tv-first/check_contract.py';s=(ROOT/name).read_text().replace('versionCode = 42','versionCode = 43').replace('0.6.0-rc12.3-playback-focus-clock-review','0.6.0-rc12.4-page-performance-review');planned[name]=s
    name='android-tv/app/src/main/java/in/ghartv/nova/FilmPageSnapshot.java';s=(ROOT/name).read_text();s=once(s,'    static String read(){','    static String read(){return FilmSnapshotCache.wrap(fullRead());}\n    static String fullRead(){');planned[name]=s
    name='android-tv/app/src/main/java/in/ghartv/nova/FilmHomeView.java';s=(ROOT/name).read_text()
    s=once(s,'    private boolean pageBlocked;','''    private boolean pageBlocked;
    private static final class PosterTask {
        final ImageView view;final String url;boolean loaded;
        PosterTask(ImageView view,String url){this.view=view;this.url=url;}
    }
    private final List<PosterTask> posterTasks=new ArrayList<>();
    private boolean posterPass;
    private void schedulePosters(){
        if(posterPass||posterTasks.isEmpty()||getVisibility()!=VISIBLE)return;
        posterPass=true;postOnAnimation(()->{posterPass=false;loadNearbyPosters();});
    }
    private void loadNearbyPosters(){
        if(!isShown()||getHeight()==0)return;
        int[] origin=new int[2],position=new int[2];getLocationInWindow(origin);
        int top=origin[1]-dp(260),bottom=origin[1]+getHeight()+dp(260);
        for(PosterTask task:posterTasks){
            if(task.loaded||task.view.getHeight()==0)continue;
            task.view.getLocationInWindow(position);
            if(position[1]+task.view.getHeight()>=top&&position[1]<=bottom){task.loaded=true;loader.load(task.view,task.url);}
        }
    }
    private void clearPosters(){
        for(PosterTask task:posterTasks)if(task.loaded&&!activity.isDestroyed())Glide.with(activity).clear(task.view);
        posterTasks.clear();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);schedulePosters();}
''')
    s=once(s,'@Override public void setVisibility(int visibility){super.setVisibility(visibility);syncPageFocus();}', '@Override public void setVisibility(int visibility){super.setVisibility(visibility);syncPageFocus();if(visibility==VISIBLE)schedulePosters();}')
    s=once(s,'        heading=TvUi.label(a,"Your next watch.",30,TvUi.TEXT,true);content.addView(heading);','        scroll.getViewTreeObserver().addOnScrollChangedListener(this::schedulePosters);\n        heading=TvUi.label(a,"Your next watch.",30,TvUi.TEXT,true);content.addView(heading);')
    s=once(s,'        lastCards.clear();lastCards.addAll(allowed);','')
    s=once(s,'        identity=next;grid.removeAllViews();cards.clear();cardCount=0;','        lastCards.clear();lastCards.addAll(allowed);\n        identity=next;clearPosters();grid.removeAllViews();cards.clear();cardCount=0;')
    s=once(s,'loader.load(image,item.optString("image"));','posterTasks.add(new PosterTask(image,item.optString("image")));')
    s=once(s,'        wireFocus();\n        status.setText(cardCount+', '        wireFocus();schedulePosters();\n        status.setText(cardCount+')
    s=once(s,'void discardSuggestions(){boolean focused=grid.hasFocus();identity="";cardCount=0;lastCards.clear();grid.removeAllViews();','void discardSuggestions(){boolean focused=grid.hasFocus();identity="";cardCount=0;lastCards.clear();clearPosters();grid.removeAllViews();')
    planned[name]=s
    # Reuse the proven original-key packaging, with nonstreaming install from the
    # successful42 postflight. No claim that the earlier failed transport is known.
    p=(ROOT/'tools/tv-first/package_review42.py').read_text()
    for old,new in [('code42','code43'),('CODE42','CODE43'),('Review42','Review43'),('review42','review43'),('CODE=42','CODE=43'),("'--candidate','42'","'--candidate','43'"),('--candidate 42','--candidate 43'),('m.CODE==42','m.CODE==43'),("'version_code':42","'version_code':43"),('0.6.0-rc12.3-playback-focus-clock-review','0.6.0-rc12.4-page-performance-review')]:p=p.replace(old,new)
    p=once(p,'def rawcode(t):', '''old_install="[adb,'-s',SERIAL,'install','-r',signed]"
assert entry.count(old_install)==1
entry=entry.replace(old_install,"[adb,'-s',SERIAL,'install','--no-streaming','-r',signed]")
def rawcode(t):''')
    p=once(p,"'actual_android_tests_passed':100", "'actual_android_tests_passed':110")
    p=once(p,"'retained_review41_tests':88,'new_review43_tests':12", "'retained_review42_tests':100,'new_review43_tests':10")
    p=once(p,"'workflow_run':os.environ['GITHUB_RUN_ID']", "'native_film_handoff_implemented':False,'actual_owner_speedup_measured':False,'dom_cache_and_nearby_posters_tested':True,'workflow_run':os.environ['GITHUB_RUN_ID']")
    planned['tools/tv-first/package_review43.py']=p
    for name,text in planned.items():
        target=ROOT/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text)
    return list(planned)

if __name__=='__main__':
    import json
    print(json.dumps(prepare()))
