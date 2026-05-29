package pro.sketchware.activities.editor.gradle;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import a.a.a.jC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yq;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageGradleActivityBinding;
import pro.sketchware.databinding.ManageJavaItemHsBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ManageGradleActivity extends BaseAppCompatActivity {

    private String sc_id;
    private ProjectSettings projectSettings;
    private ManageGradleActivityBinding binding;
    private String customGradleDir;

    private final String[] gradleFiles = {
            "app_build.gradle",
            "build.gradle",
            "settings.gradle",
            "gradle.properties"
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

        customGradleDir =
                FileUtil.getExternalStorageDir()
                        + "/.sketchware/data/"
                        + sc_id
                        + "/custom_gradle/";

        setupToolbar();
        setupToggle();
        setupRecyclerView();
        setupFab();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Gradle Manager");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupToggle() {
        boolean enabled = projectSettings
                .getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE, "false")
                .equals("true");

        binding.switchGradle.setChecked(enabled);

        binding.listLayout.setVisibility(
                enabled ? View.VISIBLE : View.GONE
        );

        binding.switchGradle.setOnCheckedChangeListener((buttonView, isChecked) -> {

            projectSettings.setValue(
                    ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE,
                    isChecked ? "true" : "false"
            );

            binding.listLayout.setVisibility(
                    isChecked ? View.VISIBLE : View.GONE
            );

            if (isChecked) {
                generateDefaultGradleFiles(false);
                SketchwareUtil.toast("Custom Gradle Enabled");
            } else {
                SketchwareUtil.toast("Custom Gradle Disabled");
            }
        });
    }

    private void setupRecyclerView() {
        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        binding.recyclerView.setAdapter(
                new GradleFileAdapter()
        );
    }

    private void setupFab() {
        binding.fabClean.setOnClickListener(v -> showCleanDialog());
    }

    private class GradleFileAdapter
            extends RecyclerView.Adapter<GradleFileAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {

            ManageJavaItemHsBinding itemBinding =
                    ManageJavaItemHsBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    );

            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(
                @NonNull ViewHolder holder,
                int position
        ) {

            String fileName = gradleFiles[position];
            
            holder.binding.chevron.setVisibility(View.GONE);
            holder.binding.more.setVisibility(View.GONE);

            holder.binding.title.setText(fileName);
            holder.binding.icon.setImageResource(R.drawable.ic_mtrl_code);

            holder.binding.getRoot().setOnClickListener(v -> {
                openEditor(fileName);
            });
        }

        @Override
        public int getItemCount() {
            return gradleFiles.length;
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            ManageJavaItemHsBinding binding;

            ViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private void generateDefaultGradleFiles(boolean forceOverwrite) {

        FileUtil.makeDir(customGradleDir);

        if (!forceOverwrite
                && FileUtil.isExistFile(customGradleDir + "app_build.gradle")) {
            return;
        }

        try {

            HashMap<String, Object> metadata = lC.b(sc_id);

            yq gradleGenerator = new yq(
                    getApplicationContext(),
                    wq.d(sc_id),
                    metadata
            );

            gradleGenerator.a(
                    jC.c(sc_id),
                    jC.b(sc_id),
                    jC.a(sc_id)
            );

            gradleGenerator.generateGradleFiles();

            String projectPath = gradleGenerator.projectMyscPath;

            FileUtil.copyFile(
                    projectPath + "/app/build.gradle",
                    customGradleDir + "app_build.gradle"
            );

            FileUtil.copyFile(
                    projectPath + "/build.gradle",
                    customGradleDir + "build.gradle"
            );

            FileUtil.copyFile(
                    projectPath + "/settings.gradle",
                    customGradleDir + "settings.gradle"
            );

            FileUtil.copyFile(
                    projectPath + "/gradle.properties",
                    customGradleDir + "gradle.properties"
            );

        } catch (Throwable e) {
            e.printStackTrace();
            SketchwareUtil.toast("Failed to generate Gradle files");
        }
    }

    private void openEditor(String fileName) {

        String path = customGradleDir + fileName;

        if (!FileUtil.isExistFile(path)) {
            generateDefaultGradleFiles(false);
        }

        Intent intent = new Intent(this, SrcCodeEditor.class);

        intent.putExtra("content", path);
        intent.putExtra("title", fileName);
        intent.putExtra("sc_id", sc_id);

        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuItem item = menu.add(
                Menu.NONE,
                101,
                Menu.NONE,
                "Clean & Reset"
        );

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
                .setTitle("Clean & Reset Gradle")
                .setMessage(
                        "Are you sure you want to delete all custom Gradle files?\n\n"
                                + "A backup will be created automatically."
                )
                .setPositiveButton("Clean & Reset", (dialog, which) -> {

                    String backupPath =
                            FileUtil.getExternalStorageDir()
                                    + "/.sketchware/data/"
                                    + sc_id
                                    + "/backup_gradle_"
                                    + System.currentTimeMillis()
                                    + "/";

                    try {

                        File source = new File(customGradleDir);

                        if (source.exists()) {
                            backupDirectory(
                                    source,
                                    new File(backupPath)
                            );
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    FileUtil.deleteFile(customGradleDir);

                    projectSettings.setValue(
                            ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE,
                            "false"
                    );

                    binding.switchGradle.setChecked(false);

                    SketchwareUtil.toast(
                            "Gradle cleaned successfully"
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void backupDirectory(
            File source,
            File target
    ) throws IOException {

        if (source.isDirectory()) {

            if (!target.exists()) {
                target.mkdirs();
            }

            String[] children = source.list();

            if (children != null) {

                for (String child : children) {

                    backupDirectory(
                            new File(source, child),
                            new File(target, child)
                    );
                }
            }

        } else {

            File parent = target.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            FileInputStream input = null;
            FileOutputStream output = null;

            try {

                input = new FileInputStream(source);
                output = new FileOutputStream(target);

                byte[] buffer = new byte[4096];

                int length;

                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }

            } finally {

                if (input != null) {
                    input.close();
                }

                if (output != null) {
                    output.close();
                }
            }
        }
    }
}