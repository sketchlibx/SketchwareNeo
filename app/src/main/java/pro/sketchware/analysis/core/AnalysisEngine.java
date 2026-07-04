package pro.sketchware.analysis.core;

import java.io.File;
import java.util.Collection;
import java.util.List;

import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.ScoreWeights;

public final class AnalysisEngine {

    private final String scId;
    private final ScoreWeights weights;
    private final SourceIndexCache cache = new SourceIndexCache();

    public AnalysisEngine(String scId) {
        this(scId, ScoreWeights.defaults());
    }

    public AnalysisEngine(String scId, ScoreWeights weights) {
        this.scId = scId;
        this.weights = weights;
    }

    
    public AnalysisReport run(List<File> sourceRoots) {
        return run(sourceRoots, (String[]) null);
    }

    
    public AnalysisReport run(List<File> sourceRoots, String... categories) {
        ProjectContext ctx = ProjectContext.of(scId);
        SourceIndex index = SourceIndex.build(ctx, cache, sourceRoots);

        AnalysisReport.Builder report = new AnalysisReport.Builder(weights);

        Collection<AnalysisCheck> toRun;
        if (categories == null || categories.length == 0) {
            toRun = AnalysisCheckRegistry.all();
        } else {
            toRun = new java.util.ArrayList<>();
            for (String category : categories) {
                toRun.addAll(AnalysisCheckRegistry.byCategory(category));
            }
        }

        for (AnalysisCheck check : toRun) {
            check.run(ctx, index, report);
            report.markCategoryRan(check.category());
        }

        return report.build();
    }
}
