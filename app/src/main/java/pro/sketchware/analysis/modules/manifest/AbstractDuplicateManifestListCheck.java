package pro.sketchware.analysis.modules.manifest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;
import pro.sketchware.utility.FileResConfig;

public abstract class AbstractDuplicateManifestListCheck implements AnalysisCheck {

    @Override public final String category() { return "manifest"; }

    
    protected abstract String entryKind();

    protected abstract List<String> readList(FileResConfig frc);

    protected abstract String issueId();

    @Override
    public final void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        FileResConfig frc = new FileResConfig(ctx.getScId());
        List<String> entries = readList(frc);
        if (entries == null) return;

        Map<String, Integer> counts = new HashMap<>();
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            counts.merge(entry, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < 2) continue;
            report.addIssue(new Issue(
                    issueId(),
                    category(),
                    Severity.WARNING,
                    "Manifest " + entryKind() + " \"" + entry.getKey() + "\" is listed "
                            + entry.getValue() + " times. Remove the duplicate entries.",
                    null,
                    null
            ));
        }
    }
}
