package neo.sketchware.plugin;

import android.content.Context;

import java.io.File;

public final class NeoPluginContext {

    private final Context appContext;
    private final String pluginId;

    public NeoPluginContext(Context appContext, String pluginId) {
        this.appContext = appContext.getApplicationContext();
        this.pluginId = pluginId;
    }

    public Context getAppContext() {
        return appContext;
    }

    public String getPluginId() {
        return pluginId;
    }

    public File getPluginDataDir() {
        File dir = new File(appContext.getFilesDir(), "plugins/" + pluginId);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
