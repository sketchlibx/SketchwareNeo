package mod.sketchlibx.project.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CloudBackupManager
 *
 * Wraps the Google Drive REST API (appDataFolder space) for:
 *  - Uploading a .swb backup file (create or overwrite by filename).
 *  - Listing existing cloud backups.
 *  - Downloading a specific backup.
 *
 * Key fixes applied (v2):
 *  - Detailed per-step logging for every Drive operation.
 *  - Structured Drive API error diagnosis: auth expiry, quota, permission issues.
 *  - {@link #shutdown()} method for safe executor teardown.
 *  - {@link #getCloudBackupCount} for verifying appDataFolder uploads.
 *  - All callbacks posted to the main thread via {@link #mainHandler}.
 */
public class CloudBackupManager {

    private static final String TAG = "CloudBackupManager";
    /** The Drive space used — isolated from the user's My Drive. */
    private static final String FOLDER_SPACE = "appDataFolder";

    private Drive driveService;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private String initError = null;

    public CloudBackupManager(Context context, GoogleSignInAccount account) {
        executor    = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "Initialising Drive service | account=" + account.getEmail());

        try {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singleton(DriveScopes.DRIVE_APPDATA));
            credential.setSelectedAccount(account.getAccount());

            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName("Sketchware Pro Backup")
                    .build();

            Log.i(TAG, "Drive service initialised successfully.");
        } catch (Exception e) {
            initError = Log.getStackTraceString(e);
            Log.e(TAG, "Drive service init FAILED:\n" + initError);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Callbacks
    // ────────────────────────────────────────────────────────────────────────────────

    public interface BackupCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface FileListCallback {
        void onSuccess(List<File> files);
        void onError(String error);
    }

    public interface CountCallback {
        void onResult(int count);
        void onError(String error);
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Upload
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Uploads {@code swbFile} to the appDataFolder. If a file with the same name
     * already exists it is overwritten via the Drive update API; otherwise a new
     * file is created.
     *
     * <p>The callback is always delivered on the <b>main thread</b>.</p>
     */
    public void uploadBackupToCloud(
            final java.io.File swbFile,
            final String projectName,
            final BackupCallback callback) {

        if (driveService == null) {
            String msg = "Drive service not available (init failed).\n\nDetails:\n" + initError;
            Log.e(TAG, msg);
            postError(callback, msg);
            return;
        }

        executor.execute(() -> {
            String fileName = swbFile.getName();
            Log.i(TAG, "uploadBackupToCloud START"
                    + " | file=" + fileName
                    + " | size=" + swbFile.length() + " B"
                    + " | project=" + projectName
                    + " | space=" + FOLDER_SPACE);

            try {
                // Step 1 — check for existing file with the same name
                String query = "name = '" + fileName.replace("'", "\\'")
                        + "' and '" + FOLDER_SPACE + "' in parents and trashed = false";
                Log.d(TAG, "Drive query: " + query);

                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setQ(query)
                        .setFields("files(id, name, size)")
                        .execute();

                int matchCount = result.getFiles() != null ? result.getFiles().size() : 0;
                Log.d(TAG, "Drive query result: " + matchCount + " matching file(s).");

                FileContent mediaContent = new FileContent("application/zip", swbFile);

                if (matchCount > 0) {
                    // Step 2a — overwrite existing
                    String existingId = result.getFiles().get(0).getId();
                    Log.d(TAG, "Overwriting existing Drive file | id=" + existingId);

                    File updateMeta = new File();
                    updateMeta.setProperties(
                            Collections.singletonMap("projectName", projectName));

                    File updated = driveService.files()
                            .update(existingId, updateMeta, mediaContent)
                            .setFields("id, name, size")
                            .execute();

                    Log.i(TAG, "Drive UPDATE success"
                            + " | id=" + updated.getId()
                            + " | name=" + updated.getName()
                            + " | size=" + updated.getSize());
                    postSuccess(callback, "Backup overwritten in cloud: " + updated.getName());

                } else {
                    // Step 2b — create new
                    Log.d(TAG, "No existing file — creating new entry in " + FOLDER_SPACE);

                    File fileMeta = new File();
                    fileMeta.setName(fileName);
                    fileMeta.setParents(Collections.singletonList(FOLDER_SPACE));
                    fileMeta.setProperties(
                            Collections.singletonMap("projectName", projectName));

                    File created = driveService.files()
                            .create(fileMeta, mediaContent)
                            .setFields("id, name, size")
                            .execute();

                    Log.i(TAG, "Drive CREATE success"
                            + " | id=" + created.getId()
                            + " | name=" + created.getName()
                            + " | size=" + created.getSize());
                    postSuccess(callback, "New backup uploaded to cloud: " + created.getName());
                }

            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                Log.e(TAG, "uploadBackupToCloud FAILED | " + diagnosis, e);
                postError(callback, "Cloud upload failed [" + diagnosis + "]:\n"
                        + Log.getStackTraceString(e));
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // List backups
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves the list of all backup files stored in the appDataFolder.
     * Callback delivered on the <b>main thread</b>.
     */
    public void getCloudBackupsList(final FileListCallback callback) {
        if (driveService == null) {
            String msg = "Drive service not available.\n\nDetails:\n" + initError;
            mainHandler.post(() -> callback.onError(msg));
            return;
        }

        executor.execute(() -> {
            Log.d(TAG, "getCloudBackupsList: fetching from " + FOLDER_SPACE);
            try {
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setFields("files(id, name, createdTime, size, properties)")
                        .execute();

                int count = result.getFiles() != null ? result.getFiles().size() : 0;
                Log.i(TAG, "getCloudBackupsList: " + count + " file(s) found.");
                mainHandler.post(() -> callback.onSuccess(result.getFiles()));

            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                Log.e(TAG, "getCloudBackupsList FAILED | " + diagnosis, e);
                mainHandler.post(() -> callback.onError(
                        "Failed to fetch cloud backups [" + diagnosis + "]:\n"
                                + Log.getStackTraceString(e)));
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Download
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Downloads a specific backup file to {@code downloadPath/fileName}.
     * Callback delivered on the <b>main thread</b>.
     */
    public void downloadBackupFromCloud(
            final String fileId,
            final String fileName,
            final String downloadPath,
            final BackupCallback callback) {

        if (driveService == null) {
            postError(callback, "Drive service not available.\n\nDetails:\n" + initError);
            return;
        }

        executor.execute(() -> {
            Log.i(TAG, "downloadBackupFromCloud START"
                    + " | fileId=" + fileId
                    + " | fileName=" + fileName
                    + " | dest=" + downloadPath);
            try {
                java.io.File destFile = new java.io.File(downloadPath, fileName);
                if (!destFile.getParentFile().exists()) {
                    boolean mkdirs = destFile.getParentFile().mkdirs();
                    Log.d(TAG, "Download dest dir created: " + mkdirs);
                }

                OutputStream out = new FileOutputStream(destFile);
                driveService.files().get(fileId).executeMediaAndDownloadTo(out);
                out.flush();
                out.close();

                Log.i(TAG, "downloadBackupFromCloud SUCCESS"
                        + " | dest=" + destFile.getAbsolutePath()
                        + " | size=" + destFile.length() + " B");
                postSuccess(callback,
                        "Backup downloaded to " + destFile.getAbsolutePath());

            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                Log.e(TAG, "downloadBackupFromCloud FAILED | " + diagnosis, e);
                postError(callback, "Download failed [" + diagnosis + "]:\n"
                        + Log.getStackTraceString(e));
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Verification helper
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Counts the number of files currently stored in the appDataFolder.
     * Use this after an upload to verify the file actually landed in Drive.
     *
     * Callback delivered on the <b>main thread</b>.
     */
    public void getCloudBackupCount(final CountCallback callback) {
        if (driveService == null) {
            mainHandler.post(() -> callback.onError("Drive service not available."));
            return;
        }

        executor.execute(() -> {
            Log.d(TAG, "getCloudBackupCount: querying " + FOLDER_SPACE);
            try {
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setFields("files(id, name, size)")
                        .execute();

                int count = result.getFiles() != null ? result.getFiles().size() : 0;
                Log.i(TAG, "getCloudBackupCount: " + count + " file(s) in appDataFolder.");

                if (result.getFiles() != null) {
                    for (File f : result.getFiles()) {
                        Log.d(TAG, "  → " + f.getName()
                                + " | id=" + f.getId()
                                + " | size=" + f.getSize());
                    }
                }

                final int finalCount = count;
                mainHandler.post(() -> callback.onResult(finalCount));

            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                Log.e(TAG, "getCloudBackupCount FAILED | " + diagnosis, e);
                mainHandler.post(() -> callback.onError(
                        "Count query failed [" + diagnosis + "]"));
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Shuts down the background executor gracefully.
     * Call this from {@code onDestroy()} of any Activity or Service that owns
     * a {@code CloudBackupManager} instance to prevent thread leaks.
     */
    public void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    Log.w(TAG, "Executor did not terminate cleanly — forced shutdown.");
                } else {
                    Log.d(TAG, "Executor shut down cleanly.");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                Log.w(TAG, "Executor shutdown interrupted.");
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ────────────────────────────────────────────────────────────────────────────────

    private void postSuccess(BackupCallback callback, String msg) {
        mainHandler.post(() -> callback.onSuccess(msg));
    }

    private void postError(BackupCallback callback, String err) {
        mainHandler.post(() -> callback.onError(err));
    }

    /**
     * Inspects a Drive exception and returns a short diagnostic label.
     * This makes it easier to triage logs without reading full stack traces.
     *
     * <p>Covered categories:
     * <ul>
     *   <li><b>AUTH_EXPIRED</b>  — 401 / token expired, user must re-sign-in.</li>
     *   <li><b>PERMISSION_DENIED</b> — 403 scope or access denied.</li>
     *   <li><b>QUOTA_EXCEEDED</b> — 403 storage/rate limit hit.</li>
     *   <li><b>NOT_FOUND</b>     — 404 file or folder missing.</li>
     *   <li><b>NETWORK_ERROR</b> — IOException / connectivity issue.</li>
     *   <li><b>UNKNOWN</b>       — anything else.</li>
     * </ul>
     */
    private String diagnoseDriveError(Exception e) {
        if (e == null) return "UNKNOWN";

        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (e instanceof com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            com.google.api.client.googleapis.json.GoogleJsonResponseException gje =
                    (com.google.api.client.googleapis.json.GoogleJsonResponseException) e;
            int code = gje.getStatusCode();
            Log.d(TAG, "GoogleJsonResponseException: HTTP " + code
                    + " | details=" + gje.getDetails());

            return switch (code) {
                case 401 -> "AUTH_EXPIRED (HTTP 401 — user must re-sign-in)";
                case 403 -> {
                    if (msg.contains("quota") || msg.contains("storageQuota")) {
                        yield "QUOTA_EXCEEDED (HTTP 403)";
                    }
                    yield "PERMISSION_DENIED (HTTP 403 — check DRIVE_APPDATA scope)";
                }
                case 404 -> "NOT_FOUND (HTTP 404)";
                case 429 -> "RATE_LIMITED (HTTP 429 — too many requests)";
                case 500, 502, 503 -> "DRIVE_SERVER_ERROR (HTTP " + code + ")";
                default  -> "DRIVE_ERROR_HTTP_" + code;
            };
        }

        if (e instanceof IOException) {
            if (msg.contains("ssl") || msg.contains("tls") || msg.contains("cert")) {
                return "SSL_ERROR";
            }
            return "NETWORK_ERROR (IOException)";
        }

        if (msg.contains("token") || msg.contains("auth") || msg.contains("credential")) {
            return "AUTH_ISSUE";
        }
        if (msg.contains("quota")) {
            return "QUOTA_EXCEEDED";
        }

        return "UNKNOWN (" + e.getClass().getSimpleName() + ")";
    }
}