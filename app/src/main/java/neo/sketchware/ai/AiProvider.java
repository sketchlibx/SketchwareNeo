package neo.sketchware.ai;

public interface AiProvider {
    String getProviderId();
    String getProviderName();
    boolean requiresCustomEndpoint();
    void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback);

    /** True when the provider accepts a sampling temperature. */
    default boolean supportsTemperature() {
        return true;
    }

    /** True when CPU thread count is meaningful for the provider's inference runtime. */
    default boolean supportsThreads() {
        return false;
    }

    /** Whether the provider needs an API key. Local/self-hosted providers normally do not. */
    default boolean requiresApiKey() {
        return !isLocal();
    }

    /** True for a local/self-hosted endpoint. */
    default boolean isLocal() {
        return false;
    }

    /** Optional default endpoint shown by the model editor. */
    default String getDefaultEndpoint() {
        return "";
    }

    /**
     * Performs a small real request against the configured provider. Providers that need a
     * special health-check can override this, while the default keeps old providers compatible.
     */
    default void testConnection(AiModelConfig config, AiResponseCallback callback) {
        sendRequest(
                config,
                "You are testing an AI connection. Reply with exactly: OK",
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
}
