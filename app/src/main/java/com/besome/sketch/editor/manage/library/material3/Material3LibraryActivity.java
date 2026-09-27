package com.besome.sketch.editor.manage.library.material3;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CompoundButton;

import androidx.activity.OnBackPressedCallback;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageLibraryMaterial3Binding;

public class Material3LibraryActivity extends BaseAppCompatActivity {

    private ManageLibraryMaterial3Binding binding;
    private Material3LibraryManager material3LibraryManager;
    private String scId;
    private ProjectLibraryBean compatBean;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageLibraryMaterial3Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        enableEdgeToEdgeNoContrast();
        initialize();
    }

    private void initialize() {
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        scId = getIntent().getStringExtra("sc_id");
        if (scId == null || scId.trim().isEmpty()) {
            scId = com.besome.sketch.design.DesignActivity.sc_id;
        }

        if (scId != null && !scId.isEmpty()) {
            compatBean = a.a.a.jC.c(scId).c();
            material3LibraryManager = new Material3LibraryManager(scId);
        } else {
            compatBean = getIntent().getParcelableExtra("compat");
            material3LibraryManager = new Material3LibraryManager(compatBean);
        }

        if (compatBean != null && compatBean.configurations == null) {
            compatBean.configurations = new HashMap<>();
        }

        if (!material3LibraryManager.isAppCompatEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_mtrl_warning)
                    .setTitle("AppCompat is disabled!")
                    .setMessage("Please enable AppCompat first to use this feature")
                    .setPositiveButton("OK", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
        }

        binding.libSwitch.setChecked(material3LibraryManager.isMaterial3Enabled());
        binding.dynamicColorsSwitch.setChecked(material3LibraryManager.isDynamicColorsEnabled());

        binding.toggleGroup.setEnabled(binding.libSwitch.isChecked());
        binding.dynamicColorsSwitch.setEnabled(binding.libSwitch.isChecked());

        binding.libSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.toggleGroup.setEnabled(isChecked);
            binding.dynamicColorsSwitch.setEnabled(isChecked);
        });

        binding.layoutSwitchLib.setOnClickListener(view -> binding.libSwitch.setChecked(!binding.libSwitch.isChecked()));
        binding.layoutSwitchDynamicColors.setOnClickListener(view -> {
            if (binding.libSwitch.isChecked()) {
                binding.dynamicColorsSwitch.setChecked(!binding.dynamicColorsSwitch.isChecked());
            }
        });

        switch (material3LibraryManager.getTheme()) {
            case "Dark" -> binding.selectDark.setChecked(true);
            case "Light" -> binding.selectLight.setChecked(true);
            default -> binding.selectDayNight.setChecked(true);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (compatBean != null) {
                    compatBean.configurations.put("material3", binding.libSwitch.isChecked());
                    compatBean.configurations.put("dynamic_colors", binding.dynamicColorsSwitch.isChecked());

                    if (binding.selectDark.isChecked()) {
                        compatBean.configurations.put("theme", "Dark");
                    } else if (binding.selectLight.isChecked()) {
                        compatBean.configurations.put("theme", "Light");
                    } else {
                        compatBean.configurations.put("theme", "DayNight");
                    }

                    if (scId != null && !scId.isEmpty()) {
                        a.a.a.jC.c(scId).l();
                    }
                }

                Intent resultIntent = new Intent();
                resultIntent.putExtra("compat", compatBean);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }
}
