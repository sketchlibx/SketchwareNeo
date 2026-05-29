package mod.hey.studios.activity.managers.java;

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
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.DialogInputLayoutBinding;
import pro.sketchware.databinding.ManageFileBinding;
import pro.sketchware.databinding.ManageJavaItemHsBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileResConfig;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

@SuppressLint("SetTextI18n")
public class ManageJavaActivity extends BaseAppCompatActivity {

    private static final String PACKAGE_DECL_REGEX = "package (.*?);?\\n";

    private static final String ACTIVITY_TEMPLATE = "package %s;\n\nimport android.app.Activity;\nimport android.os.Bundle;\n\npublic class %s extends Activity {\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n    }\n}\n";
    private static final String CLASS_TEMPLATE = "package %s;\n\npublic class %s {\n   \n}\n";
    private static final String KT_ACTIVITY_TEMPLATE = "package %s\n\nimport android.app.Activity\nimport android.os.Bundle\n\nclass %s : Activity() {\n\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}\n";
    private static final String KT_CLASS_TEMPLATE = "package %s\n\nclass %s {\n   \n}\n";

    private ManageFileBinding binding;
    private String current_path;
    private FilePathUtil fpu;
    private FileResConfig frc;
    private String sc_id;
    private FilesAdapter filesAdapter;
    private FilePickerDialogFragment dialog;
    private String importTargetPath;

    private boolean isTreeViewEnabled;
    private final ArrayList<FileNode> rootNodes = new ArrayList<>();
    private final ArrayList<FileNode> flatNodesList = new ArrayList<>();
    private final Set<String> expandedPaths = new HashSet<>();
    private String searchQuery = "";
    private SortMode currentSortMode = SortMode.NAME;

    public enum SortMode { NAME, TYPE }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageFileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sc_id = getIntent().getStringExtra("sc_id");
        Helper.fixFileprovider();
        frc = new FileResConfig(sc_id);
        fpu = new FilePathUtil();
        current_path = Uri.parse(fpu.getPathJava(sc_id)).getPath();

