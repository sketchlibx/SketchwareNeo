package neo.sketchware.ai.providers;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import neo.sketchware.ai.AiHttpUtil;
import neo.sketchware.ai.AiModelConfig;
import neo.sketchware.ai.AiProvider;
import neo.sketchware.ai.AiResponseCallback;

/**
 * Local/self-hosted AI provider.
 *
 * Supports:
 *  - Ollama's /api/chat endpoint
 *  - OpenAI-compatible local endpoints such as:
 *      http://127.0.0.1:11434/v1/chat/completions
 *      LM Studio / llama.cpp server / other OpenAI-compatible servers
 *
 * This provider intentionally does not bundle a native inference engine. The endpoint can point
 * at an on-device/self-hosted server or another machine reachable by the Android device.
 */
public class LocalProvider implements AiProvider {

    public static final String DEFAULT_ENDPOINT =
            "http://127.0.0.1:11434/v1/chat/completions";

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public String getProviderId() {
        return "local";
    }

    @Override
    public String getProviderName() {
        return "Local / Self-hosted";
    }

    @Override
    public boolean requiresCustomEndpoint() {
        return true;
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public String getDefaultEndpoint() {
        return DEFAULT_ENDPOINT;
    }

    @Override
    public void sendRequest(
            AiModelConfig config,
            String systemPrompt,
            String userPrompt,
            AiResponseCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                String endpoint = normalizeEndpoint(config.customEndpoint);
                if (TextUtils.isEmpty(config.modelName)) {
                    throw new IllegalArgumentException("Local model name is not set");
                }

                String response;
                if (isOllamaEndpoint(endpoint)) {
                    response = requestOllama(endpoint, config, systemPrompt, userPrompt);
                } else {
                    response = requestOpenAiCompatible(endpoint, config, systemPrompt, userPrompt);
                }

                String reply = parseResponse(response, isOllamaEndpoint(endpoint));
                if (TextUtils.isEmpty(reply)) {
                    throw new IllegalStateException("Local model returned an empty response");
                }

                postSuccess(callback, reply);
            } catch (Exception e) {
                String message = e.getMessage();
                postFailure(callback, TextUtils.isEmpty(message)
                        ? "Local AI request failed"
                        : message);
            }
        });
    }

    @Override
    public void testConnection(AiModelConfig config, AiResponseCallback callback) {
        sendRequest(
                config,
                "Connection test. Reply with exactly OK.",
                "OK",
                new AiResponseCallback() {
                    @Override
                    public void onSuccess(String response) {
                        callback.onSuccess("Connection successful");
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        callback.onFailure(errorMessage);
                    }
                }
        );
    }

    private String requestOllama(
            String endpoint,
            AiModelConfig config,
            String systemPrompt,
            String userPrompt
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", config.modelName);
        body.put("stream", false);

        JSONArray messages = new JSONArray();

        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.put(system);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.put(user);

        body.put("messages", messages);

        JSONObject options = new JSONObject();
        options.put("temperature", config.temperature);
        if (config.maxTokens > 0) {
            options.put("num_predict", config.maxTokens);
        }
        body.put("options", options);

        return AiHttpUtil.post(endpoint, new HashMap<>(), body.toString());
    }

    private String requestOpenAiCompatible(
            String endpoint,
            AiModelConfig config,
            String systemPrompt,
            String userPrompt
    ) throws Exception {
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
        body.put("temperature", config.temperature);
        body.put("stream", false);

        if (config.maxTokens > 0) {
            body.put("max_tokens", config.maxTokens);
        }
        if (config.topP > 0.0 && config.topP <= 1.0) {
            body.put("top_p", config.topP);
        }

        Map<String, String> headers = new HashMap<>();
        if (!TextUtils.isEmpty(config.apiKey)) {
            headers.put("Authorization", "Bearer " + config.apiKey);
        }

        return AiHttpUtil.post(endpoint, headers, body.toString());
    }

    private String parseResponse(String response, boolean ollama) throws Exception {
        JSONObject json = new JSONObject(response);

        if (ollama) {
            JSONObject message = json.optJSONObject("message");
            return message != null ? message.optString("content", "") : "";
        }

        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";

        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        return message != null ? message.optString("content", "") : "";
    }

    private String normalizeEndpoint(String endpoint) {
        if (TextUtils.isEmpty(endpoint)) {
            return DEFAULT_ENDPOINT;
        }

        String value = endpoint.trim();

        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.endsWith("/v1")) {
            value += "/chat/completions";
        } else if (!value.endsWith("/chat/completions") && !value.endsWith("/api/chat")) {
            // Ollama base URL is the common local case.
            if (value.endsWith(":11434")) {
                value += "/api/chat";
            }
        }

        return value;
    }

    private boolean isOllamaEndpoint(String endpoint) {
        return endpoint.endsWith("/api/chat");
    }

    private void postSuccess(AiResponseCallback callback, String content) {
        MAIN_HANDLER.post(() -> callback.onSuccess(content));
    }

    private void postFailure(AiResponseCallback callback, String message) {
        MAIN_HANDLER.post(() -> callback.onFailure(message));
    }
}
