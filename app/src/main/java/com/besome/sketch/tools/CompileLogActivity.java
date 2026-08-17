package com.besome.sketch.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.Helper;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.util.AddMarginOnApplyWindowInsetsListener;
import neo.sketchware.ai.ErrorFixResult;
import neo.sketchware.ai.ErrorFixTask;
import pro.sketchware.databinding.CompileLogBinding;
import pro.sketchware.utility.SketchwareUtil;

public class CompileLogActivity extends BaseAppCompatActivity {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private CompileErrorSaver compileErrorSaver;
    private String scId;
    private SharedPreferences logViewerPreferences;

    private CompileLogBinding binding;
    private String currentErrorText;
    private androidx.appcompat.app.AlertDialog aiLoadingDialog;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = CompileLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.optionsLayout,
                new AddMarginOnApplyWindowInsetsListener(WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.CONSUMED));

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            binding.topAppBar.setTitle("Last compile log");
        } else {
            binding.topAppBar.setTitle("Compile log");
        }

        String sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }
        this.scId = sc_id;

        compileErrorSaver = new CompileErrorSaver(sc_id);

        if (compileErrorSaver.logFileExists()) {
            binding.clearButton.setOnClickListener(v -> {
                if (compileErrorSaver.logFileExists()) {
                    compileErrorSaver.deleteSavedLogs();
                    getIntent().removeExtra("error");
                    SketchwareUtil.toast("Compile logs have been cleared.");
                } else {
                    SketchwareUtil.toast("No compile logs found.");
                }

                setErrorText();
            });
        }

        final String wrapTextLabel = "Wrap text";
        final String monospacedFontLabel = "Monospaced font";
        final String fontSizeLabel = "Font size";

        PopupMenu options = new PopupMenu(this, binding.formatButton);
        options.getMenu().add(wrapTextLabel).setCheckable(true).setChecked(getWrappedTextPreference());
        options.getMenu().add(monospacedFontLabel).setCheckable(true).setChecked(getMonospacedFontPreference());
        options.getMenu().add(fontSizeLabel);

        options.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getTitle().toString()) {
                case wrapTextLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                }
                case monospacedFontLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                }
                case fontSizeLabel -> changeFontSizeDialog();
                default -> {
                    return false;
                }
            }

            return true;
        });

        binding.formatButton.setOnClickListener(v -> options.show());

        binding.analyzeAiButton.setOnClickListener(v -> analyzeErrorWithAi());
        binding.copyLogButton.setOnClickListener(v -> copyLogToClipboard());

        applyLogViewerPreferences();

        setErrorText();
    }

    private void setErrorText() {
        String error = getIntent().getStringExtra("error");
        if (error == null) error = compileErrorSaver.getLogsFromFile();
        currentErrorText = error;
        if (error == null) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.optionsLayout.setVisibility(View.GONE);
            return;
        }

        binding.optionsLayout.setVisibility(View.VISIBLE);
        binding.noContentLayout.setVisibility(View.GONE);

        binding.tvCompileLog.setText(CompileLogHelper.getColoredLogs(this, error));
        binding.tvCompileLog.setTextIsSelectable(true);
    }

    private void copyLogToClipboard() {
        if (currentErrorText == null) {
            SketchwareUtil.toast("No log to copy.");
            return;
        }
        Helper.copyToClipboard(currentErrorText);
        SketchwareUtil.toast("Log copied to clipboard.");
    }

    private void analyzeErrorWithAi() {
        if (currentErrorText == null) {
            SketchwareUtil.toast("No error to analyze.");
            return;
        }

        aiLoadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Analyzing error")
                .setMessage("Asking AI to analyze the build error...")
                .setCancelable(false)
                .show();

        ErrorFixTask.analyze(this, scId, currentErrorText, new neo.sketchware.ai.ErrorFixCallback() {
            @Override
            public void onResult(ErrorFixResult result) {
                aiLoadingDialog.dismiss();
                showFixResultDialog(result);
            }

            @Override
            public void onError(String message) {
                aiLoadingDialog.dismiss();
                new MaterialAlertDialogBuilder(CompileLogActivity.this)
                        .setTitle("AI analysis failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void showFixResultDialog(ErrorFixResult result) {
        StringBuilder message = new StringBuilder();
        message.append(result.summary).append("\n\n").append(result.explanation);

        if (result.needsManualEvent && result.manualEventHint != null && !result.manualEventHint.isEmpty()) {
            message.append("\n\nThis needs a new event to be created manually:\n").append(result.manualEventHint);
        }

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("AI analysis")
                .setMessage(message.toString())
                .setNegativeButton("Close", null);

        if (result.createActivityEventName != null && !result.createActivityEventName.isEmpty()) {
            dialogBuilder.setNeutralButton("Create \"" + result.createActivityEventName + "\" Event", (dialog, which) ->
                    createActivityEvent(result.filePath, result.createActivityEventName));
        } else if (result.createViewEventTargetId != null && !result.createViewEventTargetId.isEmpty()
                && result.createViewEventName != null && !result.createViewEventName.isEmpty()) {
            dialogBuilder.setNeutralButton("Create \"" + result.createViewEventName + "\" Event", (dialog, which) ->
                    createViewEvent(result.filePath, result.createViewEventTargetId, result.createViewEventName));
        }

        if (result.patchable && !result.patches.isEmpty()) {
            dialogBuilder.setPositiveButton("Review Changes", (dialog, which) -> showReviewChangesDialog(result));
        }

        dialogBuilder.show();
    }

    private void createActivityEvent(String filePath, String eventName) {
        if (filePath == null) {
            SketchwareUtil.toastError("Couldn't tell which file this event belongs to.");
            return;
        }

        String javaName = neo.sketchware.ai.ErrorFixTask.javaNameFromFilePath(filePath);

        java.util.ArrayList<com.besome.sketch.beans.EventBean> existingEvents = a.a.a.jC.a(scId).g(javaName);
        if (existingEvents != null) {
            for (com.besome.sketch.beans.EventBean existing : existingEvents) {
                if (existing.eventType == com.besome.sketch.beans.EventBean.EVENT_TYPE_ACTIVITY
                        && eventName.equals(existing.eventName)) {
                    SketchwareUtil.toast("\"" + eventName + "\" already exists for " + javaName + ".");
                    return;
                }
            }
        }

        com.besome.sketch.beans.EventBean eventBean = new com.besome.sketch.beans.EventBean(
                com.besome.sketch.beans.EventBean.EVENT_TYPE_ACTIVITY, 0, eventName, eventName);
        a.a.a.jC.a(scId).a(javaName, eventBean);
        a.a.a.jC.a(scId).k();

        SketchwareUtil.toast("\"" + eventName + "\" created for " + javaName + ". Open Logic Editor and use \"Generate with AI\" to add the logic.");
    }

    private void createViewEvent(String filePath, String targetId, String eventName) {
        if (filePath == null) {
            SketchwareUtil.toastError("Couldn't tell which file this event belongs to.");
            return;
        }

        String javaName = neo.sketchware.ai.ErrorFixTask.javaNameFromFilePath(filePath);

        com.besome.sketch.beans.ProjectFileBean projectFile = a.a.a.jC.b(scId).a(javaName);
        if (projectFile == null) {
            SketchwareUtil.toastError("Couldn't find the layout for " + javaName + ".");
            return;
        }

        java.util.ArrayList<com.besome.sketch.beans.ViewBean> views = a.a.a.jC.a(scId).d(projectFile.getXmlName());
        com.besome.sketch.beans.ViewBean targetView = null;
        if (views != null) {
            for (com.besome.sketch.beans.ViewBean view : views) {
                if (targetId.equals(view.id)) {
                    targetView = view;
                    break;
                }
            }
        }

        if (targetView == null) {
            SketchwareUtil.toastError("Couldn't find a view with id \"" + targetId + "\" in this layout.");
            return;
        }

        java.util.ArrayList<com.besome.sketch.beans.EventBean> existingEvents = a.a.a.jC.a(scId).g(javaName);
        if (existingEvents != null) {
            for (com.besome.sketch.beans.EventBean existing : existingEvents) {
                if (existing.eventType == com.besome.sketch.beans.EventBean.EVENT_TYPE_VIEW
                        && targetId.equals(existing.targetId)
                        && eventName.equals(existing.eventName)) {
                    SketchwareUtil.toast("\"" + eventName + "\" already exists for " + targetId + ".");
                    return;
                }
            }
        }

        com.besome.sketch.beans.EventBean eventBean = new com.besome.sketch.beans.EventBean(
                com.besome.sketch.beans.EventBean.EVENT_TYPE_VIEW, targetView.type, targetId, eventName);
        a.a.a.jC.a(scId).a(javaName, eventBean);
        a.a.a.jC.a(scId).k();

        SketchwareUtil.toast("\"" + eventName + "\" created for \"" + targetId + "\". Open Logic Editor and use \"Generate with AI\" to add the logic.");
    }

    private void showReviewChangesDialog(ErrorFixResult result) {
        int dp20 = (int) (20 * getResources().getDisplayMetrics().density);
        int dp12 = (int) (12 * getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp20, dp20, dp20, dp8);
        scrollView.addView(root);

        int colorOnSurface = pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        int colorOnSurfaceVariant = pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int colorOutlineVariant = pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        int colorSurfaceVariant = pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant);
        int colorErrorContainer = pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorErrorContainer);
        int colorPrimaryContainer = pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer);

        TextView title = new TextView(this);
        title.setText(result.patches.size() == 1 ? "1 change to review" : result.patches.size() + " changes to review");
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(colorOnSurface);
        root.addView(title);

        LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(-1, dp12);
        root.addView(new android.view.View(this), spacer);

        int index = 1;
        for (ErrorFixResult.Patch patch : result.patches) {
            TextView changeLabel = new TextView(this);
            boolean isAddition = patch.originalSnippet == null || patch.originalSnippet.isEmpty();
            boolean isRemoval = patch.fixedSnippet == null || patch.fixedSnippet.isEmpty();
            String kind = isAddition ? "Addition" : isRemoval ? "Removal" : "Edit";
            changeLabel.setText("Change " + index + " · " + kind);
            changeLabel.setTextSize(13);
            changeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            changeLabel.setTextColor(colorOnSurfaceVariant);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.setMargins(0, dp12, 0, dp8 / 2);
            changeLabel.setLayoutParams(labelParams);
            root.addView(changeLabel);

            if (!isAddition) {
                root.addView(buildCodeBlock(this, "Before", patch.originalSnippet, colorErrorContainer, colorOnSurface, colorOutlineVariant, dp8));
            }
            if (!isRemoval) {
                LinearLayout.LayoutParams afterParams = new LinearLayout.LayoutParams(-1, -2);
                afterParams.setMargins(0, isAddition ? 0 : dp8 / 2, 0, 0);
                View afterBlock = buildCodeBlock(this, isAddition ? "New code" : "After", patch.fixedSnippet, colorPrimaryContainer, colorOnSurface, colorOutlineVariant, dp8);
                afterBlock.setLayoutParams(afterParams);
                root.addView(afterBlock);
            }

            index++;
        }

        new MaterialAlertDialogBuilder(this)
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply All Changes", (dialog, which) -> {
                    boolean applied = ErrorFixTask.applyFix(result);
                    if (applied) {
                        SketchwareUtil.toast("Changes applied. Rebuild the project to check.");
                    } else {
                        SketchwareUtil.toast("Could not apply the changes. The file may have changed since analysis - try Analyze with AI again.");
                    }
                })
                .show();
    }

    private static View buildCodeBlock(android.content.Context context, String label, String code, int accentColor, int textColor, int borderColor, int dp8) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        labelView.setTextColor(textColor);
        labelView.setPadding(dp8, dp8 / 2, dp8, dp8 / 2);
        labelView.setBackgroundColor(accentColor);
        container.addView(labelView);

        TextView codeView = new TextView(context);
        codeView.setText(code == null || code.isEmpty() ? "(empty)" : code);
        codeView.setTextSize(12);
        codeView.setTypeface(android.graphics.Typeface.MONOSPACE);
        codeView.setTextColor(textColor);
        codeView.setPadding(dp8, dp8, dp8, dp8);
        codeView.setTextIsSelectable(true);
        container.addView(codeView);

        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setStroke(1, borderColor);
        border.setCornerRadius(dp8);
        container.setBackground(border);

        return container;
    }

    private void applyLogViewerPreferences() {
        toggleWrapText(getWrappedTextPreference());
        toggleMonospacedText(getMonospacedFontPreference());
        binding.tvCompileLog.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, false);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 11);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errVScroll.addView(binding.tvCompileLog);
        } else {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errHScroll.removeAllViews();
            binding.errHScroll.addView(binding.tvCompileLog);
            binding.errVScroll.addView(binding.errHScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            binding.tvCompileLog.setTypeface(Typeface.MONOSPACE);
        } else {
            binding.tvCompileLog.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10); //Must not be less than setValue(), which is currently 11 in compile_log.xml
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select font size")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                    binding.tvCompileLog.setTextSize((float) picker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
