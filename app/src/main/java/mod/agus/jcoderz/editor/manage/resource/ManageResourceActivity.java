package mod.agus.jcoderz.editor.manage.resource;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
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
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.bobur.VectorDrawableLoader;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import neo.sketchware.R;
import neo.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import neo.sketchware.databinding.DialogInputLayoutBinding;
import neo.sketchware.databinding.ManageFileBinding;
import neo.sketchware.databinding.ManageJavaItemHsBinding;
import neo.sketchware.utility.FilePathUtil;
import neo.sketchware.utility.FileResConfig;
import neo.sketchware.utility.FileUtil;
import neo.sketchware.utility.SketchwareUtil;
import neo.sketchware.utility.ThemeUtils;

@SuppressLint("SetTextI18n")
public class ManageResourceActivity extends BaseAppCompatActivity {

    private CustomAdapter adapter;
    private FilePickerDialogFragment dialog;
    private FilePathUtil fpu;
    private FileResConfig frc;
    private String numProj;
    private String temp;
    private String importTargetPath;

    private ManageFileBinding binding;

    private boolean isTreeViewEnabled;
    private final ArrayList<FileNode> rootNodes = new ArrayList<>();
    private final ArrayList<FileNode> flatNodesList = new ArrayList<>();
    private final Set<String> expandedPaths = new HashSet<>();
    private String searchQuery = "";
    private SortMode currentSortMode = SortMode.NAME;

    public enum SortMode { NAME, TYPE }

    public static String getLastDirectory(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        String parentPath = path.substring(0, lastSlashIndex);
        lastSlashIndex = parentPath.lastIndexOf('/');
        return parentPath.substring(lastSlashIndex + 1);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageFileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getIntent().hasExtra("sc_id")) {
            numProj = getIntent().getStringExtra("sc_id");
        }
        Helper.fixFileprovider();
        frc = new FileResConfig(numProj);
        fpu = new FilePathUtil();
        setupDialog();
        checkDir();
        initToolbar();
        setupSearch();

        // FAB hide on scroll
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

