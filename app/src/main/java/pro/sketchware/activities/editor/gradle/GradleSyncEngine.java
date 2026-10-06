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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import mod.sketchlibx.importer.GradleDependency;
import pro.sketchware.utility.FileUtil;

public final class GradleSyncEngine {

    public enum DepState {READY, CACHED, SYNCING, PENDING, FAILED, SKIPPED}

    public static final class Row {
        public final GradleDependency dep;
        public final DepState state;
        @Nullable
        public final String error;
        @Nullable
        public final String note;
        public final List<String> folders;
        public final long cacheBytes;

        public Row(GradleDependency dep, DepState state, @Nullable String error, @Nullable String note, List<String> folders, long cacheBytes) {
            this.dep = dep;
            this.state = state;
            this.error = error;
            this.note = note;
            this.folders = Collections.unmodifiableList(new ArrayList<>(folders));
            this.cacheBytes = cacheBytes;
        }

        public Row withState(DepState newState, @Nullable String newError) {
            return new Row(dep, newState, newError, newState == DepState.READY ? note : null, folders, cacheBytes);
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
        public Map<String, List<String>> builtIns = new HashMap<>();
        public Map<String, String> artifacts = new HashMap<>();
        public Map<String, String> superseded = new HashMap<>();
        public Map<String, Boolean> skipSub = new HashMap<>();
        public Map<String, Boolean> resolvedSkip = new HashMap<>();
    }

