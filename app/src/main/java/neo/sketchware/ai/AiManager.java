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
        List<AiModelConfig> configs = getGson().fromJson(json, listType);
        return configs != null ? configs : new ArrayList<>();
    }

    private static void saveConfigs(Context context, List<AiModelConfig> configs) {
        getPrefs(context).edit().putString(KEY_CONFIGS, encrypt(getGson().toJson(configs))).apply();
    }

    public static void addConfig(Context context, AiModelConfig config) {
        List<AiModelConfig> configs = getConfigs(context);
        configs.add(config);
        saveConfigs(context, configs);
        if (configs.size() == 1) setActiveConfigId(context, config.id);
    }

    public static void updateConfig(Context context, AiModelConfig updated) {
        List<AiModelConfig> configs = getConfigs(context);
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).id.equals(updated.id)) { configs.set(i, updated); break; }
        }
        saveConfigs(context, configs);
    }

    public static void removeConfig(Context context, String id) {
        List<AiModelConfig> configs = getConfigs(context);
        configs.removeIf(c -> c.id.equals(id));
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

    public static AiModelConfig getActiveConfig(Context context) {
        String activeId = getActiveConfigId(context);
        if (activeId == null) return null;
        for (AiModelConfig c : getConfigs(context)) if (activeId.equals(c.id)) return c;
        return null;
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

    public static void sendPrompt(Context context, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        AiModelConfig active = getActiveConfig(context);
        if (active == null) { callback.onFailure("No active AI model configured. Add one from AI Agents."); return; }
        AiProvider provider = AiProviderRegistry.get(active.providerId);
        if (provider == null) { callback.onFailure("Unknown AI provider: " + active.providerId); return; }
        String agentPrompt = active.systemPrompt == null ? "" : active.systemPrompt.trim();
        String mergedSystem = agentPrompt.isEmpty() ? systemPrompt : agentPrompt + "\n\n" + systemPrompt;
        provider.sendRequest(active, mergedSystem, userPrompt, callback);
    }
}
