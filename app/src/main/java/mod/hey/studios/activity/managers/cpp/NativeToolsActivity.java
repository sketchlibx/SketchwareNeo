package mod.hey.studios.activity.managers.cpp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;

import pro.sketchware.R;
import pro.sketchware.databinding.ManageLibraryNativeToolsBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class NativeToolsActivity extends BaseAppCompatActivity {

    private static final String PREFS_NAME = "native_tools_prefs";
    private static final String KEY_PREFIX = "enabled_";

    private ManageLibraryNativeToolsBinding binding;
    private String sc_id;

    public static boolean isEnabled(android.content.Context context, String sc_id) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_PREFIX + sc_id, false);
    }

    private void setEnabled(boolean enabled) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_PREFIX + sc_id, enabled)
                .apply();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        sc_id = getIntent().getStringExtra("sc_id");

        binding = ManageLibraryNativeToolsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appBar.setPadding(0, systemBars.top, 0, 0);
            binding.contentLayout.setPadding(
                    binding.contentLayout.getPaddingLeft(),
                    binding.contentLayout.getPaddingTop(),
                    binding.contentLayout.getPaddingRight(),
                    systemBars.bottom + SketchwareUtil.dpToPx(16)
            );
            return WindowInsetsCompat.CONSUMED;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finishWithResult());
        setSupportActionBar(binding.toolbar);

        MaterialSwitch switchWidget = (MaterialSwitch) binding.switchEnable.getRoot();
        switchWidget.setChecked(isEnabled(this, sc_id));
        binding.layoutSwitch.setOnClickListener(v -> {
            boolean newState = !switchWidget.isChecked();
            switchWidget.setChecked(newState);
            setEnabled(newState);
        });

        binding.btnManageFiles.setOnClickListener(v -> openManageCpp(false));
        binding.btnManageToolchain.setOnClickListener(v -> openManageCpp(true));

        refreshSourceInfo();
        refreshToolchainStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSourceInfo();
        refreshToolchainStatus();
    }

    private void openManageCpp(boolean openNdkManager) {
        Intent intent = new Intent(this, ManageCppActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("pkgName", getIntent().getStringExtra("pkgName"));
        if (openNdkManager) intent.putExtra("openNdkManager", true);
        startActivity(intent);
    }

    private void refreshSourceInfo() {
        String path = new FilePathUtil().getPathCpp(sc_id);
        binding.tvSourcePath.setText(path);

        File dir = new File(android.net.Uri.parse(path).getPath() != null
                ? android.net.Uri.parse(path).getPath() : path);
        int fileCount = countFilesRecursive(dir);
        binding.tvSourceStatus.setText(dir.exists()
                ? "Directory ready • " + fileCount + " file" + (fileCount == 1 ? "" : "s") + " present in native folder"
                : "Directory will be created when you add your first source file");
    }

    private int countFilesRecursive(File dir) {
        if (dir == null || !dir.exists()) return 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        int count = 0;
        for (File child : children) {
            count += child.isDirectory() ? countFilesRecursive(child) : 1;
        }
        return count;
    }

    private void refreshToolchainStatus() {
        boolean ndkInstalled = InbuiltNdkManager.isNdkInstalled(this);
        setStatusChip(binding.chipNdkStatus, ndkInstalled);
        setStatusChip(binding.chipCmakeStatus, ndkInstalled);
        binding.tvNdkLocation.setText("Location: files/bin/android-ndk/ (~360 MB)");
        binding.tvCmakeLocation.setText("Location: files/bin/cmake/ (~48 MB)");
    }

    private void setStatusChip(Chip chip, boolean installed) {
        chip.setText(installed ? "Installed" : "Not Installed");
        int bgAttr = installed ? R.attr.colorPrimaryContainer : R.attr.colorSurfaceContainerHigh;
        int textAttr = installed ? R.attr.colorOnPrimaryContainer : R.attr.colorOnSurfaceVariant;
        chip.setChipBackgroundColor(ColorStateList.valueOf(ThemeUtils.getColor(this, bgAttr)));
        chip.setTextColor(ThemeUtils.getColor(this, textAttr));
        chip.setChipStrokeWidth(0f);
    }

    private void finishWithResult() {
        Intent result = new Intent();
        result.putExtra("sc_id", sc_id);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        finishWithResult();
    }
}
