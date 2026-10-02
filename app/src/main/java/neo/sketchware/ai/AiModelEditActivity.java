package neo.sketchware.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityAiModelEditBinding;

public class AiModelEditActivity extends BaseAppCompatActivity {
    public static final String EXTRA_CONFIG = "config";

    private ActivityAiModelEditBinding binding;

    private AiModelConfig existingConfig;
    private List<String> providerIds;
    private Map<String, AiProvider> providers;
    private int cpuCores;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiModelEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        if (getIntent().getSerializableExtra(EXTRA_CONFIG) instanceof AiModelConfig) {
            existingConfig = (AiModelConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
        } else {
            existingConfig = null;
        }

        boolean edit = existingConfig != null;
        binding.topAppBar.setTitle(edit ? "Edit AI Agent" : "Add AI Agent");
        binding.topAppBar.setNavigationOnClickListener(v -> finish());

        cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        binding.sliderThreads.setValueTo(cpuCores);
        providers = AiProviderRegistry.getAll();
        providerIds = new ArrayList<>(providers.keySet());
        
        List<String> names = new ArrayList<>();
        for (String id : providerIds) {
            names.add(providers.get(id).getProviderName());
        }
        
        binding.dropdownProvider.setSimpleItems(names.toArray(new String[0]));
        binding.dropdownProvider.setOnItemClickListener((p, v, position, id) -> applyProviderCapabilities(providerIds.get(position), true));

        binding.sliderTemperature.addOnChangeListener((s, value, fromUser) ->
                binding.textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", value)));
        binding.sliderThreads.addOnChangeListener((s, value, fromUser) -> 
                binding.textThreadsValue.setText(String.valueOf((int) value)));

        int initial = 0;
        if (edit) {
            binding.editDisplayName.setText(existingConfig.displayName);
            binding.editModelName.setText(existingConfig.modelName);
            binding.editApiKey.setText(existingConfig.apiKey);
            binding.editCustomEndpoint.setText(existingConfig.customEndpoint);
            binding.editMaxTokens.setText(String.valueOf(existingConfig.maxTokens));
            binding.editSystemPrompt.setText(existingConfig.systemPrompt);
            binding.checkChat.setChecked(existingConfig.enableChat);
            binding.checkBlocks.setChecked(existingConfig.enableBlocks);
            binding.checkLogic.setChecked(existingConfig.enableLogic);
            binding.checkLayouts.setChecked(existingConfig.enableLayouts);
            binding.checkCustomBlocks.setChecked(existingConfig.enableCustomBlocks);
            binding.checkErrorFix.setChecked(existingConfig.enableErrorFix);
            int found = providerIds.indexOf(existingConfig.providerId);
            if (found >= 0) {
                initial = found;
            }
        } else {
            binding.editMaxTokens.setText("4096");
        }

        if (!providerIds.isEmpty()) {
            binding.dropdownProvider.setText(names.get(initial), false);
            applyProviderCapabilities(providerIds.get(initial), false);
        }
        
        binding.sliderTemperature.setValue(clamp((float) (edit ? existingConfig.temperature : 0.7), 0f, 2f));
        binding.textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", binding.sliderTemperature.getValue()));
        
        int initialThreads = edit && existingConfig.threads > 0 ? existingConfig.threads : Math.min(4, cpuCores);
        binding.sliderThreads.setValue(clamp(initialThreads, 1, cpuCores));
        binding.textThreadsValue.setText(String.valueOf((int) binding.sliderThreads.getValue()));
        
        binding.buttonSave.setOnClickListener(v -> save());
        binding.buttonTestConnection.setOnClickListener(v -> testConnection());
        
        binding.buttonModeLocal.setOnClickListener(v -> selectProviderType(true));
        binding.buttonModeCloud.setOnClickListener(v -> selectProviderType(false));
    }

    private void applyProviderCapabilities(String providerId, boolean userSelected) {
        AiProvider provider = providers.get(providerId);
        if (provider == null) return;
        
        boolean endpoint = provider.requiresCustomEndpoint();
        binding.layoutCustomEndpoint.setVisibility(endpoint ? View.VISIBLE : View.GONE);
        binding.textEndpointHint.setVisibility(provider.isLocal() ? View.VISIBLE : View.GONE);
        binding.layoutApiKey.setVisibility(provider.requiresApiKey() ? View.VISIBLE : View.GONE);
        binding.sliderTemperature.setEnabled(provider.supportsTemperature());
        binding.layoutThreads.setVisibility(provider.supportsThreads() ? View.VISIBLE : View.GONE);
        
        if (endpoint && TextUtils.isEmpty(String.valueOf(binding.editCustomEndpoint.getText()))) {
            String def = provider.getDefaultEndpoint();
            if (!TextUtils.isEmpty(def)) {
                binding.editCustomEndpoint.setText(def);
                binding.editCustomEndpoint.setSelection(def.length());
            }
        }
        
        if (provider.isLocal() && userSelected && TextUtils.isEmpty(String.valueOf(binding.editDisplayName.getText()))) {
            binding.editDisplayName.setText("Local AI");
        }
    }

    private void selectProviderType(boolean local) {
        for (int i = 0; i < providerIds.size(); i++) {
            AiProvider p = providers.get(providerIds.get(i));
            if (p != null && p.isLocal() == local) {
                binding.dropdownProvider.setText(p.getProviderName(), false);
                applyProviderCapabilities(providerIds.get(i), true);
                return;
            }
        }
        Toast.makeText(this, local ? "Local provider is unavailable" : "Cloud providers are unavailable", Toast.LENGTH_SHORT).show();
    }

    private boolean validateFields(boolean allowBlankName) {
        binding.layoutProvider.setError(null);
        binding.layoutDisplayName.setError(null);
        binding.layoutModelName.setError(null);
        binding.layoutApiKey.setError(null);
        binding.layoutCustomEndpoint.setError(null);
        binding.layoutMaxTokens.setError(null);
        
        String providerName = String.valueOf(binding.dropdownProvider.getText()).trim();
        int index = -1;
        for (int i = 0; i < providerIds.size(); i++) {
            AiProvider p = providers.get(providerIds.get(i));
            if (p != null && p.getProviderName().equals(providerName)) {
                index = i;
                break;
            }
        }
        
        if (index < 0) {
            binding.layoutProvider.setError("Choose a provider");
            return false;
        }
        
        String name = String.valueOf(binding.editDisplayName.getText()).trim();
        String model = String.valueOf(binding.editModelName.getText()).trim();
        String key = String.valueOf(binding.editApiKey.getText()).trim();
        String endpoint = String.valueOf(binding.editCustomEndpoint.getText()).trim();
        boolean ok = true;
        
        if (TextUtils.isEmpty(name) && !allowBlankName) {
            binding.layoutDisplayName.setError("Required");
            ok = false;
        }
        
        if (TextUtils.isEmpty(model)) {
            binding.layoutModelName.setError("Required");
            ok = false;
        }
        
        AiProvider p = providers.get(providerIds.get(index));
        if (p != null && p.requiresApiKey() && TextUtils.isEmpty(key)) {
            binding.layoutApiKey.setError("Required");
            ok = false;
        }
        
        if (p != null && p.requiresCustomEndpoint() && TextUtils.isEmpty(endpoint)) {
            binding.layoutCustomEndpoint.setError("Required");
            ok = false;
        }
        
        try {
            int mt = Integer.parseInt(String.valueOf(binding.editMaxTokens.getText()).trim());
            if (mt < 128 || mt > 32768) {
                binding.layoutMaxTokens.setError("Use 128–32768");
                ok = false;
            }
        } catch (Exception e) {
            binding.layoutMaxTokens.setError("Enter a valid number");
            ok = false;
        }
        
        return ok;
    }

    private void save() {
        if (!validateFields(false)) return;
        
        String providerName = String.valueOf(binding.dropdownProvider.getText()).trim();
        String providerId = null;
        for (String id : providerIds) {
            AiProvider p = providers.get(id);
            if (p != null && p.getProviderName().equals(providerName)) {
                providerId = id;
                break;
            }
        }
        
        int maxTokens = Integer.parseInt(String.valueOf(binding.editMaxTokens.getText()).trim());
        String endpoint = String.valueOf(binding.editCustomEndpoint.getText()).trim();
        
        if (existingConfig == null) {
            existingConfig = new AiModelConfig(
                    providerId,
                    String.valueOf(binding.editDisplayName.getText()).trim(),
                    String.valueOf(binding.editApiKey.getText()).trim(),
                    String.valueOf(binding.editModelName.getText()).trim(),
                    endpoint
            );
        } else {
            existingConfig.providerId = providerId;
            existingConfig.displayName = String.valueOf(binding.editDisplayName.getText()).trim();
            existingConfig.modelName = String.valueOf(binding.editModelName.getText()).trim();
            existingConfig.apiKey = String.valueOf(binding.editApiKey.getText()).trim();
            existingConfig.customEndpoint = endpoint;
        }
        
        AiProvider p = providers.get(providerId);
        existingConfig.temperature = binding.sliderTemperature.getValue();
        existingConfig.threads = p != null && p.supportsThreads() ? (int) binding.sliderThreads.getValue() : 0;
        existingConfig.maxTokens = maxTokens;
        existingConfig.topP = 0.9;
        existingConfig.systemPrompt = String.valueOf(binding.editSystemPrompt.getText()).trim();
        existingConfig.enableChat = binding.checkChat.isChecked();
        existingConfig.enableBlocks = binding.checkBlocks.isChecked();
        existingConfig.enableLogic = binding.checkLogic.isChecked();
        existingConfig.enableLayouts = binding.checkLayouts.isChecked();
        existingConfig.enableCustomBlocks = binding.checkCustomBlocks.isChecked();
        existingConfig.enableErrorFix = binding.checkErrorFix.isChecked();
        
        if (AiManager.getConfigs(this).stream().anyMatch(c -> c.id.equals(existingConfig.id))) {
            AiManager.updateConfig(this, existingConfig);
        } else {
            AiManager.addConfig(this, existingConfig);
        }
        
        setResult(RESULT_OK, new Intent());
        finish();
    }

    private void testConnection() {
        if (!validateFields(true)) return;
        
        String providerName = String.valueOf(binding.dropdownProvider.getText()).trim();
        String id = null;
        for (String p : providerIds) {
            AiProvider x = providers.get(p);
            if (x != null && x.getProviderName().equals(providerName)) {
                id = p;
                break;
            }
        }
        
        if (id == null) return;
        
        AiModelConfig c = new AiModelConfig(
                id,
                "Connection test",
                String.valueOf(binding.editApiKey.getText()).trim(),
                String.valueOf(binding.editModelName.getText()).trim(),
                String.valueOf(binding.editCustomEndpoint.getText()).trim()
        );
        c.temperature = binding.sliderTemperature.getValue();
        c.maxTokens = 128;
        
        binding.buttonTestConnection.setEnabled(false);
        binding.buttonSave.setEnabled(false);
        binding.buttonTestConnection.setText("Testing…");
        
        AiManager.testConfig(this, c, new AiResponseCallback() {
            @Override
            public void onSuccess(String s) {
                runOnUiThread(() -> {
                    binding.buttonTestConnection.setEnabled(true);
                    binding.buttonSave.setEnabled(true);
                    binding.buttonTestConnection.setText("Test connection");
                    Toast.makeText(AiModelEditActivity.this, "Connection successful", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String e) {
                runOnUiThread(() -> {
                    binding.buttonTestConnection.setEnabled(true);
                    binding.buttonSave.setEnabled(true);
                    binding.buttonTestConnection.setText("Test connection");
                    new MaterialAlertDialogBuilder(AiModelEditActivity.this)
                            .setTitle("Connection failed")
                            .setMessage(e)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
