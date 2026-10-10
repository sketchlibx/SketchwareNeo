package dev.aldi.sayuti.editor.manage;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
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
import org.cosmic.ide.dependency.resolver.api.Repository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import mod.pranav.dependency.resolver.FailureFormatter;
import mod.pranav.dependency.resolver.FileDownloader;
import mod.pranav.dependency.resolver.HttpFetcher;
import mod.pranav.dependency.resolver.MavenVersions;
import mod.pranav.dependency.resolver.RepositoryProbe;
import mod.pranav.dependency.resolver.ResolverFailure;
import pro.sketchware.R;
import pro.sketchware.activities.editor.gradle.BuiltInArtifacts;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class LibraryDownloaderDialogFragment extends BottomSheetDialogFragment {

    private static final Pattern PART_PATTERN = Pattern.compile("[A-Za-z0-9_.\\-]+");
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9_.\\-+]+");
    private static final long PROGRESS_POST_INTERVAL_MS = 150;

    private static final class DownloadRequest {
        final String group;
        final String artifact;
        final String version;
        final String requestString;
        final boolean skipSubDeps;
        final boolean includeSources;
        final boolean directUrl;
        final boolean upgrade;
        @Nullable
        final String oldFolder;

        DownloadRequest(String group, String artifact, String version, String requestString, boolean skipSubDeps,
                        boolean includeSources, boolean directUrl, boolean upgrade, @Nullable String oldFolder) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
            this.requestString = requestString;
            this.skipSubDeps = skipSubDeps;
            this.includeSources = includeSources;
            this.directUrl = directUrl;
            this.upgrade = upgrade;
            this.oldFolder = oldFolder;
        }

        String key() {
            return directUrl ? requestString : group + ":" + artifact + ":" + version;
        }

        String coordinateName() {
            return directUrl ? requestString : group + ":" + artifact;
        }
    }

    private static final class RequestResult {
        final DownloadRequest request;
        final boolean success;
        final boolean cancelled;
        final String message;
        final List<String> folders;

        RequestResult(DownloadRequest request, boolean success, boolean cancelled, String message, List<String> folders) {
            this.request = request;
            this.success = success;
            this.cancelled = cancelled;
            this.message = message;
            this.folders = folders;
        }
    }

    private interface VersionsCallback {
        void onLoaded(@NonNull MavenCentralClient.VersionList list);

        void onFailure(@NonNull String message);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final List<DependencyDownloadItem> downloadItems = new ArrayList<>();
    private final Set<String> processingCoordinates = new HashSet<>();
    private final Map<String, MavenSearchResult> selectedMaven = new LinkedHashMap<>();
    private final List<RequestResult> queueResults = new ArrayList<>();
    private final Object resolverLock = new Object();

    private DependencyDownloadAdapter dependencyAdapter;
    private ExecutorService downloadExecutor;
    private ExecutorService searchExecutor;
    private ExecutorService metadataExecutor;
    private MavenSearchResultAdapter mavenSearchAdapter;
    private BuildSettings buildSettings;
    private boolean notAssociatedWithProject;
    private String scId;
    private String localLibFile;
    private String prefillDependencyUrl = null;
    private String oldLibraryFolder = null;
    private boolean isUpgradeMode = false;
    private OnLibraryDownloadedTask onLibraryDownloadedTask;
    private View rootView;
    private String githubUser = null;
    private String githubRepo = null;

    private List<MavenSearchResult> lastResults = new ArrayList<>();
    private String lastSource = "";
    private String lastQuery = "";
    private int searchGeneration = 0;
    private MavenCentralClient.SortMode sortMode = MavenCentralClient.SortMode.RELEVANCE;
    private MavenCentralClient.TypeFilter typeFilter = MavenCentralClient.TypeFilter.ANY;
    private boolean stableOnly = false;
    private MavenCentralClient mavenClient;

    private volatile boolean queueRunning = false;
    private volatile boolean cancelRequested = false;
    private DependencyResolver activeResolver;
    private List<DownloadRequest> currentQueue = new ArrayList<>();
    private int queueTotal = 0;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
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
        cancelRequested = true;
        synchronized (resolverLock) {
            if (activeResolver != null) activeResolver.cancel();
        }
        if (downloadExecutor != null && !downloadExecutor.isShutdown()) downloadExecutor.shutdownNow();
        if (searchExecutor != null && !searchExecutor.isShutdown()) searchExecutor.shutdownNow();
        if (metadataExecutor != null && !metadataExecutor.isShutdown()) metadataExecutor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        rootView = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() == null) return;
        downloadExecutor = Executors.newSingleThreadExecutor();
        searchExecutor = Executors.newSingleThreadExecutor();
        metadataExecutor = Executors.newSingleThreadExecutor();
        notAssociatedWithProject = getArguments().getBoolean("notAssociatedWithProject", false);
        buildSettings = (BuildSettings) getArguments().getSerializable("buildSettings");
        scId = getArguments().getString("scId");
        localLibFile = getArguments().getString("localLibFile");
        prefillDependencyUrl = getArguments().getString("prefillDependency");
        isUpgradeMode = getArguments().getBoolean("isUpgradeMode", false);
        oldLibraryFolder = getArguments().getString("oldLibraryFolder");

        setupTabsAndViews(view);
        setupMavenTab(view);
        setupManualTab(view);
        setupGitHubTab(view);
        setupAdvancedOptions(view);
        setupBottomOptions(view);

        if (prefillDependencyUrl != null && !prefillDependencyUrl.isEmpty()) {
            MaterialButtonToggleGroup tabGroup = view.findViewById(R.id.tab_group);
            tabGroup.check(R.id.tab_manual);
            EditText etManual = view.findViewById(R.id.dependency_input);
            etManual.setText(prefillDependencyUrl);
        }
        updateBottomButtonState();

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_download_main).setOnClickListener(v -> triggerBottomDownloadAction(view));
    }

    private void setupTabsAndViews(View view) {
        MaterialButtonToggleGroup tabGroup = view.findViewById(R.id.tab_group);
        android.widget.ViewFlipper viewFlipper = view.findViewById(R.id.view_flipper_tabs);
        tabGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.tab_maven) {
                viewFlipper.setDisplayedChild(0);
            } else if (checkedId == R.id.tab_manual) {
                viewFlipper.setDisplayedChild(1);
            } else if (checkedId == R.id.tab_github) {
                viewFlipper.setDisplayedChild(2);
            }
            updateBottomButtonState();
            hideKeyboard();
            resetTabScroll();
        });

        RecyclerView rvDependencies = view.findViewById(R.id.dependencies_recycler_view);
        dependencyAdapter = new DependencyDownloadAdapter();
        rvDependencies.setAdapter(dependencyAdapter);
        rvDependencies.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void resetTabScroll() {
        if (rootView == null) return;
        NestedScrollView scroll = rootView.findViewById(R.id.tabs_scroll);
        if (scroll == null) return;
        scroll.stopNestedScroll();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void hideKeyboard() {
        if (rootView == null) return;
        View focused = rootView.findFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
        if (focused != null) focused.clearFocus();
    }

    private void setupMavenTab(View view) {
        RecyclerView rvSearch = view.findViewById(R.id.search_results_recycler_view);
        mavenSearchAdapter = new MavenSearchResultAdapter(processingCoordinates, selectedMaven, new MavenSearchResultAdapter.SelectionListener() {
            @Override
            public void onResultClicked(@NonNull MavenSearchResult result) {
                toggleMavenSelection(result);
            }

            @Override
            public void onVersionClicked(@NonNull MavenSearchResult result) {
                showVersionPicker(result);
            }
        });
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
        view.findViewById(R.id.btn_search_retry).setOnClickListener(v -> performMavenSearch(lastQuery));

        view.findViewById(R.id.btn_select_all).setOnClickListener(v -> selectAllVisible());
        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            selectedMaven.clear();
            mavenSearchAdapter.refreshAll();
            updateSelectionUi();
            updateBottomButtonState();
        });

        MaterialButton btnSort = view.findViewById(R.id.btn_sort);
        btnSort.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(requireContext(), v);
            menu.getMenu().add(0, 0, 0, "Relevance");
            menu.getMenu().add(0, 1, 1, "Name (A-Z)");
            menu.getMenu().add(0, 2, 2, "Newest release");
            menu.getMenu().add(0, 3, 3, "Most versions");
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        sortMode = MavenCentralClient.SortMode.NAME;
                        break;
                    case 2:
                        sortMode = MavenCentralClient.SortMode.NEWEST;
                        break;
                    case 3:
                        sortMode = MavenCentralClient.SortMode.MOST_VERSIONS;
                        break;
                    default:
                        sortMode = MavenCentralClient.SortMode.RELEVANCE;
                }
                btnSort.setText(item.getTitle());
                applyResultFilters();
                return true;
            });
            menu.show();
        });

        MaterialButton btnType = view.findViewById(R.id.btn_type_filter);
        btnType.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(requireContext(), v);
            menu.getMenu().add(0, 0, 0, "Any type");
            menu.getMenu().add(0, 1, 1, "AAR (Android library)");
            menu.getMenu().add(0, 2, 2, "JAR (Java library)");
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        typeFilter = MavenCentralClient.TypeFilter.AAR;
                        btnType.setText("AAR");
                        break;
                    case 2:
                        typeFilter = MavenCentralClient.TypeFilter.JAR;
                        btnType.setText("JAR");
                        break;
                    default:
                        typeFilter = MavenCentralClient.TypeFilter.ANY;
                        btnType.setText("Any type");
                }
                applyResultFilters();
                return true;
            });
            menu.show();
        });

        MaterialButton btnVersionMode = view.findViewById(R.id.btn_version_mode);
        btnVersionMode.setOnClickListener(v -> {
            stableOnly = !stableOnly;
            btnVersionMode.setText(stableOnly ? "Stable only" : "Any version");
            if (stableOnly) {
                for (MavenSearchResult selected : new ArrayList<>(selectedMaven.values())) {
                    resolveStableDefault(selected);
                }
                SketchwareUtil.toast("Newly selected libraries use their latest stable version");
            }
        });
    }

    private void setupManualTab(View view) {
        EditText input = view.findViewById(R.id.dependency_input);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateBottomButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupGitHubTab(View view) {
        EditText ghUrlInput = view.findViewById(R.id.et_github_url);
        ghUrlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                ghUrlInput.clearFocus();
                hideKeyboard();
                detectGitHubRepo(ghUrlInput.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void setupAdvancedOptions(View view) {
        android.widget.ViewFlipper flipper = view.findViewById(R.id.view_flipper_tabs);
        setupAdvancedToggle(flipper.getChildAt(1));
        setupAdvancedToggle(flipper.getChildAt(2));
    }

    private void setupAdvancedToggle(View parentRoot) {
        if (parentRoot == null) return;
        View header = parentRoot.findViewById(R.id.layout_advanced_header);
        View content = parentRoot.findViewById(R.id.layout_advanced_content);
        if (header == null || content == null) return;
        View divider = parentRoot.findViewById(R.id.advanced_divider);
        ImageView arrow = header.findViewById(R.id.img_advanced_arrow);
        header.setOnClickListener(v -> {
            boolean wasVisible = content.getVisibility() == View.VISIBLE;
            content.setVisibility(wasVisible ? View.GONE : View.VISIBLE);
            if (divider != null) divider.setVisibility(wasVisible ? View.GONE : View.VISIBLE);
            if (arrow != null) arrow.setRotation(wasVisible ? 0 : 180);
            if (!wasVisible) {
                content.post(() -> content.requestRectangleOnScreen(new Rect(0, 0, content.getWidth(), content.getHeight()), false));
            }
        });
    }

    private void setupBottomOptions(View view) {
        view.findViewById(R.id.btn_skip_info).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Skip downloading sub-dependencies")
                .setMessage("Only the library you request is downloaded. The libraries it depends on are not fetched, "
                        + "so add them yourself if the build reports missing classes.\n\n"
                        + "Leave this off to download the library together with everything it needs. "
                        + "Dependencies already provided by Neo's built-in libraries are never downloaded again.")
                .setPositiveButton("OK", null)
                .show());
    }

    private void updateBottomButtonState() {
        if (rootView == null) return;
        MaterialButtonToggleGroup tabs = rootView.findViewById(R.id.tab_group);
        MaterialButton btnDownload = rootView.findViewById(R.id.btn_download_main);
        boolean busy = queueRunning;
        if (tabs.getCheckedButtonId() == R.id.tab_maven) {
            int count = selectedMaven.size();
            btnDownload.setEnabled(count > 0 && !busy);
            btnDownload.setText(count > 1 ? "Download (" + count + ")" : "Download");
        } else {
            btnDownload.setText("Download");
            if (tabs.getCheckedButtonId() == R.id.tab_manual) {
                EditText et = rootView.findViewById(R.id.dependency_input);
                btnDownload.setEnabled(!busy && et.getText() != null && et.getText().toString().trim().length() > 3);
            } else if (tabs.getCheckedButtonId() == R.id.tab_github) {
                btnDownload.setEnabled(!busy && githubUser != null && githubRepo != null);
            }
        }
    }

    private boolean skipSubDepsChecked() {
        if (rootView == null) return false;
        CheckBox cb = rootView.findViewById(R.id.cb_skip_maven);
        return cb != null && cb.isChecked();
    }

    private boolean includeSourcesFromView(int childIndex) {
        if (rootView == null) return false;
        android.widget.ViewFlipper flipper = rootView.findViewById(R.id.view_flipper_tabs);
        if (flipper == null) return false;
        View child = flipper.getChildAt(childIndex);
        CheckBox cb = child == null ? null : child.findViewById(R.id.cb_include_sources);
        return cb != null && cb.isChecked();
    }

    private void triggerBottomDownloadAction(View root) {
        if (queueRunning) return;
        MaterialButtonToggleGroup tabs = root.findViewById(R.id.tab_group);
        int activeId = tabs.getCheckedButtonId();
        hideKeyboard();
        if (activeId == R.id.tab_maven) {
            startMavenSelectionDownload();
        } else if (activeId == R.id.tab_manual) {
            EditText et = root.findViewById(R.id.dependency_input);
            initManualDownloadFlow(et.getText().toString().trim(), skipSubDepsChecked(), includeSourcesFromView(1));
        } else if (activeId == R.id.tab_github) {
            if (githubUser == null || githubRepo == null) return;
            AutoCompleteTextView spVersion = root.findViewById(R.id.spinner_gh_versions);
            AutoCompleteTextView spModule = root.findViewById(R.id.spinner_gh_modules);
            String tag = spVersion.getText().toString();
            String module = spModule.getText().toString();
            if (tag.isEmpty()) {
                SketchwareUtil.toast("Select a version");
                return;
            }
            String dependencyStr = module.equals("(Root Module)")
                    ? "com.github." + githubUser + ":" + githubRepo + ":" + tag
                    : "com.github." + githubUser + "." + githubRepo + ":" + module + ":" + tag;
            initManualDownloadFlow(dependencyStr, skipSubDepsChecked(), includeSourcesFromView(2));
        }
    }

    private void detectGitHubRepo(String url) {
        Matcher m = Pattern.compile("github\\.com/([^/\\s?#]+)/([^/\\s?#]+)").matcher(url);
        if (!m.find()) {
            SketchwareUtil.toastError("Invalid GitHub URL");
            return;
        }
        githubUser = m.group(1);
        githubRepo = m.group(2).replaceFirst("\\.git$", "");
        final String user = githubUser;
        final String repo = githubRepo;
        View cardInfo = rootView.findViewById(R.id.card_github_repo_info);
        cardInfo.setVisibility(View.GONE);
        updateBottomButtonState();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Detecting Repository...")
                .setMessage("Fetching releases and modules from GitHub API for " + user + "/" + repo)
                .setCancelable(false)
                .create();
        dialog.show();

        downloadExecutor.execute(() -> {
            try {
                String base = "https://api.github.com/repos/" + user + "/" + repo;
                HttpFetcher.Response info = HttpFetcher.getWithRetry(base, 1024 * 1024, 2, null);
                if (!info.isSuccess()) throw new IOException(describeGitHubFailure(info, "repository " + user + "/" + repo));
                JSONObject repoJson = new JSONObject(info.body);
                String description = repoJson.isNull("description") ? "A GitHub Repository" : repoJson.optString("description", "A GitHub Repository");

                HttpFetcher.Response tagResponse = HttpFetcher.getWithRetry(base + "/tags?per_page=100", 2 * 1024 * 1024, 2, null);
                if (!tagResponse.isSuccess()) throw new IOException(describeGitHubFailure(tagResponse, "tags of " + user + "/" + repo));
                JSONArray tagsArray = new JSONArray(tagResponse.body);
                List<String> tags = new ArrayList<>();
                for (int i = 0; i < tagsArray.length(); i++) {
                    String name = tagsArray.getJSONObject(i).optString("name", "");
                    if (!name.isEmpty()) tags.add(name);
                }

                List<String> modules = new ArrayList<>();
                modules.add("(Root Module)");
                HttpFetcher.Response contents = HttpFetcher.getWithRetry(base + "/contents", 2 * 1024 * 1024, 2, null);
                if (contents.isSuccess()) {
                    try {
                        JSONArray contentsArray = new JSONArray(contents.body);
                        for (int i = 0; i < contentsArray.length(); i++) {
                            JSONObject obj = contentsArray.getJSONObject(i);
                            String name = obj.optString("name", "");
                            if ("dir".equals(obj.optString("type")) && !name.isEmpty() && !name.startsWith(".")) {
                                modules.add(name);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                handler.post(() -> {
                    dialog.dismiss();
                    if (rootView == null) return;
                    if (tags.isEmpty()) {
                        githubUser = null;
                        githubRepo = null;
                        updateBottomButtonState();
                        SketchwareUtil.toast("No tags/releases found for this repository.");
                        return;
                    }
                    cardInfo.setVisibility(View.VISIBLE);
                    ((TextView) rootView.findViewById(R.id.tv_gh_repo_name)).setText(user + " / " + repo);
                    ((TextView) rootView.findViewById(R.id.tv_gh_repo_desc)).setText(description);
                    rootView.findViewById(R.id.btn_github_open).setOnClickListener(view ->
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + user + "/" + repo))));
                    AutoCompleteTextView spVersion = rootView.findViewById(R.id.spinner_gh_versions);
                    spVersion.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, tags));
                    spVersion.setText(tags.get(0), false);
                    AutoCompleteTextView spModule = rootView.findViewById(R.id.spinner_gh_modules);
                    spModule.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, modules));
                    spModule.setText(modules.get(0), false);
                    updateBottomButtonState();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    dialog.dismiss();
                    if (rootView == null) return;
                    githubUser = null;
                    githubRepo = null;
                    updateBottomButtonState();
                    SketchwareUtil.toastError("GitHub: " + FailureFormatter.describe(e));
                });
            }
        });
    }

    private String describeGitHubFailure(HttpFetcher.Response response, String subject) {
        if (response.isNetworkFailure()) return "Could not reach GitHub for " + subject + ": " + response.describe();
        if (response.code == 404) return "GitHub could not find " + subject + ". Check the owner and repository name.";
        if (response.code == 403 || response.code == 429) {
            return "GitHub refused the request for " + subject + " (" + response.describe() + "). The anonymous API rate limit may be exhausted; try again later.";
        }
        return "GitHub request for " + subject + " failed: " + response.describe();
    }

    @NonNull
    private List<RepositoryProbe.Repo> loadConfiguredRepositories() {
        List<RepositoryProbe.Repo> repos = new ArrayList<>();
        try {
            File file = new File(Environment.getExternalStorageDirectory(), ".sketchware/libs/repositories.json");
            if (file.isFile()) {
                ArrayList<HashMap<String, Object>> parsed = gson.fromJson(FileUtil.readFile(file.getAbsolutePath()), Helper.TYPE_MAP_LIST);
                if (parsed != null) {
                    for (HashMap<String, Object> entry : parsed) {
                        Object url = entry.get("url");
                        Object name = entry.get("name");
                        if (url instanceof String && !((String) url).isEmpty()) {
                            repos.add(new RepositoryProbe.Repo(name instanceof String ? (String) name : (String) url, (String) url));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (repos.isEmpty()) repos.add(new RepositoryProbe.Repo("Google Maven", "https://maven.google.com"));
        return repos;
    }

    @NonNull
    private synchronized MavenCentralClient getMavenClient() {
        if (mavenClient == null) mavenClient = new MavenCentralClient(loadConfiguredRepositories());
        return mavenClient;
    }

    private void performMavenSearch(String query) {
        if (rootView == null || query == null || query.trim().isEmpty()) return;
        final String trimmed = query.trim();
        lastQuery = trimmed;
        hideKeyboard();
        final int generation = ++searchGeneration;

        LinearProgressIndicator pBar = rootView.findViewById(R.id.search_progress);
        RecyclerView rvSearch = rootView.findViewById(R.id.search_results_recycler_view);
        View errorLayout = rootView.findViewById(R.id.layout_search_error);
        TextView status = rootView.findViewById(R.id.tv_search_status);
        pBar.setVisibility(View.VISIBLE);
        rvSearch.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
        status.setVisibility(View.GONE);

        searchExecutor.execute(() -> {
            try {
                MavenCentralClient.SearchOutcome outcome = getMavenClient().search(trimmed);
                handler.post(() -> {
                    if (rootView == null || generation != searchGeneration) return;
                    pBar.setVisibility(View.GONE);
                    lastResults = outcome.results;
                    lastSource = outcome.source;
                    applyResultFilters();
                });
            } catch (MavenCentralClient.SearchException e) {
                handler.post(() -> showSearchError(generation, e.getMessage()));
            } catch (Exception e) {
                handler.post(() -> showSearchError(generation, "Search failed: " + FailureFormatter.describe(e)));
            }
        });
    }

    private void showSearchError(int generation, String message) {
        if (rootView == null || generation != searchGeneration) return;
        rootView.findViewById(R.id.search_progress).setVisibility(View.GONE);
        lastResults = new ArrayList<>();
        mavenSearchAdapter.setResults(new ArrayList<>());
        rootView.findViewById(R.id.search_results_recycler_view).setVisibility(View.GONE);
        ((TextView) rootView.findViewById(R.id.tv_search_error)).setText(message);
        rootView.findViewById(R.id.layout_search_error).setVisibility(View.VISIBLE);
        updateSelectionUi();
    }

    private void applyResultFilters() {
        if (rootView == null) return;
        List<MavenSearchResult> shown = MavenCentralClient.sortAndFilter(lastResults, sortMode, typeFilter);
        mavenSearchAdapter.setResults(shown);
        RecyclerView rvSearch = rootView.findViewById(R.id.search_results_recycler_view);
        rvSearch.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        TextView status = rootView.findViewById(R.id.tv_search_status);
        if (lastQuery.isEmpty()) {
            status.setVisibility(View.GONE);
        } else if (lastResults.isEmpty()) {
            status.setText("No results for \"" + lastQuery + "\"");
            status.setVisibility(View.VISIBLE);
        } else {
            int hidden = lastResults.size() - shown.size();
            String text = shown.size() + (shown.size() == 1 ? " result" : " results") + " from " + lastSource;
            if (hidden > 0) text += " (" + hidden + " hidden by the type filter)";
            status.setText(text);
            status.setVisibility(View.VISIBLE);
        }
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        if (rootView == null) return;
        View bar = rootView.findViewById(R.id.layout_selection_bar);
        boolean show = mavenSearchAdapter.getItemCount() > 0 || !selectedMaven.isEmpty();
        bar.setVisibility(show ? View.VISIBLE : View.GONE);
        ((TextView) rootView.findViewById(R.id.tv_selection_summary)).setText(selectedMaven.isEmpty()
                ? "Tap libraries to select them"
                : selectedMaven.size() + " selected");
    }

    private void toggleMavenSelection(@NonNull MavenSearchResult result) {
        String key = result.getCoordinateName();
        if (selectedMaven.containsKey(key)) {
            selectedMaven.remove(key);
        } else {
            selectedMaven.put(key, result);
            if (stableOnly) resolveStableDefault(result);
        }
        mavenSearchAdapter.refreshResult(key);
        updateSelectionUi();
        updateBottomButtonState();
    }

    private void selectAllVisible() {
        for (MavenSearchResult result : mavenSearchAdapter.getResults()) {
            String key = result.getCoordinateName();
            if (processingCoordinates.contains(key) || selectedMaven.containsKey(key)) continue;
            selectedMaven.put(key, result);
            if (stableOnly) resolveStableDefault(result);
        }
        mavenSearchAdapter.refreshAll();
        updateSelectionUi();
        updateBottomButtonState();
    }

    private void resolveStableDefault(@NonNull MavenSearchResult result) {
        if (result.hasExplicitVersion() || !MavenVersions.isPreRelease(result.getLatestVersion())) return;
        final String key = result.getCoordinateName();
        metadataExecutor.execute(() -> {
            try {
                MavenCentralClient.VersionList list = getMavenClient().fetchVersions(result.getGroup(), result.getArtifact());
                String stable = list.latestStable();
                handler.post(() -> {
                    if (rootView == null || !selectedMaven.containsKey(key) || result.hasExplicitVersion()) return;
                    if (stable != null) {
                        result.setSelectedVersion(stable);
                        mavenSearchAdapter.refreshResult(key);
                    } else {
                        SketchwareUtil.toast(key + " has no stable version; the latest pre-release stays selected");
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void loadVersions(@NonNull MavenSearchResult result, @NonNull VersionsCallback callback) {
        metadataExecutor.execute(() -> {
            try {
                MavenCentralClient.VersionList list = getMavenClient().fetchVersions(result.getGroup(), result.getArtifact());
                handler.post(() -> {
                    if (rootView != null) callback.onLoaded(list);
                });
            } catch (MavenCentralClient.SearchException e) {
                handler.post(() -> {
                    if (rootView != null) callback.onFailure(e.getMessage());
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (rootView != null) callback.onFailure("Could not load versions: " + FailureFormatter.describe(e));
                });
            }
        });
    }

    private void showVersionPicker(@NonNull MavenSearchResult result) {
        View content = getLayoutInflater().inflate(R.layout.dialog_maven_versions, null, false);
        CheckBox cbStable = content.findViewById(R.id.cb_stable_only);
        LinearProgressIndicator progress = content.findViewById(R.id.versions_progress);
        TextView message = content.findViewById(R.id.tv_versions_message);
        ListView listView = content.findViewById(R.id.versions_list);
        cbStable.setChecked(stableOnly);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(result.getCoordinateName())
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Retry", null)
                .create();

        final List<String> all = new ArrayList<>();
        final List<String> visible = new ArrayList<>();
        final String[] latestHolder = new String[1];

        Runnable render = () -> {
            visible.clear();
            for (String version : all) {
                if (cbStable.isChecked() && (MavenVersions.isPreRelease(version) || MavenVersions.isSnapshot(version))) continue;
                visible.add(version);
            }
            if (visible.isEmpty()) {
                listView.setVisibility(View.GONE);
                message.setText(all.isEmpty() ? "No versions found." : "No stable versions available. Turn off \"Stable versions only\" to see all.");
                message.setVisibility(View.VISIBLE);
                return;
            }
            message.setVisibility(View.GONE);
            List<String> labels = new ArrayList<>();
            for (String version : visible) {
                labels.add(version.equals(latestHolder[0]) ? version + "  (latest)" : version);
            }
            listView.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_single_choice, labels));
            int current = visible.indexOf(result.getSelectedVersion());
            if (current >= 0) {
                listView.setItemChecked(current, true);
                listView.setSelection(current);
            }
            listView.setVisibility(View.VISIBLE);
        };

        Runnable load = new Runnable() {
            @Override
            public void run() {
                progress.setVisibility(View.VISIBLE);
                message.setVisibility(View.GONE);
                listView.setVisibility(View.GONE);
                loadVersions(result, new VersionsCallback() {
                    @Override
                    public void onLoaded(@NonNull MavenCentralClient.VersionList list) {
                        progress.setVisibility(View.GONE);
                        all.clear();
                        all.addAll(list.versions);
                        latestHolder[0] = list.latestOrNewest();
                        render.run();
                    }

                    @Override
                    public void onFailure(@NonNull String failure) {
                        progress.setVisibility(View.GONE);
                        message.setText(failure);
                        message.setVisibility(View.VISIBLE);
                    }
                });
            }
        };

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visible.size()) return;
            String chosen = visible.get(position);
            result.setSelectedVersion(chosen);
            mavenSearchAdapter.refreshResult(result.getCoordinateName());
            dialog.dismiss();
        });
        cbStable.setOnCheckedChangeListener((button, checked) -> {
            if (!all.isEmpty()) render.run();
        });

        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> load.run()));
        dialog.show();
        load.run();
    }

    private void startMavenSelectionDownload() {
        if (selectedMaven.isEmpty()) return;
        boolean skip = skipSubDepsChecked();
        boolean sources = rootView != null && ((CheckBox) rootView.findViewById(R.id.cb_include_sources_maven)).isChecked();
        List<DownloadRequest> requests = new ArrayList<>();
        for (MavenSearchResult selected : selectedMaven.values()) {
            String version = selected.getSelectedVersion();
            boolean upgrade = false;
            String oldFolder = null;
            LocalLibrary installed = LocalLibrariesUtil.findInstalledLibraryByGroupArtifact(selected.getGroup(), selected.getArtifact());
            if (installed != null) {
                String installedDependency = installed.getMavenDependency();
                String[] parts = installedDependency != null ? installedDependency.split(":") : new String[0];
                String installedVersion = parts.length == 3 ? parts[2] : "unknown";
                if (!installedVersion.equals(version)) {
                    upgrade = true;
                    oldFolder = installed.getName();
                }
            }
            requests.add(new DownloadRequest(selected.getGroup(), selected.getArtifact(), version, selected.getFullCoordinate(),
                    skip, sources, false, upgrade, oldFolder));
        }
        confirmAndStart(requests);
    }

    private void initManualDownloadFlow(String dependencyStr, boolean skipSubDeps, boolean includeSources) {
        if (dependencyStr == null || dependencyStr.trim().isEmpty()) {
            SketchwareUtil.toastError("Dependency cannot be empty");
            return;
        }
        final String input = dependencyStr.trim();
        boolean isDirectUrl = input.startsWith("http://") || input.startsWith("https://");
        if (isDirectUrl) {
            List<DownloadRequest> single = new ArrayList<>();
            single.add(new DownloadRequest(input, "", "", input, skipSubDeps, includeSources, true, isUpgradeMode, oldLibraryFolder));
            confirmAndStart(single);
            return;
        }

        String[] parts = input.split(":", -1);
        if (parts.length == 4 || parts.length == 5) {
            SketchwareUtil.toastError("Classifiers are not supported. Use group:artifact:version");
            return;
        }
        if (parts.length != 3) {
            SketchwareUtil.toastError("Invalid format. Use group:artifact:version OR a full URL");
            return;
        }
        final String group = parts[0].trim();
        final String artifact = parts[1].trim();
        final String version = parts[2].trim();
        if (group.isEmpty() || artifact.isEmpty() || version.isEmpty()) {
            SketchwareUtil.toastError("Group, artifact and version must all be filled in");
            return;
        }
        if (!PART_PATTERN.matcher(group).matches() || !PART_PATTERN.matcher(artifact).matches()) {
            SketchwareUtil.toastError("Group and artifact may only contain letters, digits, '.', '_' and '-'");
            return;
        }
        if (!version.equals("+") && !VERSION_PATTERN.matcher(version).matches()) {
            SketchwareUtil.toastError("Invalid version '" + version + "'. Use one fixed version such as 1.2.3");
            return;
        }

        if (version.equals("+")) {
            SketchwareUtil.toast("Looking up the newest version of " + group + ":" + artifact + "...");
            metadataExecutor.execute(() -> {
                try {
                    MavenCentralClient.VersionList list = getMavenClient().fetchVersions(group, artifact);
                    String resolved = list.latestOrNewest();
                    handler.post(() -> {
                        if (rootView == null) return;
                        EditText manual = rootView.findViewById(R.id.dependency_input);
                        if (manual != null && manual.getText().toString().trim().equals(input)) {
                            manual.setText(group + ":" + artifact + ":" + resolved);
                        }
                        prepareManualRequest(group, artifact, resolved, skipSubDeps, includeSources);
                    });
                } catch (Exception e) {
                    String message = e instanceof MavenCentralClient.SearchException ? e.getMessage() : FailureFormatter.describe(e);
                    handler.post(() -> {
                        if (rootView != null) SketchwareUtil.showAnErrorOccurredDialog(getActivity(),
                                "Could not resolve the newest version of " + group + ":" + artifact + ".\n" + message);
                    });
                }
            });
            return;
        }
        prepareManualRequest(group, artifact, version, skipSubDeps, includeSources);
    }

    private void prepareManualRequest(String group, String artifact, String version, boolean skipSubDeps, boolean includeSources) {
        List<DownloadRequest> single = new ArrayList<>();
        single.add(new DownloadRequest(group, artifact, version, group + ":" + artifact + ":" + version,
                skipSubDeps, includeSources, false, isUpgradeMode, oldLibraryFolder));
        confirmAndStart(single);
    }

    private void confirmAndStart(List<DownloadRequest> requested) {
        Map<String, DownloadRequest> unique = new LinkedHashMap<>();
        Map<String, String> versionByModule = new HashMap<>();
        for (DownloadRequest request : requested) {
            if (!request.directUrl) {
                String module = request.group + ":" + request.artifact;
                String previous = versionByModule.put(module, request.version);
                if (previous != null && !previous.equals(request.version)) {
                    SketchwareUtil.toastError("Two versions of " + module + " were selected (" + previous + " and " + request.version + "). Keep only one.");
                    return;
                }
            }
            unique.putIfAbsent(request.key(), request);
        }
        List<DownloadRequest> requests = new ArrayList<>(unique.values());

        List<DownloadRequest> builtInProvided = new ArrayList<>();
        StringBuilder builtInText = new StringBuilder();
        if (!notAssociatedWithProject && scId != null) {
            for (DownloadRequest request : requests) {
                if (request.directUrl) continue;
                BuiltInArtifacts.Match match = BuiltInArtifacts.findSatisfying(scId, request.group, request.artifact, request.version);
                if (match != null) {
                    builtInProvided.add(request);
                    builtInText.append("\n\u2022 ").append(request.group).append(':').append(request.artifact).append(':').append(request.version)
                            .append(" \u2192 built-in ").append(match.libraryName);
                }
            }
        }

        if (!builtInProvided.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Built-in Library Detected")
                    .setMessage("Neo already provides these libraries in the same or a newer version:" + builtInText
                            + "\n\nDownloading them again can cause 'Duplicate Classes' errors.")
                    .setPositiveButton("Skip built-in", (d, w) -> {
                        List<DownloadRequest> remaining = new ArrayList<>(requests);
                        remaining.removeAll(builtInProvided);
                        if (remaining.isEmpty()) {
                            SketchwareUtil.toast("Nothing to download: everything selected is already built in");
                            return;
                        }
                        showConfirmation(remaining);
                    })
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Download anyway", (d, w) -> showConfirmation(requests))
                    .show();
            return;
        }
        showConfirmation(requests);
    }

    private void showConfirmation(List<DownloadRequest> requests) {
        boolean anyUpgrade = false;
        StringBuilder message = new StringBuilder();
        if (requests.size() == 1) {
            DownloadRequest only = requests.get(0);
            if (only.directUrl) {
                message.append("Are you sure you want to download the library directly from this URL?\n\n").append(only.requestString);
            } else if (only.skipSubDeps) {
                message.append("Are you sure you want to download ").append(only.requestString).append('?');
            } else {
                message.append("Are you sure you want to download ").append(only.requestString).append(" and its sub-dependencies?");
            }
            if (only.upgrade) {
                anyUpgrade = true;
                message.insert(0, "Old version of this library (" + only.oldFolder + ") will be safely replaced with " + only.requestString + ".\n\n");
            }
        } else {
            message.append("Download these ").append(requests.size()).append(" libraries")
                    .append(requests.get(0).skipSubDeps ? "?" : " and their sub-dependencies?").append('\n');
            for (DownloadRequest request : requests) {
                message.append("\n\u2022 ").append(request.requestString);
                if (request.upgrade) {
                    anyUpgrade = true;
                    message.append("  (replaces ").append(request.oldFolder).append(')');
                }
            }
        }
        final boolean upgradeTitle = anyUpgrade;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(upgradeTitle ? "Confirm Upgrade" : "Confirm Download")
                .setMessage(message.toString())
                .setPositiveButton(upgradeTitle ? "Upgrade" : "Download", (dialog, which) -> {
                    isUpgradeMode = false;
                    oldLibraryFolder = null;
                    startQueue(requests, upgradeTitle);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startQueue(List<DownloadRequest> requests, boolean upgradeHeader) {
        if (rootView == null || queueRunning) return;
        queueRunning = true;
        cancelRequested = false;
        currentQueue = new ArrayList<>(requests);
        queueTotal = requests.size();
        queueResults.clear();
        downloadItems.clear();
        dependencyAdapter.setDependencies(new ArrayList<>());
        for (DownloadRequest request : requests) processingCoordinates.add(request.coordinateName());
        mavenSearchAdapter.refreshAll();

        ((android.widget.ViewFlipper) rootView.findViewById(R.id.view_flipper_main)).setDisplayedChild(1);
        TextView header = rootView.findViewById(R.id.tv_processing_header);
        header.setText(upgradeHeader ? "Upgrading Dependency..." : "Processing Dependencies...");
        TextView sub = rootView.findViewById(R.id.tv_processing_sub);
        sub.setText(requests.size() == 1 ? "Resolving components and sub-modules" : "0 of " + requests.size() + " libraries finished");
        LinearProgressIndicator overall = rootView.findViewById(R.id.overall_progress);
        overall.setIndeterminate(true);
        rootView.findViewById(R.id.btn_retry_failed).setVisibility(View.GONE);
        MaterialButton cancel = rootView.findViewById(R.id.btn_cancel_processing);
        cancel.setText("Cancel Operation");
        cancel.setEnabled(true);
        cancel.setOnClickListener(v -> requestCancel());
        setCancelable(false);
        updateBottomButtonState();

        final List<DownloadRequest> queue = new ArrayList<>(requests);
        downloadExecutor.execute(() -> {
            boolean assetsReady = true;
            String assetsError = null;
            try {
                BuiltInLibraries.maybeExtractAndroidJar((message, progress) -> handler.post(() -> {
                    if (rootView != null) ((LinearProgressIndicator) rootView.findViewById(R.id.overall_progress)).setIndeterminate(true);
                }));
                BuiltInLibraries.maybeExtractCoreLambdaStubsJar();
            } catch (Throwable t) {
                assetsReady = false;
                assetsError = "Could not prepare the compile assets needed for dexing: " + FailureFormatter.describe(t);
            }
            for (int i = 0; i < queue.size(); i++) {
                DownloadRequest request = queue.get(i);
                if (cancelRequested || Thread.currentThread().isInterrupted()) {
                    synchronized (queueResults) {
                        queueResults.add(new RequestResult(request, false, true, "Cancelled", new ArrayList<>()));
                    }
                    continue;
                }
                RequestResult result = assetsReady
                        ? runSingleRequest(request)
                        : new RequestResult(request, false, false, assetsError, new ArrayList<>());
                synchronized (queueResults) {
                    queueResults.add(result);
                }
                final int finished = i + 1;
                handler.post(() -> onRequestFinished(request, finished));
            }
            handler.post(this::onQueueFinished);
        });
    }

    private void requestCancel() {
        cancelRequested = true;
        synchronized (resolverLock) {
            if (activeResolver != null) activeResolver.cancel();
        }
        if (rootView == null) return;
        MaterialButton cancel = rootView.findViewById(R.id.btn_cancel_processing);
        cancel.setText("Cancelling...");
        cancel.setEnabled(false);
    }

    private void onRequestFinished(DownloadRequest request, int finished) {
        processingCoordinates.remove(request.coordinateName());
        if (rootView == null) return;
        mavenSearchAdapter.refreshResult(request.coordinateName());
        if (queueTotal > 1) {
            ((TextView) rootView.findViewById(R.id.tv_processing_sub)).setText(finished + " of " + queueTotal + " libraries finished");
        }
    }

    @NonNull
    private RequestResult runSingleRequest(DownloadRequest request) {
        final DependencyResolver resolver = new DependencyResolver(request.group, request.artifact, request.version, request.skipSubDeps, buildSettings);
        if (!notAssociatedWithProject && scId != null && !request.directUrl) {
            resolver.setProvidedBy(artifact -> {
                BuiltInArtifacts.Match match = BuiltInArtifacts.findSatisfying(scId, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                return match == null ? null : match.libraryName;
            });
        }
        synchronized (resolverLock) {
            activeResolver = resolver;
        }

        final List<String>[] completed = new List[1];
        final ResolverFailure[] failure = new ResolverFailure[1];
        final long[] lastProgress = new long[1];

        try {
            resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                @Override
                public void artifactFound(@NonNull Artifact artifact) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        DependencyDownloadItem item = findOrCreateDependencyItem(artifact);
                        item.setState(DependencyDownloadItem.DownloadState.RESOLVING);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onResolving(@NonNull Artifact artifact, @NonNull Artifact dependency) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        DependencyDownloadItem item = findOrCreateDependencyItem(dependency);
                        item.setState(DependencyDownloadItem.DownloadState.RESOLVING);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onResolutionComplete(@NonNull Artifact dep) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        updateDependencyState(dep, DependencyDownloadItem.DownloadState.COMPLETED);
                        updateOverallProgress();
                    });
                }

                @Override
                public void onCacheHit(@NonNull Artifact artifact) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        DependencyDownloadItem item = findOrCreateDependencyItem(artifact);
                        item.setNote("Completed (cached)");
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onProvided(@NonNull Artifact artifact, @NonNull String provider) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        DependencyDownloadItem item = findOrCreateDependencyItem(artifact);
                        item.setNote("Provided by Neo built-in " + provider + " (not downloaded)");
                        item.setState(DependencyDownloadItem.DownloadState.COMPLETED);
                        dependencyAdapter.updateDependency(item);
                        updateOverallProgress();
                    });
                }

                @Override
                public void onDependencySkipped(@NonNull Artifact artifact, @NonNull String reason) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        DependencyDownloadItem item = findOrCreateDependencyItem(artifact);
                        item.setNote("Skipped: " + reason);
                        item.setState(DependencyDownloadItem.DownloadState.COMPLETED);
                        dependencyAdapter.updateDependency(item);
                        updateOverallProgress();
                    });
                }

                @Override
                public void onDownloadStart(@NonNull Artifact artifact) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        DependencyDownloadItem item = findOrCreateDependencyItem(artifact);
                        item.setState(DependencyDownloadItem.DownloadState.DOWNLOADING);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onDownloadProgress(@NonNull Artifact artifact, long bytes, long total) {
                    long now = SystemClock.uptimeMillis();
                    boolean last = total > 0 && bytes >= total;
                    if (!last && now - lastProgress[0] < PROGRESS_POST_INTERVAL_MS) return;
                    lastProgress[0] = now;
                    handler.post(() -> {
                        if (rootView == null) return;
                        DependencyDownloadItem item = findOrCreateDependencyItem(artifact);
                        item.setProgress(bytes, total);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void unzipping(@NonNull Artifact artifact) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        updateDependencyState(artifact, DependencyDownloadItem.DownloadState.UNZIPPING);
                    });
                }

                @Override
                public void dexing(@NonNull Artifact artifact) {
                    handler.post(() -> {
                        if (rootView == null) return;
                        updateDependencyState(artifact, DependencyDownloadItem.DownloadState.DEXING);
                    });
                }

                @Override
                public void onDownloadError(@NonNull Artifact artifact, @NonNull Throwable error) {
                    handler.post(() -> markItemError(artifact, FailureFormatter.describe(error)));
                }

                @Override
                public void dexingFailed(@NonNull Artifact artifact, @NonNull Exception e) {
                    handler.post(() -> markItemError(artifact, FailureFormatter.describe(e)));
                }

                @Override
                public void onFailure(@NonNull ResolverFailure resolverFailure) {
                    failure[0] = resolverFailure;
                    handler.post(() -> markCoordinateError(resolverFailure));
                }

                @Override
                public void onTaskCompleted(@NonNull List<String> artifacts) {
                    completed[0] = new ArrayList<>(artifacts);
                }
            });
        } catch (Throwable t) {
            failure[0] = new ResolverFailure(ResolverFailure.Stage.UNEXPECTED, request.key(), FailureFormatter.describe(t), t);
            handler.post(() -> markCoordinateError(failure[0]));
        } finally {
            synchronized (resolverLock) {
                activeResolver = null;
            }
        }

        if (failure[0] != null) {
            boolean cancelled = failure[0].stage == ResolverFailure.Stage.CANCELLED;
            return new RequestResult(request, false, cancelled, failure[0].toUserMessage(), new ArrayList<>());
        }
        if (completed[0] == null || completed[0].isEmpty()) {
            ResolverFailure missing = new ResolverFailure(ResolverFailure.Stage.UNEXPECTED, request.key(),
                    "The resolver finished without reporting a result.", null);
            handler.post(() -> markCoordinateError(missing));
            return new RequestResult(request, false, false, missing.toUserMessage(), new ArrayList<>());
        }

        List<String> folders = completed[0];
        String postError = registerDownloadedLibraries(request, folders);
        if (postError != null) {
            return new RequestResult(request, false, false, postError, folders);
        }
        if (request.includeSources && !request.directUrl) {
            final String sourcesStatus = downloadSources(resolver);
            handler.post(() -> {
                if (rootView != null && sourcesStatus != null) SketchwareUtil.toast(sourcesStatus);
            });
        }
        return new RequestResult(request, true, false, "Completed", folders);
    }

    @Nullable
    private String registerDownloadedLibraries(DownloadRequest request, List<String> folders) {
        try {
            if (!request.directUrl) {
                for (String folder : folders) {
                    LocalLibrariesUtil.writeArtifactMetadata(folder, request.requestString);
                }
            }
            LocalLibrariesUtil.clearCache();
            if (!notAssociatedWithProject) {
                String content = FileUtil.readFile(localLibFile);
                ArrayList<HashMap<String, Object>> enabledLibs = content == null || content.trim().isEmpty()
                        ? null : gson.fromJson(content, Helper.TYPE_MAP_LIST);
                if (enabledLibs == null) enabledLibs = new ArrayList<>();

                if (request.upgrade && request.oldFolder != null) {
                    for (int i = 0; i < enabledLibs.size(); i++) {
                        Object name = enabledLibs.get(i).get("name");
                        if (name != null && name.toString().equals(request.oldFolder)) {
                            enabledLibs.remove(i);
                            break;
                        }
                    }
                }
                for (String folder : folders) {
                    boolean exists = false;
                    for (Map<String, Object> libMap : enabledLibs) {
                        Object name = libMap.get("name");
                        if (name != null && name.toString().equals(folder)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        enabledLibs.add(LocalLibrariesUtil.createLibraryMap(folder, request.directUrl ? request.group : request.requestString));
                    }
                }
                FileUtil.writeFile(localLibFile, gson.toJson(enabledLibs));
            }
            return null;
        } catch (Exception e) {
            return "The library was downloaded but could not be added to the project: " + FailureFormatter.describe(e);
        }
    }

    @Nullable
    private String downloadSources(DependencyResolver resolver) {
        Artifact root = resolver.getRootArtifact();
        String folder = resolver.getRootFolder();
        if (root == null || folder == null) return "Sources skipped: the main artifact is unknown";
        Repository repository = root.getRepository();
        if (repository == null) return "Sources skipped: no repository is known for " + root;
        String base = repository.getURL();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String url = base + "/" + root.getGroupId().replace('.', '/') + "/" + root.getArtifactId() + "/" + root.getVersion()
                + "/" + root.getArtifactId() + "-" + root.getVersion() + "-sources.jar";
        File out = new File(FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/" + folder,
                root.getArtifactId() + "-" + root.getVersion() + "-sources.jar");
        try {
            FileDownloader.download(url, out, null, null);
            return "Sources downloaded for " + root.getArtifactId();
        } catch (IOException e) {
            return "Sources are not available for " + root + ": " + e.getMessage();
        }
    }

    private void onQueueFinished() {
        queueRunning = false;
        if (rootView == null) return;
        setCancelable(true);
        List<RequestResult> results;
        synchronized (queueResults) {
            results = new ArrayList<>(queueResults);
        }
        List<String> successFolders = new ArrayList<>();
        List<RequestResult> failed = new ArrayList<>();
        int cancelledCount = 0;
        for (RequestResult result : results) {
            if (result.success) {
                for (String folder : result.folders) {
                    if (!successFolders.contains(folder)) successFolders.add(folder);
                }
            } else if (result.cancelled) {
                cancelledCount++;
            } else {
                failed.add(result);
            }
        }
        for (DownloadRequest request : currentQueue) processingCoordinates.remove(request.coordinateName());
        mavenSearchAdapter.refreshAll();
        updateBottomButtonState();

        if (!successFolders.isEmpty() && onLibraryDownloadedTask != null) {
            onLibraryDownloadedTask.invoke(successFolders);
        }

        LinearProgressIndicator overall = rootView.findViewById(R.id.overall_progress);
        overall.setIndeterminate(false);
        overall.setProgress(100);

        if (failed.isEmpty() && cancelledCount == 0) {
            if (!successFolders.isEmpty()) {
                selectedMaven.clear();
                dismiss();
            } else {
                returnToInput();
            }
            return;
        }

        if (failed.isEmpty()) {
            returnToInput();
            SketchwareUtil.toast(successFolders.isEmpty() ? "Cancelled" : "Cancelled. " + successFolders.size() + " library folder(s) were already added");
            return;
        }

        if (queueTotal == 1) {
            String message = failed.get(0).message;
            returnToInput();
            SketchwareUtil.showAnErrorOccurredDialog(getActivity(), message);
            return;
        }

        int okCount = 0;
        for (RequestResult result : results) {
            if (result.success) okCount++;
        }
        ((TextView) rootView.findViewById(R.id.tv_processing_header)).setText(okCount + " of " + queueTotal + " downloaded");
        StringBuilder detail = new StringBuilder();
        for (RequestResult result : failed) {
            if (detail.length() > 0) detail.append('\n');
            detail.append(result.request.requestString).append(": ").append(firstLine(result.message));
        }
        ((TextView) rootView.findViewById(R.id.tv_processing_sub)).setText(failed.size() + " failed:\n" + detail);

        final List<DownloadRequest> retry = new ArrayList<>();
        for (RequestResult result : failed) retry.add(result.request);
        for (DownloadRequest request : currentQueue) {
            RequestResult match = null;
            for (RequestResult result : results) {
                if (result.request == request) match = result;
            }
            if (match != null && match.cancelled) retry.add(request);
        }
        MaterialButton retryButton = rootView.findViewById(R.id.btn_retry_failed);
        retryButton.setVisibility(View.VISIBLE);
        retryButton.setOnClickListener(v -> startQueue(retry, false));
        MaterialButton close = rootView.findViewById(R.id.btn_cancel_processing);
        close.setText("Close");
        close.setEnabled(true);
        close.setOnClickListener(v -> dismiss());
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }

    private void returnToInput() {
        if (rootView == null) return;
        ((android.widget.ViewFlipper) rootView.findViewById(R.id.view_flipper_main)).setDisplayedChild(0);
        setCancelable(true);
        updateBottomButtonState();
    }

    private DependencyDownloadItem findOrCreateDependencyItem(Artifact artifact) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact) || item.getName().equals(artifact.toString())) return item;
        }
        DependencyDownloadItem newItem = new DependencyDownloadItem(artifact);
        downloadItems.add(newItem);
        dependencyAdapter.addDependency(newItem);
        return newItem;
    }

    private void updateDependencyState(Artifact artifact, DependencyDownloadItem.DownloadState state) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact) || item.getName().equals(artifact.toString())) {
                item.setState(state);
                dependencyAdapter.updateDependency(item);
                break;
            }
        }
    }

    private void markItemError(Artifact artifact, String message) {
        if (rootView == null) return;
        DependencyDownloadItem item = findOrCreateDependencyItem(artifact);
        item.setError(message);
        dependencyAdapter.updateDependency(item);
    }

    private void markCoordinateError(ResolverFailure failure) {
        if (rootView == null || failure.coordinate == null) return;
        String coordinate = failure.coordinate;
        Artifact artifact;
        String[] parts = coordinate.split(":", -1);
        if (parts.length == 3) {
            artifact = new Artifact(parts[0], parts[1], parts[2], null, "jar");
        } else {
            artifact = new Artifact("direct", coordinate, "", null, "jar");
        }
        DependencyDownloadItem item = null;
        for (DependencyDownloadItem existing : downloadItems) {
            if (existing.getName().equals(coordinate)) item = existing;
        }
        if (item == null) item = findOrCreateDependencyItem(artifact);
        item.setError(firstLine(failure.message));
        dependencyAdapter.updateDependency(item);
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

    public void setOnLibraryDownloadedTask(OnLibraryDownloadedTask onLibraryDownloadedTask) {
        this.onLibraryDownloadedTask = onLibraryDownloadedTask;
    }

    public interface OnLibraryDownloadedTask {
        void invoke(List<String> dependencies);
    }
}
