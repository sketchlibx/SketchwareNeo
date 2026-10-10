package com.besome.sketch.editor.manage.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import a.a.a.hC;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.yB;

public class ScreenImportActivity extends BaseAppCompatActivity {
    public static final String EXTRA_TARGET_PROJECT_ID = "target_sc_id";
    public static final String EXTRA_SOURCE_PROJECT_ID = "source_sc_id";
    public static final String EXTRA_CLONE_MODE = "clone_mode";
    private static final Pattern VALID_BASE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private String targetProjectId;
    private String sourceProjectId;
    private boolean cloneMode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!super.isStoragePermissionGranted()) {
            finish();
            return;
        }
        setContentView(new FrameLayout(this), new ViewGroup.LayoutParams(1, 1));
        targetProjectId = getIntent().getStringExtra(EXTRA_TARGET_PROJECT_ID);
        sourceProjectId = getIntent().getStringExtra(EXTRA_SOURCE_PROJECT_ID);
        cloneMode = getIntent().getBooleanExtra(EXTRA_CLONE_MODE, false);
        if (isEmpty(targetProjectId)) {
            finish();
            return;
        }
        if (cloneMode) {
            if (isEmpty(sourceProjectId)) {
                sourceProjectId = targetProjectId;
            }
            showScreenPicker(sourceProjectId);
        } else {
            showProjectPicker();
        }
    }

    private void showProjectPicker() {
        List<HashMap<String, Object>> allProjects;
        try {
            allProjects = lC.a();
        } catch (Exception e) {
            showMessageAndFinish("The project list could not be loaded.");
            return;
        }
        if (allProjects == null) {
            showMessageAndFinish("The project list could not be loaded.");
            return;
        }
        ArrayList<HashMap<String, Object>> availableProjects = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (HashMap<String, Object> project : allProjects) {
            if (project == null) {
                continue;
            }
            String projectId = yB.c(project, "sc_id");
            if (!projectId.isEmpty() && !projectId.equals(targetProjectId)) {
                availableProjects.add(project);
                String name = yB.c(project, "my_ws_name");
                labels.add(name.isEmpty() ? "Project " + projectId : name);
            }
        }
        if (availableProjects.isEmpty()) {
            showMessageAndFinish("No other projects are available to import from.");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose source project")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    sourceProjectId = yB.c(availableProjects.get(which), "sc_id");
                    showScreenPicker(sourceProjectId);
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
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
        } catch (Exception e) {
            showMessageAndFinish("The source project's screen list could not be loaded.");
            return;
        }
        ArrayList<ProjectFileBean> choices = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (ProjectFileBean bean : activities) {
            if (bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                choices.add(bean);
                labels.add(bean.fileName + "  ·  " + getScreenTypeLabel(bean));
            }
        }
        for (ProjectFileBean bean : otherFiles) {
            if (bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW) {
                choices.add(bean);
                labels.add(bean.fileName + "  ·  Custom View");
            }
        }
        if (choices.isEmpty()) {
            showMessageAndFinish("No activities or custom views were found in this project.");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(cloneMode ? "Choose screen to clone" : "Choose screen to import")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> showNameDialog(projectId, choices.get(which)))
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showNameDialog(String sourceId, ProjectFileBean sourceScreen) {
        if (sourceScreen == null || isEmpty(sourceScreen.fileName)) {
            showMessageAndFinish("The selected screen is invalid.");
            return;
        }
        String suffix = getScreenSuffix(sourceScreen);
        String sourceBaseName = suffix.isEmpty() ? sourceScreen.fileName : sourceScreen.fileName.substring(0, sourceScreen.fileName.length() - suffix.length());
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(sourceBaseName + "Copy");
        input.setSelection(input.length());
        TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint("New screen name");
        textInputLayout.setHelperText(suffix.isEmpty() ? "Use letters, numbers and underscores." : "The " + suffix + " suffix will be kept.");
        textInputLayout.addView(input, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(textInputLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(cloneMode ? "Clone screen" : "Import screen")
                .setView(container)
                .setPositiveButton(cloneMode ? "Clone" : "Import", null)
                .setNegativeButton("Cancel", (d, which) -> finish())
                .create();
        dialog.setOnCancelListener(d -> finish());
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String baseName = input.getText() == null ? "" : input.getText().toString().trim();
            if (!VALID_BASE_NAME.matcher(baseName).matches()) {
                textInputLayout.setError("Enter a valid name without spaces or punctuation.");
                return;
            }
            textInputLayout.setError(null);
            String targetName = baseName + suffix;
            dialog.dismiss();
            performTransfer(sourceId, sourceScreen, targetName);
        }));
        dialog.show();
    }

    private void performTransfer(String sourceId, ProjectFileBean sourceScreen, String targetName) {
        ProgressBar progress = new ProgressBar(this);
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setGravity(android.view.Gravity.CENTER);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        progressContainer.setPadding(padding, padding, padding, padding);
        progressContainer.addView(progress);
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
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
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result.success) {
                    setResult(RESULT_OK, new Intent().putExtra("screen_file_name", targetName));
                }
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
        String name = bean.fileName;
        if (name.endsWith("_bottomdialog_fragment")) return "Bottom Sheet DialogFragment";
        if (name.endsWith("_dialog_fragment")) return "DialogFragment";
        if (name.endsWith("_fragment")) return "Fragment";
        return "Activity";
    }

    private String getScreenSuffix(ProjectFileBean bean) {
        if (bean.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
            return "";
        }
        String name = bean.fileName;
        if (name.endsWith("_bottomdialog_fragment")) return "_bottomdialog_fragment";
        if (name.endsWith("_dialog_fragment")) return "_dialog_fragment";
        if (name.endsWith("_fragment")) return "_fragment";
        return "";
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
}
