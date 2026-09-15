import in.ghartv.nova.GuideTimeline;
public class GuideRulesSmoke{
 static void eq(long got,long expected){if(got!=expected)throw new AssertionError(got+" != "+expected);}
 public static void main(String[] args){
  long time=1757939400000L;
  eq(GuideTimeline.epoch(time),time);eq(GuideTimeline.epoch(time/1000),time);eq(GuideTimeline.epoch(""+time),time);eq(GuideTimeline.epoch(time*1000),time);
  eq(GuideTimeline.epoch("2026-09-15T05:30:00+05:30"),1789430400000L);
  eq(GuideTimeline.epoch("bad"),0);eq(GuideTimeline.epoch(0),0);eq(GuideTimeline.epoch("12:30"),0);
  long[] starts={300,100,200,0},ends={400,200,300,900};eq(GuideTimeline.current(starts,ends,225),2);eq(GuideTimeline.next(starts,ends,225),0);eq(GuideTimeline.current(starts,ends,300),0);eq(GuideTimeline.next(starts,ends,500),-1);
  int[] channels={81,7,99};eq(GuideTimeline.nextChannelIndex(channels,81,1),1);eq(GuideTimeline.nextChannelIndex(channels,99,1),0);eq(GuideTimeline.nextChannelIndex(channels,81,-1),2);eq(GuideTimeline.nextChannelIndex(channels,22,1),0);eq(GuideTimeline.nextChannelIndex(new int[]{},1,1),-1);
  System.out.println("GUIDE_TIME_AND_SCOPED_ORDER=PASS");
 }
}