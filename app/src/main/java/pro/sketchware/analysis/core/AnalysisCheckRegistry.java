package pro.sketchware.analysis.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnalysisCheckRegistry {

    private static final Map<String, AnalysisCheck> checks = new ConcurrentHashMap<>();

    private AnalysisCheckRegistry() {}

    public static void register(AnalysisCheck check) {
        checks.put(check.id(), check);
    }

    public static void unregister(String id) {
        checks.remove(id);
    }

    public static Collection<AnalysisCheck> all() {
        return Collections.unmodifiableCollection(checks.values());
    }

    public static List<AnalysisCheck> byCategory(String category) {
        List<AnalysisCheck> result = new ArrayList<>();
        for (AnalysisCheck check : checks.values()) {
            if (check.category().equals(category)) result.add(check);
        }
        return result;
    }

    
    public static void clearForTesting() {
        checks.clear();
    }
}
