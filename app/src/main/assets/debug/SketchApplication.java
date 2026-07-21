package <?package_name?>;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SketchApplication extends Application {

    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();
        super.onCreate();

        SketchLogger.startLogging();
        SketchLogger.i("SketchApplication", "Application Started Successfully");

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String stackTrace = Log.getStackTraceString(throwable);
            SketchLogger.e("FATAL_CRASH", "Uncaught Exception in thread " + thread.getName() + ":\n" + stackTrace);
            
            Intent intent = new Intent(getApplicationContext(), DebugActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("error", stackTrace);
            startActivity(intent);
            
            Process.killProcess(Process.myPid());
            System.exit(1);
        });

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                SketchLogger.d("Lifecycle", activity.getClass().getSimpleName() + " Created");
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                SketchLogger.d("Lifecycle", activity.getClass().getSimpleName() + " Started");
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                SketchLogger.d("Lifecycle", activity.getClass().getSimpleName() + " Resumed");
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                SketchLogger.d("Lifecycle", activity.getClass().getSimpleName() + " Paused");
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                SketchLogger.d("Lifecycle", activity.getClass().getSimpleName() + " Stopped");
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                SketchLogger.d("Lifecycle", activity.getClass().getSimpleName() + " Destroyed");
            }
        });
    }
}
