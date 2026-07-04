package pro.sketchware.analysis.modules.manifest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.hC;
import com.besome.sketch.beans.ProjectFileBean;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class DuplicateActivityCheck implements AnalysisCheck {

    @Override public String id() { return "duplicate_activities"; }
    @Override public String category() { return "manifest"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        hC files = ctx.getFileManager();
        if (files == null) return;

        checkForDuplicates(files.b(), "activity", report);
        checkForDuplicates(files.c(), "custom view", report);
    }

    private void checkForDuplicates(List<ProjectFileBean> beans, String kind, AnalysisReport.Builder report) {
        if (beans == null) return;

        Map<String, Integer> countByFileName = new HashMap<>();
        for (ProjectFileBean bean : beans) {
            if (bean == null || bean.fileName == null) continue;
            countByFileName.merge(bean.fileName, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : countByFileName.entrySet()) {
            if (entry.getValue() < 2) continue;

            report.addIssue(new Issue(
                    "DUP_ACTIVITY",
                    category(),
                    Severity.CRITICAL,
                    "Duplicate " + kind + " identifier \"" + entry.getKey() + "\" appears "
                            + entry.getValue() + " times. This will break builds or cause "
                            + "unpredictable behavior — one must be renamed or removed.",
                    entry.getKey(),
                    null
            ));
        }
    }
}
