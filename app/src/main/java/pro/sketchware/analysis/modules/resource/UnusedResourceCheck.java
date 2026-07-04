package pro.sketchware.analysis.modules.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import a.a.a.kC;
import com.besome.sketch.beans.ProjectResourceBean;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class UnusedResourceCheck implements AnalysisCheck {

    @Override public String id() { return "unused_resources"; }
    @Override public String category() { return "resource"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        kC resources = ctx.getResourceManager();
        if (resources == null) return;

        List<ProjectResourceBean> all = new ArrayList<>();
        if (resources.b != null) all.addAll(resources.b);
        if (resources.c != null) all.addAll(resources.c);
        if (resources.d != null) all.addAll(resources.d);

        String combined = index.getCombinedText();

        for (ProjectResourceBean bean : all) {
            if (bean == null || bean.resName == null || bean.resName.isEmpty()) continue;

            String baseName = stripExtension(bean.resName);
            Pattern reference = Pattern.compile("[./]" + Pattern.quote(baseName) + "(?![A-Za-z0-9_])");

            if (!reference.matcher(combined).find()) {
                String typeLabel = ResourceTypeUtil.typeOf(bean.resFullName).name().toLowerCase();
                report.addIssue(new Issue(
                        "UNUSED_RESOURCE",
                        category(),
                        Severity.WARNING,
                        "Resource \"" + bean.resName + "\" (" + typeLabel + ") does not appear to be referenced anywhere in the project.",
                        ResourceTypeUtil.absolutePathOf(ctx.getScId(), bean.resFullName),
                        null
                ));
            }
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
