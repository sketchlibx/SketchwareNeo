package pro.sketchware.activities.editor.gradle;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.io.File;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import a.a.a.Jp;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import mod.sketchlibx.importer.GradleDependency;
import pro.sketchware.util.library.BuiltInLibraryManager;
import pro.sketchware.utility.FileUtil;

public final class GradleSyncEngine {

    public enum DepState {READY, CACHED, SYNCING, PENDING, FAILED, SKIPPED}

    public static final class Row {
        public final GradleDependency dep;
        public final DepState state;
        @Nullable
        public final String error;
        public final List<String> folders;
        public final long cacheBytes;

        public Row(GradleDependency dep, DepState state, @Nullable String error, List<String> folders, long cacheBytes) {
            this.dep = dep;
            this.state = state;
            this.error = error;
            this.folders = Collections.unmodifiableList(new ArrayList<>(folders));
            this.cacheBytes = cacheBytes;
        }

        public Row withState(DepState newState, @Nullable String newError) {
            return new Row(dep, newState, newError, folders, cacheBytes);
        }

        public boolean isConsumed() {
            return GradleSyncEngine.isConsumedConfiguration(dep.configuration);
        }
    }

    public interface Listener {
        void onStatus(String message, int done, int total);

        void onDependencyState(String key, DepState state, @Nullable String error);

        void onLog(String line);
    }

    public static final class Result {
        public final boolean success;
        public final boolean cancelled;
        public final boolean offline;
        public final String message;

        Result(boolean success, boolean cancelled, boolean offline, String message) {
            this.success = success;
            this.cancelled = cancelled;
            this.offline = offline;
            this.message = message;
        }
    }

    public static final class Store {
        public String status = "NONE";
        public long time;
        public String message = "";
        public String appHash = "";
        public Map<String, List<String>> folders = new HashMap<>();
    }

    private static final Set<String> CONSUMED_CONFIGURATIONS = Set.of("implementation", "api", "runtimeOnly");

    private GradleSyncEngine() {
    }

    public static boolean isConsumedConfiguration(String configuration) {
        return CONSUMED_CONFIGURATIONS.contains(configuration);
    }

    private static String localLibsDir() {
        return FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/";
    }

    private static File storeFile(String scId) {
        return new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/gradle_sync.json");
    }

    @NonNull
    public static Store loadStore(String scId) {
        File file = storeFile(scId);
        if (!file.isFile()) return new Store();
        try {
            Store store = new Gson().fromJson(FileUtil.readFile(file.getAbsolutePath()), Store.class);
            if (store == null) return new Store();
            if (store.folders == null) store.folders = new HashMap<>();
            if (store.status == null) store.status = "NONE";
            if (store.message == null) store.message = "";
            if (store.appHash == null) store.appHash = "";
            return store;
        } catch (Exception e) {
            return new Store();
        }
    }

    public static void saveStore(String scId, Store store) {
        FileUtil.writeFile(storeFile(scId).getAbsolutePath(), new Gson().toJson(store));
    }

