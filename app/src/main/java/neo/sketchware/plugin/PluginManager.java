package neo.sketchware.plugin;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.Gson;

import dev.aldi.sayuti.block.ExtraBlockFile;

public final class PluginManager {

    private static final String TAG = "PluginManager";
    private static final int CURRENT_API_VERSION = 1;

    private static final Map<String, NeoPluginInterface> loadedPlugins = new ConcurrentHashMap<>();
    private static final Map<String, NeoPluginContext> pluginContexts = new ConcurrentHashMap<>();
    private static final Map<String, File> pluginFiles = new ConcurrentHashMap<>();
    private static final Map<String, String> disabledPluginIds = new ConcurrentHashMap<>();
    private static final List<String> lastLoadErrors = new ArrayList<>();
    private static boolean initialized = false;

    private PluginManager() {}

    public static synchronized void init(Context appContext) {
        if (initialized) return;
        initialized = true;
        loadDisabledState(appContext);
        scan(appContext);
    }

    public static synchronized void rescan(Context appContext) {
        scan(appContext);
    }

    private static void scan(Context appContext) {
        lastLoadErrors.clear();

        File installDir = getInstallDir(appContext);
        if (!installDir.exists()) {
            installDir.mkdirs();
        }

        File[] files = installDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.getName().endsWith(".jar") && !file.getName().endsWith(".apk")) continue;
            if (pluginFiles.containsValue(file)) continue;

            JSONObject manifest = readPluginManifest(file);
            if (manifest == null) {
                lastLoadErrors.add(file.getName() + ": Missing plugin.json");
                continue;
            }

            String pluginId = manifest.optString("pluginId", null);
            if (pluginId != null && disabledPluginIds.containsKey(pluginId)) {
                continue;
            }

            try {
                loadPlugin(appContext, file, manifest);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to load plugin: " + file.getName(), t);
                lastLoadErrors.add(file.getName() + ": " + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }
    }

    public static PluginInstallResult installAndLoad(Context appContext, File sourceJar) {
        File installDir = getInstallDir(appContext);
        if (!installDir.exists()) installDir.mkdirs();

        String fileName = sourceJar.getName().endsWith(".jar") ? sourceJar.getName() : sourceJar.getName() + ".jar";
        File destination = new File(installDir, fileName);

        try (InputStream in = new FileInputStream(sourceJar);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            return new PluginInstallResult(false, null, "Copy failed: " + e.getMessage());
        }

        JSONObject manifest = readPluginManifest(destination);
        if (manifest == null) {
            destination.delete();
            return new PluginInstallResult(false, null, "Missing plugin.json inside the file");
        }

        try {
            String pluginId = loadPlugin(appContext, destination, manifest);
            return new PluginInstallResult(true, pluginId, null);
        } catch (Throwable t) {
            destination.delete();
            String error = t.getClass().getSimpleName() + " - " + t.getMessage();
            lastLoadErrors.add(0, fileName + ": " + error);
            return new PluginInstallResult(false, null, error);
        }
    }

    public static synchronized boolean deletePlugin(Context appContext, String pluginId) {
        unloadOnly(pluginId);

        File file = pluginFiles.remove(pluginId);
        boolean deleted = file == null || !file.exists() || file.delete();

        disabledPluginIds.remove(pluginId);
        persistDisabledState(appContext);
        deleteBlockContributions(pluginId);

        return deleted;
    }

    private static void unloadOnly(String pluginId) {
        NeoPluginInterface plugin = loadedPlugins.remove(pluginId);
        pluginContexts.remove(pluginId);
        if (plugin != null) {
            try {
                plugin.onUnload();
            } catch (Throwable t) {
                Log.e(TAG, "Error unloading plugin " + pluginId, t);
            }
        }
    }

    public static synchronized void setEnabled(Context appContext, String pluginId, boolean enabled) {
        if (enabled) {
            disabledPluginIds.remove(pluginId);
            persistDisabledState(appContext);

            if (!loadedPlugins.containsKey(pluginId)) {
                File file = findInstalledFile(appContext, pluginId);
                if (file != null) {
                    try {
                        JSONObject manifest = readPluginManifest(file);
                        if (manifest != null) loadPlugin(appContext, file, manifest);
                    } catch (Throwable t) {
                        lastLoadErrors.add(0, pluginId + ": " + t.getMessage());
                    }
                }
            }
        } else {
            disabledPluginIds.put(pluginId, "true");
            persistDisabledState(appContext);
            unloadOnly(pluginId);
        }
    }

