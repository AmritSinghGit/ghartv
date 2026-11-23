package in.ghartv.nova;

import java.net.URI;
import java.util.Locale;

/** Direct-first, one-attempt Tor fallback policy. No Android or network code. */
final class FilmTorFallback {
    enum State { NEW, DIRECT, CONSENT_REQUIRED, STARTING_TOR, APPLYING_PROXY, TOR, CLEARING_DIRECT, FAILED, CLOSED }
    enum Failure { DNS, CONNECT, TIMEOUT, NO_CATALOGUE_RESPONSE, TLS, HTTP_AUTH, HTTP_DENIED, HTTP_RATE_LIMIT, EMPTY_RESULTS, OTHER }
    enum Action { NONE, ASK_CONSENT, START_TOR, APPLY_TOR_PROXY, RELOAD_THROUGH_TOR, CLEAR_PROXY_THEN_RELOAD_DIRECT, SHOW_FAILURE }

    private State state=State.NEW;
    private long generation;
    private boolean consent,mayAsk,attempted,catalogueObserved,proxyMayBeApplied;

    FilmTorFallback(boolean existingConsent,boolean mayAskForConsent){consent=existingConsent;mayAsk=mayAskForConsent;}
    State state(){return state;} long generation(){return generation;} boolean hasCatalogue(){return catalogueObserved;}
    boolean attemptedTor(){return attempted;} boolean consentEnabled(){return consent;}
    boolean routeIsTor(){return state==State.TOR||state==State.APPLYING_PROXY;}
    void configureConsent(boolean enabled,boolean allowPrompt){consent=enabled;mayAsk=allowPrompt;}

    long beginNavigation(boolean torRoute){
        if(state==State.CLOSED)throw new IllegalStateException("FALLBACK_CLOSED");
        generation++;catalogueObserved=false;
        if(torRoute){state=State.TOR;attempted=true;proxyMayBeApplied=true;}
        else{state=State.DIRECT;attempted=false;proxyMayBeApplied=false;}
        return generation;
    }
    long beginDirect(){return beginNavigation(false);}

    Action directFailed(long ticket,String url,Failure failure,boolean foreground,boolean mediaAlreadyPlaying){
        if(ticket!=generation||state!=State.DIRECT||attempted||catalogueObserved||!foreground||mediaAlreadyPlaying||!failureCanRetry(failure)||!fallbackTrigger(url))return Action.NONE;
        if(!consent){if(!mayAsk){state=State.FAILED;return Action.SHOW_FAILURE;}state=State.CONSENT_REQUIRED;return Action.ASK_CONSENT;}
        return startTor();
    }
    Action consentResult(long ticket,boolean allowed,boolean foreground){
        if(ticket!=generation||state!=State.CONSENT_REQUIRED)return Action.NONE;
        consent=allowed;mayAsk=false;
        if(!allowed||!foreground){state=State.FAILED;return Action.SHOW_FAILURE;}
        return startTor();
    }
    Action requestTor(long ticket,boolean foreground,boolean mediaAlreadyPlaying){
        if(ticket!=generation||state!=State.DIRECT||attempted||!consent||!foreground||mediaAlreadyPlaying)return Action.NONE;
        return startTor();
    }
    private Action startTor(){attempted=true;state=State.STARTING_TOR;return Action.START_TOR;}
    Action torReady(long ticket,boolean circuitEstablished,boolean scopeSupported){
        if(ticket!=generation||state!=State.STARTING_TOR)return Action.NONE;
        if(!circuitEstablished||!scopeSupported){state=State.FAILED;return Action.SHOW_FAILURE;}
        state=State.APPLYING_PROXY;proxyMayBeApplied=true;return Action.APPLY_TOR_PROXY;
    }
    Action proxyApplied(long ticket,boolean acknowledged){
        if(ticket!=generation||state!=State.APPLYING_PROXY)return Action.NONE;
        if(!acknowledged){state=State.FAILED;return Action.SHOW_FAILURE;}
        state=State.TOR;catalogueObserved=false;return Action.RELOAD_THROUGH_TOR;
    }
    Action torFailed(long ticket){
        if(ticket!=generation||state==State.CLOSED||(state!=State.STARTING_TOR&&state!=State.APPLYING_PROXY&&state!=State.TOR))return Action.NONE;
        state=State.FAILED;return Action.SHOW_FAILURE;
    }
    void catalogueObserved(long ticket,int readableCards){if(ticket==generation&&readableCards>0&&(state==State.DIRECT||state==State.TOR))catalogueObserved=true;}
    Action requestDirect(){if(state==State.CLOSED)return Action.NONE;generation++;state=State.CLEARING_DIRECT;attempted=false;catalogueObserved=false;return Action.CLEAR_PROXY_THEN_RELOAD_DIRECT;}
    void directConfirmed(long ticket){if(ticket==generation&&state==State.CLEARING_DIRECT){proxyMayBeApplied=false;state=State.DIRECT;}}
    boolean close(){generation++;state=State.CLOSED;return proxyMayBeApplied;}

    static boolean failureCanRetry(Failure failure){return failure==Failure.DNS||failure==Failure.CONNECT||failure==Failure.TIMEOUT||failure==Failure.NO_CATALOGUE_RESPONSE;}
    static String[] proxyHosts(){return new String[]{"flixmomo.bet","www.flixmomo.bet","flixmomo.app","flixmomo.st","www.flixmomo.st","api.themoviedb.org","image.tmdb.org"};}
    private static String publicHttpsHost(String url){try{URI uri=new URI(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getRawUserInfo()!=null||uri.getPort()!=-1||uri.getHost()==null)return "";return uri.getHost().toLowerCase(Locale.ROOT);}catch(Exception invalid){return "";}}
    static boolean fallbackTrigger(String url){String host=publicHttpsHost(url);return providerHost(host)||"api.themoviedb.org".equals(host);}
    static boolean torScope(String url){String host=publicHttpsHost(url);return providerHost(host)||"api.themoviedb.org".equals(host)||"image.tmdb.org".equals(host);}
    private static boolean providerHost(String host){return "flixmomo.bet".equals(host)||"flixmomo.app".equals(host)||"flixmomo.st".equals(host)||"www.flixmomo.st".equals(host)||"www.flixmomo.bet".equals(host);}
}
