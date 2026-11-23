package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/** Coordinates the owner-selected film route, app-local Tor and scoped WebView proxy callbacks. */
final class FilmTorController {
    interface Result{void done(boolean ok);} interface TransportResult{void ready(boolean ok,int httpPort);} interface ConsentResult{void answer(boolean allowed);}
    interface Transport{void start(TransportResult callback);int currentHttpPort();void cancel();void close();}
    interface ProxyAdapter{boolean supported();boolean applied();void apply(int httpPort,Result callback);void clear(Result callback);}
    interface Listener{
        boolean foreground();boolean mediaPlaying();void requestConsent(long ticket,ConsentResult callback);
        void status(String message);void reload(String url);void routeChanged(boolean tor);void failure(String message);
    }
    private static final String PREFS="ghartv.film-tor-fallback",KEY_DECIDED="decided",KEY_ENABLED="enabled",KEY_DEFAULT_VERSION="default_version";
    private final Context app;private final SharedPreferences preferences;private final Listener listener;private final Transport transport;private final ProxyAdapter proxy;private final FilmTorFallback policy;
    private String currentUrl=FilmProviderPolicy.HOME;private FilmTorFallback.Failure pendingFailure=FilmTorFallback.Failure.OTHER;private int httpPort;private boolean closed,preferTorThisSession;
    // Film and voluntary Tor browser: fail closed, never silently fall back to direct.
    private final Handler retryHandler=new Handler(Looper.getMainLooper());
    private int bootstrapAttempts,pageRetryCount;private long retryGeneration;
    private String lastPageRetryUrl="";

