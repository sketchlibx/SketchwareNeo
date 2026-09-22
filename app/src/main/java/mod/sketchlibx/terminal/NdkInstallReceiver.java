package mod.sketchlibx.terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import mod.hey.studios.activity.managers.cpp.InbuiltNdkManager;

/**
 * Receives the broadcast sent by the terminal's real {@code ndk_install} shell function and
 * forwards it to the same {@link InbuiltNdkManager#installNdkAndCmake} the C/C++ Manager's own
 * UI calls — this is the same real installer, not a shell-side simulation of one.
 */
public class NdkInstallReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL = "mod.sketchlibx.terminal.ACTION_INSTALL_NDK";
    public static final String EXTRA_NDK_URL = "ndk_url";
    private static final String TAG = "NdkInstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_INSTALL.equals(intent.getAction())) return;

        String url = intent.getStringExtra(EXTRA_NDK_URL);
        if (url == null || url.isEmpty()) {
            Log.w(TAG, "ndk_install broadcast received with no URL, ignoring");
            return;
        }

        Context appContext = context.getApplicationContext();
        InbuiltNdkManager.installNdkAndCmake(appContext, url, new InbuiltNdkManager.InstallCallback() {
            @Override
            public void onProgress(String message, int progress, boolean isIndeterminate) {
                Log.i(TAG, message);
            }

            @Override
            public void onSuccess() {
                Log.i(TAG, "NDK/CMake install finished successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "NDK/CMake install failed: " + error);
            }
        });
    }
}
