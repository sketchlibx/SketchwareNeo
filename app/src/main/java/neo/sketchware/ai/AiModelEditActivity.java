package neo.sketchware.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.R;

public class AiModelEditActivity extends BaseAppCompatActivity {

    public static final String EXTRA_CONFIG = "config";

    private TextInputLayout layoutProvider;
    private MaterialAutoCompleteTextView dropdownProvider;
    private TextInputLayout layoutDisplayName;
    private TextInputLayout layoutModelName;
    private TextInputLayout layoutApiKey;
    private TextInputLayout layoutCustomEndpoint;
    private TextInputLayout layoutMaxTokens;
    private TextView textEndpointHint;

    private TextInputEditText editDisplayName;
    private TextInputEditText editModelName;
    private TextInputEditText editApiKey;
    private TextInputEditText editCustomEndpoint;
    private TextInputEditText editMaxTokens;

    private Slider sliderTemperature;
    private TextView textTemperatureValue;
    private View layoutThreads;
    private Slider sliderThreads;
    private TextView textThreadsValue;

    private AiModelConfig existingConfig;
    private List<String> providerIds;
    private Map<String, AiProvider> providers;
    private int cpuCores;

    private MaterialButton buttonTestConnection;
    private MaterialButton buttonSave;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_model_edit);

        existingConfig = getIntent().getSerializableExtra(EXTRA_CONFIG) instanceof AiModelConfig
                ? (AiModelConfig) getIntent().getSerializableExtra(EXTRA_CONFIG)
                : null;

        boolean isEdit = existingConfig != null;

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setTitle(isEdit ? "Edit AI model" : "Add AI model");
        toolbar.setNavigationOnClickListener(v -> finish());

        layoutProvider = findViewById(R.id.layoutProvider);
        dropdownProvider = findViewById(R.id.dropdownProvider);
        layoutDisplayName = findViewById(R.id.layoutDisplayName);
        layoutModelName = findViewById(R.id.layoutModelName);
        layoutApiKey = findViewById(R.id.layoutApiKey);
        layoutCustomEndpoint = findViewById(R.id.layoutCustomEndpoint);
        layoutMaxTokens = findViewById(R.id.layoutMaxTokens);
        textEndpointHint = findViewById(R.id.textEndpointHint);

        editDisplayName = findViewById(R.id.editDisplayName);
        editModelName = findViewById(R.id.editModelName);
        editApiKey = findViewById(R.id.editApiKey);
        editCustomEndpoint = findViewById(R.id.editCustomEndpoint);
        editMaxTokens = findViewById(R.id.editMaxTokens);

        sliderTemperature = findViewById(R.id.sliderTemperature);
        textTemperatureValue = findViewById(R.id.textTemperatureValue);
        layoutThreads = findViewById(R.id.layoutThreads);
        sliderThreads = findViewById(R.id.sliderThreads);
        textThreadsValue = findViewById(R.id.textThreadsValue);

        buttonTestConnection = findViewById(R.id.buttonTestConnection);
        buttonSave = findViewById(R.id.buttonSave);

        cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        sliderThreads.setValueTo(cpuCores);

        providers = AiProviderRegistry.getAll();
        providerIds = new ArrayList<>(providers.keySet());

        List<String> providerNames = new ArrayList<>();
        for (String id : providerIds) {
            providerNames.add(providers.get(id).getProviderName());
        }

        dropdownProvider.setSimpleItems(providerNames.toArray(new String[0]));
        dropdownProvider.setOnItemClickListener(
                (parent, view, position, id) ->
                        applyProviderCapabilities(providerIds.get(position), true)
        );

        sliderTemperature.addOnChangeListener((slider, value, fromUser) ->
                textTemperatureValue.setText(
                        String.format(Locale.getDefault(), "%.1f", value)
                )
        );

        sliderThreads.addOnChangeListener((slider, value, fromUser) ->
                textThreadsValue.setText(String.valueOf((int) value))
        );

        int initialProviderIndex = 0;

        if (isEdit) {
            editDisplayName.setText(existingConfig.displayName);
            editModelName.setText(existingConfig.modelName);
            editApiKey.setText(existingConfig.apiKey);
            editCustomEndpoint.setText(existingConfig.customEndpoint);
            editMaxTokens.setText(String.valueOf(existingConfig.maxTokens));

            int idx = providerIds.indexOf(existingConfig.providerId);
            if (idx >= 0) initialProviderIndex = idx;
        } else {
            editMaxTokens.setText("4096");
        }

        if (!providerIds.isEmpty()) {
            dropdownProvider.setText(providerNames.get(initialProviderIndex), false);
            applyProviderCapabilities(providerIds.get(initialProviderIndex), false);
        }

        sliderTemperature.setValue(
                clamp(isEdit ? (float) existingConfig.temperature : 0.7f, 0f, 2f)
        );
        textTemperatureValue.setText(
                String.format(Locale.getDefault(), "%.1f", sliderTemperature.getValue())
        );

        int initialThreads = isEdit && existingConfig.threads > 0
                ? existingConfig.threads
                : Math.min(4, cpuCores);
        sliderThreads.setValue(clamp(initialThreads, 1, cpuCores));
        textThreadsValue.setText(String.valueOf((int) sliderThreads.getValue()));

        buttonSave.setOnClickListener(v -> save());
        buttonTestConnection.setOnClickListener(v -> testConnection());
    }

    private void applyProviderCapabilities(String providerId, boolean userSelected) {
        AiProvider provider = providers.get(providerId);
        if (provider == null) return;

        boolean needsEndpoint = provider.requiresCustomEndpoint();
        layoutCustomEndpoint.setVisibility(needsEndpoint ? View.VISIBLE : View.GONE);
        textEndpointHint.setVisibility(provider.isLocal() ? View.VISIBLE : View.GONE);

        boolean needsApiKey = provider.requiresApiKey();
        layoutApiKey.setVisibility(needsApiKey ? View.VISIBLE : View.GONE);

        sliderTemperature.setEnabled(provider.supportsTemperature());
        layoutThreads.setVisibility(
                provider.supportsThreads() ? View.VISIBLE : View.GONE
        );

        if (needsEndpoint && TextUtils.isEmpty(String.valueOf(editCustomEndpoint.getText()))) {
            String defaultEndpoint = provider.getDefaultEndpoint();
            if (!TextUtils.isEmpty(defaultEndpoint)) {
                editCustomEndpoint.setText(defaultEndpoint);
                editCustomEndpoint.setSelection(defaultEndpoint.length());
            }
        }

        if (provider.isLocal() && userSelected && TextUtils.isEmpty(String.valueOf(editDisplayName.getText()))) {
            editDisplayName.setText("Local AI");
        }
    }

    private void save() {
        layoutProvider.setError(null);
        layoutDisplayName.setError(null);
        layoutModelName.setError(null);
        layoutApiKey.setError(null);
        layoutCustomEndpoint.setError(null);
        layoutMaxTokens.setError(null);

        String providerName = dropdownProvider.getText() == null
                ? ""
                : dropdownProvider.getText().toString();

        int providerIndex = -1;
        for (int i = 0; i < providerIds.size(); i++) {
            AiProvider provider = providers.get(providerIds.get(i));
            if (provider != null && provider.getProviderName().equals(providerName)) {
                providerIndex = i;
                break;
            }
        }

        String displayName = String.valueOf(editDisplayName.getText()).trim();
        String modelName = String.valueOf(editModelName.getText()).trim();
        String apiKey = String.valueOf(editApiKey.getText()).trim();
        String customEndpoint = String.valueOf(editCustomEndpoint.getText()).trim();
        String maxTokensText = String.valueOf(editMaxTokens.getText()).trim();

        boolean valid = true;

        if (providerIndex == -1) {
            layoutProvider.setError("Choose a provider");
            valid = false;
        }

        if (TextUtils.isEmpty(displayName)) {
            layoutDisplayName.setError("Required");
            valid = false;
        }

        if (TextUtils.isEmpty(modelName)) {
            layoutModelName.setError("Required");
            valid = false;
        }

        AiProvider provider = providerIndex >= 0
                ? providers.get(providerIds.get(providerIndex))
                : null;

        if (provider != null && provider.requiresApiKey() && TextUtils.isEmpty(apiKey)) {
            layoutApiKey.setError("Required for this provider");
            valid = false;
        }

        if (provider != null
                && provider.requiresCustomEndpoint()
                && TextUtils.isEmpty(customEndpoint)) {
            layoutCustomEndpoint.setError("Required for this provider");
            valid = false;
        }

        int maxTokens = 4096;
        if (!TextUtils.isEmpty(maxTokensText)) {
            try {
                maxTokens = Integer.parseInt(maxTokensText);
            } catch (NumberFormatException e) {
                layoutMaxTokens.setError("Enter a valid number");
                valid = false;
            }
        }

        if (maxTokens < 128 || maxTokens > 32768) {
            layoutMaxTokens.setError("Use 128–32768");
            valid = false;
        }

        if (!valid) return;

        String providerId = providerIds.get(providerIndex);
        double temperature = sliderTemperature.getValue();
        int threads = provider != null && provider.supportsThreads()
                ? (int) sliderThreads.getValue()
                : 0;

        if (existingConfig != null) {
            existingConfig.providerId = providerId;
            existingConfig.displayName = displayName;
            existingConfig.modelName = modelName;
            existingConfig.apiKey = apiKey;
            existingConfig.customEndpoint = customEndpoint;
            existingConfig.temperature = temperature;
            existingConfig.threads = threads;
            existingConfig.maxTokens = maxTokens;
            AiManager.updateConfig(this, existingConfig);
        } else {
            AiModelConfig newConfig = new AiModelConfig(
                    providerId,
                    displayName,
                    apiKey,
                    modelName,
                    customEndpoint
            );
            newConfig.temperature = temperature;
            newConfig.threads = threads;
            newConfig.maxTokens = maxTokens;
            AiManager.addConfig(this, newConfig);
        }

        setResult(RESULT_OK, new Intent());
        finish();
    }

    private void testConnection() {
        AiModelConfig candidate = buildCandidateConfig();
        if (candidate == null) return;

        buttonTestConnection.setEnabled(false);
        buttonSave.setEnabled(false);
        buttonTestConnection.setText("Testing…");

        AiManager.testConfig(this, candidate, new AiResponseCallback() {
            @Override
            public void onSuccess(String response) {
                buttonTestConnection.setEnabled(true);
                buttonSave.setEnabled(true);
                buttonTestConnection.setText("Test connection");
                Toast.makeText(
                        AiModelEditActivity.this,
                        "Connection successful",
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                buttonTestConnection.setEnabled(true);
                buttonSave.setEnabled(true);
                buttonTestConnection.setText("Test connection");
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        AiModelEditActivity.this
                )
                        .setTitle("Connection failed")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private AiModelConfig buildCandidateConfig() {
        layoutProvider.setError(null);
        layoutDisplayName.setError(null);
        layoutModelName.setError(null);
        layoutApiKey.setError(null);
        layoutCustomEndpoint.setError(null);
        layoutMaxTokens.setError(null);

        String providerName = dropdownProvider.getText() == null
                ? ""
                : dropdownProvider.getText().toString();

        String providerId = null;
        AiProvider selectedProvider = null;

        for (String id : providerIds) {
            AiProvider provider = providers.get(id);
            if (provider != null && provider.getProviderName().equals(providerName)) {
                providerId = id;
                selectedProvider = provider;
                break;
            }
        }

        if (selectedProvider == null) {
            layoutProvider.setError("Choose a provider");
            return null;
        }

        String displayName = String.valueOf(editDisplayName.getText()).trim();
        String modelName = String.valueOf(editModelName.getText()).trim();
        String apiKey = String.valueOf(editApiKey.getText()).trim();
        String endpoint = String.valueOf(editCustomEndpoint.getText()).trim();

        if (TextUtils.isEmpty(displayName)) {
            layoutDisplayName.setError("Required");
            return null;
        }

        if (TextUtils.isEmpty(modelName)) {
            layoutModelName.setError("Required");
            return null;
        }

        if (selectedProvider.requiresApiKey() && TextUtils.isEmpty(apiKey)) {
            layoutApiKey.setError("Required for this provider");
            return null;
        }

        if (selectedProvider.requiresCustomEndpoint() && TextUtils.isEmpty(endpoint)) {
            layoutCustomEndpoint.setError("Required for this provider");
            return null;
        }

        int maxTokens = 4096;
        try {
            maxTokens = Integer.parseInt(String.valueOf(editMaxTokens.getText()).trim());
        } catch (Exception e) {
            layoutMaxTokens.setError("Enter a valid number");
            return null;
        }

        if (maxTokens < 128 || maxTokens > 32768) {
            layoutMaxTokens.setError("Use 128–32768");
            return null;
        }

        AiModelConfig candidate = new AiModelConfig(
                providerId,
                displayName,
                apiKey,
                modelName,
                endpoint
        );
        candidate.temperature = sliderTemperature.getValue();
        candidate.threads = selectedProvider.supportsThreads()
                ? (int) sliderThreads.getValue()
                : 0;
        candidate.maxTokens = maxTokens;
        return candidate;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
