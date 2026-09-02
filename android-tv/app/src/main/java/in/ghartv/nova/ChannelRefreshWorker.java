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
        long startedAt = System.currentTimeMillis();
        try {
            int count = new ChannelRepository(getApplicationContext()).refreshJio().size();
            Telemetry.event(getApplicationContext(), "catalogue_refresh", Telemetry.data(
                    "result", "success",
                    "manual", false,
                    "duration_ms", System.currentTimeMillis() - startedAt,
                    "channel_count", count));
            return Result.success();
        } catch (Exception error) {
            Telemetry.error(getApplicationContext(), "catalogue_refresh_background", error, Telemetry.data(
                    "duration_ms", System.currentTimeMillis() - startedAt,
                    "attempt", getRunAttemptCount()));
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
}
