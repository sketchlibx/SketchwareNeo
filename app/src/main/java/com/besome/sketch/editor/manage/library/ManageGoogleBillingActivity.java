package com.besome.sketch.editor.manage.library;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.HashMap;

import pro.sketchware.databinding.ManageLibraryManageGoogleBillingBinding;

public class ManageGoogleBillingActivity extends BaseAppCompatActivity {
    private static final String DOCS_URL = "https://developer.android.com/google/play/billing";

    private ManageLibraryManageGoogleBillingBinding binding;
    private ProjectLibraryBean networkBean;
    private boolean restoring;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageLibraryManageGoogleBillingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        networkBean = savedInstanceState == null
                ? getIntent().getParcelableExtra("admob")
                : savedInstanceState.getParcelable("admob");
        if (networkBean == null) networkBean = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_ADMOB);
        if (networkBean.configurations == null) networkBean.configurations = new HashMap<>();

        setupUi();
    }

    private void setupUi() {
        binding.toolbar.setNavigationOnClickListener(v -> finishWithResult());

        restoring = true;
        binding.libSwitch.setChecked(networkBean.isGoogleBillingEnabled());
        restoring = false;

        binding.layoutSwitch.setOnClickListener(v -> {
            boolean newState = !binding.libSwitch.isChecked();
            binding.libSwitch.setChecked(newState);
            if (!restoring) {
                networkBean.setConfiguration(ProjectLibraryBean.CONFIG_GOOGLE_BILLING_ENABLED, newState);
            }
        });

        binding.libSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!restoring) {
                networkBean.setConfiguration(ProjectLibraryBean.CONFIG_GOOGLE_BILLING_ENABLED, checked);
            }
        });

        binding.btnOpenDoc.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No browser available to open this link", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void finishWithResult() {
        if (networkBean != null && binding != null) {
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_GOOGLE_BILLING_ENABLED, binding.libSwitch.isChecked());
        }
        Intent result = new Intent();
        result.putExtra("admob", networkBean);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        finishWithResult();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (networkBean != null && binding != null) {
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_GOOGLE_BILLING_ENABLED, binding.libSwitch.isChecked());
        }
        outState.putParcelable("admob", networkBean);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
