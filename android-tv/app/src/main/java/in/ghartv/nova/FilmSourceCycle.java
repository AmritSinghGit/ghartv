package in.ghartv.nova;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source rotation based only on provider-observed, numbered source buttons. */
final class FilmSourceCycle {
    private static final Pattern LABEL =
        Pattern.compile("(?i)^(?:player|server|source)\\s*#?\\s*(\\d{1,2})$");

    private FilmSourceCycle() {}

    private static boolean eligible(String label) {
        if(label==null)return false;
        Matcher match=LABEL.matcher(label.trim());
        if(!match.matches())return false;
        int number=Integer.parseInt(match.group(1));
        return number>=1&&number<=12;
    }

    private static int lowestUntried(List<String> observed,Set<String> attempted,int after){
        int best=-1,number=Integer.MAX_VALUE;
        for(int i=0;i<observed.size();i++){
            String label=observed.get(i);
            if(!eligible(label)||attempted.contains(label))continue;
            Matcher match=LABEL.matcher(label.trim());
            if(match.matches()){
                int next=Integer.parseInt(match.group(1));
                if(next>after&&next<number){best=i;number=next;}
            }
        }
        return best;
    }
    /** The provider can reorder its buttons. Prefer numeric 1,2,3,
     * then 4,5,6..., not incidental DOM order. Manual wrap is explicit.
     */
    static int next(List<String> observed,Set<String> attempted,boolean manual){
        return next(observed,attempted,manual,"");
    }
    static int next(List<String> observed,Set<String> attempted,boolean manual,String current){
        int after=0;
        if(eligible(current)){
            Matcher match=LABEL.matcher(current.trim());
            if(match.matches())after=Integer.parseInt(match.group(1));
        }
        int next=lowestUntried(observed,attempted,after);
        if(next>=0)return next;
        // A deliberate Next can wrap to another untried source; automatic
        // retries never wrap/replay an already attempted player.
        if(manual){
            next=lowestUntried(observed,attempted,0);
            if(next>=0)return next;
            attempted.clear();
            return lowestUntried(observed,attempted,0);
        }
        return -1;
    }
}
