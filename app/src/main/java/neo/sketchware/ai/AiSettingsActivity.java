package neo.sketchware.ai;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.R;

public class AiSettingsActivity extends BaseAppCompatActivity implements AiModelAdapter.Listener {

    private RecyclerView recyclerView;
    private View emptyState;
    private AiModelAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_settings);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerViewAiModels);
        emptyState = findViewById(R.id.textEmptyState);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AiModelAdapter(this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAddAiModel);
        fab.setOnClickListener(v -> showModelDialog(null));

        refreshList();
    }

    private void refreshList() {
        List<AiModelConfig> configs = AiManager.getConfigs(this);
        String activeId = AiManager.getActiveConfigId(this);
        adapter.submitList(configs, activeId);
        emptyState.setVisibility(configs.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(configs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onEditClicked(AiModelConfig config) {
        showModelDialog(config);
    }

    @Override
    public void onDeleteClicked(AiModelConfig config) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete AI model")
                .setMessage("Remove \"" + config.displayName + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AiManager.removeConfig(this, config.id);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onItemClicked(AiModelConfig config) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Set as active model?")
                .setMessage(config.displayName + " will be used for all AI features (layout, logic, error fix).")
                .setPositiveButton("Set active", (dialog, which) -> {
                    AiManager.setActiveConfigId(this, config.id);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showModelDialog(AiModelConfig existingConfig) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_ai_model, null);

        Spinner spinnerProvider = dialogView.findViewById(R.id.spinnerProvider);
        TextInputEditText editDisplayName = dialogView.findViewById(R.id.editDisplayName);
        TextInputEditText editModelName = dialogView.findViewById(R.id.editModelName);
        TextInputEditText editApiKey = dialogView.findViewById(R.id.editApiKey);
        TextInputLayout layoutCustomEndpoint = dialogView.findViewById(R.id.layoutCustomEndpoint);
        TextInputEditText editCustomEndpoint = dialogView.findViewById(R.id.editCustomEndpoint);

        Map<String, AiProvider> providers = AiProviderRegistry.getAll();
        List<String> providerIds = new ArrayList<>(providers.keySet());
        List<String> providerNames = new ArrayList<>();
        for (String id : providerIds) {
            providerNames.add(providers.get(id).getProviderName());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providerNames);
        spinnerProvider.setAdapter(spinnerAdapter);

        spinnerProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean needsEndpoint = providers.get(providerIds.get(position)).requiresCustomEndpoint();
                layoutCustomEndpoint.setVisibility(needsEndpoint ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        boolean isEdit = existingConfig != null;
        if (isEdit) {
            int index = providerIds.indexOf(existingConfig.providerId);
            if (index >= 0) spinnerProvider.setSelection(index);
            editDisplayName.setText(existingConfig.displayName);
            editModelName.setText(existingConfig.modelName);
            editApiKey.setText(existingConfig.apiKey);
            editCustomEndpoint.setText(existingConfig.customEndpoint);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? "Edit AI model" : "Add AI model")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String displayName = String.valueOf(editDisplayName.getText()).trim();
                    String modelName = String.valueOf(editModelName.getText()).trim();
                    String apiKey = String.valueOf(editApiKey.getText()).trim();
                    String customEndpoint = String.valueOf(editCustomEndpoint.getText()).trim();
                    String providerId = providerIds.get(spinnerProvider.getSelectedItemPosition());

                    if (TextUtils.isEmpty(displayName) || TextUtils.isEmpty(modelName)) {
                        return;
                    }

                    if (isEdit) {
                        existingConfig.providerId = providerId;
                        existingConfig.displayName = displayName;
                        existingConfig.modelName = modelName;
                        existingConfig.apiKey = apiKey;
                        existingConfig.customEndpoint = customEndpoint;
                        AiManager.updateConfig(this, existingConfig);
                    } else {
                        AiModelConfig newConfig = new AiModelConfig(providerId, displayName, apiKey, modelName, customEndpoint);
                        AiManager.addConfig(this, newConfig);
                    }
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
