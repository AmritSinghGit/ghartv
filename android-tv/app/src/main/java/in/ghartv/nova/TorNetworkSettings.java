package in.ghartv.nova;
import android.app.Activity;
import android.content.Context;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.app.Dialog;

/** Central route policy. Exit country is a preference, not an IP or anonymity guarantee. */
final class TorNetworkSettings {
    private static final String FILE="ghartv.tor-network";
    private static final String KEY="exit_country";
    private static final String[] CODES={"auto","us","nl","de","gb","in","ca","ch"};
    private static final String[] LABELS={"Automatic · recommended","United States","Netherlands",
        "Germany","United Kingdom","India","Canada","Switzerland"};
    static String country(Context context){
        String value=context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(KEY,"auto");
        for(String code:CODES)if(code.equals(value))return value;
        return "auto";
    }
    static boolean valid(String code){
        for(String item:CODES)if(item.equals(code))return true;
        return false;
    }
    static void show(Activity activity){
        String now=country(activity);
        TvModal modal=new TvModal(activity,"NETWORK & PRIVACY","Connection settings",
            "JioTV: direct connection. Public IPTV: Tor. FlixMomo and Tor Web: Tor only. "+
            "Choose a preferred Tor exit country. It may slow connections and does not "+
            "guarantee a location; change takes effect when Tor reconnects.");
        final Dialog[] currentDialog=new Dialog[1];
        for(int i=0;i<CODES.length;i++){
            final String choice=CODES[i];
            Button button=TvUi.button(activity,(choice.equals(now)?"●  ":"○  ")+LABELS[i],choice.equals(now));
            button.setTextSize(17);
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,TvUi.dp(activity,48));
            pp.bottomMargin=TvUi.dp(activity,7);
            modal.add(button);
            button.setOnClickListener(v->{
                activity.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putString(KEY,choice).apply();
                android.widget.Toast.makeText(activity,"Saved. Reconnect Tor to use "+LABELS[index(choice)]+".",android.widget.Toast.LENGTH_LONG).show();
                if(currentDialog[0]!=null)currentDialog[0].dismiss();
                show(activity);
            });
        }
        currentDialog[0]=modal.action("Close",true,Dialog::dismiss).show();
    }
    private static int index(String choice){for(int i=0;i<CODES.length;i++)if(CODES[i].equals(choice))return i;return 0;}
}
