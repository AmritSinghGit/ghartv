package in.ghartv.nova;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import org.torproject.jni.TorService;

/** Shared app-local HTTP tunnel. It is never a device VPN or global Android proxy. */
final class AppTorTunnel {
    private static volatile AppTorTunnel instance;
    static AppTorTunnel get(Context context) {
        AppTorTunnel value = instance;
        if (value != null) return value;
        synchronized (AppTorTunnel.class) {
            if (instance == null) instance = new AppTorTunnel(context.getApplicationContext());
            return instance;
        }
    }

    private final Context app;
    private final Object lock = new Object();
    private volatile TorService service;
    private boolean bound;
    private boolean binding;

    private AppTorTunnel(Context context) { app = context; }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (lock) {
                binding = false;
                bound = true;
                if (binder instanceof TorService.LocalBinder) {
                    service = ((TorService.LocalBinder) binder).getService();
                }
                lock.notifyAll();
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) { service = null; bound = false; binding = false; lock.notifyAll(); }
        }
        @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
    };

    int currentHttpPort() {
        TorService current = service;
        if (current == null) return 0;
        try {
            String circuit = current.getInfo("status/circuit-established");
            int port = current.getHttpTunnelPort();
            return "1".equals(circuit) && port > 0 && port <= 65535 ? port : 0;
        } catch (RuntimeException ignored) { return 0; }
    }

    int awaitHttpPort(long timeoutMs) {
        if (Looper.myLooper() == Looper.getMainLooper()) return 0;
        int ready = currentHttpPort();
        if (ready > 0) return ready;
        ensureBound();
        long deadline = SystemClock.elapsedRealtime() + Math.max(500L, timeoutMs);
        while (SystemClock.elapsedRealtime() < deadline) {
            ready = currentHttpPort();
            if (ready > 0) return ready;
            synchronized (lock) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) break;
                try { lock.wait(Math.min(400L, remaining)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return 0;
                }
            }
        }
        return 0;
    }

    private void ensureBound() {
        synchronized (lock) {
            if (bound || binding) return;
            binding = true;
        }
        boolean ok = false;
        try {
            Intent intent = new Intent(app, TorService.class).setAction(TorService.ACTION_START);
            ok = app.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException ignored) {}
        if (!ok) synchronized (lock) { binding = false; bound = false; lock.notifyAll(); }
    }
}
