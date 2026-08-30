package mod.sketchlibx.project.history;

import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

                File dataDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id);
                File projFile = new File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list/" + sc_id + "/project");

                // Check BEFORE doing any work whether anything actually changed since
                // the last snapshot - avoids creating (and then having to clean up) a
                // snapshot zip for a no-op save, and is a proper content comparison
                // rather than the old "delete it afterwards if the file size happens
                // to match" heuristic (two different snapshots can easily land on the
                // same byte size).
                if (!hasChangedSinceLastSnapshot(historyFolder, dataDir, projFile)) {
                    return;
                }

                String timestamp = new SimpleDateFormat("dd-MMM-yyyy_hh-mm-ss_a", Locale.ENGLISH).format(new Date());
                File outZip = new File(historyFolder, "Snapshot_" + timestamp + ".zip");

                File tempDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/cache/history_temp_" + sc_id);
                if (tempDir.exists()) FileUtil.deleteFile(tempDir.getAbsolutePath());
                tempDir.mkdirs();

                BackupFactory.copy(dataDir, new File(tempDir, "data"));
                BackupFactory.copy(projFile, new File(tempDir, "project"));

                BackupFactory.zipFolder(tempDir, outZip);
                FileUtil.deleteFile(tempDir.getAbsolutePath());

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

    /**
     * True if the live logic/view/file/project files differ (by actual byte
     * content, not size) from what's in the most recent existing snapshot, or
     * if there's no snapshot yet at all (first save always gets one).
     */
    private static boolean hasChangedSinceLastSnapshot(File historyFolder, File dataDir, File projFile) {
        File[] existing = historyFolder.listFiles((dir, name) -> name.endsWith(".zip"));
        if (existing == null || existing.length == 0) {
            return true;
        }

        Arrays.sort(existing, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        File latest = existing[0];

        try (ZipFile zip = new ZipFile(latest)) {
            if (!zipEntryMatchesFile(zip, "data/logic", new File(dataDir, "logic"))) return true;
            if (!zipEntryMatchesFile(zip, "data/view", new File(dataDir, "view"))) return true;
            if (!zipEntryMatchesFile(zip, "data/file", new File(dataDir, "file"))) return true;
            if (!zipEntryMatchesFile(zip, "project", projFile)) return true;
            return false;
        } catch (Exception e) {
            // If the latest snapshot can't be read for comparison, err on the
            // side of taking a new one rather than silently skipping forever.
            return true;
        }
    }

    private static boolean zipEntryMatchesFile(ZipFile zip, String entryName, File liveFile) {
        try {
            ZipEntry entry = zip.getEntry(entryName);
            byte[] zipBytes = readZipEntryBytes(zip, entry);
            byte[] liveBytes = readFileBytes(liveFile);
            return Arrays.equals(zipBytes, liveBytes);
        } catch (Exception e) {
            return false; // couldn't compare this file - treat as "changed" to be safe
        }
    }

    private static byte[] readZipEntryBytes(ZipFile zip, ZipEntry entry) throws Exception {
        if (entry == null) return new byte[0];
        try (InputStream is = zip.getInputStream(entry); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = is.read(buffer)) != -1) baos.write(buffer, 0, count);
            return baos.toByteArray();
        }
    }

    private static byte[] readFileBytes(File file) {
        if (file == null || !file.exists()) return new byte[0];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] bytes = new byte[(int) raf.length()];
            raf.readFully(bytes);
            return bytes;
        } catch (Exception e) {
            return new byte[0];
        }
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
