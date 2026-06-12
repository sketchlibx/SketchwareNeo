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

        if (prefillDependencyUrl != null && !prefillDependencyUrl.isEmpty()) {
            String[] dp = prefillDependencyUrl.split(":");
            if (dp.length == 3 && isUpgradeMode) {
                binding.dependencyInput.setText(dp[0] + ":" + dp[1] + ":+");
                binding.dependencyInfo.setText("Please enter the version you want to upgrade/downgrade to (e.g. 2.9.0) or leave '+' to fetch the latest.");
            } else {
                binding.dependencyInput.setText(prefillDependencyUrl);
            }
        } else {
            // Updated hint to show that URL is also supported
            binding.dependencyInputLayout.setHint("Dependency (group:artifact:version) OR Direct URL (.aar/.jar)");
        }

        binding.btnDownload.setOnClickListener(v -> initDownloadFlow());

        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
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
            message = "Old versions of this library will be safely removed and replaced with " + dependencyName + ".\n\n" + message;
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
        
        if (isUpgradeMode && prefillDependencyUrl != null && !prefillDependencyUrl.startsWith("http")) {
            String[] dp = prefillDependencyUrl.split(":");
            if (dp.length >= 2) {
                String libraryId = dp[0] + "-" + dp[1];
                File libsFolder = new File(Environment.getExternalStorageDirectory(), ".sketchware/libs/local_libs/");
                if (libsFolder.exists() && libsFolder.listFiles() != null) {
                    for (File lib : libsFolder.listFiles()) {
                        if (lib.getName().startsWith(libraryId)) {
                            FileUtil.deleteFile(lib.getAbsolutePath()); 
                        }
                    }
                }
            }
        }

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
                            if (!notAssociatedWithProject) {
                                var fileContent = FileUtil.readFile(localLibFile);
                                var enabledLibs = gson.fromJson(fileContent, Helper.TYPE_MAP_LIST);
                                
                                if (isUpgradeMode && prefillDependencyUrl != null && !prefillDependencyUrl.startsWith("http")) {
                                    String oldBaseName = prefillDependencyUrl.split(":")[0] + "-" + prefillDependencyUrl.split(":")[1];
                                    for (int i = 0; i < enabledLibs.size(); i++) {
                                        if (enabledLibs.get(i).get("name").toString().startsWith(oldBaseName)) {
                                            enabledLibs.remove(i);
                                            break;
                                        }
                                    }
                                }

                                enabledLibs.addAll(dependencies.stream()
                                        .map(name -> createLibraryMap(name, dependencyName))
                                        .toList());
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
