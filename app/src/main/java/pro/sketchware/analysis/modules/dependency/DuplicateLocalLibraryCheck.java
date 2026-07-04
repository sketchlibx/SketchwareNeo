package pro.sketchware.analysis.modules.dependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class DuplicateLocalLibraryCheck implements AnalysisCheck {

    @Override public String id() { return "duplicate_local_libraries"; }
    @Override public String category() { return "dependency"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        ArrayList<HashMap<String, Object>> libs = LocalLibrariesUtil.getLocalLibraries(ctx.getScId());
        if (libs == null) return;

        Map<String, Integer> counts = new HashMap<>();
        for (HashMap<String, Object> lib : libs) {
            Object name = lib.get("name");
            if (!(name instanceof String) || ((String) name).isEmpty()) continue;
            counts.merge((String) name, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < 2) continue;
            report.addIssue(new Issue(
                    "DUP_LOCAL_LIBRARY", category(), Severity.WARNING,
                    "Local library \"" + entry.getKey() + "\" is added to this project " + entry.getValue() + " times.",
                    null, null
            ));
        }
    }
}
