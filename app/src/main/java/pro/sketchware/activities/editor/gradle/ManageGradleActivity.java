package pro.sketchware.activities.editor.gradle;

import static com.besome.sketch.Config.VAR_DEFAULT_MIN_SDK_VERSION;
import static com.besome.sketch.Config.VAR_DEFAULT_TARGET_SDK_VERSION;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import a.a.a.Lx;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yq;
import dev.aldi.sayuti.editor.manage.MavenSearchResult;
import dev.aldi.sayuti.editor.manage.MavenSearchResultAdapter;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.code.CodeEditorLanguages;
import mod.sketchlibx.importer.GradleDependency;
import mod.sketchlibx.importer.GradleParser;
import mod.sketchlibx.importer.ParsedGradle;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogGradleDependencyBinding;
import pro.sketchware.databinding.ManageGradleActivityBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ManageGradleActivity extends BaseAppCompatActivity {

    private static final String FILE_APP_BUILD = CustomGradleBuildManager.FILE_APP_BUILD;
    private static final String FILE_BUILD = CustomGradleBuildManager.FILE_BUILD;
    private static final String FILE_SETTINGS = CustomGradleBuildManager.FILE_SETTINGS;
    private static final String FILE_PROPERTIES = CustomGradleBuildManager.FILE_PROPERTIES;

    private static final String STATE_TAB = "tab";
    private static final String STATE_FILE = "file";
    private static final String STATE_BUFFERS = "buffers";
    private static final int MENU_REFRESH = 100;
    private static final int MENU_CUSTOM = 101;
    private static final int MENU_LOG = 102;
    private static final int MENU_RESET = 103;
    private static final int MENU_SAVE = 104;
    private static final int MENU_SYNC = 105;

    private static final String[] CONFIGURATIONS = {"implementation", "api", "kapt", "compileOnly", "runtimeOnly", "annotationProcessor"};
    private static final Pattern COORDINATE = Pattern.compile("^[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+$");

    private final String[] gradleFiles = {FILE_APP_BUILD, FILE_BUILD, FILE_SETTINGS, FILE_PROPERTIES};
    private final Map<String, String> buffers = new HashMap<>();
    private final Map<String, String> saved = new HashMap<>();

    private String sc_id;
    private ProjectSettings projectSettings;
    private BuildSettings buildSettings;
    private ManageGradleActivityBinding binding;
    private GradleManagerViewModel viewModel;
    private GradleDependencyAdapter dependencyAdapter;
    private String customGradleDir;
    private String currentFile = FILE_APP_BUILD;
    private boolean loadingEditor;
    private List<GradleSyncEngine.Row> allRows = new ArrayList<>();
    private String javaChoice;
    private boolean lastDirty;
    private boolean imeVisible;
    private boolean updatingSkipSwitches;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageGradleActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            applyKeyboardState(insets.isVisible(WindowInsetsCompat.Type.ime()));
            return WindowInsetsCompat.CONSUMED;
        });

        sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }
        projectSettings = new ProjectSettings(sc_id);
        buildSettings = new BuildSettings(sc_id);
        customGradleDir = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_gradle/";

        viewModel = new ViewModelProvider(this).get(GradleManagerViewModel.class);
        viewModel.init(sc_id);

        int tab = 0;
        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt(STATE_TAB, 0);
            String file = savedInstanceState.getString(STATE_FILE);
            if (file != null) currentFile = file;
            Bundle stored = savedInstanceState.getBundle(STATE_BUFFERS);
            if (stored != null) {
                for (String key : stored.keySet()) buffers.put(key, stored.getString(key));
            }
        }

        setupToolbar();
        setupTabs(tab);
        setupEditor();
        setupFileChips();
        setupDependencies();
        setupBuildSettings();
        observeViewModel();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (anyDirty()) {
                    new MaterialAlertDialogBuilder(ManageGradleActivity.this)
                            .setTitle("Unsaved changes")
                            .setMessage("Discard your unsaved Gradle edits?")
                            .setPositiveButton("Discard", (d, w) -> finish())
                            .setNegativeButton("Keep editing", null)
                            .show();
                } else {
                    finish();
                }
            }
        });

        if (isCustomGradleEnabled()) ensureGradleFilesExist(false);
        loadFile(currentFile, false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        stashEditor();
        outState.putInt(STATE_TAB, binding.tabs.getSelectedTabPosition());
        outState.putString(STATE_FILE, currentFile);
        Bundle stored = new Bundle();
        for (String name : gradleFiles) {
            if (isDirty(name)) stored.putString(name, buffers.get(name));
        }
        outState.putBundle(STATE_BUFFERS, stored);
    }

    @Override
    public void onDestroy() {
        if (binding != null) {
            try {
                binding.editor.release();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Gradle Manager");
            getSupportActionBar().setSubtitle("Project " + sc_id);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupTabs(int selected) {
        binding.tabs.addTab(binding.tabs.newTab().setText("Gradle Sync").setIcon(R.drawable.ic_mtrl_gradle));
        binding.tabs.addTab(binding.tabs.newTab().setText("Dependencies").setIcon(R.drawable.ic_mtrl_dependency));
        binding.tabs.addTab(binding.tabs.newTab().setText("Build Settings").setIcon(R.drawable.ic_mtrl_configuration));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        TabLayout.Tab tab = binding.tabs.getTabAt(Math.max(0, Math.min(2, selected)));
        if (tab != null) tab.select();
        showTab(binding.tabs.getSelectedTabPosition());
    }

    private void showTab(int position) {
        binding.tabSync.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.tabDeps.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.tabSettings.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }

    private void setupEditor() {
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.getText().addContentListener(new ContentListener() {
            @Override
            public void beforeReplace(Content content) {
            }

            @Override
            public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
                onEditorChanged();
            }

            @Override
            public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
                onEditorChanged();
            }
        });
        binding.btnUndo.setOnClickListener(v -> {
            if (binding.editor.canUndo()) binding.editor.undo();
            refreshEditorState();
        });
        binding.btnRedo.setOnClickListener(v -> {
            if (binding.editor.canRedo()) binding.editor.redo();
            refreshEditorState();
        });
        binding.btnOpenDir.setOnClickListener(v -> openDirectory());
    }

    private void onEditorChanged() {
        if (loadingEditor) return;
        refreshEditorState();
    }

    private void applyKeyboardState(boolean visible) {
        if (visible == imeVisible) return;
        imeVisible = visible;
        int visibility = visible ? View.GONE : View.VISIBLE;
        binding.statusCard.setVisibility(visibility);
        binding.filesHeader.setVisibility(visibility);
    }

    private void refreshEditorState() {
        boolean dirty = isDirty(currentFile);
        boolean anyDirty = dirty || otherFilesDirty();
        if (anyDirty != lastDirty) {
            lastDirty = anyDirty;
            invalidateOptionsMenu();
        }
        binding.editorState.setText(dirty ? "Modified" : "Editable");
        binding.editorState.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColor(this,
                dirty ? com.google.android.material.R.attr.colorTertiaryContainer : com.google.android.material.R.attr.colorSecondaryContainer)));
        binding.editorState.setTextColor(ThemeUtils.getColor(this,
                dirty ? com.google.android.material.R.attr.colorOnTertiaryContainer : com.google.android.material.R.attr.colorOnSecondaryContainer));
        binding.btnUndo.setEnabled(binding.editor.canUndo());
        binding.btnRedo.setEnabled(binding.editor.canRedo());
        String lang = languageLabel;
        int line = binding.editor.getCursor().getLeftLine() + 1;
        int column = binding.editor.getCursor().getLeftColumn() + 1;
        binding.editorPosition.setText(String.format(Locale.US, "Line %d, Column %d  |  UTF-8  |  %s", line, column, lang));
    }

    private String languageLabel = "";

    private String displayName(String file) {
        return file.equals(FILE_APP_BUILD) ? "app/build.gradle" : file;
    }

    private void setupFileChips() {
        binding.fileChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            String target = id == R.id.chip_app ? FILE_APP_BUILD
                    : id == R.id.chip_build ? FILE_BUILD
                    : id == R.id.chip_settings ? FILE_SETTINGS
                    : FILE_PROPERTIES;
            if (!target.equals(currentFile)) {
                stashEditor();
                loadFile(target, false);
            }
        });
    }

    private void syncChipSelection() {
        int id = currentFile.equals(FILE_APP_BUILD) ? R.id.chip_app
                : currentFile.equals(FILE_BUILD) ? R.id.chip_build
                : currentFile.equals(FILE_SETTINGS) ? R.id.chip_settings
                : R.id.chip_properties;
        if (binding.fileChips.getCheckedChipId() != id) binding.fileChips.check(id);
    }

    private void stashEditor() {
        if (binding == null || currentFile == null || !saved.containsKey(currentFile)) return;
        buffers.put(currentFile, binding.editor.getText().toString());
    }

    private void loadFile(String name, boolean forceDisk) {
        String path = customGradleDir + name;
        if (!FileUtil.isExistFile(path) || FileUtil.readFile(path).trim().isEmpty()) {
            FileUtil.makeDir(customGradleDir);
            FileUtil.writeFile(path, getDefaultContent(name));
        }
        String disk = FileUtil.readFile(path);
        saved.put(name, disk);
        if (forceDisk) buffers.remove(name);
        String text = buffers.containsKey(name) ? buffers.get(name) : disk;
        currentFile = name;
        syncChipSelection();
        loadingEditor = true;
        try {
            io.github.rosemoe.sora.widget.CodeEditor editor = binding.editor;
            CodeEditorLanguages.LanguageSpec spec = CodeEditorLanguages.resolveLanguageSpec(displayName(name).equals("app/build.gradle") ? "build.gradle" : name);
            editor.setText(text);
            SrcCodeEditor.applyLanguageSpec(this, editor, spec);
            languageLabel = spec.label;
            SrcCodeEditor.loadCESettings(this, editor, "act", false);
        } finally {
            loadingEditor = false;
        }
        binding.editorTitle.setText(displayName(name));
        refreshEditorState();
    }

    private boolean isDirty(String name) {
        String base = saved.get(name);
        if (base == null) return false;
        String current;
        if (name.equals(currentFile) && binding != null) current = binding.editor.getText().toString();
        else current = buffers.get(name);
        return current != null && !current.equals(base);
    }

    private boolean otherFilesDirty() {
        for (String name : gradleFiles) {
            if (name.equals(currentFile)) continue;
            String base = saved.get(name);
            String current = buffers.get(name);
            if (base != null && current != null && !current.equals(base)) return true;
        }
        return false;
    }

    private boolean anyDirty() {
        for (String name : gradleFiles) {
            if (isDirty(name)) return true;
        }
        return false;
    }

    private boolean saveFile(String name, boolean announce) {
        String text = name.equals(currentFile) ? binding.editor.getText().toString() : buffers.get(name);
        if (text == null) return true;
        FileUtil.writeFile(customGradleDir + name, text);
        saved.put(name, text);
        buffers.put(name, text);
        refreshEditorState();
        viewModel.reload();
        if (announce) {
            List<GradleFileValidator.Issue> issues = GradleFileValidator.validateContent(name, text, new File(customGradleDir));
            GradleFileValidator.Issue error = null;
            for (GradleFileValidator.Issue issue : issues) {
                if (issue.severity == GradleFileValidator.Severity.ERROR) {
                    error = issue;
                    break;
                }
            }
            if (error != null) SketchwareUtil.toastError("Saved with error: " + error);
            else SketchwareUtil.toast("Saved " + displayName(name));
        }
        return true;
    }

    private void saveDirtyFiles() {
        stashEditor();
        List<String> names = new ArrayList<>();
        for (String name : gradleFiles) {
            if (isDirty(name)) names.add(name);
        }
        if (names.isEmpty()) return;
        for (String name : names) saveFile(name, false);
        List<GradleFileValidator.Issue> issues = GradleFileValidator.validate(new File(customGradleDir));
        GradleFileValidator.Issue error = null;
        for (GradleFileValidator.Issue issue : issues) {
            if (issue.severity == GradleFileValidator.Severity.ERROR) {
                error = issue;
                break;
            }
        }
        if (error != null) SketchwareUtil.toastError("Saved with error: " + error);
        else SketchwareUtil.toast(names.size() == 1 ? "Saved " + displayName(names.get(0)) : "Saved " + names.size() + " files");
        invalidateOptionsMenu();
    }

    private void saveAllDirty() {
        stashEditor();
        for (String name : gradleFiles) {
            if (isDirty(name)) saveFile(name, false);
        }
    }

    private void onSyncClicked() {
        if (viewModel.isRunning()) {
            viewModel.cancelSync();
            return;
        }
        requestSync(null);
    }

    private void requestSync(@Nullable Set<String> only) {
        if (viewModel.isRunning()) {
            SketchwareUtil.toast("A sync is already running");
            return;
        }
        if (!isCustomGradleEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Custom Gradle is off")
                    .setMessage("Builds only use these files when Custom Gradle is enabled. Enable it now?")
                    .setPositiveButton("Enable", (d, w) -> {
                        setCustomGradleEnabled(true);
                        requestSync(only);
                    })
                    .setNegativeButton("Sync anyway", (d, w) -> proceedSync(only))
                    .show();
            return;
        }
        proceedSync(only);
    }

    private void proceedSync(@Nullable Set<String> only) {
        if (anyDirty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Unsaved changes")
                    .setMessage("Save your edits before syncing?")
                    .setPositiveButton("Save & Sync", (d, w) -> {
                        saveAllDirty();
                        startSyncNow(only);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        startSyncNow(only);
    }

    private void startSyncNow(@Nullable Set<String> only) {
        if (!viewModel.startSync(only)) SketchwareUtil.toast("A sync is already running");
    }

    private void openDirectory() {
        File dir = new File(customGradleDir);
        if (!dir.isDirectory()) {
            SketchwareUtil.toast("Directory does not exist yet");
            return;
        }
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String path = dir.getAbsolutePath();
        try {
            if (!path.startsWith(root)) throw new ActivityNotFoundException();
            String relative = path.substring(root.length());
            if (relative.startsWith("/")) relative = relative.substring(1);
            Uri uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:" + relative);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("path", path));
            SketchwareUtil.toast("No file manager could open it. Path copied: " + path);
        }
    }

    private void setupDependencies() {
        dependencyAdapter = new GradleDependencyAdapter(this::showDependencyMenu);
        binding.depsList.setLayoutManager(new LinearLayoutManager(this));
        binding.depsList.setAdapter(dependencyAdapter);
        binding.depsList.setItemAnimator(null);
        binding.btnAddDep.setOnClickListener(v -> showDependencyDialog(null));
        binding.btnSyncNow.setOnClickListener(v -> onSyncClicked());
        updatingSkipSwitches = true;
        binding.switchSkipDeps.setChecked(new BuildSettings(sc_id).isSkipSubDependencies());
        updatingSkipSwitches = false;
        binding.switchSkipDeps.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSkipSwitches) return;
            GradleSyncEngine.setSkipSubDependenciesForAll(sc_id, checked);
            buildSettings = new BuildSettings(sc_id);
            updatingSkipSwitches = true;
            binding.switchSkipSub.setChecked(checked);
            updatingSkipSwitches = false;
            viewModel.reload();
            SketchwareUtil.toast(checked ? "Only listed dependencies will be downloaded. Run Sync to apply." : "Sub-dependencies will be downloaded too. Run Sync to apply.");
        });
        binding.depsSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderDependencies();
            }
        });
        binding.filterChips.setOnCheckedStateChangeListener((group, ids) -> renderDependencies());
    }

    private String selectedFilter() {
        int id = binding.filterChips.getCheckedChipId();
        if (id == R.id.chip_impl) return "implementation";
        if (id == R.id.chip_api) return "api";
        if (id == R.id.chip_kapt) return "kapt";
        if (id == R.id.chip_other) return "other";
        return "all";
    }

    private static String bucket(String configuration) {
        return configuration.equals("implementation") || configuration.equals("api") || configuration.equals("kapt") ? configuration : "other";
    }

    private void renderDependencies() {
        int impl = 0, api = 0, kapt = 0, other = 0, synced = 0, pending = 0;
        for (GradleSyncEngine.Row row : allRows) {
            switch (bucket(row.dep.configuration)) {
                case "implementation" -> impl++;
                case "api" -> api++;
                case "kapt" -> kapt++;
                default -> other++;
            }
            if (row.isConsumed()) {
                if (row.state == GradleSyncEngine.DepState.READY) synced++;
                else pending++;
            }
        }
        binding.chipAll.setText("All " + allRows.size());
        binding.chipImpl.setText("Implementation " + impl);
        binding.chipApi.setText("API " + api);
        binding.chipKapt.setText("kapt " + kapt);
        binding.chipOther.setText("Other " + other);
        binding.depsSummary.setText(synced + " synced  •  " + pending + " pending");
        binding.depsProgress.setMax(Math.max(1, synced + pending));
        binding.depsProgress.setProgress(synced);
        binding.depsHint.setText(pending == 0
                ? (allRows.isEmpty() ? "No dependencies declared in app/build.gradle." : "All dependencies are ready.")
                : pending + " dependenc" + (pending == 1 ? "y is" : "ies are") + " not ready. Run Sync to resolve " + (pending == 1 ? "it." : "them."));

        String filter = selectedFilter();
        String query = binding.depsSearch.getText() == null ? "" : binding.depsSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<GradleSyncEngine.Row> shown = new ArrayList<>();
        for (GradleSyncEngine.Row row : allRows) {
            if (!filter.equals("all") && !bucket(row.dep.configuration).equals(filter)) continue;
            if (!query.isEmpty() && !row.dep.coordinate().toLowerCase(Locale.ROOT).contains(query)) continue;
            shown.add(row);
        }
        dependencyAdapter.submitList(shown);
        binding.depsEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        binding.depsList.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showDependencyMenu(@NonNull GradleSyncEngine.Row row, @NonNull View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Edit");
        menu.getMenu().add(0, 2, 1, "Change configuration");
        if (row.state == GradleSyncEngine.DepState.FAILED) menu.getMenu().add(0, 3, 2, "Retry");
        boolean skipping = GradleSyncEngine.isSkipSubDependencies(sc_id, row.dep.key());
        menu.getMenu().add(0, 7, 3, skipping ? "Include sub-dependencies" : "Skip sub-dependencies");
        menu.getMenu().add(0, 4, 4, "Details");
        menu.getMenu().add(0, 5, 5, "Copy coordinate");
        menu.getMenu().add(0, 6, 6, "Remove");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> showDependencyDialog(row);
                case 2 -> showConfigurationChooser(row);
                case 3 -> {
                    Set<String> keys = new HashSet<>();
                    keys.add(row.dep.key());
                    requestSync(keys);
                }
                case 4 -> showDependencyDetails(row);
                case 5 -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("dependency", row.dep.coordinate()));
                    SketchwareUtil.toast("Copied");
                }
                case 6 -> confirmRemove(row);
                case 7 -> {
                    GradleSyncEngine.setSkipSubDependencies(sc_id, row.dep.key(), !skipping);
                    viewModel.reload();
                    SketchwareUtil.toast("Run Sync to apply for " + row.dep.key());
                }
            }
            return true;
        });
        menu.show();
    }

    private void showDependencyDetails(GradleSyncEngine.Row row) {
        StringBuilder sb = new StringBuilder();
        sb.append("Coordinate: ").append(row.dep.coordinate()).append('\n');
        sb.append("Configuration: ").append(row.dep.configuration).append('\n');
        sb.append("State: ").append(row.state.name().toLowerCase(Locale.ROOT)).append('\n');
        if (row.error != null) sb.append("Message: ").append(row.error).append('\n');
        sb.append("Cached size: ").append(formatBytes(row.cacheBytes)).append('\n');
        sb.append("\nResolved artifacts (").append(row.folders.size()).append("), including transitive dependencies:\n");
        if (row.folders.isEmpty()) sb.append("none yet");
        for (String folder : row.folders) sb.append("  ").append(folder).append('\n');
        showTextDialog(row.dep.key(), sb.toString().trim());
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private void showTextDialog(String title, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextIsSelectable(true);
        view.setTypeface(android.graphics.Typeface.MONOSPACE);
        view.setTextSize(12f);
        view.setPadding(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(8), SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .setNeutralButton("Copy", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(title, text));
                })
                .show();
    }

    private void showConfigurationChooser(GradleSyncEngine.Row row) {
        int checked = 0;
        for (int i = 0; i < CONFIGURATIONS.length; i++) {
            if (CONFIGURATIONS[i].equals(row.dep.configuration)) checked = i;
        }
        final int[] choice = {checked};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Configuration")
                .setSingleChoiceItems(CONFIGURATIONS, checked, (d, which) -> choice[0] = which)
                .setPositiveButton("Apply", (d, w) -> {
                    String configuration = CONFIGURATIONS[choice[0]];
                    if (!configuration.equals(row.dep.configuration)) {
                        boolean skip = GradleSyncEngine.isSkipSubDependencies(sc_id, row.dep.key());
                        mutateAppBuild(content -> GradleDependencyEditor.update(content, row.dep.key(), configuration, row.dep.coordinate()),
                                row.dep.key(), replacedFor(row, configuration, row.dep.coordinate()),
                                () -> GradleSyncEngine.setSkipSubDependencies(sc_id, row.dep.key(), skip));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemove(GradleSyncEngine.Row row) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove dependency")
                .setMessage("Remove " + row.dep.key() + " from app/build.gradle? Downloaded files stay in the cache.")
                .setPositiveButton("Remove", (d, w) -> mutateAppBuild(content -> GradleDependencyEditor.remove(content, row.dep.key()), row.dep.key(), row.dep, null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface Mutation {
        String apply(String content);
    }

    private void mutateAppBuild(Mutation mutation, @Nullable String requiredKey, @Nullable GradleDependency replaced, @Nullable Runnable after) {
        if (viewModel.isRunning()) {
            SketchwareUtil.toast("Wait for the running sync to finish");
            return;
        }
        stashEditor();
        if (isDirty(FILE_APP_BUILD)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Unsaved changes")
                    .setMessage("Save app/build.gradle before changing dependencies?")
                    .setPositiveButton("Save", (d, w) -> {
                        saveFile(FILE_APP_BUILD, false);
                        mutateAppBuild(mutation, requiredKey, replaced, after);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        if (!isCustomGradleEnabled()) setCustomGradleEnabled(true);
        String path = customGradleDir + FILE_APP_BUILD;
        if (!FileUtil.isExistFile(path)) ensureGradleFilesExist(false);
        String content = FileUtil.readFile(path);
        if (requiredKey != null && !GradleDependencyEditor.has(content, requiredKey)) {
            SketchwareUtil.toastError(requiredKey + " is not declared as a single quoted dependency inside the dependencies block, so it cannot be edited here. Edit it directly in app/build.gradle.");
            return;
        }
        String updated = mutation.apply(content);
        if (updated.equals(content)) {
            SketchwareUtil.toast("No change made to app/build.gradle");
            return;
        }
        if (replaced != null) {
            List<GradleDependency> remaining = new ArrayList<>();
            for (GradleSyncEngine.Row row : allRows) {
                if (!row.dep.key().equals(replaced.key())) remaining.add(row.dep);
            }
            GradleSyncEngine.removeFromProject(sc_id, replaced, remaining);
        }
        FileUtil.writeFile(path, updated);
        saved.put(FILE_APP_BUILD, updated);
        buffers.remove(FILE_APP_BUILD);
        if (after != null) after.run();
        if (currentFile.equals(FILE_APP_BUILD)) loadFile(FILE_APP_BUILD, true);
        viewModel.reload();
    }

    @Nullable
    private GradleDependency replacedFor(GradleSyncEngine.Row row, String newConfiguration, String newCoordinate) {
        boolean coordinateChanged = !row.dep.coordinate().equals(newCoordinate);
        boolean consumedChanged = GradleSyncEngine.isConsumedConfiguration(row.dep.configuration) != GradleSyncEngine.isConsumedConfiguration(newConfiguration);
        return coordinateChanged || consumedChanged ? row.dep : null;
    }

    private static String keyOf(String coordinate) {
        String[] parts = coordinate.split(":");
        return parts[0] + ":" + parts[1];
    }

    private void showDependencyDialog(@Nullable GradleSyncEngine.Row editing) {
        DialogGradleDependencyBinding d = DialogGradleDependencyBinding.inflate(getLayoutInflater());
        d.actvConfig.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, CONFIGURATIONS));
        d.actvConfig.setText(editing != null ? editing.dep.configuration : "implementation", false);
        d.cbSkipSub.setChecked(editing != null
                ? GradleSyncEngine.isSkipSubDependencies(sc_id, editing.dep.key())
                : new BuildSettings(sc_id).isSkipSubDependencies());
        if (editing != null) {
            d.etCoordinate.setText(editing.dep.coordinate());
            d.searchRow.setVisibility(View.GONE);
        }
        Set<String> processing = new HashSet<>();
        MavenSearchResultAdapter results = new MavenSearchResultAdapter(processing, result -> {
            d.etCoordinate.setText(result.getFullCoordinate());
            d.tilCoordinate.setError(null);
        });
        d.searchResults.setLayoutManager(new LinearLayoutManager(this));
        d.searchResults.setAdapter(results);
        Runnable runSearch = () -> {
            String query = d.etSearch.getText() == null ? "" : d.etSearch.getText().toString().trim();
            if (query.isEmpty()) return;
            d.searchProgress.setVisibility(View.VISIBLE);
            new Thread(() -> {
                try {
                    List<MavenSearchResult> found = MavenSearchClient.search(query);
                    runOnUiThread(() -> {
                        d.searchProgress.setVisibility(View.GONE);
                        results.setResults(found);
                        d.searchResults.setVisibility(found.isEmpty() ? View.GONE : View.VISIBLE);
                        if (found.isEmpty()) SketchwareUtil.toast("No results for \"" + query + "\"");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        d.searchProgress.setVisibility(View.GONE);
                        SketchwareUtil.toastError("Search failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                    });
                }
            }, "gradle-maven-search").start();
        };
        d.btnSearch.setOnClickListener(v -> runSearch.run());
        d.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            runSearch.run();
            return true;
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(editing != null ? "Edit dependency" : "Add dependency")
                .setView(d.getRoot())
                .setPositiveButton(editing != null ? "Save" : "Add", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String coordinate = d.etCoordinate.getText() == null ? "" : d.etCoordinate.getText().toString().trim();
            String configuration = d.actvConfig.getText().toString().trim();
            String error = validateCoordinate(coordinate, editing);
            if (error != null) {
                d.tilCoordinate.setError(error);
                return;
            }
            if (configuration.isEmpty()) configuration = "implementation";
            String finalConfiguration = configuration;
            boolean skip = d.cbSkipSub.isChecked();
            String newKey = keyOf(coordinate);
            dialog.dismiss();
            if (editing == null) {
                mutateAppBuild(content -> GradleDependencyEditor.add(content, finalConfiguration, coordinate), null, null,
                        () -> GradleSyncEngine.setSkipSubDependencies(sc_id, newKey, skip));
            } else if (coordinate.equals(editing.dep.coordinate()) && finalConfiguration.equals(editing.dep.configuration)) {
                if (skip != GradleSyncEngine.isSkipSubDependencies(sc_id, editing.dep.key())) {
                    GradleSyncEngine.setSkipSubDependencies(sc_id, editing.dep.key(), skip);
                    viewModel.reload();
                    SketchwareUtil.toast("Sub-dependency option updated. Run Sync to apply it.");
                } else {
                    SketchwareUtil.toast("No changes made");
                }
            } else {
                mutateAppBuild(content -> GradleDependencyEditor.update(content, editing.dep.key(), finalConfiguration, coordinate),
                        editing.dep.key(), replacedFor(editing, finalConfiguration, coordinate),
                        () -> GradleSyncEngine.setSkipSubDependencies(sc_id, newKey, skip));
            }
        }));
        dialog.show();
    }

    @Nullable
    private String validateCoordinate(String coordinate, @Nullable GradleSyncEngine.Row editing) {
        if (coordinate.isEmpty()) return "Enter group:artifact:version";
        if (coordinate.contains("+") || coordinate.contains("$")) return "Use a fixed version, not '+' or a variable";
        if (!COORDINATE.matcher(coordinate).matches()) return "Malformed coordinate, expected group:artifact:version";
        String[] parts = coordinate.split(":");
        String key = parts[0] + ":" + parts[1];
        for (GradleSyncEngine.Row row : allRows) {
            if (row.dep.key().equals(key) && (editing == null || !row.dep.key().equals(editing.dep.key()))) {
                return key + " is already declared";
            }
        }
        return null;
    }

    private void setupBuildSettings() {
        binding.switchOffline.setChecked(buildSettings.isOfflineCacheEnabled());
        binding.switchSkipSub.setChecked(new BuildSettings(sc_id).isSkipSubDependencies());

        int max = BuildSettings.getMaxParallelThreads();
        boolean adjustable = max >= 2;
        binding.sliderThreads.setValueFrom(1);
        binding.sliderThreads.setValueTo(adjustable ? max : 2);
        binding.sliderThreads.setStepSize(1);
        binding.sliderThreads.setEnabled(adjustable);
        binding.sliderThreads.setValue(Math.min(buildSettings.getParallelThreads(), adjustable ? max : 1));
        binding.threadsHint.setText(adjustable
                ? "Used by native C/C++ builds and R8. This device allows up to " + max + "."
                : "This device exposes a single CPU core.");
        updateThreadsLabel();
        binding.sliderThreads.addOnChangeListener((slider, value, fromUser) -> updateThreadsLabel());

        String current = buildSettings.getValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8);
        List<String> options = new ArrayList<>(List.of(BuildSettings.SETTING_JAVA_VERSION_1_8, BuildSettings.SETTING_JAVA_VERSION_11, BuildSettings.SETTING_JAVA_VERSION_17));
        if (!options.contains(current)) options.add(0, current);
        String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = javaLabel(options.get(i));
        binding.actvJava.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        javaChoice = current;
        binding.actvJava.setText(javaLabel(current), false);
        binding.actvJava.setOnItemClickListener((parent, view, position, id) -> javaChoice = options.get(position));

        binding.btnApply.setOnClickListener(v -> applyBuildSettings());
    }

    private static String javaLabel(String value) {
        return switch (value) {
            case "1.8" -> "Java 8 (1.8)";
            case "11" -> "Java 11";
            case "17" -> "Java 17";
            default -> "Java " + value;
        };
    }

    private void updateThreadsLabel() {
        int value = (int) binding.sliderThreads.getValue();
        binding.threadsLabel.setText(value + (value == 1 ? " thread" : " threads"));
    }

    private void applyBuildSettings() {
        boolean skipSub = binding.switchSkipSub.isChecked();
        if (skipSub != new BuildSettings(sc_id).isSkipSubDependencies()) {
            GradleSyncEngine.setSkipSubDependenciesForAll(sc_id, skipSub);
        }
        buildSettings = new BuildSettings(sc_id);
        updatingSkipSwitches = true;
        binding.switchSkipDeps.setChecked(skipSub);
        updatingSkipSwitches = false;
        buildSettings.setValue(BuildSettings.SETTING_OFFLINE_CACHE, binding.switchOffline.isChecked()
                ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
        int threads = Math.max(1, Math.min((int) binding.sliderThreads.getValue(), BuildSettings.getMaxParallelThreads()));
        buildSettings.setValue(BuildSettings.SETTING_PARALLEL_THREADS, String.valueOf(threads));
        buildSettings.setValue(BuildSettings.SETTING_JAVA_VERSION, javaChoice);
        if (javaChoice.equals(BuildSettings.SETTING_JAVA_VERSION_11) || javaChoice.equals(BuildSettings.SETTING_JAVA_VERSION_17)) {
            buildSettings.setValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_D8);
        }
        String note = "";
        if (isCustomGradleEnabled()) {
            File app = new File(customGradleDir, FILE_APP_BUILD);
            if (app.isFile()) {
                ParsedGradle parsed = new GradleParser().parseFile(app, null);
                if (parsed.explicitFields.contains(ParsedGradle.FIELD_JAVA_VERSION) && !parsed.javaVersion.equals(javaChoice)) {
                    note = " app/build.gradle sets sourceCompatibility " + parsed.javaVersion + " and wins at build time.";
                }
            }
        }
        SketchwareUtil.toast("Build settings applied." + note);
        viewModel.reload();
    }

    private void observeViewModel() {
        viewModel.getRows().observe(this, rows -> {
            allRows = rows;
            renderDependencies();
        });
        viewModel.getSyncUi().observe(this, this::renderSyncUi);
    }

    private void renderSyncUi(GradleManagerViewModel.SyncUi ui) {
        String title;
        int icon;
        int container;
        int content;
        switch (ui.status) {
            case SYNCED -> {
                title = "Gradle is Synced";
                icon = R.drawable.ic_mtrl_check;
                container = com.google.android.material.R.attr.colorPrimaryContainer;
                content = com.google.android.material.R.attr.colorOnPrimaryContainer;
            }
            case STALE -> {
                title = "Sync required";
                icon = R.drawable.ic_mtrl_warning;
                container = com.google.android.material.R.attr.colorTertiaryContainer;
                content = com.google.android.material.R.attr.colorOnTertiaryContainer;
            }
            case SYNCING -> {
                title = "Syncing...";
                icon = R.drawable.ic_mtrl_sync;
                container = com.google.android.material.R.attr.colorSecondaryContainer;
                content = com.google.android.material.R.attr.colorOnSecondaryContainer;
            }
            case FAILED -> {
                title = "Sync failed";
                icon = R.drawable.ic_mtrl_cancel;
                container = com.google.android.material.R.attr.colorErrorContainer;
                content = com.google.android.material.R.attr.colorOnErrorContainer;
            }
            case OFFLINE -> {
                title = "Offline";
                icon = R.drawable.ic_mtrl_warning;
                container = com.google.android.material.R.attr.colorErrorContainer;
                content = com.google.android.material.R.attr.colorOnErrorContainer;
            }
            case INVALID -> {
                title = "Invalid configuration";
                icon = R.drawable.ic_mtrl_cancel;
                container = com.google.android.material.R.attr.colorErrorContainer;
                content = com.google.android.material.R.attr.colorOnErrorContainer;
            }
            default -> {
                title = "Not synced yet";
                icon = R.drawable.ic_mtrl_info;
                container = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                content = com.google.android.material.R.attr.colorOnSurface;
            }
        }
        boolean syncing = ui.status == GradleManagerViewModel.Status.SYNCING;
        binding.statusTitle.setText(title);
        binding.statusMessage.setText(ui.message);
        binding.statusIcon.setImageResource(icon);
        binding.statusIcon.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColor(this, container)));
        binding.statusIcon.setImageTintList(ColorStateList.valueOf(ThemeUtils.getColor(this, content)));
        binding.statusIcon.setVisibility(syncing ? View.INVISIBLE : View.VISIBLE);
        binding.statusProgress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.statusAgp.setText(ui.agpVersion != null ? "AGP " + ui.agpVersion : "AGP n/a");
        binding.statusEngine.setText("In-app build");

        binding.syncProgress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        if (syncing) {
            binding.syncProgress.setIndeterminate(ui.total <= 0);
            if (ui.total > 0) {
                binding.syncProgress.setMax(ui.total);
                binding.syncProgress.setProgress(ui.done);
            }
        }
        binding.btnSyncNow.setText(syncing ? "Cancel" : "Sync Now");
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem save = menu.add(Menu.NONE, MENU_SAVE, Menu.NONE, "Save");
        save.setIcon(R.drawable.ic_mtrl_save);
        save.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem sync = menu.add(Menu.NONE, MENU_SYNC, Menu.NONE, "Sync Gradle");
        sync.setIcon(R.drawable.ic_mtrl_sync);
        sync.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem refresh = menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, "Refresh");
        refresh.setIcon(R.drawable.ic_mtrl_refresh);
        refresh.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem custom = menu.add(Menu.NONE, MENU_CUSTOM, Menu.NONE, "Use Custom Gradle");
        custom.setCheckable(true);
        custom.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        MenuItem log = menu.add(Menu.NONE, MENU_LOG, Menu.NONE, "Sync log");
        log.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        MenuItem reset = menu.add(Menu.NONE, MENU_RESET, Menu.NONE, "Reset Configuration");
        reset.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean running = viewModel.isRunning();
        MenuItem save = menu.findItem(MENU_SAVE);
        if (save != null) save.setVisible(anyDirty());
        MenuItem sync = menu.findItem(MENU_SYNC);
        if (sync != null) {
            sync.setTitle(running ? "Cancel sync" : "Sync Gradle");
            sync.setIcon(running ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_sync);
        }
        MenuItem custom = menu.findItem(MENU_CUSTOM);
        if (custom != null) custom.setChecked(isCustomGradleEnabled());
        MenuItem refresh = menu.findItem(MENU_REFRESH);
        if (refresh != null) refresh.setEnabled(!running);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SAVE -> {
                saveDirtyFiles();
                return true;
            }
            case MENU_SYNC -> {
                onSyncClicked();
                return true;
            }
            case MENU_REFRESH -> {
                refreshFromDisk();
                return true;
            }
            case MENU_CUSTOM -> {
                setCustomGradleEnabled(!isCustomGradleEnabled());
                return true;
            }
            case MENU_LOG -> {
                String log = viewModel.getLog().getValue();
                showTextDialog("Sync log", log == null || log.isEmpty() ? "No sync has run in this session." : log);
                return true;
            }
            case MENU_RESET -> {
                showResetDialog();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshFromDisk() {
        if (viewModel.isRunning()) {
            SketchwareUtil.toast("Wait for the running sync to finish");
            return;
        }
        stashEditor();
        if (anyDirty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Reload from disk")
                    .setMessage("Unsaved edits will be lost.")
                    .setPositiveButton("Reload", (d, w) -> reloadAllFromDisk())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            reloadAllFromDisk();
        }
    }

    private void reloadAllFromDisk() {
        buffers.clear();
        saved.clear();
        loadFile(currentFile, true);
        viewModel.reload();
    }

    private boolean isCustomGradleEnabled() {
        return projectSettings.getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
    }

    private void setCustomGradleEnabled(boolean enabled) {
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE,
                enabled ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
        if (enabled) {
            ensureGradleFilesExist(false);
            SketchwareUtil.toast("Custom Gradle enabled");
        } else {
            SketchwareUtil.toast("Using default generated configurations");
        }
        invalidateOptionsMenu();
        viewModel.reload();
    }

    private void showResetDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset Gradle Configuration?")
                .setMessage("This will delete your customized Gradle files and revert to the default Sketchware build configurations.\n\nA backup of your current files will be created first. If the backup fails, nothing is deleted.")
                .setPositiveButton("Reset", (dialog, which) -> resetConfiguration())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetConfiguration() {
        String backupPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/backup_gradle_" + System.currentTimeMillis() + "/";
        File source = new File(customGradleDir);
        try {
            if (source.exists()) backupDirectory(source, new File(backupPath));
        } catch (IOException e) {
            SketchwareUtil.showAnErrorOccurredDialog(this, "Backup failed, nothing was deleted: " + e.getMessage());
            return;
        }
        FileUtil.deleteFile(customGradleDir);
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
        buffers.clear();
        saved.clear();
        invalidateOptionsMenu();
        loadFile(FILE_APP_BUILD, true);
        viewModel.reload();
        SketchwareUtil.toast("Gradle configurations reset. Backup saved to: " + backupPath);
    }

    private String getDefaultContent(String fileName) {
        yq yqInstance = buildYqInstance();

        switch (fileName) {
            case FILE_APP_BUILD: {
                String targetSdk = projectSettings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION));
                boolean viewBindingEnabled = projectSettings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_FALSE).equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
                return Lx.getBuildGradleString(VAR_DEFAULT_TARGET_SDK_VERSION, VAR_DEFAULT_MIN_SDK_VERSION, targetSdk, yqInstance != null ? yqInstance.N : new a.a.a.jq(), viewBindingEnabled);
            }
            case FILE_BUILD:
                return Lx.c("8.12.0", "4.4.3");

            case FILE_SETTINGS:
                return Lx.a();

            case FILE_PROPERTIES:
                return "android.enableR8.fullMode=false\nandroid.enableJetifier=true\nandroid.useAndroidX=true";

            default:
                return "";
        }
    }

    private yq buildYqInstance() {
        try {
            HashMap<String, Object> metadata = lC.b(sc_id);
            if (metadata == null) return null;
            yq instance = new yq(getApplicationContext(), wq.d(sc_id), metadata);
            try {
                instance.a(jC.c(sc_id), jC.b(sc_id), jC.a(sc_id));
            } catch (Exception ignored) { }
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureGradleFilesExist(boolean forceOverwrite) {
        FileUtil.makeDir(customGradleDir);
        for (String fileName : gradleFiles) {
            String path = customGradleDir + fileName;
            boolean needsWrite = forceOverwrite || !FileUtil.isExistFile(path) || FileUtil.readFile(path).trim().isEmpty();
            if (needsWrite) {
                FileUtil.writeFile(path, getDefaultContent(fileName));
            }
        }
    }

    private void backupDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            String[] children = source.list();
            if (children != null) {
                for (String child : children) backupDirectory(new File(source, child), new File(target, child));
            }
        } else {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
        }
    }
}
