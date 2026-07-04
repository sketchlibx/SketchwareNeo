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

public final class DuplicateMavenDependencyCheck implements AnalysisCheck {

    @Override public String id() { return "duplicate_maven_dependencies"; }
    @Override public String category() { return "dependency"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        ArrayList<HashMap<String, Object>> libs = LocalLibrariesUtil.getLocalLibraries(ctx.getScId());
        if (libs == null) return;

        Map<String, Integer> counts = new HashMap<>();
        for (HashMap<String, Object> lib : libs) {
            Object dependency = lib.get("dependency");
            if (!(dependency instanceof String) || ((String) dependency).isEmpty()) continue;
            counts.merge((String) dependency, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < 2) continue;
            report.addIssue(new Issue(
                    "DUP_MAVEN_DEPENDENCY", category(), Severity.ERROR,
                    "Maven dependency \"" + entry.getKey() + "\" is added " + entry.getValue()
                            + " times — this can cause a Gradle dependency conflict.",
                    null, null
            ));
        }
    }
}
