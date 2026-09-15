package in.ghartv.nova;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.time.MonthDay;
import java.util.List;

/** Device-local date corrections. Neither names nor dates go to diagnostic events. */
public final class FamilyDatesEditor {
    private FamilyDatesEditor() {}
    public static void show(Activity activity) {
        List<FamilyTheme.Birthday> rows = FamilyTheme.allBirthdays(activity);
        String[] labels = new String[rows.size()];
        for (int i=0;i<rows.size();i++) labels[i]=rows.get(i).name+" — "+rows.get(i).dateLabel();
        new AlertDialog.Builder(activity).setTitle("Family dates · India Standard Time")
            .setItems(labels,(dialog,which)->edit(activity,rows.get(which)))
            .setNegativeButton("Close",null).show();
    }
    private static void edit(Activity activity, FamilyTheme.Birthday birthday) {
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);
        int pad=TvUi.dp(activity,24);box.setPadding(pad,pad,pad,pad);
        box.addView(TvUi.label(activity,"Day (1–31), then month (1–12). Saved on this TV only.",14,TvUi.MUTED,false));
        EditText day=new EditText(activity),month=new EditText(activity);
        day.setHint("Day");month.setHint("Month");
        day.setInputType(InputType.TYPE_CLASS_NUMBER);month.setInputType(InputType.TYPE_CLASS_NUMBER);
        day.setText(String.valueOf(birthday.date.getDayOfMonth()));month.setText(String.valueOf(birthday.date.getMonthValue()));
        box.addView(day);box.addView(month);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(birthday.name+" · "+birthday.dateLabel())
            .setView(box).setPositiveButton("Save",null)
            .setNeutralButton("Reset saved correction",(d,w)->{FamilyTheme.resetDate(activity,birthday.key);show(activity);})
            .setNegativeButton("Cancel",(d,w)->show(activity)).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                MonthDay date=MonthDay.of(Integer.parseInt(month.getText().toString().trim()),Integer.parseInt(day.getText().toString().trim()));
                FamilyTheme.setDate(activity,birthday.key,date);dialog.dismiss();show(activity);
            } catch(RuntimeException error) { Toast.makeText(activity,"Enter a valid day and month; 4 January means day 4, month 1.",Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }
}
