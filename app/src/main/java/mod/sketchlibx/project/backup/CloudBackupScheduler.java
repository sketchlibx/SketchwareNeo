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

/**
 * CloudBackupScheduler
 *
 * Central helper for:
 *  - Scheduling / cancelling the periodic {@link AutoBackupWorker}.
 *  - Verifying the DRIVE_APPDATA OAuth scope is actually granted.
 *  - Checking the Android 13+ POST_NOTIFICATIONS permission.
 *  - Logging current WorkManager state for debugging.
 *
 * Usage — call {@link #schedule} once after the user enables auto-backup or
 * changes the interval. The policy {@link ExistingPeriodicWorkPolicy#UPDATE}
 * replaces any stale/cancelled work entry so the worker is always current.
 */
public class CloudBackupScheduler {

    private static final String TAG = "CloudBackupScheduler";

    /** Unique WorkManager name — used for enqueue, cancel, and status queries. */
    public static final String WORK_NAME = "sketchware_cloud_auto_backup";

    // ────────────────────────────────────────────────────────────────────────────────
    // Scheduling
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Enqueues (or updates) the periodic backup worker.
     *
     * <p>The {@link ExistingPeriodicWorkPolicy#UPDATE} policy guarantees that if a
     * work entry with {@link #WORK_NAME} already exists in any state (ENQUEUED,
     * RUNNING, BLOCKED, even CANCELLED), it is atomically replaced with the new
     * request. This fixes the common bug where the worker appears "scheduled" but
     * never actually runs because an old, cancelled entry is blocking the queue.</p>
     *
     * <p>WorkManager requires a minimum repeat interval of 15 minutes. Pass at least
     * 1 hour in production to avoid excessive battery/network usage.</p>
     *
     * @param context       application context
     * @param intervalHours repeat interval in hours (minimum enforced: 1 h)
     */
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

        // Immediately log the resulting state so the caller can confirm enqueue.
        logWorkStatus(context);
    }

    /**
     * Cancels the periodic backup work entirely.
     * Call this when the user disables auto-backup.
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.i(TAG, "AutoBackupWorker cancelled for workName=" + WORK_NAME);
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Diagnostics
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Dumps the current {@link WorkInfo} for the backup work to logcat.
     * Call this from a debug screen or after calling {@link #schedule} to confirm
     * the worker is in ENQUEUED or RUNNING state.
     */
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

    /**
     * Returns a human-readable summary of the current work state.
     * Useful for displaying in a settings screen.
     *
     * @return e.g. "ENQUEUED (attempt 0)" or "Not scheduled"
     */
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

    // ────────────────────────────────────────────────────────────────────────────────
    // OAuth / permission guards
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the last signed-in Google account has been granted
     * the {@code DRIVE_APPDATA} OAuth scope.
     *
     * <p>If this returns {@code false}, the worker will fail immediately and the
     * user must re-authenticate via the sign-in flow with the Drive scope included.</p>
     */
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

    /**
     * Returns {@code true} if {@code POST_NOTIFICATIONS} is granted (required on
     * Android 13 / API 33+ to show foreground-service notifications).
     *
     * <p>Always returns {@code true} on Android 12 and below where the permission
     * does not exist.</p>
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "POST_NOTIFICATIONS permission granted=" + granted);
            return granted;
        }
        // Permission does not exist before Android 13; always considered granted.
        return true;
    }
}