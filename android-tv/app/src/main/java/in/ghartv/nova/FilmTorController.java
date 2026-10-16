package in.ghartv.nova;

import android.content.Context;
import android.content.SharedPreferences;

/** Coordinates direct-first policy, app-local Tor and scoped WebView proxy callbacks. */
final class FilmTorController {
    interface Result{void done(boolean ok);} interface TransportResult{void ready(boolean ok,int httpPort);} interface ConsentResult{void answer(boolean allowed);}
    interface Transport{void start(TransportResult callback);int currentHttpPort();void cancel();void close();}
    interface ProxyAdapter{boolean supported();boolean applied();void apply(int httpPort,Result callback);void clear(Result callback);}
    interface Listener{
        boolean foreground();boolean mediaPlaying();void requestConsent(long ticket,ConsentResult callback);
        void status(String message);void reload(String url);void routeChanged(boolean tor);void failure(String message);
    }
    private static final String PREFS="ghartv.film-tor-fallback",KEY_DECIDED="decided",KEY_ENABLED="enabled";
    private final Context app;private final SharedPreferences preferences;private final Listener listener;private final Transport transport;private final ProxyAdapter proxy;private final FilmTorFallback policy;
    private String currentUrl=FilmProviderPolicy.HOME;private FilmTorFallback.Failure pendingFailure=FilmTorFallback.Failure.OTHER;private int httpPort;private boolean closed;

    FilmTorController(Context context,Listener listener){this(context,listener,new FilmTorTransport(context),new FilmTorProxy(context));}
    FilmTorController(Context context,Listener listener,Transport transport,ProxyAdapter proxy){
        app=context.getApplicationContext();preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);this.listener=listener;this.transport=transport;this.proxy=proxy;
        boolean decided=preferences.getBoolean(KEY_DECIDED,false),enabled=decided&&preferences.getBoolean(KEY_ENABLED,false);policy=new FilmTorFallback(enabled,!decided);
    }
    long beginNavigation(String url){
        if(closed)return -1;if(url!=null)currentUrl=url;
        if(!proxy.applied()&&policy.state()==FilmTorFallback.State.STARTING_TOR)transport.cancel();
        return policy.beginNavigation(proxy.applied());
    }
    boolean failure(String url,FilmTorFallback.Failure failure){
        if(closed)return false;String target=url==null?currentUrl:url;pendingFailure=failure;
        FilmTorFallback.Action action=policy.directFailed(policy.generation(),target,failure,listener.foreground(),listener.mediaPlaying());return handle(action,policy.generation());
    }
    void catalogueObserved(int readableCards){if(closed)return;policy.catalogueObserved(policy.generation(),readableCards);if(readableCards>0&&proxy.applied()){listener.status("FlixMomo catalogue loaded through the Tor fallback.");event("film_tor_catalogue_ready","route","tor");}}
    boolean enabled(){return policy.consentEnabled();} boolean active(){return proxy.applied()&&policy.routeIsTor();} int posterProxyPort(){return active()?httpPort:0;}
    String routeLabel(){return active()?"Tor fallback":"Direct";} boolean supported(){return proxy.supported();}
    void setEnabled(boolean enabled){
        if(closed)return;preferences.edit().putBoolean(KEY_DECIDED,true).putBoolean(KEY_ENABLED,enabled).apply();policy.configureConsent(enabled,false);event("film_tor_consent","enabled",enabled);
        if(!enabled){if(proxy.applied()||policy.state()==FilmTorFallback.State.STARTING_TOR||policy.state()==FilmTorFallback.State.APPLYING_PROXY)requestDirect();else{transport.cancel();listener.routeChanged(false);listener.status("Tor fallback is off. FlixMomo remains on the direct connection.");}}
        else listener.status("Tor fallback enabled. GharTV will still try FlixMomo directly first.");
    }
    void tryTorNow(){
        if(closed||proxy.applied())return;
        if(!policy.consentEnabled()){listener.requestConsent(policy.generation(),allowed->{if(closed)return;preferences.edit().putBoolean(KEY_DECIDED,true).putBoolean(KEY_ENABLED,allowed).apply();policy.configureConsent(allowed,false);if(allowed)handle(policy.requestTor(policy.generation(),listener.foreground(),listener.mediaPlaying()),policy.generation());else listener.failure("Tor fallback remains off; the direct page was preserved.");});return;}
        handle(policy.requestTor(policy.generation(),listener.foreground(),listener.mediaPlaying()),policy.generation());
    }
    void requestDirect(){
        if(closed)return;FilmTorFallback.Action action=policy.requestDirect();if(action!=FilmTorFallback.Action.CLEAR_PROXY_THEN_RELOAD_DIRECT)return;long ticket=policy.generation();listener.status("Returning FlixMomo to the direct connection…");
        proxy.clear(ok->{if(closed||ticket!=policy.generation())return;if(!ok){listener.failure("Could not clear the scoped Tor route. Close and reopen Discover before retrying.");return;}httpPort=0;transport.cancel();policy.directConfirmed(ticket);listener.routeChanged(false);event("film_tor_route","route","direct");listener.reload(currentUrl);});
    }
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
        listener.status("Direct FlixMomo access failed. Starting the scoped Tor fallback…");event("film_tor_start","failure",pendingFailure.name());
        transport.start((ready,port)->{if(closed||ticket!=policy.generation())return;httpPort=ready?port:0;FilmTorFallback.Action action=policy.torReady(ticket,ready,proxy.supported());if(action==FilmTorFallback.Action.APPLY_TOR_PROXY)applyProxy(ticket,port);else if(action==FilmTorFallback.Action.SHOW_FAILURE)showFailure();});
    }
    private void applyProxy(long ticket,int port){
        listener.status("Tor is ready. Applying the FlixMomo-only route…");
        proxy.apply(port,acknowledged->{if(closed||ticket!=policy.generation()){if(acknowledged)proxy.clear(ignored->{});return;}FilmTorFallback.Action action=policy.proxyApplied(ticket,acknowledged);if(action==FilmTorFallback.Action.RELOAD_THROUGH_TOR){listener.routeChanged(true);listener.status("Retrying FlixMomo through Tor. JioTV, updates and diagnostics remain direct.");event("film_tor_route","route","tor");listener.reload(currentUrl);}else if(action==FilmTorFallback.Action.SHOW_FAILURE)showFailure();});
    }
    private void showFailure(){String message=policy.consentEnabled()?"The Tor fallback could not be prepared. The failed page was not silently rerouted or downgraded.":"The direct FlixMomo request failed and Tor fallback is off. Enable it from Options to retry privately.";event("film_tor_failure","failure",pendingFailure.name());listener.failure(message);}
    void destroy(){if(closed)return;closed=true;boolean clear=policy.close();if(clear||proxy.applied())proxy.clear(ignored->transport.close());else transport.close();}
    private void event(String name,Object... values){Telemetry.event(app,name,Telemetry.data(values));}
}
