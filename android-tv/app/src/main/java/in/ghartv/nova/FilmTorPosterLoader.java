package in.ghartv.nova;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Native poster loading follows the same explicit film-only route as the WebView. */
final class FilmTorPosterLoader {
    private static final long MAX_IMAGE_BYTES=8L*1024L*1024L;
    private final Activity activity;private final FilmTorController controller;private final ExecutorService io=Executors.newFixedThreadPool(2);
    FilmTorPosterLoader(Activity activity,FilmTorController controller){this.activity=activity;this.controller=controller;}
    void load(ImageView view,String url){if(!FilmHomeView.safePoster(url))return;int port=controller==null?0:controller.posterProxyPort();if(port<=0||!FilmTorFallback.torScope(url)){direct(view,url);return;}io.execute(()->loadThroughTor(view,url,port));}
    private void direct(ImageView view,String url){if(activity.isDestroyed())return;Glide.with(activity).load(url).override(240,360).centerCrop().diskCacheStrategy(DiskCacheStrategy.NONE).placeholder(new ColorDrawable(TvUi.SURFACE_2)).error(new ColorDrawable(TvUi.SURFACE_2)).into(view);}
    private void loadThroughTor(ImageView view,String url,int port){
        byte[] bytes=null;
        try{OkHttpClient client=new OkHttpClient.Builder().proxy(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",port))).connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).build();Request request=new Request.Builder().url(url).get().header("Accept","image/*").build();try(Response response=client.newCall(request).execute()){ResponseBody body=response.body();long length=body==null?-1L:body.contentLength();if(response.isSuccessful()&&body!=null&&length<=MAX_IMAGE_BYTES){byte[] candidate=body.bytes();if(candidate.length<=MAX_IMAGE_BYTES)bytes=candidate;}}}catch(Exception ignored){}
        final byte[] image=bytes;activity.runOnUiThread(()->{if(activity.isDestroyed()||controller==null||!controller.active())return;if(image==null){view.setImageDrawable(new ColorDrawable(TvUi.SURFACE_2));return;}Glide.with(activity).load(image).override(240,360).centerCrop().diskCacheStrategy(DiskCacheStrategy.NONE).placeholder(new ColorDrawable(TvUi.SURFACE_2)).error(new ColorDrawable(TvUi.SURFACE_2)).into(view);});
    }
    void close(){io.shutdownNow();}
}
