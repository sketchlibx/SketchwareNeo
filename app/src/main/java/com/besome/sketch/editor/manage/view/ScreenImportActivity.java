package com.besome.sketch.editor.manage.view;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import a.a.a.hC;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.R;

/**
 * Translucent entry activity which shows the project/screen picker as a Material bottom sheet.
 * The underlying project list remains visible and dimmed while the user selects an item.
 */
public class ScreenImportActivity extends BaseAppCompatActivity {
    public static final String EXTRA_TARGET_PROJECT_ID = "target_sc_id";
    public static final String EXTRA_SOURCE_PROJECT_ID = "source_sc_id";
    public static final String EXTRA_CLONE_MODE = "clone_mode";

    private static final Pattern VALID_BASE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final int PROJECT_PICKER = 0;
    private static final int SCREEN_PICKER = 1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService filterExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger filterGeneration = new AtomicInteger();

    private String targetProjectId;
    private String sourceProjectId;
    private boolean cloneMode;
    private BottomSheetDialog activePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        makeWindowTransparent();
        if (!super.isStoragePermissionGranted()) {
            finish();
            return;
        }
        setContentView(new FrameLayout(this));

        targetProjectId = getIntent().getStringExtra(EXTRA_TARGET_PROJECT_ID);
        sourceProjectId = getIntent().getStringExtra(EXTRA_SOURCE_PROJECT_ID);
        cloneMode = getIntent().getBooleanExtra(EXTRA_CLONE_MODE, false);
        if (isEmpty(targetProjectId)) {
            finish();
            return;
        }
        if (cloneMode) {
            if (isEmpty(sourceProjectId)) sourceProjectId = targetProjectId;
            showScreenPicker(sourceProjectId);
        } else {
            showProjectPicker();
        }
    }

    private void makeWindowTransparent() {
        Window window = getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void showProjectPicker() {
        List<HashMap<String, Object>> allProjects;
        try {
            allProjects = lC.a();
        } catch (Exception exception) {
            showMessageAndFinish("The project list could not be loaded.");
            return;
        }
        if (allProjects == null) {
            showMessageAndFinish("The project list could not be loaded.");
            return;
        }

        ArrayList<PickerItem> items = new ArrayList<>();
        for (HashMap<String, Object> project : allProjects) {
            if (project == null) continue;
            String projectId = safeValue(project, "sc_id");
            if (isEmpty(projectId) || projectId.equals(targetProjectId)) continue;

            String appName = safeValue(project, "my_app_name");
            String packageName = safeValue(project, "my_sc_pkg_name");
            String workspaceName = safeValue(project, "my_ws_name");
            if (isEmpty(appName)) appName = isEmpty(workspaceName) ? "Untitled project" : workspaceName;
            if (isEmpty(packageName)) packageName = "Project ID · " + projectId;
            if (isEmpty(workspaceName)) workspaceName = "Project ID · " + projectId;
            String versionName = safeValue(project, "sc_ver_name");
            String versionCode = safeValue(project, "sc_ver_code");
            if (isEmpty(versionName)) versionName = "1.0";
            if (isEmpty(versionCode)) versionCode = "1";
            String badge = "v" + versionName + " (" + versionCode + ")";
            String searchText = appName + " " + packageName + " " + workspaceName + " " + projectId + " " + badge;
            items.add(new PickerItem("project:" + projectId, project, PickerItem.KIND_PROJECT,
                    appName, packageName, workspaceName, badge, searchText, R.drawable.default_icon, projectId));
        }
        if (items.isEmpty()) {
            showMessageAndFinish("No other projects are available to import from.");
            return;
        }
        showPickerSheet(PROJECT_PICKER, "Import Project", "Choose a project to import from",
                R.drawable.ic_mtrl_folder_code, "Import", items, selected -> {
                    if (selected == null || !(selected.value instanceof HashMap)) return;
                    @SuppressWarnings("unchecked")
                    HashMap<String, Object> project = (HashMap<String, Object>) selected.value;
                    sourceProjectId = safeValue(project, "sc_id");
                    showScreenPicker(sourceProjectId);
                });
    }

    private void showScreenPicker(String projectId) {
        if (isEmpty(projectId)) {
            showMessageAndFinish("The source project could not be opened.");
            return;
        }
        ArrayList<ProjectFileBean> activities;
        ArrayList<ProjectFileBean> otherFiles;
        try {
            hC sourceFiles = jC.b(projectId);
            if (sourceFiles == null || sourceFiles.b() == null || sourceFiles.c() == null) {
                showMessageAndFinish("The source project's screen list could not be loaded.");
                return;
            }
            activities = new ArrayList<>(sourceFiles.b());
            otherFiles = new ArrayList<>(sourceFiles.c());
        } catch (Exception exception) {
            showMessageAndFinish("The source project's screen list could not be loaded.");
            return;
        }

        ArrayList<PickerItem> items = new ArrayList<>();
        for (ProjectFileBean bean : activities) {
            if (bean == null || !isImportableType(bean.fileType)) continue;
            String xmlName = bean.getXmlName();
            String typeLabel = getScreenTypeLabel(bean);
            String secondary = bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    ? bean.getJavaName() : typeLabel;
            String detail = bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    ? typeLabel : bean.fileName;
            items.add(new PickerItem("screen:" + bean.fileType + ":" + bean.fileName, bean,
                    PickerItem.KIND_SCREEN, xmlName, secondary, detail, "",
                    xmlName + " " + secondary + " " + detail + " " + bean.fileName,
                    R.drawable.ic_mtrl_screen, projectId));
        }
        for (ProjectFileBean bean : otherFiles) {
            if (bean == null || bean.fileType != ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW) continue;
            String xmlName = bean.getXmlName();
            items.add(new PickerItem("custom:" + bean.fileName, bean, PickerItem.KIND_SCREEN,
                    xmlName, "Custom View", bean.fileName, "",
                    xmlName + " custom view " + bean.fileName,
                    R.drawable.ic_mtrl_screen, projectId));
        }
        if (items.isEmpty()) {
            showMessageAndFinish("No importable screens or custom views were found in this project.");
            return;
        }

        showPickerSheet(SCREEN_PICKER, "Choose Screen", "Select a screen to open or import",
                R.drawable.ic_mtrl_screen, cloneMode ? "Clone" : "Open", items, selected -> {
                    if (selected == null || !(selected.value instanceof ProjectFileBean)) return;
                    showNameDialog(projectId, (ProjectFileBean) selected.value);
                });
    }

    private boolean isImportableType(int type) {
        return type == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                || type == ProjectFileBean.PROJECT_FILE_TYPE_FRAGMENT
                || type == ProjectFileBean.PROJECT_FILE_TYPE_SHEET
                || type == ProjectFileBean.PROJECT_FILE_TYPE_DIALOG_FRAGMENT;
    }

    private void showPickerSheet(int pickerType, String title, String subtitle, int headerIcon,
                                 String continueText, ArrayList<PickerItem> items,
                                 PickerSelectionListener onContinue) {
        filterGeneration.incrementAndGet();
        View sheet = getLayoutInflater().inflate(R.layout.screen_import_picker_sheet, null, false);
        ImageView headerImage = sheet.findViewById(R.id.picker_header_icon);
        TextView titleView = sheet.findViewById(R.id.picker_title);
        TextView subtitleView = sheet.findViewById(R.id.picker_subtitle);
        TextInputLayout searchLayout = sheet.findViewById(R.id.picker_search_layout);
        TextInputEditText searchInput = sheet.findViewById(R.id.picker_search_input);
        RecyclerView recyclerView = sheet.findViewById(R.id.picker_recycler);
        TextView emptyView = sheet.findViewById(R.id.picker_empty);
        MaterialButton cancelButton = sheet.findViewById(R.id.picker_cancel);
        MaterialButton continueButton = sheet.findViewById(R.id.picker_continue);

        titleView.setText(title);
        subtitleView.setText(subtitle);
        headerImage.setImageResource(headerIcon);
        searchLayout.setHint(pickerType == PROJECT_PICKER
                ? "Search projects by name, package or ID..."
                : "Search by screen name or type...");
        continueButton.setText(continueText);
        cancelButton.setOnClickListener(view -> {
            filterGeneration.incrementAndGet();
            if (activePicker != null) activePicker.dismiss();
            finish();
        });

        PickerAdapter adapter = new PickerAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        // Recycler list filtering uses DiffUtil and background filtering; removing default
        // change animations avoids row flashes while the search text is changing rapidly.
        recyclerView.setItemAnimator(null);
        recyclerView.setPadding(dp(16), dp(8), dp(16), dp(8));
        recyclerView.setClipToPadding(false);
        ViewGroup.LayoutParams listParams = recyclerView.getLayoutParams();
        if (listParams != null) {
            int heightDp = Math.max(190, Math.min(580, (int) (getResources().getDisplayMetrics().heightPixels
                    / getResources().getDisplayMetrics().density) - 320));
            listParams.height = dp(heightDp);
            recyclerView.setLayoutParams(listParams);
        }

        String initialSelection = items.get(0).key;
        adapter.setSelectedKey(initialSelection);
        adapter.submitList(new ArrayList<>(items));
        continueButton.setEnabled(true);
        updateEmptyState(recyclerView, emptyView, items.size());

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(ignored -> {
            filterGeneration.incrementAndGet();
            if (!isFinishing()) finish();
        });
        dialog.setOnDismissListener(ignored -> {
            if (activePicker == dialog) activePicker = null;
        });
        activePicker = dialog;

        PickerItem[] selectedHolder = new PickerItem[]{items.get(0)};
        adapter.setOnSelectionChanged(item -> {
            selectedHolder[0] = item;
            continueButton.setEnabled(item != null);
        });
        continueButton.setOnClickListener(view -> {
            PickerItem selected = selectedHolder[0];
            if (selected == null) return;
            filterGeneration.incrementAndGet();
            dialog.dismiss();
            onContinue.onSelected(selected);
        });

        final int generationAtOpen = filterGeneration.incrementAndGet();
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                final int generation = filterGeneration.incrementAndGet();
                try {
                    filterExecutor.execute(() -> {
                        ArrayList<PickerItem> filtered = new ArrayList<>();
                        for (PickerItem item : items) {
                            if (query.isEmpty() || item.searchText.toLowerCase(Locale.ROOT).contains(query)) {
                                filtered.add(item);
                            }
                        }
                        if (generation != filterGeneration.get() || generationAtOpen > generation) return;
                        mainHandler.post(() -> {
                            if (!dialog.isShowing() || isFinishing() || generation != filterGeneration.get()) return;
                            String previousSelectedKey = selectedHolder[0] == null ? null : selectedHolder[0].key;
                            adapter.submitList(filtered, () -> {
                                if (generation != filterGeneration.get() || !dialog.isShowing()) return;
                                int countNow = adapter.getItemCount();
                                updateEmptyState(recyclerView, emptyView, countNow);
                                PickerItem keep = findByKey(filtered, previousSelectedKey);
                                if (keep == null && !filtered.isEmpty()) keep = filtered.get(0);
                                selectedHolder[0] = keep;
                                adapter.setSelectedKey(keep == null ? null : keep.key);
                                continueButton.setEnabled(keep != null);
                            });
                        });
                    });
                } catch (RuntimeException ignored) {
                    // The Activity may already be finishing and its filter executor shut down.
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                window.setDimAmount(0.48f);
            }
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private PickerItem findByKey(List<PickerItem> items, String key) {
        if (key == null) return null;
        for (PickerItem item : items) if (key.equals(item.key)) return item;
        return null;
    }

    private void updateEmptyState(RecyclerView recyclerView, TextView emptyView, int count) {
        recyclerView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void showNameDialog(String sourceId, ProjectFileBean sourceScreen) {
        if (sourceScreen == null || isEmpty(sourceScreen.fileName)) {
            showMessageAndFinish("The selected screen is invalid.");
            return;
        }
        String suffix = getScreenSuffix(sourceScreen);
        String sourceBaseName = suffix.isEmpty() ? sourceScreen.fileName
                : sourceScreen.fileName.substring(0, sourceScreen.fileName.length() - suffix.length());
        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(sourceBaseName + "Copy");
        input.setSelection(input.length());
        TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint("New screen name");
        textInputLayout.setHelperText(suffix.isEmpty()
                ? "Use letters, numbers and underscores."
                : "The " + suffix + " suffix will be kept.");
        textInputLayout.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int padding = dp(24);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(textInputLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(cloneMode ? "Clone screen" : "Import screen")
                .setView(container)
                .setPositiveButton(cloneMode ? "Clone" : "Import", null)
                .setNegativeButton("Cancel", (d, which) -> finish())
                .create();
        dialog.setOnCancelListener(d -> finish());
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String baseName = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!VALID_BASE_NAME.matcher(baseName).matches()) {
                        textInputLayout.setError("Enter a valid name without spaces or punctuation.");
                        return;
                    }
                    textInputLayout.setError(null);
                    dialog.dismiss();
                    performTransfer(sourceId, sourceScreen, baseName + suffix);
                }));
        dialog.show();
    }

    private void performTransfer(String sourceId, ProjectFileBean sourceScreen, String targetName) {
        ProgressBar progress = new ProgressBar(this);
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setGravity(Gravity.CENTER);
        int padding = dp(24);
        progressContainer.setPadding(padding, padding, padding, padding);
        progressContainer.addView(progress);
        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(cloneMode ? "Cloning screen" : "Importing screen")
                .setView(progressContainer)
                .setCancelable(false)
                .create();
        progressDialog.show();
        new Thread(() -> {
            ScreenTransferManager.Result result = ScreenTransferManager.transfer(
                    sourceId, targetProjectId, sourceScreen.fileName, sourceScreen.fileType, targetName);
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (isFinishing() || isDestroyed()) return;
                if (result.success) setResult(RESULT_OK, new Intent().putExtra("screen_file_name", targetName));
                new MaterialAlertDialogBuilder(this)
                        .setTitle(result.success ? "Complete" : "Could not copy screen")
                        .setMessage(result.message)
                        .setPositiveButton("OK", (d, which) -> {
                            if (result.success || !cloneMode || result.message.contains("rollback could not be confirmed")) {
                                finish();
                            } else {
                                showScreenPicker(sourceId);
                            }
                        })
                        .setOnCancelListener(d -> finish())
                        .show();
            });
        }, "ScreenTransfer").start();
    }

    private String getScreenTypeLabel(ProjectFileBean bean) {
        String name = bean.fileName == null ? "" : bean.fileName;
        if (bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_DIALOG_FRAGMENT || name.endsWith("_dialog_fragment")) {
            return "DialogFragment";
        }
        if (bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_SHEET || name.endsWith("_bottomdialog_fragment")) {
            return "Bottom Sheet DialogFragment";
        }
        if (bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_FRAGMENT || name.endsWith("_fragment")) {
            return "Fragment";
        }
        if (bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW) return "Custom View";
        return "Activity";
    }

    private String getScreenSuffix(ProjectFileBean bean) {
        if (bean == null || (bean.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                && bean.fileType != ProjectFileBean.PROJECT_FILE_TYPE_FRAGMENT
                && bean.fileType != ProjectFileBean.PROJECT_FILE_TYPE_SHEET
                && bean.fileType != ProjectFileBean.PROJECT_FILE_TYPE_DIALOG_FRAGMENT)) return "";
        String name = bean.fileName == null ? "" : bean.fileName;
        if (name.endsWith("_bottomdialog_fragment")) return "_bottomdialog_fragment";
        if (name.endsWith("_dialog_fragment")) return "_dialog_fragment";
        if (name.endsWith("_fragment")) return "_fragment";
        return "";
    }

    private String safeValue(HashMap<String, Object> map, String key) {
        try {
            return yB.c(map, key);
        } catch (Exception ignored) {
            Object value = map.get(key);
            return value == null ? "" : String.valueOf(value);
        }
    }

    private void showMessageAndFinish(String message) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        filterGeneration.incrementAndGet();
        if (activePicker != null) {
            activePicker.setOnCancelListener(null);
            activePicker.dismiss();
            activePicker = null;
        }
        filterExecutor.shutdownNow();
        super.onDestroy();
    }

    private interface PickerSelectionListener {
        void onSelected(PickerItem item);
    }

    private interface OnPickerSelectionChanged {
        void onSelected(PickerItem item);
    }

    private static final class PickerItem {
        static final int KIND_PROJECT = 0;
        static final int KIND_SCREEN = 1;

        final String key;
        final Object value;
        final int kind;
        final String title;
        final String subtitle;
        final String detail;
        final String badge;
        final String searchText;
        final int iconRes;
        final String projectId;

        PickerItem(String key, Object value, int kind, String title, String subtitle, String detail,
                   String badge, String searchText, int iconRes, String projectId) {
            this.key = key;
            this.value = value;
            this.kind = kind;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.detail = detail == null ? "" : detail;
            this.badge = badge == null ? "" : badge;
            this.searchText = searchText == null ? "" : searchText;
            this.iconRes = iconRes;
            this.projectId = projectId == null ? "" : projectId;
        }
    }

    private final class PickerAdapter extends ListAdapter<PickerItem, PickerAdapter.PickerViewHolder> {
        private String selectedKey;
        private OnPickerSelectionChanged selectionChanged;

        PickerAdapter() {
            super(new DiffUtil.ItemCallback<PickerItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull PickerItem oldItem, @NonNull PickerItem newItem) {
                    return oldItem.key.equals(newItem.key);
                }

                @Override
                public boolean areContentsTheSame(@NonNull PickerItem oldItem, @NonNull PickerItem newItem) {
                    return oldItem.title.equals(newItem.title)
                            && oldItem.subtitle.equals(newItem.subtitle)
                            && oldItem.detail.equals(newItem.detail)
                            && oldItem.badge.equals(newItem.badge)
                            && oldItem.key.equals(newItem.key);
                }
            });
        }

        void setSelectedKey(String key) {
            if (key != null && key.equals(selectedKey)) return;
            String old = selectedKey;
            selectedKey = key;
            int oldPosition = findPosition(old);
            int newPosition = findPosition(key);
            if (oldPosition != RecyclerView.NO_POSITION) notifyItemChanged(oldPosition);
            if (newPosition != RecyclerView.NO_POSITION && newPosition != oldPosition) notifyItemChanged(newPosition);
        }

        void setOnSelectionChanged(OnPickerSelectionChanged listener) {
            selectionChanged = listener;
        }

        private int findPosition(String key) {
            if (key == null) return RecyclerView.NO_POSITION;
            List<PickerItem> current = getCurrentList();
            for (int index = 0; index < current.size(); index++) {
                if (key.equals(current.get(index).key)) return index;
            }
            return RecyclerView.NO_POSITION;
        }

        @NonNull
        @Override
        public PickerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_screen_import_picker, parent, false);
            return new PickerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PickerViewHolder holder, int position) {
            PickerItem item = getItem(position);
            holder.title.setText(item.title);
            holder.subtitle.setText(item.subtitle);
            holder.detail.setText(item.detail);
            holder.detail.setVisibility(isEmpty(item.detail) ? View.GONE : View.VISIBLE);
            holder.badge.setText(item.badge);
            holder.badgeContainer.setVisibility(isEmpty(item.badge) ? View.GONE : View.VISIBLE);
            holder.icon.setImageResource(item.iconRes);
            if (item.kind == PickerItem.KIND_PROJECT) loadProjectIcon(item, holder.icon);
            boolean selected = item.key.equals(selectedKey);
            holder.radio.setChecked(selected);
            int stroke = MaterialColors.getColor(holder.card, R.attr.colorOutlineVariant, Color.GRAY);
            int primary = MaterialColors.getColor(holder.card, R.attr.colorPrimary, Color.BLUE);
            int normalBg = MaterialColors.getColor(holder.card, R.attr.colorSurfaceContainerLow, Color.DKGRAY);
            int selectedBg = MaterialColors.getColor(holder.card, R.attr.colorSecondaryContainer, normalBg);
            holder.card.setStrokeColor(selected ? primary : stroke);
            holder.card.setStrokeWidth(dp(selected ? 2 : 1));
            holder.card.setCardBackgroundColor(selected ? selectedBg : normalBg);
            holder.itemView.setOnClickListener(view -> {
                int currentPosition = holder.getBindingAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) return;
                PickerItem chosen = getItem(currentPosition);
                if (chosen.key.equals(selectedKey)) return;
                setSelectedKey(chosen.key);
                if (selectionChanged != null) selectionChanged.onSelected(chosen);
            });
        }

        private void loadProjectIcon(PickerItem item, ImageView image) {
            if (item.value == null || !(item.value instanceof HashMap)) return;
            @SuppressWarnings("unchecked")
            HashMap<String, Object> project = (HashMap<String, Object>) item.value;
            if (!yB.a(project, "custom_icon")) return;
            try {
                File iconFile = new File(wq.e() + File.separator + item.projectId, "icon.png");
                if (!iconFile.isFile()) return;
                Uri uri = FileProvider.getUriForFile(ScreenImportActivity.this,
                        getPackageName() + ".provider", iconFile);
                image.setImageURI(uri);
            } catch (Exception ignored) {
                image.setImageResource(R.drawable.default_icon);
            }
        }

        final class PickerViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ImageView icon;
            final TextView title;
            final TextView subtitle;
            final TextView detail;
            final MaterialCardView badgeContainer;
            final TextView badge;
            final RadioButton radio;

            PickerViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.picker_item_card);
                icon = itemView.findViewById(R.id.picker_item_icon);
                title = itemView.findViewById(R.id.picker_item_title);
                subtitle = itemView.findViewById(R.id.picker_item_subtitle);
                detail = itemView.findViewById(R.id.picker_item_detail);
                badgeContainer = itemView.findViewById(R.id.picker_item_badge_container);
                badge = itemView.findViewById(R.id.picker_item_badge);
                radio = itemView.findViewById(R.id.picker_item_radio);
                radio.setClickable(false);
                radio.setFocusable(false);
            }
        }
    }
}
