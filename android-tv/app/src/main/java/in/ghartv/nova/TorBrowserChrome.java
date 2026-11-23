package in.ghartv.nova;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native top-edge Tor address bar; content continues in the same WebView below. */
final class TorBrowserChrome extends FrameLayout {
    interface Host {
        void navigate(String url);void back();void forward();void reload();
        void close();void find();void bookmarks();void settings();void phone();void page();
    }
    private final Activity owner;
    private Host host;
    private final LinearLayout strip;
    private Button backButton;
    private EditText address;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean showing,locked;
    private float phoneX;
    private final View phoneDot;
    private final Runnable hide=()->{
        if(showing&&!locked&&!address.hasFocus()){
            hideBar();host.page();
        }
    };
    TorBrowserChrome(Activity a,Host h){
        super(a);owner=a;host=h;
        setBackground(TvUi.gradient(0xFA071B2B,0xFA0C3542,0,0xFF31616B,1f,a));
        setPadding(TvUi.dp(a,12),TvUi.dp(a,7),TvUi.dp(a,12),TvUi.dp(a,8));
        strip=new LinearLayout(a);strip.setGravity(Gravity.CENTER_VERTICAL);
        addView(strip,new FrameLayout.LayoutParams(-1,TvUi.dp(a,52),Gravity.TOP));
        backButton=add("‹",48,h::back);
        add("›",48,h::forward);
        add("↻",48,h::reload);
        address=new EditText(a);address.setSingleLine(true);
        address.setTextColor(TvUi.TEXT);address.setHintTextColor(TvUi.MUTED);
        address.setHint("Search or enter a secure website");
        address.setTextSize(16);address.setSelectAllOnFocus(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setPadding(TvUi.dp(a,15),0,TvUi.dp(a,15),0);
        address.setBackground(TvUi.rounded(0xFF0B2532,12,0xFF4E8A93,1,a));
        strip.addView(address,new LinearLayout.LayoutParams(0,TvUi.dp(a,47),1f));
        address.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_GO||
              (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                goAddress();return true;
            }return false;
        });
        add("Go",55,this::goAddress);
        add("⋮",46,this::moreActions);
        add("Page ↓",90,h::page);
        phoneDot=new View(a);
        phoneDot.setBackground(TvUi.rounded(0xFF8CF4D5,9,Color.WHITE,1,a));
        FrameLayout.LayoutParams pointer=new FrameLayout.LayoutParams(TvUi.dp(a,12),
            TvUi.dp(a,12),Gravity.TOP|Gravity.LEFT);
        pointer.topMargin=TvUi.dp(a,43);addView(phoneDot,pointer);
        phoneDot.setVisibility(GONE);
        setVisibility(GONE);
        address.setOnFocusChangeListener((v,focus)->{
            if(focus)locked=true;
            else {locked=false;autoHide();}
        });
    }
    private Button add(String text,int width,Runnable action){
        Button b=TvUi.button(owner,text,false);
        b.setTextSize(15);b.setPadding(0,0,0,0);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(TvUi.dp(owner,width),
            TvUi.dp(owner,47));
        bp.leftMargin=TvUi.dp(owner,4);strip.addView(b,bp);
        b.setOnClickListener(v->{locked=false;action.run();});
        return b;
    }
    private void moreActions(){
        new TvModal(owner,"TOR WEB","Browser options",
                "Use the TV remote or paired phone. All websites stay on the Tor route.")
            .action("Find",false,d->{d.dismiss();host.find();})
            .action("Bookmarks",false,d->{d.dismiss();host.bookmarks();})
            .action("More",true,d->{d.dismiss();moreSecondPage();}).show();
    }
    private void moreSecondPage(){
        new TvModal(owner,"TOR WEB","More tools",
                "Connect a phone, choose privacy settings or return to the page.")
            .action("Phone",false,d->{d.dismiss();host.phone();})
            .action("Network settings",false,d->{d.dismiss();host.settings();})
            .action("Page",true,d->{d.dismiss();host.page();}).show();
    }
    void goFromPhone(){goAddress();}
    private void goAddress(){
        String value=address.getText().toString();
        String url=TorWebUrlPolicy.addressOrSearch(value);
        if(url==null){
            address.setError("Enter a public HTTPS site without a port or login");
            address.requestFocus();return;
        }
        address.clearFocus();locked=false;host.navigate(url);
        autoHide();
    }
    boolean showing(){return showing;}
    EditText address(){return address;}
    void showBar(String value,boolean focus){
        if(value!=null&&!value.isEmpty())address.setText(value);
        setVisibility(VISIBLE);bringToFront();setTranslationY(0);
        showing=true;handler.removeCallbacks(hide);
        if(focus){address.requestFocus();locked=true;}
        else{locked=false;backButton.requestFocus();autoHide();}
    }
    void current(String url){
        if(url!=null&&TorWebUrlPolicy.allowed(url)&&!address.hasFocus())
            address.setText(url);
        autoHide();
    }
    void autoHide(){
        handler.removeCallbacks(hide);
        if(showing&&!locked)handler.postDelayed(hide,6500);
    }
    void hideBar(){
        showing=false;locked=false;handler.removeCallbacks(hide);
        phoneDot.setVisibility(GONE);address.clearFocus();setVisibility(GONE);
    }
    /** Paired phone's mouse keeps working over the auto-hidden toolbar, no system injection. */
    void movePhone(float dx,float dy){
        if(!showing)return;
        phoneX=Math.max(0,Math.min(getWidth()-1,phoneX+dx));
        phoneDot.setTranslationX(phoneX);
        phoneDot.setVisibility(VISIBLE);autoHide();
        focusAt(phoneX);
    }
    private void focusAt(float x){
        for(int i=0;i<strip.getChildCount();i++){
            View v=strip.getChildAt(i);
            if(x>=v.getLeft()&&x<v.getRight()&&v.isFocusable()){
                v.requestFocus();break;
            }
        }
    }
    void phoneClick(){
        if(!showing)return;
        View focused=strip.findFocus();
        if(focused instanceof Button)focused.performClick();
        else if(focused==address)address.requestFocus();
        autoHide();
    }
    void dispose(){handler.removeCallbacksAndMessages(null);}
}
