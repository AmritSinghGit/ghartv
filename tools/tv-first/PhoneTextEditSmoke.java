package in.ghartv.nova;
public final class PhoneTextEditSmoke {
    private static int tests=0;
    private static void check(String text,int start,int end,boolean clear,String expected,int caret){
        PhoneTextEdit.Result r=PhoneTextEdit.edit(text,start,end,clear);
        if(!r.value.equals(expected)||r.caret!=caret)
            throw new AssertionError("incorrect editing");
        tests++;
    }
    public static void main(String[] args){
        check("Avengers",8,8,false,"Avenger",7);
        check("Avengers",2,5,false,"Avers",2);
        check("Avengers",0,0,false,"Avengers",0);
        check("Avengers",8,8,true,"",0);
        check("🙂",2,2,false,"",0);
        check("a🙂b",3,3,false,"ab",1);
        check("नमस्ते",6,6,true,"",0);
        check("Series",0,6,false,"",0);
        check("Series",-1,-1,false,"Series",6);
        System.out.println("PHONE_TEXT_EDIT="+tests+"_PASS");
    }
}
