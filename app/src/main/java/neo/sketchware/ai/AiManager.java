package neo.sketchware.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import static pro.sketchware.utility.GsonUtils.getGson;

public final class AiManager {
    private static final String PREFS_NAME = "ai_prefs";
    private static final String KEY_CONFIGS = "ai_model_configs";
    private static final String KEY_ACTIVE_ID = "ai_active_config_id";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "neo_sketchware_ai_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private AiManager() {}

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).build();
                kg.init(spec);
                kg.generateKey();
            }
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access AI storage key", e);
        }
    }

    private static String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] bytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + bytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(bytes, 0, combined, iv.length, bytes.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt AI storage value", e);
        }
    }

    private static String decrypt(String storedValue) {
        if (storedValue == null) return null;
        try {
            byte[] combined = Base64.decode(storedValue, Base64.NO_WRAP);
            if (combined.length <= 12) return null;
            byte[] iv = new byte[12];
            byte[] bytes = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, bytes, 0, bytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(bytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<AiModelConfig> getConfigs(Context context) {
        String json = decrypt(getPrefs(context).getString(KEY_CONFIGS, null));
        if (json == null) return new ArrayList<>();
        Type listType = new TypeToken<ArrayList<AiModelConfig>>() {}.getType();
        try {
            List<AiModelConfig> configs = getGson().fromJson(json, listType);
            if (configs == null) return new ArrayList<>();
            List<AiModelConfig> safe = new ArrayList<>();
            for (AiModelConfig config : configs) {
                if (config != null && config.id != null && config.providerId != null) safe.add(config);
            }
            return safe;
        } catch (RuntimeException e) {
            // A damaged preference must not crash the AI screens; retain the raw value for diagnosis.
            return new ArrayList<>();
        }
    }

    private static void saveConfigs(Context context, List<AiModelConfig> configs) {
        getPrefs(context).edit().putString(KEY_CONFIGS, encrypt(getGson().toJson(configs))).apply();
    }

    public static void addConfig(Context context, AiModelConfig config) {
        if (context == null || config == null || config.providerId == null || config.id == null) return;
        List<AiModelConfig> configs = getConfigs(context);
        for (AiModelConfig item : configs) if (config.id.equals(item.id)) return;
        boolean wasEmpty = configs.isEmpty();
        configs.add(config);
        saveConfigs(context, configs);
        if (wasEmpty) setActiveConfigId(context, config.id);
    }

    public static void updateConfig(Context context, AiModelConfig updated) {
        List<AiModelConfig> configs = getConfigs(context);
        if (updated == null || updated.id == null || updated.providerId == null) return;
        for (int i = 0; i < configs.size(); i++) {
            if (updated.id.equals(configs.get(i).id)) {
                configs.set(i, updated);
                saveConfigs(context, configs);
                return;
            }
        }
        configs.add(updated);
        saveConfigs(context, configs);
    }

    public static void removeConfig(Context context, String id) {
        List<AiModelConfig> configs = getConfigs(context);
        configs.removeIf(c -> c != null && id != null && id.equals(c.id));
        saveConfigs(context, configs);
        if (id != null && id.equals(getActiveConfigId(context))) {
            getPrefs(context).edit().remove(KEY_ACTIVE_ID).apply();
            if (!configs.isEmpty()) setActiveConfigId(context, configs.get(0).id);
        }
    }

    public static String getActiveConfigId(Context context) {
        return decrypt(getPrefs(context).getString(KEY_ACTIVE_ID, null));
    }

    public static void setActiveConfigId(Context context, String id) {
        if (id == null) getPrefs(context).edit().remove(KEY_ACTIVE_ID).apply();
        else getPrefs(context).edit().putString(KEY_ACTIVE_ID, encrypt(id)).apply();
    }

    /**
     * Returns the user's active agent, or a configured local agent when the explicit
     * "prefer local" setting is enabled and a usable local configuration exists.
     * The explicitly selected ID remains unchanged, so users can turn this preference off
     * without losing their chosen default.
     */
    public static AiModelConfig getActiveConfig(Context context) {
        if (context == null) return null;
        List<AiModelConfig> configs = getConfigs(context);
        String activeId = getActiveConfigId(context);
        AiModelConfig selected = null;
        for (AiModelConfig c : configs) {
            if (c != null && activeId != null && activeId.equals(c.id)) {
                selected = c;
                break;
            }
        }

        boolean preferLocal = getPrefs(context).getBoolean("prefer_local", false);
        if (preferLocal && (selected == null || !isLocalConfig(selected))) {
            for (AiModelConfig candidate : configs) {
                if (candidate != null && isLocalConfig(candidate) &&
                        candidate.modelName != null && !candidate.modelName.trim().isEmpty()) {
                    return candidate;
                }
            }
        }
        return selected;
    }

    private static boolean isLocalConfig(AiModelConfig config) {
        if (config == null || config.providerId == null) return false;
        AiProvider provider = AiProviderRegistry.get(config.providerId);
        return provider != null && provider.isLocal();
    }

    public static AiProvider getActiveProvider(Context context) {
        AiModelConfig c = getActiveConfig(context);
        return c == null ? null : AiProviderRegistry.get(c.providerId);
    }

    public static void testConfig(Context context, AiModelConfig config, AiResponseCallback callback) {
        if (config == null) { callback.onFailure("No AI model is configured."); return; }
        AiProvider provider = AiProviderRegistry.get(config.providerId);
        if (provider == null) { callback.onFailure("Unknown AI provider: " + config.providerId); return; }
        provider.testConnection(config, callback);
    }

    public static void testActive(Context context, AiResponseCallback callback) {
        testConfig(context, getActiveConfig(context), callback);
    }

    public enum Feature { GENERAL, CHAT, BLOCKS, LOGIC, LAYOUTS, CUSTOM_BLOCKS, ERROR_FIX }

    /** Entry point used by existing AI features that follow the globally selected agent. */
    public static void sendPrompt(Context context, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        sendPrompt(context, systemPrompt, userPrompt, Feature.GENERAL, callback);
    }

    /** Sends through the configured active agent and honours the existing global integration toggle. */
    public static void sendPrompt(Context context, String systemPrompt, String userPrompt,
                                  Feature feature, AiResponseCallback callback) {
        if (callback == null) return;
        if (context == null) {
            callback.onFailure("AI context is unavailable.");
            return;
        }
        if (!getPrefs(context).getBoolean("auto_agent", true)) {
            callback.onFailure("AI integrations are paused. Enable ‘Use active agent for existing AI generation’ in AI Settings.");
            return;
        }
        sendPrompt(context, getActiveConfig(context), systemPrompt, userPrompt, feature, callback);
    }

    /** Backwards-compatible explicit-agent entry point. */
    public static void sendPrompt(Context context, AiModelConfig config, String systemPrompt,
                                  String userPrompt, AiResponseCallback callback) {
        sendPrompt(context, config, systemPrompt, userPrompt, Feature.GENERAL, callback);
    }

    /** Sends through an explicit agent and enforces its per-feature capabilities. */
    public static void sendPrompt(Context context, AiModelConfig config, String systemPrompt,
                                  String userPrompt, Feature feature, AiResponseCallback callback) {
        if (callback == null) return;
        if (context == null) {
            callback.onFailure("AI context is unavailable.");
            return;
        }
        if (config == null) {
            callback.onFailure("No AI agent selected. Add or select an agent from AI Agents.");
            return;
        }
        AiProvider provider = AiProviderRegistry.get(config.providerId);
        if (provider == null) {
            callback.onFailure("Unknown AI provider: " + config.providerId + ". Edit this agent and choose a supported provider.");
            return;
        }
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            callback.onFailure("Enter a message to send.");
            return;
        }
        if (!isFeatureEnabled(config, feature)) {
            callback.onFailure(featureLabel(feature) + " is disabled for this agent. Open Edit Agent → Advanced options and enable it, or select another agent.");
            return;
        }

        String agentPrompt = config.systemPrompt == null ? "" : config.systemPrompt.trim();
        String basePrompt = systemPrompt == null ? "" : systemPrompt.trim();
        String mergedSystem = agentPrompt.isEmpty() ? basePrompt
                : (basePrompt.isEmpty() ? agentPrompt : agentPrompt + "\n\n" + basePrompt);
        String safeSystem = AiPromptOptimizer.optimizeSystem(mergedSystem);
        String safeUser = AiPromptOptimizer.optimizeUser(userPrompt);
        provider.sendRequest(config, safeSystem, safeUser, callback);
    }

    private static boolean isFeatureEnabled(AiModelConfig config, Feature feature) {
        if (feature == null || feature == Feature.GENERAL) return true;
        switch (feature) {
            case CHAT: return config.enableChat;
            case BLOCKS: return config.enableBlocks;
            case LOGIC: return config.enableLogic;
            case LAYOUTS: return config.enableLayouts;
            case CUSTOM_BLOCKS: return config.enableCustomBlocks;
            case ERROR_FIX: return config.enableErrorFix;
            default: return true;
        }
    }

    private static String featureLabel(Feature feature) {
        if (feature == null) return "This feature";
        switch (feature) {
            case CHAT: return "Chat and explanations";
            case BLOCKS: return "Block generation";
            case LOGIC: return "Event logic generation";
            case LAYOUTS: return "Layout generation";
            case CUSTOM_BLOCKS: return "Custom block generation";
            case ERROR_FIX: return "Build error fixing";
            default: return "This feature";
        }
    }

}
