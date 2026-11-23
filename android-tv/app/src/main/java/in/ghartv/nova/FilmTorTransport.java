package in.ghartv.nova;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.os.SystemClock;
import org.torproject.jni.TorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-local Tor service binding. No VPN, global proxy or browser credential reuse. */
final class FilmTorTransport implements FilmTorController.Transport {
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile TorService service;
    private boolean bound;
    private boolean started;
    private volatile long generation;
    private FilmTorController.TransportResult pending;
    FilmTorTransport(Context context){app=context.getApplicationContext();}

    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(!(binder instanceof TorService.LocalBinder)){finish(generation,false,0);return;}
            service=((TorService.LocalBinder)binder).getService();final long ticket=generation;io.execute(()->awaitReady(ticket));
        }
        @Override public void onServiceDisconnected(ComponentName name){service=null;finish(generation,false,0);}
    };

    @Override public void start(FilmTorController.TransportResult callback){
        generation++; pending=callback; final long ticket=generation;
        if(bound) {
            if(service!=null)io.execute(()->awaitReady(ticket));
            // An in-flight bind will dispatch its callback; never bind twice.
            return;
        }
        Intent intent=new Intent(app,TorService.class).setAction(TorService.ACTION_START);
        try{
            // Binding alone can hand back TorService before its native control socket is ready.
            // Explicitly start the local service while this visible activity is in foreground.
            app.startService(intent);
            started=true;
        }catch(RuntimeException e){
            // Binding is still worth trying; never make a background-service workaround.
            Log.w("GharTVFilmTor","Tor service start refused: "+e.getClass().getSimpleName());
        }
        try{
            bound=app.bindService(intent,connection,Context.BIND_AUTO_CREATE);
            if(!bound)finish(ticket,false,0);
        }catch(RuntimeException failure){
            Log.w("GharTVFilmTor","Tor service binding failed: "+failure.getClass().getSimpleName());
            finish(ticket,false,0);
        }
    }
    private void awaitReady(long ticket){
        final long deadline=SystemClock.elapsedRealtime()+70_000L;
        long lastReport=0L;
        while(ticket==generation&&SystemClock.elapsedRealtime()<deadline){
            TorService current=service;
            if(current!=null)try{
                // Tor's LocalBinder exists before its native TorControlConnection is assigned.
                // Never ask getInfo() while the connection is null (Review 67 crash source).
                if(current.getTorControlConnection()!=null){
                    String circuit=current.getInfo("status/circuit-established");
                    int port=current.getHttpTunnelPort();
                    if("1".equals(circuit)&&port>0&&port<=65535){
                        TorCountryControl.apply(app,current);
                        Log.i("GharTVFilmTor","Tor control and HTTP proxy ready");
                        finish(ticket,true,port);return;
                    }
                }
            }catch(RuntimeException failure){
                Log.w("GharTVFilmTor","Tor readiness poll failed: "+failure.getClass().getSimpleName());
            }
            long now=SystemClock.elapsedRealtime();
            if(now-lastReport>=10_000L){
                // Privacy-safe diagnostic: no node IPs, exit details, service paths or
                // browser requests are written to logcat.
                boolean control=current!=null&&current.getTorControlConnection()!=null;
                String progress="unknown";
                if(control)try{
                    String phase=current.getInfo("status/bootstrap-phase");
                    java.util.regex.Matcher m=java.util.regex.Pattern.compile("PROGRESS=([0-9]{1,3})").matcher(phase==null?"":phase);
                    if(m.find())progress=m.group(1);
                }catch(RuntimeException ignored){}
                Log.i("GharTVFilmTor","Tor control "+(control?"ready":"waiting")+" · bootstrap "+progress+"%");
                lastReport=now;
            }
            SystemClock.sleep(400L);
        }
        if(ticket==generation)Log.w("GharTVFilmTor","Tor not ready before 70-second deadline");
        finish(ticket,false,0);
    }
    private void finish(long ticket,boolean ready,int port){main.post(()->{if(ticket!=generation||pending==null)return;FilmTorController.TransportResult callback=pending;pending=null;callback.ready(ready,port);});}
    @Override public int currentHttpPort(){TorService current=service;if(current==null)return 0;try{if(current.getTorControlConnection()==null)return 0;int port=current.getHttpTunnelPort();return port>0&&port<=65535?port:0;}catch(RuntimeException ignored){return 0;}}
    @Override public void cancel(){generation++;pending=null;if(bound)try{app.unbindService(connection);}catch(RuntimeException ignored){}bound=false;service=null;}
    @Override public void close(){cancel();io.shutdownNow();if(started){try{app.stopService(new Intent(app,TorService.class));}catch(RuntimeException ignored){}started=false;}}
}
