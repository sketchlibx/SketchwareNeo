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
    /** Sampling temperature, 0.0-2.0. Sent for every current provider (all support it). */
    public double temperature = 0.7;
    /** CPU thread count for local inference. 0 = unset. Only ever read/sent when the
     *  active provider's supportsThreads() is true - no current provider qualifies. */
    public int threads = 0;

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
