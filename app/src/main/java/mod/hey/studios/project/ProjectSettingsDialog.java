package mod.hey.studios.project;

import static com.besome.sketch.Config.VAR_DEFAULT_MIN_SDK_VERSION;
import static com.besome.sketch.Config.VAR_DEFAULT_TARGET_SDK_VERSION;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.checkbox.MaterialCheckBox;

import pro.sketchware.databinding.DialogProjectSettingsBinding;

public class ProjectSettingsDialog {

    private final Activity activity;
    private final ProjectSettings settings;
    public static final String SETTING_ENABLE_VERSION_HISTORY = "enable_version_history";
    public static final String SETTING_COMPILE_SDK_VERSION = "compile_sdk_version"; // New Constant

    public ProjectSettingsDialog(Activity activity, String sc_id) {
        this.activity = activity;
        settings = new ProjectSettings(sc_id);
    }

    public void show() {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        DialogProjectSettingsBinding binding = DialogProjectSettingsBinding.inflate(activity.getLayoutInflater());

        dialog.setOnShowListener(bsd -> {
            var b = (BottomSheetDialog) bsd;
            var parent = b.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (parent != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        // Load Values
        binding.etCompileSdkVersion.setText(settings.getValue(SETTING_COMPILE_SDK_VERSION, "34")); // Defaulting to 34
        binding.etMinimumSdkVersion.setText(settings.getValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, String.valueOf(VAR_DEFAULT_MIN_SDK_VERSION)));
        binding.etTargetSdkVersion.setText(settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION)));
        binding.etApplicationClassName.setText(settings.getValue(ProjectSettings.SETTING_APPLICATION_CLASS, ".SketchApplication"));

        MaterialCheckBox cbVersionHistory = binding.getRoot().findViewById(pro.sketchware.R.id.cb_enable_version_history);
        LinearLayout layoutVersionHistory = binding.getRoot().findViewById(pro.sketchware.R.id.enable_version_history);
        
        cbVersionHistory.setChecked(settings.getValue(SETTING_ENABLE_VERSION_HISTORY, "false").equals("true"));
        binding.cbEnableViewbinding.setChecked(settings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false").equals("true"));
        binding.cbRemoveOldMethods.setChecked(settings.getValue(ProjectSettings.SETTING_DISABLE_OLD_METHODS, "true").equals("true"));
        binding.cbUseNewMaterialComponentsAppTheme.setChecked(settings.getValue(ProjectSettings.SETTING_ENABLE_BRIDGELESS_THEMES, "false").equals("true"));

        // Click listeners for entire row
        layoutVersionHistory.setOnClickListener(v -> cbVersionHistory.performClick());
        binding.enableViewbinding.setOnClickListener(v -> binding.cbEnableViewbinding.performClick());
        binding.removeOldMethods.setOnClickListener(v -> binding.cbRemoveOldMethods.performClick());
        binding.useNewMaterialComponentsAppTheme.setOnClickListener(v -> binding.cbUseNewMaterialComponentsAppTheme.performClick());

        // Tags for saving
        binding.etCompileSdkVersion.setTag(SETTING_COMPILE_SDK_VERSION);
        binding.etMinimumSdkVersion.setTag(ProjectSettings.SETTING_MINIMUM_SDK_VERSION);
        binding.etTargetSdkVersion.setTag(ProjectSettings.SETTING_TARGET_SDK_VERSION);
        binding.etApplicationClassName.setTag(ProjectSettings.SETTING_APPLICATION_CLASS);
        cbVersionHistory.setTag(SETTING_ENABLE_VERSION_HISTORY);
        binding.cbEnableViewbinding.setTag(ProjectSettings.SETTING_ENABLE_VIEWBINDING);
        binding.cbRemoveOldMethods.setTag(ProjectSettings.SETTING_DISABLE_OLD_METHODS);
        binding.cbUseNewMaterialComponentsAppTheme.setTag(ProjectSettings.SETTING_ENABLE_BRIDGELESS_THEMES);

        dialog.setContentView(binding.getRoot());

        // Add to preferences array
        View[] preferences = {
                binding.etCompileSdkVersion,
                binding.etMinimumSdkVersion,
                binding.etTargetSdkVersion,
                binding.etApplicationClassName,
                cbVersionHistory,
                binding.cbEnableViewbinding,
                binding.cbRemoveOldMethods,
                binding.cbUseNewMaterialComponentsAppTheme
        };

        binding.save.setOnClickListener(v -> {
            settings.setValues(preferences);
            dialog.dismiss();
        });
        binding.cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
