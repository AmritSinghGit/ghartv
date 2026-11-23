package in.ghartv.nova;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;

/** Consistent high-contrast TV-native modal; intentionally never platform default alerts. */
final class TvModal {
    interface Click{void action(Dialog dialog);}
    private final Activity owner;
    private final Dialog dialog;
    private final LinearLayout content,buttons;
    TvModal(Activity a,String eyebrow,String title,String subtitle){
        owner=a;dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(TvUi.dp(a,28),TvUi.dp(a,25),TvUi.dp(a,28),TvUi.dp(a,25));
        root.setBackground(TvUi.gradient(0xFF081C2C,0xFF0C3440,24,
                0xFF418A93,1.1f,a));
        TextView tag=TvUi.label(a,eyebrow,12,TvUi.MINT,true);
        tag.setLetterSpacing(.12f);root.addView(tag,new LinearLayout.LayoutParams(-1,-2));
        TextView header=TvUi.label(a,title,25,TvUi.TEXT,true);
        LinearLayout.LayoutParams head=new LinearLayout.LayoutParams(-1,-2);head.topMargin=TvUi.dp(a,8);
        root.addView(header,head);
        if(subtitle!=null&&!subtitle.isEmpty()){
            TextView detail=TvUi.label(a,subtitle,15,TvUi.MUTED,false);
            detail.setLineSpacing(TvUi.dp(a,3),1.08f);
            LinearLayout.LayoutParams desc=new LinearLayout.LayoutParams(-1,-2);
            desc.topMargin=TvUi.dp(a,10);root.addView(detail,desc);
        }
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);
        content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0,TvUi.dp(a,14),0,TvUi.dp(a,12));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        buttons=new LinearLayout(a);buttons.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        root.addView(buttons,new LinearLayout.LayoutParams(-1,TvUi.dp(a,58)));
        dialog.setContentView(root);
    }
    TvModal add(View v){content.addView(v,new LinearLayout.LayoutParams(-1,-2));return this;}
    TvModal action(String label,boolean primary,Click click){
        Button b=TvUi.button(owner,label,primary);b.setTextSize(16);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,TvUi.dp(owner,51),1f);
        bp.leftMargin=TvUi.dp(owner,8);buttons.addView(b,bp);
        b.setOnClickListener(v->click.action(dialog));
        return this;
    }
    Dialog show(){
        dialog.show();Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(Math.min(TvUi.dp(owner,730),
                owner.getResources().getDisplayMetrics().widthPixels-TvUi.dp(owner,40)),
                Math.min(TvUi.dp(owner,570),
                owner.getResources().getDisplayMetrics().heightPixels-TvUi.dp(owner,48)));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.78f;w.setAttributes(lp);
        }
        if(buttons.getChildCount()>0)buttons.getChildAt(buttons.getChildCount()-1).requestFocus();
        return dialog;
    }
}
