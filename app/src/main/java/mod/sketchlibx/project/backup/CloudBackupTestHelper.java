package mod.sketchlibx.project.backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import a.a.a.lC;

/**
 * CloudBackupTestHelper
 *
 * Provides a "Test Cloud Backup" action for use in settings or debug screens.
 * Runs the full backup pipeline (generate → zip → upload) for a single project
 * on a background thread and shows a detailed result dialog on the UI thread.
 *
 * Usage:
 * <pre>
 *   // In your Activity / Fragment
 *   testButton.setOnClickListener(v ->
 *       CloudBackupTestHelper.runTest(this));
 * </pre>
 *
 * The test picks the first available project (or whichever {@code sc_id} you
 * supply via {@link #runTestForProject}). It is deliberately self-contained and
 * does not modify any "last_backup_time" preference, so it won't interfere with
 * the scheduled worker.
 */
public class CloudBackupTestHelper {

    private static final String TAG = "CloudBackupTestHelper";
    private static final long UPLOAD_TIMEOUT_MS = 120_000L;

    // ────────────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Runs a test backup for the first project found on the device.
     * Shows a progress dialog while running, then a result dialog when done.
     *
     * @param activity calling Activity (used for dialog and context)
     */
    public static void runTest(Activity activity) {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            showDialog(activity, "No Projects Found",
                    "There are no Sketchware projects on this device to test with.");
            return;
        }
        String scId   = (String) projects.get(0).get("sc_id");
        String name   = (String) projects.get(0).get("my_app_name");
        runTestForProject(activity, scId, name);
    }

    /**
     * Runs a test backup for a specific project by {@code sc_id}.
     *
     * @param activity    calling Activity
     * @param sc_id       project identifier
     * @param projectName display name (for notifications and Drive metadata)
     */
    public static void runTestForProject(Activity activity, String sc_id, String projectName) {
        if (sc_id == null || projectName == null) {
            showDialog(activity, "Invalid Project", "sc_id or project name is null.");
            return;
        }

        Log.i(TAG, "=== Test backup START | sc_id=" + sc_id + " | project=" + projectName + " ===");

        // Show progress dialog
        AlertDialog[] progressHolder = new AlertDialog[1];
        activity.runOnUiThread(() -> {
            progressHolder[0] = new AlertDialog.Builder(activity)
                    .setTitle("Testing Cloud Backup")
                    .setMessage("Generating backup for:\n" + projectName + "\n\nPlease wait…")
                    .setCancelable(false)
                    .create();
            progressHolder[0].show();
        });

        // Run on background thread
        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            boolean success = false;

            try {
                // ── Pre-flight checks ────────────────────────────────────────────────
                Context ctx = activity.getApplicationContext();

                report.append("▶ Account check\n");
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(ctx);
                if (account == null) {
                    report.append("  ✗ Not signed in to Google.\n");
                    finishTest(activity, progressHolder[0], false, report.toString());
                    return;
                }
                report.append("  ✓ Signed in as ").append(account.getEmail()).append("\n\n");

                report.append("▶ DRIVE_APPDATA scope check\n");
                if (!CloudBackupScheduler.hasDriveAppDataScope(ctx)) {
                    report.append("  ✗ DRIVE_APPDATA scope not granted.\n"
                            + "    Re-authorize via the Cloud Backup sign-in flow.\n");
                    finishTest(activity, progressHolder[0], false, report.toString());
                    return;
                }
                report.append("  ✓ Scope granted\n\n");

                // ── Generate .swb ────────────────────────────────────────────────────
                report.append("▶ Generating .swb backup\n");
                report.append("  sc_id   = ").append(sc_id).append("\n");
                report.append("  project = ").append(projectName).append("\n");

                CloudBackupFactory factory = new CloudBackupFactory(sc_id);
                factory.backup(ctx, projectName);
                File swbFile = factory.getOutFile();

                if (swbFile == null || !swbFile.exists() || swbFile.length() == 0) {
                    report.append("  ✗ .swb generation FAILED\n");
                    if (swbFile != null) {
                        report.append("    exists=").append(swbFile.exists())
                              .append("  size=").append(swbFile.length()).append("\n");
                    } else {
                        report.append("    outFile is null\n");
                    }
                    finishTest(activity, progressHolder[0], false, report.toString());
                    return;
                }
                report.append("  ✓ .swb created: ").append(swbFile.getName()).append("\n");
                report.append("  ✓ Size: ").append(swbFile.length()).append(" bytes\n\n");

                // ── Upload ───────────────────────────────────────────────────────────
                report.append("▶ Uploading to Google Drive (appDataFolder)\n");

                CloudBackupManager manager = new CloudBackupManager(ctx, account);

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> successMsg = new AtomicReference<>(null);
                AtomicReference<String> errorMsg   = new AtomicReference<>(null);

                manager.uploadBackupToCloud(swbFile, projectName,
                        new CloudBackupManager.BackupCallback() {
                            @Override
                            public void onSuccess(String message) {
                                successMsg.set(message);
                                latch.countDown();
                            }

                            @Override
                            public void onError(String error) {
                                errorMsg.set(error);
                                latch.countDown();
                            }
                        });

                boolean completed = latch.await(UPLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                // Clean up temp file regardless of upload result
                if (swbFile.exists()) {
                    boolean del = swbFile.delete();
                    Log.d(TAG, "Temp .swb deleted=" + del);
                }
                neo.sketchware.utility.FileUtil.deleteFile(CloudBackupFactory.getCloudBackupDir());

                if (!completed) {
                    report.append("  ✗ Upload TIMED OUT after ")
                          .append(UPLOAD_TIMEOUT_MS / 1000).append(" seconds\n");
                    finishTest(activity, progressHolder[0], false, report.toString());
                    return;
                }

                if (errorMsg.get() != null) {
                    report.append("  ✗ Upload FAILED:\n");
                    // Indent the error block for readability
                    for (String line : errorMsg.get().split("\n")) {
                        report.append("    ").append(line).append("\n");
                    }
                    finishTest(activity, progressHolder[0], false, report.toString());
                    return;
                }

                report.append("  ✓ ").append(successMsg.get()).append("\n\n");

                // ── Verify via file count ────────────────────────────────────────────
                report.append("▶ Verifying upload (file count in appDataFolder)\n");

                CountDownLatch countLatch = new CountDownLatch(1);
                AtomicReference<Integer> fileCount = new AtomicReference<>(-1);

                manager.getCloudBackupCount(new CloudBackupManager.CountCallback() {
                    @Override
                    public void onResult(int count) {
                        fileCount.set(count);
                        countLatch.countDown();
                    }

                    @Override
                    public void onError(String error) {
                        fileCount.set(-1);
                        countLatch.countDown();
                    }
                });

                countLatch.await(30, TimeUnit.SECONDS);

                int count = fileCount.get();
                if (count >= 0) {
                    report.append("  ✓ Files in appDataFolder: ").append(count).append("\n\n");
                } else {
                    report.append("  ⚠ Could not verify file count (non-fatal)\n\n");
                }

                manager.shutdown();

                // ── Worker status ────────────────────────────────────────────────────
                report.append("▶ WorkManager status\n");
                report.append("  ").append(CloudBackupScheduler.getWorkStatusSummary(ctx)).append("\n");

                success = true;

            } catch (Exception e) {
                Log.e(TAG, "Test backup threw unexpected exception", e);
                report.append("\n✗ Unexpected error:\n").append(Log.getStackTraceString(e));
            }

            finishTest(activity, progressHolder[0], success, report.toString());

        }, "cloud-backup-test-thread").start();
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Internal
    // ────────────────────────────────────────────────────────────────────────────────

    private static void finishTest(
            Activity activity,
            AlertDialog progressDialog,
            boolean success,
            String report) {

        Log.i(TAG, "=== Test backup " + (success ? "PASSED" : "FAILED") + " ===\n" + report);

        activity.runOnUiThread(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception ignored) {}

            showDialog(activity,
                    success ? "✓ Cloud Backup Test Passed" : "✗ Cloud Backup Test Failed",
                    report);
        });
    }

    private static void showDialog(Activity activity, String title, String message) {
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show());
    }
}