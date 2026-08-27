package in.ghartv.nova;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class ChannelRefreshWorker extends Worker {
    public ChannelRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        JioSession session = JioSession.load(getApplicationContext());
        if (!session.isPresent()) return Result.success();
        try {
            new ChannelRepository(getApplicationContext()).refreshJio();
            return Result.success();
        } catch (Exception error) {
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
}
