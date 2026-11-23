import in.ghartv.nova.DeviceClass;
public class DeviceClassSmoke {
 private static int checks;
 static void ok(boolean v){if(!v)throw new AssertionError("check "+checks);checks++;}
 public static void main(String[] a){
  ok(DeviceClass.tierOf(true,256,4000,8,34)==DeviceClass.Tier.LOW);
  ok(DeviceClass.tierOf(false,128,0,4,28)==DeviceClass.Tier.LOW);
  ok(DeviceClass.tierOf(false,256,1000,4,28)==DeviceClass.Tier.LOW);
  ok(DeviceClass.tierOf(false,256,2000,8,31)==DeviceClass.Tier.MID);
  ok(DeviceClass.tierOf(false,192,0,8,31)==DeviceClass.Tier.MID);
  ok(DeviceClass.tierOf(false,256,0,4,31)==DeviceClass.Tier.MID);
  ok(DeviceClass.tierOf(false,256,3900,8,34)==DeviceClass.Tier.HIGH);
  ok(DeviceClass.tierOf(false,256,0,0,34)==DeviceClass.Tier.HIGH);
  DeviceClass low=DeviceClass.of(DeviceClass.Tier.LOW),hi=DeviceClass.of(DeviceClass.Tier.HIGH),mid=DeviceClass.of(DeviceClass.Tier.MID);
  ok(low.maxVideoHeight==720&&!low.previewsByDefault&&mid.previewsByDefault&&hi.maxVideoHeight==2160);
  ok(low.targetBytes<mid.targetBytes&&mid.targetBytes<hi.targetBytes);
  ok(low.minBufferMs<=low.maxBufferMs&&hi.minBufferMs<=hi.maxBufferMs&&low.maxBufferMs<hi.maxBufferMs);
  ok(hi.maxBufferMs==30_000&&hi.targetBytes==32*1024*1024); // high tier = today's behaviour
  ok(low.label().equals("low"));
  System.out.println("DEVICE_CLASS="+checks+"_PASS");
 }
}
