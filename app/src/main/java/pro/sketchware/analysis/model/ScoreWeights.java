package pro.sketchware.analysis.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ScoreWeights {

    private final Map<String, Double> weightByCategory;
    private final double defaultWeight;

    private ScoreWeights(Map<String, Double> weightByCategory, double defaultWeight) {
        this.weightByCategory = weightByCategory;
        this.defaultWeight = defaultWeight;
    }

    public double weightFor(String category) {
        return weightByCategory.getOrDefault(category, defaultWeight);
    }

    public static ScoreWeights defaults() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("resource", 1.0);
        w.put("manifest", 1.2);
        w.put("dependency", 1.0);
        w.put("native", 0.8);
        w.put("build", 1.5);
        w.put("security", 1.3);
        return new ScoreWeights(w,  1.0);
    }

    public static ScoreWeights of(Map<String, Double> customWeights, double defaultWeight) {
        return new ScoreWeights(new LinkedHashMap<>(customWeights), defaultWeight);
    }
}
