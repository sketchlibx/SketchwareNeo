package pro.sketchware.analysis.autofix;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoFixRegistry {

    private static final Map<String, FixAction> fixes = new ConcurrentHashMap<>();

    private AutoFixRegistry() {}

    public static void register(String fixId, FixAction action) {
        fixes.put(fixId, action);
    }

    public static FixAction get(String fixId) {
        return fixes.get(fixId);
    }

    public static void clearForTesting() {
        fixes.clear();
    }
}
