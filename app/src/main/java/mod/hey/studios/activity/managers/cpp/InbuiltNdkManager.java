package mod.hey.studios.activity.managers.cpp;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

public class InbuiltNdkManager {

    private static final String TAG = "InbuiltNdkManager";

    public interface InstallCallback {
        void onProgress(String message, int progress, boolean isIndeterminate);
        void onSuccess();
        void onError(String error);
    }

    public static boolean isNdkInstalled(Context context) {
        File cmake = new File(context.getFilesDir(), "cmake/3.22.1/bin/cmake");
        return cmake.exists() && getInstalledNdkDir(context) != null;
    }

    public static File getInstalledNdkDir(Context context) {
        File[] versions = new File(context.getFilesDir(), "ndk").listFiles(File::isDirectory);
        if (versions == null) return null;
        for (File version : versions) {
            if (new File(version, "build/cmake/android.toolchain.cmake").exists()) {
                return version;
            }
        }
        return null;
    }

    private static String guessNdkVersionFromUrl(String url) {
        Matcher m = Pattern.compile("r\\d+[a-z]?", Pattern.CASE_INSENSITIVE).matcher(url);
        if (m.find()) return m.group().toLowerCase(Locale.ROOT);
        return "custom-" + Integer.toHexString(url.hashCode());
    }

