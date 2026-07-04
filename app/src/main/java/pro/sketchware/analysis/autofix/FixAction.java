package pro.sketchware.analysis.autofix;

import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.model.Issue;

public interface FixAction {

    FixPreview preview(ProjectContext ctx, Issue issue);

    void apply(ProjectContext ctx, Issue issue);
}
