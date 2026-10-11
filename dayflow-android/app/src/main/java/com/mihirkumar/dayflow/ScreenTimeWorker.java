package com.mihirkumar.dayflow;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Low-frequency, battery-conscious snapshotting of local screen-time totals. */
public class ScreenTimeWorker extends Worker {
    public ScreenTimeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!ScreenTimeTracker.hasUsageAccess(context)) return Result.success();
        try {
            boolean changed = ScreenTimeTracker.refreshRecentDays(context, 7);
            if (changed) DayFlowWidgetProvider.refreshAll(context);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
