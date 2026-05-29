package mod.sketchlibx.project.backup;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import a.a.a.lC;
import pro.sketchware.utility.FileUtil;

public class AutoBackupWorker extends Worker {

    private static final String TAG = "AutoBackupWorker";
    private static final String CHANNEL_ID = "cloud_backup_channel";
    private static final int NOTIFICATION_ID = 9988;
    /** Hard cap per upload — 2 minutes, same as before but now enforced by latch. */
    private static final long UPLOAD_TIMEOUT_MS = 120_000L;

    private NotificationManager notificationManager;

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "========== AutoBackupWorker started ==========");
        Context context = getApplicationContext();

        // ── Step 1: Set foreground IMMEDIATELY ──────────────────────────────────────────
        // Must happen before any long-running work; otherwise Android 8+ can kill the
        // worker silently while it waits for network or Drive responses.
        if (CloudBackupScheduler.hasNotificationPermission(context)) {
            try {
                setForegroundAsync(buildForegroundInfo("Preparing cloud backup…", 0, 1))
                        .get(5, TimeUnit.SECONDS);
                Log.d(TAG, "Foreground service promoted successfully.");
            } catch (Exception e) {
                Log.w(TAG, "setForegroundAsync failed (non-fatal, continuing): " + e.getMessage());
            }
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — running without foreground notification (Android 13+).");
        }

        // ── Step 2: Network check (NetworkCapabilities, Android Q+) ──────────────────
        if (!isNetworkAvailable(context)) {
            Log.e(TAG, "No validated internet connection — scheduling retry.");
            return Result.retry();
        }

        // ── Step 3: Read prefs and check interval ────────────────────────────────────
        SharedPreferences prefs = context.getSharedPreferences("cloud_backup_prefs", Context.MODE_PRIVATE);
        int intervalType = prefs.getInt("auto_backup_interval", 2);
        Log.d(TAG, "auto_backup_interval pref = " + intervalType);

        if (intervalType == 0) {
            Log.i(TAG, "Auto backup disabled by user — no-op.");
            return Result.success();
        }

        long intervalMs = switch (intervalType) {
            case 1 -> 24L * 60 * 60 * 1000;          // Daily
            case 2 -> 7L * 24 * 60 * 60 * 1000;      // Weekly
            case 3 -> 30L * 24 * 60 * 60 * 1000;     // Monthly
            default -> 7L * 24 * 60 * 60 * 1000;
        };

        long lastBackupTime = prefs.getLong("last_backup_time", 0);
        long elapsed = System.currentTimeMillis() - lastBackupTime;
        Log.d(TAG, "Time since last backup: " + (elapsed / 1000 / 60) + " min | Required interval: "
                + (intervalMs / 1000 / 60) + " min");

        if (elapsed < intervalMs) {
            Log.i(TAG, "Backup interval not yet reached — skipping.");
            return Result.success();
        }

        // ── Step 4: Verify Google account + Drive scope ──────────────────────────────
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            Log.e(TAG, "Google account not found — user is not signed in. Failing permanently.");
            return Result.failure();
        }
        Log.d(TAG, "Signed-in account: " + account.getEmail()
                + " | displayName=" + account.getDisplayName());

        if (!CloudBackupScheduler.hasDriveAppDataScope(context)) {
            Log.e(TAG, "DRIVE_APPDATA scope NOT granted for " + account.getEmail()
                    + " — user must re-authorize. Failing permanently.");
            return Result.failure();
        }
        Log.d(TAG, "DRIVE_APPDATA scope confirmed.");

        // Stamp last-run time now to avoid tight retry loops if a single upload fails.
        prefs.edit().putLong("last_backup_time", System.currentTimeMillis()).apply();

        // ── Step 5: Build project list ───────────────────────────────────────────────
        CloudBackupManager cloudManager = new CloudBackupManager(context, account);

        ArrayList<HashMap<String, Object>> allProjects = lC.a();
        if (allProjects == null || allProjects.isEmpty()) {
            Log.i(TAG, "No projects found on device — nothing to back up.");
            return Result.success();
        }
        Log.d(TAG, "Total projects on device: " + allProjects.size());

        Set<String> selectedScIds = prefs.getStringSet("auto_backup_sc_ids", new HashSet<>());
        ArrayList<HashMap<String, Object>> projectsToBackup = new ArrayList<>();

        if (selectedScIds.isEmpty()) {
            projectsToBackup.addAll(allProjects);
            Log.d(TAG, "Backing up ALL projects (" + projectsToBackup.size() + ").");
        } else {
            for (HashMap<String, Object> project : allProjects) {
                if (selectedScIds.contains((String) project.get("sc_id"))) {
                    projectsToBackup.add(project);
                }
            }
            Log.d(TAG, "Backing up " + projectsToBackup.size() + " selected project(s) "
                    + "out of " + allProjects.size() + " total.");
        }

        int total = projectsToBackup.size();
        if (total == 0) {
            Log.i(TAG, "No matching projects for the current filter — nothing to do.");
            return Result.success();
        }

        // ── Step 6: Per-project backup + upload loop ─────────────────────────────────
        boolean allSuccess = true;

        for (int i = 0; i < total; i++) {
            HashMap<String, Object> project = projectsToBackup.get(i);
            String scId = (String) project.get("sc_id");
            String projectName = (String) project.get("my_app_name");

            if (scId == null || projectName == null) {
                Log.w(TAG, "Skipping project at index " + i + " — null sc_id or app_name.");
                continue;
            }

            Log.i(TAG, "--- Project [" + (i + 1) + "/" + total + "]: "
                    + projectName + "  sc_id=" + scId);
            updateNotification("Backing up: " + projectName, i + 1, total);

            // 6a. Generate .swb zip
            CloudBackupFactory backupFactory = new CloudBackupFactory(scId);
            backupFactory.backup(context, projectName);
            File swbFile = backupFactory.getOutFile();

            if (swbFile == null) {
                Log.e(TAG, "backup() returned null outFile for " + projectName + " — skipping.");
                allSuccess = false;
                continue;
            }
            if (!swbFile.exists() || swbFile.length() == 0) {
                Log.e(TAG, ".swb file invalid: exists=" + swbFile.exists()
                        + " size=" + swbFile.length() + " path=" + swbFile.getAbsolutePath());
                allSuccess = false;
                continue;
            }
            Log.d(TAG, ".swb ready: " + swbFile.getAbsolutePath()
                    + " | " + swbFile.length() + " bytes");

            // 6b. Upload
            try {
                uploadSync(cloudManager, swbFile, projectName);
                Log.i(TAG, "Upload SUCCESS for " + projectName);
            } catch (Exception e) {
                Log.e(TAG, "Upload FAILED for " + projectName, e);
                allSuccess = false;
            } finally {
                boolean deleted = swbFile.delete();
                Log.d(TAG, "Temp .swb deleted=" + deleted + "  path=" + swbFile.getAbsolutePath());
            }
        }

        // ── Step 7: Cleanup and report ───────────────────────────────────────────────
        FileUtil.deleteFile(CloudBackupFactory.getCloudBackupDir());
        Log.d(TAG, "Cloud backup temp directory cleaned up.");

        if (allSuccess) {
            updateNotification("Cloud Backup Complete!", total, total);
            Log.i(TAG, "========== AutoBackupWorker finished: SUCCESS ==========");
            return Result.success();
        } else {
            updateNotification("Cloud Backup finished with errors.", total, total);
            Log.w(TAG, "========== AutoBackupWorker finished: PARTIAL FAILURE — retrying ==========");
            return Result.retry();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Network helpers
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Returns true when there is an active, validated internet connection.
     * Uses {@link NetworkCapabilities} on Android Q+ and the legacy
     * {@code getActiveNetworkInfo()} on older devices as a fallback.
     */
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.e(TAG, "ConnectivityManager is null — assuming no network.");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Network active = cm.getActiveNetwork();
            if (active == null) {
                Log.d(TAG, "isNetworkAvailable: getActiveNetwork() returned null");
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            boolean ok = caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            Log.d(TAG, "isNetworkAvailable [NetworkCapabilities]: " + ok
                    + (caps != null ? " | transport flags available" : " | caps null"));
            return ok;
        } else {
            //noinspection deprecation
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            boolean ok = info != null && info.isConnected() && info.isAvailable();
            Log.d(TAG, "isNetworkAvailable [legacy NetworkInfo]: " + ok);
            return ok;
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Upload helper — CountDownLatch replaces raw Object.wait()
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Wraps the async {@link CloudBackupManager#uploadBackupToCloud} in a
     * {@link CountDownLatch} so that the Worker thread blocks until the upload
     * completes or times out. This is safer than a raw {@code synchronized}/
     * {@code wait()} block because CountDownLatch cannot be signalled by spurious
     * wakeups and its semantics are unambiguous.
     */
    private void uploadSync(
            CloudBackupManager cloudManager,
            File swbFile,
            String projectName) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> uploadError = new AtomicReference<>(null);

        Log.d(TAG, "uploadSync: enqueueing | file=" + swbFile.getName()
                + " | size=" + swbFile.length() + " B | project=" + projectName);

        cloudManager.uploadBackupToCloud(swbFile, projectName,
                new CloudBackupManager.BackupCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Log.i(TAG, "uploadSync → onSuccess: " + message);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "uploadSync → onError: " + error);
                        uploadError.set(new Exception(error));
                        latch.countDown();
                    }
                });

        boolean completed = latch.await(UPLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (!completed) {
            throw new Exception("Upload timed out after "
                    + (UPLOAD_TIMEOUT_MS / 1000) + " s for project: " + projectName);
        }
        if (uploadError.get() != null) {
            throw uploadError.get();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ────────────────────────────────────────────────────────────────────────────────

    /** Builds the initial {@link ForegroundInfo} shown as soon as the worker starts. */
    private ForegroundInfo buildForegroundInfo(String text, int current, int total) {
        return new ForegroundInfo(NOTIFICATION_ID, buildNotification(text, current, total, true));
    }

    private void updateNotification(String text, int current, int total) {
        notificationManager.notify(NOTIFICATION_ID,
                buildNotification(text, current, total, current < total));
    }

    private android.app.Notification buildNotification(
            String text, int current, int total, boolean ongoing) {
        return new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Sketchware Cloud Sync")
                .setContentText(text)
                .setProgress(total, current, total == 0)
                .setOngoing(ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Cloud Backup", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows progress of automatic cloud backups");
            notificationManager.createNotificationChannel(channel);
        }
    }
}