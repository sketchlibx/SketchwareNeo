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

public class ClaudeProvider implements AiProvider {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public String getProviderId() {
        return "claude";
    }

    @Override
    public String getProviderName() {
        return "Anthropic Claude";
    }

    @Override
    public boolean requiresCustomEndpoint() {
        return false;
    }

    @Override
    public void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (config == null) throw new IllegalArgumentException("No Claude agent selected.");
                if (config.apiKey == null || config.apiKey.trim().isEmpty()) throw new IllegalArgumentException("Anthropic API key is missing. Edit the agent and add an API key.");
                if (config.modelName == null || config.modelName.trim().isEmpty()) throw new IllegalArgumentException("Claude model ID is empty. Enter a model ID supported by your API account.");
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);

                JSONArray messages = new JSONArray();
                messages.put(userMessage);

                JSONObject body = new JSONObject();
                body.put("model", config.modelName);
                body.put("max_tokens", Math.max(128, Math.min(config.maxTokens > 0 ? config.maxTokens : 4096, 32768)));
                body.put("system", systemPrompt);
                body.put("messages", messages);
                // Anthropic's Messages API only accepts temperature in [0.0, 1.0], unlike our
                // Settings UI's 0.0-2.0 range - clamp so a value above 1.0 doesn't fail the request.
                body.put("temperature", Math.max(0.0, Math.min(config.temperature, 1.0)));

                Map<String, String> headers = new HashMap<>();
                headers.put("x-api-key", config.apiKey);
                headers.put("anthropic-version", "2023-06-01");

                String response = AiHttpUtil.post("https://api.anthropic.com/v1/messages", headers, body.toString());

                JSONObject json = new JSONObject(response);
                JSONArray contentBlocks = json.optJSONArray("content");
                StringBuilder reply = new StringBuilder();
                if (contentBlocks != null) {
                    for (int i = 0; i < contentBlocks.length(); i++) {
                        JSONObject block = contentBlocks.optJSONObject(i);
                        if (block != null && "text".equals(block.optString("type", "text"))) reply.append(block.optString("text", ""));
                    }
                }
                if (reply.toString().trim().isEmpty()) throw new java.io.IOException("Claude returned no readable text. Check model access and output token settings.");
                postSuccess(callback, reply.toString().trim());
            } catch (Exception e) {
                postFailure(callback, AiHttpUtil.humanizeError(e, "Claude"));
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
