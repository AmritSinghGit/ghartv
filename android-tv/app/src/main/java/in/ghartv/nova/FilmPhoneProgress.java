package in.ghartv.nova;

/** Verified timestamp progress only, not proof of an actual rendered video frame. */
final class FilmPhoneProgress {
    private int epoch=Integer.MIN_VALUE;
    private long previousPosition=-1,previousAt=-1;
    private boolean observedProgress;
    boolean sample(int mediaEpoch,long now,long positionMs,boolean playing){
        if(mediaEpoch!=epoch){
            epoch=mediaEpoch;previousPosition=-1;previousAt=-1;observedProgress=false;
        }
        if(!playing||positionMs<0||positionMs>86_400_000L){
            previousAt=-1;previousPosition=-1;return false;
        }
        if(!observedProgress&&previousAt>=0&&now-previousAt>=500&&
            now-previousAt<=5000&&positionMs-previousPosition>=250)
            observedProgress=true;
        previousPosition=positionMs;previousAt=now;
        return observedProgress;
    }
}
