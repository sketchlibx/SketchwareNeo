package neo.sketchware.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import neo.sketchware.ai.providers.ClaudeProvider;
import neo.sketchware.ai.providers.CustomEndpointProvider;
import neo.sketchware.ai.providers.DeepSeekProvider;
import neo.sketchware.ai.providers.GeminiProvider;
import neo.sketchware.ai.providers.NvidiaProvider;
import neo.sketchware.ai.providers.OpenAiProvider;

public final class AiProviderRegistry {

    private static final Map<String, AiProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        register(new OpenAiProvider());
        register(new GeminiProvider());
        register(new ClaudeProvider());
        register(new NvidiaProvider());
        register(new DeepSeekProvider());
        register(new CustomEndpointProvider());
    }

    private AiProviderRegistry() {}

    private static void register(AiProvider provider) {
        PROVIDERS.put(provider.getProviderId(), provider);
    }

    public static AiProvider get(String providerId) {
        return PROVIDERS.get(providerId);
    }

    public static Map<String, AiProvider> getAll() {
        return PROVIDERS;
    }
}
