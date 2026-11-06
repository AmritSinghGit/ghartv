package in.ghartv.nova;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import org.torproject.jni.TorService;

/** Starts the app-local Tor service when the app opens, so FlixMomo's Tor route finds a ready circuit.
 * Its own binding: AppTorTunnel (the JioTV route fallback) is not bound or changed by it. */
final class TorWarmup {
    private static boolean started;
    private static final ServiceConnection KEEP = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {}
        @Override public void onServiceDisconnected(ComponentName name) {}
    };

    /** Once per process, and only when FlixMomo will use Tor: the owner default or Tor left on in Menu. */
    static synchronized void start(Context context) {
        if (started || !filmTorPreferred(context)) return;
        started = true;
        Context app = context.getApplicationContext();
        try { app.bindService(new Intent(app, TorService.class).setAction(TorService.ACTION_START), KEEP, Context.BIND_AUTO_CREATE); }
        catch (RuntimeException ignored) { started = false; }
    }

    /** Reads FilmTorController's preference file (same name and keys) without changing it. */
    static boolean filmTorPreferred(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences("ghartv.film-tor-fallback", Context.MODE_PRIVATE);
        return !p.getBoolean("decided", false) || p.getBoolean("enabled", false);
    }

    private TorWarmup() {}
}
