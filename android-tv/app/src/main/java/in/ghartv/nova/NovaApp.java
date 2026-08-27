package in.ghartv.nova;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class NovaApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
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
}
