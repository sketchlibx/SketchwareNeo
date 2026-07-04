package pro.sketchware.analysis.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AnalysisReport {

    private final List<Issue> issues;
    private final ProjectScore score;

    private AnalysisReport(List<Issue> issues, ProjectScore score) {
        this.issues = Collections.unmodifiableList(issues);
        this.score = score;
    }
    public List<Issue> getIssues() { return issues; }
    public ProjectScore getScore() { return score; }

    public List<Issue> getIssuesByCategory(String category) {
        List<Issue> result = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.getCategory().equals(category)) result.add(issue);
        }
        return Collections.unmodifiableList(result);
    }

    public boolean hasAnyAtLeast(Severity minimum) {
        for (Issue issue : issues) {
            if (issue.getSeverity().ordinal() >= minimum.ordinal()) return true;
        }
        return false;
    }

    
    public static final class Builder {
        private final List<Issue> issues = new ArrayList<>();
        private final Set<String> categoriesRan = new LinkedHashSet<>();
        private final ScoreWeights weights;

        public Builder(ScoreWeights weights) {
            this.weights = weights;
        }

        public void addIssue(Issue issue) {
            issues.add(issue);
        }

        
        public void markCategoryRan(String category) {
            categoriesRan.add(category);
        }

        public AnalysisReport build() {
            ProjectScore score = ProjectScore.compute(issues, categoriesRan, weights);
            return new AnalysisReport(new ArrayList<>(issues), score);
        }
    }
}
