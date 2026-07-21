package dev.aldi.sayuti.editor.manage;

import static android.net.ConnectivityManager.NetworkCallback;
import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.createLibraryMap;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.R;
import pro.sketchware.databinding.LibraryDownloaderDialogBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class LibraryDownloaderDialogFragment extends BottomSheetDialogFragment {
    private LibraryDownloaderDialogBinding binding;

    private DependencyDownloadAdapter dependencyAdapter;
    private final List<DependencyDownloadItem> downloadItems = new ArrayList<>();
    private ExecutorService downloadExecutor;

    private MavenSearchResultAdapter mavenSearchAdapter;

    private final Gson gson = new Gson();
    private BuildSettings buildSettings;

    private boolean notAssociatedWithProject;
    private String dependencyName;
    private String localLibFile;
    private String prefillDependencyUrl = null;
    private String oldLibraryFolder = null;
    private boolean isUpgradeMode = false;
    private OnLibraryDownloadedTask onLibraryDownloadedTask;

    private ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LibraryDownloaderDialogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (downloadExecutor != null && !downloadExecutor.isShutdown()) {
            downloadExecutor.shutdownNow();
        }
        unregisterNetworkCallback();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() == null) return;

        dependencyAdapter = new DependencyDownloadAdapter();
        binding.dependenciesRecyclerView.setAdapter(dependencyAdapter);
        binding.dependenciesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // FIXED: Added an empty HashSet to resolve the missing Set<String> parameter
        mavenSearchAdapter = new MavenSearchResultAdapter(new java.util.HashSet<>(), this::onMavenSearchResultClicked);
        binding.searchResultsRecyclerView.setAdapter(mavenSearchAdapter);
        binding.searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        binding.btnMavenSearch.setOnClickListener(v -> performMavenSearch(Helper.getText(binding.searchInput)));
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performMavenSearch(Helper.getText(binding.searchInput));
                return true;
            }
            return false;
        });

        downloadExecutor = Executors.newSingleThreadExecutor();

        notAssociatedWithProject = getArguments().getBoolean("notAssociatedWithProject", false);
        buildSettings = (BuildSettings) getArguments().getSerializable("buildSettings");
        localLibFile = getArguments().getString("localLibFile");
        
        prefillDependencyUrl = getArguments().getString("prefillDependency");
        isUpgradeMode = getArguments().getBoolean("isUpgradeMode", false);
        oldLibraryFolder = getArguments().getString("oldLibraryFolder");

        if (prefillDependencyUrl != null && !prefillDependencyUrl.isEmpty()) {
            String[] dp = prefillDependencyUrl.split(":");
            if (dp.length == 3 && isUpgradeMode) {
                binding.dependencyInput.setText(prefillDependencyUrl);
                binding.dependencyInfo.setText("Fetching latest version from Maven Central...");
                fetchLatestVersion(dp[0], dp[1], dp[2]);
            } else {
                binding.dependencyInput.setText(prefillDependencyUrl);
            }
        } else {
            binding.dependencyInputLayout.setHint("Enter Dependency or URL");
        }

        binding.btnDownload.setOnClickListener(v -> initDownloadFlow());

        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
    }

    private void fetchLatestVersion(String group, String artifact, String currentVersion) {
        resolveLatestVersion(group, artifact, new LatestVersionCallback() {
            @Override
            public void onResolved(@NonNull String latestVersion) {
                if (binding == null) return;
                binding.dependencyInput.setText(group + ":" + artifact + ":" + latestVersion);
                if (currentVersion.equals(latestVersion)) {
                    binding.dependencyInfo.setText("You are already on the latest version (" + latestVersion + ").");
                } else {
                    binding.dependencyInfo.setText("Latest version found: " + latestVersion + ". Click Download to upgrade.");
                }
            }

            @Override
            public void onFailure(@NonNull Exception e) {
                if (binding == null) return;
                binding.dependencyInfo.setText("Could not fetch latest version automatically. Please enter version manually.");
            }
        });
    }

    private interface LatestVersionCallback {
        void onResolved(@NonNull String latestVersion);
        void onFailure(@NonNull Exception e);
    }

    private void resolveLatestVersion(String group, String artifact, LatestVersionCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.net.URL url = new java.net.URL("https://search.maven.org/solrsearch/select?q=g:%22" + group + "%22+AND+a:%22" + artifact + "%22&rows=1&wt=json");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                java.io.InputStream in = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                org.json.JSONObject json = new org.json.JSONObject(response.toString());
                org.json.JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

                if (docs.length() > 0) {
                    String latestVersion = docs.getJSONObject(0).getString("latestVersion");
                    new Handler(Looper.getMainLooper()).post(() -> callback.onResolved(latestVersion));
                } else {
                    throw new Exception("No versions found for " + group + ":" + artifact);
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            }
        });
    }

    private void registerNetworkCallback() {
        networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (binding != null && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> binding.btnDownload.setEnabled(true));
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (binding != null && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> binding.btnDownload.setEnabled(false));
                }
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
        binding.btnDownload.setEnabled(isNetworkAvailable());
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    public void setOnLibraryDownloadedTask(OnLibraryDownloadedTask onLibraryDownloadedTask) {
        this.onLibraryDownloadedTask = onLibraryDownloadedTask;
    }

    private void initDownloadFlow() {
        dependencyName = Helper.getText(binding.dependencyInput).trim();
        if (dependencyName.isEmpty()) {
            binding.dependencyInputLayout.setError("Please enter a dependency or URL");
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        boolean isDirectUrl = dependencyName.startsWith("http://") || dependencyName.startsWith("https://");

        if (isDirectUrl) {
            showDownloadConfirmationDialog(dependencyName, "direct", "url");
            return;
        }

        var parts = dependencyName.split(":");
        if (parts.length != 3) {
            binding.dependencyInputLayout.setError("Invalid format. Use group:artifact:version OR a full http(s) URL");
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2].trim();

        if (version.equals("+")) {
            resolveDynamicVersionThenShowConfirmation(group, artifact);
            return;
        }

        showDownloadConfirmationDialog(group, artifact, version);
    }

    private void resolveDynamicVersionThenShowConfirmation(String group, String artifact) {
        binding.dependencyInputLayout.setErrorEnabled(false);
        binding.btnDownload.setEnabled(false);
        binding.dependencyInfo.setVisibility(View.VISIBLE);
        binding.dependencyInfo.setText("Resolving latest version for " + group + ":" + artifact + "...");

        resolveLatestVersion(group, artifact, new LatestVersionCallback() {
            @Override
            public void onResolved(@NonNull String latestVersion) {
                if (binding == null) return;
                binding.btnDownload.setEnabled(true);
                binding.dependencyInfo.setText(R.string.local_library_manager_dependency_info);
                dependencyName = group + ":" + artifact + ":" + latestVersion;
                binding.dependencyInput.setText(dependencyName);
                showDownloadConfirmationDialog(group, artifact, latestVersion);
            }

            @Override
            public void onFailure(@NonNull Exception e) {
                if (binding == null) return;
                binding.btnDownload.setEnabled(true);
                binding.dependencyInfo.setText(R.string.local_library_manager_dependency_info);
                binding.dependencyInputLayout.setError("Could not resolve latest version for " + group + ":" + artifact);
                binding.dependencyInputLayout.setErrorEnabled(true);
            }
        });
    }

    private void performMavenSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        String trimmedQuery = query.trim();

        binding.searchProgress.setVisibility(View.VISIBLE);
        binding.searchResultsRecyclerView.setVisibility(View.GONE);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String encodedQuery = java.net.URLEncoder.encode(trimmedQuery, "UTF-8");
                java.net.URL url = new java.net.URL("https://search.maven.org/solrsearch/select?q=" + encodedQuery + "&rows=20&wt=json");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                java.io.InputStream in = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                org.json.JSONObject json = new org.json.JSONObject(response.toString());
                org.json.JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

                List<MavenSearchResult> results = new ArrayList<>();
                for (int i = 0; i < docs.length(); i++) {
                    org.json.JSONObject doc = docs.getJSONObject(i);
                    String g = doc.optString("g");
                    String a = doc.optString("a");
                    String v = doc.optString("latestVersion", doc.optString("v", ""));
                    if (!g.isEmpty() && !a.isEmpty() && !v.isEmpty()) {
                        results.add(new MavenSearchResult(g, a, v));
                    }
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (binding == null) return;
                    binding.searchProgress.setVisibility(View.GONE);
                    mavenSearchAdapter.setResults(results);
                    binding.searchResultsRecyclerView.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
                    if (results.isEmpty()) {
                        SketchwareUtil.toast("No results found for \"" + trimmedQuery + "\"");
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (binding == null) return;
                    binding.searchProgress.setVisibility(View.GONE);
                    SketchwareUtil.toast("Search failed: " + e.getMessage());
                });
            }
        });
    }

    private void onMavenSearchResultClicked(@NonNull MavenSearchResult result) {
        LocalLibrary installed = LocalLibrariesUtil.findInstalledLibraryByGroupArtifact(result.getGroup(), result.getArtifact());

        if (installed == null) {
            startDownloadProcess(result.getGroup(), result.getArtifact(), result.getLatestVersion(), false, null);
            return;
        }

        String installedDependency = installed.getMavenDependency();
        String[] installedParts = installedDependency != null ? installedDependency.split(":") : new String[0];
        String installedVersion = installedParts.length == 3 ? installedParts[2] : "unknown";

        if (installedVersion.equals(result.getLatestVersion())) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(result.getArtifact())
                    .setMessage("Already installed and up to date (" + installedVersion + ").")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(result.getArtifact())
                    .setMessage("Version " + installedVersion + " is installed. Upgrade to latest (" + result.getLatestVersion() + ")?")
                    .setPositiveButton("Upgrade", (dialog, which) ->
                            startDownloadProcess(result.getGroup(), result.getArtifact(), result.getLatestVersion(), true, installed.getName()))
                    .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                    .show();
        }
    }

    private void showDownloadConfirmationDialog(String group, String artifact, String version) {
        boolean isDirectUrl = group.startsWith("http://") || group.startsWith("https://");
        boolean skipSubdependencies = binding.cbSkipSubdependencies.isChecked();

        String message;
        if (isDirectUrl) {
            message = "Are you sure you want to download the library directly from this URL?\n\n" + dependencyName;
        } else {
            message = skipSubdependencies 
                    ? "Are you sure you want to download " + dependencyName 
                    : "Are you sure you want to download " + dependencyName + " and its sub-dependencies?";
        }

        if (isUpgradeMode) {
            message = "Old version of this library (" + oldLibraryFolder + ") will be safely replaced with " + dependencyName + ".\n\n" + message;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isUpgradeMode ? "Confirm Upgrade" : "Confirm Download")
                .setMessage(message)
                .setPositiveButton(isUpgradeMode ? "Upgrade" : "Download", (dialog, which) -> startDownloadProcess(group, artifact, version))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownloadProcess(String group, String artifact, String version) {
        startDownloadProcess(group, artifact, version, isUpgradeMode, oldLibraryFolder);
        isUpgradeMode = false;
        oldLibraryFolder = null;
    }

    private void startDownloadProcess(String group, String artifact, String version,
                                       boolean upgradeModeForThisRequest, @Nullable String oldFolderForThisRequest) {
        binding.dependencyInputLayout.setErrorEnabled(false);

        binding.dependencyInfo.setVisibility(View.GONE);
        binding.overallProgress.setVisibility(View.VISIBLE);
        binding.dependenciesRecyclerView.setVisibility(View.VISIBLE);

        setDownloadState(true);

        boolean isDirectUrlRequest = group.startsWith("http://") || group.startsWith("https://");
        final String requestDependencyString = isDirectUrlRequest ? null : (group + ":" + artifact + ":" + version);
        final boolean finalUpgradeMode = upgradeModeForThisRequest;
        final String finalOldFolder = oldFolderForThisRequest;

        var resolver = new DependencyResolver(group, artifact, version,
                binding.cbSkipSubdependencies.isChecked(), buildSettings);
        var handler = new Handler(Looper.getMainLooper());

        downloadExecutor.execute(() -> {
            try {
                BuiltInLibraries.maybeExtractAndroidJar((message, progress) ->
                        handler.post(() -> binding.overallProgress.setIndeterminate(true)));
                BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

                resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    @Override
                    public void onResolving(@NonNull Artifact artifact, @NonNull Artifact dependency) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dependency);
                            item.setState(DependencyDownloadItem.DownloadState.RESOLVING);
                            dependencyAdapter.updateDependency(item);
                        });
                    }

                    @Override
                    public void onResolutionComplete(@NonNull Artifact dep) {
                        handler.post(() -> updateDependencyState(dep, DependencyDownloadItem.DownloadState.COMPLETED));
                    }

                    @Override
                    public void onArtifactNotFound(@NonNull Artifact dep) {
                        handler.post(() -> {
                            setDownloadState(false);
                            SketchwareUtil.showAnErrorOccurredDialog(getActivity(), "Dependency '" + dep + "' not found");
                        });
                    }

                    @Override
                    public void onSkippingResolution(@NonNull Artifact dep) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                            item.setState(DependencyDownloadItem.DownloadState.COMPLETED);
                            dependencyAdapter.updateDependency(item);
                        });
                    }

                    @Override
                    public void onVersionNotFound(@NonNull Artifact dep) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                            item.setError("Version not available");
                            dependencyAdapter.updateDependency(item);
                        });
                    }

                    @Override
                    public void onDependenciesNotFound(@NonNull Artifact dep) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                            item.setError("Dependencies not found");
                            dependencyAdapter.updateDependency(item);
                        });
                    }

                    @Override
                    public void onInvalidScope(@NonNull Artifact dep, @NonNull String scope) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                            item.setError("Invalid scope: " + scope);
                            dependencyAdapter.updateDependency(item);
                        });
                    }

                    @Override
                    public void invalidPackaging(@NonNull Artifact dep) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                            item.setError("Invalid packaging");
                            dependencyAdapter.updateDependency(item);
                        });
                    }

                    @Override
                    public void onDownloadStart(@NonNull Artifact dep) {
                        handler.post(() -> {
                            setDownloadState(true);
                            DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                            item.setState(DependencyDownloadItem.DownloadState.DOWNLOADING);
                            dependencyAdapter.updateDependency(item);
                            updateOverallProgress();
                        });
                    }

                    @Override
                    public void onDownloadEnd(@NonNull Artifact dep) {
                        handler.post(() -> {
                            updateDependencyState(dep, DependencyDownloadItem.DownloadState.COMPLETED);
                            updateOverallProgress();
                        });
                    }

                    @Override
                    public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                            item.setError(e.getMessage());
                            dependencyAdapter.updateDependency(item);
                            setDownloadState(false);
                            SketchwareUtil.showAnErrorOccurredDialog(getActivity(),
                                    "Downloading dependency '" + dep + "' failed: " + Log.getStackTraceString(e));
                        });
                    }

                    @Override
                    public void unzipping(@NonNull Artifact artifact) {
                        handler.post(() -> updateDependencyState(artifact, DependencyDownloadItem.DownloadState.UNZIPPING));
                    }

                    @Override
                    public void dexing(@NonNull Artifact dep) {
                        handler.post(() -> updateDependencyState(dep, DependencyDownloadItem.DownloadState.DEXING));
                    }

                    @Override
                    public void dexingFailed(@NonNull Artifact dependency, @NonNull Exception e) {
                        handler.post(() -> {
                            DependencyDownloadItem item = findOrCreateDependencyItem(dependency);
                            item.setError("Dexing failed: " + e.getMessage());
                            dependencyAdapter.updateDependency(item);
                            setDownloadState(false);
                            SketchwareUtil.showAnErrorOccurredDialog(getActivity(),
                                    "Dexing dependency '" + dependency + "' failed: " + Log.getStackTraceString(e));
                        });
                    }

                    @Override
                    public void onTaskCompleted(@NonNull List<String> dependencies) {
                        handler.post(() -> {
                            SketchwareUtil.toast("Library downloaded successfully");

                            if (finalUpgradeMode && finalOldFolder != null) {
                                if (!dependencies.contains(finalOldFolder)) {
                                    File oldLibPath = new File(Environment.getExternalStorageDirectory(), ".sketchware/libs/local_libs/" + finalOldFolder);
                                    if (oldLibPath.exists()) {
                                        FileUtil.deleteFile(oldLibPath.getAbsolutePath());
                                    }
                                }
                            }

                            if (requestDependencyString != null) {
                                for (String dep : dependencies) {
                                    LocalLibrariesUtil.writeArtifactMetadata(dep, requestDependencyString);
                                }
                                LocalLibrariesUtil.clearCache();
                            }

                            if (!notAssociatedWithProject) {
                                var fileContent = FileUtil.readFile(localLibFile);
                                var enabledLibs = gson.fromJson(fileContent, Helper.TYPE_MAP_LIST);

                                if (finalUpgradeMode && finalOldFolder != null) {
                                    for (int i = 0; i < enabledLibs.size(); i++) {
                                        if (enabledLibs.get(i).get("name").toString().equals(finalOldFolder)) {
                                            enabledLibs.remove(i);
                                            break;
                                        }
                                    }
                                }

                                for (String dep : dependencies) {
                                    boolean exists = false;
                                    for (Map<String, Object> libMap : enabledLibs) {
                                        if (libMap.get("name").toString().equals(dep)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        enabledLibs.add(createLibraryMap(dep, requestDependencyString != null ? requestDependencyString : group));
                                    }
                                }
                                FileUtil.writeFile(localLibFile, gson.toJson(enabledLibs));
                            }

                            if (getActivity() == null) return;

                            setDownloadState(false);
                            binding.dependencyInput.setText("");
                            if (onLibraryDownloadedTask != null) onLibraryDownloadedTask.invoke();
                        });
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    setDownloadState(false);
                    SketchwareUtil.showAnErrorOccurredDialog(getActivity(), 
                        "Invalid Dependency or Tag Not Found!\nDetails: " + e.getMessage());
                });
            }
        });
    }

    private DependencyDownloadItem findOrCreateDependencyItem(Artifact artifact) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact)) {
                return item;
            }
        }
        DependencyDownloadItem newItem = new DependencyDownloadItem(artifact);
        downloadItems.add(newItem);
        dependencyAdapter.addDependency(newItem);
        return newItem;
    }

    private void updateDependencyState(Artifact artifact, DependencyDownloadItem.DownloadState state) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact)) {
                item.setState(state);
                dependencyAdapter.updateDependency(item);
                break;
            }
        }
    }

    private void updateOverallProgress() {
        int completed = 0;
        for (DependencyDownloadItem item : downloadItems) {
            if (item.isCompleted()) completed++;
        }

        if (!downloadItems.isEmpty()) {
            binding.overallProgress.setIndeterminate(false);
            binding.overallProgress.setProgress((completed * 100) / downloadItems.size());
        }
    }

    private void setDownloadState(boolean downloading) {
        if (downloading) {
            binding.btnDownload.setVisibility(View.GONE);
        } else {
            binding.btnDownload.setVisibility(View.VISIBLE);
            binding.btnDownload.setEnabled(true);
        }

        binding.dependencyInput.setEnabled(!downloading);
        binding.cbSkipSubdependencies.setEnabled(!downloading);
        setCancelable(!downloading);

        if (!downloading) {
            binding.dependencyInfo.setVisibility(View.VISIBLE);
            binding.dependencyInfo.setText(R.string.local_library_manager_dependency_info);

            if (downloadItems.isEmpty()) {
                binding.overallProgress.setVisibility(View.GONE);
                binding.dependenciesRecyclerView.setVisibility(View.GONE);
            }
        }
    }

    private boolean isNetworkAvailable() {
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public interface OnLibraryDownloadedTask {
        void invoke();
    }
}
