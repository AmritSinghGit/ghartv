import in.ghartv.nova.PreviewGate;
public class PreviewGateSmoke {
 private static int checks;
 static void ok(boolean v){if(!v)throw new AssertionError("check "+checks);checks++;}
 public static void main(String[] args){
  ok(PreviewGate.defaultEnabled(null));ok(PreviewGate.defaultEnabled(true));ok(!PreviewGate.defaultEnabled(false));
  PreviewGate g=new PreviewGate();g.select("a");ok(g.arm()==-1);g.focus(true);
  long a=g.arm();ok(a>=0);ok(g.arm()==-1);g.select("b");ok(!g.begin(a));
  long b=g.arm();ok(g.begin(b));ok(g.firstFrame(b));ok(!g.firstFrame(b));
  ok(g.finish(b,false));ok(g.phase()==PreviewGate.Phase.FINISHED);ok(g.arm()==-1);ok(!g.firstFrame(b));
  g.focus(false);g.focus(true);long again=g.arm();ok(g.begin(again));g.focus(false);ok(!g.firstFrame(again));ok(g.arm()==-1);
  g.focus(true);long timeout=g.arm();ok(g.begin(timeout));ok(g.finish(timeout,true));ok(g.arm()==-1);
  g.select("c");long c=g.arm();ok(g.begin(c));g.enabled(false);ok(!g.current(c));ok(g.arm()==-1);
  g.enabled(true);long resumed=g.arm();ok(g.begin(resumed));g.select("");ok(!g.current(resumed));ok(g.arm()==-1);
  ok(PreviewGate.FOCUS_DELAY_MS==650L);ok(PreviewGate.FIRST_FRAME_BUDGET_MS==4500L);ok(PreviewGate.VISIBLE_PREVIEW_MS==12000L);
  System.out.println("PREVIEW_GATE="+checks+"_PASS");
 }
}
