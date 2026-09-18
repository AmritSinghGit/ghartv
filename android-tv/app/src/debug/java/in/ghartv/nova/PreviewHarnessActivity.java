package in.ghartv.nova;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.media3.ui.PlayerView;
import java.util.concurrent.atomic.AtomicInteger;
/** Debug-only, controlled-media exercise of the actual production preview controller. */
public class PreviewHarnessActivity extends Activity {
 public HeroPreviewController controller;public ImageView poster;public TextView status;public PlayerView video;
 public AtomicInteger calls=new AtomicInteger(),cancels=new AtomicInteger();public volatile String lastId="";
 @Override public void onCreate(Bundle b){super.onCreate(b);
  getSharedPreferences("ghartv_comfort_v1",MODE_PRIVATE).edit().clear().commit();
  FrameLayout f=new FrameLayout(this);video=new PlayerView(this);f.addView(video,new FrameLayout.LayoutParams(-1,-1));
  poster=new ImageView(this);poster.setBackgroundColor(0xff153340);f.addView(poster,new FrameLayout.LayoutParams(-1,-1));
  ProgressBar spinner=new ProgressBar(this);f.addView(spinner,new FrameLayout.LayoutParams(50,50));
  status=new TextView(this);status.setTextColor(-1);f.addView(status,new FrameLayout.LayoutParams(-1,60));setContentView(f);
  controller=new HeroPreviewController(this,video,poster,spinner,status,new HeroPreviewController.Resolver(){
   public PlaybackInfo fetch(Channel c)throws Exception{calls.incrementAndGet();lastId=c.id;
    if("slow".equals(c.id))Thread.sleep(6500);
    PlaybackInfo i=new PlaybackInfo();i.streamUrl="asset:///preview-fixture.mp4";return i;}
   public void cancel(){cancels.incrementAndGet();}
  });
 }
 public void focus(String id){Channel c=new Channel();c.id=id;c.number=Math.abs(id.hashCode())%1000;c.name="Fixture";c.language="Fixture";c.category="Fixture";controller.select(c);controller.focused(true);}
 @Override public void onDestroy(){controller.release();super.onDestroy();}
}
