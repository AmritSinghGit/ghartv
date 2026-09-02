package in.ghartv.nova;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class TelemetryUploadWorker extends Worker {
    public TelemetryUploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        Telemetry.UploadOutcome outcome = Telemetry.upload(getApplicationContext());
        switch (outcome) {
            case RETRY:
                return getRunAttemptCount() < 5 ? Result.retry() : Result.success();
            case SUCCESS:
            case EMPTY:
            case DISABLED:
            case PERMANENT_FAILURE:
            default:
                return Result.success();
        }
    }
}
