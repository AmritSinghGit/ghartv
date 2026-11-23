package in.ghartv.nova;

import android.app.Application;
import android.util.Log;
import java.io.File;
import java.nio.file.Files;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class NovaApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        prepareFreshTorStateOnce();
        // Side-by-side owner review APK is entirely local: no remote telemetry, device pairing,
        // or cloud owner-command polling. Stream providers remain their own HTTPS services.
        // Local-first family build: no background telemetry, remote owner-command
        // polling or data-centre remote control service is started.
        // Provider browsing still uses user-selected Tor and provider connections.
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ChannelRefreshWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ghartv-channel-refresh",
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
        new Thread(() -> new JioApiClient(this).prewarm(), "ghartv-prewarm").start();
    }
    private void prepareFreshTorStateOnce(){
        if(BuildConfig.VERSION_CODE<72)return;
        // Code 67's TorService persisted bootstrap data can prevent a new
        // controller from establishing a circuit. Atomically PRESERVE only
        // TorService's private directory on this one-time v72 upgrade.
        // Never touch Jio login, SQLite, provider cookies, photos or uploads.
        android.content.SharedPreferences prefs=getSharedPreferences(
            "ghartv.tor-state-recovery",MODE_PRIVATE);
        if(prefs.getBoolean("migrated_v72",false))return;
        File base=getDataDir();
        File old=new File(base,"app_TorService");
        File saved=new File(base,"app_TorService_preserved_before_v72");
        try{
            if(!old.exists()){
                prefs.edit().putBoolean("migrated_v72",true).apply();return;
            }
            if(!old.isDirectory()||Files.isSymbolicLink(old.toPath())||saved.exists()){
                Log.w("GharTVTor","Tor state preservation blocked; no files changed");
                return;
            }
            if(old.renameTo(saved)){
                prefs.edit().putBoolean("migrated_v72",true).apply();
                Log.i("GharTVTor","Old Tor state preserved privately; starting fresh Tor state");
            }else{
                Log.w("GharTVTor","Could not preserve Tor state; original files kept");
            }
        }catch(Exception e){Log.w("GharTVTor",
            "Tor state preservation aborted without changing other app data");}
    }
}
