package in.ghartv.nova;

/** Pure validation shared by the device-local editor. No names or dates are sent anywhere. */
public final class FamilyMemberRules {
    public static final int MAX_MEMBERS=32;
    private FamilyMemberRules() {}
    public static String name(String input) {
        String name=input==null?"":input.trim();
        if(name.isEmpty() || name.codePointCount(0,name.length())>60)
            throw new IllegalArgumentException("Use a name between 1 and 60 characters.");
        for(int i=0;i<name.length();) {
            int c=name.codePointAt(i);i+=Character.charCount(c);
            if(Character.isISOControl(c) || c==0x2028 || c==0x2029)
                throw new IllegalArgumentException("Use one line for the name.");
        }
        return name;
    }
}
