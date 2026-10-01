package neo.sketchware.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

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

/**
 * Dedicated Add/Edit screen for an AI model configuration - replaces the old cramped
 * AlertDialog. Launched by AiSettingsActivity with EXTRA_CONFIG (Serializable AiModelConfig)
 * for edit mode, or no extra for add mode. Returns RESULT_OK when saved so the caller knows
 * to refresh its list; AiManager persistence itself is untouched/reused as-is.
 */
public class AiModelEditActivity extends BaseAppCompatActivity {

    public static final String EXTRA_CONFIG = "config";

    private TextInputLayout layoutProvider;
    private MaterialAutoCompleteTextView dropdownProvider;
    private TextInputLayout layoutDisplayName;
    private TextInputLayout layoutModelName;
    private TextInputLayout layoutApiKey;
    private TextInputLayout layoutCustomEndpoint;
    private TextInputEditText editDisplayName;
    private TextInputEditText editModelName;
    private TextInputEditText editApiKey;
    private TextInputEditText editCustomEndpoint;
    private Slider sliderTemperature;
    private android.widget.TextView textTemperatureValue;
    private View layoutThreads;
    private Slider sliderThreads;
    private android.widget.TextView textThreadsValue;

    private AiModelConfig existingConfig;
    private List<String> providerIds;
    private Map<String, AiProvider> providers;
    private int cpuCores;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_model_edit);

        existingConfig = (AiModelConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
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
        editDisplayName = findViewById(R.id.editDisplayName);
        editModelName = findViewById(R.id.editModelName);
        editApiKey = findViewById(R.id.editApiKey);
        editCustomEndpoint = findViewById(R.id.editCustomEndpoint);
        sliderTemperature = findViewById(R.id.sliderTemperature);
        textTemperatureValue = findViewById(R.id.textTemperatureValue);
        layoutThreads = findViewById(R.id.layoutThreads);
        sliderThreads = findViewById(R.id.sliderThreads);
        textThreadsValue = findViewById(R.id.textThreadsValue);
        MaterialButton buttonSave = findViewById(R.id.buttonSave);

        cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        sliderThreads.setValueTo(cpuCores);

        providers = AiProviderRegistry.getAll();
        providerIds = new ArrayList<>(providers.keySet());
        List<String> providerNames = new ArrayList<>();
        for (String id : providerIds) providerNames.add(providers.get(id).getProviderName());

        dropdownProvider.setSimpleItems(providerNames.toArray(new String[0]));
        dropdownProvider.setOnItemClickListener((parent, view, position, id) -> applyProviderCapabilities(providerIds.get(position)));

        sliderTemperature.addOnChangeListener((slider, value, fromUser) ->
                textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", value)));
        sliderThreads.addOnChangeListener((slider, value, fromUser) ->
                textThreadsValue.setText(String.valueOf((int) value)));

        int initialProviderIndex = 0;
        if (isEdit) {
            editDisplayName.setText(existingConfig.displayName);
            editModelName.setText(existingConfig.modelName);
            editApiKey.setText(existingConfig.apiKey);
            editCustomEndpoint.setText(existingConfig.customEndpoint);
            int idx = providerIds.indexOf(existingConfig.providerId);
            if (idx >= 0) initialProviderIndex = idx;
        }
        if (!providerIds.isEmpty()) {
            dropdownProvider.setText(providerNames.get(initialProviderIndex), false);
            applyProviderCapabilities(providerIds.get(initialProviderIndex));
        }

        sliderTemperature.setValue(clamp(isEdit ? (float) existingConfig.temperature : 0.7f, 0f, 2f));
        textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", sliderTemperature.getValue()));
        int initialThreads = isEdit && existingConfig.threads > 0 ? existingConfig.threads : cpuCores;
        sliderThreads.setValue(clamp(initialThreads, 1, cpuCores));
        textThreadsValue.setText(String.valueOf((int) sliderThreads.getValue()));

        buttonSave.setOnClickListener(v -> save());
    }

    private void applyProviderCapabilities(String providerId) {
        AiProvider provider = providers.get(providerId);
        if (provider == null) return;
        layoutCustomEndpoint.setVisibility(provider.requiresCustomEndpoint() ? View.VISIBLE : View.GONE);
        sliderTemperature.setEnabled(provider.supportsTemperature());
        layoutThreads.setVisibility(provider.supportsThreads() ? View.VISIBLE : View.GONE);
    }

    private void save() {
        layoutProvider.setError(null);
        layoutDisplayName.setError(null);
        layoutModelName.setError(null);
        layoutCustomEndpoint.setError(null);

        String providerName = dropdownProvider.getText() == null ? "" : dropdownProvider.getText().toString();
        int providerIndex = -1;
        for (int i = 0; i < providerIds.size(); i++) {
            if (providers.get(providerIds.get(i)).getProviderName().equals(providerName)) {
                providerIndex = i;
                break;
            }
        }
        String displayName = String.valueOf(editDisplayName.getText()).trim();
        String modelName = String.valueOf(editModelName.getText()).trim();
        String apiKey = String.valueOf(editApiKey.getText()).trim();
        String customEndpoint = String.valueOf(editCustomEndpoint.getText()).trim();

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
        AiProvider provider = providerIndex >= 0 ? providers.get(providerIds.get(providerIndex)) : null;
        if (provider != null && provider.requiresCustomEndpoint() && TextUtils.isEmpty(customEndpoint)) {
            layoutCustomEndpoint.setError("Required for this provider");
            valid = false;
        }
        if (!valid) return;

        String providerId = providerIds.get(providerIndex);
        double temperature = sliderTemperature.getValue();
        int threads = provider.supportsThreads() ? (int) sliderThreads.getValue() : 0;

        if (existingConfig != null) {
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

        setResult(RESULT_OK, new Intent());
        finish();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
