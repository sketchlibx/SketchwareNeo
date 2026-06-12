package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonParseException;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mod.hey.studios.util.Helper;
import mod.jbk.util.LogUtil;
import pro.sketchware.R;
import pro.sketchware.databinding.PreferenceActivityNewBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ConfigActivity extends BaseAppCompatActivity {

    public static final File SETTINGS_FILE = new File(FileUtil.getExternalStorageDir(), ".sketchware/data/settings.json");
    
    // Constants kept exactly as requested to maintain compilation
    public static final String SETTING_ALWAYS_SHOW_BLOCKS = "always-show-blocks";
    public static final String SETTING_BACKUP_DIRECTORY = "backup-dir";
    public static final String SETTING_ROOT_AUTO_INSTALL_PROJECTS = "root-auto-install-projects";
    public static final String SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING = "root-auto-open-after-installing";
    public static final String SETTING_BACKUP_FILENAME = "backup-filename";
    public static final String SETTING_SHOW_BUILT_IN_BLOCKS = "built-in-blocks";
    public static final String SETTING_SHOW_EVERY_SINGLE_BLOCK = "show-every-single-block";
    public static final String SETTING_USE_NEW_VERSION_CONTROL = "use-new-version-control";
    public static final String SETTING_USE_ASD_HIGHLIGHTER = "use-asd-highlighter";
    public static final String SETTING_CRITICAL_UPDATE_REMINDER = "critical-update-reminder";
    public static final String SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH = "palletteDir";
    public static final String SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH = "blockDir";
    
    public static final String SETTING_TREE_VIEW = "enable-tree-view";
    public static final String SETTING_JAVA_TREE_VIEW = "enable-java-tree-view"; 
    public static final String SETTING_ASSETS_TREE_VIEW = "enable-assets-tree-view"; 
    public static final String SETTING_RESOURCE_TREE_VIEW = "enable-resource-tree-view"; 
    public static final String SETTING_GIT_DIRECT_PUSH = "git-direct-push";

    private PreferenceActivityNewBinding binding;

    public static String getBackupPath() {
        return DataStore.getInstance().getString(SETTING_BACKUP_DIRECTORY, "/.sketchware/backups/");
    }

    public static String getStringSettingValueOrSetAndGet(String settingKey, String toReturnAndSetIfNotFound) {
        var dataStore = DataStore.getInstance();
        Map<String, Object> settings = dataStore.getSettings();

        Object value = settings.get(settingKey);
        if (value instanceof String s) return s;
        
        dataStore.putString(settingKey, toReturnAndSetIfNotFound);
        dataStore.persist();
        return toReturnAndSetIfNotFound;
    }

    public static String getBackupFileName() {
        return DataStore.getInstance().getString(SETTING_BACKUP_FILENAME, "$projectName v$versionName ($pkgName, $versionCode) $time(yyyy-MM-dd'T'HHmmss)");
    }

    public static boolean isSettingEnabled(String keyName) {
        return DataStore.getInstance().getBoolean(keyName, false);
    }

    public static void setSetting(String key, Object value) {
        var dataStore = DataStore.getInstance();
        if (value instanceof String s) dataStore.putString(key, s);
        else if (value instanceof Boolean b) dataStore.putBoolean(key, b);
        else throw new IllegalArgumentException("Unhandled data type " + value.getClass());
        dataStore.persist();
    }

    @NonNull
    private static HashMap<String, Object> readSettings() {
        HashMap<String, Object> settings;
        if (SETTINGS_FILE.exists()) {
            Exception toLog;
            try {
                settings = getGson().fromJson(FileUtil.readFile(SETTINGS_FILE.getAbsolutePath()), Helper.TYPE_MAP);
                if (settings != null) return settings;
                toLog = new NullPointerException("settings == null");
            } catch (JsonParseException e) {
                toLog = e;
            }
            SketchwareUtil.toastError("Couldn't parse App Settings! Restoring defaults.");
            LogUtil.e("ConfigActivity", "Failed to parse App Settings.", toLog);
        }
        settings = new HashMap<>();
        restoreDefaultSettings(settings);
        return settings;
    }

    private static void restoreDefaultSettings(HashMap<String, Object> settings) {
        settings.clear();
        List<String> keys = Arrays.asList(
                SETTING_ALWAYS_SHOW_BLOCKS, SETTING_BACKUP_DIRECTORY, SETTING_ROOT_AUTO_INSTALL_PROJECTS,
                SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING, SETTING_SHOW_BUILT_IN_BLOCKS, SETTING_SHOW_EVERY_SINGLE_BLOCK,
                SETTING_USE_NEW_VERSION_CONTROL, SETTING_USE_ASD_HIGHLIGHTER, SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, SETTING_TREE_VIEW, SETTING_JAVA_TREE_VIEW,
                SETTING_ASSETS_TREE_VIEW, SETTING_RESOURCE_TREE_VIEW, SETTING_GIT_DIRECT_PUSH);

        for (String key : keys) {
            settings.put(key, getDefaultValue(key));
        }
        FileUtil.writeFile(SETTINGS_FILE.getAbsolutePath(), getGson().toJson(settings));
    }

    public static Object getDefaultValue(String key) {
        return switch (key) {
            case SETTING_ALWAYS_SHOW_BLOCKS, SETTING_ROOT_AUTO_INSTALL_PROJECTS, SETTING_SHOW_BUILT_IN_BLOCKS,
                 SETTING_SHOW_EVERY_SINGLE_BLOCK, SETTING_USE_NEW_VERSION_CONTROL, SETTING_USE_ASD_HIGHLIGHTER, 
                 SETTING_TREE_VIEW, SETTING_JAVA_TREE_VIEW, SETTING_ASSETS_TREE_VIEW, SETTING_RESOURCE_TREE_VIEW, 
                 SETTING_GIT_DIRECT_PUSH -> false;
            case SETTING_BACKUP_DIRECTORY -> "/.sketchware/backups/";
            case SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING -> true;
            case SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH -> "/.sketchware/resources/block/My Block/palette.json";
            case SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH -> "/.sketchware/resources/block/My Block/block.json";
            default -> throw new IllegalArgumentException("Unknown key '" + key + "'!");
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        
        binding = PreferenceActivityNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.topAppBar.setTitle("Mod Settings");
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        setupPreferences(binding.content);
    }

    private void setupPreferences(ViewGroup content) {
        content.removeAllViews();

        // --- General ---
        content.addView(createCategoryHeader("General"));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_block, "Always Show Blocks", "Keep code blocks visible even in invalid states", SETTING_ALWAYS_SHOW_BLOCKS));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_puzzle, "Show Built-in Blocks", "Display standard blocks in custom palettes", SETTING_SHOW_BUILT_IN_BLOCKS));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_view_module, "Show Every Single Block", "Unhide experimental and deprecated blocks", SETTING_SHOW_EVERY_SINGLE_BLOCK));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_code, "ASD Highlighter", "Use advanced syntax highlighting for dialogs", SETTING_USE_ASD_HIGHLIGHTER));

        // --- Project Explorer ---
        content.addView(createCategoryHeader("Project Explorer"));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_folder_open, "Enable Tree View", "Display project files in a hierarchical tree structure", SETTING_TREE_VIEW));

        // --- Backup & Restore ---
        content.addView(createCategoryHeader("Backup & Restore"));
        TextView[] backupDirDesc = new TextView[1];
        content.addView(createActionPreference(R.drawable.ic_mtrl_folder, "Backup Directory", getBackupPath(), v -> showBackupDirDialog(backupDirDesc[0]), backupDirDesc));
        TextView[] backupNameDesc = new TextView[1];
        content.addView(createActionPreference(R.drawable.ic_mtrl_file, "Backup Filename Format", "Configure SWB naming syntax", v -> showBackupNameDialog(), backupNameDesc));

        // --- Version Control ---
        content.addView(createCategoryHeader("Version Control"));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_version_control, "New Version Control", "Use optimized Git-based system for project history", SETTING_USE_NEW_VERSION_CONTROL));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_upload, "Git Direct Commit & Push", "Single action to commit and push changes", SETTING_GIT_DIRECT_PUSH));

        // --- Root Features ---
        content.addView(createCategoryHeader("Root Features"));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_code, "Auto Install Projects (Root)", "Silently install compiled APKs using root access", SETTING_ROOT_AUTO_INSTALL_PROJECTS));
        content.addView(createSwitchPreference(R.drawable.ic_mtrl_apk_install, "Auto Open App", "Launch application immediately after install", SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING));

        // --- Block Manager ---
        content.addView(createCategoryHeader("Block Manager"));
        TextView[] palDirDesc = new TextView[1];
        content.addView(createActionPreference(R.drawable.ic_mtrl_palette, "Palette File Path", "Set custom palette.json location", v -> showPathDialog("Palette Path", SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, palDirDesc[0]), palDirDesc));
        TextView[] blkDirDesc = new TextView[1];
        content.addView(createActionPreference(R.drawable.ic_mtrl_moreblock, "Block File Path", "Set custom block.json location", v -> showPathDialog("Block Path", SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, blkDirDesc[0]), blkDirDesc));
    }

    // --- Modern M3 UI Generators ---

    private View createCategoryHeader(String titleText) {
        TextView tv = new TextView(this);
        tv.setText(titleText);
        tv.setTextSize(14f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(ThemeUtils.getColor(this, R.attr.colorPrimary));
        int padH = SketchwareUtil.dpToPx(24);
        tv.setPadding(padH, SketchwareUtil.dpToPx(24), padH, SketchwareUtil.dpToPx(8));
        return tv;
    }

    private View createSwitchPreference(int iconRes, String title, String desc, String prefKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);
        
        int pad = SketchwareUtil.dpToPx(16);
        row.setPadding(SketchwareUtil.dpToPx(24), pad, SketchwareUtil.dpToPx(24), pad);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        row.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textParams.setMargins(SketchwareUtil.dpToPx(16), 0, SketchwareUtil.dpToPx(16), 0);
        row.addView(textContainer, textParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        textContainer.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(13f);
        tvDesc.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        tvDesc.setPadding(0, SketchwareUtil.dpToPx(2), 0, 0);
        textContainer.addView(tvDesc);

        MaterialSwitch mSwitch = new MaterialSwitch(this);
        DataStore ds = DataStore.getInstance();
        mSwitch.setChecked(ds.getBoolean(prefKey, (Boolean) getDefaultValue(prefKey)));
        row.addView(mSwitch);

        row.setOnClickListener(v -> mSwitch.toggle());

        mSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (prefKey.equals(SETTING_ROOT_AUTO_INSTALL_PROJECTS) && isChecked) {
                Shell.getShell(shell -> {
                    if (!shell.isRoot()) {
                        Snackbar.make(binding.getRoot(), "Couldn't acquire root access", BaseTransientBottomBar.LENGTH_SHORT).show();
                        mSwitch.setChecked(false);
                    } else {
                        ds.putBoolean(prefKey, true);
                    }
                });
            } else {
                ds.putBoolean(prefKey, isChecked);
            }
        });

        return row;
    }

    private View createActionPreference(int iconRes, String title, String desc, View.OnClickListener listener, TextView[] outDescView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);
        
        int pad = SketchwareUtil.dpToPx(16);
        row.setPadding(SketchwareUtil.dpToPx(24), pad, SketchwareUtil.dpToPx(24), pad);
        row.setOnClickListener(listener);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        row.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textParams.setMargins(SketchwareUtil.dpToPx(16), 0, SketchwareUtil.dpToPx(16), 0);
        row.addView(textContainer, textParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        textContainer.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(13f);
        tvDesc.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        tvDesc.setPadding(0, SketchwareUtil.dpToPx(2), 0, 0);
        textContainer.addView(tvDesc);
        
        if (outDescView != null && outDescView.length > 0) {
            outDescView[0] = tvDesc;
        }

        return row;
    }

    // --- Input Dialogs ---

    private void showBackupDirDialog(TextView descView) {
        showInputDialog("Backup Directory", "e.g. /.sketchware/backups/", getBackupPath(), text -> {
            DataStore.getInstance().putString(SETTING_BACKUP_DIRECTORY, text);
            if (descView != null) descView.setText(text);
        });
    }

    private void showPathDialog(String title, String key, TextView descView) {
        showInputDialog(title, "Enter path inside /Internal storage/", DataStore.getInstance().getString(key, ""), text -> {
            DataStore.getInstance().putString(key, text);
            if (descView != null) descView.setText("Custom path configured");
        });
    }

    private void showBackupNameDialog() {
        String hint = "Variables: $projectName, $versionCode, $versionName, $pkgName, $timeInMs";
        showInputDialog("Backup Filename Format", hint, getBackupFileName(), text -> {
            DataStore.getInstance().putString(SETTING_BACKUP_FILENAME, text);
        });
    }

    private void showInputDialog(String title, String hint, String currentVal, OnInputSaved listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = SketchwareUtil.dpToPx(24);
        layout.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        layout.addView(tvTitle);

        TextView tvHint = new TextView(this);
        tvHint.setText(hint);
        tvHint.setTextSize(14f);
        tvHint.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        tvHint.setPadding(0, SketchwareUtil.dpToPx(8), 0, SketchwareUtil.dpToPx(16));
        layout.addView(tvHint);

        TextInputLayout til = new TextInputLayout(this);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(28f, 28f, 28f, 28f);

        TextInputEditText et = new TextInputEditText(this);
        et.setText(currentVal);
        et.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        til.addView(et);
        layout.addView(til, new LinearLayout.LayoutParams(-1, -2));

        MaterialButton btnSave = new MaterialButton(this);
        btnSave.setText("Save");
        btnSave.setCornerRadius(SketchwareUtil.dpToPx(24));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-1, -2);
        btnParams.topMargin = SketchwareUtil.dpToPx(16);
        layout.addView(btnSave, btnParams);

        btnSave.setOnClickListener(v -> {
            listener.onSave(et.getText().toString().trim());
            dialog.dismiss();
        });

        dialog.setContentView(layout);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }

    interface OnInputSaved { void onSave(String text); }

    public static class DataStore {
        private static DataStore INSTANCE;
        private final Map<String, Object> settings;

        private DataStore() {
            settings = readSettings();
        }

        public static DataStore getInstance() {
            return INSTANCE == null ? (INSTANCE = new DataStore()) : INSTANCE;
        }

        private Map<String, Object> getSettings() {
            return settings;
        }

        public void persist() {
            FileUtil.writeFile(SETTINGS_FILE.getAbsolutePath(), getGson().toJson(settings));
        }

        public void putString(String key, @Nullable String value) {
            if (value == null) settings.remove(key);
            else settings.put(key, value);
            persist();
        }

        @Nullable
        public String getString(String key, @Nullable String defValue) {
            var value = settings.get(key);
            if (value instanceof String s) return s;
            return defValue;
        }

        public void putBoolean(String key, boolean value) {
            settings.put(key, value);
            persist();
        }

        public boolean getBoolean(String key, boolean defValue) {
            var value = settings.get(key);
            if (value instanceof Boolean b) return b;
            return defValue;
        }
    }
}
