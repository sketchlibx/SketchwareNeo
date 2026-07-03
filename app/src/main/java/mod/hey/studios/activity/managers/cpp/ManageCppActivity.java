package mod.hey.studios.activity.managers.cpp;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import mod.hey.studios.activity.managers.cpp.JniBridgeGenerator;
import mod.hey.studios.activity.managers.cpp.JniValidator;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogCreateCppFileLayoutBinding;
import pro.sketchware.databinding.DialogInputLayoutBinding;
import pro.sketchware.databinding.ManageFileBinding;
import pro.sketchware.databinding.ManageJavaItemHsBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

@SuppressLint("SetTextI18n")
public class ManageCppActivity extends BaseAppCompatActivity {

    // ── File templates ────────────────────────────────────────────────────────
    private static final String C_TEMPLATE =
            "/* %s.c */\n\n" +
            "#include <stdio.h>\n\n" +
            "/* TODO: implement %s */\n";

    private static final String CPP_TEMPLATE =
            "// %s.cpp\n\n" +
            "#include <iostream>\n\n" +
            "// TODO: implement %s\n";

    private static final String H_TEMPLATE =
            "/* %s.h */\n\n" +
            "#pragma once\n\n" +
            "/* TODO: declare %s */\n";

    private static final String HPP_TEMPLATE =
            "// %s.hpp\n\n" +
            "#pragma once\n\n" +
            "// TODO: declare %s\n";

    private static final String MK_TEMPLATE =
            "# %s.mk\n\n";

    private static final String TXT_TEMPLATE = "";

    // ── Fields ────────────────────────────────────────────────────────────────
    private ManageFileBinding binding;
    private String current_path;
    private FilePathUtil fpu;
    private String sc_id;
    private String pkgName;
    private CppAdapter cppAdapter;
    private FilePickerDialogFragment filePickerDialog;
    private String importTargetPath;
    
    private String pendingJavaStub = null;
    private boolean isTreeViewEnabled;
    private final ArrayList<FileNode> rootNodes = new ArrayList<>();
    private final ArrayList<FileNode> flatNodesList = new ArrayList<>();
    private final Set<String> expandedPaths = new HashSet<>();
    private String searchQuery = "";
    private SortMode currentSortMode = SortMode.NAME;

    public enum SortMode { NAME, TYPE }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageFileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sc_id = getIntent().getStringExtra("sc_id");
        pkgName = getIntent().getStringExtra("pkgName");
        if (pkgName == null) pkgName = "";
        Helper.fixFileprovider();

        fpu = new FilePathUtil();
        current_path = Uri.parse(fpu.getPathCpp(sc_id)).getPath();

        setupUI();
        setupFilePicker();
        setupSearch();
        refresh();