    public static void installNdkAndCmake(Context context, String customNdkUrl, InstallCallback callback) {
        new Thread(() -> {
            try {
                if (!Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a")) {
                    throw new Exception("This device's CPU (" + Build.SUPPORTED_ABIS[0] + ") is not arm64-v8a. "
                            + "The inbuilt on-device compiler only supports arm64 devices.");
                }

                String unzipBinary = resolveUnzipBinary();
                if (unzipBinary == null) {
                    throw new Exception("Your device/ROM does not have the 'unzip' utility available "
                            + "(checked /system/bin/unzip and PATH).");
                }

                File filesDir = context.getFilesDir();
                File cmakeDir = new File(filesDir, "cmake");
                File ndkBaseDir = new File(filesDir, "ndk");

                FileUtil.makeDir(cmakeDir.getAbsolutePath());
                FileUtil.makeDir(ndkBaseDir.getAbsolutePath());

                Handler handler = new Handler(Looper.getMainLooper());

                if (!new File(cmakeDir, "3.22.1/bin/cmake").exists()) {
                    File cmakeZip = new File(cmakeDir, "cmake-3.22.1.zip");
                    downloadFile("https://github.com/MrIkso/AndroidIDE-NDK/releases/download/cmake/cmake-3.22.1-android-aarch64.zip", cmakeZip, handler, callback, "Downloading CMake");

                    handler.post(() -> callback.onProgress("Extracting CMake...", 0, true));
                    unzipNative(unzipBinary, cmakeZip, cmakeDir);
                    cmakeZip.delete();
                    chmodExecutableRecursive(new File(cmakeDir, "3.22.1/bin"));
                } else {
                    handler.post(() -> callback.onProgress("CMake already installed, skipping...", 0, true));
                }

                String versionTag = guessNdkVersionFromUrl(customNdkUrl);
                File targetNdkDir = new File(ndkBaseDir, versionTag);
                boolean ndkAlreadyInstalled = new File(targetNdkDir, "build/cmake/android.toolchain.cmake").exists();

                if (!ndkAlreadyInstalled) {
                    File ndkZip = new File(ndkBaseDir, "ndk-package.zip");
                    downloadFile(customNdkUrl, ndkZip, handler, callback, "Downloading NDK");

                    handler.post(() -> callback.onProgress("Extracting NDK...", 0, true));
                    unzipNative(unzipBinary, ndkZip, ndkBaseDir);
                    ndkZip.delete();

                    deleteRecursive(targetNdkDir);

                    File[] dirs = ndkBaseDir.listFiles();
                    File extractedRoot = null;
                    if (dirs != null) {
                        for (File d : dirs) {
                            if (d.isDirectory() && d.getName().startsWith("android-ndk") && !d.getName().equals(versionTag)) {
                                extractedRoot = d;
                                break;
                            }
                        }
                    }
                    if (extractedRoot == null) {
                        throw new Exception("NDK zip extracted, but no 'android-ndk*' folder was found inside " + ndkBaseDir + ".");
                    }
                    moveDirectory(extractedRoot, targetNdkDir);

                    chmodExecutableRecursive(new File(targetNdkDir, "toolchains/llvm/prebuilt"));

                    handler.post(() -> callback.onProgress("Repairing broken symlinks...", 0, true));
                    repairBrokenSymlinkStubs(new File(targetNdkDir, "toolchains/llvm/prebuilt"));
                } else {
                    handler.post(() -> callback.onProgress("NDK " + versionTag + " already installed — checking for broken symlinks...", 0, true));
                    repairBrokenSymlinkStubs(new File(targetNdkDir, "toolchains/llvm/prebuilt"));
                }

                handler.post(callback::onSuccess);
            } catch (Exception e) {
                Log.e(TAG, "Install failed", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    private static String resolveUnzipBinary() {
        String[] candidates = {"/system/bin/unzip", "/system/xbin/unzip", "unzip"};
        for (String candidate : candidates) {
            try {
                Process p = new ProcessBuilder(candidate, "-v").redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    while (r.readLine() != null) { /* drain */ }
                }
                p.waitFor();
                return candidate;
            } catch (IOException notFound) {
                // try next candidate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    private static void unzipNative(String unzipBinary, File zipFile, File targetDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(unzipBinary, "-o", zipFile.getAbsolutePath(), "-d", targetDir.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) Log.d(TAG, line);
        }

        if (process.waitFor() != 0) {
            throw new Exception("Extraction of " + zipFile.getName() + " failed. The download may be corrupt — try Setup NDK again.");
        }
    }

    /**
     * Repairs the case where a symlink from the original NDK archive (clang, clang++, ...) got
     * extracted as a tiny executable-flagged text file containing just the link target's name,
     * instead of a real symlink. Replaces each such stub with a real copy of the sibling it names.
     */
    private static void repairBrokenSymlinkStubs(File prebuiltRoot) {
        if (!prebuiltRoot.isDirectory()) return;

        repairBrokenSymlinkStubsInDir(new File(prebuiltRoot, "bin"));

        File[] hostDirs = prebuiltRoot.listFiles(File::isDirectory);
        if (hostDirs == null) return;
        for (File hostDir : hostDirs) {
            repairBrokenSymlinkStubsInDir(new File(hostDir, "bin"));
        }
    }

    private static void repairBrokenSymlinkStubsInDir(File binDir) {
        if (!binDir.isDirectory()) return;
        File[] files = binDir.listFiles();
        if (files == null) return;

        Map<String, File> byName = new HashMap<>();
        for (File f : files) byName.put(f.getName(), f);

        for (File candidate : files) {
            if (!candidate.isFile() || candidate.length() == 0 || candidate.length() > 512) continue;
            if (isValidElf(candidate)) continue;

            String content = readWholeFileIfPrintableText(candidate);
            if (content == null) continue;
            content = content.trim();
            if (content.isEmpty() || content.contains("/") || content.contains("\n") || content.length() > 128) continue;

            File target = byName.get(content);
            if (target != null && !target.equals(candidate) && isValidElf(target)) {
                try {
                    FileUtil.copyFile(target.getAbsolutePath(), candidate.getAbsolutePath());
                    candidate.setExecutable(true, false);
                    Log.i(TAG, "Repaired broken symlink-stub " + candidate + " -> copied bytes from " + target);
                } catch (Exception e) {
                    Log.w(TAG, "Found broken stub " + candidate + " but failed to repair it", e);
                }
            }
        }
    }

    private static boolean isValidElf(File file) {
        if (!file.isFile() || file.length() < 4) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int read = fis.read(magic);
            return read == 4 && magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F';
        } catch (IOException e) {
            return false;
        }
    }

    private static String readWholeFileIfPrintableText(File file) {
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                if (fis.read(bytes) != bytes.length) return null;
            }
            for (byte b : bytes) {
                if (b == 0) return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> listInstalledNdkVersions(Context context) {
        List<String> result = new ArrayList<>();
        File[] versions = new File(context.getFilesDir(), "ndk").listFiles(File::isDirectory);
        if (versions != null) {
            for (File v : versions) {
                if (new File(v, "build/cmake/android.toolchain.cmake").exists()) {
                    result.add(v.getName());
                }
            }
        }
        return result;
    }

    public static void deleteNdkVersion(Context context, String versionTag) {
        deleteRecursive(new File(new File(context.getFilesDir(), "ndk"), versionTag));
    }

    public static void deleteAllNdkVersions(Context context) {
        File[] versions = new File(context.getFilesDir(), "ndk").listFiles(File::isDirectory);
        if (versions != null) {
            for (File v : versions) deleteRecursive(v);
        }
    }

    public static void repairInstalledNdk(Context context, String versionTag) {
        File ndkDir = new File(new File(context.getFilesDir(), "ndk"), versionTag);
        repairBrokenSymlinkStubs(new File(ndkDir, "toolchains/llvm/prebuilt"));
    }

    private static void chmodExecutableRecursive(File dir) {
        if (!dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                chmodExecutableRecursive(child);
            } else {
                child.setExecutable(true, false);
            }
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    private static void moveDirectory(File source, File target) throws Exception {
        if (source.renameTo(target)) return;
        copyRecursive(source, target);
        deleteRecursive(source);
        if (!target.exists()) {
            throw new Exception("Moved (copied) " + source + " to " + target + " but the target doesn't exist afterward.");
        }
    }

    private static void copyRecursive(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new Exception("Could not create directory " + target);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursive(child, new File(target, child.getName()));
                }
            }
        } else {
            FileUtil.copyFile(source.getAbsolutePath(), target.getAbsolutePath());
            target.setExecutable(source.canExecute(), false);
        }
    }

    private static void downloadFile(String urlStr, File dest, Handler handler, InstallCallback callback, String prefix) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.connect();

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server returned HTTP " + conn.getResponseCode() + " " + conn.getResponseMessage() + " for " + urlStr);
        }

        int fileLength = conn.getContentLength();
        try (InputStream input = new BufferedInputStream(conn.getInputStream());
             FileOutputStream output = new FileOutputStream(dest)) {

            byte[] data = new byte[8192];
            long total = 0;
            int count;
            long lastUpdate = 0;

            while ((count = input.read(data)) != -1) {
                total += count;
                output.write(data, 0, count);

                long now = System.currentTimeMillis();
                if (now - lastUpdate > 150) {
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        handler.post(() -> callback.onProgress(prefix + "... " + progress + "%", progress, false));
                    } else {
                        long kb = total / 1024;
                        handler.post(() -> callback.onProgress(prefix + "... " + kb + " KB", 0, true));
                    }
                    lastUpdate = now;
                }
            }
        }
    }
}
