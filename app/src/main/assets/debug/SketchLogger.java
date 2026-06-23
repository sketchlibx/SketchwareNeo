package <?package_name?>;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import <?class_name_package?>.<?class_name?>;

public class SketchLogger {
    private static volatile boolean isRunning = false;
    private static ExecutorService executorService;
    private static Process logcatProcess;
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
    
    public static void d(String tag, String message) {
        Log.d(tag, message);
        broadcastDirectLog("D", tag, message);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        broadcastDirectLog("I", tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        broadcastDirectLog("W", tag, message);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
        broadcastDirectLog("E", tag, message);
    }

    public static void v(String tag, String message) {
        Log.v(tag, message);
        broadcastDirectLog("V", tag, message);
    }

    private static void broadcastDirectLog(String level, String tag, String message) {
        // Matches the standard Regex format expected by LogReaderActivity: "MM-dd HH:mm:ss.SSS LEVEL TAG: MSG"
        String formattedLog = String.format(Locale.ENGLISH, "%s %s %s: %s", dateFormat.format(new Date()), level, tag, message);
        broadcastLog(formattedLog);
    }

    // --- Background Logcat Process ---

    public static synchronized void startLogging() {
        if (isRunning) {
            broadcastDirectLog("W", "SketchLogger", "Logger is already running.");
            return;
        }

        isRunning = true;
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        executorService.execute(() -> {
            try {
                // Clear the buffer first
                Runtime.getRuntime().exec("logcat -c").waitFor();
                
                // Use explicit time formatting to ensure regex matches across all Android versions
                logcatProcess = Runtime.getRuntime().exec("logcat -v time");

                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()))) {
                    String logLine;
                    while (isRunning && (logLine = bufferedReader.readLine()) != null) {
                        // Skip blank lines
                        if (logLine.trim().isEmpty()) continue;
                        broadcastLog(logLine);
                    }
                }
            } catch (IOException | InterruptedException e) {
                broadcastDirectLog("E", "SketchLogger", "Logger process error: " + e.getMessage());
            } finally {
                if (isRunning) {
                    broadcastDirectLog("W", "SketchLogger", "System killed logcat process. Auto-recovering...");
                    isRunning = false;
                    startLogging(); // Auto-recovery
                } else {
                    broadcastDirectLog("I", "SketchLogger", "Logger stopped gracefully.");
                }
            }
        });
        
        broadcastDirectLog("I", "SketchLogger", "Logging engine started successfully.");
    }

    public static synchronized void stopLogging() {
        if (!isRunning) {
            broadcastDirectLog("W", "SketchLogger", "Logger is not running.");
            return;
        }

        isRunning = false;
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    public static void broadcastLog(String log) {
        Context context = <?class_name?>.getContext();
        if (context == null) return;

        Intent intent = new Intent();
        intent.setAction("pro.sketchware.ACTION_NEW_DEBUG_LOG");
        intent.putExtra("log", log);
        intent.putExtra("packageName", context.getPackageName());
        context.sendBroadcast(intent);
    }
}
