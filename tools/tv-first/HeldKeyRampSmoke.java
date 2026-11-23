import in.ghartv.nova.HeldKeyRamp;
public final class HeldKeyRampSmoke {
    private static int checks;
    private static void ok(boolean value){checks++;if(!value)throw new AssertionError("HeldKeyRamp check "+checks);}
    /** Holds a key for holdMs with repeats every 50 ms; returns how many moves were allowed. */
    private static int moves(long holdMs){
        HeldKeyRamp r=new HeldKeyRamp();int n=0;long down=1000;
        if(r.allow(20,down,down,0))n++;
        for(long t=50;t<=holdMs;t+=50)if(r.allow(20,down,down+t,(int)(t/50)))n++;
        return n;
    }
    public static void main(String[] args){
        HeldKeyRamp r=new HeldKeyRamp();
        ok(r.allow(20,1000,1000,0));                                   // a tap moves at once
        ok(r.allow(20,2000,2000,0)&&r.allow(21,3000,3000,0));          // new press / new key moves at once
        ok(HeldKeyRamp.gapMs(0)==900);                                 // slow start
        ok(HeldKeyRamp.gapMs(-5)==900);                                // negative hold treated as 0
        ok(HeldKeyRamp.gapMs(6000)==225&&HeldKeyRamp.gapMs(60000)==225); // top speed reached and kept
        long prev=Long.MAX_VALUE;                                      // gap never grows while held: speed rises continuously
        for(long t=0;t<=8000;t+=100){long g=HeldKeyRamp.gapMs(t);ok(g<=prev);prev=g;}
        ok(HeldKeyRamp.gapMs(1000)<HeldKeyRamp.gapMs(0));              // rises already in the first second (no step)
        ok(HeldKeyRamp.gapMs(2000)<HeldKeyRamp.gapMs(1000));           // and keeps rising at 2 s
        ok(moves(500)<=2);                                             // 0.5 s hold: tap + at most one repeat
        ok(moves(3000)>moves(1500));                                   // holding longer moves more
        ok(moves(8000)>moves(4000));                                   // still speeding up past 4 s
        ok(moves(8000)<=8000/225+2);                                   // never faster than the top speed
        HeldKeyRamp h=new HeldKeyRamp();long down=10000,last=down;ok(h.allow(20,down,down,0));
        for(long t=50;t<=3000;t+=50){
            if(h.allow(20,down,down+t,(int)(t/50))){long gap=down+t-last;ok(gap>=HeldKeyRamp.gapMs(t-50)-50);last=down+t;}
        }
        ok(HeldKeyRamp.pointerDpPerSec(0)==120f);
        ok(HeldKeyRamp.pointerDpPerSec(3000)>HeldKeyRamp.pointerDpPerSec(1000));  // pointer speeds up continuously
        ok(HeldKeyRamp.pointerDpPerSec(6000)==420f&&HeldKeyRamp.pointerDpPerSec(60000)==420f);
        ok(HeldKeyRamp.pointerDpPerSec(-5)==120f);
        System.out.println("HELD_KEY_RAMP="+checks+"_PASS");
    }
}
