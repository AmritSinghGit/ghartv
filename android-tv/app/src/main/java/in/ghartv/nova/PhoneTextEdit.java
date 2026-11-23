package in.ghartv.nova;

/** Bounded text editing shared by film/guide native fields; unicode-aware. */
final class PhoneTextEdit {
    static final class Result {
        final String value;
        final int caret;
        Result(String text, int pos){value=text;caret=pos;}
    }
    static Result edit(String text,int start,int end,boolean clear){
        if(text==null)text="";
        if(clear)return new Result("",0);
        if(start<0||end<0)return new Result(text,text.length());
        int a=Math.min(Math.max(0,Math.min(start,end)),text.length());
        int b=Math.min(Math.max(0,Math.max(start,end)),text.length());
        // A collapsed selection removes precisely one Unicode codepoint.
        if(a==b&&a>0)a=text.offsetByCodePoints(a,-1);
        return new Result(text.substring(0,a)+text.substring(b),a);
    }
    private PhoneTextEdit(){}
}
