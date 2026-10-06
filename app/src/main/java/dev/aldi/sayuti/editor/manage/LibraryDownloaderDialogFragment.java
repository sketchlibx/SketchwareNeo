package dev.aldi.sayuti.editor.manage;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class LibraryDownloaderDialogFragment extends BottomSheetDialogFragment {

    private DependencyDownloadAdapter dependencyAdapter;
    private final List<DependencyDownloadItem> downloadItems = new ArrayList<>();
    private ExecutorService downloadExecutor;
    private MavenSearchResultAdapter mavenSearchAdapter;
    private final Set<String> processingCoordinates = new HashSet<>();

    private final Gson gson = new Gson();
    private BuildSettings buildSettings;

    private boolean notAssociatedWithProject;
    private String localLibFile;
    private String prefillDependencyUrl = null;
    private String oldLibraryFolder = null;
    private boolean isUpgradeMode = false;
    private OnLibraryDownloadedTask onLibraryDownloadedTask;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private View rootView;
    private String githubUser = null;
    private String githubRepo = null;
    private MavenSearchResult selectedMavenResult = null;

    private interface LatestVersionCallback {
        void onResolved(@NonNull String latestVersion);
        void onFailure(@NonNull Exception e);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(layoutParams);

                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.library_downloader_dialog, container, false);
        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (downloadExecutor != null && !downloadExecutor.isShutdown()) {
            downloadExecutor.shutdownNow();
        }
        unregisterNetworkCallback();
        rootView = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() == null) return;

        downloadExecutor = Executors.newSingleThreadExecutor();
        notAssociatedWithProject = getArguments().getBoolean("notAssociatedWithProject", false);
        buildSettings = (BuildSettings) getArguments().getSerializable("buildSettings");
        localLibFile = getArguments().getString("localLibFile");
        prefillDependencyUrl = getArguments().getString("prefillDependency");
        isUpgradeMode = getArguments().getBoolean("isUpgradeMode", false);
        oldLibraryFolder = getArguments().getString("oldLibraryFolder");

        setupHeader(view);
        setupTabsAndViews(view);
        setupMavenTab(view);
        setupManualTab(view);
        setupGitHubTab(view);
        setupAdvancedOptions(view);

        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();

        if (prefillDependencyUrl != null && !prefillDependencyUrl.isEmpty()) {
            MaterialButtonToggleGroup tabGroup = view.findViewById(R.id.tab_group);
            tabGroup.check(R.id.tab_manual);
            EditText etManual = view.findViewById(R.id.dependency_input);
            etManual.setText(prefillDependencyUrl);
        }

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_download_main).setOnClickListener(v -> triggerBottomDownloadAction(view));
    }

    private void setupHeader(View view) {
        view.findViewById(R.id.btn_help_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.sketchware.pro"));
            startActivity(intent);
        });
    }

    private void setupTabsAndViews(View view) {
        MaterialButtonToggleGroup tabGroup = view.findViewById(R.id.tab_group);
        android.widget.ViewFlipper viewFlipper = view.findViewById(R.id.view_flipper_tabs);
        View layoutMavenBottom = view.findViewById(R.id.layout_maven_bottom_options);
        
        tabGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                updateBottomButtonState();
                if (checkedId == R.id.tab_maven) {
                    viewFlipper.setDisplayedChild(0);
                    layoutMavenBottom.setVisibility(View.VISIBLE);
                } else if (checkedId == R.id.tab_manual) {
                    viewFlipper.setDisplayedChild(1);
                    layoutMavenBottom.setVisibility(View.GONE);
                } else if (checkedId == R.id.tab_github) {
                    viewFlipper.setDisplayedChild(2);
                    layoutMavenBottom.setVisibility(View.GONE);
                }
            }
        });

        RecyclerView rvDependencies = view.findViewById(R.id.dependencies_recycler_view);
        dependencyAdapter = new DependencyDownloadAdapter();
        rvDependencies.setAdapter(dependencyAdapter);
        rvDependencies.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupMavenTab(View view) {
        RecyclerView rvSearch = view.findViewById(R.id.search_results_recycler_view);
        mavenSearchAdapter = new MavenSearchResultAdapter(processingCoordinates, this::onMavenSearchResultClicked);
        rvSearch.setAdapter(mavenSearchAdapter);
        rvSearch.setLayoutManager(new LinearLayoutManager(getContext()));

        EditText searchInput = view.findViewById(R.id.search_input);
        view.findViewById(R.id.btn_maven_search).setOnClickListener(v -> performMavenSearch(Helper.getText(searchInput)));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performMavenSearch(Helper.getText(searchInput));
                return true;
            }
            return false;
        });
    }

    private void setupManualTab(View view) {
        EditText input = view.findViewById(R.id.dependency_input);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { updateBottomButtonState(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupGitHubTab(View view) {
        EditText ghUrlInput = view.findViewById(R.id.et_github_url);
        ghUrlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                ghUrlInput.clearFocus();
                detectGitHubRepo(ghUrlInput.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void setupAdvancedOptions(View view) {
        setupAdvancedToggle(view.findViewById(R.id.tab_manual)); 
        setupAdvancedToggle(view.findViewById(R.id.tab_github)); 
    }

    private void setupAdvancedToggle(View parentRoot) {
        if (rootView == null) return;
        View header = parentRoot.findViewById(R.id.layout_advanced_header); 
        if (header != null) {
            View content = parentRoot.findViewById(R.id.layout_advanced_content);
            View divider = parentRoot.findViewById(R.id.advanced_divider);
            ImageView arrow = header.findViewById(R.id.img_advanced_arrow);
            header.setOnClickListener(v -> {
                boolean isVis = content.getVisibility() == View.VISIBLE;
                content.setVisibility(isVis ? View.GONE : View.VISIBLE);
                if (divider != null) divider.setVisibility(isVis ? View.GONE : View.VISIBLE);
                if (arrow != null) arrow.setRotation(isVis ? 0 : 180);
                
                if (content.getParent() != null && content.getParent().getParent() instanceof ScrollView) {
                    ((ScrollView) content.getParent().getParent()).smoothScrollBy(0, 200);
                }
            });
        }
    }

    private void updateBottomButtonState() {
        if (rootView == null) return;
        MaterialButtonToggleGroup tabs = rootView.findViewById(R.id.tab_group);
        MaterialButton btnDownload = rootView.findViewById(R.id.btn_download_main);
        
        if (tabs.getCheckedButtonId() == R.id.tab_maven) {
            btnDownload.setEnabled(selectedMavenResult != null);
        } else if (tabs.getCheckedButtonId() == R.id.tab_manual) {
            EditText et = rootView.findViewById(R.id.dependency_input);
            btnDownload.setEnabled(et.getText() != null && et.getText().toString().trim().length() > 3);
        } else if (tabs.getCheckedButtonId() == R.id.tab_github) {
            btnDownload.setEnabled(githubUser != null && githubRepo != null);
        }
    }

    private void triggerBottomDownloadAction(View root) {
        MaterialButtonToggleGroup tabs = root.findViewById(R.id.tab_group);
        int activeId = tabs.getCheckedButtonId();
        
        if (activeId == R.id.tab_maven) {
            if (selectedMavenResult != null) {
                CheckBox cbSkip = root.findViewById(R.id.cb_skip_maven);
                initDownloadFlow(selectedMavenResult.getFullCoordinate(), cbSkip.isChecked(), false);
            }
        } else if (activeId == R.id.tab_manual) {
            EditText et = root.findViewById(R.id.dependency_input);
            initDownloadFlow(et.getText().toString().trim(), getSkipSubDepsFromView(root, 1), getIncludeSourcesFromView(root, 1));
        } else if (activeId == R.id.tab_github) {
            if (githubUser == null || githubRepo == null) return;
            AutoCompleteTextView spVersion = root.findViewById(R.id.spinner_gh_versions);
            AutoCompleteTextView spModule = root.findViewById(R.id.spinner_gh_modules);
            
            String tag = spVersion.getText().toString();
            String module = spModule.getText().toString();
            if (tag.isEmpty()) { SketchwareUtil.toast("Select a version"); return; }
            
            String dependencyStr = module.equals("(Root Module)") ? 
                    "com.github." + githubUser + ":" + githubRepo + ":" + tag :
                    "com.github." + githubUser + "." + githubRepo + ":" + module + ":" + tag;
                    
            initDownloadFlow(dependencyStr, getSkipSubDepsFromView(root, 2), getIncludeSourcesFromView(root, 2));
        }
    }

    private boolean getSkipSubDepsFromView(View root, int childIndex) {
        android.widget.ViewFlipper flipper = root.findViewById(R.id.view_flipper_tabs);
        if (flipper != null) {
            CheckBox cb = flipper.getChildAt(childIndex).findViewById(R.id.cb_skip_subdependencies);
            return cb != null && cb.isChecked();
        }
        return false;
    }

    private boolean getIncludeSourcesFromView(View root, int childIndex) {
        android.widget.ViewFlipper flipper = root.findViewById(R.id.view_flipper_tabs);
        if (flipper != null) {
            CheckBox cb = flipper.getChildAt(childIndex).findViewById(R.id.cb_include_sources);
            return cb != null && cb.isChecked();
        }
        return false;
    }

    private void detectGitHubRepo(String url) {
        Matcher m = Pattern.compile("github\\.com/([^/]+)/([^/]+)").matcher(url);
        if (!m.find()) {
            SketchwareUtil.toastError("Invalid GitHub URL");
            return;
        }
        githubUser = m.group(1);
        githubRepo = m.group(2).replace(".git", "");

        View cardInfo = rootView.findViewById(R.id.card_github_repo_info);
        cardInfo.setVisibility(View.GONE);
        
        MaterialAlertDialogBuilder progress = new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detecting Repository...")
            .setMessage("Fetching releases and modules from GitHub API for " + githubUser + "/" + githubRepo)
            .setCancelable(false);
        androidx.appcompat.app.AlertDialog dialog = progress.create();
        dialog.show();

        downloadExecutor.execute(() -> {
            try {
                URL repoUrl = new URL("https://api.github.com/repos/" + githubUser + "/" + githubRepo);
                HttpURLConnection c1 = (HttpURLConnection) repoUrl.openConnection();
                c1.setRequestMethod("GET");
                c1.setRequestProperty("Accept", "application/vnd.github.v3+json");
                BufferedReader r1 = new BufferedReader(new InputStreamReader(c1.getInputStream()));
                StringBuilder sb1 = new StringBuilder();
                String line;
                while ((line = r1.readLine()) != null) sb1.append(line);
                r1.close();
                String description = new JSONObject(sb1.toString()).optString("description", "A GitHub Repository");

                URL tagsUrl = new URL("https://api.github.com/repos/" + githubUser + "/" + githubRepo + "/tags");
                HttpURLConnection c2 = (HttpURLConnection) tagsUrl.openConnection();
                c2.setRequestMethod("GET");
                c2.setRequestProperty("Accept", "application/vnd.github.v3+json");
                BufferedReader r2 = new BufferedReader(new InputStreamReader(c2.getInputStream()));
                StringBuilder sb2 = new StringBuilder();
                while ((line = r2.readLine()) != null) sb2.append(line);
                r2.close();

                JSONArray tagsArray = new JSONArray(sb2.toString());
                List<String> tags = new ArrayList<>();
                for (int i = 0; i < tagsArray.length(); i++) {
                    tags.add(tagsArray.getJSONObject(i).getString("name"));
                }
                
                List<String> modules = new ArrayList<>();
                modules.add("(Root Module)");
                try {
                    URL contentsUrl = new URL("https://api.github.com/repos/" + githubUser + "/" + githubRepo + "/contents");
                    HttpURLConnection c3 = (HttpURLConnection) contentsUrl.openConnection();
                    c3.setRequestMethod("GET");
                    c3.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    BufferedReader r3 = new BufferedReader(new InputStreamReader(c3.getInputStream()));
                    StringBuilder sb3 = new StringBuilder();
                    while ((line = r3.readLine()) != null) sb3.append(line);
                    r3.close();

                    JSONArray contentsArray = new JSONArray(sb3.toString());
                    for (int i = 0; i < contentsArray.length(); i++) {
                        JSONObject obj = contentsArray.getJSONObject(i);
                        if ("dir".equals(obj.getString("type"))) {
                            modules.add(obj.getString("name"));
                        }
                    }
                } catch (Exception ignored) { } 

                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    if (tags.isEmpty()) {
                        SketchwareUtil.toast("No tags/releases found for this repository.");
                        return;
                    }
                    cardInfo.setVisibility(View.VISIBLE);
                    ((TextView) rootView.findViewById(R.id.tv_gh_repo_name)).setText(githubUser + " / " + githubRepo);
                    ((TextView) rootView.findViewById(R.id.tv_gh_repo_desc)).setText(description);
                    
                    rootView.findViewById(R.id.btn_github_open).setOnClickListener(view -> {
                         startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + githubUser + "/" + githubRepo)));
                    });
                    
                    AutoCompleteTextView spVersion = rootView.findViewById(R.id.spinner_gh_versions);
                    spVersion.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, tags));
                    spVersion.setText(tags.get(0), false);

                    AutoCompleteTextView spModule = rootView.findViewById(R.id.spinner_gh_modules);
                    spModule.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, modules));
                    spModule.setText(modules.get(0), false);
                    
                    updateBottomButtonState();
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    SketchwareUtil.toastError("GitHub API Error: " + e.getMessage());
                });
            }
        });
    }

    private void fetchLatestVersionForManual(String group, String artifact, String currentVersion) {
        resolveLatestVersion(group, artifact, new LatestVersionCallback() {
            @Override
            public void onResolved(@NonNull String latestVersion) {
                if (rootView == null) return;
                EditText input = rootView.findViewById(R.id.dependency_input);
                if (input != null) {
                    input.setText(group + ":" + artifact + ":" + latestVersion);
                }
                if (currentVersion.equals(latestVersion)) {
                    SketchwareUtil.toast("Already on the latest version (" + latestVersion + ")");
                } else {
                    SketchwareUtil.toast("Latest version found: " + latestVersion);
                }
            }
            @Override
            public void onFailure(@NonNull Exception e) { }
        });
    }

    private void performMavenSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        String trimmedQuery = query.trim();

        LinearProgressIndicator pBar = rootView.findViewById(R.id.search_progress);
        RecyclerView rvSearch = rootView.findViewById(R.id.search_results_recycler_view);
        pBar.setVisibility(View.VISIBLE);
        rvSearch.setVisibility(View.GONE);
        selectedMavenResult = null;
        updateBottomButtonState();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String encodedQuery = java.net.URLEncoder.encode(trimmedQuery, "UTF-8");
                URL url = new URL("https://search.maven.org/solrsearch/select?q=" + encodedQuery + "&rows=30&wt=json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

                List<MavenSearchResult> results = new ArrayList<>();
                for (int i = 0; i < docs.length(); i++) {
                    JSONObject doc = docs.getJSONObject(i);
                    String g = doc.optString("g");
                    String a = doc.optString("a");
                    String v = doc.optString("latestVersion", doc.optString("v", ""));
                    if (!g.isEmpty() && !a.isEmpty() && !v.isEmpty()) {
                        results.add(new MavenSearchResult(g, a, v));
                    }
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (rootView == null) return;
                    pBar.setVisibility(View.GONE);
                    mavenSearchAdapter.setResults(results);
                    rvSearch.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
                    if (results.isEmpty()) SketchwareUtil.toast("No results found for \"" + trimmedQuery + "\"");
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (rootView == null) return;
                    pBar.setVisibility(View.GONE);
                    SketchwareUtil.toastError("Search failed: " + e.getMessage());
                });
            }
        });
    }

    private void onMavenSearchResultClicked(@NonNull MavenSearchResult result) {
        selectedMavenResult = result;
        mavenSearchAdapter.setSelected(result);
        updateBottomButtonState();
        
        LocalLibrary installed = LocalLibrariesUtil.findInstalledLibraryByGroupArtifact(result.getGroup(), result.getArtifact());
        if (installed != null) {
            String installedDependency = installed.getMavenDependency();
            String[] installedParts = installedDependency != null ? installedDependency.split(":") : new String[0];
            String installedVersion = installedParts.length == 3 ? installedParts[2] : "unknown";

            if (!installedVersion.equals(result.getLatestVersion())) {
                isUpgradeMode = true;
                oldLibraryFolder = installed.getName();
                SketchwareUtil.toast("Upgrade available from " + installedVersion + " to " + result.getLatestVersion());
            } else {
                isUpgradeMode = false;
                oldLibraryFolder = null;
            }
        }
    }

    private boolean isLibraryBuiltIn(String group, String artifact) {
        for (BuiltInLibraries.BuiltInLibrary lib : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            String name = lib.getName();
            if (name.startsWith(artifact + "-")) {
                return true;
            }
        }
        return false;
    }

    private void resolveLatestVersion(String group, String artifact, LatestVersionCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL("https://search.maven.org/solrsearch/select?q=g:%22" + group + "%22+AND+a:%22" + artifact + "%22&rows=1&wt=json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONArray docs = json.getJSONObject("response").getJSONArray("docs");

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

    private void initDownloadFlow(String dependencyStr, boolean skipSubDeps, boolean includeSources) {
        if (dependencyStr == null || dependencyStr.isEmpty()) {
            SketchwareUtil.toastError("Dependency cannot be empty");
            return;
        }

        boolean isDirectUrl = dependencyStr.startsWith("http://") || dependencyStr.startsWith("https://");

        if (isDirectUrl) {
            startDownloadProcess(dependencyStr, "", "", dependencyStr, skipSubDeps, includeSources, isUpgradeMode, oldLibraryFolder);
            return;
        }

        var parts = dependencyStr.split(":");
        if (parts.length != 3) {
            SketchwareUtil.toastError("Invalid format. Use group:artifact:version OR a full URL");
            return;
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2].trim();

        if (version.equals("+")) {
            resolveLatestVersion(group, artifact, new LatestVersionCallback() {
                @Override
                public void onResolved(@NonNull String latestVersion) {
                    showDownloadConfirmationDialog(group, artifact, latestVersion, group + ":" + artifact + ":" + latestVersion, skipSubDeps, includeSources);
                }
                @Override
                public void onFailure(@NonNull Exception e) {
                    SketchwareUtil.toastError("Could not resolve latest version for " + group + ":" + artifact);
                }
            });
            return;
        }

        if (isLibraryBuiltIn(group, artifact)) {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Built-in Library Detected")
                .setMessage("A version of '" + artifact + "' is already built into Sketchware Neo. Downloading an external version might cause 'Duplicate Classes' compilation errors unless configured carefully.\n\nProceed anyway?")
                .setPositiveButton("Download Anyway", (d, w) -> 
                        startDownloadProcess(group, artifact, version, dependencyStr, skipSubDeps, includeSources, isUpgradeMode, oldLibraryFolder))
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        showDownloadConfirmationDialog(group, artifact, version, dependencyStr, skipSubDeps, includeSources);
    }

    private void showDownloadConfirmationDialog(String group, String artifact, String version, String fullDepStr, boolean skipSubDeps, boolean includeSources) {
        boolean isDirectUrl = group.startsWith("http://") || group.startsWith("https://");

        String message;
        if (isDirectUrl) {
            message = "Are you sure you want to download the library directly from this URL?\n\n" + fullDepStr;
        } else {
            message = skipSubDeps 
                    ? "Are you sure you want to download " + fullDepStr 
                    : "Are you sure you want to download " + fullDepStr + " and its sub-dependencies?";
        }

        if (isUpgradeMode) {
            message = "Old version of this library (" + oldLibraryFolder + ") will be safely replaced with " + fullDepStr + ".\n\n" + message;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isUpgradeMode ? "Confirm Upgrade" : "Confirm Download")
                .setMessage(message)
                .setPositiveButton(isUpgradeMode ? "Upgrade" : "Download", (dialog, which) -> 
                        startDownloadProcess(group, artifact, version, fullDepStr, skipSubDeps, includeSources, isUpgradeMode, oldLibraryFolder))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownloadProcess(String group, String artifact, String version, String requestDependencyString, 
                                      boolean skipSubDeps, boolean includeSources, boolean upgradeMode, @Nullable String oldFolder) {
        
        isUpgradeMode = false;
        oldLibraryFolder = null;
        processingCoordinates.add(group + ":" + artifact);
        if (mavenSearchAdapter != null) mavenSearchAdapter.notifyDataSetChanged();

        View flipperMain = rootView.findViewById(R.id.view_flipper_main);
        ((android.widget.ViewFlipper) flipperMain).setDisplayedChild(1); 
        
        TextView tvHeader = rootView.findViewById(R.id.tv_processing_header);
        if (tvHeader != null) {
            tvHeader.setText(upgradeMode ? "Upgrading Dependency..." : "Processing Dependencies...");
        }

        rootView.findViewById(R.id.btn_cancel_processing).setOnClickListener(v -> finishDownloadSuccess(group, artifact, new ArrayList<>()));
        setCancelable(false);

        LinearProgressIndicator overallProgress = rootView.findViewById(R.id.overall_progress);
        overallProgress.setIndeterminate(true);

        boolean isDirectUrlRequest = group.startsWith("http://") || group.startsWith("https://");

        var resolver = new DependencyResolver(group, artifact, version, skipSubDeps, buildSettings);
        var handler = new Handler(Looper.getMainLooper());

        downloadExecutor.execute(() -> {
            try {
                BuiltInLibraries.maybeExtractAndroidJar((message, progress) ->
                        handler.post(() -> overallProgress.setIndeterminate(true)));
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
                        handler.post(() -> finishDownloadWithError("Dependency '" + dep + "' not found", group, artifact));
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
                    public void onDownloadStart(@NonNull Artifact dep) {
                        handler.post(() -> {
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
                            finishDownloadWithError("Downloading dependency '" + dep + "' failed: " + e.getMessage(), group, artifact);
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
                            finishDownloadWithError("Dexing dependency '" + dependency + "' failed: " + e.getMessage(), group, artifact);
                        });
                    }

                    @Override
                    public void onTaskCompleted(@NonNull List<String> dependencies) {
                        if (includeSources && !isDirectUrlRequest) {
                            handler.post(() -> {
                                if (tvHeader != null) tvHeader.setText("Downloading Sources...");
                            });
                            downloadSourcesIfAvailable(group, artifact, version);
                        }

                        handler.post(() -> {
                            SketchwareUtil.toast("Library downloaded successfully");

                            if (upgradeMode && oldFolder != null) {
                                if (!dependencies.contains(oldFolder)) {
                                    File oldLibPath = new File(Environment.getExternalStorageDirectory(), ".sketchware/libs/local_libs/" + oldFolder);
                                    if (oldLibPath.exists()) {
                                        FileUtil.deleteFile(oldLibPath.getAbsolutePath());
                                    }
                                }
                            }

                            if (!isDirectUrlRequest) {
                                for (String dep : dependencies) {
                                    LocalLibrariesUtil.writeArtifactMetadata(dep, requestDependencyString);
                                }
                                LocalLibrariesUtil.clearCache();
                            }

                            if (!notAssociatedWithProject) {
                                var fileContent = FileUtil.readFile(localLibFile);
                                var enabledLibs = gson.fromJson(fileContent, Helper.TYPE_MAP_LIST);

                                if (upgradeMode && oldFolder != null) {
                                    for (int i = 0; i < enabledLibs.size(); i++) {
                                        if (enabledLibs.get(i).get("name").toString().equals(oldFolder)) {
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
                                        enabledLibs.add(LocalLibrariesUtil.createLibraryMap(dep, isDirectUrlRequest ? group : requestDependencyString));
                                    }
                                }
                                FileUtil.writeFile(localLibFile, gson.toJson(enabledLibs));
                            }

                            finishDownloadSuccess(group, artifact, dependencies);
                        });
                    }
                });
            } catch (Exception e) {
                handler.post(() -> finishDownloadWithError("Invalid Dependency or Tag Not Found!\nDetails: " + e.getMessage(), group, artifact));
            }
        });
    }

    private void downloadSourcesIfAvailable(String group, String artifact, String version) {
        try {
            String pathGroup = group.replace(".", "/");
            String sourcesUrl = "https://repo1.maven.org/maven2/" + pathGroup + "/" + artifact + "/" + version + "/" + artifact + "-" + version + "-sources.jar";
            if (group.startsWith("com.github")) {
                String[] ghParts = group.split("\\.");
                if (ghParts.length >= 3) {
                    sourcesUrl = "https://jitpack.io/com/github/" + ghParts[2] + "/" + artifact + "/" + version + "/" + artifact + "-" + version + "-sources.jar";
                }
            }
            
            URL url = new URL(sourcesUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            if (conn.getResponseCode() == 200) {
                File outDir = new File(FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/" + artifact + "-v" + version);
                outDir.mkdirs();
                File outFile = new File(outDir, artifact + "-" + version + "-sources.jar");
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(outFile);
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
            }
        } catch (Exception ignored) {}
    }

    private void finishDownloadSuccess(String group, String artifact, List<String> dependencies) {
        processingCoordinates.remove(group + ":" + artifact);
        if (mavenSearchAdapter != null) mavenSearchAdapter.notifyDataSetChanged();
        setCancelable(true);
        if (onLibraryDownloadedTask != null) onLibraryDownloadedTask.invoke(dependencies);
        dismiss();
    }

    private void finishDownloadWithError(String error, String group, String artifact) {
        processingCoordinates.remove(group + ":" + artifact);
        if (mavenSearchAdapter != null) mavenSearchAdapter.notifyDataSetChanged();
        
        ((android.widget.ViewFlipper) rootView.findViewById(R.id.view_flipper_main)).setDisplayedChild(0);
        setCancelable(true);
        
        SketchwareUtil.showAnErrorOccurredDialog(getActivity(), error);
    }

    private DependencyDownloadItem findOrCreateDependencyItem(Artifact artifact) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact)) return item;
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
        if (rootView == null) return;
        int completed = 0;
        for (DependencyDownloadItem item : downloadItems) {
            if (item.isCompleted()) completed++;
        }
        if (!downloadItems.isEmpty()) {
            LinearProgressIndicator pi = rootView.findViewById(R.id.overall_progress);
            pi.setIndeterminate(false);
            pi.setProgress((completed * 100) / downloadItems.size());
        }
    }

    private void registerNetworkCallback() {
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) { }
            @Override
            public void onLost(@NonNull Network network) { }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    public void setOnLibraryDownloadedTask(OnLibraryDownloadedTask onLibraryDownloadedTask) {
        this.onLibraryDownloadedTask = onLibraryDownloadedTask;
    }

    public interface OnLibraryDownloadedTask {
        void invoke(List<String> dependencies);
    }
}
