import in.ghartv.nova.FamilyMemberRules;
import java.time.MonthDay;
public final class FamilyRulesSmoke {
 public static void main(String[] args){
  if(!FamilyMemberRules.name(" Simrat ").equals("Simrat"))throw new AssertionError();
  if(!FamilyMemberRules.name("ਸਿਮਰਤ").equals("ਸਿਮਰਤ"))throw new AssertionError();
  for(String bad:new String[]{"", "\n", "Hi\nthere", "x".repeat(61)}){
   try{FamilyMemberRules.name(bad);throw new AssertionError("Accepted invalid name");}catch(IllegalArgumentException expected){}
  }
  MonthDay.of(2,29);
  try{MonthDay.of(4,31);throw new AssertionError("Accepted invalid date");}catch(java.time.DateTimeException expected){}
  System.out.println("FAMILY_NAME_UNICODE_LENGTH_AND_CALENDAR_VALIDATION=PASS");
 }
}
