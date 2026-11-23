package in.ghartv.nova;

/** Pure device-class rules (no Android types): one place decides buffers, picture size cap and previews for weak vs strong TVs. */
public final class DeviceClass {
    public enum Tier { LOW, MID, HIGH }
    public final Tier tier;
    public final int minBufferMs, maxBufferMs, targetBytes, maxVideoHeight;
    public final boolean previewsByDefault;
    private DeviceClass(Tier t,int minMs,int maxMs,int mb,int h,boolean previews){tier=t;minBufferMs=minMs;maxBufferMs=maxMs;targetBytes=mb*1024*1024;maxVideoHeight=h;previewsByDefault=previews;}
    /** memoryClassMb = ActivityManager.getMemoryClass(); totalRamMb 0 = unknown; cores 0 = unknown. */
    public static Tier tierOf(boolean lowRamFlag,int memoryClassMb,int totalRamMb,int cores,int sdk){
        if(lowRamFlag||memoryClassMb<=128||(totalRamMb>0&&totalRamMb<=1100)||sdk<=24)return Tier.LOW;
        if(memoryClassMb<=192||(totalRamMb>0&&totalRamMb<=2200)||(cores>0&&cores<=4))return Tier.MID;
        return Tier.HIGH;
    }
    public static DeviceClass of(Tier t){
        switch(t){
            case LOW: return new DeviceClass(t,6_000,20_000,12,720,false);
            case MID: return new DeviceClass(t,8_000,25_000,20,1080,true);
            default:  return new DeviceClass(t,10_000,30_000,32,2160,true);
        }
    }
    public static DeviceClass of(boolean lowRamFlag,int memoryClassMb,int totalRamMb,int cores,int sdk){return of(tierOf(lowRamFlag,memoryClassMb,totalRamMb,cores,sdk));}
    public String label(){return tier.name().toLowerCase(java.util.Locale.ROOT);}
}
