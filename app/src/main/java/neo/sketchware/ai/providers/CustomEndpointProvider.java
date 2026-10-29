package neo.sketchware.ai.providers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import neo.sketchware.ai.AiHttpUtil;
import neo.sketchware.ai.AiModelConfig;
import neo.sketchware.ai.AiProvider;
import neo.sketchware.ai.AiResponseCallback;

public class CustomEndpointProvider implements AiProvider {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public String getProviderId() {
        return "custom";
    }

    @Override
    public String getProviderName() {
        return "Custom (OpenAI-compatible)";
    }

    @Override
    public boolean requiresCustomEndpoint() {
        return true;
    }

    @Override
    public void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (config == null) throw new IllegalArgumentException("No custom endpoint agent selected.");
                if (TextUtils.isEmpty(config.modelName)) throw new IllegalArgumentException("Model ID is empty. Enter the model/deployment name expected by this endpoint.");
                if (TextUtils.isEmpty(config.customEndpoint)) {
                    throw new IllegalArgumentException("Custom endpoint URL is not set");
                }

                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", systemPrompt);

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);

                JSONArray messages = new JSONArray();
                messages.put(systemMessage);
                messages.put(userMessage);

                JSONObject body = new JSONObject();
                body.put("model", config.modelName);
                body.put("messages", messages);
                body.put("temperature", Math.max(0.0, Math.min(2.0, config.temperature)));
                if (config.maxTokens > 0) body.put("max_tokens", Math.max(128, Math.min(config.maxTokens, 32768)));
                if (config.topP > 0.0 && config.topP <= 1.0) body.put("top_p", config.topP);
                body.put("stream", false);

                Map<String, String> headers = new HashMap<>();
                if (!TextUtils.isEmpty(config.apiKey)) {
                    headers.put("Authorization", "Bearer " + config.apiKey);
                }

                String response = AiHttpUtil.post(config.customEndpoint, headers, body.toString());

                postSuccess(callback, AiHttpUtil.extractChatCompletionText(response, "custom endpoint"));
            } catch (Exception e) {
                postFailure(callback, AiHttpUtil.humanizeError(e, "custom endpoint"));
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
