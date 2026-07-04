package pro.sketchware.analysis.modules.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.kC;
import com.besome.sketch.beans.ProjectResourceBean;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class DuplicateResourceCheck implements AnalysisCheck {

    @Override public String id() { return "duplicate_resources"; }
    @Override public String category() { return "resource"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        kC resources = ctx.getResourceManager();
        if (resources == null) return;

        List<ProjectResourceBean> all = new ArrayList<>();
        if (resources.b != null) all.addAll(resources.b);
        if (resources.c != null) all.addAll(resources.c);
        if (resources.d != null) all.addAll(resources.d);

        Map<String, List<ProjectResourceBean>> byName = new HashMap<>();
        for (ProjectResourceBean bean : all) {
            if (bean == null || bean.resName == null) continue;
            byName.computeIfAbsent(bean.resName, k -> new ArrayList<>()).add(bean);
        }

        for (Map.Entry<String, List<ProjectResourceBean>> entry : byName.entrySet()) {
            if (entry.getValue().size() < 2) continue;

            String name = entry.getKey();
            ProjectResourceBean first = entry.getValue().get(0);
            String typeLabel = ResourceTypeUtil.typeOf(first.resFullName).name().toLowerCase();

            StringBuilder files = new StringBuilder();
            for (ProjectResourceBean bean : entry.getValue()) {
                if (files.length() > 0) files.append(", ");
                files.append(bean.resFullName != null ? bean.resFullName : bean.resName);
            }

            report.addIssue(new Issue(
                    "DUP_RESOURCE",
                    category(),
                    Severity.ERROR,
                    "Resource name \"" + name + "\" (" + typeLabel + ") is used by " + entry.getValue().size()
                            + " different files: " + files + ". Rename all but one to avoid a build conflict.",
                    ResourceTypeUtil.absolutePathOf(ctx.getScId(), first.resFullName),
                    null
            ));
        }
    }
}
