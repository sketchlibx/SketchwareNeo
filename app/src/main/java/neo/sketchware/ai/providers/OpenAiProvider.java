package neo.sketchware.ai.providers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;

import neo.sketchware.ai.AiHttpUtil;
import neo.sketchware.ai.AiModelConfig;
import neo.sketchware.ai.AiProvider;
import neo.sketchware.ai.AiResponseCallback;

public class OpenAiProvider implements AiProvider {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public String getProviderId() {
        return "openai";
    }

    @Override
    public String getProviderName() {
        return "OpenAI";
    }

    @Override
    public boolean requiresCustomEndpoint() {
        return false;
    }

    @Override
    public void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (config == null) throw new IllegalArgumentException("No OpenAI agent selected.");
                if (config.apiKey == null || config.apiKey.trim().isEmpty()) throw new IllegalArgumentException("OpenAI API key is missing. Edit the agent and add an API key.");
                if (config.modelName == null || config.modelName.trim().isEmpty()) throw new IllegalArgumentException("OpenAI model ID is empty. Enter a model ID supported by your API account.");

                String modelId = config.modelName.trim().toLowerCase(java.util.Locale.ROOT);
                boolean responsesApi = modelId.startsWith("o1") || modelId.startsWith("o3") || modelId.startsWith("o4")
                        || modelId.startsWith("gpt-5") || modelId.startsWith("gpt-6");
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + config.apiKey.trim());

                String response;
                String result;
                if (responsesApi) {
                    // The Responses API is the more compatible route for modern reasoning/GPT-5+ models.
                    JSONObject body = new JSONObject();
                    body.put("model", config.modelName.trim());
                    body.put("instructions", systemPrompt == null ? "" : systemPrompt);
                    body.put("input", userPrompt == null ? "" : userPrompt);
                    if (config.maxTokens > 0) body.put("max_output_tokens", Math.max(128, Math.min(config.maxTokens, 32768)));
                    body.put("store", false);
                    body.put("stream", false);
                    response = AiHttpUtil.post("https://api.openai.com/v1/responses", headers, body.toString());
                    result = AiHttpUtil.extractResponsesText(response, "OpenAI");
                } else {
                    JSONObject systemMessage = new JSONObject();
                    systemMessage.put("role", "system");
                    systemMessage.put("content", systemPrompt == null ? "" : systemPrompt);

                    JSONObject userMessage = new JSONObject();
                    userMessage.put("role", "user");
                    userMessage.put("content", userPrompt == null ? "" : userPrompt);

                    JSONArray messages = new JSONArray();
                    messages.put(systemMessage);
                    messages.put(userMessage);

                    JSONObject body = new JSONObject();
                    body.put("model", config.modelName.trim());
                    body.put("messages", messages);
                    body.put("temperature", Math.max(0.0, Math.min(2.0, config.temperature)));
                    if (config.maxTokens > 0) body.put("max_completion_tokens", Math.max(128, Math.min(config.maxTokens, 32768)));
                    body.put("stream", false);
                    response = AiHttpUtil.post("https://api.openai.com/v1/chat/completions", headers, body.toString());
                    result = AiHttpUtil.extractChatCompletionText(response, "OpenAI");
                }
                postSuccess(callback, result);
            } catch (Exception e) {
                postFailure(callback, AiHttpUtil.humanizeError(e, "OpenAI"));
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
