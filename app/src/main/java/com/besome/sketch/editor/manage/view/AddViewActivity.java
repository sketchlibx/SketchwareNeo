package com.besome.sketch.editor.manage.view;

import android.content.Intent;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import a.a.a.YB;
import a.a.a.bB;
import a.a.a.rq;
import a.a.a.uq;
import a.a.a.wB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageScreenActivityAddTempBinding;

public class AddViewActivity extends BaseAppCompatActivity {

    public static final int REQUEST_CODE_EDIT = 265;
    public static final int REQUEST_CODE_ADD = 264;
    public static final int REQUEST_CODE_PRESET_SELECTION = 276;
    public static final int VIEW_TYPE_ACTIVITY = 0;
    public static final int VIEW_TYPE_FRAGMENT = 1;
    public static final int VIEW_TYPE_DIALOG_FRAGMENT = 2;
    public static final int VIEW_TYPE_BOTTOM_DIALOG_FRAGMENT = 3;
    private static final int MENU_EDIT_DETAILS = 0x00E0D001;
    private static final String EXTRA_PROJECT_ID = "sc_id";
    private static final int FEATURE_TYPE_STATUS_BAR = 0;
    private static final int FEATURE_TYPE_TOOLBAR = 1;
    private static final int FEATURE_TYPE_DRAWER = 2;
    private static final int FEATURE_TYPE_FAB = 3;
    private YB nameValidator;
    private boolean featureStatusBar, featureToolbar, featureFab, featureDrawer;
    private int requestCode;
    private ProjectFileBean projectFileBean;
    private String presetName;
    private ArrayList<FeatureItem> featureItems;
    private FeaturesAdapter featuresAdapter;
    private ArrayList<String> screenNames;
    private ManageScreenActivityAddTempBinding binding;
    private String projectId;
    private boolean editDetailsEnabled;

    private void handleFeatureAnimation(FeatureItem featureItem) {
        int type = featureItem.type;
        switch (type) {
            case FEATURE_TYPE_STATUS_BAR -> {
                if (featureItem.isEnabled) {
                    resetTranslationY(binding.previewStatusbar);
                    if (featureToolbar) {
                        resetTranslationY(binding.previewToolbar);
                    }
                } else {
                    slideInVertically(binding.previewStatusbar);
                    if (featureToolbar) {
                        binding.previewToolbar.animate().translationY((float) -binding.previewStatusbar.getMeasuredHeight()).start();
                    } else {
                        slideOutPreviewToolbar();
                    }
                }
            }
            case FEATURE_TYPE_TOOLBAR -> {
                if (featureItem.isEnabled) {
                    if (!featureStatusBar) {
                        binding.previewToolbar.animate().translationY((float) -binding.previewStatusbar.getMeasuredHeight()).start();
                    } else {
                        resetTranslationY(binding.previewToolbar);
                    }
                } else if (!featureStatusBar) {
                    slideOutPreviewToolbar();
                } else {
                    slideInVertically(binding.previewToolbar);
                }
            }
            case FEATURE_TYPE_DRAWER -> {
                if (featureItem.isEnabled) {
                    resetTranslationX(binding.previewDrawer);
                } else {
                    slideOutHorizontally(binding.previewDrawer, "left");
                }
            }
            case FEATURE_TYPE_FAB -> {
                if (featureItem.isEnabled) {
                    resetTranslationX(binding.previewFab);
                } else {
                    slideOutHorizontally(binding.previewFab, "right");
                }
            }
        }
    }

    private boolean isValid(YB validator) {
        return validator.b();
    }

    private void slideOutHorizontally(View view, String direction) {
        if ("left".equals(direction)) {
            view.animate().translationX((float) -view.getMeasuredWidth()).start();
        } else {
            view.animate().translationX((float) view.getMeasuredWidth()).start();
        }
    }

    private void slideOutVertically(View view) {
        view.animate().translationY((float) view.getMeasuredHeight()).start();
    }

    private void slideOutPreviewToolbar() {
        binding.previewToolbar.animate().translationY((float) -(binding.previewStatusbar.getMeasuredHeight() + binding.previewToolbar.getMeasuredHeight())).start();
    }

    private void slideInVertically(View view) {
        view.animate().translationY((float) -view.getMeasuredHeight()).start();
    }

    private void disableDrawer() {
        for (int i = 0; i < featureItems.size(); i++) {
            FeatureItem item = featureItems.get(i);
            if (item.type == 2) {
                item.isEnabled = false;
                featuresAdapter.notifyItemChanged(i);
                break;
            }
        }
    }

