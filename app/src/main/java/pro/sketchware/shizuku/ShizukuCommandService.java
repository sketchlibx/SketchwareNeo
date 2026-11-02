package pro.sketchware.shizuku;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;

@Keep
public final class ShizukuCommandService extends IShizukuCommandService.Stub {
    private static final long COMMAND_TIMEOUT_SECONDS = 120L;

    public ShizukuCommandService() { }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String installApk(String apkPath) {
        if (apkPath == null || apkPath.trim().isEmpty()) return "ERROR: APK path is empty";
        File apk = new File(apkPath);
        if (!apk.isFile() || !apk.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            return "ERROR: APK file was not found";
        }
        if (!apk.canRead()) return "ERROR: APK file cannot be read by Shizuku";
        return installFromStream(apk);
    }

    @Override
    public String launchApp(String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            return "ERROR: Invalid package name";
        }
        return runCommand("/system/bin/monkey", "-p", packageName,
                "-c", "android.intent.category.LAUNCHER", "1");
    }


    private String installFromStream(File apk) {
        Process process = null;
        try {
            long length = apk.length();
            if (length <= 0L) return "ERROR: APK file is empty";
            process = new ProcessBuilder("/system/bin/pm", "install", "-r", "-S", String.valueOf(length))
                    .redirectErrorStream(true).start();
            Process running = process;
            FutureTask<String> outputTask = new FutureTask<>(() -> {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() < 4000) output.append(line).append('\n');
                    }
                }
                return output.toString().trim();
            });
            Thread reader = new Thread(outputTask, "shizuku-install-output");
            reader.setDaemon(true);
            reader.start();
            try (InputStream input = new FileInputStream(apk); OutputStream output = process.getOutputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush();
            }
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "ERROR: Package installation timed out";
            }
            String result = outputTask.get(5L, TimeUnit.SECONDS);
            if (process.exitValue() == 0 && result.toLowerCase(java.util.Locale.ROOT).contains("success")) {
                return "SUCCESS: " + result;
            }
            return "ERROR: " + (result.isEmpty() ? "Package manager rejected the APK" : result);
        } catch (Exception error) {
            if (process != null) process.destroyForcibly();
            return "ERROR: " + error.getMessage();
        }
    }

    private String runCommand(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Process runningProcess = process;
            FutureTask<String> outputTask = new FutureTask<>(() -> {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() < 4000) output.append(line).append('\n');
                    }
                }
                return output.toString().trim();
            });
            Thread outputReader = new Thread(outputTask, "shizuku-command-output");
            outputReader.setDaemon(true);
            outputReader.start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5L, TimeUnit.SECONDS);
                outputTask.cancel(true);
                return "ERROR: Command timed out";
            }
            String result = outputTask.get(5L, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            String normalized = result.toLowerCase(java.util.Locale.ROOT);
            if (command.length > 0 && command[0].endsWith("/monkey")
                    && (normalized.contains("no activities found") || normalized.contains("monkey aborted"))) {
                return "ERROR: No launcher activity was found";
            }
            if (command.length > 0 && command[0].endsWith("/pm")
                    && normalized.startsWith("failure")) {
                return "ERROR: " + result;
            }
            if (exitCode == 0) return "SUCCESS: " + result;
            return "ERROR: " + (result.isEmpty() ? "Command failed" : result);
        } catch (Exception error) {
            if (process != null) process.destroyForcibly();
            return "ERROR: " + error.getMessage();
        }
    }
}
