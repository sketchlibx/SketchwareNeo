package neo.sketchware.ai;

public interface AiProvider {
    String getProviderId();
    String getProviderName();
    boolean requiresCustomEndpoint();
    void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback);
}
