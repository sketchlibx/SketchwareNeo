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

public class CloudBackupManager {

    private static final String TAG = "CloudBackupManager";
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
                    .setApplicationName("Sketchware Neo Backup")
                    .build();

            Log.i(TAG, "Drive service initialised successfully.");
        } catch (Exception e) {
            initError = Log.getStackTraceString(e);
            Log.e(TAG, "Drive service init FAILED:\n" + initError);
        }
    }

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
                String query = "name = '" + fileName.replace("'", "\\'")
                        + "' and '" + FOLDER_SPACE + "' in parents and trashed = false";
                
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setQ(query)
                        .setFields("files(id, name, size)")
                        .execute();

                int matchCount = result.getFiles() != null ? result.getFiles().size() : 0;
                FileContent mediaContent = new FileContent("application/zip", swbFile);

                if (matchCount > 0) {
                    String existingId = result.getFiles().get(0).getId();
                    File updateMeta = new File();
                    updateMeta.setProperties(Collections.singletonMap("projectName", projectName));

                    File updated = driveService.files()
                            .update(existingId, updateMeta, mediaContent)
                            .setFields("id, name, size")
                            .execute();

                    Log.i(TAG, "Drive UPDATE success | id=" + updated.getId());
                    postSuccess(callback, "Backup overwritten in cloud: " + updated.getName());
                } else {
                    File fileMeta = new File();
                    fileMeta.setName(fileName);
                    fileMeta.setParents(Collections.singletonList(FOLDER_SPACE));
                    fileMeta.setProperties(Collections.singletonMap("projectName", projectName));

                    File created = driveService.files()
                            .create(fileMeta, mediaContent)
                            .setFields("id, name, size")
                            .execute();

                    Log.i(TAG, "Drive CREATE success | id=" + created.getId());
                    postSuccess(callback, "New backup uploaded to cloud: " + created.getName());
                }

            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                Log.e(TAG, "uploadBackupToCloud FAILED | " + diagnosis, e);
                postError(callback, "Cloud upload failed [" + diagnosis + "]:\n" + Log.getStackTraceString(e));
            }
        });
    }

    public void getCloudBackupsList(final FileListCallback callback) {
        if (driveService == null) {
            String msg = "Drive service not available.\n\nDetails:\n" + initError;
            mainHandler.post(() -> callback.onError(msg));
            return;
        }

        executor.execute(() -> {
            try {
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setFields("files(id, name, createdTime, size, properties)")
                        .execute();

                mainHandler.post(() -> callback.onSuccess(result.getFiles()));
            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                Log.e(TAG, "getCloudBackupsList FAILED | " + diagnosis, e);
                mainHandler.post(() -> callback.onError("Failed to fetch cloud backups [" + diagnosis + "]:\n" + Log.getStackTraceString(e)));
            }
        });
    }

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
            try {
                java.io.File destFile = new java.io.File(downloadPath, fileName);
                if (!destFile.getParentFile().exists()) {
                    destFile.getParentFile().mkdirs();
                }

                OutputStream out = new FileOutputStream(destFile);
                driveService.files().get(fileId).executeMediaAndDownloadTo(out);
                out.flush();
                out.close();

                postSuccess(callback, "Backup downloaded to " + destFile.getAbsolutePath());

            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                Log.e(TAG, "downloadBackupFromCloud FAILED | " + diagnosis, e);
                postError(callback, "Download failed [" + diagnosis + "]:\n" + Log.getStackTraceString(e));
            }
        });
    }

    public void getCloudBackupCount(final CountCallback callback) {
        if (driveService == null) {
            mainHandler.post(() -> callback.onError("Drive service not available."));
            return;
        }

        executor.execute(() -> {
            try {
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setFields("files(id, name, size)")
                        .execute();

                int count = result.getFiles() != null ? result.getFiles().size() : 0;
                mainHandler.post(() -> callback.onResult(count));
            } catch (Exception e) {
                String diagnosis = diagnoseDriveError(e);
                mainHandler.post(() -> callback.onError("Count query failed [" + diagnosis + "]"));
            }
        });
    }

    public void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void postSuccess(BackupCallback callback, String msg) {
        mainHandler.post(() -> callback.onSuccess(msg));
    }

    private void postError(BackupCallback callback, String err) {
        mainHandler.post(() -> callback.onError(err));
    }

    private String diagnoseDriveError(Exception e) {
        if (e == null) return "UNKNOWN";

        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (e instanceof com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            com.google.api.client.googleapis.json.GoogleJsonResponseException gje =
                    (com.google.api.client.googleapis.json.GoogleJsonResponseException) e;
            int code = gje.getStatusCode();

            return switch (code) {
                case 401 -> "AUTH_EXPIRED (HTTP 401 — user must re-sign-in)";
                case 403 -> {
                    if (msg.contains("quota") || msg.contains("storageQuota")) {
                        yield "QUOTA_EXCEEDED (HTTP 403)";
                    }
                    if (msg.contains("accessnotconfigured") || msg.contains("has not been used in project")
                            || msg.contains("it is disabled") || msg.contains("service_disabled")) {
                        yield "DRIVE_API_DISABLED (HTTP 403 — Google Drive API is not enabled for your project)";
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
