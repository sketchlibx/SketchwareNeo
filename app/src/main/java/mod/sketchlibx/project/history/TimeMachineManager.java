package mod.sketchlibx.project.history;

import android.os.Environment;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.utility.FileUtil;

public class TimeMachineManager {

    private static final String HISTORY_DIR = ".sketchware/backups/history/";
    private static final int MAX_SNAPSHOTS = 20;

    public static void takeSnapshot(String sc_id) {
    
        ProjectSettings settings = new ProjectSettings(sc_id);
        if (!settings.getValue("enable_version_history", "false").equals("true")) {
            return; // Silently abort, user opted out
        }

        new Thread(() -> {
            try {
                File historyFolder = new File(Environment.getExternalStorageDirectory(), HISTORY_DIR + sc_id);
                if (!historyFolder.exists()) historyFolder.mkdirs();

                String timestamp = new SimpleDateFormat("dd-MMM-yyyy_hh-mm-ss_a", Locale.ENGLISH).format(new Date());
                File outZip = new File(historyFolder, "Snapshot_" + timestamp + ".zip");

                File tempDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/cache/history_temp_" + sc_id);
                if (tempDir.exists()) FileUtil.deleteFile(tempDir.getAbsolutePath());
                tempDir.mkdirs();

                File dataDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id);
                File projFile = new File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list/" + sc_id + "/project");

                BackupFactory.copy(dataDir, new File(tempDir, "data"));
                BackupFactory.copy(projFile, new File(tempDir, "project"));

                BackupFactory.zipFolder(tempDir, outZip);
                FileUtil.deleteFile(tempDir.getAbsolutePath());
                
                File[] existingSnaps = historyFolder.listFiles((dir, name) -> name.endsWith(".zip"));
                if (existingSnaps != null && existingSnaps.length > 1) {
                    Arrays.sort(existingSnaps, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                    File newest = existingSnaps[0];
                    File previous = existingSnaps[1];

                    // Prevent duplicate saving if file size matches
                    if (newest.length() == previous.length()) {
                        newest.delete();
                        return;
                    }
                }

                // Cleanup oldest snapshots to avoid filling user storage
                File[] files = historyFolder.listFiles((dir, name) -> name.endsWith(".zip"));
                if (files != null && files.length > MAX_SNAPSHOTS) {
                    Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
                    int toDelete = files.length - MAX_SNAPSHOTS;
                    for (int i = 0; i < toDelete; i++) {
                        files[files.length - 1 - i].delete();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static boolean restoreSnapshot(String sc_id, File snapshotZip) {
        try {
            File tempDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/cache/history_temp_" + sc_id);
            if (tempDir.exists()) FileUtil.deleteFile(tempDir.getAbsolutePath());
            tempDir.mkdirs();

            boolean unzipped = BackupFactory.unzip(snapshotZip, tempDir);
            if (!unzipped) return false;

            File dataDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id);
            File projFile = new File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list/" + sc_id + "/project");

            FileUtil.deleteFile(dataDir.getAbsolutePath());
            
            BackupFactory.copySafe(new File(tempDir, "data"), dataDir);
            BackupFactory.copySafe(new File(tempDir, "project"), projFile);

            FileUtil.deleteFile(tempDir.getAbsolutePath());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
