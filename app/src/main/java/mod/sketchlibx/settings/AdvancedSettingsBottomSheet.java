package mod.sketchlibx.settings;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;

import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.ProjectSettings;
import neo.sketchware.R;

public class AdvancedSettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "AdvancedSettingsBottomSheet";

    private final String sc_id;
    private final DesignActivity activity;
    private ProjectSettings projectSettings;

    private MaterialSwitch cbForceAndroidX;
    private MaterialSwitch cbCustomJava;
    private MaterialSwitch cbCustomManifest;
    private MaterialSwitch cbMultiDex;

    public AdvancedSettingsBottomSheet(String sc_id, String currentJavaFileName, DesignActivity activity) {
        this.sc_id = sc_id;
        this.activity = activity;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), getTheme());
        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_advanced_settings, container, false);
        projectSettings = new ProjectSettings(sc_id);

        cbForceAndroidX = root.findViewById(R.id.switch_force_androidx);
        cbCustomJava = root.findViewById(R.id.switch_custom_java);
        cbCustomManifest = root.findViewById(R.id.switch_custom_manifest);
        cbMultiDex = root.findViewById(R.id.switch_multidex);

        LinearLayout rowForceAndroidX = root.findViewById(R.id.row_force_androidx);
        LinearLayout rowCustomJava = root.findViewById(R.id.row_custom_java);
        LinearLayout rowCustomManifest = root.findViewById(R.id.row_custom_manifest);
        LinearLayout rowMultiDex = root.findViewById(R.id.row_multidex);

        cbForceAndroidX.setChecked(projectSettings.getValue(ProjectSettings.SETTING_FORCE_ANDROIDX, "false").equals("true"));
        cbCustomJava.setChecked(projectSettings.getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false").equals("true"));
        cbCustomManifest.setChecked(projectSettings.getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_MANIFEST, "false").equals("true"));
        cbMultiDex.setChecked(projectSettings.getValue("multidex", "false").equals("true"));

        rowForceAndroidX.setOnClickListener(v -> cbForceAndroidX.performClick());
        rowCustomJava.setOnClickListener(v -> cbCustomJava.performClick());
        rowCustomManifest.setOnClickListener(v -> cbCustomManifest.performClick());
        rowMultiDex.setOnClickListener(v -> cbMultiDex.performClick());

        root.findViewById(R.id.btn_project_analyzer).setOnClickListener(v -> {
            saveSettings();
            dismiss();
            Intent intent = new Intent(activity, ResourceTrackerActivity.class);
            intent.putExtra("sc_id", sc_id);
            startActivity(intent);
        });

        root.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.btn_save).setOnClickListener(v -> {
            saveSettings();
            activity.invalidateOptionsMenu(); 
            dismiss();
        });

        return root;
    }

    private void saveSettings() {
        projectSettings.setValue(ProjectSettings.SETTING_FORCE_ANDROIDX, cbForceAndroidX.isChecked() ? "true" : "false");
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, cbCustomJava.isChecked() ? "true" : "false");
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_MANIFEST, cbCustomManifest.isChecked() ? "true" : "false");
        projectSettings.setValue("multidex", cbMultiDex.isChecked() ? "true" : "false");
    }
}
