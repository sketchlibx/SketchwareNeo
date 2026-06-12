package pro.sketchware.activities.editor.gradle;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import a.a.a.Lx;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yq;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageGradleActivityBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

import static com.besome.sketch.Config.VAR_DEFAULT_MIN_SDK_VERSION;
import static com.besome.sketch.Config.VAR_DEFAULT_TARGET_SDK_VERSION;

public class ManageGradleActivity extends BaseAppCompatActivity {

    private String sc_id;
    private ProjectSettings projectSettings;
    private ManageGradleActivityBinding binding;
    private String customGradleDir;

    private static final String FILE_APP_BUILD   = "app_build.gradle";
    private static final String FILE_BUILD       = "build.gradle";
    private static final String FILE_SETTINGS    = "settings.gradle";
    private static final String FILE_PROPERTIES  = "gradle.properties";

    private final String[] gradleFiles = {
            FILE_APP_BUILD,
            FILE_BUILD,
            FILE_SETTINGS,
            FILE_PROPERTIES
    };

    private final String[] fileDescriptions = {
            "Module-level build configuration (dependencies, plugins)",
            "Project-level build configuration",
            "Project repository and module settings",
            "Global Gradle properties and flags"
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ManageGradleActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }

        projectSettings = new ProjectSettings(sc_id);
        customGradleDir = FileUtil.getExternalStorageDir()
                + "/.sketchware/data/" + sc_id + "/custom_gradle/";

        setupToolbar();
        setupToggle();
        setupRecyclerView();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Gradle Scripts");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupToggle() {
        boolean enabled = projectSettings
                .getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE, "false")
                .equals("true");

        binding.switchGradle.setChecked(enabled);
        binding.listLayout.setVisibility(enabled ? View.VISIBLE : View.GONE);

        binding.switchContainer.setOnClickListener(v -> binding.switchGradle.performClick());

        binding.switchGradle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE,
                    isChecked ? "true" : "false");
            binding.listLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            if (isChecked) {
                ensureGradleFilesExist(false);
                SketchwareUtil.toast("Custom Gradle enabled");
            } else {
                SketchwareUtil.toast("Using default generated configurations");
            }
        });
    }

    private void setupRecyclerView() {
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(new GradleFileAdapter());
    }

    private class GradleFileAdapter extends RecyclerView.Adapter<GradleFileAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Programmatically creating a sleek list item to avoid needing a new XML file
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.setOrientation(LinearLayout.VERTICAL);

            LinearLayout container = new LinearLayout(parent.getContext());
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(android.view.Gravity.CENTER_VERTICAL);
            container.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(16));
            
            android.util.TypedValue outValue = new android.util.TypedValue();
            parent.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            container.setBackgroundResource(outValue.resourceId);
            container.setClickable(true);
            container.setFocusable(true);

            ImageView icon = new ImageView(parent.getContext());
            icon.setImageResource(R.drawable.ic_mtrl_code);
            icon.setColorFilter(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant));
            container.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

            LinearLayout textContainer = new LinearLayout(parent.getContext());
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            textParams.setMarginStart(SketchwareUtil.dpToPx(16));
            container.addView(textContainer, textParams);

            TextView title = new TextView(parent.getContext());
            title.setTextSize(16f);
            title.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            textContainer.addView(title);

            TextView subtitle = new TextView(parent.getContext());
            subtitle.setTextSize(12f);
            subtitle.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant));
            textContainer.addView(subtitle);

            root.addView(container);

            MaterialDivider divider = new MaterialDivider(parent.getContext());
            divider.setDividerInsetStart(SketchwareUtil.dpToPx(56));
            root.addView(divider);

            return new VH(root, container, title, subtitle, divider);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String fileName = gradleFiles[position];
            holder.title.setText(fileName);
            holder.subtitle.setText(fileDescriptions[position]);
            
            if (position == gradleFiles.length - 1) {
                holder.divider.setVisibility(View.GONE);
            } else {
                holder.divider.setVisibility(View.VISIBLE);
            }

            holder.container.setOnClickListener(v -> openEditor(fileName));
        }

        @Override
        public int getItemCount() { return gradleFiles.length; }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout container;
            TextView title;
            TextView subtitle;
            MaterialDivider divider;

            VH(View itemView, LinearLayout container, TextView title, TextView subtitle, MaterialDivider divider) { 
                super(itemView); 
                this.container = container;
                this.title = title;
                this.subtitle = subtitle;
                this.divider = divider;
            }
        }
    }

    private String getDefaultContent(String fileName) {
        yq yqInstance = buildYqInstance();

        switch (fileName) {
            case FILE_APP_BUILD: {
                String targetSdk = projectSettings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION));
                boolean viewBindingEnabled = projectSettings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_FALSE).equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
                return Lx.getBuildGradleString(VAR_DEFAULT_TARGET_SDK_VERSION, VAR_DEFAULT_MIN_SDK_VERSION, targetSdk, yqInstance != null ? yqInstance.N : new a.a.a.jq(), viewBindingEnabled);
            }
            case FILE_BUILD:
                return Lx.c("8.12.0", "4.4.3");

            case FILE_SETTINGS:
                return Lx.a();

            case FILE_PROPERTIES:
                return "android.enableR8.fullMode=false\nandroid.enableJetifier=true\nandroid.useAndroidX=true";

            default:
                return "";
        }
    }

    private yq buildYqInstance() {
        try {
            HashMap<String, Object> metadata = lC.b(sc_id);
            if (metadata == null) return null;
            yq instance = new yq(getApplicationContext(), wq.d(sc_id), metadata);
            try {
                instance.a(jC.c(sc_id), jC.b(sc_id), jC.a(sc_id));
            } catch (Exception ignored) { }
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureGradleFilesExist(boolean forceOverwrite) {
        FileUtil.makeDir(customGradleDir);
        for (String fileName : gradleFiles) {
            String path = customGradleDir + fileName;
            boolean needsWrite = forceOverwrite || !FileUtil.isExistFile(path) || FileUtil.readFile(path).trim().isEmpty();
            if (needsWrite) {
                FileUtil.writeFile(path, getDefaultContent(fileName));
            }
        }
    }

    private void openEditor(String fileName) {
        String path = customGradleDir + fileName;
        if (!FileUtil.isExistFile(path) || FileUtil.readFile(path).trim().isEmpty()) {
            FileUtil.makeDir(customGradleDir);
            FileUtil.writeFile(path, getDefaultContent(fileName));
        }
        Intent intent = new Intent(this, SrcCodeEditor.class);
        intent.putExtra("content", path);
        intent.putExtra("title", fileName);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(Menu.NONE, 101, Menu.NONE, "Reset Configuration");
        item.setIcon(R.drawable.ic_delete_white_24dp);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 101) {
            showCleanDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCleanDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset Gradle Configuration?")
                .setMessage("This will delete your customized Gradle files and revert to the default Sketchware build configurations.\n\nA backup of your current files will be created.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    String backupPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/backup_gradle_" + System.currentTimeMillis() + "/";
                    try {
                        File source = new File(customGradleDir);
                        if (source.exists()) backupDirectory(source, new File(backupPath));
                    } catch (IOException e) { e.printStackTrace(); }

                    FileUtil.deleteFile(customGradleDir);
                    projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE, "false");
                    binding.switchGradle.setChecked(false);

                    SketchwareUtil.toast("Gradle configurations reset. Backup saved to: " + backupPath);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void backupDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            String[] children = source.list();
            if (children != null) {
                for (String child : children) backupDirectory(new File(source, child), new File(target, child));
            }
        } else {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
        }
    }
}