        binding.filesListRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && binding.showOptionsButton.isExtended()) binding.showOptionsButton.shrink();
                else if (dy < 0 && !binding.showOptionsButton.isExtended()) binding.showOptionsButton.extend();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (binding.searchLayout.getVisibility() == View.VISIBLE) {
            binding.searchLayout.setVisibility(View.GONE);
            binding.searchEditText.setText("");
            return;
        }
        if (isTreeViewEnabled) {
            finish();
            super.onBackPressed();
        } else {
            if (Objects.equals(
                    Uri.parse(current_path).getPath(),
                    Uri.parse(fpu.getPathCpp(sc_id)).getPath())) {
                super.onBackPressed();
            } else {
                current_path = current_path.substring(0, current_path.lastIndexOf("/"));
                refresh();
            }
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────
    private void setupUI() {
        binding.topAppBar.setNavigationOnClickListener(v -> onBackPressed());
        binding.topAppBar.setTitle("C/C++ Manager");
        setSupportActionBar(binding.topAppBar);

        binding.showOptionsButton.setOnClickListener(v -> hideShowOptionsButton(false));
        binding.closeButton.setOnClickListener(v -> hideShowOptionsButton(true));

        binding.createNewButton.setOnClickListener(v -> {
            showCreateDialog(isTreeViewEnabled ? fpu.getPathCpp(sc_id) : current_path);
            hideShowOptionsButton(true);
        });

        binding.importNewButton.setOnClickListener(v -> {
            importTargetPath = isTreeViewEnabled ? fpu.getPathCpp(sc_id) : current_path;
            filePickerDialog.show(getSupportFragmentManager(), "filePicker");
            hideShowOptionsButton(true);
        });

        binding.btnCreateEmpty.setOnClickListener(v ->
                showCreateDialog(isTreeViewEnabled ? fpu.getPathCpp(sc_id) : current_path));
    }

    private void hideShowOptionsButton(boolean isHide) {
        binding.optionsLayout.animate()
                .translationY(isHide ? 300 : 0).alpha(isHide ? 0 : 1)
                .setInterpolator(new OvershootInterpolator());
        binding.showOptionsButton.animate()
                .translationY(isHide ? 0 : 300).alpha(isHide ? 1 : 0)
                .setInterpolator(new OvershootInterpolator());
    }

    private void setupSearch() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                if (isTreeViewEnabled) rebuildFlatList();
                else refresh();
            }
        });
    }

    // ── Options menu ──────────────────────────────────────────────────────────
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Search")
                .setIcon(R.drawable.ic_mtrl_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 2, Menu.NONE, "Collapse All")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 3, Menu.NONE, "Sort by Name")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 4, Menu.NONE, "Sort by Type")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 5, Menu.NONE, "Validate JNI")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1 -> {
                boolean visible = binding.searchLayout.getVisibility() == View.VISIBLE;
                binding.searchLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
                if (!visible) binding.searchEditText.requestFocus();
            }
            case 2 -> {
                expandedPaths.clear();
                if (isTreeViewEnabled) rebuildFlatList(); else refresh();
            }
            case 3 -> {
                currentSortMode = SortMode.NAME;
                if (isTreeViewEnabled) rebuildFlatList(); else refresh();
            }
            case 4 -> {
                currentSortMode = SortMode.TYPE;
                if (isTreeViewEnabled) rebuildFlatList(); else refresh();
            }
            case 5 -> showJniValidationReport();
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    private void showCreateDialog(String targetPath) {
        DialogCreateCppFileLayoutBinding dialogBinding =
                DialogCreateCppFileLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var alertDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle("Create new")
                .setMessage("File will be created in the selected directory.")
                .setNegativeButton("Cancel", (d, i) -> d.dismiss())
                .setPositiveButton("Create", null)
                .create();

        alertDialog.setOnShowListener(dialogInterface -> {
            alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            inputText.requestFocus();

            // Show the Java class name field only when JNI Bridge chip is selected
            dialogBinding.chipGroupTypes.setOnCheckedStateChangeListener((group, checkedIds) -> {
                boolean isJni = !checkedIds.isEmpty()
                        && checkedIds.get(0) == R.id.chip_jni_bridge;
                dialogBinding.javaClassInputLayout.setVisibility(
                        isJni ? View.VISIBLE : View.GONE);
            });

            Button positiveButton = ((androidx.appcompat.app.AlertDialog) dialogInterface)
                    .getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String name = Helper.getText(inputText).trim();
                if (name.isEmpty()) {
                    SketchwareUtil.toastError("Invalid file name");
                    return;
                }

                int chipId = dialogBinding.chipGroupTypes.getCheckedChipId();
                if (chipId == View.NO_ID) {
                    SketchwareUtil.toast("Select a file type");
                    return;
                }

                String content;
                String finalName;

                if (chipId == R.id.chip_c_source) {
                    content  = String.format(C_TEMPLATE, name, name);
                    finalName = name + ".c";
                } else if (chipId == R.id.chip_cpp_source) {
                    content  = String.format(CPP_TEMPLATE, name, name);
                    finalName = name + ".cpp";
                } else if (chipId == R.id.chip_h_header) {
                    content  = String.format(H_TEMPLATE, name, name);
                    finalName = name + ".h";
                } else if (chipId == R.id.chip_hpp_header) {
                    content  = String.format(HPP_TEMPLATE, name, name);
                    finalName = name + ".hpp";
                } else if (chipId == R.id.chip_txt_file) {
                    content = TXT_TEMPLATE;
                    finalName = name + ".txt";
                } else if (chipId == R.id.chip_mk_file) {
                    content = String.format(MK_TEMPLATE, name);
                    finalName = name + ".mk";
                } else if (chipId == R.id.chip_jni_bridge) {
                    // Derive class name from the extra input field
                    String javaClassName = Helper.getText(dialogBinding.javaClassInput).trim();
                    if (javaClassName.isEmpty()) javaClassName = "MainActivity";

                    // Derive library name from package name last segment
                    String libName = JniBridgeGenerator.inferLibName(pkgName);

                    // Generate using the full JniBridgeGenerator
                    List<JniBridgeGenerator.JniMethodSpec> samples =
                            JniBridgeGenerator.defaultSampleMethods();
                    content   = JniBridgeGenerator.generateBridgeFile(
                            name, pkgName, javaClassName, libName, samples);
                    finalName = name + ".cpp";

                    // Stash for post-creation dialog
                    pendingJavaStub = JniBridgeGenerator.generateJavaStub(
                            javaClassName, libName, samples);
                } else if (chipId == R.id.chip_cpp_folder) {
                    FileUtil.makeDir(new File(targetPath, name).getAbsolutePath());
                    forceRefreshTree();
                    SketchwareUtil.toast("Folder created successfully");
                    alertDialog.dismiss();
                    return;
                } else {
                    SketchwareUtil.toast("Select a file type");
                    return;
                }

                File dest = new File(targetPath, finalName);
                if (dest.exists()) {
                    SketchwareUtil.toastError("A file with that name already exists");
                    return;
                }
                FileUtil.writeFile(dest.getAbsolutePath(), content);
                forceRefreshTree();
                alertDialog.dismiss();

                // Show Java stub dialog immediately after JNI bridge creation
                if (pendingJavaStub != null) {
                    String stub = pendingJavaStub;
                    pendingJavaStub = null;
                    showJavaStubDialog(stub);
                } else {
                    SketchwareUtil.toast("File created successfully");
                }
            });
        });

        alertDialog.show();
    }

    private void setupFilePicker() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        // ADDED SUPPORT FOR TXT AND MK IMPORTING HERE
        options.setExtensions(new String[]{"c", "cpp", "h", "hpp", "txt", "mk"});
        options.setTitle("Select C/C++ file(s)");

        filePickerDialog = new FilePickerDialogFragment(options, new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        try {
                            FileUtil.copyDirectory(file, new File(importTargetPath, file.getName()));
                        } catch (IOException e) {
                            SketchwareUtil.toastError("Couldn't import: " + e.getMessage());
                        }
                    } else {
                        FileUtil.writeFile(
                                new File(importTargetPath, file.getName()).getAbsolutePath(),
                                FileUtil.readFile(file.getAbsolutePath()));
                    }
                }
                forceRefreshTree();
            }
        });
    }

    private void showRenameDialog(int position) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Rename " + cppAdapter.getFileName(position))
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", (d, i) -> d.dismiss())
                .setPositiveButton("Rename", (d, i) -> {
                    String newName = Helper.getText(inputText);
                    if (!newName.isEmpty()) {
                        String oldPath = cppAdapter.getItem(position);
                        String newPath = new File(new File(oldPath).getParent(), newName).getAbsolutePath();
                        FileUtil.renameFile(oldPath, newPath);
                        forceRefreshTree();
                        SketchwareUtil.toast("Renamed successfully");
                    }
                    d.dismiss();
                })
                .create();

        inputText.setText(cppAdapter.getFileName(position));
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void showDeleteDialog(int position) {
        boolean isFolder = cppAdapter.isFolder(position);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + cppAdapter.getFileName(position) + "?")
                .setMessage("Are you sure you want to delete this " + (isFolder ? "folder" : "file") + "? This action cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (d, w) -> {
                    FileUtil.deleteFile(cppAdapter.getItem(position));
                    forceRefreshTree();
                    SketchwareUtil.toast("Deleted successfully");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .create().show();
    }
    
    /**
     * Shows a dialog with the Java-side stub code the user must add to their Activity.
     * Includes a "Copy" button that copies the stub to clipboard.
     *
     * <p>Called automatically after JNI bridge file creation.
     */
    private void showJavaStubDialog(String javaStub) {
        // Build a scrollable code view
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        TextView codeView = new TextView(this);
        codeView.setText(javaStub);
        codeView.setTypeface(android.graphics.Typeface.MONOSPACE);
        codeView.setTextSize(12f);
        codeView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        codeView.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceVariant));
        int pad = SketchwareUtil.dpToPx(12);
        codeView.setPadding(pad, pad, pad, pad);
        scrollView.addView(codeView);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add to Java Class")
                .setMessage("Copy this code and paste it into your Activity / class file:")
                .setView(scrollView)
                .setNegativeButton("Close", (d, i) -> d.dismiss())
                .setPositiveButton("Copy", (d, i) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("JNI Java Stub", javaStub));
                        SketchwareUtil.toast("Copied to clipboard");
                    }
                    d.dismiss();
                })
                .create()
                .show();
    }
    
    /**
     * Runs JniValidator against this project and shows results in a dialog.
     * Accessible from the overflow menu as "Validate JNI".
     */
    private void showJniValidationReport() {
        JniValidator.ValidationReport report = JniValidator.validate(sc_id);

        new MaterialAlertDialogBuilder(this)
                .setTitle("JNI Validation — " + report.statusSummary())
                .setMessage(report.formatForDisplay())
                .setPositiveButton("OK", (d, i) -> d.dismiss())
                .create()
                .show();
    }

    // ── Tree / list management ────────────────────────────────────────────────
    private void forceRefreshTree() {
        rootNodes.clear();
        refresh();
    }

    private void sortTreePaths(ArrayList<String> paths) {
        paths.sort((p1, p2) -> {
            boolean dir1 = new File(p1).isDirectory();
            boolean dir2 = new File(p2).isDirectory();
            if (dir1 && !dir2) return -1;
            if (!dir1 && dir2) return 1;
            if (currentSortMode == SortMode.TYPE) {
                int dot1 = p1.lastIndexOf('.');
                int dot2 = p2.lastIndexOf('.');
                String ext1 = dot1 >= 0 ? p1.substring(dot1 + 1) : "";
                String ext2 = dot2 >= 0 ? p2.substring(dot2 + 1) : "";
                if (!ext1.equalsIgnoreCase(ext2)) return ext1.compareToIgnoreCase(ext2);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(
                    new File(p1).getName(), new File(p2).getName());
        });
    }

    private void refresh() {
        if (!FileUtil.isExistFile(fpu.getPathCpp(sc_id))) {
            FileUtil.makeDir(fpu.getPathCpp(sc_id));
        }

        // Respect the global tree-view setting + per-type cpp setting
        boolean globalTree = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW);
        boolean cppTreeView;
        try {
            cppTreeView = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_CPP_TREE_VIEW);
        } catch (Exception ignored) {
            // Constant not yet added to ConfigActivity; fall back to java tree-view setting
            cppTreeView = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_JAVA_TREE_VIEW);
        }
        isTreeViewEnabled = globalTree;

        if (isTreeViewEnabled) {
            if (rootNodes.isEmpty()) {
                ArrayList<String> paths = new ArrayList<>();
                FileUtil.listDir(fpu.getPathCpp(sc_id), paths);
                sortTreePaths(paths);
                for (String p : paths) rootNodes.add(new FileNode(p, 0));
            }
            rebuildFlatList();
        } else {
            ArrayList<String> currentTree = new ArrayList<>();
            FileUtil.listDir(current_path, currentTree);
            sortTreePaths(currentTree);

            flatNodesList.clear();
            for (String p : currentTree) {
                if (searchQuery.isEmpty() ||
                        new File(p).getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                    flatNodesList.add(new FileNode(p, 0));
                }
            }

            if (cppAdapter == null) {
                cppAdapter = new CppAdapter();
                binding.filesListRecyclerView.setAdapter(cppAdapter);
            } else {
                cppAdapter.notifyDataSetChanged();
            }
            binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void rebuildFlatList() {
        flatNodesList.clear();
        for (FileNode node : rootNodes) addNodeToFlatListRecursive(node);

        if (cppAdapter == null) {
            cppAdapter = new CppAdapter();
            binding.filesListRecyclerView.setAdapter(cppAdapter);
        } else {
            cppAdapter.notifyDataSetChanged();
        }
        binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addNodeToFlatListRecursive(FileNode node) {
        if (!searchQuery.isEmpty()) {
            boolean nameMatch   = node.name.toLowerCase().contains(searchQuery.toLowerCase());
            boolean childMatch  = node.isFolder && searchTreeForMatch(node, searchQuery.toLowerCase());
            if (!nameMatch && !childMatch) return;
        }

        flatNodesList.add(node);

        if (node.isFolder && (expandedPaths.contains(node.path) || !searchQuery.isEmpty())) {
            ArrayList<String> paths = new ArrayList<>();
            FileUtil.listDir(node.path, paths);
            sortTreePaths(paths);
            for (String p : paths) addNodeToFlatListRecursive(new FileNode(p, node.depth + 1));
        }
    }

    private boolean searchTreeForMatch(FileNode folder, String query) {
        ArrayList<String> paths = new ArrayList<>();
        FileUtil.listDir(folder.path, paths);
        for (String p : paths) {
            if (new File(p).getName().toLowerCase().contains(query)) return true;
            if (FileUtil.isDirectory(p) && searchTreeForMatch(new FileNode(p, 0), query)) return true;
        }
        return false;
    }

    private void toggleFolder(FileNode node, int position) {
        if (!node.isFolder) return;
        boolean expanded = expandedPaths.contains(node.path);
        if (expanded) {
            expandedPaths.remove(node.path);
            int removed = removeChildrenFromList(node.depth, position + 1);
            if (cppAdapter != null) {
                cppAdapter.notifyItemRangeRemoved(position + 1, removed);
                cppAdapter.notifyItemChanged(position);
            }
        } else {
            expandedPaths.add(node.path);
            ArrayList<String> paths = new ArrayList<>();
            FileUtil.listDir(node.path, paths);
            sortTreePaths(paths);

            ArrayList<FileNode> children = new ArrayList<>();
            for (String p : paths) children.add(new FileNode(p, node.depth + 1));

            flatNodesList.addAll(position + 1, children);
            if (cppAdapter != null) {
                cppAdapter.notifyItemRangeInserted(position + 1, children.size());
                cppAdapter.notifyItemChanged(position);
            }
        }
    }

    private int removeChildrenFromList(int parentDepth, int startIndex) {
        int count = 0;
        while (startIndex < flatNodesList.size() && flatNodesList.get(startIndex).depth > parentDepth) {
            flatNodesList.remove(startIndex);
            count++;
        }
        return count;
    }

    // ── FileNode ──────────────────────────────────────────────────────────────
    public static class FileNode {
        public final String path;
        public final String name;
        public final boolean isFolder;
        public final int depth;

        public FileNode(String p, int d) {
            path     = p;
            name     = new File(p).getName();
            isFolder = FileUtil.isDirectory(p);
            depth    = d;
        }
    }

    // ── Bottom sheet ──────────────────────────────────────────────────────────
    private void showModernBottomSheet(FileNode node, int position) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout layout    = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = SketchwareUtil.dpToPx(16);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(node.name);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        title.setPadding(pad, pad, pad, pad * 2);
        layout.addView(title);

        if (node.isFolder) {
            layout.addView(createSheetItem("Create inside", R.drawable.ic_mtrl_file_present, () -> {
                sheet.dismiss(); showCreateDialog(node.path);
            }));
            layout.addView(createSheetItem("Import here", R.drawable.ic_mtrl_file_download, () -> {
                sheet.dismiss();
                importTargetPath = node.path;
                filePickerDialog.show(getSupportFragmentManager(), "filePicker");
            }));
        } else {
            layout.addView(createSheetItem("Edit", R.drawable.ic_mtrl_edit, () -> {
                sheet.dismiss(); cppAdapter.goEditFile(position);
            }));
            layout.addView(createSheetItem("Edit with...", R.drawable.ic_mtrl_edit, () -> {
                sheet.dismiss();
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(new File(cppAdapter.getItem(position))), "text/plain");
                startActivity(intent);
            }));
        }

        layout.addView(createSheetItem("Rename", R.drawable.ic_mtrl_edit, () -> {
            sheet.dismiss(); showRenameDialog(position);
        }));
        layout.addView(createSheetItem("Delete", R.drawable.ic_delete_white_24dp, () -> {
            sheet.dismiss(); showDeleteDialog(position);
        }));

        sheet.setContentView(layout);
        sheet.show();
    }

    private View createSheetItem(String text, int iconRes, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        int pad = SketchwareUtil.dpToPx(16);
        row.setPadding(pad, pad, pad, pad);

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        row.addView(icon, new LinearLayout.LayoutParams(
                SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(SketchwareUtil.dpToPx(16));
        row.addView(tv, lp);

        row.setOnClickListener(v -> action.run());
        return row;
    }

    // ── Icon helper ───────────────────────────────────────────────────────────
    /**
     * Returns the appropriate drawable resource for a given C/C++ file name.
     * Header files get ic_mtrl_file; source files get ic_mtrl_code.
     */
    private static int getIconForCppFile(String fileName) {
        String lower = fileName.toLowerCase();
        // ADDED SUPPORT FOR TXT AND MK FILE ICONS
        if (lower.endsWith(".h") || lower.endsWith(".hpp") || lower.endsWith(".txt") || lower.endsWith(".mk")) {
            return R.drawable.ic_mtrl_file;
        }
        return R.drawable.ic_mtrl_code;
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    public class CppAdapter extends RecyclerView.Adapter<CppAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageJavaItemHsBinding b = ManageJavaItemHsBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileNode node = flatNodesList.get(position);
            var b = holder.binding;

            b.title.setText(node.name);
            b.more.setOnClickListener(v -> showModernBottomSheet(node, position));

            if (isTreeViewEnabled) {
                // ── Tree mode ──────────────────────────────────────────
                int indentPx = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        node.depth * 20f,
                        getResources().getDisplayMetrics());
                ViewGroup.LayoutParams sp = b.indentSpacer.getLayoutParams();
                sp.width = indentPx;
                b.indentSpacer.setLayoutParams(sp);

                if (node.isFolder) {
                    b.title.setTypeface(null, Typeface.BOLD);
                    b.chevron.setVisibility(View.VISIBLE);
                    b.chevron.animate().cancel();
                    b.chevron.setRotation(expandedPaths.contains(node.path) ? 90f : 0f);
                    b.icon.setImageResource(R.drawable.ic_mtrl_folder);
                    b.icon.setColorFilter(ThemeUtils.getColor(ManageCppActivity.this, R.attr.colorPrimary));
                } else {
                    b.title.setTypeface(null, Typeface.NORMAL);
                    b.chevron.setVisibility(View.GONE);
                    b.icon.setImageResource(getIconForCppFile(node.name));
                    b.icon.setColorFilter(ThemeUtils.getColor(ManageCppActivity.this, R.attr.colorOnSurfaceVariant));
                }

                b.getRoot().setOnClickListener(v -> {
                    if (node.isFolder) {
                        b.chevron.animate()
                                .rotation(expandedPaths.contains(node.path) ? 0f : 90f)
                                .setDuration(200).start();
                        toggleFolder(node, holder.getAdapterPosition());
                    } else {
                        goEditFile(position);
                    }
                });
                b.getRoot().setOnLongClickListener(v -> {
                    showModernBottomSheet(node, position);
                    return true;
                });

            } else {
                // ── Flat mode ──────────────────────────────────────────
                b.indentSpacer.getLayoutParams().width = 0;
                b.chevron.setVisibility(View.GONE);
                b.title.setTypeface(null, Typeface.NORMAL);
                b.icon.setVisibility(View.VISIBLE);

                if (node.isFolder) {
                    b.icon.setImageResource(R.drawable.ic_mtrl_folder);
                    b.icon.setColorFilter(ThemeUtils.getColor(ManageCppActivity.this, R.attr.colorPrimary));
                } else {
                    b.icon.setImageResource(getIconForCppFile(node.name));
                    b.icon.setColorFilter(ThemeUtils.getColor(ManageCppActivity.this, R.attr.colorOnSurfaceVariant));
                }

                b.getRoot().setOnClickListener(v -> {
                    if (node.isFolder) {
                        current_path = node.path;
                        refresh();
                    } else {
                        goEditFile(position);
                    }
                });
                b.getRoot().setOnLongClickListener(v -> {
                    current_path = node.isFolder ? node.path : new File(node.path).getParent();
                    showModernBottomSheet(node, position);
                    return true;
                });
            }
        }

        @Override public int getItemCount() { return flatNodesList.size(); }

        public String  getItem(int pos)     { return flatNodesList.get(pos).path; }
        public String  getFileName(int pos) { return flatNodesList.get(pos).name; }
        public boolean isFolder(int pos)    { return flatNodesList.get(pos).isFolder; }

        public void goEditFile(int position) {
            Intent intent = new Intent(getApplicationContext(), SrcCodeEditor.class);
            intent.putExtra("title",   getFileName(position));
            intent.putExtra("content", getItem(position));
            startActivity(intent);
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            final ManageJavaItemHsBinding binding;
            ViewHolder(ManageJavaItemHsBinding b) {
                super(b.getRoot());
                binding = b;
            }
        }
    }
}