    @NonNull
    public static String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content == null ? 0 : content.hashCode());
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || cm.getActiveNetwork() == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @NonNull
    public static List<Row> inspect(String scId, List<GradleDependency> deps) {
        Store store = loadStore(scId);
        List<LocalLibrary> all = LocalLibrariesUtil.getAllLocalLibraries();
        Set<String> enabledNames = enabledNames(scId);
        Set<String> builtIns = enabledBuiltIns(scId);
        List<Row> rows = new ArrayList<>();
        for (GradleDependency dep : deps) {
            rows.add(inspectOne(dep, store, all, enabledNames, builtIns));
        }
        return rows;
    }

    private static final Set<String> BUILT_IN_GROUP_PREFIXES = Set.of(
            "androidx.", "com.google.android.material", "com.google.code.gson", "com.squareup.okhttp3", "com.squareup.okio",
            "org.jetbrains.kotlin", "org.jetbrains.kotlinx", "com.github.bumptech.glide", "com.google.firebase",
            "com.google.android.gms", "com.airbnb.android", "de.hdodenhof", "com.google.auto.value", "com.google.errorprone", "org.jspecify");

    private static Set<String> enabledBuiltIns(String scId) {
        Set<String> names = new HashSet<>();
        try {
            ArrayList<Jp> libraries = new BuiltInLibraryManager(scId).getLibraries();
            if (libraries != null) {
                for (Jp library : libraries) {
                    if (library != null && library.getName() != null) names.add(library.getName());
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    @Nullable
    private static String builtInProvider(GradleDependency dep, Set<String> builtIns) {
        boolean knownGroup = false;
        for (String prefix : BUILT_IN_GROUP_PREFIXES) {
            if (dep.group.startsWith(prefix)) {
                knownGroup = true;
                break;
            }
        }
        if (!knownGroup) return null;
        String prefix = dep.artifact + "-";
        for (String name : builtIns) {
            if (name.startsWith(prefix) && name.length() > prefix.length() && Character.isDigit(name.charAt(prefix.length()))) {
                return name;
            }
        }
        return null;
    }

    private static Set<String> enabledNames(String scId) {
        Set<String> names = new HashSet<>();
        try {
            for (HashMap<String, Object> map : LocalLibrariesUtil.getLocalLibraries(scId)) {
                Object name = map.get("name");
                if (name != null) names.add(name.toString());
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private static String versionProblem(GradleDependency dep) {
        String v = dep.version;
        if (v == null || v.isEmpty() || v.equals("+") || v.contains("$") || v.contains("[") || v.contains("(")) {
            return "A fixed version is required (group:artifact:1.2.3)";
        }
        return null;
    }

    private static Row inspectOne(GradleDependency dep, Store store, List<LocalLibrary> all, Set<String> enabledNames, Set<String> builtIns) {
        String provider = isConsumedConfiguration(dep.configuration) ? builtInProvider(dep, builtIns) : null;
        if (provider != null) {
            return new Row(dep, DepState.READY, "Provided by Neo built-in library " + provider, Collections.emptyList(), 0);
        }
        String problem = versionProblem(dep);
        if (problem != null) return new Row(dep, DepState.FAILED, problem, Collections.emptyList(), 0);
        String coordinate = dep.coordinate();
        Set<String> folders = new LinkedHashSet<>();
        List<String> recorded = store.folders.get(coordinate);
        if (recorded != null) folders.addAll(recorded);
        for (LocalLibrary library : all) {
            if (coordinate.equals(library.getMavenDependency())) folders.add(library.getName());
        }
        if (folders.isEmpty()) {
            return new Row(dep, isConsumedConfiguration(dep.configuration) ? DepState.PENDING : DepState.SKIPPED,
                    isConsumedConfiguration(dep.configuration) ? null : "'" + dep.configuration + "' is not used by the in-app build",
                    Collections.emptyList(), 0);
        }
        long bytes = 0;
        boolean allEnabled = true;
        for (String name : folders) {
            File dir = new File(localLibsDir(), name);
            HashMap<String, Object> map = LocalLibrariesUtil.createLibraryMap(name, coordinate);
            if (!dir.isDirectory() || !map.containsKey("jarPath") || !map.containsKey("dexPath")) {
                return new Row(dep, DepState.FAILED, "Cached artifact '" + name + "' is incomplete (needs classes.jar and classes.dex). Sync again to repair it.",
                        new ArrayList<>(folders), bytes);
            }
            bytes += directorySize(dir);
            if (!enabledNames.contains(name)) allEnabled = false;
        }
        if (!isConsumedConfiguration(dep.configuration)) {
            return new Row(dep, DepState.SKIPPED, "'" + dep.configuration + "' is not used by the in-app build", new ArrayList<>(folders), bytes);
        }
        return new Row(dep, allEnabled ? DepState.READY : DepState.CACHED, null, new ArrayList<>(folders), bytes);
    }

    private static long directorySize(File dir) {
        long total = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            total += child.isDirectory() ? directorySize(child) : child.length();
        }
        return total;
    }

    @NonNull
    public static Result sync(Context context, String scId, List<GradleDependency> deps, @Nullable Set<String> onlyKeys,
                              String appGradleContent, Listener listener, AtomicBoolean cancel) {
        BuildSettings buildSettings = new BuildSettings(scId);
        boolean useCache = buildSettings.isOfflineCacheEnabled();
        List<GradleDependency> targets = new ArrayList<>();
        for (GradleDependency dep : deps) {
            if (onlyKeys == null || onlyKeys.contains(dep.key())) targets.add(dep);
        }
        int total = targets.size();
        int done = 0;
        int failures = 0;
        boolean offlineFailure = false;
        listener.onLog("Sync started: " + total + " dependenc" + (total == 1 ? "y" : "ies") + ", offline cache " + (useCache ? "on" : "off"));

        for (GradleDependency dep : targets) {
            if (cancel.get()) {
                listener.onLog("Sync cancelled");
                return new Result(false, true, false, "Sync cancelled");
            }
            String key = dep.key();
            listener.onStatus("Resolving " + dep.coordinate(), done, total);

            if (!isConsumedConfiguration(dep.configuration)) {
                listener.onLog("Skipping " + dep + ": '" + dep.configuration + "' is not used by the in-app build");
                listener.onDependencyState(key, DepState.SKIPPED, "'" + dep.configuration + "' is not used by the in-app build");
                done++;
                continue;
            }
            String provider = builtInProvider(dep, enabledBuiltIns(scId));
            if (provider != null) {
                listener.onLog("Provided by Neo built-in library " + provider + ": " + dep.coordinate() + " (no download)");
                listener.onDependencyState(key, DepState.READY, "Provided by Neo built-in library " + provider);
                done++;
                continue;
            }
            String problem = versionProblem(dep);
            if (problem != null) {
                listener.onLog("error: " + dep + ": " + problem);
                listener.onDependencyState(key, DepState.FAILED, problem);
                failures++;
                done++;
                continue;
            }

            if (useCache) {
                Row cached = inspectOne(dep, loadStore(scId), LocalLibrariesUtil.getAllLocalLibraries(), enabledNames(scId), enabledBuiltIns(scId));
                if (cached.state == DepState.READY || (cached.state == DepState.CACHED && cached.error == null)) {
                    enableFolders(scId, cached.folders, dep.coordinate());
                    listener.onLog("Cache hit: " + dep.coordinate() + " (" + cached.folders.size() + " artifact(s), no download)");
                    listener.onDependencyState(key, DepState.READY, null);
                    done++;
                    continue;
                }
                if (cached.state == DepState.FAILED && cached.error != null && !cached.folders.isEmpty()) {
                    listener.onLog("Cache invalid for " + dep.coordinate() + ": " + cached.error);
                }
            }

            if (!isNetworkAvailable(context)) {
                String message = "No network connection and " + dep.coordinate() + " is not available in the local cache";
                listener.onLog("error: " + message);
                listener.onDependencyState(key, DepState.FAILED, message);
                offlineFailure = true;
                failures++;
                done++;
                continue;
            }

            listener.onDependencyState(key, DepState.SYNCING, null);
            String error = download(scId, dep, buildSettings, listener);
            if (error != null) {
                listener.onDependencyState(key, DepState.FAILED, error);
                failures++;
            } else {
                Row verified = inspectOne(dep, loadStore(scId), LocalLibrariesUtil.getAllLocalLibraries(), enabledNames(scId), enabledBuiltIns(scId));
                if (verified.state == DepState.READY) {
                    listener.onDependencyState(key, DepState.READY, null);
                } else {
                    String message = verified.error != null ? verified.error : "Downloaded artifact could not be verified";
                    listener.onLog("error: " + dep + ": " + message);
                    listener.onDependencyState(key, DepState.FAILED, message);
                    failures++;
                }
            }
            done++;
        }

        listener.onStatus("Verifying", done, total);
        List<Row> finalRows = inspect(scId, deps);
        int notReady = 0;
        for (Row row : finalRows) {
            if (row.isConsumed() && row.state != DepState.READY) notReady++;
        }
        Store store = loadStore(scId);
        store.time = System.currentTimeMillis();
        store.appHash = hash(appGradleContent);
        boolean success = failures == 0 && notReady == 0;
        String message;
        if (success) {
            message = "All " + countConsumed(finalRows) + " dependencies verified";
            store.status = "SUCCESS";
        } else if (offlineFailure) {
            message = "Offline: " + notReady + " dependenc" + (notReady == 1 ? "y is" : "ies are") + " not cached";
            store.status = "FAILED";
        } else {
            message = failures + " dependenc" + (failures == 1 ? "y" : "ies") + " failed to sync";
            store.status = "FAILED";
        }
        store.message = message;
        saveStore(scId, store);
        listener.onLog(success ? "Sync finished: " + message : "Sync failed: " + message);
        return new Result(success, false, !success && offlineFailure, message);
    }

    private static int countConsumed(List<Row> rows) {
        int count = 0;
        for (Row row : rows) {
            if (row.isConsumed()) count++;
        }
        return count;
    }

    @Nullable
    private static String download(String scId, GradleDependency dep, BuildSettings buildSettings, Listener listener) {
        String coordinate = dep.coordinate();
        List<String> resolved = new ArrayList<>();
        String[] failure = new String[1];
        listener.onLog("Downloading " + coordinate);
        try {
            BuiltInLibraries.maybeExtractAndroidJar((message, progress) -> {
            });
            BuiltInLibraries.maybeExtractCoreLambdaStubsJar();
            new DependencyResolver(dep.group, dep.artifact, dep.version, false, buildSettings)
                    .resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                        @Override
                        public void onResolving(@NonNull Artifact artifact, @NonNull Artifact dependency) {
                            listener.onLog("Resolving " + dependency);
                        }

                        @Override
                        public void onDownloadStart(@NonNull Artifact artifact) {
                            listener.onLog("Downloading " + artifact);
                        }

                        @Override
                        public void onSkippingResolution(@NonNull Artifact artifact) {
                            listener.onLog("Already resolved " + artifact);
                        }

                        @Override
                        public void unzipping(@NonNull Artifact artifact) {
                            listener.onLog("Unzipping " + artifact);
                        }

                        @Override
                        public void dexing(@NonNull Artifact artifact) {
                            listener.onLog("Dexing " + artifact);
                        }

                        @Override
                        public void onArtifactNotFound(@NonNull Artifact artifact) {
                            failure[0] = "Could not find " + artifact + " in any configured repository";
                        }

                        @Override
                        public void onVersionNotFound(@NonNull Artifact artifact) {
                            failure[0] = "Version not found for " + artifact;
                        }

                        @Override
                        public void onInvalidScope(@NonNull Artifact artifact, @NonNull String scope) {
                            failure[0] = "Invalid scope '" + scope + "' for " + artifact;
                        }

                        @Override
                        public void invalidPackaging(@NonNull Artifact artifact) {
                            failure[0] = "Unsupported packaging for " + artifact + " (only jar and aar are supported)";
                        }

                        @Override
                        public void onDownloadError(@NonNull Artifact artifact, @NonNull Throwable error) {
                            failure[0] = "Could not download " + artifact + ": " + describe(error);
                        }

                        @Override
                        public void dexingFailed(@NonNull Artifact artifact, @NonNull Exception error) {
                            failure[0] = "D8 failed to process " + artifact + ": " + describe(error);
                        }

                        @Override
                        public void onTaskCompleted(@NonNull List<String> artifacts) {
                            resolved.addAll(artifacts);
                        }
                    });
        } catch (Exception e) {
            failure[0] = failure[0] != null ? failure[0] : describe(e);
        }
        if (failure[0] != null) {
            listener.onLog("error: " + coordinate + ": " + failure[0]);
            return failure[0];
        }
        if (resolved.isEmpty()) {
            String message = "Resolver finished without producing any artifact for " + coordinate;
            listener.onLog("error: " + message);
            return message;
        }
        for (String folder : resolved) {
            LocalLibrariesUtil.writeArtifactMetadata(folder, coordinate);
        }
        LocalLibrariesUtil.clearCache();
        Store store = loadStore(scId);
        store.folders.put(coordinate, new ArrayList<>(resolved));
        saveStore(scId, store);
        enableFolders(scId, resolved, coordinate);
        listener.onLog("Resolved " + coordinate + " into " + resolved.size() + " artifact(s)");
        return null;
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        if (root instanceof UnknownHostException || root instanceof ConnectException || root instanceof SocketTimeoutException) {
            return "Network failure (" + root.getClass().getSimpleName() + ": " + root.getMessage() + ")";
        }
        String message = t.getMessage();
        return message != null && !message.isEmpty() ? message : t.getClass().getSimpleName();
    }

    public static void enableFolders(String scId, List<String> folders, String coordinate) {
        ArrayList<HashMap<String, Object>> enabled = LocalLibrariesUtil.getLocalLibraries(scId);
        Set<String> names = new HashSet<>();
        for (HashMap<String, Object> map : enabled) {
            Object name = map.get("name");
            if (name != null) names.add(name.toString());
        }
        boolean changed = false;
        for (String folder : folders) {
            if (names.add(folder)) {
                enabled.add(LocalLibrariesUtil.createLibraryMap(folder, coordinate));
                changed = true;
            }
        }
        if (changed) LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(enabled));
    }

    public static void removeFromProject(String scId, GradleDependency removed, List<GradleDependency> remaining) {
        Store store = loadStore(scId);
        List<LocalLibrary> all = LocalLibrariesUtil.getAllLocalLibraries();
        Set<String> toRemove = new LinkedHashSet<>(foldersOf(removed, store, all));
        for (GradleDependency other : remaining) {
            toRemove.removeAll(foldersOf(other, store, all));
        }
        store.folders.remove(removed.coordinate());
        store.status = "NONE";
        saveStore(scId, store);
        if (toRemove.isEmpty()) return;
        ArrayList<HashMap<String, Object>> enabled = LocalLibrariesUtil.getLocalLibraries(scId);
        boolean changed = enabled.removeIf(map -> map.get("name") != null && toRemove.contains(map.get("name").toString()));
        if (changed) LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(enabled));
    }

    private static Set<String> foldersOf(GradleDependency dep, Store store, List<LocalLibrary> all) {
        Set<String> folders = new LinkedHashSet<>();
        String coordinate = dep.coordinate();
        List<String> recorded = store.folders.get(coordinate);
        if (recorded != null) folders.addAll(recorded);
        for (LocalLibrary library : all) {
            if (coordinate.equals(library.getMavenDependency())) folders.add(library.getName());
        }
        return folders;
    }
}
