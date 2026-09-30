package neo.sketchware.ai;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.R;

public class AiSettingsActivity extends BaseAppCompatActivity implements AiModelAdapter.Listener {

    private RecyclerView recyclerView;
    private View emptyState;
    private View errorState;
    private TextView errorMessageText;
    private CircularProgressIndicator loadingIndicator;
    private AiModelAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_settings);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerViewAiModels);
        emptyState = findViewById(R.id.emptyStateLayout);
        errorState = findViewById(R.id.errorStateLayout);
        errorMessageText = findViewById(R.id.textErrorMessage);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AiModelAdapter(this);
        recyclerView.setAdapter(adapter);

        MaterialButton retryButton = findViewById(R.id.buttonRetry);
        retryButton.setOnClickListener(v -> refreshList());

        FloatingActionButton fab = findViewById(R.id.fabAddAiModel);
        fab.setOnClickListener(v -> showModelDialog(null));

        refreshList();
    }

    /**
     * Config reads are just an encrypted local SharedPreferences lookup (see AiManager) - not
     * slow enough to need a background thread - but the loading/empty/error states are wired
     * properly here in case that storage ever becomes async or starts failing (e.g. Keystore
     * key access problems, which AiManager.decrypt() otherwise swallows into an empty list).
     */
    private void refreshList() {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);

        try {
            List<AiModelConfig> configs = AiManager.getConfigs(this);
            String activeId = AiManager.getActiveConfigId(this);
            adapter.submitList(configs, activeId);

            loadingIndicator.setVisibility(View.GONE);
            boolean empty = configs.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        } catch (Exception e) {
            loadingIndicator.setVisibility(View.GONE);
            errorState.setVisibility(View.VISIBLE);
            errorMessageText.setText("Couldn't load your AI models: " +
                    (e.getMessage() != null ? e.getMessage() : "unknown error"));
        }
    }

    @Override
    public void onEditClicked(AiModelConfig config) {
        showModelDialog(config);
    }

    @Override
    public void onDuplicateClicked(AiModelConfig config) {
        AiModelConfig copy = new AiModelConfig(config.providerId, config.displayName + " (copy)", config.apiKey, config.modelName, config.customEndpoint);
        copy.temperature = config.temperature;
        copy.threads = config.threads;
        AiManager.addConfig(this, copy);
        refreshList();
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
                .setMessage(config.displayName + " will be used for all AI features (layout, logic, error fix, block generation).")
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
        TextInputLayout layoutDisplayName = dialogView.findViewById(R.id.layoutDisplayName);
        TextInputLayout layoutModelName = dialogView.findViewById(R.id.layoutModelName);
        TextInputEditText editDisplayName = dialogView.findViewById(R.id.editDisplayName);
        TextInputEditText editModelName = dialogView.findViewById(R.id.editModelName);
        TextInputEditText editApiKey = dialogView.findViewById(R.id.editApiKey);
        TextInputLayout layoutCustomEndpoint = dialogView.findViewById(R.id.layoutCustomEndpoint);
        TextInputEditText editCustomEndpoint = dialogView.findViewById(R.id.editCustomEndpoint);
        Slider sliderTemperature = dialogView.findViewById(R.id.sliderTemperature);
        TextView textTemperatureValue = dialogView.findViewById(R.id.textTemperatureValue);
        View layoutThreads = dialogView.findViewById(R.id.layoutThreads);
        Slider sliderThreads = dialogView.findViewById(R.id.sliderThreads);
        TextView textThreadsValue = dialogView.findViewById(R.id.textThreadsValue);

        int cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        sliderThreads.setValueTo(cpuCores);

        Map<String, AiProvider> providers = AiProviderRegistry.getAll();
        List<String> providerIds = new ArrayList<>(providers.keySet());
        List<String> providerNames = new ArrayList<>();
        for (String id : providerIds) {
            providerNames.add(providers.get(id).getProviderName());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providerNames);
        spinnerProvider.setAdapter(spinnerAdapter);

        sliderTemperature.addOnChangeListener((slider, value, fromUser) ->
                textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", value)));
        sliderThreads.addOnChangeListener((slider, value, fromUser) ->
                textThreadsValue.setText(String.valueOf((int) value)));

        spinnerProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                AiProvider provider = providers.get(providerIds.get(position));
                layoutCustomEndpoint.setVisibility(provider.requiresCustomEndpoint() ? View.VISIBLE : View.GONE);
                sliderTemperature.setEnabled(provider.supportsTemperature());
                layoutThreads.setVisibility(provider.supportsThreads() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        boolean isEdit = existingConfig != null;
        float initialTemperature = isEdit ? (float) existingConfig.temperature : 0.7f;
        sliderTemperature.setValue(clamp(initialTemperature, 0f, 2f));
        textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", sliderTemperature.getValue()));

        int initialThreads = isEdit && existingConfig.threads > 0 ? existingConfig.threads : cpuCores;
        sliderThreads.setValue(clamp(initialThreads, 1, cpuCores));
        textThreadsValue.setText(String.valueOf((int) sliderThreads.getValue()));

        if (isEdit) {
            int index = providerIds.indexOf(existingConfig.providerId);
            if (index >= 0) spinnerProvider.setSelection(index);
            editDisplayName.setText(existingConfig.displayName);
            editModelName.setText(existingConfig.modelName);
            editApiKey.setText(existingConfig.apiKey);
            editCustomEndpoint.setText(existingConfig.customEndpoint);
            // setSelection() above doesn't reliably fire onItemSelected when the position is
            // unchanged from the spinner's default, so apply the visibility rules directly too.
            AiProvider provider = providers.get(existingConfig.providerId);
            if (provider != null) {
                layoutCustomEndpoint.setVisibility(provider.requiresCustomEndpoint() ? View.VISIBLE : View.GONE);
                sliderTemperature.setEnabled(provider.supportsTemperature());
                layoutThreads.setVisibility(provider.supportsThreads() ? View.VISIBLE : View.GONE);
            }
        } else if (!providerIds.isEmpty()) {
            AiProvider provider = providers.get(providerIds.get(0));
            layoutCustomEndpoint.setVisibility(provider.requiresCustomEndpoint() ? View.VISIBLE : View.GONE);
            sliderTemperature.setEnabled(provider.supportsTemperature());
            layoutThreads.setVisibility(provider.supportsThreads() ? View.VISIBLE : View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? "Edit AI model" : "Add AI model")
                .setView(dialogView)
                .setPositiveButton("Save", null) // wired manually below so validation can keep the dialog open
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String displayName = String.valueOf(editDisplayName.getText()).trim();
            String modelName = String.valueOf(editModelName.getText()).trim();
            String apiKey = String.valueOf(editApiKey.getText()).trim();
            String customEndpoint = String.valueOf(editCustomEndpoint.getText()).trim();
            String providerId = providerIds.get(spinnerProvider.getSelectedItemPosition());
            AiProvider provider = providers.get(providerId);

            layoutDisplayName.setError(null);
            layoutModelName.setError(null);
            layoutCustomEndpoint.setError(null);
            boolean valid = true;
            if (TextUtils.isEmpty(displayName)) {
                layoutDisplayName.setError("Required");
                valid = false;
            }
            if (TextUtils.isEmpty(modelName)) {
                layoutModelName.setError("Required");
                valid = false;
            }
            if (provider != null && provider.requiresCustomEndpoint() && TextUtils.isEmpty(customEndpoint)) {
                layoutCustomEndpoint.setError("Required for this provider");
                valid = false;
            }
            if (!valid) return; // keep the dialog open so the errors are visible

            double temperature = sliderTemperature.getValue();
            int threads = provider != null && provider.supportsThreads() ? (int) sliderThreads.getValue() : 0;

            if (isEdit) {
                existingConfig.providerId = providerId;
                existingConfig.displayName = displayName;
                existingConfig.modelName = modelName;
                existingConfig.apiKey = apiKey;
                existingConfig.customEndpoint = customEndpoint;
                existingConfig.temperature = temperature;
                existingConfig.threads = threads;
                AiManager.updateConfig(this, existingConfig);
            } else {
                AiModelConfig newConfig = new AiModelConfig(providerId, displayName, apiKey, modelName, customEndpoint);
                newConfig.temperature = temperature;
                newConfig.threads = threads;
                AiManager.addConfig(this, newConfig);
            }
            refreshList();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
