package in.ghartv.nova;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import org.torproject.jni.TorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-local Tor service binding. No VPN, global proxy or browser credential reuse. */
final class FilmTorTransport implements FilmTorController.Transport {
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TorService service;
    private boolean bound;
    private long generation;
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
        generation++;pending=callback;final long ticket=generation;
        if(bound&&service!=null){io.execute(()->awaitReady(ticket));return;}
        try{Intent intent=new Intent(app,TorService.class).setAction(TorService.ACTION_START);bound=app.bindService(intent,connection,Context.BIND_AUTO_CREATE);if(!bound)finish(ticket,false,0);}
        catch(RuntimeException failure){finish(ticket,false,0);}
    }
    private void awaitReady(long ticket){
        long deadline=SystemClock.elapsedRealtime()+70_000L;
        while(ticket==generation&&SystemClock.elapsedRealtime()<deadline){
            TorService current=service;
            if(current!=null)try{String circuit=current.getInfo("status/circuit-established");int port=current.getHttpTunnelPort();if("1".equals(circuit)&&port>0&&port<=65535){finish(ticket,true,port);return;}}catch(RuntimeException ignored){}
            SystemClock.sleep(400L);
        }
        finish(ticket,false,0);
    }
    private void finish(long ticket,boolean ready,int port){main.post(()->{if(ticket!=generation||pending==null)return;FilmTorController.TransportResult callback=pending;pending=null;callback.ready(ready,port);});}
    @Override public int currentHttpPort(){TorService current=service;if(current==null)return 0;try{int port=current.getHttpTunnelPort();return port>0&&port<=65535?port:0;}catch(RuntimeException ignored){return 0;}}
    @Override public void cancel(){generation++;pending=null;if(bound)try{app.unbindService(connection);}catch(RuntimeException ignored){}bound=false;service=null;}
    @Override public void close(){cancel();io.shutdownNow();}
}
