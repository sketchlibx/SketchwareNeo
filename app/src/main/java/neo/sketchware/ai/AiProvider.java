package neo.sketchware.ai;

public interface AiProvider {
    String getProviderId();
    String getProviderName();
    boolean requiresCustomEndpoint();
    void sendRequest(AiModelConfig config, String systemPrompt, String userPrompt, AiResponseCallback callback);

    /** True for every current (cloud API) provider - all of them accept a sampling temperature. */
    default boolean supportsTemperature() {
        return true;
    }

    /**
     * "Threads" is a CPU-inference concept (e.g. llama.cpp-style local execution) - it has no
     * meaning for a remote cloud API, so no current provider supports it. A future local/offline
     * provider (Phase D) would override this to true; until then the Threads control in AI
     * Settings stays hidden so we never send an unsupported field.
     */
    default boolean supportsThreads() {
        return false;
    }
}