    private void checkDir() {
        if (FileUtil.isExistFile(fpu.getPathResource(numProj))) {
            temp = fpu.getPathResource(numProj);
            refresh();
            return;
        }
        FileUtil.makeDir(fpu.getPathResource(numProj));
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/anim");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/drawable");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/drawable-xhdpi");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/layout");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/menu");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/values");
        checkDir();
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
        isTreeViewEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW)
                && ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_RESOURCE_TREE_VIEW);

        if (isTreeViewEnabled) {
            rootNodes.clear();
            ArrayList<String> paths = frc.getResourceFile(fpu.getPathResource(numProj));
            sortTreePaths(paths);
            for (String p : paths) {
                rootNodes.add(new FileNode(p, 0));
            }
            rebuildFlatList();
            handleFab();
        } else {
            ArrayList<String> resourceFile = frc.getResourceFile(temp);
            sortTreePaths(resourceFile);

            flatNodesList.clear();
            for (String p : resourceFile) {
                if (p.toLowerCase().contains(searchQuery.toLowerCase())) {
                    flatNodesList.add(new FileNode(p, 0));
                }
            }

            if (adapter == null) {
                adapter = new CustomAdapter();
                binding.filesListRecyclerView.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }
            checkEmptyState();
            handleFab();
        }
    }

    private void rebuildFlatList() {
        flatNodesList.clear();
        for (FileNode node : rootNodes) {
            addNodeToFlatListRecursive(node);
        }
        if (adapter == null) {
            adapter = new CustomAdapter();
            binding.filesListRecyclerView.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
        checkEmptyState();
    }

    private void addNodeToFlatListRecursive(FileNode node) {
        if (searchQuery.isEmpty() || node.name.toLowerCase().contains(searchQuery.toLowerCase()) || node.isFolder) {
            
            boolean hasMatchingChild = false;
            if (!searchQuery.isEmpty() && node.isFolder) {
                hasMatchingChild = searchTreeForMatch(node, searchQuery.toLowerCase());
                if (!hasMatchingChild && !node.name.toLowerCase().contains(searchQuery.toLowerCase())) {
                    return; // Hide folder if it doesn't match and has no matching children
                }
            }

            flatNodesList.add(node);
            
            if (node.isFolder && (expandedPaths.contains(node.path) || !searchQuery.isEmpty())) {
                ArrayList<String> paths = frc.getResourceFile(node.path);
                sortTreePaths(paths);
                for (String p : paths) {
                    addNodeToFlatListRecursive(new FileNode(p, node.depth + 1));
                }
            }
        }
    }

    private boolean searchTreeForMatch(FileNode folder, String query) {
        ArrayList<String> paths = frc.getResourceFile(folder.path);
        for (String p : paths) {
            if (new File(p).getName().toLowerCase().contains(query)) return true;
            if (new File(p).isDirectory() && searchTreeForMatch(new FileNode(p, 0), query)) return true;
        }
        return false;
    }

    private void toggleFolder(FileNode node, int position) {
        if (!node.isFolder) return;

        boolean isExpanded = expandedPaths.contains(node.path);
        if (isExpanded) {
            expandedPaths.remove(node.path);
            int countRemoved = removeChildrenFromList(node.depth, position + 1);
            adapter.notifyItemRangeRemoved(position + 1, countRemoved);
            adapter.notifyItemChanged(position);
        } else {
            expandedPaths.add(node.path);
            ArrayList<String> paths = frc.getResourceFile(node.path);
            sortTreePaths(paths);
            
            ArrayList<FileNode> childrenToInsert = new ArrayList<>();
            for (String p : paths) {
                childrenToInsert.add(new FileNode(p, node.depth + 1));
            }
            
            flatNodesList.addAll(position + 1, childrenToInsert);
            adapter.notifyItemRangeInserted(position + 1, childrenToInsert.size());
            adapter.notifyItemChanged(position);
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

    private void checkEmptyState() {
        binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    public static class FileNode {
        public String path;
        public String name;
        public boolean isFolder;
        public int depth;

        public FileNode(String p, int d) {
            path = p;
            name = Uri.parse(p).getLastPathSegment();
            isFolder = FileUtil.isDirectory(p);
            depth = d;
        }
    }

    private boolean isInMainDirectory() {
        return temp.equals(fpu.getPathResource(numProj));
    }

    private void handleFab() {
        var optionsButton = binding.showOptionsButton;
        if (isInMainDirectory() || isTreeViewEnabled) {
            optionsButton.setText("Create Resource");
            hideShowOptionsButton(true);
        } else {
            optionsButton.setText("Create or import");
        }
    }

    private void initToolbar() {
        binding.topAppBar.setTitle("Resource Manager");
        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setNavigationOnClickListener(v -> onBackPressed());

        binding.showOptionsButton.setOnClickListener(view -> {
            if (isInMainDirectory() || isTreeViewEnabled) {
                createNewDialog(true, isTreeViewEnabled ? fpu.getPathResource(numProj) : temp);
                return;
            }
            hideShowOptionsButton(false);
        });

        binding.btnCreateEmpty.setOnClickListener(view -> createNewDialog(true, isTreeViewEnabled ? fpu.getPathResource(numProj) : temp));
        binding.closeButton.setOnClickListener(view -> hideShowOptionsButton(true));
        binding.createNewButton.setOnClickListener(v -> {
            createNewDialog(false, isTreeViewEnabled ? fpu.getPathResource(numProj) : temp);
            hideShowOptionsButton(true);
        });
        binding.importNewButton.setOnClickListener(v -> {
            importTargetPath = isTreeViewEnabled ? fpu.getPathResource(numProj) : temp;
            dialog.show(getSupportFragmentManager(), "filePicker");
            hideShowOptionsButton(true);
        });
    }

    private void setupSearch() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                refresh();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Search").setIcon(R.drawable.ic_mtrl_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 2, Menu.NONE, "Collapse All").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 3, Menu.NONE, "Sort by Name").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 4, Menu.NONE, "Sort by Type").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return super.onCreateOptionsMenu(menu);
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
                refresh();
            }
            case 3 -> {
                currentSortMode = SortMode.NAME;
                refresh();
            }
            case 4 -> {
                currentSortMode = SortMode.TYPE;
                refresh();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void hideShowOptionsButton(boolean isHide) {
        binding.optionsLayout.animate().translationY(isHide ? 300 : 0).alpha(isHide ? 0 : 1).setInterpolator(new OvershootInterpolator());
        binding.showOptionsButton.animate().translationY(isHide ? 0 : 300).alpha(isHide ? 1 : 0).setInterpolator(new OvershootInterpolator());
    }

    @Override
    public void onBackPressed() {
        if (binding.searchLayout.getVisibility() == View.VISIBLE) {
            binding.searchLayout.setVisibility(View.GONE);
            binding.searchEditText.setText("");
            return;
        }

        if (isTreeViewEnabled) {
            setResult(RESULT_OK);
            finish();
            super.onBackPressed();
        } else {
            try {
                temp = temp.substring(0, temp.lastIndexOf("/"));
                if (temp.contains("resource")) {
                    refresh();
                    return;
                }
            } catch (IndexOutOfBoundsException ignored) {}
            setResult(RESULT_OK);
            finish();
            super.onBackPressed();
        }
    }

    private void createNewDialog(boolean isFolder, String targetPath) {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle(isFolder ? "Create a new folder" : "Create a new file")
                .setMessage("Enter a name for the new " + (isFolder ? "folder" : "file"))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialogBinding.chipGroupTypes.setVisibility(View.GONE);
        if (!isFolder) dialogBinding.inputText.setText(".xml");

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (Helper.getText(dialogBinding.inputText).isEmpty()) {
                    SketchwareUtil.toastError("Invalid name");
                    return;
                }

                String name = Helper.getText(dialogBinding.inputText);
                String path = new File(targetPath, name).getAbsolutePath();

                if (FileUtil.isExistFile(path)) {
                    SketchwareUtil.toastError("File exists already");
                    return;
                }
                if (isFolder) {
                    FileUtil.makeDir(path);
                } else {
                    FileUtil.writeFile(path, "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
                }
                refresh();
                SketchwareUtil.toast("Created successfully");
                dialog.dismiss();
            });

            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialogBinding.inputText.requestFocus();
            if (!isFolder) dialogBinding.inputText.setSelection(0);
        });

        dialog.show();
    }

        private void setupDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        options.setTitle("Select resource files");

        dialog = new FilePickerDialogFragment(options, new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                if (files.isEmpty()) return;
                for (File file : files) {
                    try {
                        FileUtil.copyDirectory(file, new File(importTargetPath + File.separator + file.getName()));
                    } catch (IOException e) {
                        SketchwareUtil.toastError("Import failed: " + e.getMessage());
                    }
                }
                refresh();
            }
        });
    }

    private void showRenameDialog(String path) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Rename")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (dialogInterface, i) -> {
                    String newName = Helper.getText(dialogBinding.inputText);
                    if (!newName.isEmpty()) {
                        if (FileUtil.renameFile(path, path.substring(0, path.lastIndexOf("/")) + "/" + newName)) {
                            SketchwareUtil.toast("Renamed");
                        } else {
                            SketchwareUtil.toastError("Rename failed");
                        }
                        refresh();
                    }
                }).create();

        dialogBinding.inputText.setText(new File(path).getName());
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialogBinding.inputText.requestFocus();
    }

    private void showDeleteDialog(String path) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + new File(path).getName() + "?")
                .setMessage("Are you sure you want to delete this? This action cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    FileUtil.deleteFile(path);
                    refresh();
                    SketchwareUtil.toast("Deleted");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void goEdit(String path) {
        if (path.endsWith("xml")) {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("title", Uri.parse(path).getLastPathSegment());
            intent.putExtra("content", path);
            intent.putExtra("xml", "");
            if (getIntent().hasExtra("sc_id")) intent.putExtra("sc_id", numProj);
            startActivity(intent);
        } else {
            SketchwareUtil.toast("Only XML files can be edited");
        }
    }

    private void showModernBottomSheet(FileNode node) {
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
            layout.addView(createSheetItem("Create file inside", R.drawable.ic_mtrl_file_present, () -> { bottomSheet.dismiss(); createNewDialog(false, node.path); }));
            layout.addView(createSheetItem("Create folder inside", R.drawable.ic_mtrl_folder, () -> { bottomSheet.dismiss(); createNewDialog(true, node.path); }));
            layout.addView(createSheetItem("Import here", R.drawable.ic_mtrl_file_download, () -> { bottomSheet.dismiss(); importTargetPath = node.path; dialog.show(getSupportFragmentManager(), "filePicker"); }));
        } else {
            layout.addView(createSheetItem("Edit", R.drawable.ic_mtrl_edit, () -> { bottomSheet.dismiss(); goEdit(node.path); }));
        }
        layout.addView(createSheetItem("Rename", R.drawable.ic_mtrl_edit, () -> { bottomSheet.dismiss(); showRenameDialog(node.path); }));
        layout.addView(createSheetItem("Delete", R.drawable.ic_delete_white_24dp, () -> { bottomSheet.dismiss(); showDeleteDialog(node.path); }));

        bottomSheet.setContentView(layout);
        bottomSheet.show();
    }

        private View createSheetItem(String text, int iconRes, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
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

    private class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageJavaItemHsBinding binding = ManageJavaItemHsBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileNode node = flatNodesList.get(position);
            var binding = holder.binding;

            binding.title.setText(node.name);
            binding.more.setOnClickListener(v -> showModernBottomSheet(node));

            if (isTreeViewEnabled) {
                // Indent Space
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
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageResourceActivity.this, R.attr.colorPrimary));
                } else {
                    binding.title.setTypeface(null, Typeface.NORMAL);
                    binding.chevron.setVisibility(View.INVISIBLE); // Keep space
                    binding.icon.clearColorFilter();
                    
                    Glide.with(ManageResourceActivity.this).clear(binding.icon); // Prevent image flashing

                    try {
                        if (FileUtil.isImageFile(node.path)) {
                            Glide.with(ManageResourceActivity.this)
                                 .load(new File(node.path))
                                 .diskCacheStrategy(DiskCacheStrategy.ALL)
                                 .override(100)
                                 .into(binding.icon);
                        } else if (node.path.endsWith(".xml") && "drawable".equals(getLastDirectory(node.path))) {
                            new VectorDrawableLoader().setImageVectorFromFile(binding.icon, node.path);
                        } else {
                            binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                            binding.icon.setColorFilter(ThemeUtils.getColor(ManageResourceActivity.this, R.attr.colorOnSurfaceVariant));
                        }
                    } catch (Exception e) {
                        binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                    }
                }

                binding.getRoot().setOnClickListener(v -> {
                    if (node.isFolder) {
                        binding.chevron.animate().rotation(expandedPaths.contains(node.path) ? 0f : 90f).setDuration(200).start();
                        toggleFolder(node, holder.getAdapterPosition());
                    } else {
                        goEdit(node.path);
                    }
                });

                binding.getRoot().setOnLongClickListener(v -> { showModernBottomSheet(node); return true; });
            } else {
                // Classic Mode fallback
                binding.indentSpacer.getLayoutParams().width = 0;
                binding.chevron.setVisibility(View.GONE);
                binding.title.setTypeface(null, Typeface.NORMAL);
                
                if (node.isFolder) {
                    binding.icon.setImageResource(R.drawable.ic_mtrl_folder);
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageResourceActivity.this, R.attr.colorPrimary));
                } else {
                    binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                }

                binding.getRoot().setOnClickListener(v -> {
                    if (node.isFolder) {
                        temp = node.path;
                        refresh();
                    } else goEdit(node.path);
                });
            }
        }

        @Override
        public int getItemCount() {
            return flatNodesList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ManageJavaItemHsBinding binding;
            public ViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
