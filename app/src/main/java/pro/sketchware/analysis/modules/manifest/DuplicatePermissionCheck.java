package pro.sketchware.analysis.modules.manifest;

import java.util.ArrayList;
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

public final class DuplicatePermissionCheck implements AnalysisCheck {

    @Override public String id() { return "duplicate_permissions"; }
    @Override public String category() { return "manifest"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        FileResConfig frc = new FileResConfig(ctx.getScId());
        List<String> permissions = frc.getPermissionList();
        if (permissions == null) return;

        Map<String, Integer> counts = new HashMap<>();
        for (String permission : permissions) {
            if (permission == null || permission.isEmpty()) continue;
            counts.merge(permission, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < 2) continue;
            report.addIssue(new Issue(
                    "DUP_PERMISSION",
                    category(),
                    Severity.WARNING,
                    "Permission \"" + entry.getKey() + "\" is listed " + entry.getValue()
                            + " times. Remove the duplicate entries.",
                    null,
                    null
            ));
        }
    }
}
