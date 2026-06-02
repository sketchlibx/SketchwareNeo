package mod.hey.studios.activity.managers.assets;

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
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import neo.sketchware.R;
import neo.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import neo.sketchware.databinding.DialogInputLayoutBinding;
import neo.sketchware.databinding.ManageFileBinding;
import neo.sketchware.databinding.ManageJavaItemHsBinding;
import neo.sketchware.utility.FilePathUtil;
import neo.sketchware.utility.FileUtil;
import neo.sketchware.utility.SketchwareUtil;
import neo.sketchware.utility.ThemeUtils;

@SuppressLint("SetTextI18n")
public class ManageAssetsActivity extends BaseAppCompatActivity {

    private String current_path;
    private FilePathUtil fpu;
    private AssetsAdapter assetsAdapter;
    private String sc_id;
    private ManageFileBinding binding;
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
        
        fpu = new FilePathUtil();
        current_path = Uri.parse(fpu.getPathAssets(sc_id)).getPath();

        setupUI();
        setupDialog();
        setupSearch();
        refresh();

        binding.filesListRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && binding.showOptionsButton.isExtended()) {
                    binding.showOptionsButton.shrink();
                } else if (dy < 0 && !binding.showOptionsButton.isExtended()) {
                    binding.showOptionsButton.extend();
                }
            }
        });
    }

    private void setupUI() {
        binding.topAppBar.setTitle("Assets Manager");
        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setNavigationOnClickListener(v -> onBackPressed());

        binding.showOptionsButton.setOnClickListener(view -> hideShowOptionsButton(false));
        binding.closeButton.setOnClickListener(view -> hideShowOptionsButton(true));
        
        binding.createNewButton.setOnClickListener(v -> {
            showCreateDialog(isTreeViewEnabled ? fpu.getPathAssets(sc_id) : current_path);
            hideShowOptionsButton(true);
        });
        
        binding.importNewButton.setOnClickListener(v -> {
            importTargetPath = isTreeViewEnabled ? fpu.getPathAssets(sc_id) : current_path;
            dialog.show(getSupportFragmentManager(), "filePicker");
            hideShowOptionsButton(true);
        });

        binding.btnCreateEmpty.setOnClickListener(v -> {
            showCreateDialog(isTreeViewEnabled ? fpu.getPathAssets(sc_id) : current_path);
        });
    }

    private void hideShowOptionsButton(boolean isHide) {
        binding.optionsLayout.animate()
                .translationY(isHide ? 300 : 0)
                .alpha(isHide ? 0 : 1)
                .setInterpolator(new OvershootInterpolator());

        binding.showOptionsButton.animate()
                .translationY(isHide ? 0 : 300)
                .alpha(isHide ? 1 : 0)
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
            if (Objects.equals(Uri.parse(current_path).getPath(), Uri.parse(fpu.getPathAssets(sc_id)).getPath())) {
                super.onBackPressed();
            } else {
                current_path = current_path.substring(0, current_path.lastIndexOf(File.separator));
                refresh();
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void showCreateDialog(String targetPath) {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle("Create new")
                .setMessage("If you're creating a file, make sure to add an extension.")
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = ((AlertDialog) dialogInterface).getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                String editable = Helper.getText(inputText).trim();

                if (editable.isEmpty()) {
                    SketchwareUtil.toastError("Invalid name");
                    return;
                }

                int checkedChipId = dialogBinding.chipGroupTypes.getCheckedChipId();
                if (checkedChipId == R.id.chip_file) {
                    FileUtil.writeFile(new File(targetPath, editable).getAbsolutePath(), "");
                } else if (checkedChipId == R.id.chip_folder) {
                    FileUtil.makeDir(new File(targetPath, editable).getAbsolutePath());
                } else {
                    SketchwareUtil.toast("Select a file type");
                    return;
                }

                forceRefreshTree();
                SketchwareUtil.toast("Created successfully");
                dialogInterface.dismiss();
            });
        });

        dialogBinding.chipFile.setVisibility(View.VISIBLE);
        dialogBinding.chipFolder.setVisibility(View.VISIBLE);

        dialog.setView(dialogBinding.getRoot());
        dialog.show();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void setupDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        options.setTitle("Select an asset file");

        dialog = new FilePickerDialogFragment(options, new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                if (files.isEmpty()) return;
                for (File file : files) {
                    try {
                        FileUtil.copyDirectory(file, new File(importTargetPath, file.getName()));
                    } catch (IOException e) {
                        SketchwareUtil.toastError("Couldn't import file! [" + e.getMessage() + "]");
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
                .setTitle("Rename " + assetsAdapter.getFileName(position))
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Rename", (dialogInterface, i) -> {
                    if (!Helper.getText(inputText).isEmpty()) {
                        FileUtil.renameFile(assetsAdapter.getItem(position), new File(new File(assetsAdapter.getItem(position)).getParent(), Helper.getText(inputText)).getAbsolutePath());
                        forceRefreshTree();
                        SketchwareUtil.toast("Renamed successfully");
                    }
                    dialogInterface.dismiss();
                })
                .create();

        inputText.setText(assetsAdapter.getFileName(position));
        dialog.show();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void showDeleteDialog(int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + assetsAdapter.getFileName(position) + "?")
                .setMessage("Are you sure you want to delete this " + (assetsAdapter.isFolder(position) ? "folder" : "file") + "? "
                        + "This action cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    FileUtil.deleteFile(assetsAdapter.getItem(position));
                    forceRefreshTree();
                    SketchwareUtil.toast("Deleted successfully");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .create()
                .show();
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
        if (!FileUtil.isExistFile(fpu.getPathAssets(sc_id))) {
            FileUtil.makeDir(fpu.getPathAssets(sc_id));
        }

        isTreeViewEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW)
                && ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ASSETS_TREE_VIEW);

        if (isTreeViewEnabled) {
            if (rootNodes.isEmpty()) {
                ArrayList<String> paths = new ArrayList<>();
                FileUtil.listDir(fpu.getPathAssets(sc_id), paths);
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

            if (assetsAdapter == null) {
                assetsAdapter = new AssetsAdapter();
                binding.filesListRecyclerView.setAdapter(assetsAdapter);
            } else {
                assetsAdapter.notifyDataSetChanged();
            }
            binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void rebuildFlatList() {
        flatNodesList.clear();
        for (FileNode node : rootNodes) addNodeToFlatListRecursive(node);

        if (assetsAdapter == null) {
            assetsAdapter = new AssetsAdapter();
            binding.filesListRecyclerView.setAdapter(assetsAdapter);
        } else {
            assetsAdapter.notifyDataSetChanged();
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
            if (assetsAdapter != null) {
                assetsAdapter.notifyItemRangeRemoved(position + 1, countRemoved);
                assetsAdapter.notifyItemChanged(position);
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
            if (assetsAdapter != null) {
                assetsAdapter.notifyItemRangeInserted(position + 1, childrenToInsert.size());
                assetsAdapter.notifyItemChanged(position);
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
            layout.addView(createSheetItem("Edit", R.drawable.ic_mtrl_edit, () -> { bottomSheet.dismiss(); assetsAdapter.goEditFile(position); }));
            layout.addView(createSheetItem("Edit with...", R.drawable.ic_mtrl_edit, () -> { 
                bottomSheet.dismiss(); 
                Intent launchIntent = new Intent(Intent.ACTION_VIEW);
                launchIntent.setDataAndType(Uri.fromFile(new File(assetsAdapter.getItem(position))), "*/*");
                startActivity(launchIntent);
            }));
        }

        layout.addView(createSheetItem("Rename", R.drawable.ic_mtrl_edit, () -> { bottomSheet.dismiss(); showRenameDialog(position); }));
        layout.addView(createSheetItem("Delete", R.drawable.ic_delete_white_24dp, () -> { bottomSheet.dismiss(); showDeleteDialog(position); }));

        bottomSheet.setContentView(layout);
        bottomSheet.show();
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

    public static class FileNode {
        public String path;
        public String name;
        public boolean isFolder;
        public boolean isExpanded;
        public int depth;

        public FileNode(String p, int d) {
            path = p;
            name = new File(p).getName();
            isFolder = FileUtil.isDirectory(p);
            depth = d;
            isExpanded = false;
        }
    }

    public class AssetsAdapter extends RecyclerView.Adapter<AssetsAdapter.AssetsViewHolder> {

        private static final String[] textExtensions = {
                ".txt", ".xml", ".java", ".json", ".csv", ".html", ".css", ".js",
                ".md", ".rtf", ".log", ".sql", ".yml", ".yaml", ".properties", ".ini",
                ".kt", ".toml", ".kts", ".php", ".py", ".ts", ".md", ".sh", ".c", ".h",
                ".hpp", ".cpp"
        };

        @NonNull
        @Override
        public AssetsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            ManageJavaItemHsBinding binding = ManageJavaItemHsBinding.inflate(inflater, parent, false);
            var layoutParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            binding.getRoot().setLayoutParams(layoutParams);
            return new AssetsViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull AssetsViewHolder holder, int position) {
            FileNode node = flatNodesList.get(position);
            String item = node.path;
            var binding = holder.binding;

            binding.title.setText(node.name);
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
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageAssetsActivity.this, R.attr.colorPrimary));
                } else {
                    binding.title.setTypeface(null, Typeface.NORMAL);
                    binding.chevron.setVisibility(View.INVISIBLE);
                    binding.icon.clearColorFilter();
                    
                    Glide.with(ManageAssetsActivity.this).clear(binding.icon);

                    try {
                        if (FileUtil.isImageFile(item)) {
                            Glide.with(ManageAssetsActivity.this)
                                    .load(new File(item))
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .override(100)
                                    .into(binding.icon);
                        } else {
                            binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                            binding.icon.setColorFilter(ThemeUtils.getColor(ManageAssetsActivity.this, R.attr.colorOnSurfaceVariant));
                        }
                    } catch (Exception ignored) {
                        binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                    }
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
                Glide.with(ManageAssetsActivity.this).clear(binding.icon);

                if (node.isFolder) {
                    binding.icon.setImageResource(R.drawable.ic_mtrl_folder);
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageAssetsActivity.this, R.attr.colorPrimary));
                } else {
                    try {
                        if (FileUtil.isImageFile(item)) {
                            Glide.with(ManageAssetsActivity.this).load(new File(item)).override(100).into(binding.icon);
                        } else {
                            binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                            binding.icon.setColorFilter(ThemeUtils.getColor(ManageAssetsActivity.this, R.attr.colorOnSurfaceVariant));
                        }
                    } catch (Exception ignored) {
                        binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                    }
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

        public String getFileName(int position) {
            return flatNodesList.get(position).name;
        }

        public boolean isFolder(int position) {
            return flatNodesList.get(position).isFolder;
        }

        public void goEditFile(int position) {
            if (Arrays.stream(textExtensions).anyMatch(getItem(position)::endsWith)) {
                Intent launchIntent = new Intent();
                launchIntent.setClass(getApplicationContext(), SrcCodeEditor.class);
                launchIntent.putExtra("title", getFileName(position));
                launchIntent.putExtra("content", getItem(position));
                startActivity(launchIntent);
            } else {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setDataAndType(Uri.fromFile(new File(getItem(position))), "*/*");
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(viewIntent);
            }
        }

        public class AssetsViewHolder extends RecyclerView.ViewHolder {
            ManageJavaItemHsBinding binding;
            public AssetsViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
