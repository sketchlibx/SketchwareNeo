package mod.pranav.build;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import a.a.a.yq;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.origin.Origin;
import mod.hey.studios.build.BuildSettings;

/**
 * Executes R8 in an isolated, foreground service process so a large shrink operation does not
 * compete directly with the Sketchware editor's UI heap. This class intentionally uses Java so
 * it is included by Java-only source compilation paths as well as the normal Gradle build.
 */
public final class R8Service extends Service {
    public static final String ACTION_COMPLETE = "com.sketchware.R8_COMPLETE";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_ARGS_PATH = "args_path";
    public static final String EXTRA_SC_ID = "sc_id";

    private static final String TAG = "R8Service";
    private static final String CHANNEL_ID = "sketchware_r8_compiler";
    private static final int NOTIFICATION_ID = 8241;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object runStateLock = new Object();
    private volatile Thread workerThread;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String requestId = intent == null ? null : intent.getStringExtra(EXTRA_REQUEST_ID);
        final String argsPath = intent == null ? null : intent.getStringExtra(EXTRA_ARGS_PATH);
        final String scId = intent == null ? null : intent.getStringExtra(EXTRA_SC_ID);

        if (!startForegroundSafely()) {
            publishResult(requestId, false, "Android refused to start the R8 foreground service notification. Check notification-channel/device policy settings.");
            if (!running.get()) stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (requestId == null || requestId.trim().isEmpty() || argsPath == null || argsPath.trim().isEmpty()
                || scId == null || scId.trim().isEmpty()) {
            publishResult(requestId, false,
                    "R8 could not start: project ID, request ID, or arguments-file path is missing.");
            if (!running.get()) {
                stopForeground(true);
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }

        synchronized (runStateLock) {
            if (!running.compareAndSet(false, true)) {
                publishResult(requestId, false, "R8 is already running for another build. Wait for it to finish.");
                return START_NOT_STICKY;
            }
        }

        workerThread = new Thread(() -> {
            boolean success = false;
            String error = null;
            try {
                compileR8(new File(argsPath), scId);
                success = true;
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "R8 ran out of memory in the isolated compiler process", oom);
                error = "R8 ran out of memory in the compiler process. R8 did not finish; the build has not been marked successful. "
                        + "Try disabling full-mode rules or reducing the libraries included in shrinking.";
            } catch (Throwable failure) {
                Log.e(TAG, "R8 compilation failed", failure);
                error = summarizeFailure(failure);
            } finally {
                try {
                    stopForeground(true);
                } catch (RuntimeException foregroundFailure) {
                    Log.w(TAG, "Could not remove R8 foreground notification", foregroundFailure);
                }
                synchronized (runStateLock) {
                    running.set(false);
                    try {
                        publishResult(requestId, success, error);
                    } catch (RuntimeException broadcastFailure) {
                        Log.e(TAG, "Could not publish R8 completion result", broadcastFailure);
                    }
                    stopSelf(startId);
                }
            }
        }, "Sketchware-R8-Compiler");
        workerThread.start();
        return START_NOT_STICKY;
    }

