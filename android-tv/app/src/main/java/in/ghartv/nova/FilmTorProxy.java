package in.ghartv.nova;

import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.util.concurrent.Executor;

/** Process-scoped WebView proxy constrained by reverse-bypass to fixed film hosts. */
final class FilmTorProxy implements FilmTorController.ProxyAdapter {
    private static final Object LOCK=new Object();
    private static Object owner;
    private final Executor mainExecutor;
    private final Object ownerToken=new Object();
    private volatile boolean applied;

    FilmTorProxy(Context context){mainExecutor=ContextCompat.getMainExecutor(context.getApplicationContext());}
    @Override public boolean supported(){return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)&&WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS);}
    @Override public boolean applied(){return applied;}
    @Override public void apply(int httpPort,FilmTorController.Result callback){
        if(!supported()||httpPort<1||httpPort>65535){callback.done(false);return;}
        synchronized(LOCK){if(owner!=null&&owner!=ownerToken){callback.done(false);return;}owner=ownerToken;}
        try{
            ProxyConfig.Builder builder=new ProxyConfig.Builder().addProxyRule("http://127.0.0.1:"+httpPort).setReverseBypassEnabled(true);
            for(String host:FilmTorFallback.proxyHosts())builder.addBypassRule(host);
            ProxyController.getInstance().setProxyOverride(builder.build(),mainExecutor,()->{applied=true;callback.done(true);});
        }catch(RuntimeException failure){releaseOwner();callback.done(false);}
    }
    @Override public void clear(FilmTorController.Result callback){
        boolean ours;synchronized(LOCK){ours=owner==ownerToken;}
        if(!ours&&!applied){callback.done(true);return;}
        try{ProxyController.getInstance().clearProxyOverride(mainExecutor,()->{applied=false;releaseOwner();callback.done(true);});}
        catch(RuntimeException failure){callback.done(false);}
    }
    private void releaseOwner(){synchronized(LOCK){if(owner==ownerToken)owner=null;}}
}
