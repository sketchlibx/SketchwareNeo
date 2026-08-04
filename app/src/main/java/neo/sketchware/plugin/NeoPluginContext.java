package neo.sketchware.plugin;

import android.content.Context;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NeoPluginContext {

    private final Context appContext;
    private final String pluginId;
    private final Set<String> permissions;
    private final List<NeoEditorAction> editorActions = new CopyOnWriteArrayList<>();

    public NeoPluginContext(Context appContext, String pluginId) {
        this(appContext, pluginId, Collections.emptySet());
    }

    public NeoPluginContext(Context appContext, String pluginId, Set<String> permissions) {
        this.appContext = appContext.getApplicationContext();
        this.pluginId = pluginId;
        this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
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

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public NeoProjectAccess getProjectAccess(String scId) {
        if (!hasPermission("project.read")) {
            throw new SecurityException("Plugin " + pluginId + " does not have the 'project.read' permission");
        }
        return new NeoProjectAccess(scId, permissions.contains("project.write"));
    }

    public void registerEditorAction(NeoEditorAction action) {
        if (!hasPermission("editor.contextMenu")) {
            throw new SecurityException("Plugin " + pluginId + " does not have the 'editor.contextMenu' permission");
        }
        editorActions.add(action);
    }

    public List<NeoEditorAction> getEditorActions() {
        return editorActions;
    }
}