        setupUI();
        setupDialog();
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
            super.onBackPressed();
        } else {
            if (Objects.equals(Uri.parse(current_path).getPath(), Uri.parse(fpu.getPathJava(sc_id)).getPath())) {
                super.onBackPressed();
            } else {
                current_path = current_path.substring(0, current_path.lastIndexOf("/"));
                refresh();
            }
        }
    }

    private void setupUI() {
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.topAppBar.setTitle("Java/Kotlin Manager");
        setSupportActionBar(binding.topAppBar);

        binding.showOptionsButton.setOnClickListener(view -> hideShowOptionsButton(false));
        binding.closeButton.setOnClickListener(view -> hideShowOptionsButton(true));
        
        binding.createNewButton.setOnClickListener(v -> {
            showCreateDialog(isTreeViewEnabled ? fpu.getPathJava(sc_id) : current_path);
            hideShowOptionsButton(true);
        });
        
        binding.importNewButton.setOnClickListener(v -> {
            importTargetPath = isTreeViewEnabled ? fpu.getPathJava(sc_id) : current_path;
            dialog.show(getSupportFragmentManager(), "filePicker");
            hideShowOptionsButton(true);
        });

        binding.btnCreateEmpty.setOnClickListener(v -> {
            showCreateDialog(isTreeViewEnabled ? fpu.getPathJava(sc_id) : current_path);
        });
    }

    private void hideShowOptionsButton(boolean isHide) {
        binding.optionsLayout.animate().translationY(isHide ? 300 : 0).alpha(isHide ? 0 : 1).setInterpolator(new OvershootInterpolator());
        binding.showOptionsButton.animate().translationY(isHide ? 0 : 300).alpha(isHide ? 1 : 0).setInterpolator(new OvershootInterpolator());
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Search").setIcon(R.drawable.ic_mtrl_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 2, Menu.NONE, "Collapse All").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 3, Menu.NONE, "Sort by Name").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 4, Menu.NONE, "Sort by Type").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1 -> {
                binding.searchLayout.setVisibility(binding.searchLayout.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                if (binding.searchLayout.getVisibility() == View.VISIBLE) binding.searchEditText.requestFocus();
            }
            case 2 -> {
                expandedPaths.clear();
                if (isTreeViewEnabled) rebuildFlatList();
                else refresh();
            }
            case 3 -> {
                currentSortMode = SortMode.NAME;
                if (isTreeViewEnabled) rebuildFlatList();
                else refresh();
            }
            case 4 -> {
                currentSortMode = SortMode.TYPE;
                if (isTreeViewEnabled) rebuildFlatList();
                else refresh();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private String getPkgNameForPath(String targetPath) {
        String pkgName = getIntent().getStringExtra("pkgName");
        try {
            String trimmedPath = Helper.trimPath(fpu.getPathJava(sc_id));
            String substring = targetPath.substring(targetPath.indexOf(trimmedPath) + trimmedPath.length());
            if (substring.endsWith("/")) substring = substring.substring(0, substring.length() - 1);
            if (substring.startsWith("/")) substring = substring.substring(1);
            String replace = substring.replace("/", ".");
            return replace.isEmpty() ? pkgName : pkgName + "." + replace;
        } catch (Exception e) {
            return pkgName;
        }
    }

    private void showCreateDialog(String targetPath) {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle("Create new")
                .setMessage("File will be created in the selected directory.")
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            inputText.requestFocus();

            Button positiveButton = ((androidx.appcompat.app.AlertDialog) dialogInterface).getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                if (Helper.getText(inputText).isEmpty()) {
                    SketchwareUtil.toastError("Invalid file name");
                    return;
                }

                String name = Helper.getText(inputText);
                String packageName = getPkgNameForPath(targetPath);
                String extension;
                String newFileContent;
                int checkedChipId = dialogBinding.chipGroupTypes.getCheckedChipId();
                if (checkedChipId == R.id.chip_java_class) {
                    newFileContent = String.format(CLASS_TEMPLATE, packageName, name);
                    extension = ".java";
                } else if (checkedChipId == R.id.chip_java_activity) {
                    newFileContent = String.format(ACTIVITY_TEMPLATE, packageName, name);
                    extension = ".java";
                } else if (checkedChipId == R.id.chip_kotlin_class) {
                    newFileContent = String.format(KT_CLASS_TEMPLATE, packageName, name);
                    extension = ".kt";
                } else if (checkedChipId == R.id.chip_kotlin_activity) {
                    newFileContent = String.format(KT_ACTIVITY_TEMPLATE, packageName, name);
                    extension = ".kt";
                } else if (checkedChipId == R.id.chip_folder) {
                    FileUtil.makeDir(new File(targetPath, name).getAbsolutePath());
                    forceRefreshTree();
                    SketchwareUtil.toast("Folder was created successfully");
                    dialog.dismiss();
                    return;
                } else {
                    SketchwareUtil.toast("Select a file type");
                    return;
                }

                FileUtil.writeFile(new File(targetPath, name + extension).getAbsolutePath(), newFileContent);
                forceRefreshTree();
                SketchwareUtil.toast("File was created successfully");
                dialog.dismiss();
            });

            dialogBinding.chipFolder.setVisibility(View.VISIBLE);
            dialogBinding.chipJavaClass.setVisibility(View.VISIBLE);
            dialogBinding.chipJavaActivity.setVisibility(View.VISIBLE);
            dialogBinding.chipKotlinClass.setVisibility(View.VISIBLE);
            dialogBinding.chipKotlinActivity.setVisibility(View.VISIBLE);
        });

        dialog.show();
    }

    private void setupDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        options.setExtensions(new String[]{"java", "kt"});
        options.setTitle("Select Java/Kotlin file(s)");

        dialog = new FilePickerDialogFragment(options, new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        try { FileUtil.copyDirectory(file, new File(importTargetPath, file.getName())); } catch (IOException ignored) {}
                    } else {
                        String fileContent = FileUtil.readFile(file.getAbsolutePath());
                        if (fileContent.contains("package ")) {
                            fileContent = fileContent.replaceFirst(PACKAGE_DECL_REGEX, "package " + getPkgNameForPath(importTargetPath) + (file.getName().endsWith(".java") ? ";" : "") + "\n");
                        }
                        FileUtil.writeFile(new File(importTargetPath, file.getName()).getAbsolutePath(), fileContent);
                    }
                }
                forceRefreshTree();
            }
        });
    }

    private void showRenameDialog(int position) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;
        var renameOccurrencesCheckBox = dialogBinding.renameOccurrencesCheckBox;

        var dialog = new MaterialAlertDialogBuilder(this).setTitle("Rename " + filesAdapter.getFileName(position))
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Rename", (dialogInterface, i) -> {
            if (!Helper.getText(inputText).isEmpty()) {
                if (!filesAdapter.isFolder(position)) {
                    if (frc.getJavaManifestList().contains(filesAdapter.getFullName(position))) {
                        frc.getJavaManifestList().remove(filesAdapter.getFullName(position));
                        FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                        SketchwareUtil.toast("NOTE: Removed Activity from manifest");
                    }
                    if (renameOccurrencesCheckBox.isChecked()) {
                        String fileContent = FileUtil.readFile(filesAdapter.getItem(position));
                        FileUtil.writeFile(filesAdapter.getItem(position), fileContent.replaceAll(filesAdapter.getFileNameWoExt(position), FileUtil.getFileNameNoExtension(Helper.getText(inputText))));
                    }
                }
                FileUtil.renameFile(filesAdapter.getItem(position), new File(new File(filesAdapter.getItem(position)).getParent(), Helper.getText(inputText)).getAbsolutePath());
                forceRefreshTree();
                SketchwareUtil.toast("Renamed successfully");
            }
            dialogInterface.dismiss();
        }).create();

        inputText.setText(filesAdapter.getFileName(position));
        boolean isFolder = filesAdapter.isFolder(position);

        if (!isFolder) {
            renameOccurrencesCheckBox.setVisibility(View.VISIBLE);
            renameOccurrencesCheckBox.setText("Rename occurrences of \"" + filesAdapter.getFileNameWoExt(position) + "\" in file");
        }
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void showDeleteDialog(int position) {
        boolean isInManifest = frc.getJavaManifestList().contains(filesAdapter.getFullName(position));
        new MaterialAlertDialogBuilder(this).setTitle("Delete " + filesAdapter.getFileName(position) + "?")
                .setMessage("Are you sure you want to delete this " + (filesAdapter.isFolder(position) ? "folder" : "file") + "? " + (isInManifest ? "This will also remove it from AndroidManifest. " : "") + "This action cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
            if (!filesAdapter.isFolder(position) && isInManifest) {
                frc.getJavaManifestList().remove(filesAdapter.getFullName(position));
                FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
            }
            FileUtil.deleteFile(filesAdapter.getItem(position));
            forceRefreshTree();
            SketchwareUtil.toast("Deleted successfully");
        }).setNegativeButton(R.string.common_word_cancel, null).create().show();
    }

    private void forceRefreshTree() {
        rootNodes.clear();
        refresh();
    }

    private void sortTreePaths(ArrayList<String> paths) {
        paths.sort((p1, p2) -> {
            boolean isDir1 = new File(p1).isDirectory();
            boolean isDir2 = new File(p2).isDirectory();
            if (isDir1 && !isDir2) return -1;
            if (!isDir1 && isDir2) return 1;

            if (currentSortMode == SortMode.TYPE) {
                String ext1 = p1.substring(p1.lastIndexOf('.') + 1);
                String ext2 = p2.substring(p2.lastIndexOf('.') + 1);
                if (!ext1.equals(ext2)) return ext1.compareToIgnoreCase(ext2);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(new File(p1).getName(), new File(p2).getName());
        });
    }

    private void refresh() {
        if (!FileUtil.isExistFile(fpu.getPathJava(sc_id))) {
            FileUtil.makeDir(fpu.getPathJava(sc_id));
        }
        if (!FileUtil.isExistFile(fpu.getManifestJava(sc_id))) {
            FileUtil.writeFile(fpu.getManifestJava(sc_id), "");
        }

        isTreeViewEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW)
                            && ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_JAVA_TREE_VIEW);

        if (isTreeViewEnabled) {
            if (rootNodes.isEmpty()) {
                ArrayList<String> paths = new ArrayList<>();
                FileUtil.listDir(fpu.getPathJava(sc_id), paths);
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
                if (searchQuery.isEmpty() || new File(p).getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                    flatNodesList.add(new FileNode(p, 0)); 
                }
            }
            
            if (filesAdapter == null) {
                filesAdapter = new FilesAdapter();
                binding.filesListRecyclerView.setAdapter(filesAdapter);
            } else {
                filesAdapter.notifyDataSetChanged();
            }
            binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void rebuildFlatList() {
        flatNodesList.clear();
        for (FileNode node : rootNodes) addNodeToFlatListRecursive(node);
        
        if (filesAdapter == null) {
            filesAdapter = new FilesAdapter();
            binding.filesListRecyclerView.setAdapter(filesAdapter);
        } else {
            filesAdapter.notifyDataSetChanged();
        }
        binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addNodeToFlatListRecursive(FileNode node) {
        if (searchQuery.isEmpty() || node.name.toLowerCase().contains(searchQuery.toLowerCase()) || node.isFolder) {
            boolean hasMatchingChild = false;
            if (!searchQuery.isEmpty() && node.isFolder) {
                hasMatchingChild = searchTreeForMatch(node, searchQuery.toLowerCase());
                if (!hasMatchingChild && !node.name.toLowerCase().contains(searchQuery.toLowerCase())) {
                    return; 
                }
            }

            flatNodesList.add(node);
            
            if (node.isFolder && (expandedPaths.contains(node.path) || !searchQuery.isEmpty())) {
                ArrayList<String> paths = new ArrayList<>();
                FileUtil.listDir(node.path, paths);
                sortTreePaths(paths);
                for (String p : paths) {
                    addNodeToFlatListRecursive(new FileNode(p, node.depth + 1));
                }
            }
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

        boolean isExpanded = expandedPaths.contains(node.path);
        if (isExpanded) {
            expandedPaths.remove(node.path);
            int countRemoved = removeChildrenFromList(node.depth, position + 1);
            if (filesAdapter != null) {
                filesAdapter.notifyItemRangeRemoved(position + 1, countRemoved);
                filesAdapter.notifyItemChanged(position);
            }
        } else {
            expandedPaths.add(node.path);
            ArrayList<String> paths = new ArrayList<>();
            FileUtil.listDir(node.path, paths);
            sortTreePaths(paths);
            
            ArrayList<FileNode> childrenToInsert = new ArrayList<>();
            for (String p : paths) {
                childrenToInsert.add(new FileNode(p, node.depth + 1));
            }
            
            flatNodesList.addAll(position + 1, childrenToInsert);
            if (filesAdapter != null) {
                filesAdapter.notifyItemRangeInserted(position + 1, childrenToInsert.size());
                filesAdapter.notifyItemChanged(position);
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

    public static class FileNode {
        public String path;
        public String name;
        public boolean isFolder;
        public int depth;

        public FileNode(String p, int d) {
            path = p;
            name = new File(p).getName();
            isFolder = FileUtil.isDirectory(p);
            depth = d;
        }
    }

    private void showModernBottomSheet(FileNode node, int position) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        LinearLayout layout = new LinearLayout(this);
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
            layout.addView(createSheetItem("Create inside", R.drawable.ic_mtrl_file_present, () -> { bottomSheet.dismiss(); showCreateDialog(node.path); }));
            layout.addView(createSheetItem("Import here", R.drawable.ic_mtrl_file_download, () -> { bottomSheet.dismiss(); importTargetPath = node.path; dialog.show(getSupportFragmentManager(), "filePicker"); }));
        } else {
            boolean isActivityInManifest = frc.getJavaManifestList().contains(filesAdapter.getFullName(position));
            boolean isServiceInManifest = frc.getServiceManifestList().contains(filesAdapter.getFullName(position));

            if (isActivityInManifest) {
                layout.addView(createSheetItem("Remove Activity from manifest", R.drawable.ic_delete_white_24dp, () -> { bottomSheet.dismiss(); handleManifestAction(position, "removeAct"); }));
            } else if (!isServiceInManifest) {
                layout.addView(createSheetItem("Add as Activity to manifest", R.drawable.ic_mtrl_add, () -> { bottomSheet.dismiss(); handleManifestAction(position, "addAct"); }));
            }

            if (isServiceInManifest) {
                layout.addView(createSheetItem("Remove Service from manifest", R.drawable.ic_delete_white_24dp, () -> { bottomSheet.dismiss(); handleManifestAction(position, "removeSrv"); }));
            } else if (!isActivityInManifest) {
                layout.addView(createSheetItem("Add as Service to manifest", R.drawable.ic_mtrl_add, () -> { bottomSheet.dismiss(); handleManifestAction(position, "addSrv"); }));
            }

            layout.addView(createSheetItem("Edit", R.drawable.ic_mtrl_edit, () -> { bottomSheet.dismiss(); filesAdapter.goEditFile(position); }));
            layout.addView(createSheetItem("Edit with...", R.drawable.ic_mtrl_edit, () -> { 
                bottomSheet.dismiss(); 
                Intent launchIntent = new Intent(Intent.ACTION_VIEW);
                launchIntent.setDataAndType(Uri.fromFile(new File(filesAdapter.getItem(position))), "text/plain");
                startActivity(launchIntent);
            }));
        }

        layout.addView(createSheetItem("Rename", R.drawable.ic_mtrl_edit, () -> { bottomSheet.dismiss(); showRenameDialog(position); }));
        layout.addView(createSheetItem("Delete", R.drawable.ic_delete_white_24dp, () -> { bottomSheet.dismiss(); showDeleteDialog(position); }));

        bottomSheet.setContentView(layout);
        bottomSheet.show();
    }

    private void handleManifestAction(int position, String action) {
        String fullName = filesAdapter.getFullName(position);
        String nameWoExt = filesAdapter.getFileNameWoExt(position);
        
        switch (action) {
            case "addAct":
                frc.getJavaManifestList().add(fullName);
                FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                SketchwareUtil.toast("Successfully added " + nameWoExt + " as Activity to AndroidManifest");
                break;
            case "removeAct":
                if (frc.getJavaManifestList().remove(fullName)) {
                    FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                    SketchwareUtil.toast("Successfully removed Activity " + nameWoExt + " from AndroidManifest");
                } else {
                    SketchwareUtil.toast("Activity was not defined in AndroidManifest.");
                }
                break;
            case "addSrv":
                frc.getServiceManifestList().add(fullName);
                FileUtil.writeFile(fpu.getManifestService(sc_id), new Gson().toJson(frc.listServiceManifest));
                SketchwareUtil.toast("Successfully added " + nameWoExt + " as Service to AndroidManifest");
                break;
            case "removeSrv":
                if (frc.getServiceManifestList().remove(fullName)) {
                    FileUtil.writeFile(fpu.getManifestService(sc_id), new Gson().toJson(frc.listServiceManifest));
                    SketchwareUtil.toast("Successfully removed Service " + nameWoExt + " from AndroidManifest");
                } else {
                    SketchwareUtil.toast("Service was not defined in AndroidManifest.");
                }
                break;
        }
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
        row.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(SketchwareUtil.dpToPx(16));
        row.addView(tv, lp);

        row.setOnClickListener(v -> action.run());
        return row;
    }

    public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ViewHolder> {
        
        public FilesAdapter() {}

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageJavaItemHsBinding binding = ManageJavaItemHsBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileNode node = flatNodesList.get(position);
            String fileName = node.name;
            var binding = holder.binding;

            binding.title.setText(fileName);
            binding.more.setOnClickListener(v -> showModernBottomSheet(node, position));

            if (isTreeViewEnabled) {
                int indentPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, node.depth * 20, getResources().getDisplayMetrics());
                ViewGroup.LayoutParams spaceParams = binding.indentSpacer.getLayoutParams();
                spaceParams.width = indentPx;
                binding.indentSpacer.setLayoutParams(spaceParams);

                if (node.isFolder) {
                    binding.title.setTypeface(null, Typeface.BOLD);
                    binding.chevron.setVisibility(View.VISIBLE);
                    binding.chevron.animate().cancel();
                    binding.chevron.setRotation(expandedPaths.contains(node.path) ? 90f : 0f);
                    binding.icon.setImageResource(R.drawable.ic_mtrl_folder);
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageJavaActivity.this, R.attr.colorPrimary)); 
                } else {
                    binding.title.setTypeface(null, Typeface.NORMAL);
                    binding.chevron.setVisibility(View.GONE);
                    binding.icon.clearColorFilter();
                    int fileIcon = fileName.endsWith(".kt") ? R.drawable.ic_mtrl_kotlin : R.drawable.ic_mtrl_java;
                    binding.icon.setImageResource(fileIcon);
                }

                binding.getRoot().setOnClickListener(view -> {
                    if (node.isFolder) {
                        binding.chevron.animate().rotation(expandedPaths.contains(node.path) ? 0f : 90f).setDuration(200).start();
                        toggleFolder(node, holder.getAdapterPosition());
                    } else {
                        goEditFile(position);
                    }
                });

                binding.getRoot().setOnLongClickListener(view -> {
                    showModernBottomSheet(node, position);
                    return true;
                });
                
            } else {
                binding.indentSpacer.getLayoutParams().width = 0;
                binding.chevron.setVisibility(View.GONE);
                binding.title.setTypeface(null, Typeface.NORMAL);
                
                binding.icon.setVisibility(View.VISIBLE);
                binding.icon.clearColorFilter();

                if (node.isFolder) {
                    binding.icon.setImageResource(R.drawable.ic_mtrl_folder);
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageJavaActivity.this, R.attr.colorPrimary));
                } else if (fileName.endsWith(".kt")) {
                    binding.icon.setImageResource(R.drawable.ic_mtrl_kotlin);
                } else {
                    binding.icon.setImageResource(R.drawable.ic_mtrl_java);
                }

                binding.getRoot().setOnClickListener(view -> {
                    if (node.isFolder) {
                        current_path = node.path; 
                        refresh();
                    } else {
                        goEditFile(position);
                    }
                });

                binding.getRoot().setOnLongClickListener(view -> {
                    current_path = node.isFolder ? node.path : new File(node.path).getParent();
                    showModernBottomSheet(node, position);
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return flatNodesList.size();
        }

        public String getItem(int position) {
            return flatNodesList.get(position).path;
        }

        public String getFullName(int position) {
            String readFile = FileUtil.readFile(getItem(position));
            if (!readFile.contains("package ")) return getFileNameWoExt(position);
            Matcher m = Pattern.compile(PACKAGE_DECL_REGEX).matcher(readFile);
            if (m.find()) return m.group(1) + "." + getFileNameWoExt(position);
            return getFileNameWoExt(position);
        }

        public String getFileName(int position) {
            return flatNodesList.get(position).name;
        }

        public String getFileNameWoExt(int position) {
            return FileUtil.getFileNameNoExtension(getItem(position));
        }

        public boolean isFolder(int position) {
            return flatNodesList.get(position).isFolder;
        }

        public void goEditFile(int position) {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), SrcCodeEditor.class);
            intent.putExtra("java", "");
            intent.putExtra("title", getFileName(position));
            intent.putExtra("content", getItem(position));
            startActivity(intent);
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ManageJavaItemHsBinding binding;
            public ViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
