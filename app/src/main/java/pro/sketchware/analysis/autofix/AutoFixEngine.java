package pro.sketchware.analysis.autofix;

import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.model.Issue;

public final class AutoFixEngine {

    private final UndoManager undoManager;

    public AutoFixEngine(UndoManager undoManager) {
        this.undoManager = undoManager;
    }

    public FixPreview preview(ProjectContext ctx, Issue issue) {
        if (!issue.hasAutoFix()) return null;
        FixAction action = AutoFixRegistry.get(issue.getFixId());
        if (action == null) return null;
        return action.preview(ctx, issue);
    }

    public boolean apply(ProjectContext ctx, Issue issue, UndoManager.Snapshot snapshotBeforeApply) {
        if (!issue.hasAutoFix()) return false;
        FixAction action = AutoFixRegistry.get(issue.getFixId());
        if (action == null) return false;

        undoManager.push(snapshotBeforeApply);
        action.apply(ctx, issue);
        return true;
    }
}
