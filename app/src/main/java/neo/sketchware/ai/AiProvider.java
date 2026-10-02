package neo.sketchware.ai;

public interface AiProvider {
    String getProviderId();
    String getProviderName();
    boolean requiresCustomEndpoint();
    void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback);

    default boolean supportsTemperature() { return true; }
    default boolean supportsThreads() { return false; }
    default boolean requiresApiKey() { return !isLocal(); }
    default boolean isLocal() { return false; }
    default String getDefaultEndpoint() { return ""; }

    /** Provider test uses a real request unless the implementation overrides it. */
    default void testConnection(AiModelConfig config, AiResponseCallback callback) {
        sendRequest(config,
                "Connection test. Reply with exactly OK.",
                "OK",
                new AiResponseCallback() {
                    @Override public void onSuccess(String response) { callback.onSuccess("Connection successful"); }
                    @Override public void onFailure(String errorMessage) { callback.onFailure(errorMessage); }
                });
    }
}
