package in.ghartv.nova;
public final class FilmStartBudgetSmoke { public static void main(String[] args){
 FilmStartBudget b=new FilmStartBudget();b.begin(0);
 if(b.sample(13000,false,true,false,false,false,0,0,false)!=FilmStartBudget.State.MANUAL)throw new AssertionError();
 b.begin(0);b.sample(500,true,false,true,true,false,1,0,false);
 if(b.sample(1700,true,false,true,true,false,2,0,false)!=FilmStartBudget.State.PLAYING)throw new AssertionError();
 b.cancel();if(b.sample(50000,true,false,true,true,false,3,0,false)!=FilmStartBudget.State.CANCELLED)throw new AssertionError();
 System.out.println("START_BUDGET_SMOKE_PASS");
}}