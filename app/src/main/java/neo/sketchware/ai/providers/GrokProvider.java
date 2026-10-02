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

/** xAI Grok provider using xAI's OpenAI-compatible Chat Completions API. */
public class GrokProvider implements AiProvider {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final String ENDPOINT = "https://api.x.ai/v1/chat/completions";

    @Override public String getProviderId() { return "grok"; }
    @Override public String getProviderName() { return "xAI Grok"; }
    @Override public boolean requiresCustomEndpoint() { return false; }
    @Override public boolean requiresApiKey() { return true; }

    @Override public void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", TextUtils.isEmpty(config.modelName) ? "grok-4.7" : config.modelName);
                JSONArray messages = new JSONArray();
                JSONObject system = new JSONObject(); system.put("role", "system"); system.put("content", systemPrompt); messages.put(system);
                JSONObject user = new JSONObject(); user.put("role", "user"); user.put("content", userPrompt); messages.put(user);
                body.put("messages", messages);
                body.put("temperature", config.temperature);
                if (config.maxTokens > 0) body.put("max_tokens", config.maxTokens);
                if (config.topP > 0 && config.topP <= 1) body.put("top_p", config.topP);
                Map<String,String> headers = new HashMap<>(); headers.put("Authorization", "Bearer " + config.apiKey);
                String response = AiHttpUtil.post(ENDPOINT, headers, body.toString());
                JSONObject json = new JSONObject(response);
                JSONArray choices = json.optJSONArray("choices");
                if (choices == null || choices.length() == 0) throw new IllegalStateException("Grok returned no choices");
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                String content = message == null ? "" : message.optString("content", "");
                if (TextUtils.isEmpty(content)) throw new IllegalStateException("Grok returned an empty response");
                MAIN_HANDLER.post(() -> callback.onSuccess(content));
            } catch (Exception e) {
                MAIN_HANDLER.post(() -> callback.onFailure(TextUtils.isEmpty(e.getMessage()) ? "Grok request failed" : e.getMessage()));
            }
        });
    }
}
