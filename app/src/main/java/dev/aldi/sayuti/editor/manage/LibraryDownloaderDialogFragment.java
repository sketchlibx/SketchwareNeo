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
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (binding != null) {
                            binding.dependencyInput.setText(group + ":" + artifact + ":" + latestVersion);
                            if (currentVersion.equals(latestVersion)) {
                                binding.dependencyInfo.setText("You are already on the latest version (" + latestVersion + ").");
                            } else {
                                binding.dependencyInfo.setText("Latest version found: " + latestVersion + ". Click Download to upgrade.");
                            }
                        }
                    });
                } else {
                    throw new Exception("No docs");
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (binding != null) {
                        binding.dependencyInfo.setText("Could not fetch latest version automatically. Please enter version manually.");
                    }
                });
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
        } else {
            var parts = dependencyName.split(":");
            if (parts.length != 3) {
                binding.dependencyInputLayout.setError("Invalid format. Use group:artifact:version OR a full http(s) URL");
                binding.dependencyInputLayout.setErrorEnabled(true);
                return;
            }
            showDownloadConfirmationDialog(parts[0], parts[1], parts[2]);
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
        binding.dependencyInputLayout.setErrorEnabled(false);

        binding.dependencyInfo.setVisibility(View.GONE);
        binding.overallProgress.setVisibility(View.VISIBLE);
        binding.dependenciesRecyclerView.setVisibility(View.VISIBLE);

        setDownloadState(true);
        
        // DELETION LOGIC REMOVED FROM HERE! 
        // It is now strictly handled inside onTaskCompleted() to ensure we don't break existing library if download fails.

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
                            
                            // SAFE UPGRADE LOGIC - Run only after successful download
                            if (isUpgradeMode && oldLibraryFolder != null) {
                                // 1. Delete physical old folder from disk ONLY if it doesn't match new folder names
                                if (!dependencies.contains(oldLibraryFolder)) {
                                    File oldLibPath = new File(Environment.getExternalStorageDirectory(), ".sketchware/libs/local_libs/" + oldLibraryFolder);
                                    if (oldLibPath.exists()) {
                                        FileUtil.deleteFile(oldLibPath.getAbsolutePath());
                                    }
                                }
                            }
                            
                            if (!notAssociatedWithProject) {
                                var fileContent = FileUtil.readFile(localLibFile);
                                var enabledLibs = gson.fromJson(fileContent, Helper.TYPE_MAP_LIST);
                                
                                // 2. Remove exactly the old library entry from local_library file
                                if (isUpgradeMode && oldLibraryFolder != null) {
                                    for (int i = 0; i < enabledLibs.size(); i++) {
                                        if (enabledLibs.get(i).get("name").toString().equals(oldLibraryFolder)) {
                                            enabledLibs.remove(i);
                                            break;
                                        }
                                    }
                                }

                                // 3. Insert new downloaded libraries without duplicating existing entries
                                for (String dep : dependencies) {
                                    boolean exists = false;
                                    for (Map<String, Object> libMap : enabledLibs) {
                                        if (libMap.get("name").toString().equals(dep)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        enabledLibs.add(createLibraryMap(dep, dependencyName));
                                    }
                                }
                                FileUtil.writeFile(localLibFile, gson.toJson(enabledLibs));
                            }
                            
                            if (getActivity() == null) return;
                            dismiss();
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
            binding.overallProgress.setVisibility(View.GONE);
            binding.dependenciesRecyclerView.setVisibility(View.GONE);
            binding.dependencyInfo.setText(R.string.local_library_manager_dependency_info);

            downloadItems.clear();
            dependencyAdapter.setDependencies(new ArrayList<>());
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
