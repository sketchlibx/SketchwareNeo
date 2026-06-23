package mod.hey.studios.activity.managers.cpp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import pro.sketchware.utility.FileUtil;

public class TermuxNativeCompiler {

    private static final String TAG = "TermuxNativeCompiler";
    private static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String ACTION_TERMUX_RESULT = "pro.sketchware.TERMUX_BUILD_RESULT";

    public static void compileOnDevice(Context context, String scId, String cppSourcePath, String nativeLibsOutputPath, String tempBuildPath) throws Exception {
        
        // Use the temporary 'mysc' directory for all build artifacts, logs, and scripts.
        FileUtil.makeDir(tempBuildPath);

        String buildScriptPath = tempBuildPath + File.separator + "build_native.sh";
        String logFilePath = tempBuildPath + File.separator + "build.log";
        String exitCodePath = tempBuildPath + File.separator + "exit_code.txt";
        
        // Delete old logs and status flags from temp dir
        if (new File(logFilePath).exists()) new File(logFilePath).delete();
        if (new File(exitCodePath).exists()) new File(exitCodePath).delete();

        // The script now operates entirely inside the mysc temp folder
        String scriptContent = 
                "#!/bin/bash\n" +
                "LOG_FILE=\"" + logFilePath + "\"\n" +
                "EXIT_FILE=\"" + exitCodePath + "\"\n" +
                "exec > \"$LOG_FILE\" 2>&1\n" + 
                "echo -1 > \"$EXIT_FILE\"\n" + 
                "cd \"" + tempBuildPath + "\" || { echo 'Error: Could not enter temp build directory'; echo 1 > \"$EXIT_FILE\"; exit 1; }\n" +
                "echo '--- Running CMake Configuration ---'\n" +
                // Pass the absolute source path to CMake instead of '..'
                "cmake \"" + cppSourcePath + "\" -DCMAKE_BUILD_TYPE=Release || { echo 'Error: CMake configuration failed'; echo 1 > \"$EXIT_FILE\"; exit 1; }\n" +
                "echo '--- Running Make Compilation ---'\n" +
                "make -j4 || { echo 'Error: Make compilation failed'; echo 1 > \"$EXIT_FILE\"; exit 1; }\n" +
                "mkdir -p \"" + nativeLibsOutputPath + "/arm64-v8a\"\n" +
                "find . -name \"*.so\" -exec cp {} \"" + nativeLibsOutputPath + "/arm64-v8a/\" \\;\n" +
                "if [ -f /data/data/com.termux/files/usr/lib/libc++_shared.so ]; then\n" +
                "    cp /data/data/com.termux/files/usr/lib/libc++_shared.so \"" + nativeLibsOutputPath + "/arm64-v8a/\"\n" +
                "    echo 'Packaged libc++_shared.so successfully'\n" +
                "else\n" +
                "    echo 'Warning: libc++_shared.so not found in Termux'\n" +
                "fi\n" +
                "echo 'Build Successful!'\n" +
                "echo 0 > \"$EXIT_FILE\"\n" + 
                "exit 0\n";
                
        FileUtil.writeFile(buildScriptPath, scriptContent);

        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorContainer = new String[1];

        BroadcastReceiver resultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                latch.countDown();
            }
        };

        int flags = Build.VERSION.SDK_INT >= 33 ? 2 : 0; 
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(resultReceiver, new IntentFilter(ACTION_TERMUX_RESULT), flags);
        } else {
            context.registerReceiver(resultReceiver, new IntentFilter(ACTION_TERMUX_RESULT));
        }

        Intent pluginIntent = new Intent(ACTION_TERMUX_RESULT);
        pluginIntent.setPackage(context.getPackageName());
        
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 
                (PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT) : 
                PendingIntent.FLAG_UPDATE_CURRENT;
                
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, scId.hashCode(), pluginIntent, piFlags);

        Intent termuxIntent = new Intent();
        termuxIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
        termuxIntent.setAction(ACTION_RUN_COMMAND);
        termuxIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        termuxIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{buildScriptPath});
        // Set Termux working directory directly to the temp build path
        termuxIntent.putExtra("com.termux.RUN_COMMAND_WORKDIR", tempBuildPath);
        termuxIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        termuxIntent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);

        try {
            context.startService(termuxIntent);
            
            boolean completedInTime = latch.await(5, TimeUnit.MINUTES);
            
            if (!completedInTime) {
                errorContainer[0] = "Native compilation timed out after 5 minutes.\n" +
                                    "Please ensure Termux is running and 'allow-external-apps=true' is set in ~/.termux/termux.properties";
            } else {
                String exitCodeStr = FileUtil.readFile(exitCodePath);
                if (exitCodeStr != null) exitCodeStr = exitCodeStr.trim();
                
                if (!"0".equals(exitCodeStr)) {
                    String logContent = FileUtil.readFile(logFilePath);
                    if (logContent == null || logContent.isEmpty()) {
                        logContent = "Unknown error. Script crashed before writing to log.";
                    }
                    errorContainer[0] = "Native C/C++ Compilation Failed (Code " + exitCodeStr + "):\n\n" + logContent;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "IPC transmission to Termux failed", e);
            errorContainer[0] = "Failed to communicate with Termux: " + e.getMessage();
        } finally {
            context.unregisterReceiver(resultReceiver);
        }

        if (errorContainer[0] != null) {
            throw new Exception(errorContainer[0]);
        }
    }
}
