package neo.sketchware.ai.providers;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import neo.sketchware.ai.AiHttpUtil;
import neo.sketchware.ai.AiModelConfig;
import neo.sketchware.ai.AiProvider;
import neo.sketchware.ai.AiResponseCallback;

/** Google Gemini GenerateContent API. System instructions are sent in their own field. */
public class GeminiProvider implements AiProvider {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override public String getProviderId() { return "gemini"; }
    @Override public String getProviderName() { return "Google Gemini"; }
    @Override public boolean requiresCustomEndpoint() { return false; }

    @Override
    public void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (config == null || TextUtils.isEmpty(config.apiKey)) throw new IllegalArgumentException("Gemini API key is missing. Edit the agent and add your Google AI Studio key.");
                if (TextUtils.isEmpty(config.modelName)) throw new IllegalArgumentException("Gemini model ID is empty. Enter a model ID supported by your API key.");

                JSONObject userPart = new JSONObject();
                userPart.put("text", userPrompt == null ? "" : userPrompt);
                JSONArray userParts = new JSONArray();
                userParts.put(userPart);
                JSONObject userContent = new JSONObject();
                userContent.put("role", "user");
                userContent.put("parts", userParts);
                JSONArray contents = new JSONArray();
                contents.put(userContent);

                JSONObject body = new JSONObject();
                body.put("contents", contents);

                if (!TextUtils.isEmpty(systemPrompt)) {
                    JSONObject systemPart = new JSONObject();
                    systemPart.put("text", systemPrompt);
                    JSONArray systemParts = new JSONArray();
                    systemParts.put(systemPart);
                    JSONObject systemInstruction = new JSONObject();
                    systemInstruction.put("parts", systemParts);
                    body.put("systemInstruction", systemInstruction);
                }

                JSONObject generationConfig = new JSONObject();
                generationConfig.put("temperature", Math.max(0.0, Math.min(2.0, config.temperature)));
                if (config.maxTokens > 0) generationConfig.put("maxOutputTokens", Math.max(128, Math.min(config.maxTokens, 32768)));
                if (config.topP > 0.0 && config.topP <= 1.0) generationConfig.put("topP", config.topP);
                body.put("generationConfig", generationConfig);

                String modelName = config.modelName.trim();
                if (modelName.startsWith("models/")) modelName = modelName.substring("models/".length());
                String model = AiHttpUtil.encodeUrlComponent(modelName);
                String key = AiHttpUtil.encodeUrlComponent(config.apiKey.trim());
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
                String response = AiHttpUtil.post(url, new HashMap<>(), body.toString());
                postSuccess(callback, AiHttpUtil.extractGeminiText(response));
            } catch (Exception e) {
                postFailure(callback, AiHttpUtil.humanizeError(e, "Gemini"));
            }
        });
    }

    private void postSuccess(AiResponseCallback callback, String content) {
        MAIN_HANDLER.post(() -> callback.onSuccess(content));
    }

    private void postFailure(AiResponseCallback callback, String message) {
        MAIN_HANDLER.post(() -> callback.onFailure(message));
    }
}
