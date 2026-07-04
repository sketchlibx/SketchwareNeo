package pro.sketchware.analysis.modules.dependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import a.a.a.Jp;
import pro.sketchware.util.library.BuiltInLibraryManager;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class DuplicateBuiltInLibraryCheck implements AnalysisCheck {

    @Override public String id() { return "duplicate_builtin_libraries"; }
    @Override public String category() { return "dependency"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        BuiltInLibraryManager manager = new BuiltInLibraryManager(ctx.getScId());
        ArrayList<Jp> libraries = manager.getLibraries();
        if (libraries == null) return;

        Map<String, Integer> counts = new HashMap<>();
        for (Jp lib : libraries) {
            if (lib == null || lib.getName() == null) continue;
            counts.merge(lib.getName(), 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < 2) continue;
            report.addIssue(new Issue(
                    "DUP_BUILTIN_LIBRARY", category(), Severity.WARNING,
                    "Built-in library \"" + entry.getKey() + "\" is listed " + entry.getValue() + " times.",
                    null, null
            ));
        }
    }
}
