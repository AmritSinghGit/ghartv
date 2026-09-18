package in.ghartv.nova;

/** Main-thread preview ownership. A completed preview never loops without fresh focus. */
public final class PreviewGate {
    public static final long FOCUS_DELAY_MS=650L;
    public static final long FIRST_FRAME_BUDGET_MS=4500L;
    public static final long VISIBLE_PREVIEW_MS=12000L;
    public enum Phase { IDLE, WAITING, LOADING, PLAYING, FINISHED, FAILED }
    private String key="";
    private boolean focused, enabled=true;
    private long generation;
    private Phase phase=Phase.IDLE;
    public boolean select(String value) {
        value=value==null?"":value;
        if(key.equals(value))return false;
        key=value;cancel();return true;
    }
    public boolean focus(boolean value) {
        if(focused==value)return false;
        focused=value;cancel();return true;
    }
    public boolean enabled(boolean value) {
        if(enabled==value)return false;
        enabled=value;cancel();return true;
    }
    public boolean eligible(){return focused&&enabled&&!key.isEmpty();}
    public long arm(){
        if(!eligible()||phase!=Phase.IDLE)return -1;
        phase=Phase.WAITING;return generation;
    }
    public boolean begin(long token){
        if(!current(token)||phase!=Phase.WAITING)return false;
        phase=Phase.LOADING;return true;
    }
    public boolean firstFrame(long token){
        if(!current(token)||phase!=Phase.LOADING)return false;
        phase=Phase.PLAYING;return true;
    }
    public boolean current(long token){return eligible()&&token==generation;}
    public boolean finish(long token,boolean error){
        if(!current(token))return false;
        generation++;phase=error?Phase.FAILED:Phase.FINISHED;return true;
    }
    public void cancel(){generation++;phase=Phase.IDLE;}
    public Phase phase(){return phase;}
    public static boolean defaultEnabled(Boolean explicitChoice){return explicitChoice==null||explicitChoice;}
}