    public static boolean isEnabled(String pluginId) {
        return !disabledPluginIds.containsKey(pluginId);
    }

    private static File findInstalledFile(Context appContext, String pluginId) {
        File file = pluginFiles.get(pluginId);
        if (file != null) return file;

        File installDir = getInstallDir(appContext);
        File[] files = installDir.listFiles();
        if (files == null) return null;

        for (File candidate : files) {
            JSONObject manifest = readPluginManifest(candidate);
            if (manifest != null && pluginId.equals(manifest.optString("pluginId", null))) {
                return candidate;
            }
        }
        return null;
    }

    public static File getInstallDir(Context appContext) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/plugins");
    }

    private static File getStateFile(Context appContext) {
        return new File(getInstallDir(appContext), ".state.json");
    }

    private static void loadDisabledState(Context appContext) {
        File stateFile = getStateFile(appContext);
        if (!stateFile.exists()) return;

        try {
            String content = readFile(stateFile);
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                disabledPluginIds.put(array.getString(i), "true");
            }
        } catch (Exception ignored) {
        }
    }

    private static void persistDisabledState(Context appContext) {
        try {
            JSONArray array = new JSONArray();
            for (String id : disabledPluginIds.keySet()) array.put(id);

            writeFile(getStateFile(appContext), array.toString());
        } catch (Exception ignored) {
        }
    }

    public static List<String> getLastLoadErrors() {
        return new ArrayList<>(lastLoadErrors);
    }

    private static String loadPlugin(Context appContext, File pluginFile, JSONObject manifest) throws Exception {
        String pluginId = manifest.getString("pluginId");
        String entryClassName = manifest.getString("entryClass");
        int apiVersion = manifest.optInt("apiVersion", 1);

        if (apiVersion > CURRENT_API_VERSION) {
            throw new IllegalStateException("Plugin " + pluginId + " requires a newer host API version");
        }

        if (loadedPlugins.containsKey(pluginId)) {
            throw new IllegalStateException("Plugin " + pluginId + " is already loaded");
        }

        File dexOutputDir = new File(appContext.getApplicationContext().getCacheDir(), "plugin-dex/" + pluginId);
        if (!dexOutputDir.exists()) dexOutputDir.mkdirs();

        DexClassLoader loader = new DexClassLoader(
                pluginFile.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                NeoPluginInterface.class.getClassLoader()
        );

        Class<?> clazz = loader.loadClass(entryClassName);

        if (!NeoPluginInterface.class.isAssignableFrom(clazz)) {
            throw new SecurityException("Entry class does not implement NeoPluginInterface: " + entryClassName);
        }

        NeoPluginInterface plugin = (NeoPluginInterface) clazz.getDeclaredConstructor().newInstance();

        if (!pluginId.equals(plugin.getPluginId())) {
            throw new SecurityException("plugin.json pluginId does not match NeoPluginInterface#getPluginId()");
        }

        java.util.Set<String> permissions = new java.util.HashSet<>();
        org.json.JSONArray permissionsArray = manifest.optJSONArray("permissions");
        if (permissionsArray != null) {
            for (int i = 0; i < permissionsArray.length(); i++) {
                permissions.add(permissionsArray.optString(i));
            }
        }

        NeoPluginContext pluginContext = new NeoPluginContext(appContext, pluginId, permissions);
        plugin.onLoad(pluginContext);

        loadedPlugins.put(pluginId, plugin);
        pluginContexts.put(pluginId, pluginContext);
        pluginFiles.put(pluginId, pluginFile);

        try {
            writeBlockContributions(pluginId, plugin.getBlockContributions());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to write block contributions for " + pluginId, t);
        }

        return pluginId;
    }

    private static JSONObject readPluginManifest(File pluginFile) {
        try (ZipFile zip = new ZipFile(pluginFile)) {
            ZipEntry entry = zip.getEntry("plugin.json");
            if (entry == null) return null;

            try (InputStream is = zip.getInputStream(entry)) {
                byte[] bytes = is.readAllBytes();
                return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeBlockContributions(String pluginId, List<NeoBlockContribution> contributions) throws Exception {
        if (contributions == null || contributions.isEmpty()) return;

        File pluginBlocksDir = new File(ExtraBlockFile.PLUGIN_BLOCKS_DIR, pluginId);
        if (!pluginBlocksDir.exists()) pluginBlocksDir.mkdirs();

        JSONArray paletteArray = new JSONArray();
        List<Object> allBlocks = new ArrayList<>();

        for (NeoBlockContribution contribution : contributions) {
            JSONObject paletteEntry = new JSONObject();
            paletteEntry.put("name", contribution.categoryName());
            paletteEntry.put("color", contribution.categoryColorHex());
            paletteArray.put(paletteEntry);

            if (contribution.blocks() != null) {
                allBlocks.addAll(contribution.blocks());
            }
        }

        writeFile(new File(pluginBlocksDir, "palette.json"), paletteArray.toString());
        writeFile(new File(pluginBlocksDir, "block.json"), new Gson().toJson(allBlocks));
    }

    private static void deleteBlockContributions(String pluginId) {
        File pluginBlocksDir = new File(ExtraBlockFile.PLUGIN_BLOCKS_DIR, pluginId);
        if (!pluginBlocksDir.exists()) return;

        File[] children = pluginBlocksDir.listFiles();
        if (children != null) {
            for (File child : children) child.delete();
        }
        pluginBlocksDir.delete();
    }

    private static void writeFile(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readFile(File file) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static List<NeoDrawerEntry> getDrawerEntries() {
        List<NeoDrawerEntry> entries = new ArrayList<>();
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try {
                List<NeoDrawerEntry> pluginEntries = plugin.getDrawerEntries();
                if (pluginEntries != null) entries.addAll(pluginEntries);
            } catch (Throwable ignored) {
            }
        }
        return entries;
    }

    public static void notifyBuildError(String scId, String errorText) {
        NeoBuildErrorInfo errorInfo = NeoBuildErrorInfo.parse(errorText);
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try {
                plugin.onBuildError(scId, errorText);
            } catch (Throwable ignored) {
            }
            try {
                plugin.onBuildError(scId, errorInfo);
            } catch (Throwable ignored) {
            }
        }
    }

    public static List<NeoEditorAction> getAllEditorActions() {
        List<NeoEditorAction> actions = new ArrayList<>();
        for (NeoPluginContext context : pluginContexts.values()) {
            try {
                actions.addAll(context.getEditorActions());
            } catch (Throwable ignored) {
            }
        }
        return actions;
    }

    public static List<NeoBlockConverter> getAllBlockConverters() {
        List<NeoBlockConverter> converters = new ArrayList<>();
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try {
                NeoBlockConverter converter = plugin.getBlockConverter();
                if (converter != null) converters.add(converter);
            } catch (Throwable ignored) {
            }
        }
        return converters;
    }

    public static void notifyBuildSuccess(String scId) {
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try {
                plugin.onBuildSuccess(scId);
            } catch (Throwable ignored) {
            }
        }
    }

    public static List<PluginInfo> getInstalledPluginInfos(Context appContext) {
        Map<String, PluginInfo> result = new HashMap<>();

        for (Map.Entry<String, NeoPluginInterface> entry : loadedPlugins.entrySet()) {
            File file = pluginFiles.get(entry.getKey());
            result.put(entry.getKey(), new PluginInfo(
                    entry.getKey(),
                    entry.getValue().getPluginApiVersion(),
                    file != null ? file.getName() : "",
                    true
            ));
        }

        File installDir = getInstallDir(appContext);
        File[] files = installDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.getName().endsWith(".jar") && !file.getName().endsWith(".apk")) continue;

                JSONObject manifest = readPluginManifest(file);
                if (manifest == null) continue;

                String pluginId = manifest.optString("pluginId", null);
                if (pluginId == null || result.containsKey(pluginId)) continue;

                result.put(pluginId, new PluginInfo(
                        pluginId,
                        manifest.optInt("apiVersion", 1),
                        file.getName(),
                        false
                ));
            }
        }

        return new ArrayList<>(result.values());
    }

    public static List<String> getLoadedPluginIds() {
        return new ArrayList<>(loadedPlugins.keySet());
    }

    public record PluginInfo(String pluginId, int apiVersion, String fileName, boolean enabled) {}

    public record PluginInstallResult(boolean success, String pluginId, String error) {}
}
