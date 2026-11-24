package in.ghartv.nova;

/** One bounded start session. Time is injected for deterministic tests. No network or player APIs. */
final class FilmStartBudget {
    enum State {IDLE, STARTING, NEXT, PLAYING, PAUSED, MANUAL, EXHAUSTED, CANCELLED}
    // A legitimately slow source can take 1-2 minutes to begin. Unknown embedded
    // frame health never justifies automatically trying another source.
    static final long START_TIMEOUT_MS=150000, TOTAL_TIMEOUT_MS=1800000, PROGRESS_INTERVAL_MS=1000;
    static final int MAX_ATTEMPTS=12;
    State state=State.IDLE;
    long began, sourceBegan, firstFrameMs=-1, proofMs=-1, progressAt=-1;
    double progressPosition=-1;
    int attempt;
    boolean sawObservable, sawFrame, sawOtherFrames;
    String reason="";
    void begin(long now){state=State.STARTING;began=sourceBegan=now;attempt=1;firstFrameMs=proofMs=-1;resetProgress();reason="Opening player";}
    private void resetProgress(){progressAt=-1;progressPosition=-1;sawObservable=sawFrame=sawOtherFrames=false;}
    boolean running(){return state==State.STARTING||state==State.NEXT;}
    void selected(long now){if(!running()){begin(now);return;}state=State.STARTING;sourceBegan=now;firstFrameMs=-1;resetProgress();reason="Starting selected source";}
    void cancel(){state=State.CANCELLED;reason="Cancelled";}
    void paused(){if(running()){state=State.PAUSED;reason="Paused; no automatic retry";}}
    void unavailable(String message){if(running()){state=State.MANUAL;reason=message;}}
    void noNext(){state=State.EXHAUSTED;reason="No untried source remains";}
    /** Advance only when the current, fully loaded page has exposed *no*
     * video OR iframe for a sustained window. An opaque iframe is NOT an
     * empty source: it may be playing, so it never triggers this path.
     */
    State confirmedEmptyAfterWait(long now,int emptyObservations,boolean ready){
        if(state!=State.STARTING||!ready||sawObservable||sawFrame||sawOtherFrames||
            emptyObservations<8||now-sourceBegan<25000)return state;
        if(attempt>=MAX_ATTEMPTS){
            state=State.EXHAUSTED;reason="No more verified source choices";return state;
        }
        attempt++;state=State.NEXT;
        reason="No video or iframe exposed after waiting; trying next source";
        return state;
    }
    State sample(long now,boolean observable,boolean hasOtherFrames,boolean frame,
                 boolean playing,boolean paused,double position,int error,boolean protectedMedia){
        if(state!=State.STARTING)return state;
        if(now-began>=TOTAL_TIMEOUT_MS){state=State.EXHAUSTED;reason="Total start budget exhausted";return state;}
        sawObservable|=observable;
        sawOtherFrames|=hasOtherFrames;
        if(frame){sawFrame=true;if(firstFrameMs<0)firstFrameMs=now-sourceBegan;}
        if(observable&&playing&&Double.isFinite(position)){
            if(progressAt<0||position<progressPosition){progressAt=now;progressPosition=position;}
            else if(now-progressAt>=PROGRESS_INTERVAL_MS&&position-progressPosition>=0.25){
                if(frame){state=State.PLAYING;proofMs=now-sourceBegan;reason="Rendered frame and advancing position verified";}
                else{state=State.MANUAL;reason="Position is advancing; first-frame evidence is unavailable. Playback is left undisturbed";}
                return state;
            }
        }
        if(observable&&frame&&paused){paused();return state;}
        if(!observable||hasOtherFrames||protectedMedia){
            if(now-sourceBegan>=START_TIMEOUT_MS){state=State.MANUAL;reason=protectedMedia?"Protected player requires its own controls":"Embedded player progress cannot be observed. No automatic source switching.";}
            return state;
        }
        if(error>0||now-sourceBegan>=START_TIMEOUT_MS){
            if(attempt>=MAX_ATTEMPTS){state=State.EXHAUSTED;reason="Twelve source attempts exhausted";return state;}
            attempt++;state=State.NEXT;reason=error>0?"Media error; releasing before next source":"Start timed out; releasing before next source";
        }
        return state;
    }
}
