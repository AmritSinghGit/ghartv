package in.ghartv.nova;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** Timestamp and ordering rules shared by the guide and player. No network or Android state. */
public final class GuideTimeline {
    private GuideTimeline() {}
    public static long epoch(Object value) {
        if(value==null) return 0L;
        String text=String.valueOf(value).trim();
        try {
            double n=Double.parseDouble(text);
            if(!Double.isFinite(n)||n<=0) return 0;
            if(n<100_000_000_000L)n*=1000;
            else if(n>100_000_000_000_000L)n/=1000;
            long result=(long)n;
            return result>=946684800000L&&result<4133980800000L?result:0L;
        } catch(NumberFormatException ignored) {}
        try {return Instant.parse(text).toEpochMilli();}catch(RuntimeException ignored){}
        try {return OffsetDateTime.parse(text).toInstant().toEpochMilli();}catch(RuntimeException ignored){}
        for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd HH:mm"}){
            try{return LocalDateTime.parse(text,DateTimeFormatter.ofPattern(pattern)).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();}catch(RuntimeException ignored){}
        }
        return 0;
    }
    public static int current(long[] starts,long[] ends,long now){
        int best=-1;
        for(int i=0;i<Math.min(starts.length,ends.length);i++)
            if(starts[i]>0&&starts[i]<=now&&ends[i]>now&&(best<0||starts[i]>starts[best]))best=i;
        return best;
    }
    public static int next(long[] starts,long[] ends,long now){
        int current=current(starts,ends,now),best=-1;
        long boundary=current<0?now:ends[current];
        for(int i=0;i<Math.min(starts.length,ends.length);i++)
            if(starts[i]>now&&starts[i]>=boundary&&ends[i]>starts[i]&&(best<0||starts[i]<starts[best]))best=i;
        return best;
    }
    public static int nextChannelIndex(int[] numbers,int current,int direction){
        if(numbers.length==0)return -1;
        for(int i=0;i<numbers.length;i++)if(numbers[i]==current)return (i+(direction>=0?1:-1)+numbers.length)%numbers.length;
        return direction>=0?0:numbers.length-1;
    }
}