    FilmTorController(Context context,Listener listener){this(context,listener,new FilmTorTransport(context),new FilmTorProxy(context),true);}
    // Existing controller tests and legacy harnesses retain the pre-56 direct-first default.
    FilmTorController(Context context,Listener listener,Transport transport,ProxyAdapter proxy){this(context,listener,transport,proxy,false);}
    FilmTorController(Context context,Listener listener,Transport transport,ProxyAdapter proxy,boolean ownerDefaultTor){
        app=context.getApplicationContext();preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);this.listener=listener;this.transport=transport;this.proxy=proxy;
        // This owner build is Tor-only for film and the in-app browser.
        // Never inherit an older saved "Direct" choice from code 67/70.
        preferences.edit().putBoolean(KEY_DECIDED,true).putBoolean(KEY_ENABLED,true)
                .putInt(KEY_DEFAULT_VERSION,BuildConfig.VERSION_CODE).apply();
        policy=new FilmTorFallback(true,false);preferTorThisSession=true;
    }
    long beginNavigation(String url){
        if(closed)return -1;if(url!=null)currentUrl=url;
        if(!proxy.applied()&&policy.state()==FilmTorFallback.State.STARTING_TOR)transport.cancel();
        return policy.beginNavigation(proxy.applied());
    }
    /** Begin one navigation with Tor as the preferred film route. True means page loading is deferred until proxy acknowledgement. */
    boolean beginPreferredNavigation(String url){
        if(closed)return true;
        if(url!=null&&!url.equals(currentUrl)){lastPageRetryUrl="";pageRetryCount=0;}
        if(url!=null)currentUrl=url;
        if(proxy.applied()){
            policy.beginNavigation(true);bootstrapAttempts=0;return false;
        }
        // A pending Tor start holds the navigation. Under no circumstance
        // perform a direct WebView load while proxy override is absent.
        if(policy.state()==FilmTorFallback.State.STARTING_TOR||
           policy.state()==FilmTorFallback.State.APPLYING_PROXY)return true;
        long ticket=policy.beginNavigation(false);
        if(!listener.foreground()){
            listener.status("Tor-only browsing will resume when GharTV is foregrounded.");return true;
        }
        bootstrapAttempts=0;
        handle(policy.requestTor(ticket,true,false),ticket);
        return true;
    }
    /** Network failures on an already-proxied document retry only through Tor. */
    boolean retryPageThroughTor(String url,FilmTorFallback.Failure kind){
        if(closed||!active()||!listener.foreground()||listener.mediaPlaying()||
           !FilmTorFallback.failureCanRetry(kind)||url==null)return false;
        if(!url.equals(lastPageRetryUrl)){lastPageRetryUrl=url;pageRetryCount=0;}
        if(pageRetryCount>=2)return false;
        pageRetryCount++;final long version=++retryGeneration;
        listener.status("Retrying the page through Tor ("+pageRetryCount+"/2) …");
        retryHandler.postDelayed(()->{
            if(!closed&&version==retryGeneration&&active()&&listener.foreground()&&!listener.mediaPlaying())
                listener.reload(url);
        },pageRetryCount==1?1500L:4500L);
        return true;
    }
    void documentLoaded(String url){
        if(url!=null&&url.equals(lastPageRetryUrl)){pageRetryCount=0;lastPageRetryUrl="";}
    }
    private void retryBootstrap(){
        if(closed)return;
        transport.cancel();httpPort=0;
        if(bootstrapAttempts>=3||!listener.foreground()){
            listener.failure("Tor-only connection unavailable after three attempts. "+
                "Nothing was loaded directly. Check connection, then reopen the page.");
            return;
        }
        bootstrapAttempts++;final long version=++retryGeneration;
        long delay=bootstrapAttempts==1?1250L:bootstrapAttempts==2?3500L:8000L;
        listener.status("Tor-only connection not ready. Automatically retrying "+
                bootstrapAttempts+"/3 …");
        retryHandler.postDelayed(()->{
            if(closed||version!=retryGeneration||proxy.applied()||!listener.foreground())return;
            long ticket=policy.beginNavigation(false);
            handle(policy.requestTor(ticket,true,false),ticket);
        },delay);
    }
    boolean failure(String url,FilmTorFallback.Failure failure){
        if(closed)return false;String target=url==null?currentUrl:url;pendingFailure=failure;
        FilmTorFallback.Action action=policy.directFailed(policy.generation(),target,failure,listener.foreground(),listener.mediaPlaying());return handle(action,policy.generation());
    }
    void catalogueObserved(int readableCards){if(closed)return;policy.catalogueObserved(policy.generation(),readableCards);if(readableCards>0&&proxy.applied()){listener.status("FlixMomo ready · connected through Tor.");event("film_tor_catalogue_ready","route","tor");}}
    boolean enabled(){return policy.consentEnabled();} boolean active(){return proxy.applied()&&policy.routeIsTor();} int posterProxyPort(){return active()?httpPort:0;}
    String routeLabel(){return active()?"Tor connected":"Tor connecting";} boolean supported(){return proxy.supported();}
    void setEnabled(boolean enabled){
        // Tor-only owner policy: no direct toggle for films or external browsing.
        listener.status("Tor-only mode is required. It cannot be disabled in this build.");
    }
    void tryTorNow(){if(closed)return;if(proxy.applied()){listener.status("Tor route is ready.");return;}
        ++retryGeneration;bootstrapAttempts=0;
        long ticket=policy.beginNavigation(false);
        handle(policy.requestTor(ticket,listener.foreground(),false),ticket);
    }
    void requestDirect(){listener.status("Direct browsing is disabled in this owner build.");}
    private boolean handle(FilmTorFallback.Action action,long ticket){
        switch(action){
            case NONE:return false;
            case ASK_CONSENT:listener.requestConsent(ticket,allowed->{if(closed)return;preferences.edit().putBoolean(KEY_DECIDED,true).putBoolean(KEY_ENABLED,allowed).apply();event("film_tor_consent","enabled",allowed);handle(policy.consentResult(ticket,allowed,listener.foreground()),ticket);});return true;
            case START_TOR:startTor(ticket);return true;
            case SHOW_FAILURE:showFailure();return true;
            default:return true;
        }
    }
    private void startTor(long ticket){
        listener.status("Connecting privately through Tor…");event("film_tor_start","failure",pendingFailure.name());
        transport.start((ready,port)->{
            if(closed||ticket!=policy.generation())return;
            httpPort=ready?port:0;
            FilmTorFallback.Action action=policy.torReady(ticket,ready,proxy.supported());
            if(action==FilmTorFallback.Action.APPLY_TOR_PROXY)applyProxy(ticket,port);
            else if(action==FilmTorFallback.Action.SHOW_FAILURE)retryBootstrap();
        });
    }
    private void applyProxy(long ticket,int port){
        listener.status("Tor connected · preparing secure browser…");
        proxy.apply(port,acknowledged->{if(closed||ticket!=policy.generation()){if(acknowledged)proxy.clear(ignored->{});return;}FilmTorFallback.Action action=policy.proxyApplied(ticket,acknowledged);if(action==FilmTorFallback.Action.RELOAD_THROUGH_TOR){listener.routeChanged(true);listener.status("Tor connected · opening page…");event("film_tor_route","route","tor");listener.reload(currentUrl);}else if(action==FilmTorFallback.Action.SHOW_FAILURE)retryBootstrap();});
    }
    private void showFailure(){
        // The route must never fall through to a direct WebView request.
        retryBootstrap();
    }
    void destroy(){if(closed)return;closed=true;++retryGeneration;retryHandler.removeCallbacksAndMessages(null);boolean clear=policy.close();if(clear||proxy.applied())proxy.clear(ignored->transport.close());else transport.close();}
    private void event(String name,Object... values){Object[] all=new Object[values.length+4];all[0]="event_version_code";all[1]=BuildConfig.VERSION_CODE;all[2]="route";all[3]=active()?"tor":"direct";System.arraycopy(values,0,all,4,values.length);Telemetry.event(app,name,Telemetry.data(all));}
}
