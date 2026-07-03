package mod.sketchlibx.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SourceMapRegistry {
    // Structure: sc_id -> activityName -> eventName -> List<BlockRecord>
    public static final Map<String, Map<String, Map<String, List<BlockRecord>>>> registry = new HashMap<>();

    public static class BlockRecord {
        public String blockId;
        public String generatedCode;

        public BlockRecord(String blockId, String generatedCode) {
            this.blockId = blockId;
            this.generatedCode = generatedCode;
        }
    }

    public static void startEvent(String sc_id, String activityName, String eventName) {
        if (sc_id == null || activityName == null || eventName == null) return;
        registry.putIfAbsent(sc_id, new HashMap<>());
        registry.get(sc_id).putIfAbsent(activityName, new HashMap<>());
        
        // Clear previous compilation data for this specific event
        registry.get(sc_id).get(activityName).put(eventName, new ArrayList<>());
    }

    public static void recordBlock(String sc_id, String activityName, String eventName, String blockId, String code) {
        if (sc_id == null || activityName == null || eventName == null || blockId == null || code == null || code.trim().isEmpty()) return;
        
        if (registry.containsKey(sc_id) && registry.get(sc_id).containsKey(activityName) && registry.get(sc_id).get(activityName).containsKey(eventName)) {
            registry.get(sc_id).get(activityName).get(eventName).add(new BlockRecord(blockId, code.trim()));
        }
    }

    public static List<BlockRecord> getEventMap(String sc_id, String activityName, String eventName) {
        try {
            return registry.get(sc_id).get(activityName).get(eventName);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
