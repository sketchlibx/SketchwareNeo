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
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);

                JSONArray messages = new JSONArray();
                messages.put(userMessage);

                JSONObject body = new JSONObject();
                body.put("model", config.modelName);
                body.put("max_tokens", 4096);
                body.put("system", systemPrompt);
                body.put("messages", messages);

                Map<String, String> headers = new HashMap<>();
                headers.put("x-api-key", config.apiKey);
                headers.put("anthropic-version", "2023-06-01");

                String response = AiHttpUtil.post("https://api.anthropic.com/v1/messages", headers, body.toString());

                JSONObject json = new JSONObject(response);
                String replyText = json.getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text");

                postSuccess(callback, replyText);
            } catch (Exception e) {
                postFailure(callback, e.getMessage() != null ? e.getMessage() : "Claude request failed");
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
