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

public class DeepSeekProvider implements AiProvider {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public String getProviderId() {
        return "deepseek";
    }

    @Override
    public String getProviderName() {
        return "DeepSeek";
    }

    @Override
    public boolean requiresCustomEndpoint() {
        return false;
    }

    @Override
    public void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        EXECUTOR.execute(() -> {
            try {
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
                body.put("stream", false);

                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + config.apiKey);

                String response = AiHttpUtil.post("https://api.deepseek.com/chat/completions", headers, body.toString());

                JSONObject json = new JSONObject(response);
                String content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                postSuccess(callback, content);
            } catch (Exception e) {
                postFailure(callback, e.getMessage() != null ? e.getMessage() : "DeepSeek request failed");
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
