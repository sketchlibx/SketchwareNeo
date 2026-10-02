package neo.sketchware.ai;

import java.io.Serializable;
import java.util.UUID;

public class AiModelConfig implements Serializable {
    public String id;
    public String providerId;
    public String displayName;
    public String apiKey;
    public String modelName;
    public String customEndpoint;
    public boolean isActive;

    /** Sampling temperature, 0.0-2.0 where supported by the provider. */
    public double temperature = 0.7;

    /** CPU thread count for native/local runtimes. 0 = provider default. */
    public int threads = 0;

    /** Preferred maximum generated tokens. Existing providers may ignore this safely. */
    public int maxTokens = 4096;

    /** Nucleus sampling value for providers that support it. */
    public double topP = 1.0;

    public AiModelConfig() {
        this.id = UUID.randomUUID().toString();
    }

    public AiModelConfig(String providerId, String displayName, String apiKey, String modelName, String customEndpoint) {
        this();
        this.providerId = providerId;
        this.displayName = displayName;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.customEndpoint = customEndpoint;
    }
}
