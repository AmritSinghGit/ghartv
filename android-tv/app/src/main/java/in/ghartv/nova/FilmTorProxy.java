package in.ghartv.nova;

import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.util.concurrent.Executor;

/** Process-scoped proxy for the single film WebView. Jio/update/telemetry hosts are explicitly bypassed. */
final class FilmTorProxy implements FilmTorController.ProxyAdapter {
    private static final Object LOCK=new Object();
    private static Object owner;
    private final Executor mainExecutor;
    private final Object ownerToken=new Object();
    private volatile boolean applied;

    FilmTorProxy(Context context){mainExecutor=ContextCompat.getMainExecutor(context.getApplicationContext());}
    @Override public boolean supported(){return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE);}
    @Override public boolean applied(){return applied;}
    @Override public void apply(int httpPort,FilmTorController.Result callback){
        if(!supported()||httpPort<1||httpPort>65535){callback.done(false);return;}
        synchronized(LOCK){
            if(owner!=null&&owner!=ownerToken){
                android.util.Log.w("GharTvFilmTorProxy","Apply refused: another scoped proxy owner is active");
                callback.done(false);return;
            }
            owner=ownerToken;
        }
        try{
            ProxyConfig.Builder builder=new ProxyConfig.Builder()
                .addProxyRule("http://127.0.0.1:"+httpPort).bypassSimpleHostnames();
            for(String host:new String[]{"jiotvapi.media.jio.com","jiotvapi.cdn.jio.com",
                    "api.github.com","ghartv-telemetry.ghartv-47d9a0.workers.dev"})
                builder.addBypassRule(host);
            final ProxyConfig config=builder.build();
            // WebView provider startup can throw "Must be started before we
            // block!" when setProxyOverride first runs on a binder/test thread.
            // The caller does not navigate until the asynchronous ACK below.
            mainExecutor.execute(()->{
                boolean ours;
                synchronized(LOCK){ours=owner==ownerToken;}
                if(!ours){callback.done(false);return;}
                try{
                    ProxyController.getInstance().setProxyOverride(config,mainExecutor,()->{
                        synchronized(LOCK){applied=owner==ownerToken;}
                        callback.done(applied);
                    });
                }catch(RuntimeException failure){failure("apply",failure);releaseOwner();callback.done(false);}
            });
        }catch(RuntimeException failure){failure("prepare",failure);releaseOwner();callback.done(false);}
    }
    @Override public void clear(FilmTorController.Result callback){
        boolean ours;synchronized(LOCK){ours=owner==ownerToken;}
        if(!ours&&!applied){callback.done(true);return;}
        try{
            // Clearing WebView's process-wide proxy must also use the UI
            // thread; never leave a new direct route active on clear failure.
            mainExecutor.execute(()->{
                try{
                    ProxyController.getInstance().clearProxyOverride(mainExecutor,()->{
                        applied=false;releaseOwner();callback.done(true);
                    });
                }catch(RuntimeException failure){failure("clear",failure);callback.done(false);}
            });
        }catch(RuntimeException failure){failure("clear-dispatch",failure);callback.done(false);}
    }
    private static void failure(String operation,RuntimeException failure){
        String reason=String.valueOf(failure.getMessage());
        // Low-cardinality, privacy-safe diagnostics only.
        reason=reason.replaceAll("(?i)https?://[^\\s]+","<url>")
            .replaceAll("[A-Za-z0-9_\\-]{20,}","<name>")
            .replaceAll("[0-9]{2,}","<n>");
        if(reason.length()>140)reason=reason.substring(0,140);
        Throwable cause=failure.getCause();
        android.util.Log.w("GharTvFilmTorProxy",
            operation+" failed: "+failure.getClass().getSimpleName()+
            " cause="+(cause==null?"none":cause.getClass().getSimpleName())+
            " detail="+reason);
    }
    private void releaseOwner(){synchronized(LOCK){if(owner==ownerToken)owner=null;}}
}
