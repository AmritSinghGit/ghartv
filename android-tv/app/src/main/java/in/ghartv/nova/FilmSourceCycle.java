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

    /** Returns a source index, or -1 if no eligible untried choice remains.
     * Only a manual request wraps back to the first observable source.
     * Retry bookkeeping changes only when the caller explicitly wraps.
     */
    static int next(List<String> observed, Set<String> attempted, boolean manual) {
        for(int i=0;i<observed.size();i++){
            String label=observed.get(i);
            if(eligible(label)&&!attempted.contains(label))return i;
        }
        if(manual){
            attempted.clear();
            for(int i=0;i<observed.size();i++)
                if(eligible(observed.get(i)))return i;
        }
        return -1;
    }
}
