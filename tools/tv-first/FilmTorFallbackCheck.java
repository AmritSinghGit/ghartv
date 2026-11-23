package in.ghartv.nova;

/** Calls the real direct-first policy. No Android, Tor or network success is simulated. */
public final class FilmTorFallbackCheck {
    private static int checks;
    private static final String SITE="https://flixmomo.bet/",CATALOGUE="https://api.themoviedb.org/3/discover/movie";
    private static void eq(Object expected,Object observed){if(!expected.equals(observed))throw new AssertionError(expected+" != "+observed);}
    private static void yes(boolean value){if(!value)throw new AssertionError();}
    private static void run(String name,Runnable test){test.run();checks++;System.out.println("PASS "+name);}
    private static FilmTorFallback started(boolean consent){FilmTorFallback p=new FilmTorFallback(consent,!consent);p.beginDirect();return p;}
    private static FilmTorFallback.Action failure(FilmTorFallback p){return p.directFailed(p.generation(),CATALOGUE,FilmTorFallback.Failure.CONNECT,true,false);}
    public static void main(String[] args){
        run("direct first",()->{FilmTorFallback p=new FilmTorFallback(true,false);p.beginDirect();eq(FilmTorFallback.State.DIRECT,p.state());yes(!p.attemptedTor());});
        run("failure asks consent",()->{FilmTorFallback p=started(false);eq(FilmTorFallback.Action.ASK_CONSENT,failure(p));});
        run("refused consent no Tor",()->{FilmTorFallback p=started(false);failure(p);eq(FilmTorFallback.Action.SHOW_FAILURE,p.consentResult(p.generation(),false,true));yes(!p.attemptedTor());});
        run("consent then one attempt",()->{FilmTorFallback p=started(false);failure(p);eq(FilmTorFallback.Action.START_TOR,p.consentResult(p.generation(),true,true));eq(FilmTorFallback.Action.NONE,failure(p));});
        run("existing consent retries",()->{FilmTorFallback p=started(true);eq(FilmTorFallback.Action.START_TOR,failure(p));yes(p.attemptedTor());});
        run("success suppresses fallback",()->{FilmTorFallback p=started(true);p.catalogueObserved(p.generation(),3);eq(FilmTorFallback.Action.NONE,failure(p));});
        run("empty is not success",()->{FilmTorFallback p=started(true);p.catalogueObserved(p.generation(),0);eq(FilmTorFallback.Action.START_TOR,failure(p));});
        run("no TLS auth rate or empty retry",()->{for(FilmTorFallback.Failure f:new FilmTorFallback.Failure[]{FilmTorFallback.Failure.TLS,FilmTorFallback.Failure.HTTP_AUTH,FilmTorFallback.Failure.HTTP_DENIED,FilmTorFallback.Failure.HTTP_RATE_LIMIT,FilmTorFallback.Failure.EMPTY_RESULTS}){FilmTorFallback p=started(true);eq(FilmTorFallback.Action.NONE,p.directFailed(p.generation(),SITE,f,true,false));}});
        run("no background or playback switch",()->{FilmTorFallback p=started(true);eq(FilmTorFallback.Action.NONE,p.directFailed(p.generation(),SITE,FilmTorFallback.Failure.TIMEOUT,false,false));eq(FilmTorFallback.Action.NONE,p.directFailed(p.generation(),SITE,FilmTorFallback.Failure.TIMEOUT,true,true));});
        run("stale callback ignored",()->{FilmTorFallback p=started(true);long old=p.generation();p.beginDirect();eq(FilmTorFallback.Action.NONE,p.directFailed(old,SITE,FilmTorFallback.Failure.CONNECT,true,false));});
        run("circuit and scope required",()->{FilmTorFallback p=started(true);failure(p);eq(FilmTorFallback.Action.SHOW_FAILURE,p.torReady(p.generation(),false,true));FilmTorFallback q=started(true);failure(q);eq(FilmTorFallback.Action.SHOW_FAILURE,q.torReady(q.generation(),true,false));});
        run("proxy acknowledgement precedes reload",()->{FilmTorFallback p=started(true);failure(p);eq(FilmTorFallback.Action.APPLY_TOR_PROXY,p.torReady(p.generation(),true,true));eq(FilmTorFallback.Action.RELOAD_THROUGH_TOR,p.proxyApplied(p.generation(),true));eq(FilmTorFallback.State.TOR,p.state());});
        run("failed proxy never reloads",()->{FilmTorFallback p=started(true);failure(p);p.torReady(p.generation(),true,true);eq(FilmTorFallback.Action.SHOW_FAILURE,p.proxyApplied(p.generation(),false));});
        run("Tor failure does not loop direct",()->{FilmTorFallback p=started(true);failure(p);eq(FilmTorFallback.Action.SHOW_FAILURE,p.torFailed(p.generation()));eq(FilmTorFallback.Action.NONE,failure(p));});
        run("direct reset waits for clear",()->{FilmTorFallback p=started(true);failure(p);p.torReady(p.generation(),true,true);p.proxyApplied(p.generation(),true);eq(FilmTorFallback.Action.CLEAR_PROXY_THEN_RELOAD_DIRECT,p.requestDirect());p.directConfirmed(p.generation());eq(FilmTorFallback.State.DIRECT,p.state());});
        run("close invalidates callback",()->{FilmTorFallback p=started(true);failure(p);long t=p.generation();p.torReady(t,true,true);yes(p.close());eq(FilmTorFallback.Action.NONE,p.proxyApplied(t,true));});
        run("scoped URLs only",()->{for(String u:new String[]{SITE,"https://www.flixmomo.bet/","https://www.flixmomo.st/",CATALOGUE,"https://image.tmdb.org/t/p/w500/owned.jpg"})yes(FilmTorFallback.torScope(u));for(String u:new String[]{"https://jiotvapi.cdn.jio.com/","https://api.github.com/","https://ghartv-telemetry.ghartv-47d9a0.workers.dev/","https://127.0.0.1/","https://flixmomo.bet.evil.invalid/","http://flixmomo.bet/","https://flixmomo.bet:443/","https://user:pass@flixmomo.bet/"})yes(!FilmTorFallback.torScope(u));});
        run("image cannot trigger fallback",()->{FilmTorFallback p=started(true);eq(FilmTorFallback.Action.NONE,p.directFailed(p.generation(),"https://image.tmdb.org/owned.png",FilmTorFallback.Failure.CONNECT,true,false));});
        run("Tor success distinct from activation",()->{FilmTorFallback p=started(true);failure(p);p.torReady(p.generation(),true,true);p.proxyApplied(p.generation(),true);yes(!p.hasCatalogue());p.catalogueObserved(p.generation(),4);yes(p.hasCatalogue());});
        run("manual Tor requires consent",()->{FilmTorFallback p=started(false);eq(FilmTorFallback.Action.NONE,p.requestTor(p.generation(),true,false));p.configureConsent(true,false);eq(FilmTorFallback.Action.START_TOR,p.requestTor(p.generation(),true,false));});
        run("new Tor navigation retains route",()->{FilmTorFallback p=started(true);failure(p);p.torReady(p.generation(),true,true);p.proxyApplied(p.generation(),true);p.beginNavigation(true);eq(FilmTorFallback.State.TOR,p.state());});
        System.out.println("POLICY_CHECKS="+checks+" PASSED; Android/Tor transport NOT RUN");
    }
}
