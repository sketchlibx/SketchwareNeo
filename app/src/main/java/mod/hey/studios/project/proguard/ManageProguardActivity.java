package mod.hey.studios.project.proguard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;

import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.code.SrcCodeEditor;
import neo.sketchware.R;
import neo.sketchware.databinding.ManageProguardBinding;
import neo.sketchware.utility.SketchwareUtil;

public class ManageProguardActivity extends BaseAppCompatActivity {

    private ProguardHandler pg;
    private ManageProguardBinding binding;
    private String sc_id;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageProguardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null || sc_id.isEmpty()) {
            SketchwareUtil.toast("Project ID not found", Toast.LENGTH_SHORT);
            finish();
            return;
        }

        pg = new ProguardHandler(sc_id);

        _initToolbar();
        initializeSwitches();
        initializeClickListeners();
    }

    private void _initToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Code Shrinking Manager");
        }
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());
    }

    private void initializeSwitches() {
        binding.swPgEnabled.setChecked(pg.isShrinkingEnabled());
        binding.swPgDebug.setChecked(pg.isDebugFilesEnabled());
        binding.r8Enabled.setChecked(pg.isR8Enabled());

        binding.swPgEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> pg.setProguardEnabled(isChecked));
        binding.swPgDebug.setOnCheckedChangeListener((buttonView, isChecked) -> pg.setDebugEnabled(isChecked));
        binding.r8Enabled.setOnCheckedChangeListener((buttonView, isChecked) -> pg.setR8Enabled(isChecked));
    }

    private void initializeClickListeners() {
        binding.rowPgEnabled.setOnClickListener(v -> binding.swPgEnabled.performClick());
        binding.rowPgDebug.setOnClickListener(v -> binding.swPgDebug.performClick());
        binding.rowR8Enabled.setOnClickListener(v -> binding.r8Enabled.performClick());

        binding.lnPgRules.setOnClickListener(v -> {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("title", "proguard-rules.pro");
            intent.putExtra("content", pg.getCustomProguardRules());
            startActivity(intent);
        });

        binding.lnPgFm.setOnClickListener(v -> showFullModeDialog());
    }

    private void showFullModeDialog() {
        ManageLocalLibrary mll = new ManageLocalLibrary(sc_id);

        if (mll.list.isEmpty()) {
            SketchwareUtil.toast("No local libraries found in this project.", Toast.LENGTH_SHORT);
            return;
        }

        String[] libraries = new String[mll.list.size()];
        boolean[] enabledLibraries = new boolean[mll.list.size()];

        for (int i = 0; i < mll.list.size(); i++) {
            HashMap<String, Object> current = mll.list.get(i);

            Object name = current.get("name");
            if (name instanceof String) {
                libraries[i] = (String) name;
                enabledLibraries[i] = pg.libIsProguardFMEnabled(libraries[i]);
            } else {
                libraries[i] = "(broken library configuration)";
                enabledLibraries[i] = false;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Local Libraries")
                .setMultiChoiceItems(
                        libraries,
                        enabledLibraries,
                        (dialog, which, isChecked) -> enabledLibraries[which] = isChecked)
                .setPositiveButton(
                        R.string.common_word_save,
                        (dialog, which) -> {
                            ArrayList<String> finalList = new ArrayList<>();
                            for (int i = 0; i < libraries.length; i++) {
                                if (enabledLibraries[i] && !libraries[i].equals("(broken library configuration)")) {
                                    finalList.add(libraries[i]);
                                }
                            }
                            pg.setProguardFMLibs(finalList);
                        })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }
}
