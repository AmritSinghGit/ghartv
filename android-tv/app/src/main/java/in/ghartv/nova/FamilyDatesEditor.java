package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.time.MonthDay;
import java.util.List;

/** Existing appearance route, expanded to local family CRUD; stable member keys never change. */
public final class FamilyDatesEditor {
    private FamilyDatesEditor() {}
    public static void show(Activity activity) {
        List<FamilyTheme.Birthday> rows=FamilyTheme.allBirthdays(activity);
        String[] labels=new String[rows.size()+1];
        labels[0]="＋ Add a person";
        for(int i=0;i<rows.size();i++) labels[i+1]=rows.get(i).name+"  ·  "+rows.get(i).dateLabel();
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle("Family · saved on this TV")
            .setItems(labels,(dialog,which)->edit(activity,which==0?null:rows.get(which-1)))
            .setNegativeButton("Done",(d,w)->activity.recreate());
        if(FamilyTheme.hasHiddenDefaults(activity)) builder.setNeutralButton("Restore removed defaults",(d,w)->
            new AlertDialog.Builder(activity).setTitle("Restore original family members?")
                .setMessage("Names and dates you edited are retained. Custom people are not recreated.")
                .setPositiveButton("Restore",(a,b)->{FamilyTheme.restoreHiddenDefaults(activity);show(activity);})
                .setNegativeButton("Cancel",(a,b)->show(activity)).show());
        builder.show();
    }
    private static void edit(Activity activity,FamilyTheme.Birthday birthday) {
        boolean adding=birthday==null;
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);
        int pad=TvUi.dp(activity,24);box.setPadding(pad,pad,pad,pad);
        box.addView(TvUi.label(activity,"Name and birthday · India Standard Time. Private to this TV.",13,TvUi.MUTED,false));
        EditText name=new EditText(activity),day=new EditText(activity),month=new EditText(activity);
        name.setHint("Name");name.setSingleLine(true);name.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        name.setText(adding?"":birthday.name);name.setContentDescription("Person name");
        day.setHint("Day (1–31)");month.setHint("Month (1–12)");
        day.setInputType(InputType.TYPE_CLASS_NUMBER);month.setInputType(InputType.TYPE_CLASS_NUMBER);
        day.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});month.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        day.setContentDescription("Birthday day");month.setContentDescription("Birthday month");
        if(!adding){day.setText(String.valueOf(birthday.date.getDayOfMonth()));month.setText(String.valueOf(birthday.date.getMonthValue()));}
        box.addView(name);
        LinearLayout dates=new LinearLayout(activity);dates.addView(day,new LinearLayout.LayoutParams(0,-2,1));dates.addView(month,new LinearLayout.LayoutParams(0,-2,1));box.addView(dates);
        box.addView(TvUi.label(activity,"4 October = day 4, month 10. Renaming preserves this person's settings.",12,TvUi.MUTED,false));
        if(!adding){
            android.widget.Button reset=TvUi.button(activity,"Reset saved date",false);
            reset.setOnClickListener(v->{FamilyTheme.resetDate(activity,birthday.key);
                for(FamilyTheme.Birthday b:FamilyTheme.allBirthdays(activity))if(b.key.equals(birthday.key)){
                    day.setText(String.valueOf(b.date.getDayOfMonth()));month.setText(String.valueOf(b.date.getMonthValue()));}
            });box.addView(reset,new LinearLayout.LayoutParams(-1,TvUi.dp(activity,38)));
        }
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(adding?"Add a person":"Edit "+birthday.name)
            .setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",(d,w)->show(activity));
        if(!adding)builder.setNeutralButton("Remove person",(d,w)->confirmRemove(activity,birthday));
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                MonthDay date=MonthDay.of(Integer.parseInt(month.getText().toString().trim()),Integer.parseInt(day.getText().toString().trim()));
                FamilyTheme.saveMember(activity,adding?null:birthday.key,name.getText().toString(),date);
                dialog.dismiss();show(activity);
            }catch(java.time.DateTimeException|NumberFormatException error){
                Toast.makeText(activity,"Enter a valid day and month. No change has been saved.",Toast.LENGTH_LONG).show();
            }catch(IllegalArgumentException|IllegalStateException error){Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }
    private static void confirmRemove(Activity activity,FamilyTheme.Birthday birthday){
        new AlertDialog.Builder(activity).setTitle("Remove "+birthday.name+"?")
            .setMessage("Remove this person from this TV's birthday list. No other person or account is changed.")
            .setPositiveButton("Remove",(d,w)->{
                try{FamilyTheme.removeMember(activity,birthday.key);show(activity);}
                catch(RuntimeException error){Toast.makeText(activity,"Could not remove this person; saved data is preserved.",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("Keep person",(d,w)->show(activity)).show();
    }
}
