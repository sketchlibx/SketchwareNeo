package pro.sketchware.update;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.ComponentName;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.util.Log;

/** Executes update metadata fetches only while Android reports network connectivity. */
public final class AppUpdateJobService extends JobService {
    private static final int IMMEDIATE_JOB_ID = 61270;
    private static final int PERIODIC_JOB_ID = 61271;
    private volatile Thread runningThread;

    public static void schedule(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            ComponentName component = new ComponentName(context, AppUpdateJobService.class);
            if (scheduler.getPendingJob(IMMEDIATE_JOB_ID) == null) {
                JobInfo immediate = new JobInfo.Builder(IMMEDIATE_JOB_ID, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .setOverrideDeadline(15 * 60 * 1000L)
                        .build();
                scheduler.schedule(immediate);
            }
            if (scheduler.getPendingJob(PERIODIC_JOB_ID) == null) {
                JobInfo periodic = new JobInfo.Builder(PERIODIC_JOB_ID, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .setPeriodic(6 * 60 * 60 * 1000L)
                        .build();
                scheduler.schedule(periodic);
            }
        } catch (Exception e) {
            Log.w("SketchwareUpdate", "Background update scheduling is unavailable.");
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        runningThread = new Thread(() -> {
            try {
                AppUpdateManager.fetchLatestBlocking(getApplicationContext());
            } finally {
                jobFinished(params, false);
            }
        }, "SketchwareUpdateJob");
        runningThread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread thread = runningThread;
        if (thread != null) thread.interrupt();
        return true;
    }
}
