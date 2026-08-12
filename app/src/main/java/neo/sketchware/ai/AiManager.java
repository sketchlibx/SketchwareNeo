package neo.sketchware.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static pro.sketchware.utility.GsonUtils.getGson;

public final class AiManager {

    private static final String PREFS_NAME = "ai_secure_prefs";
    private static final String KEY_CONFIGS = "ai_model_configs";
    private static final String KEY_ACTIVE_ID = "ai_active_config_id";

    private static volatile SharedPreferences securePrefs;

    private AiManager() {}

    private static SharedPreferences getPrefs(Context context) {
        if (securePrefs == null) {
            synchronized (AiManager.class) {
                if (securePrefs == null) {
                    try {
                        MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                .build();

                        securePrefs = EncryptedSharedPreferences.create(
                                context.getApplicationContext(),
                                PREFS_NAME,
                                masterKey,
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize secure AI storage", e);
                    }
                }
            }
        }
        return securePrefs;
    }

    public static List<AiModelConfig> getConfigs(Context context) {
        String json = getPrefs(context).getString(KEY_CONFIGS, null);
        if (json == null) return new ArrayList<>();
        Type listType = new TypeToken<ArrayList<AiModelConfig>>() {}.getType();
        List<AiModelConfig> configs = getGson().fromJson(json, listType);
        return configs != null ? configs : new ArrayList<>();
    }

    private static void saveConfigs(Context context, List<AiModelConfig> configs) {
        String json = getGson().toJson(configs);
        getPrefs(context).edit().putString(KEY_CONFIGS, json).apply();
    }

    public static void addConfig(Context context, AiModelConfig config) {
        List<AiModelConfig> configs = getConfigs(context);
        configs.add(config);
        saveConfigs(context, configs);
        if (configs.size() == 1) {
            setActiveConfigId(context, config.id);
        }
    }

    public static void updateConfig(Context context, AiModelConfig updated) {
        List<AiModelConfig> configs = getConfigs(context);
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).id.equals(updated.id)) {
                configs.set(i, updated);
                break;
            }
        }
        saveConfigs(context, configs);
    }

    public static void removeConfig(Context context, String id) {
        List<AiModelConfig> configs = getConfigs(context);
        configs.removeIf(c -> c.id.equals(id));
        saveConfigs(context, configs);

        if (id.equals(getActiveConfigId(context))) {
            getPrefs(context).edit().remove(KEY_ACTIVE_ID).apply();
            if (!configs.isEmpty()) {
                setActiveConfigId(context, configs.get(0).id);
            }
        }
    }

    public static String getActiveConfigId(Context context) {
        return getPrefs(context).getString(KEY_ACTIVE_ID, null);
    }

    public static void setActiveConfigId(Context context, String id) {
        getPrefs(context).edit().putString(KEY_ACTIVE_ID, id).apply();
    }

    public static AiModelConfig getActiveConfig(Context context) {
        String activeId = getActiveConfigId(context);
        if (activeId == null) return null;
        for (AiModelConfig config : getConfigs(context)) {
            if (config.id.equals(activeId)) return config;
        }
        return null;
    }

    public static void sendPrompt(Context context, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        AiModelConfig activeConfig = getActiveConfig(context);
        if (activeConfig == null) {
            callback.onFailure("No active AI model configured. Add one from AI Settings.");
            return;
        }

        AiProvider provider = AiProviderRegistry.get(activeConfig.providerId);
        if (provider == null) {
            callback.onFailure("Unknown AI provider: " + activeConfig.providerId);
            return;
        }

        provider.sendRequest(activeConfig, systemPrompt, userPrompt, callback);
    }
}