    private static final Object STORE_LOCK = new Object();

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
            if (store.builtIns == null) store.builtIns = new HashMap<>();
            if (store.artifacts == null) store.artifacts = new HashMap<>();
            if (store.superseded == null) store.superseded = new HashMap<>();
            if (store.skipSub == null) store.skipSub = new HashMap<>();
            if (store.resolvedSkip == null) store.resolvedSkip = new HashMap<>();
            if (store.status == null) store.status = "NONE";
            if (store.message == null) store.message = "";
            if (store.appHash == null) store.appHash = "";
            return store;
        } catch (Exception e) {
            return new Store();
        }
    }

    public static void saveStore(String scId, Store store) {
        synchronized (STORE_LOCK) {
            FileUtil.writeFile(storeFile(scId).getAbsolutePath(), new Gson().toJson(store));
        }
    }

    private static void updateStore(String scId, Consumer<Store> mutation) {
        synchronized (STORE_LOCK) {
            Store store = loadStore(scId);
            mutation.accept(store);
            FileUtil.writeFile(storeFile(scId).getAbsolutePath(), new Gson().toJson(store));
        }
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
        List<Row> rows = new ArrayList<>();
        for (GradleDependency dep : deps) {
            rows.add(inspectOne(scId, dep, store, all, enabledNames));
        }
        return rows;
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

    public static boolean isSkipSubDependencies(String scId, String key) {
        Boolean explicit = loadStore(scId).skipSub.get(key);
        return explicit != null ? explicit : new BuildSettings(scId).isSkipSubDependencies();
    }

    public static void setSkipSubDependencies(String scId, String key, boolean skip) {
        updateStore(scId, store -> store.skipSub.put(key, skip));
    }

    public static void setSkipSubDependenciesForAll(String scId, boolean skip) {
        new BuildSettings(scId).setValue(BuildSettings.SETTING_SKIP_SUB_DEPENDENCIES,
                skip ? BuildSettings.SETTING_GENERIC_VALUE_TRUE : BuildSettings.SETTING_GENERIC_VALUE_FALSE);
        updateStore(scId, store -> store.skipSub.clear());
    }

    private static boolean wantsSkip(String scId, Store store, GradleDependency dep) {
        Boolean explicit = store.skipSub.get(dep.key());
        return explicit != null ? explicit : new BuildSettings(scId).isSkipSubDependencies();
    }

    @Nullable
    private static String versionProblem(GradleDependency dep) {
        if (BuiltInArtifacts.normalizeVersion(dep.version) == null) {
            return "A fixed version is required (group:artifact:1.2.3)";
        }
        return null;
    }

    @Nullable
    private static BuiltInArtifacts.Match builtInFor(String scId, GradleDependency dep) {
        if (!isConsumedConfiguration(dep.configuration)) return null;
        return BuiltInArtifacts.findSatisfying(scId, dep.group, dep.artifact, dep.version);
    }

    private static String builtInNote(GradleDependency dep, BuiltInArtifacts.Match match) {
        String requested = BuiltInArtifacts.normalizeVersion(dep.version);
        String note = "Provided by Neo built-in " + match.libraryName;
        if (requested != null && !requested.equals(match.version)) note += " (requested " + requested + ")";
        return note;
    }

    @Nullable
    private static String artifactTag(String folderName) {
        File tag = new File(localLibsDir() + folderName, LocalLibrariesUtil.ARTIFACT_METADATA_FILE_NAME);
        if (!tag.isFile()) return null;
        String value = FileUtil.readFile(tag.getAbsolutePath()).trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean folderComplete(String folderName) {
        File dir = new File(localLibsDir(), folderName);
        File jar = new File(dir, "classes.jar");
        File dex = new File(dir, "classes.dex");
        if (!dir.isDirectory() || !jar.isFile() || jar.length() == 0 || !dex.isFile() || dex.length() == 0) return false;
        File manifest = new File(dir, "AndroidManifest.xml");
        return !manifest.isFile() || new File(dir, "config").isFile();
    }

    private static Row inspectOne(String scId, GradleDependency dep, Store store, List<LocalLibrary> all, Set<String> enabledNames) {
        if (!isConsumedConfiguration(dep.configuration)) {
            return new Row(dep, DepState.SKIPPED, null, "'" + dep.configuration + "' is not used by the in-app build", Collections.emptyList(), 0);
        }
        String problem = versionProblem(dep);
        if (problem != null) return new Row(dep, DepState.FAILED, problem, null, Collections.emptyList(), 0);

        BuiltInArtifacts.Match builtIn = builtInFor(scId, dep);
        if (builtIn != null) {
            return new Row(dep, DepState.READY, null, builtInNote(dep, builtIn), Collections.emptyList(), 0);
        }

        String coordinate = dep.coordinate();
        Set<String> folders = new LinkedHashSet<>();
        List<String> recorded = store.folders.get(coordinate);
        if (recorded != null && !recorded.isEmpty()) {
            folders.addAll(recorded);
        } else {
            for (LocalLibrary library : all) {
                if (coordinate.equals(library.getMavenDependency())) folders.add(library.getName());
            }
        }
        String rootFolder = dep.artifact + "-v" + dep.version;
        String rootOwner = store.artifacts.get(rootFolder);
        if (folders.isEmpty() && rootOwner != null && !rootOwner.startsWith(dep.key() + ":") && new File(localLibsDir(), rootFolder).isDirectory()) {
            return new Row(dep, DepState.PENDING, null, "Cache folder " + rootFolder + " belongs to " + rootOwner + "; sync will refuse to overwrite it",
                    Collections.emptyList(), 0);
        }
        if (folders.isEmpty()) {
            return new Row(dep, DepState.PENDING, null, null, Collections.emptyList(), 0);
        }
        long bytes = 0;
        boolean allEnabled = true;
        String supersededBy = null;
        for (String name : folders) {
            if (store.superseded.containsKey(name)) supersededBy = store.superseded.get(name);
            if (!folderComplete(name)) {
                return new Row(dep, DepState.FAILED, "Cached artifact '" + name + "' is incomplete (needs classes.jar and classes.dex). Sync again to repair it.",
                        null, new ArrayList<>(folders), bytes);
            }
            bytes += directorySize(new File(localLibsDir(), name));
            if (!enabledNames.contains(name) && !store.superseded.containsKey(name)) allEnabled = false;
        }
        Boolean resolvedSkip = store.resolvedSkip.get(coordinate);
        if (allEnabled && wantsSkip(scId, store, dep) != (resolvedSkip != null && resolvedSkip)) {
            return new Row(dep, DepState.PENDING, null, "Sub-dependency option changed; sync to apply", new ArrayList<>(folders), bytes);
        }
        String note = null;
        if (allEnabled) {
            if (supersededBy != null) note = "Superseded by " + supersededBy + " (highest version wins)";
            else if (resolvedSkip != null && resolvedSkip) note = "Sub-dependencies skipped";
        }
        return new Row(dep, allEnabled ? DepState.READY : DepState.CACHED, null, note, new ArrayList<>(folders), bytes);
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
        boolean assetsReady = false;
        listener.onLog("Sync started: " + total + " dependenc" + (total == 1 ? "y" : "ies") + ", offline cache " + (useCache ? "on" : "off"));

        for (GradleDependency dep : targets) {
            if (cancel.get()) {
                listener.onLog("Sync cancelled");
                return new Result(false, true, false, "Sync cancelled");
            }
            String key = dep.key();
            listener.onStatus("Checking " + dep.coordinate(), done, total);

            if (!isConsumedConfiguration(dep.configuration)) {
                listener.onLog("Skipping " + dep + ": '" + dep.configuration + "' is not used by the in-app build");
                listener.onDependencyState(key, DepState.SKIPPED, null);
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

            BuiltInArtifacts.Match builtIn = builtInFor(scId, dep);
            if (builtIn != null) {
                if (!assetsReady) {
                    listener.onStatus("Preparing built-in libraries", done, total);
                    try {
                        BuiltInLibraries.extractCompileAssets();
                        assetsReady = true;
                    } catch (Exception e) {
                        listener.onLog("error: could not extract built-in libraries: " + describe(e));
                    }
                }
                boolean complete = assetsReady;
                if (complete) {
                    for (String name : BuiltInArtifacts.closure(builtIn.libraryName)) {
                        if (!BuiltInArtifacts.isExtracted(name)) {
                            complete = false;
                            listener.onLog("error: built-in library " + name + " is missing its classes.jar or classes.dex");
                        }
                    }
                }
                if (complete) {
                    listener.onLog(builtInNote(dep, builtIn) + " for " + dep.coordinate() + " (not downloaded)");
                    listener.onDependencyState(key, DepState.READY, null);
                } else {
                    String message = "Neo built-in " + builtIn.libraryName + " is not available on this device";
                    listener.onDependencyState(key, DepState.FAILED, message);
                    failures++;
                }
                done++;
                continue;
            }

            if (useCache) {
                Row cached = inspectOne(scId, dep, loadStore(scId), LocalLibrariesUtil.getAllLocalLibraries(), enabledNames(scId));
                if (cached.state == DepState.READY || cached.state == DepState.CACHED) {
                    enableFolders(scId, cached.folders, dep.coordinate());
                    listener.onLog("Cache hit: " + dep.coordinate() + " (" + cached.folders.size() + " artifact(s), not downloaded)");
                    listener.onDependencyState(key, DepState.READY, null);
                    done++;
                    continue;
                }
                if (cached.state == DepState.FAILED && cached.error != null) {
                    listener.onLog("Cache invalid for " + dep.coordinate() + ": " + cached.error);
                }
            }

            String collision = collisionProblem(scId, dep);
            if (collision != null) {
                listener.onLog("error: " + collision);
                listener.onDependencyState(key, DepState.FAILED, collision);
                failures++;
                done++;
                continue;
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

            boolean skipSubNow = wantsSkip(scId, loadStore(scId), dep);
            listener.onLog((skipSubNow ? "Skipping sub-dependencies for " : "Including sub-dependencies for ") + dep.coordinate());
            listener.onDependencyState(key, DepState.SYNCING, null);
            String error = download(scId, dep, deps, buildSettings, listener, done, total);
            if (error != null) {
                listener.onDependencyState(key, DepState.FAILED, error);
                failures++;
            } else {
                Row verified = inspectOne(scId, dep, loadStore(scId), LocalLibrariesUtil.getAllLocalLibraries(), enabledNames(scId));
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
        for (String line : reconcile(scId, deps)) listener.onLog(line);
        String overlap = DuplicateClassGuard.find(scId, deps);
        if (overlap != null) {
            listener.onLog("error: " + overlap);
            failures++;
        }
        List<Row> finalRows = inspect(scId, deps);
        int notReady = 0;
        for (Row row : finalRows) {
            if (row.isConsumed() && row.state != DepState.READY) notReady++;
        }
        boolean success = failures == 0 && notReady == 0;
        String message;
        String status;
        if (success) {
            message = "All " + countConsumed(finalRows) + " dependencies verified";
            status = "SUCCESS";
        } else if (overlap != null) {
            message = overlap.split("\n")[1].trim();
            status = "FAILED";
        } else if (offlineFailure) {
            message = "Offline: " + notReady + " dependenc" + (notReady == 1 ? "y is" : "ies are") + " not cached";
            status = "FAILED";
        } else {
            int count = failures > 0 ? failures : notReady;
            message = count + " dependenc" + (count == 1 ? "y" : "ies") + " failed to sync";
            status = "FAILED";
        }
        String finalMessage = message;
        String appHash = hash(appGradleContent);
        updateStore(scId, store -> {
            store.time = System.currentTimeMillis();
            store.appHash = appHash;
            store.status = status;
            store.message = finalMessage;
        });
        listener.onLog(success ? "Sync finished: " + message : "Sync failed: " + message);
        return new Result(success, false, !success && offlineFailure, message);
    }

    @NonNull
    public static List<String> reconcile(String scId, List<GradleDependency> deps) {
        List<String> log = new ArrayList<>();
        Store store = loadStore(scId);
        Set<String> active = new LinkedHashSet<>();
        for (GradleDependency dep : deps) {
            if (!isConsumedConfiguration(dep.configuration)) continue;
            List<String> folders = store.folders.get(dep.coordinate());
            if (folders != null) active.addAll(folders);
        }
        Map<String, List<String>> byModule = new LinkedHashMap<>();
        for (String folder : active) {
            String coordinate = store.artifacts.get(folder);
            if (coordinate == null) continue;
            int second = coordinate.indexOf(':', coordinate.indexOf(':') + 1);
            if (second < 0) continue;
            byModule.computeIfAbsent(coordinate.substring(0, second), k -> new ArrayList<>()).add(folder);
        }
        Map<String, String> superseded = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> module : byModule.entrySet()) {
            if (module.getValue().size() < 2) continue;
            String winner = null;
            String winnerVersion = null;
            for (String folder : module.getValue()) {
                String coordinate = store.artifacts.get(folder);
                String version = coordinate.substring(coordinate.lastIndexOf(':') + 1);
                if (winner == null || BuiltInArtifacts.compareVersions(version, winnerVersion) > 0) {
                    winner = folder;
                    winnerVersion = version;
                }
            }
            for (String folder : module.getValue()) {
                if (!folder.equals(winner)) {
                    superseded.put(folder, store.artifacts.get(winner));
                    log.add("Version conflict on " + module.getKey() + ": using " + winnerVersion + ", disabled " + store.artifacts.get(folder));
                }
            }
        }
        Set<String> declaredKeys = new HashSet<>();
        for (GradleDependency dep : deps) declaredKeys.add(dep.key());
        updateStore(scId, current -> {
            current.superseded = superseded;
            current.skipSub.keySet().retainAll(declaredKeys);
        });

        ArrayList<HashMap<String, Object>> enabled = LocalLibrariesUtil.getLocalLibraries(scId);
        boolean changed = enabled.removeIf(map -> map.get("name") != null && superseded.containsKey(map.get("name").toString()));
        Set<String> names = new HashSet<>();
        for (HashMap<String, Object> map : enabled) {
            if (map.get("name") != null) names.add(map.get("name").toString());
        }
        for (String folder : active) {
            if (superseded.containsKey(folder) || names.contains(folder) || !folderComplete(folder)) continue;
            if (store.artifacts.containsKey(folder)) {
                enabled.add(LocalLibrariesUtil.createLibraryMap(folder, store.artifacts.get(folder)));
                names.add(folder);
                changed = true;
            }
        }
        if (changed) {
            LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(enabled));
            LocalLibrariesUtil.clearCache();
        }
        return log;
    }

    private static int countConsumed(List<Row> rows) {
        int count = 0;
        for (Row row : rows) {
            if (row.isConsumed()) count++;
        }
        return count;
    }

    @Nullable
    private static String collisionProblem(String scId, GradleDependency dep) {
        String folder = dep.artifact + "-v" + dep.version;
        String owner = loadStore(scId).artifacts.get(folder);
        if (owner != null && !owner.startsWith(dep.key() + ":") && new File(localLibsDir(), folder).isDirectory()) {
            return "Cache folder " + folder + " already belongs to " + owner + "; refusing to overwrite it with " + dep.coordinate()
                    + ". Remove it from Local Libraries first.";
        }
        return null;
    }

    @Nullable
    private static String download(String scId, GradleDependency dep, List<GradleDependency> declared, BuildSettings buildSettings, Listener listener, int done, int total) {
        String coordinate = dep.coordinate();
        boolean skipSub = wantsSkip(scId, loadStore(scId), dep);
        List<String> resolved = new ArrayList<>();
        List<String> providedBuiltIns = new ArrayList<>();
        Map<String, String> folderCoordinates = new LinkedHashMap<>();
        String[] failure = new String[1];
        try {
            BuiltInLibraries.maybeExtractAndroidJar((message, progress) -> {
            });
            BuiltInLibraries.maybeExtractCoreLambdaStubsJar();
            DependencyResolver resolver = new DependencyResolver(dep.group, dep.artifact, dep.version, skipSub, buildSettings);
            resolver.setProvidedBy(artifact -> {
                BuiltInArtifacts.Match match = BuiltInArtifacts.findSatisfying(scId, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                return match == null ? null : match.libraryName;
            });
            resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                @Override
                public void onResolving(@NonNull Artifact artifact, @NonNull Artifact dependency) {
                    listener.onStatus("Resolving " + dependency, done, total);
                    listener.onLog("Resolving " + dependency);
                }

                @Override
                public void onDownloadStart(@NonNull Artifact artifact) {
                    listener.onStatus("Downloading " + artifact, done, total);
                    listener.onLog("Downloading " + artifact);
                }

                @Override
                public void onCacheHit(@NonNull Artifact artifact) {
                    listener.onLog("Using cached " + artifact);
                }

                @Override
                public void onProvided(@NonNull Artifact artifact, @NonNull String provider) {
                    if (!providedBuiltIns.contains(provider)) providedBuiltIns.add(provider);
                    listener.onLog("Provided by Neo built-in " + provider + ": " + artifact + " (not downloaded)");
                }

                @Override
                public void onFolderResolved(@NonNull Artifact artifact, @NonNull String folder) {
                    folderCoordinates.put(folder, artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion());
                }

                @Override
                public void onSkippingResolution(@NonNull Artifact artifact) {
                    listener.onLog("Skipping dependency resolution for " + artifact);
                }

                @Override
                public void unzipping(@NonNull Artifact artifact) {
                    listener.onStatus("Unzipping " + artifact, done, total);
                    listener.onLog("Unzipping " + artifact);
                }

                @Override
                public void dexing(@NonNull Artifact artifact) {
                    listener.onStatus("Dexing " + artifact, done, total);
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
                public void onDependenciesNotFound(@NonNull Artifact artifact) {
                    failure[0] = "No classes.jar was produced for " + artifact;
                }

                @Override
                public void onInvalidPOM(@NonNull Artifact artifact) {
                    failure[0] = "Invalid POM for " + artifact;
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
            if (!folderComplete(folder)) {
                String message = "Resolved artifact '" + folder + "' is incomplete (missing classes.jar or classes.dex)";
                listener.onLog("error: " + message);
                return message;
            }
        }
        for (int i = 0; i < resolved.size(); i++) {
            String folder = resolved.get(i);
            if (i == 0 || artifactTag(folder) == null) {
                LocalLibrariesUtil.writeArtifactMetadata(folder, coordinate);
            }
        }
        LocalLibrariesUtil.clearCache();
        List<String> previous = loadStore(scId).folders.get(coordinate);
        updateStore(scId, store -> {
            store.folders.put(coordinate, new ArrayList<>(resolved));
            store.resolvedSkip.put(coordinate, skipSub);
            store.artifacts.putAll(folderCoordinates);
            if (providedBuiltIns.isEmpty()) store.builtIns.remove(coordinate);
            else store.builtIns.put(coordinate, providedBuiltIns);
        });
        if (previous != null) pruneUnused(scId, previous, resolved, dep, declared);
        enableFolders(scId, resolved, coordinate);
        listener.onLog(skipSub
                ? "Resolved " + coordinate + " (sub-dependencies skipped)"
                : "Resolved " + coordinate + " into " + resolved.size() + " artifact(s)");
        return null;
    }

    private static void pruneUnused(String scId, List<String> previous, List<String> current, GradleDependency owner, List<GradleDependency> declared) {
        Store store = loadStore(scId);
        List<LocalLibrary> all = LocalLibrariesUtil.getAllLocalLibraries();
        Set<String> stale = new LinkedHashSet<>(previous);
        stale.removeAll(current);
        for (GradleDependency other : declared) {
            if (other.key().equals(owner.key())) continue;
            stale.removeAll(foldersOf(other, store, all));
        }
        if (stale.isEmpty()) return;
        ArrayList<HashMap<String, Object>> enabled = LocalLibrariesUtil.getLocalLibraries(scId);
        boolean changed = enabled.removeIf(map -> map.get("name") != null && stale.contains(map.get("name").toString()));
        if (changed) LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(enabled));
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
        Map<String, String> superseded = loadStore(scId).superseded;
        boolean changed = false;
        for (String folder : folders) {
            if (superseded.containsKey(folder)) continue;
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
        updateStore(scId, current -> {
            current.folders.remove(removed.coordinate());
            current.builtIns.remove(removed.coordinate());
            current.resolvedSkip.remove(removed.coordinate());
            current.status = "NONE";
        });
        if (toRemove.isEmpty()) return;
        ArrayList<HashMap<String, Object>> enabled = LocalLibrariesUtil.getLocalLibraries(scId);
        boolean changed = enabled.removeIf(map -> map.get("name") != null && toRemove.contains(map.get("name").toString()));
        if (changed) LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(enabled));
        reconcile(scId, remaining);
    }

    private static Set<String> foldersOf(GradleDependency dep, Store store, List<LocalLibrary> all) {
        Set<String> folders = new LinkedHashSet<>();
        String coordinate = dep.coordinate();
        List<String> recorded = store.folders.get(coordinate);
        if (recorded != null && !recorded.isEmpty()) {
            folders.addAll(recorded);
        } else {
            for (LocalLibrary library : all) {
                if (coordinate.equals(library.getMavenDependency())) folders.add(library.getName());
            }
        }
        return folders;
    }
}
