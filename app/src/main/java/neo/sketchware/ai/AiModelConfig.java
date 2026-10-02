package neo.sketchware.ai;

import java.io.Serializable;
import java.util.UUID;

/**
 * Persisted AI model/agent configuration. Existing fields are intentionally preserved so older
 * Neo AI configurations remain readable. New fields are Gson-compatible additions.
 */
public class AiModelConfig implements Serializable {
    public String id;
    public String providerId;
    public String displayName;
    public String apiKey;
    public String modelName;
    public String customEndpoint;
    public boolean isActive;
    public double temperature = 0.7;
    public int threads = 0;
    public int maxTokens = 4096;
    public double topP = 0.9;

    // Agent behaviour / capabilities. Defaults intentionally keep all existing AI features enabled.
    public boolean enableChat = true;
    public boolean enableBlocks = true;
    public boolean enableLogic = true;
    public boolean enableLayouts = true;
    public boolean enableCustomBlocks = true;
    public boolean enableErrorFix = true;
    public String systemPrompt = "";

    public AiModelConfig() {
        this.id = UUID.randomUUID().toString();
    }

    public AiModelConfig(String providerId, String displayName, String apiKey,
                         String modelName, String customEndpoint) {
        this();
        this.providerId = providerId;
        this.displayName = displayName;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.customEndpoint = customEndpoint;
    }
}