    private void compileR8(File argsFile, String scId) throws Exception {
        if (!argsFile.isFile() || argsFile.length() <= 0L) {
            throw new IOException("R8 arguments file is missing or empty: " + argsFile.getAbsolutePath());
        }

        JSONObject args;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(argsFile), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            StringBuilder json = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) != -1) json.append(buffer, 0, count);
            args = new JSONObject(json.toString());
        } catch (JSONException parseError) {
            throw new IOException("R8 arguments file is not valid JSON. Rebuild to regenerate it.", parseError);
        }

        List<String> rules = readStringArray(args, "rules");
        List<String> configs = readStringArray(args, "config_files");
        List<String> libraryInputs = readStringArray(args, "library_inputs");
        List<String> programInputs = readStringArray(args, "program_inputs");
        List<String> expectedClasses = args.has("expected_classes")
                ? readStringArray(args, "expected_classes") : Collections.emptyList();
        int minApi = args.optInt("min_api", 21);
        if (minApi < 1 || minApi > 100) throw new IOException("Invalid R8 min API value: " + minApi);
        if (programInputs.isEmpty()) throw new IOException("R8 has no program inputs; refusing to emit an empty dex output.");
        if (libraryInputs.isEmpty()) throw new IOException("R8 has no Android/library classpath inputs.");

        requireReadableFiles(programInputs, "program input");
        requireReadableFiles(libraryInputs, "library input");
        requireReadableFiles(configs, "ProGuard/R8 config");

        yq project = new yq(getApplicationContext(), scId);
        File binDir = new File(project.binDirectoryPath);
        if (!binDir.exists() && !binDir.mkdirs() && !binDir.isDirectory()) {
            throw new IOException("Could not create build output directory: " + binDir.getAbsolutePath());
        }
        File outputDir = new File(binDir, "dex");
        if (!outputDir.exists() && !outputDir.mkdirs() && !outputDir.isDirectory()) {
            throw new IOException("Could not create R8 output directory: " + outputDir.getAbsolutePath());
        }
        File[] previousDex = outputDir.listFiles((dir, name) -> name.matches("classes([0-9]+)?\\.dex"));
        if (previousDex != null) {
            for (File file : previousDex) {
                if (file.exists() && !file.delete()) {
                    throw new IOException("Could not remove stale dex output before R8: " + file.getAbsolutePath());
                }
            }
        }

        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicLong infoDiagnosticCount = new AtomicLong();
        DiagnosticsHandler diagnostics = new DiagnosticsHandler() {
            @Override
            public void error(Diagnostic diagnostic) {
                errors.add("ERROR: " + diagnostic.getDiagnosticMessage());
            }

            @Override
            public void warning(Diagnostic diagnostic) {
                Log.w(TAG, "R8 warning: " + diagnostic.getDiagnosticMessage());
            }

            @Override
            public void info(Diagnostic diagnostic) {
            
                infoDiagnosticCount.incrementAndGet();
            }
        };

        long programBytes = totalBytes(programInputs);
        long libraryBytes = totalBytes(libraryInputs);
        int threadCount = computeThreadCount(programBytes, scId);
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        long startedAt = System.currentTimeMillis();
        Log.i(TAG, "Starting R8: programs=" + programInputs.size()
                + ", libraries=" + libraryInputs.size()
                + ", configs=" + configs.size()
                + ", programMB=" + (programBytes / (1024L * 1024L))
                + ", libraryMB=" + (libraryBytes / (1024L * 1024L))
                + ", expectedAppRoots=" + expectedClasses.size()
                + ", maxHeapMB=" + maxHeapMb
                + ", threads=" + threadCount
                + ", minApi=" + minApi);
        for (String programInput : programInputs) {
            Log.d(TAG, "R8 program input: " + programInput + " (" + (new File(programInput).length() / 1024L) + " KB)");
        }

        R8Command.Builder builder = R8Command.builder(diagnostics)
                .addProgramFiles(toPaths(programInputs))
                .addProguardConfiguration(rules, Origin.unknown())
                .addProguardConfigurationFiles(toPaths(configs))
                .setProguardMapOutputPath(Paths.get(project.proguardMappingPath))
                .setMinApiLevel(minApi)
                .addLibraryFiles(toPaths(libraryInputs))
                .setOutput(outputDir.toPath(), OutputMode.DexIndexed)
                .setMode(CompilationMode.RELEASE);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "Sketchware-R8-Worker");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        try {
            R8.run(builder.build(), executor);
        } catch (Throwable failure) {

            if (failure instanceof OutOfMemoryError) throw (OutOfMemoryError) failure;
            if (!errors.isEmpty()) {
                throw new IOException("R8 reported diagnostics:\n" + joinErrors(errors), failure);
            }
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof Exception) throw (Exception) failure;
            throw new RuntimeException(failure);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (!errors.isEmpty()) throw new IOException("R8 reported diagnostics:\n" + joinErrors(errors));
        File[] dexOutputs = outputDir.listFiles((dir, name) -> name.matches("classes([0-9]+)?\\.dex"));
        if (dexOutputs == null || dexOutputs.length == 0) {
            throw new IOException("R8 returned without an error but did not generate any classes*.dex files.");
        }
        for (File dex : dexOutputs) {
            if (!dex.isFile() || dex.length() == 0L) throw new IOException("R8 generated an empty dex file: " + dex.getAbsolutePath());
        }
        verifyExpectedClassesInDex(dexOutputs, expectedClasses);
        long outputBytes = totalFileBytes(dexOutputs);
        Log.i(TAG, "R8 succeeded in " + (System.currentTimeMillis() - startedAt) + " ms; dexFiles="
                + dexOutputs.length + ", dexOutputMB=" + (outputBytes / (1024L * 1024L))
                + ", expectedAppRootsVerified=" + expectedClasses.size()
                + ", infoDiagnostics=" + infoDiagnosticCount.get());
    }

    private void verifyExpectedClassesInDex(File[] dexFiles, List<String> expectedClasses) throws IOException {
        if (expectedClasses == null || expectedClasses.isEmpty()) return;
        Set<String> missing = new LinkedHashSet<>();
        for (String className : expectedClasses) {
            if (className != null && !className.trim().isEmpty()) {
                missing.add("L" + className.trim().replace('.', '/') + ";");
            }
        }
        if (missing.isEmpty()) return;

        for (File dexFile : dexFiles) {
            removeDefinedClassesFromDex(dexFile, missing);
            if (missing.isEmpty()) return;
        }

        StringBuilder readable = new StringBuilder();
        for (String descriptor : missing) {
            if (readable.length() > 0) readable.append(", ");
            readable.append(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
            if (readable.length() > 1400) {
                readable.append(" … (more missing classes omitted)");
                break;
            }
        }
        throw new IOException("R8 emitted DEX files but dropped required app component/custom-view class(es): "
                + readable + ". Manifest/layout keep rules were not applied correctly; refusing to report a successful build.");
    }

    private void removeDefinedClassesFromDex(File dexFile, Set<String> unresolved) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(dexFile, "r")) {
            long fileLength = input.length();
            if (fileLength < 112L) throw new IOException("R8 output is too small to be a valid DEX: " + dexFile.getName());
            byte[] magic = new byte[8];
            input.seek(0L);
            input.readFully(magic);
            if (magic[0] != 'd' || magic[1] != 'e' || magic[2] != 'x' || magic[3] != '\n' || magic[7] != 0) {
                throw new IOException("R8 output has an invalid DEX header: " + dexFile.getAbsolutePath());
            }

            long stringIdsSize = readUInt32LE(input, 56L, fileLength);
            long stringIdsOffset = readUInt32LE(input, 60L, fileLength);
            long typeIdsSize = readUInt32LE(input, 64L, fileLength);
            long typeIdsOffset = readUInt32LE(input, 68L, fileLength);
            long classDefsSize = readUInt32LE(input, 96L, fileLength);
            long classDefsOffset = readUInt32LE(input, 100L, fileLength);

            requireDexTable(fileLength, stringIdsOffset, stringIdsSize, 4L, "string_ids", dexFile);
            requireDexTable(fileLength, typeIdsOffset, typeIdsSize, 4L, "type_ids", dexFile);
            requireDexTable(fileLength, classDefsOffset, classDefsSize, 32L, "class_defs", dexFile);

            for (long i = 0; i < classDefsSize && !unresolved.isEmpty(); i++) {
                long classDefOffset = classDefsOffset + i * 32L;
                long classIndex = readUInt32LE(input, classDefOffset, fileLength);
                if (classIndex >= typeIdsSize) {
                    throw new IOException("Invalid class index in R8 DEX " + dexFile.getName());
                }
                long stringIndex = readUInt32LE(input, typeIdsOffset + classIndex * 4L, fileLength);
                if (stringIndex >= stringIdsSize) {
                    throw new IOException("Invalid descriptor string index in R8 DEX " + dexFile.getName());
                }
                long stringDataOffset = readUInt32LE(input, stringIdsOffset + stringIndex * 4L, fileLength);
                String descriptor = readDexString(input, stringDataOffset, fileLength);
                unresolved.remove(descriptor);
            }
        }
    }

    private long readUInt32LE(RandomAccessFile input, long offset, long fileLength) throws IOException {
        if (offset < 0L || offset > fileLength - 4L) throw new IOException("DEX table offset is outside file bounds.");
        input.seek(offset);
        long b0 = input.readUnsignedByte();
        long b1 = input.readUnsignedByte();
        long b2 = input.readUnsignedByte();
        long b3 = input.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private void requireDexTable(long fileLength, long offset, long count, long itemSize,
                                 String tableName, File dexFile) throws IOException {
        if (count == 0L) return;
        if (offset < 112L || offset > fileLength || count < 0L
                || count > (fileLength - offset) / itemSize) {
            throw new IOException("Invalid " + tableName + " table in R8 DEX " + dexFile.getName());
        }
    }

    private String readDexString(RandomAccessFile input, long offset, long fileLength) throws IOException {
        if (offset < 112L || offset >= fileLength) throw new IOException("DEX string offset is outside file bounds.");
        input.seek(offset);

        boolean ended = false;
        for (int i = 0; i < 5; i++) {
            if (input.getFilePointer() >= fileLength) throw new IOException("Truncated DEX string length.");
            int value = input.readUnsignedByte();
            if ((value & 0x80) == 0) { ended = true; break; }
        }
        if (!ended) throw new IOException("Invalid ULEB128 in DEX string data.");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        while (input.getFilePointer() < fileLength) {
            int value = input.readUnsignedByte();
            if (value == 0) return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (bytes.size() >= 8192) throw new IOException("Oversized DEX class descriptor.");
            bytes.write(value);
        }
        throw new IOException("Unterminated DEX string data.");
    }

    private long totalFileBytes(File[] files) {
        long total = 0L;
        if (files == null) return total;
        for (File file : files) {
            long size = file.length();
            total = Long.MAX_VALUE - total < size ? Long.MAX_VALUE : total + size;
        }
        return total;
    }

    private int computeThreadCount(long programBytes, String scId) {
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        int cpuCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        int heapCap;
        if (maxHeapMb < 300) heapCap = 1;
        else if (maxHeapMb < 500) heapCap = 2;
        else if (maxHeapMb < 800) heapCap = 3;
        else heapCap = 4;

        long programMb = programBytes / (1024L * 1024L);
        if (programMb > 120) heapCap = Math.min(heapCap, 1);
        else if (programMb > 40) heapCap = Math.min(heapCap, 2);

        int userCap = new BuildSettings(scId).getParallelThreads();
        return Math.max(1, Math.min(Math.min(heapCap, cpuCount), Math.max(1, userCap)));
    }

    private List<String> readStringArray(JSONObject root, String key) throws IOException, JSONException {
        JSONArray array = root.getJSONArray(key);
        List<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (!(value instanceof String)) throw new IOException("Invalid non-string entry in R8 argument '" + key + "'.");
            String pathOrRule = (String) value;
            if (pathOrRule == null) throw new IOException("Null entry in R8 argument '" + key + "'.");
            values.add(pathOrRule);
        }
        return values;
    }

    private void requireReadableFiles(List<String> paths, String label) throws IOException {
        for (String path : paths) {
            File file = new File(path);
            if (!file.isFile() || !file.canRead()) {
                throw new IOException("Missing or unreadable R8 " + label + ": " + path);
            }
        }
    }

    private List<java.nio.file.Path> toPaths(List<String> values) {
        List<java.nio.file.Path> result = new ArrayList<>(values.size());
        for (String value : values) result.add(Paths.get(value));
        return result;
    }

    private long totalBytes(List<String> paths) {
        long total = 0L;
        for (String path : paths) {
            long size = new File(path).length();
            total = Long.MAX_VALUE - total < size ? Long.MAX_VALUE : total + size;
        }
        return total;
    }

    private String joinErrors(List<String> errors) {
        StringBuilder joined = new StringBuilder();
        synchronized (errors) {
            for (String error : errors) {
                if (joined.length() > 0) joined.append('\n');
                joined.append(error);
            }
        }
        return joined.toString();
    }

    private String summarizeFailure(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) result.append("\nCaused by: ");
            result.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) result.append(": ").append(message.trim());
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
            depth++;
        }
        return result.toString();
    }

    private void publishResult(String requestId, boolean success, String error) {
        Intent result = new Intent(ACTION_COMPLETE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_REQUEST_ID, requestId == null ? "" : requestId)
                .putExtra(EXTRA_SUCCESS, success);
        if (error != null && !error.trim().isEmpty()) result.putExtra(EXTRA_ERROR, error);
        sendBroadcast(result);
    }

    private boolean startForegroundSafely() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null && Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Project code shrinking", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Progress of the R8 code shrinking task");
                manager.createNotificationChannel(channel);
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL_ID)
                    : new Notification.Builder(this);
            Notification notification = builder
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Optimizing project with R8")
                    .setContentText("Shrinking and optimizing compiled classes")
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
            return true;
        } catch (RuntimeException notificationFailure) {
            Log.e(TAG, "Could not promote R8 service to foreground", notificationFailure);
            return false;
        }
    }

    @Override
    public void onDestroy() {
        Thread worker = workerThread;
        if (worker != null && worker != Thread.currentThread() && worker.isAlive()) worker.interrupt();
        workerThread = null;
        super.onDestroy();
    }
}
