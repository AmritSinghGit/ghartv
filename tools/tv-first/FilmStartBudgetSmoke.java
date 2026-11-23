package in.ghartv.nova;
public final class FilmStartBudgetSmoke { public static void main(String[] args){
 FilmStartBudget b=new FilmStartBudget();b.begin(0);
 if(b.sample(13000,false,true,false,false,false,0,0,false)!=FilmStartBudget.State.STARTING)throw new AssertionError("13sec unobservable not a failure");
 if(b.sample(120000,false,true,false,false,false,0,0,false)!=FilmStartBudget.State.STARTING)throw new AssertionError("2min unobservable not a failure");
 if(b.sample(150000,false,true,false,false,false,0,0,false)!=FilmStartBudget.State.MANUAL)throw new AssertionError("late iframe must require manual rather than switch");
 b.begin(0);b.sample(120000,true,false,true,true,false,1,0,false);
 if(b.sample(122000,true,false,true,true,false,2,0,false)!=FilmStartBudget.State.PLAYING)throw new AssertionError("slow stream must be accepted on moving frames");
 b.begin(0);b.sample(500,true,false,true,true,false,1,0,false);
 if(b.sample(1700,true,false,true,true,false,2,0,false)!=FilmStartBudget.State.PLAYING)throw new AssertionError();
 b.cancel();if(b.sample(50000,true,false,true,true,false,3,0,false)!=FilmStartBudget.State.CANCELLED)throw new AssertionError();
 if(FilmStartBudget.MAX_ATTEMPTS!=12)throw new AssertionError("must support 12 offered source attempts");
 // A verified top-document media failure may request the next observed choice,
 // but an unknown iframe must never be rotated automatically.
 b.begin(0);
 for(int i=1;i<12;i++){
   if(b.sample(i*2000,true,false,false,false,false,0,3,false)!=FilmStartBudget.State.NEXT)
     throw new AssertionError("source "+i+" should permit next on proven media error");
   b.selected(i*2000+50);
 }
 if(b.sample(26000,true,false,false,false,false,0,3,false)!=FilmStartBudget.State.EXHAUSTED)
    throw new AssertionError("should stop after 12 attempts");
 b.begin(0);
 if(b.sample(150000,false,true,false,false,false,0,404,false)!=FilmStartBudget.State.MANUAL)
    throw new AssertionError("embedded frame 404 is not video-stream failure proof");
 System.out.println("START_BUDGET_SMOKE_PASS_12_SOURCE_ATTEMPTS");
}}