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
