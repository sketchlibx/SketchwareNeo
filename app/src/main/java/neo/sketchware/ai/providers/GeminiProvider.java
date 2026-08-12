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

public class GeminiProvider implements AiProvider {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public String getProviderId() {
        return "gemini";
    }

    @Override
    public String getProviderName() {
        return "Google Gemini";
    }

    @Override
    public boolean requiresCustomEndpoint() {
        return false;
    }

    @Override
    public void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject textPart = new JSONObject();
                textPart.put("text", systemPrompt + "\n\n" + userPrompt);

                JSONArray parts = new JSONArray();
                parts.put(textPart);

                JSONObject content = new JSONObject();
                content.put("parts", parts);

                JSONArray contents = new JSONArray();
                contents.put(content);

                JSONObject body = new JSONObject();
                body.put("contents", contents);

                String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + config.modelName + ":generateContent?key=" + config.apiKey;

                String response = AiHttpUtil.post(url, new HashMap<>(), body.toString());

                JSONObject json = new JSONObject(response);
                String replyText = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");

                postSuccess(callback, replyText);
            } catch (Exception e) {
                postFailure(callback, e.getMessage() != null ? e.getMessage() : "Gemini request failed");
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
