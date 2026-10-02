package mod.hey.studios.activity.managers.cpp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.text.DecimalFormat;

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
        boolean isGlobalContext = (sc_id == null || sc_id.trim().isEmpty());

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

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finishWithResult());

        if (isGlobalContext) {
            binding.toolbar.setTitle("Build Tools");
            binding.cardEnableTools.setVisibility(View.GONE);
            binding.tvProjectLocationHeader.setVisibility(View.GONE);
            binding.cardProjectLocation.setVisibility(View.GONE);
            binding.tvFooterWarning.setVisibility(View.GONE);
        } else {
            MaterialSwitch switchWidget = (MaterialSwitch) binding.switchEnable.getRoot();
            switchWidget.setChecked(isEnabled(this, sc_id));
            binding.layoutSwitch.setOnClickListener(v -> {
                boolean newState = !switchWidget.isChecked();
                switchWidget.setChecked(newState);
                setEnabled(newState);
            });

            binding.btnManageFiles.setOnClickListener(v -> openManageCpp(false));
            refreshSourceInfo();
        }

        binding.btnManageToolchain.setOnClickListener(v -> {
            if (InbuiltNdkManager.isNdkInstalled(this)) {
                showNdkManagerDialog();
            } else {
                showNdkInstallDialog();
            }
        });

        refreshToolchainStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sc_id != null && !sc_id.trim().isEmpty()) {
            refreshSourceInfo();
        }
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
                
        new Thread(() -> {
            int fileCount = countFilesRecursive(dir);
            runOnUiThread(() -> {
                binding.tvSourceStatus.setText(dir.exists()
                        ? "Directory ready • " + fileCount + " file" + (fileCount == 1 ? "" : "s") + " present in native folder"
                        : "Directory will be created when you add your first source file");
            });
        }).start();
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

        File cmakeDir = new File(getFilesDir(), "cmake");
        File ndkDir = InbuiltNdkManager.getInstalledNdkDir(this);
        if (ndkDir == null) ndkDir = new File(getFilesDir(), "ndk");

        calculateAndSetSizeAsync(cmakeDir, binding.tvCmakeLocation, "Location: files/cmake/");
        calculateAndSetSizeAsync(ndkDir, binding.tvNdkLocation, "Location: files/ndk/");
    }

    private void calculateAndSetSizeAsync(File dir, TextView targetView, String pathPrefix) {
        if (dir == null || !dir.exists()) {
            targetView.setText(pathPrefix + " (Not installed)");
            return;
        }
        new Thread(() -> {
            long sizeBytes = getFolderSize(dir);
            String sizeLabel = formatSize(sizeBytes);
            runOnUiThread(() -> targetView.setText(pathPrefix + " (~" + sizeLabel + ")"));
        }).start();
    }

    private long getFolderSize(File file) {
        long size = 0;
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) size += getFolderSize(child);
                }
            } else {
                size = file.length();
            }
        }
        return size;
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private void setStatusChip(Chip chip, boolean installed) {
        chip.setText(installed ? "Installed" : "Not Installed");
        int bgAttr = installed ? R.attr.colorPrimaryContainer : R.attr.colorSurfaceContainerHigh;
        int textAttr = installed ? R.attr.colorOnPrimaryContainer : R.attr.colorOnSurfaceVariant;
        chip.setChipBackgroundColor(ColorStateList.valueOf(ThemeUtils.getColor(this, bgAttr)));
        chip.setTextColor(ThemeUtils.getColor(this, textAttr));
        chip.setChipStrokeWidth(0f);
    }

    private void showNdkManagerDialog() {
        java.util.List<String> versions = InbuiltNdkManager.listInstalledNdkVersions(this);
        String message = versions.isEmpty()
                ? "No NDK installation detected."
                : "Installed: " + String.join(", ", versions);

        new MaterialAlertDialogBuilder(this)
                .setTitle("NDK Manager")
                .setMessage(message)
                .setPositiveButton("Install another version", (d, w) -> showNdkInstallDialog())
                .setNeutralButton("Repair", (d, w) -> {
                    for (String v : versions) InbuiltNdkManager.repairInstalledNdk(this, v);
                    SketchwareUtil.toast("Repair finished");
                })
                .setNegativeButton("Delete...", (d, w) -> showNdkDeleteDialog(versions))
                .show();
    }

    private void showNdkDeleteDialog(java.util.List<String> versions) {
        if (versions.isEmpty()) return;
        String[] items = versions.toArray(new String[0]);
        boolean[] checked = new boolean[items.length];

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete NDK version")
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Delete selected", (d, w) -> {
                    boolean any = false;
                    for (int i = 0; i < items.length; i++) {
                        if (checked[i]) {
                            InbuiltNdkManager.deleteNdkVersion(this, items[i]);
                            any = true;
                        }
                    }
                    if (any) {
                        SketchwareUtil.toast("Deleted");
                        refreshToolchainStatus();
                    }
                })
                .setNeutralButton("Delete all", (d, w) -> {
                    InbuiltNdkManager.deleteAllNdkVersions(this);
                    SketchwareUtil.toast("All NDK versions deleted");
                    refreshToolchainStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNdkInstallDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dp24 = SketchwareUtil.dpToPx(24);
        int dp16 = SketchwareUtil.dpToPx(16);
        layout.setPadding(dp24, dp16, dp24, dp16);

        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        til.setHint("Paste NDK Zip Link (aarch64)");

        com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(this);
        et.setText("https://github.com/MrIkso/AndroidIDE-NDK/releases/download/ndk/android-ndk-r26b-aarch64.zip");
        til.addView(et);
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Setup Inbuilt NDK")
                .setMessage("To compile C/C++ offline natively on your device, download the NDK & CMake toolchain via a direct zip link.")
                .setView(layout)
                .setPositiveButton("Download", (dialog, which) -> {
                    String url = et.getText().toString().trim();
                    if (!url.isEmpty()) {
                        startNdkDownload(url);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startNdkDownload(String url) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dp24 = SketchwareUtil.dpToPx(24);
        layout.setPadding(dp24, dp24, dp24, dp24);
        layout.setGravity(android.view.Gravity.CENTER);

        TextView statusText = new TextView(this);
        statusText.setText("Initializing Download...");
        statusText.setTextSize(14f);
        statusText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        statusText.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        statusText.setPadding(0, 0, 0, SketchwareUtil.dpToPx(16));

        com.google.android.material.progressindicator.LinearProgressIndicator progressIndicator = new com.google.android.material.progressindicator.LinearProgressIndicator(this);
        progressIndicator.setIndeterminate(true);

        layout.addView(statusText);
        layout.addView(progressIndicator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Setting up C/C++ compiler")
                .setView(layout)
                .setCancelable(false)
                .show();

        InbuiltNdkManager.installNdkAndCmake(this, url, new InbuiltNdkManager.InstallCallback() {
            @Override
            public void onProgress(String message, int progress, boolean isIndeterminate) {
                statusText.setText(message);
                if (isIndeterminate) {
                    if (!progressIndicator.isIndeterminate()) progressIndicator.setIndeterminate(true);
                } else {
                    if (progressIndicator.isIndeterminate()) progressIndicator.setIndeterminate(false);
                    progressIndicator.setProgressCompat(progress, true);
                }
            }

            @Override
            public void onSuccess() {
                progressDialog.dismiss();
                SketchwareUtil.toast("NDK and CMake installed successfully!");
                refreshToolchainStatus();
            }

            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                new MaterialAlertDialogBuilder(NativeToolsActivity.this)
                        .setTitle("Installation Failed")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
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