    private void enableToolbar() {
        for (int i = 0; i < featureItems.size(); i++) {
            FeatureItem item = featureItems.get(i);
            if (item.type == 1) {
                item.isEnabled = true;
                featuresAdapter.notifyItemChanged(i);
                break;
            }
        }
    }

    private void resetTranslationX(View view) {
        view.animate().translationX(0.0F).start();
    }

    private void resetTranslationY(View view) {
        view.animate().translationY(0.0F).start();
    }

    private ArrayList<ViewBean> getPresetData(String presetName) {
        return rq.f(presetName);
    }

    private void initItem(int featureOptions) {
        featureToolbar = (featureOptions & ProjectFileBean.OPTION_ACTIVITY_TOOLBAR) == ProjectFileBean.OPTION_ACTIVITY_TOOLBAR;
        featureStatusBar = (featureOptions & ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN) != ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN;
        featureFab = (featureOptions & ProjectFileBean.OPTION_ACTIVITY_FAB) == ProjectFileBean.OPTION_ACTIVITY_FAB;
        featureDrawer = (featureOptions & ProjectFileBean.OPTION_ACTIVITY_DRAWER) == ProjectFileBean.OPTION_ACTIVITY_DRAWER;
    }

    private void initializeItems() {
        featureItems = new ArrayList<>();
        featureItems.add(new FeatureItem(0, R.drawable.ic_statusbar_color_48dp, "StatusBar", featureStatusBar));
        featureItems.add(new FeatureItem(1, R.drawable.ic_toolbar_color_48dp, "Toolbar", featureToolbar));
        featureItems.add(new FeatureItem(2, R.drawable.ic_drawer_color_48dp, "Drawer", featureDrawer));
        featureItems.add(new FeatureItem(3, R.drawable.fab_color, "FAB", featureFab));
        featuresAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 276 && resultCode == RESULT_OK) {
            ProjectFileBean presetData = data.getParcelableExtra("preset_data");
            presetName = presetData.presetName;
            initItem(presetData.options);
            initializeItems();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageScreenActivityAddTempBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setTitle("Create new");
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        Intent intent1 = getIntent();
        screenNames = intent1.getStringArrayListExtra("screen_names");
        requestCode = intent1.getIntExtra("request_code", REQUEST_CODE_ADD);
        projectFileBean = intent1.getParcelableExtra("project_file");
        projectId = intent1.getStringExtra(EXTRA_PROJECT_ID);
        if (projectFileBean != null) {
            binding.toolbar.setTitle("Edit " + projectFileBean.fileName);
        }
        if (requestCode == REQUEST_CODE_EDIT && projectFileBean != null) {
            MenuItem editItem = binding.toolbar.getMenu().add(Menu.NONE, MENU_EDIT_DETAILS, Menu.NONE, "Edit screen");
            editItem.setIcon(R.drawable.ic_mtrl_edit);
            editItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            binding.toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == MENU_EDIT_DETAILS) {
                    enableEditDetails();
                    item.setVisible(false);
                    return true;
                }
                return false;
            });
        }

        featuresAdapter = new FeaturesAdapter();
        binding.featureTypes.setAdapter(featuresAdapter);

        binding.keyboardSettingsSelector.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.select_visible || checkedId == R.id.select_unspecified) {
                    resetTranslationY(binding.imgKeyboard);
                } else {
                    slideOutVertically(binding.imgKeyboard);
                }
            }
        });

        binding.viewTypeSelector.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                setManifestViewState(checkedId == R.id.select_activity);
            }
        });

        binding.btnSave.setOnClickListener(v -> {
            if (REQUEST_CODE_EDIT == requestCode) {
                handleEditFile();
            } else if (isValid(nameValidator)) {
                handleCreateFile();
            }
        });

        binding.btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        if (requestCode == REQUEST_CODE_EDIT) {
            handleEditModeInitialization();
        } else {
            handleCreateModeInitialization();
        }
        initializeItems();
    }

    private void handleEditFile() {
        int options = getCurrentOptions();
        int orientation = getSelectedButtonIndex(binding.screenOrientationSelector);
        int keyboardSetting = getSelectedButtonIndex(binding.keyboardSettingsSelector);
        if (orientation < 0) orientation = projectFileBean.orientation;
        if (keyboardSetting < 0) keyboardSetting = projectFileBean.keyboardSetting;

        if (!editDetailsEnabled) {
            projectFileBean.orientation = orientation;
            projectFileBean.keyboardSetting = keyboardSetting;
            projectFileBean.options = options;
            finishEditSuccessfully(projectFileBean);
            return;
        }

        String baseName = Helper.getText(binding.edName).trim();
        if (!baseName.matches("[a-z][a-z0-9_]*")) {
            binding.tiName.setError("Use lowercase letters; start with a letter and use numbers or underscores only.");
            return;
        }
        if (hasReservedSuffix(baseName)) {
            binding.tiName.setError("Do not include a screen-type suffix in the name; choose the type below.");
            return;
        }
        if (!isValid(nameValidator)) return;
        binding.tiName.setError(null);

        String newFileName = baseName + getSuffix(binding.viewTypeSelector);
        boolean nameOrTypeChanged = !newFileName.equals(projectFileBean.fileName);
        if ("main".equalsIgnoreCase(projectFileBean.fileName) && nameOrTypeChanged) {
            binding.tiName.setError("The main screen name and type cannot be changed safely.");
            return;
        }
        if (nameOrTypeChanged && !isScreenNameAvailable(newFileName, projectFileBean.fileName)) {
            binding.tiName.setError("A screen or custom view with this name already exists.");
            return;
        }
        if (nameOrTypeChanged && (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER) != featureDrawer)) {
            showEditError("Save the Drawer option separately before renaming or changing the screen type.");
            return;
        }
        if (!getSuffix(binding.viewTypeSelector).isEmpty() && (featureDrawer || featureFab)) {
            showEditError("Disable Drawer and FAB before changing this screen to a Fragment type.");
            return;
        }

        ProjectFileBean updated = new ProjectFileBean(
                projectFileBean.fileType, newFileName, orientation, keyboardSetting, options);
        updated.presetName = projectFileBean.presetName;
        updated.theme = projectFileBean.theme;

        if (!nameOrTypeChanged) {
            projectFileBean.copy(updated);
            finishEditSuccessfully(projectFileBean);
            return;
        }
        if (projectId == null || projectId.trim().isEmpty()) {
            showEditError("The project ID is missing. Reopen this screen from View Manager and try again; no changes were saved.");
            return;
        }

        AlertDialog progress = new MaterialAlertDialogBuilder(this)
                .setTitle("Updating screen")
                .setMessage("Renaming the screen and updating linked project data…")
                .setCancelable(false)
                .create();
        progress.show();
        new Thread(() -> {
            ScreenTransferManager.Result result = ScreenTransferManager.renameScreen(projectId, projectFileBean, updated);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    progress.dismiss();
                    return;
                }
                progress.dismiss();
                if (result.success) {
                    projectFileBean.copy(updated);
                    finishEditSuccessfully(projectFileBean);
                } else {
                    showEditError(result.message);
                }
            });
        }, "RenameSketchwareScreen").start();
    }

    private int getCurrentOptions() {
        int options = featureToolbar ? ProjectFileBean.OPTION_ACTIVITY_TOOLBAR : 0;
        if (!featureStatusBar) options |= ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN;
        if (featureFab) options |= ProjectFileBean.OPTION_ACTIVITY_FAB;
        if (featureDrawer) options |= ProjectFileBean.OPTION_ACTIVITY_DRAWER;
        return options;
    }

    private void finishEditSuccessfully(ProjectFileBean resultBean) {
        Intent intent = new Intent();
        intent.putExtra("project_file", resultBean);
        setResult(RESULT_OK, intent);
        bB.a(getApplicationContext(), getString(R.string.design_manager_message_edit_complete, new Object[0]), bB.TOAST_NORMAL).show();
        finish();
    }

    private void showEditError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Could not update screen")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean isScreenNameAvailable(String candidate, String currentName) {
        if (screenNames == null) return false;
        for (String existing : screenNames) {
            if (existing == null || existing.equalsIgnoreCase(currentName)) continue;
            if (existing.equalsIgnoreCase(candidate)) return false;
        }
        return true;
    }

    private boolean hasReservedSuffix(String name) {
        return name.endsWith("_fragment") || name.endsWith("_dialog_fragment")
                || name.endsWith("_bottomdialog_fragment");
    }

    private String stripScreenSuffix(String fileName) {
        if (fileName == null) return "";
        if (fileName.endsWith("_bottomdialog_fragment")) return fileName.substring(0, fileName.length() - "_bottomdialog_fragment".length());
        if (fileName.endsWith("_dialog_fragment")) return fileName.substring(0, fileName.length() - "_dialog_fragment".length());
        if (fileName.endsWith("_fragment")) return fileName.substring(0, fileName.length() - "_fragment".length());
        return fileName;
    }

    private int getTypeIndexFromFileName(String fileName) {
        if (fileName != null && fileName.endsWith("_bottomdialog_fragment")) return VIEW_TYPE_BOTTOM_DIALOG_FRAGMENT;
        if (fileName != null && fileName.endsWith("_dialog_fragment")) return VIEW_TYPE_DIALOG_FRAGMENT;
        if (fileName != null && fileName.endsWith("_fragment")) return VIEW_TYPE_FRAGMENT;
        return VIEW_TYPE_ACTIVITY;
    }

    private int getTypeButtonId(int typeIndex) {
        return switch (typeIndex) {
            case VIEW_TYPE_FRAGMENT -> R.id.select_fragment;
            case VIEW_TYPE_DIALOG_FRAGMENT -> R.id.select_dialogfragment;
            case VIEW_TYPE_BOTTOM_DIALOG_FRAGMENT -> R.id.select_bottomsheetdialogfragment;
            default -> R.id.select_activity;
        };
    }

    private void enableEditDetails() {
        if (editDetailsEnabled || projectFileBean == null) return;
        editDetailsEnabled = true;
        binding.edName.setEnabled(true);
        binding.tiName.setHint("Screen name (without type suffix)");
        binding.tiName.setError(null);
        binding.addViewTypeSelectorLayout.setVisibility(View.VISIBLE);
        binding.viewOrientationSelectorLayout.setVisibility(View.VISIBLE);
        binding.viewKeyboardSettingsSelectorLayout.setVisibility(View.VISIBLE);
        binding.edName.setText(stripScreenSuffix(projectFileBean.fileName));
        binding.edName.setSelection(binding.edName.length());
        binding.viewTypeSelector.check(getTypeButtonId(getTypeIndexFromFileName(projectFileBean.fileName)));
        setManifestViewState(getTypeIndexFromFileName(projectFileBean.fileName) == VIEW_TYPE_ACTIVITY);
    }

    private void handleCreateFile() {
        String fileName = Helper.getText(binding.edName) + getSuffix(binding.viewTypeSelector);
        ProjectFileBean projectFileBean = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY, fileName, getSelectedButtonIndex(binding.screenOrientationSelector), getSelectedButtonIndex(binding.keyboardSettingsSelector), featureToolbar, !featureStatusBar, featureFab, featureDrawer);
        Intent intent = new Intent();
        intent.putExtra("project_file", projectFileBean);
        if (presetName != null) {
            intent.putExtra("preset_views", getPresetData(presetName));
        }
        setResult(RESULT_OK, intent);
        bB.a(getApplicationContext(), getString(R.string.design_manager_message_add_complete, new Object[0]), bB.TOAST_NORMAL).show();
        finish();
    }

    private void handleEditModeInitialization() {
        nameValidator = new YB(getApplicationContext(), binding.tiName, uq.b, new ArrayList<>(), projectFileBean.fileName);
        binding.edName.setText(stripScreenSuffix(projectFileBean.fileName));
        binding.edName.setEnabled(false);
        binding.edName.setBackgroundResource(R.color.transparent);
        binding.tiName.setHint("Screen name");
        initItem(projectFileBean.options);
        binding.addViewTypeSelectorLayout.setVisibility(View.GONE);
        if (getTypeIndexFromFileName(projectFileBean.fileName) != VIEW_TYPE_ACTIVITY) {
            binding.viewOrientationSelectorLayout.setVisibility(View.GONE);
            binding.viewKeyboardSettingsSelectorLayout.setVisibility(View.GONE);
        }
        int orientation = projectFileBean.orientation;
        if (orientation < 0 || orientation >= binding.screenOrientationSelector.getChildCount()) orientation = 2;
        int keyboardSetting = projectFileBean.keyboardSetting;
        if (keyboardSetting < 0 || keyboardSetting >= binding.keyboardSettingsSelector.getChildCount()) keyboardSetting = 0;
        binding.screenOrientationSelector.check(binding.screenOrientationSelector.getChildAt(orientation).getId());
        binding.keyboardSettingsSelector.check(binding.keyboardSettingsSelector.getChildAt(keyboardSetting).getId());
        binding.viewTypeSelector.check(getTypeButtonId(getTypeIndexFromFileName(projectFileBean.fileName)));

        binding.imgKeyboard.post(() -> {
            if (binding.keyboardSettingsSelector.getCheckedButtonId() == R.id.select_hidden) {
                slideOutVertically(binding.imgKeyboard);
            }
        });
    }

    private void handleCreateModeInitialization() {
        featureToolbar = true;
        featureStatusBar = true;
        nameValidator = new YB(getApplicationContext(), binding.tiName, uq.b, screenNames);
    }


    private String getSuffix(MaterialButtonToggleGroup toggleGroup) {
        return switch (getSelectedButtonIndex(toggleGroup)) {
            case VIEW_TYPE_FRAGMENT -> "_fragment";
            case VIEW_TYPE_DIALOG_FRAGMENT -> "_dialog_fragment";
            case VIEW_TYPE_BOTTOM_DIALOG_FRAGMENT -> "_bottomdialog_fragment";
            default -> "";
        };
    }

    public int getSelectedButtonIndex(MaterialButtonToggleGroup toggleGroup) {
        for (int i = 0; i < toggleGroup.getChildCount(); i++) {
            var button = toggleGroup.getChildAt(i);
            if (toggleGroup.getCheckedButtonIds().contains(button.getId())) {
                return i;
            }
        }
        return -1;  // Return -1 if no button is selected
    }

    public void makeTransitionAnimation(LinearLayout linearLayout) {
        AutoTransition autoTransition = new AutoTransition();
        autoTransition.setDuration(200);
        TransitionManager.beginDelayedTransition(linearLayout, autoTransition);
    }

    private void setManifestViewState(boolean vis) {
        if (vis) {
            resetTranslationX(binding.viewOrientationSelectorLayout);
            resetTranslationX(binding.viewKeyboardSettingsSelectorLayout);
        } else {
            slideOutHorizontally(binding.viewOrientationSelectorLayout, "left");
            slideOutHorizontally(binding.viewKeyboardSettingsSelectorLayout, "left");
        }
    }

    private static class FeatureItem {
        public int type;
        public int previewImg;
        public String name;
        public boolean isEnabled;

        public FeatureItem(int type, int previewImg, String name, boolean isEnabled) {
            this.type = type;
            this.previewImg = previewImg;
            this.name = name;
            this.isEnabled = isEnabled;
        }
    }

    public class FeaturesAdapter extends RecyclerView.Adapter<FeaturesAdapter.ViewHolder> {
        public int layoutPosition = -1;
        public boolean isUpdatingAdapter;

        public FeaturesAdapter() {
        }

        @Override
        public int getItemCount() {
            return featureItems.size();
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int position) {
            isUpdatingAdapter = true;
            FeatureItem featureItem = featureItems.get(position);
            viewHolder.iconImageView.setImageResource(featureItem.previewImg);
            viewHolder.nameTextView.setText(featureItem.name);
            viewHolder.featureCheckBox.setChecked(featureItem.isEnabled);

            switch (featureItem.type) {
                case FEATURE_TYPE_STATUS_BAR -> featureStatusBar = featureItem.isEnabled;
                case FEATURE_TYPE_TOOLBAR -> featureToolbar = featureItem.isEnabled;
                case FEATURE_TYPE_DRAWER -> featureDrawer = featureItem.isEnabled;
                case FEATURE_TYPE_FAB -> featureFab = featureItem.isEnabled;
            }

            if (featureFab || featureDrawer) {
                makeTransitionAnimation(binding.parentLayout);
                binding.fabDrawerTipView.setVisibility(View.VISIBLE);
            }

            handleFeatureAnimation(featureItem);
            isUpdatingAdapter = false;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup var1, int var2) {
            View var3 = wB.a(var1.getContext(), R.layout.manage_screen_activity_add_feature_item);
            var3.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(var3);
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            public ImageView iconImageView;
            public TextView nameTextView;
            public CheckBox featureCheckBox;

            public ViewHolder(View var2) {
                super(var2);
                iconImageView = var2.findViewById(R.id.img_icon);
                nameTextView = var2.findViewById(R.id.tv_name);
                featureCheckBox = var2.findViewById(R.id.checkbox);
                featureCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (!isUpdatingAdapter) {
                        layoutPosition = getLayoutPosition();
                        FeatureItem item = featureItems.get(layoutPosition);
                        item.isEnabled = isChecked;
                        if (item.type == FEATURE_TYPE_DRAWER) {
                            if (item.isEnabled) {
                                enableToolbar();
                            }
                        } else if (item.type == FEATURE_TYPE_TOOLBAR) {
                            if (!item.isEnabled) {
                                disableDrawer();
                            }
                        }
                        notifyItemChanged(layoutPosition);
                    }
                });
            }
        }
    }
}
