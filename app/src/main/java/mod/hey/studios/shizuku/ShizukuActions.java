package mod.hey.studios.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.shizuku.IShizukuCommandService;
import pro.sketchware.shizuku.ShizukuCommandService;
import rikka.shizuku.Shizuku;

public final class ShizukuActions {
    public interface Callback {
        void onResult(boolean success, @NonNull String message);
    }

    private interface RemoteOperation {
        String run(IShizukuCommandService service) throws RemoteException;
    }

    private static final int PERMISSION_REQUEST_CODE = 4107;
    private static final long BIND_TIMEOUT_MS = 15_000L;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ShizukuActions() { }

    public static boolean hasPermission() {
        try {
            return !Shizuku.isPreV11()
                    && Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    public static void requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()
                && Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
        }
    }

    public static void installApkAndMaybeLaunch(Context context, File apk, String packageName,
                                                boolean launchAfterInstall, Callback callback) {
        if (apk == null || !apk.isFile() || apk.length() == 0L) {
            postResult(callback, false, "APK file was not found");
            return;
        }
        if (!hasPermission()) {
            postResult(callback, false, "Shizuku is not running or permission is not granted");
            return;
        }
        runWithService(context, service -> {
            String installResult = service.installApk(apk.getAbsolutePath());
            if (installResult == null || !installResult.startsWith("SUCCESS:")) return installResult;
            if (!launchAfterInstall) return installResult;
            String launchResult = service.launchApp(packageName);
            if (launchResult == null || !launchResult.startsWith("SUCCESS:")) {
                return "SUCCESS: Installed. Launch failed: " + launchResult;
            }
            return "SUCCESS: Installed and launched";
        }, callback);
    }

    private static void runWithService(Context context, RemoteOperation operation, Callback callback) {
        Context appContext = context.getApplicationContext();
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(appContext, ShizukuCommandService.class))
                .daemon(false)
                .tag("SketchwareNeoCommands")
                .version(2)
                .processNameSuffix("shizuku_commands");
        AtomicBoolean completed = new AtomicBoolean(false);
        ServiceConnection[] connectionHolder = new ServiceConnection[1];
        Runnable[] timeoutHolder = new Runnable[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                if (completed.get()) return;
                Runnable pendingTimeout = timeoutHolder[0];
                if (pendingTimeout != null) {
                    MAIN_HANDLER.removeCallbacks(pendingTimeout);
                    timeoutHolder[0] = null;
                }
                IShizukuCommandService service = IShizukuCommandService.Stub.asInterface(binder);
                EXECUTOR.execute(() -> {
                    String result;
                    try {
                        result = operation.run(service);
                    } catch (RemoteException | RuntimeException error) {
                        result = "ERROR: " + error.getMessage();
                    }
                    finish(result, completed, callback, args, this, MAIN_HANDLER, timeoutHolder[0]);
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                finish("ERROR: Shizuku service disconnected", completed, callback, args,
                        this, MAIN_HANDLER, timeoutHolder[0]);
            }

            @Override
            public void onBindingDied(ComponentName name) {
                finish("ERROR: Shizuku service binding died", completed, callback, args,
                        this, MAIN_HANDLER, timeoutHolder[0]);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                finish("ERROR: Shizuku returned an empty service", completed, callback, args,
                        this, MAIN_HANDLER, timeoutHolder[0]);
            }
        };
        connectionHolder[0] = connection;
        timeoutHolder[0] = () -> finish("ERROR: Shizuku service connection timed out", completed,
                callback, args, connectionHolder[0], MAIN_HANDLER, timeoutHolder[0]);
        MAIN_HANDLER.postDelayed(timeoutHolder[0], BIND_TIMEOUT_MS);

        try {
            Shizuku.bindUserService(args, connection);
        } catch (Exception | LinkageError error) {
            finish("ERROR: " + error.getMessage(), completed, callback, args,
                    connection, MAIN_HANDLER, timeoutHolder[0]);
        }
    }

    private static void finish(String result, AtomicBoolean completed, Callback callback,
                               Shizuku.UserServiceArgs args, ServiceConnection connection,
                               Handler handler, Runnable timeout) {
        if (!completed.compareAndSet(false, true)) return;
        if (timeout != null) handler.removeCallbacks(timeout);
        try {
            Shizuku.unbindUserService(args, connection, true);
        } catch (Exception | LinkageError ignored) { }
        boolean success = result != null && result.startsWith("SUCCESS:");
        String message = result == null ? "Shizuku returned no result" : result.replaceFirst("^(SUCCESS|ERROR):\\s*", "");
        postResult(callback, success, message);
    }

    private static void postResult(Callback callback, boolean success, String message) {
        if (callback == null) return;
        Handler handler = new Handler(Looper.getMainLooper());
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(success, message);
        else handler.post(() -> callback.onResult(success, message));
    }
}
