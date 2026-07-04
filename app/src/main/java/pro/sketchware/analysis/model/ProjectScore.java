package pro.sketchware.analysis.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectScore {

    
    private static final Map<Severity, Double> DEDUCTION = Map.of(
            Severity.INFO, 1.0,
            Severity.WARNING, 3.0,
            Severity.ERROR, 8.0,
            Severity.CRITICAL, 20.0
    );

    private final int overallPercent;
    private final Map<String, Integer> percentByCategory;

    private ProjectScore(int overallPercent, Map<String, Integer> percentByCategory) {
        this.overallPercent = overallPercent;
        this.percentByCategory = percentByCategory;
    }
    public int getOverallPercent() { return overallPercent; }
    public Map<String, Integer> getPercentByCategory() { return percentByCategory; }

    
    public static ProjectScore compute(List<Issue> issues, Set<String> categoriesRan, ScoreWeights weights) {
        Map<String, Double> rawByCategory = new LinkedHashMap<>();
        for (String category : categoriesRan) {
            rawByCategory.put(category, 0.0);
        }
        for (Issue issue : issues) {
            double deduction = DEDUCTION.getOrDefault(issue.getSeverity(), 5.0);
            rawByCategory.merge(issue.getCategory(), deduction, Double::sum);
        }

        Map<String, Integer> percentByCategory = new LinkedHashMap<>();
        double weightedSum = 0;
        double weightTotal = 0;
        for (Map.Entry<String, Double> entry : rawByCategory.entrySet()) {
            String category = entry.getKey();
            int categoryPercent = clampPercent(100 - entry.getValue());
            percentByCategory.put(category, categoryPercent);

            double weight = weights.weightFor(category);
            weightedSum += categoryPercent * weight;
            weightTotal += weight;
        }

        int overall = weightTotal == 0 ? 100 : clampPercent(weightedSum / weightTotal);
        return new ProjectScore(overall, percentByCategory);
    }

    private static int clampPercent(double value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }
}
