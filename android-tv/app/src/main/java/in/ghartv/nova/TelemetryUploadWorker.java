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
        Telemetry.UploadOutcome outcome = Telemetry.UploadOutcome.EMPTY;
        // Drain a bounded backlog, rather than declaring success after only40 reports
        // and leaving the rest until the12-hour periodic run.
        for (int batch=0; batch<3 && !isStopped(); batch++) {
            outcome = Telemetry.upload(getApplicationContext());
            if(outcome!=Telemetry.UploadOutcome.SUCCESS || Telemetry.queuedCount(getApplicationContext())==0)break;
        }
        if(outcome==Telemetry.UploadOutcome.SUCCESS && Telemetry.queuedCount(getApplicationContext())>0)return Result.retry();
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
