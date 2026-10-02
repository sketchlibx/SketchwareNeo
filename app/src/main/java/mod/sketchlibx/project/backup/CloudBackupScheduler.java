package mod.sketchlibx.project.backup;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CloudBackupScheduler {

    private static final String TAG = "CloudBackupScheduler";

    public static final String WORK_NAME = "sketchware_cloud_auto_backup";

    public static void schedule(Context context, long intervalHours) {
        if (intervalHours < 1) {
            Log.w(TAG, "intervalHours=" + intervalHours + " is too small; clamped to 1 h.");
            intervalHours = 1;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class,
                intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);

        Log.i(TAG, "AutoBackupWorker scheduled | interval=" + intervalHours + " h"
                + " | policy=UPDATE | workName=" + WORK_NAME);

        logWorkStatus(context);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.i(TAG, "AutoBackupWorker cancelled for workName=" + WORK_NAME);
    }

    public static void logWorkStatus(Context context) {
        try {
            List<WorkInfo> infos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .get();

            if (infos == null || infos.isEmpty()) {
                Log.w(TAG, "logWorkStatus: no WorkInfo found for '" + WORK_NAME
                        + "' — worker may not be scheduled.");
                return;
            }

            for (WorkInfo info : infos) {
                Log.i(TAG, String.format(Locale.ROOT,
                        "WorkInfo | id=%s | state=%s | runAttemptCount=%d | tags=%s",
                        info.getId(), info.getState(), info.getRunAttemptCount(), info.getTags()));
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "logWorkStatus: failed to query WorkInfo", e);
        }
    }

    public static String getWorkStatusSummary(Context context) {
        try {
            List<WorkInfo> infos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .get();
            if (infos == null || infos.isEmpty()) return "Not scheduled";
            WorkInfo info = infos.get(0);
            return info.getState().name() + " (attempt " + info.getRunAttemptCount() + ")";
        } catch (Exception e) {
            return "Unknown (query failed)";
        }
    }

    public static boolean hasDriveAppDataScope(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            Log.w(TAG, "hasDriveAppDataScope: no signed-in account.");
            return false;
        }
        Scope driveScope = new Scope(DriveScopes.DRIVE_APPDATA);
        boolean granted = GoogleSignIn.hasPermissions(account, driveScope);
        Log.d(TAG, "hasDriveAppDataScope=" + granted
                + " | account=" + account.getEmail()
                + " | grantedScopes=" + account.getGrantedScopes());
        return granted;
    }

    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "POST_NOTIFICATIONS permission granted=" + granted);
            return granted;
        }
        return true;
    }
}
